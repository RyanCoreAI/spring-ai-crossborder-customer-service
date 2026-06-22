package com.omnimerchant.knowledge.service;

import com.omnimerchant.knowledge.dto.ChunkInfo;
import com.omnimerchant.knowledge.entity.KnowledgeDoc;
import com.omnimerchant.knowledge.mapper.KnowledgeDocMapper;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Async document indexing pipeline.
 * Chunks document → generates embeddings → inserts into PGVector policy_vectors.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyIndexService {

    private final KnowledgeDocMapper docMapper;
    private final DocumentChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final RagSafetyReviewService ragSafetyReviewService;

    @Autowired
    @Qualifier("pgVectorJdbcTemplate")
    private JdbcTemplate pgVectorJdbcTemplate;

    @Async("indexingExecutor")
    @Transactional("transactionManager")
    public void indexAsync(Long docId) {
        KnowledgeDoc doc = null;
        try {
            doc = docMapper.selectById(docId);
            if (doc == null) {
                throw new IllegalArgumentException("Document not found: " + docId);
            }
            if (!ragSafetyReviewService.isIndexAllowed(doc)) {
                markIndexFailed(doc, "RAG safety review blocked indexing");
                log.warn("Indexing blocked by RAG safety review: docId={}", docId);
                return;
            }
            markIndexing(doc);

            var chunks = chunkingService.chunk(
                    doc.getRawContent(),
                    doc.getChunkSize() != null ? doc.getChunkSize() : 500,
                    doc.getChunkOverlap() != null ? doc.getChunkOverlap() : 50);

            if (chunks.isEmpty()) {
                log.warn("No chunks produced for docId={}", docId);
                markIndexFailed(doc, "No chunks produced from content");
                return;
            }

            var texts = chunks.stream().map(ChunkInfo::chunkText).toList();
            var embeddings = embeddingService.embedBatch(texts);
            insertChunks(doc, chunks, embeddings);
            completeIndexing(doc, chunks.size());

            log.info("Indexed docId={}: {} chunks → policy_vectors", docId, chunks.size());
        } catch (Exception e) {
            log.error("Indexing failed for docId={}: {}", docId, e.getMessage());
            if (doc != null) {
                markIndexFailed(doc, e.getMessage());
            }
        }
    }

    @Transactional("transactionManager")
    public void markIndexing(KnowledgeDoc doc) {
        doc.setStatus(2); // INDEXING
        docMapper.updateById(doc);
    }

    @Transactional("transactionManager")
    public void completeIndexing(KnowledgeDoc doc, int chunkCount) {
        doc.setChunkCount(chunkCount);
        doc.setVectorSynced(1);
        doc.setStatus(1); // PUBLISHED
        doc.setVectorSyncedAt(java.time.LocalDateTime.now());
        doc.setIndexError(null);
        docMapper.updateById(doc);
    }

    @Transactional("transactionManager")
    public void markIndexFailed(KnowledgeDoc doc, String errorMsg) {
        doc.setStatus(3); // INDEX_FAILED
        doc.setIndexError(errorMsg != null && errorMsg.length() > 1000
                ? errorMsg.substring(0, 1000) : errorMsg);
        docMapper.updateById(doc);
    }

    private void insertChunks(KnowledgeDoc doc, List<ChunkInfo> chunks, List<float[]> embeddings) {
        var sql = """
                INSERT INTO policy_vectors
                  (chunk_uuid, tenant_id, doc_id, doc_uuid, doc_type, doc_version,
                   chunk_index, chunk_text, chunk_length, section, language,
                   embedding, embedding_model, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?, ?::jsonb)
                """;

        pgVectorJdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                var chunk = chunks.get(i);
                ps.setString(1, UUID.randomUUID().toString());
                ps.setLong(2, doc.getTenantId());
                ps.setLong(3, doc.getId());
                ps.setString(4, doc.getDocUuid());
                ps.setString(5, doc.getDocType());
                ps.setInt(6, doc.getDocVersion() != null ? doc.getDocVersion() : 1);
                ps.setInt(7, chunk.chunkIndex());
                ps.setString(8, chunk.chunkText());
                ps.setInt(9, chunk.chunkText().length());
                ps.setString(10, chunk.section());
                ps.setString(11, chunk.language());
                ps.setObject(12, new PGvector(embeddings.get(i)));
                ps.setString(13, "text-embedding-3-small");
                ps.setString(14, "{}");
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }
}
