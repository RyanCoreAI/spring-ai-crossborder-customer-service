package com.omnimerchant.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.knowledge.dto.ChunkVectorRecord;
import com.omnimerchant.knowledge.entity.KnowledgeDoc;
import com.omnimerchant.knowledge.entity.RagSafetyReview;
import com.omnimerchant.knowledge.mapper.KnowledgeDocMapper;
import com.omnimerchant.knowledge.mapper.RagSafetyReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Apply current publication and review state to every retrieval path. */
@Service
@RequiredArgsConstructor
public class RagEvidenceEligibility {
    private final KnowledgeDocMapper docMapper;
    private final RagSafetyReviewMapper reviewMapper;
    private final RagSafetyScanner scanner;

    public Snapshot forChunks(Long tenantId, Collection<ChunkVectorRecord> records) {
        var uuids = records.stream().map(ChunkVectorRecord::docUuid).filter(Objects::nonNull).distinct().toList();
        if (uuids.isEmpty()) return new Snapshot(Map.of(), Map.of(), LocalDateTime.now());
        var docs = docMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getTenantId, tenantId).in(KnowledgeDoc::getDocUuid, uuids));
        return forDocuments(tenantId, docs);
    }

    public Snapshot forDocuments(Long tenantId, List<KnowledgeDoc> docs) {
        var byUuid = new LinkedHashMap<String, KnowledgeDoc>();
        docs.stream().filter(doc -> tenantId != null && tenantId.equals(doc.getTenantId()))
                .forEach(doc -> byUuid.put(doc.getDocUuid(), doc));
        if (byUuid.isEmpty()) return new Snapshot(Map.of(), Map.of(), LocalDateTime.now());
        var reviews = reviewMapper.selectList(new LambdaQueryWrapper<RagSafetyReview>()
                .eq(RagSafetyReview::getTenantId, tenantId).in(RagSafetyReview::getDocUuid, byUuid.keySet())
                .orderByDesc(RagSafetyReview::getUpdatedAt).orderByDesc(RagSafetyReview::getId));
        var byDoc = new LinkedHashMap<String, RagSafetyReview>();
        reviews.stream().filter(review -> tenantId.equals(review.getTenantId()))
                .forEach(review -> byDoc.putIfAbsent(review.getDocUuid(), review));
        return new Snapshot(byUuid, byDoc, LocalDateTime.now());
    }

    public final class Snapshot {
        private final Map<String, KnowledgeDoc> docs;
        private final Map<String, RagSafetyReview> reviews;
        private final LocalDateTime now;
        private final Map<String, Boolean> decisions = new LinkedHashMap<>();
        private final Map<String, String> riskLevels = new LinkedHashMap<>();
        private Snapshot(Map<String, KnowledgeDoc> docs, Map<String, RagSafetyReview> reviews, LocalDateTime now) {
            this.docs = docs; this.reviews = reviews; this.now = now;
        }
        public boolean allows(KnowledgeDoc doc) {
            return doc != null && docs.containsKey(doc.getDocUuid())
                    && decisions.computeIfAbsent(doc.getDocUuid(), ignored -> eligible(doc));
        }
        private boolean eligible(KnowledgeDoc doc) {
            if (!Integer.valueOf(1).equals(doc.getStatus()) || Integer.valueOf(1).equals(doc.getIsDeleted())
                    || "UNTRUSTED".equalsIgnoreCase(doc.getSourceTrustLevel())
                    || (doc.getEffectiveFrom() != null && doc.getEffectiveFrom().isAfter(now))
                    || (doc.getEffectiveUntil() != null && !doc.getEffectiveUntil().isAfter(now))) return false;
            var review = reviews.get(doc.getDocUuid());
            if (review != null) return Integer.valueOf(1).equals(review.getIndexAllowed())
                    && "APPROVED".equals(review.getStatus());
            // Legacy documents may predate persisted reviews. Do not label unchecked
            // injection content LOW merely because the vector index is unavailable.
            return !"HIGH".equals(riskLevel(doc));
        }
        public String riskLevel(KnowledgeDoc doc) {
            return riskLevels.computeIfAbsent(doc.getDocUuid(), ignored -> {
                var review = reviews.get(doc.getDocUuid());
                return review != null && review.getRiskLevel() != null ? review.getRiskLevel()
                        : scanner.scan(doc.getRawContent() == null ? doc.getSummary() : doc.getRawContent()).riskLevel();
            });
        }
        public boolean allows(ChunkVectorRecord record) {
            if (record == null || "HIGH".equalsIgnoreCase(record.riskLevel())
                    || "UNTRUSTED".equalsIgnoreCase(record.sourceTrustLevel())) return false;
            var doc = docs.get(record.docUuid());
            if (!allows(doc) || !Objects.equals(doc.getId(), record.docId())
                    || (doc.getDocVersion() == null ? 1 : doc.getDocVersion()) != record.docVersion()) return false;
            if ("knowledge-doc-fallback-v1".equals(record.indexVersion())) return true;
            // An edit invalidates old vectors until indexing succeeds again.
            if (!Integer.valueOf(1).equals(doc.getVectorSynced())) return false;
            return doc.getContentHash() == null || doc.getContentHash().isBlank()
                    || doc.getContentHash().equals(record.contentHash());
        }
    }
}
