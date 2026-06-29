package com.omnimerchant.knowledge.service;

import com.omnimerchant.common.config.OmniMerchantProperties;
import com.omnimerchant.knowledge.dto.*;
import com.omnimerchant.knowledge.util.RrfFusion;
import com.omnimerchant.tenant.context.TenantContextHolder;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid RAG retrieval: vector similarity + BM25 full-text search → RRF fusion → rerank.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRagService {

    private final EmbeddingService embeddingService;
    private final CrossEncoderReranker reranker;
    private final RagQueryPlanningService queryPlanningService;
    private final RagContextPacker contextPacker;
    private final OmniMerchantProperties props;

    @Autowired
    @Qualifier("pgVectorJdbcTemplate")
    private JdbcTemplate pgVectorJdbcTemplate;

    public PolicyAnswer retrieve(String question) {
        return retrieve(new RagDtos.DebugRequest(question, null, null, null, null)).answer();
    }

    public RagDtos.DebugResponse retrieve(RagDtos.DebugRequest request) {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            log.warn("RAG retrieval rejected because tenant context is missing");
            var answer = PolicyAnswer.error("Missing tenant context.");
            return new RagDtos.DebugResponse(request == null ? null : request.question(), null,
                    List.of(), List.of(), List.of(), List.of(),
                    new RagDtos.ContextPack(null, List.of(), "NONE", answer.error(), 0, 0),
                    answer, 0);
        }
        var start = System.currentTimeMillis();

        try {
            var plan = queryPlanningService.plan(request);
            var topKOverride = request == null ? null : request.topK();

            // 1. Query planning + embedding.
            var queryEmbedding = embeddingService.embed(plan.rewrittenQuery());

            // 2. Candidate generation: vector + BM25 with tenant and metadata filters.
            var vectorTopK = positiveOr(topKOverride, props.getKnowledge().getRetrieval().getVectorTopK());
            var vectorResults = vectorSearch(queryEmbedding, tenantId, vectorTopK,
                    request == null ? null : request.docType(),
                    request == null ? null : request.language());

            var bm25TopK = positiveOr(topKOverride, props.getKnowledge().getRetrieval().getBm25TopK());
            var bm25Results = bm25Search(plan.rewrittenQuery(), tenantId, bm25TopK,
                    request == null ? null : request.docType(),
                    request == null ? null : request.language());

            // 3. Fusion.
            var rrfK = props.getKnowledge().getRetrieval().getRrfK();
            var fused = RrfFusion.fuse(vectorResults, bm25Results, rrfK);

            // 4. Rerank with lexical fallback inside CrossEncoderReranker.
            var rerankTopN = props.getKnowledge().getRetrieval().getRerankTopN();
            var reranked = reranker.rerank(plan.rewrittenQuery(), fused, rerankTopN);

            // 5. Context expansion and packing.
            var expanded = expandContext(reranked, tenantId);
            var pack = contextPacker.pack(expanded);
            var answer = buildAnswer(pack);
            var elapsed = System.currentTimeMillis() - start;
            log.info("RAG retrieved {} chunks in {}ms for tenantId={}",
                    expanded.size(), elapsed, tenantId);

            return new RagDtos.DebugResponse(plan.originalQuery(), plan,
                    toCandidates(vectorResults),
                    toCandidates(bm25Results),
                    toCandidates(fused),
                    toCandidates(expanded),
                    pack,
                    answer,
                    elapsed);
        } catch (Exception e) {
            var question = request == null ? null : request.question();
            log.error("RAG retrieval failed for question: {}", question, e);
            var answer = PolicyAnswer.error("RAG retrieval failed: " + e.getMessage());
            return new RagDtos.DebugResponse(question, null, List.of(), List.of(), List.of(), List.of(),
                    new RagDtos.ContextPack(null, List.of(), "NONE", answer.error(), 0, 0),
                    answer, System.currentTimeMillis() - start);
        }
    }

    public RagDtos.DebugResponse debug(RagDtos.DebugRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            var answer = PolicyAnswer.error("RAG debug question is required.");
            return new RagDtos.DebugResponse(null, null, List.of(), List.of(), List.of(), List.of(),
                    new RagDtos.ContextPack(null, List.of(), "NONE", answer.error(), 0, 0), answer, 0);
        }
        return retrieve(request);
    }

    public RagDtos.NeighborResponse neighbors(String chunkUuid) {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            log.warn("RAG neighbor lookup rejected because tenant context is missing");
            return new RagDtos.NeighborResponse(chunkUuid, List.of());
        }
        if (chunkUuid == null || chunkUuid.isBlank()) {
            return new RagDtos.NeighborResponse(chunkUuid, List.of());
        }
        var center = pgVectorJdbcTemplate.query("""
                SELECT id, chunk_uuid, doc_id, doc_uuid, doc_type, doc_version,
                       chunk_index, chunk_text, chunk_text_en, section, section_path,
                       language, metadata::text, source_title, source_uri, source_type, source_trust_level,
                       content_hash, effective_from::text, effective_to::text, risk_level,
                       index_version, neighbor_prev_uuid, neighbor_next_uuid,
                       1.0 AS similarity
                FROM policy_vectors
                WHERE tenant_id = ? AND chunk_uuid = ?
                LIMIT 1
                """, ps -> {
            ps.setLong(1, tenantId);
            ps.setString(2, chunkUuid);
        }, this::mapVectorRow);
        if (center.isEmpty()) {
            return new RagDtos.NeighborResponse(chunkUuid, List.of());
        }
        var record = center.getFirst();
        var rows = pgVectorJdbcTemplate.query("""
                SELECT id, chunk_uuid, doc_id, doc_uuid, doc_type, doc_version,
                       chunk_index, chunk_text, chunk_text_en, section, section_path,
                       language, metadata::text, source_title, source_uri, source_type, source_trust_level,
                       content_hash, effective_from::text, effective_to::text, risk_level,
                       index_version, neighbor_prev_uuid, neighbor_next_uuid,
                       1.0 AS similarity
                FROM policy_vectors
                WHERE tenant_id = ? AND doc_uuid = ?
                  AND chunk_index BETWEEN ? AND ?
                ORDER BY chunk_index ASC
                """, ps -> {
            ps.setLong(1, tenantId);
            ps.setString(2, record.docUuid());
            ps.setInt(3, Math.max(0, record.chunkIndex() - 1));
            ps.setInt(4, record.chunkIndex() + 1);
        }, this::mapVectorRow);
        return new RagDtos.NeighborResponse(chunkUuid, rows.stream()
                .map(r -> toCandidate(new HybridSearchResult(r, 0, 0, r.chunkIndex()), !r.chunkUuid().equals(chunkUuid)))
                .toList());
    }

    private List<ChunkVectorRecord> vectorSearch(float[] queryEmbedding, Long tenantId, int topK,
                                                 String docType, String language) {
        // pgvector requires setting ef_search for HNSW quality
        pgVectorJdbcTemplate.execute("SET hnsw.ef_search = 40");

        var sql = """
                SELECT id, chunk_uuid, doc_id, doc_uuid, doc_type, doc_version,
                       chunk_index, chunk_text, chunk_text_en, section, section_path,
                       language, metadata::text, source_title, source_uri, source_type, source_trust_level,
                       content_hash, effective_from::text, effective_to::text, risk_level,
                       index_version, neighbor_prev_uuid, neighbor_next_uuid,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM policy_vectors
                WHERE tenant_id = ?
                  AND (? IS NULL OR doc_type = ?)
                  AND (? IS NULL OR language = ?)
                  AND COALESCE(risk_level, 'LOW') <> 'HIGH'
                  AND COALESCE(source_trust_level, 'MEDIUM') <> 'UNTRUSTED'
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        return pgVectorJdbcTemplate.query(sql,
                ps -> {
                    ps.setObject(1, new PGvector(queryEmbedding));
                    ps.setLong(2, tenantId);
                    ps.setString(3, blankToNull(docType));
                    ps.setString(4, blankToNull(docType));
                    ps.setString(5, blankToNull(language));
                    ps.setString(6, blankToNull(language));
                    ps.setObject(7, new PGvector(queryEmbedding));
                    ps.setInt(8, topK);
                },
                this::mapVectorRow);
    }

    private List<ChunkVectorRecord> bm25Search(String query, Long tenantId, int topK, String docType, String language) {
        var sql = """
                SELECT id, chunk_uuid, doc_id, doc_uuid, doc_type, doc_version,
                       chunk_index, chunk_text, chunk_text_en, section, section_path,
                       language, metadata::text, source_title, source_uri, source_type, source_trust_level,
                       content_hash, effective_from::text, effective_to::text, risk_level,
                       index_version, neighbor_prev_uuid, neighbor_next_uuid,
                       ts_rank(chunk_tsv, plainto_tsquery('english', ?)) AS similarity
                FROM policy_vectors
                WHERE tenant_id = ?
                  AND (? IS NULL OR doc_type = ?)
                  AND (? IS NULL OR language = ?)
                  AND COALESCE(risk_level, 'LOW') <> 'HIGH'
                  AND COALESCE(source_trust_level, 'MEDIUM') <> 'UNTRUSTED'
                  AND chunk_tsv @@ plainto_tsquery('english', ?)
                ORDER BY ts_rank(chunk_tsv, plainto_tsquery('english', ?)) DESC
                LIMIT ?
                """;
        return pgVectorJdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, query);
                    ps.setLong(2, tenantId);
                    ps.setString(3, blankToNull(docType));
                    ps.setString(4, blankToNull(docType));
                    ps.setString(5, blankToNull(language));
                    ps.setString(6, blankToNull(language));
                    ps.setString(7, query);
                    ps.setString(8, query);
                    ps.setInt(9, topK);
                },
                this::mapVectorRow);
    }

    private ChunkVectorRecord mapVectorRow(ResultSet rs, int rowNum) throws SQLException {
        return new ChunkVectorRecord(
                rs.getLong("id"),
                rs.getString("chunk_uuid"),
                rs.getLong("doc_id"),
                rs.getString("doc_uuid"),
                rs.getString("doc_type"),
                rs.getInt("doc_version"),
                rs.getInt("chunk_index"),
                rs.getString("chunk_text"),
                rs.getString("chunk_text_en"),
                rs.getString("section"),
                rs.getString("section_path"),
                rs.getString("language"),
                rs.getString("metadata"),
                rs.getString("source_title"),
                rs.getString("source_uri"),
                rs.getString("source_type"),
                rs.getString("source_trust_level"),
                rs.getString("content_hash"),
                rs.getString("effective_from"),
                rs.getString("effective_to"),
                rs.getString("risk_level"),
                rs.getString("index_version"),
                rs.getString("neighbor_prev_uuid"),
                rs.getString("neighbor_next_uuid"),
                rs.getDouble("similarity"));
    }

    private PolicyAnswer buildAnswer(RagDtos.ContextPack pack) {
        if (pack.citations().isEmpty()) {
            return PolicyAnswer.error("No relevant policy information found.");
        }
        return PolicyAnswer.of(pack.context(), pack.citations(), pack.evidenceLevel(), pack.refusalReason());
    }

    private List<HybridSearchResult> expandContext(List<HybridSearchResult> results, Long tenantId) {
        if (results.isEmpty()) {
            return results;
        }
        var expanded = new LinkedHashMap<String, HybridSearchResult>();
        for (var result : results) {
            expanded.put(result.record().chunkUuid(), result);
            for (var neighbor : neighborRows(result.record(), tenantId)) {
                expanded.putIfAbsent(neighbor.chunkUuid(),
                        new HybridSearchResult(neighbor, result.rrfScore() * 0.5, result.rerankScore() * 0.5, result.fusedRank()));
            }
        }
        return new ArrayList<>(expanded.values());
    }

    private List<ChunkVectorRecord> neighborRows(ChunkVectorRecord record, Long tenantId) {
        if (record == null || record.docUuid() == null) {
            return List.of();
        }
        try {
            return pgVectorJdbcTemplate.query("""
                    SELECT id, chunk_uuid, doc_id, doc_uuid, doc_type, doc_version,
                           chunk_index, chunk_text, chunk_text_en, section, section_path,
                           language, metadata::text, source_title, source_uri, source_type, source_trust_level,
                           content_hash, effective_from::text, effective_to::text, risk_level,
                           index_version, neighbor_prev_uuid, neighbor_next_uuid,
                           1.0 AS similarity
                    FROM policy_vectors
                    WHERE tenant_id = ? AND doc_uuid = ?
                      AND chunk_index IN (?, ?)
                    ORDER BY chunk_index ASC
                    """, ps -> {
                ps.setLong(1, tenantId);
                ps.setString(2, record.docUuid());
                ps.setInt(3, Math.max(0, record.chunkIndex() - 1));
                ps.setInt(4, record.chunkIndex() + 1);
            }, this::mapVectorRow);
        } catch (Exception e) {
            log.debug("RAG context neighbor lookup skipped: {}", e.getMessage());
            return List.of();
        }
    }

    private List<RagDtos.Candidate> toCandidates(List<?> results) {
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .map(item -> {
                    if (item instanceof HybridSearchResult result) {
                        return toCandidate(result, false);
                    }
                    if (item instanceof ChunkVectorRecord record) {
                        return toCandidate(new HybridSearchResult(record, 0, 0, 0), false);
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private RagDtos.Candidate toCandidate(HybridSearchResult result, boolean neighbor) {
        var record = result.record();
        var supportScore = result.rerankScore() > 0
                ? Math.min(100.0, result.rerankScore() * 100.0)
                : Math.max(20.0, Math.min(95.0, result.rrfScore() * 5000.0));
        return new RagDtos.Candidate(
                record.chunkUuid(),
                record.docUuid(),
                record.docType(),
                record.chunkIndex(),
                record.sectionPath(),
                record.language(),
                record.sourceTitle(),
                record.sourceUri(),
                record.sourceType(),
                record.sourceTrustLevel(),
                record.riskLevel(),
                record.indexVersion(),
                record.similarity(),
                result.rrfScore(),
                result.rerankScore(),
                result.fusedRank(),
                Math.round(supportScore * 10.0) / 10.0,
                neighbor,
                snippet(record.chunkText()));
    }

    private int positiveOr(Integer value, int fallback) {
        return value != null && value > 0 ? Math.min(value, 50) : fallback;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String snippet(String value) {
        if (value == null) {
            return "";
        }
        var cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 220 ? cleaned : cleaned.substring(0, 220).trim() + "...";
    }
}
