package com.omnimerchant.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.knowledge.dto.RagDtos;
import com.omnimerchant.knowledge.entity.KnowledgeDoc;
import com.omnimerchant.knowledge.entity.RagSafetyReview;
import com.omnimerchant.knowledge.mapper.KnowledgeDocMapper;
import com.omnimerchant.knowledge.mapper.RagSafetyReviewMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeHealthService {

    private final KnowledgeDocMapper docMapper;
    private final RagSafetyReviewMapper reviewMapper;
    private final JdbcTemplate jdbcTemplate;

    public RagDtos.Health health() {
        var tenantId = requireTenant();
        var now = LocalDateTime.now();
        var approvedDocs = docMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getTenantId, tenantId)
                .eq(KnowledgeDoc::getStatus, 1));
        var pendingReviews = reviewMapper.selectCount(new LambdaQueryWrapper<RagSafetyReview>()
                .eq(RagSafetyReview::getTenantId, tenantId)
                .in(RagSafetyReview::getStatus, List.of("DRAFT", "QUARANTINED")));
        var highRiskDocs = reviewMapper.selectCount(new LambdaQueryWrapper<RagSafetyReview>()
                .eq(RagSafetyReview::getTenantId, tenantId)
                .eq(RagSafetyReview::getRiskLevel, "HIGH"));
        var quarantinedDocs = reviewMapper.selectCount(new LambdaQueryWrapper<RagSafetyReview>()
                .eq(RagSafetyReview::getTenantId, tenantId)
                .eq(RagSafetyReview::getStatus, "QUARANTINED"));
        var rejectedDocs = reviewMapper.selectCount(new LambdaQueryWrapper<RagSafetyReview>()
                .eq(RagSafetyReview::getTenantId, tenantId)
                .eq(RagSafetyReview::getStatus, "REJECTED"));
        var staleDocs = docMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getTenantId, tenantId)
                .isNotNull(KnowledgeDoc::getEffectiveUntil)
                .lt(KnowledgeDoc::getEffectiveUntil, now));
        var indexFailedDocs = docMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getTenantId, tenantId)
                .eq(KnowledgeDoc::getStatus, 3));
        return new RagDtos.Health(
                approvedDocs,
                pendingReviews,
                highRiskDocs,
                quarantinedDocs,
                rejectedDocs,
                staleDocs,
                indexFailedDocs,
                safeCount("SELECT COUNT(*) FROM agent_eval_run WHERE tenant_id = ? AND unsupported_claim_rate > 0", tenantId),
                safeCount("SELECT COUNT(*) FROM agent_eval_run WHERE tenant_id = ? AND citation_coverage < 100", tenantId),
                topFailedQueries(tenantId));
    }

    private long safeCount(String sql, Long tenantId) {
        try {
            var value = jdbcTemplate.queryForObject(sql, Long.class, tenantId);
            return value == null ? 0 : value;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private List<Map<String, Object>> topFailedQueries(Long tenantId) {
        try {
            return jdbcTemplate.queryForList("""
                    SELECT case_code, intent, failure_category, COUNT(*) AS failures
                    FROM agent_eval_result
                    WHERE tenant_id = ?
                      AND status <> 'PASS'
                      AND (intent IN ('POLICY_QA', 'RETURN_REFUND', 'PRODUCT_ADVICE')
                           OR failure_category IN ('RAG_NO_RESULT', 'RAG_NO_CITATION', 'UNSUPPORTED_CLAIM'))
                    GROUP BY case_code, intent, failure_category
                    ORDER BY failures DESC, case_code ASC
                    LIMIT 10
                    """, tenantId);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }
}
