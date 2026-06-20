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
import java.util.List;

/**
 * Hybrid RAG retrieval: vector similarity + BM25 full-text search → RRF fusion → rerank.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRagService {

    private final EmbeddingService embeddingService;
    private final CrossEncoderReranker reranker;
    private final OmniMerchantProperties props;

    @Autowired
    @Qualifier("pgVectorJdbcTemplate")
    private JdbcTemplate pgVectorJdbcTemplate;

    public PolicyAnswer retrieve(String question) {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            log.warn("RAG retrieval rejected because tenant context is missing");
            return PolicyAnswer.error("Missing tenant context.");
        }
        var start = System.currentTimeMillis();

        try {
            // 1. Embed query
            var queryEmbedding = embeddingService.embed(question);

            // 2. Vector search
            var vectorTopK = props.getKnowledge().getRetrieval().getVectorTopK();
            var vectorResults = vectorSearch(queryEmbedding, tenantId, vectorTopK);

            // 3. BM25 search
            var bm25TopK = props.getKnowledge().getRetrieval().getBm25TopK();
            var bm25Results = bm25Search(question, tenantId, bm25TopK);

            // 4. RRF fusion
            var rrfK = props.getKnowledge().getRetrieval().getRrfK();
            var fused = RrfFusion.fuse(vectorResults, bm25Results, rrfK);

            // 5. Cross-encoder rerank
            var rerankTopN = props.getKnowledge().getRetrieval().getRerankTopN();
            var reranked = reranker.rerank(question, fused, rerankTopN);

            // 6. Build answer
            var answer = buildAnswer(reranked);
            var elapsed = System.currentTimeMillis() - start;
            log.info("RAG retrieved {} chunks in {}ms for tenantId={}",
                    reranked.size(), elapsed, tenantId);

            return answer;
        } catch (Exception e) {
            log.error("RAG retrieval failed for question: {}", question, e);
            return PolicyAnswer.error("RAG retrieval failed: " + e.getMessage());
        }
    }

    private List<ChunkVectorRecord> vectorSearch(float[] queryEmbedding, Long tenantId, int topK) {
        // pgvector requires setting ef_search for HNSW quality
        pgVectorJdbcTemplate.execute("SET hnsw.ef_search = 40");

        var sql = """
                SELECT id, chunk_uuid, doc_id, doc_uuid, doc_type,
                       chunk_index, chunk_text, chunk_text_en, section,
                       language, metadata::text,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM policy_vectors
                WHERE tenant_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        return pgVectorJdbcTemplate.query(sql,
                ps -> {
                    ps.setObject(1, new PGvector(queryEmbedding));
                    ps.setLong(2, tenantId);
                    ps.setObject(3, new PGvector(queryEmbedding));
                    ps.setInt(4, topK);
                },
                this::mapVectorRow);
    }

    private List<ChunkVectorRecord> bm25Search(String query, Long tenantId, int topK) {
        var sql = """
                SELECT id, chunk_uuid, doc_id, doc_uuid, doc_type,
                       chunk_index, chunk_text, chunk_text_en, section,
                       language, metadata::text,
                       ts_rank(chunk_tsv, plainto_tsquery('english', ?)) AS similarity
                FROM policy_vectors
                WHERE tenant_id = ?
                  AND chunk_tsv @@ plainto_tsquery('english', ?)
                ORDER BY ts_rank(chunk_tsv, plainto_tsquery('english', ?)) DESC
                LIMIT ?
                """;
        return pgVectorJdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, query);
                    ps.setLong(2, tenantId);
                    ps.setString(3, query);
                    ps.setString(4, query);
                    ps.setInt(5, topK);
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
                rs.getInt("chunk_index"),
                rs.getString("chunk_text"),
                rs.getString("chunk_text_en"),
                rs.getString("section"),
                rs.getString("language"),
                rs.getString("metadata"),
                rs.getDouble("similarity"));
    }

    private PolicyAnswer buildAnswer(List<HybridSearchResult> results) {
        if (results.isEmpty()) {
            return PolicyAnswer.error("No relevant policy information found.");
        }
        var context = new StringBuilder();
        var citations = results.stream()
                .map(r -> {
                    context.append(r.record().chunkText()).append("\n\n");
                    return new PolicyAnswer.Citation(
                            r.record().chunkUuid(),
                            r.record().docUuid(),
                            r.record().chunkIndex(),
                            r.record().chunkText().length() > 200
                                    ? r.record().chunkText().substring(0, 200) + "..."
                                    : r.record().chunkText(),
                            r.rrfScore(),
                            r.rerankScore());
                })
                .toList();

        return PolicyAnswer.of(context.toString().trim(), citations);
    }
}
