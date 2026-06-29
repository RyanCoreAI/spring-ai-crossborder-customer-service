package com.omnimerchant.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.knowledge.dto.RagSafetyReviewVO;
import com.omnimerchant.knowledge.entity.KnowledgeDoc;
import com.omnimerchant.knowledge.entity.RagSafetyReview;
import com.omnimerchant.knowledge.mapper.RagSafetyReviewMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RagSafetyReviewService {

    private final RagSafetyReviewMapper mapper;
    private final RagSafetyScanner scanner;
    private final ObjectMapper objectMapper;

    @Value("${omnimerchant.rag-safety.strict:true}")
    private boolean strict;

    @Transactional
    public RagSafetyReview scanAndStore(KnowledgeDoc doc) {
        var result = scanner.scan(doc.getRawContent());
        var existing = mapper.selectOne(new LambdaQueryWrapper<RagSafetyReview>()
                .eq(RagSafetyReview::getDocUuid, doc.getDocUuid())
                .last("LIMIT 1"));
        var review = existing == null ? new RagSafetyReview() : existing;
        review.setTenantId(doc.getTenantId());
        review.setDocUuid(doc.getDocUuid());
        review.setSourceType("KNOWLEDGE_DOC");
        review.setSourceTrustLevel(sourceTrustLevel(doc));
        review.setRiskLevel(result.riskLevel());
        review.setStatus(defaultStatus(result.riskLevel()));
        review.setIndexAllowed(defaultIndexAllowed(result.riskLevel()));
        review.setMatchedRules(toJson(result.matchedRules()));
        review.setRiskRules(toJson(result.matchedRules()));
        review.setRedactedExcerpt(result.redactedExcerpt());
        review.setIndexVersion("v1");
        if (existing == null) {
            mapper.insert(review);
        } else {
            mapper.updateById(review);
        }
        return review;
    }

    public boolean isIndexAllowed(KnowledgeDoc doc) {
        var review = mapper.selectOne(new LambdaQueryWrapper<RagSafetyReview>()
                .eq(RagSafetyReview::getDocUuid, doc.getDocUuid())
                .last("LIMIT 1"));
        if (review == null) {
            review = scanAndStore(doc);
        }
        return Integer.valueOf(1).equals(review.getIndexAllowed())
                && !"QUARANTINED".equals(review.getStatus())
                && !"REJECTED".equals(review.getStatus());
    }

    public IPage<RagSafetyReviewVO> list(String status, String riskLevel, int page, int size) {
        requireTenant();
        return mapper.selectPage(new Page<>(page, Math.max(1, Math.min(size, 100))),
                new LambdaQueryWrapper<RagSafetyReview>()
                        .eq(status != null && !status.isBlank(), RagSafetyReview::getStatus, status)
                        .eq(riskLevel != null && !riskLevel.isBlank(), RagSafetyReview::getRiskLevel, riskLevel)
                        .orderByDesc(RagSafetyReview::getCreatedAt))
                .convert(this::toVO);
    }

    @Transactional
    public RagSafetyReviewVO approve(String docUuid, String note) {
        var review = requireReview(docUuid);
        review.setStatus("APPROVED");
        review.setIndexAllowed(1);
        review.setReviewNote(note);
        review.setReviewedAt(LocalDateTime.now());
        review.setApprovalHistory(history("APPROVED", note));
        mapper.updateById(review);
        return toVO(review);
    }

    @Transactional
    public RagSafetyReviewVO reject(String docUuid, String note) {
        var review = requireReview(docUuid);
        review.setStatus("REJECTED");
        review.setIndexAllowed(0);
        review.setReviewNote(note);
        review.setReviewedAt(LocalDateTime.now());
        review.setApprovalHistory(history("REJECTED", note));
        mapper.updateById(review);
        return toVO(review);
    }

    public RagSafetyReview latestReview(Long tenantId, String docUuid) {
        if (docUuid == null || docUuid.isBlank()) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<RagSafetyReview>()
                .eq(RagSafetyReview::getTenantId, tenantId)
                .eq(RagSafetyReview::getDocUuid, docUuid)
                .last("LIMIT 1"));
    }

    private RagSafetyReview requireReview(String docUuid) {
        requireTenant();
        var review = mapper.selectOne(new LambdaQueryWrapper<RagSafetyReview>()
                .eq(RagSafetyReview::getDocUuid, docUuid)
                .last("LIMIT 1"));
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "RAG safety review 不存在");
        }
        return review;
    }

    private String defaultStatus(String riskLevel) {
        if (strict && "HIGH".equals(riskLevel)) {
            return "QUARANTINED";
        }
        return "APPROVED";
    }

    private int defaultIndexAllowed(String riskLevel) {
        return strict && "HIGH".equals(riskLevel) ? 0 : 1;
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String sourceTrustLevel(KnowledgeDoc doc) {
        return doc.getSourceTrustLevel() == null || doc.getSourceTrustLevel().isBlank()
                ? "MEDIUM"
                : doc.getSourceTrustLevel();
    }

    private String history(String action, String note) {
        return toJson(java.util.List.of(java.util.Map.of(
                "action", action,
                "note", note == null ? "" : note,
                "at", LocalDateTime.now().toString())));
    }

    private RagSafetyReviewVO toVO(RagSafetyReview r) {
        return new RagSafetyReviewVO(r.getId(), r.getDocUuid(), r.getSourceType(), r.getSourceTrustLevel(),
                r.getRiskLevel(), r.getStatus(), r.getIndexAllowed(), r.getMatchedRules(), r.getRiskRules(),
                r.getRedactedExcerpt(), r.getReviewNote(), r.getApprovalHistory(), r.getIndexVersion(),
                r.getReviewedAt(), r.getCreatedAt());
    }
}
