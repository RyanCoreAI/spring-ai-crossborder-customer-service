package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.EvalDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AgentEvalCase;
import com.omnimerchant.agent.entity.AuditEvent;
import com.omnimerchant.agent.mapper.AgentEvalCaseMapper;
import com.omnimerchant.agent.mapper.AuditEventMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.knowledge.entity.RagDatasetVersion;
import com.omnimerchant.knowledge.mapper.RagDatasetVersionMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgentEvalDatasetService {

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern SAFE_TOOL = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,63}");
    private static final Set<String> DECISIONS = Set.of("APPROVED", "REJECTED");

    private final AgentEvalCaseMapper caseMapper;
    private final RagDatasetVersionMapper datasetMapper;
    private final AuditEventMapper auditMapper;
    private final ObjectMapper objectMapper;

    public CommerceDtos.PageResult<EvalDtos.GoldEvalCaseVO> listGoldCases(
            String datasetVersion, String annotationStatus, int page, int size) {
        requireTenant();
        var result = caseMapper.selectPage(new Page<>(Math.max(1, page), clamp(size)),
                new LambdaQueryWrapper<AgentEvalCase>()
                        .eq(AgentEvalCase::getDatasetKind, "GOLD")
                        .eq(datasetVersion != null && !datasetVersion.isBlank(),
                                AgentEvalCase::getDatasetVersion, datasetVersion)
                        .eq(annotationStatus != null && !annotationStatus.isBlank(),
                                AgentEvalCase::getAnnotationStatus, upper(annotationStatus))
                        .orderByDesc(AgentEvalCase::getUpdatedAt));
        return new CommerceDtos.PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toVO).toList());
    }

    @Transactional
    public EvalDtos.GoldEvalCaseVO createGoldCase(EvalDtos.GoldEvalCaseCreateRequest request) {
        if (request == null) {
            throw badRequest("GOLD 用例不能为空");
        }
        var tenantId = requireTenant();
        var datasetVersion = requireDraftGoldDataset(request.datasetVersion()).getVersion();
        var caseCode = safeCode(request.caseCode(), "caseCode");
        ensureUniqueCase(caseCode);
        var row = new AgentEvalCase();
        row.setTenantId(tenantId);
        row.setDatasetKind("GOLD");
        row.setDatasetVersion(datasetVersion);
        row.setCaseCode(caseCode);
        row.setIntent(requiredText(request.intent(), "intent", 32).toUpperCase(Locale.ROOT));
        row.setUserMessage(requiredText(request.userMessage(), "userMessage", 4000));
        row.setExpectedTools(toToolsJson(request.expectedTools()));
        row.setExpectedOutcome(requiredText(request.expectedOutcome(), "expectedOutcome", 1024));
        row.setAttackType(optionalText(request.attackType(), 64));
        row.setAnnotationStatus("DRAFT");
        row.setEnabled(0);
        caseMapper.insert(row);
        return toVO(row);
    }

    @Transactional
    public EvalDtos.GoldEvalCaseVO copyAsGoldDraft(Long sourceCaseId,
                                                       EvalDtos.GoldEvalCaseCopyRequest request) {
        if (request == null) {
            throw badRequest("复制请求不能为空");
        }
        var source = caseMapper.selectById(sourceCaseId);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "源评测用例不存在");
        }
        return createGoldCase(new EvalDtos.GoldEvalCaseCreateRequest(
                request.datasetVersion(), request.caseCode(), source.getIntent(), source.getUserMessage(),
                parseTools(source.getExpectedTools()), source.getExpectedOutcome(), source.getAttackType()));
    }

    @Transactional
    public EvalDtos.GoldEvalCaseVO reviewGoldCase(Long id,
                                                       EvalDtos.GoldEvalCaseReviewRequest request,
                                                       Long actorId) {
        var tenantId = requireTenant();
        var row = caseMapper.selectOne(new LambdaQueryWrapper<AgentEvalCase>()
                .eq(AgentEvalCase::getId, id)
                .eq(AgentEvalCase::getDatasetKind, "GOLD")
                .last("FOR UPDATE"));
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "GOLD 评测用例不存在");
        }
        requireDraftGoldDataset(row.getDatasetVersion());
        var decision = request == null ? null : upper(request.decision());
        if (!DECISIONS.contains(decision)) {
            throw badRequest("审核结果只能是 APPROVED 或 REJECTED");
        }
        if (actorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少审核人身份");
        }
        row.setAnnotationStatus(decision);
        row.setAnnotatedBy(actorId);
        row.setAnnotatedAt(LocalDateTime.now());
        row.setAnnotationNote(optionalText(request.note(), 512));
        row.setEnabled("APPROVED".equals(decision) ? 1 : 0);
        caseMapper.updateById(row);
        writeAudit(tenantId, actorId, decision, row);
        return toVO(row);
    }

    private RagDatasetVersion requireDraftGoldDataset(String version) {
        var normalized = safeCode(version, "datasetVersion");
        var dataset = datasetMapper.selectOne(new LambdaQueryWrapper<RagDatasetVersion>()
                .eq(RagDatasetVersion::getDatasetKind, "GOLD")
                .eq(RagDatasetVersion::getVersion, normalized)
                .last("LIMIT 1"));
        if (dataset == null) {
            throw badRequest("请先创建对应的 GOLD 数据集版本");
        }
        if (!"DRAFT".equals(dataset.getStatus())) {
            throw badRequest("已发布的 GOLD 数据集不可继续修改");
        }
        return dataset;
    }

    private void ensureUniqueCase(String caseCode) {
        if (caseMapper.selectCount(new LambdaQueryWrapper<AgentEvalCase>()
                .eq(AgentEvalCase::getCaseCode, caseCode)) > 0) {
            throw badRequest("caseCode 已存在");
        }
    }

    private void writeAudit(Long tenantId, Long actorId, String decision, AgentEvalCase row) {
        var audit = new AuditEvent();
        audit.setTenantId(tenantId);
        audit.setActorId(actorId);
        audit.setActorRole("EVAL_REVIEWER");
        audit.setAction("REVIEW_GOLD_EVAL_CASE");
        audit.setResourceType("AGENT_EVAL_CASE");
        audit.setResourceId(String.valueOf(row.getId()));
        audit.setSummary("GOLD 用例 " + row.getCaseCode() + " 审核为 " + decision);
        audit.setRiskLevel("MEDIUM");
        audit.setMetadataJson("datasetVersion=" + row.getDatasetVersion());
        auditMapper.insert(audit);
    }

    private EvalDtos.GoldEvalCaseVO toVO(AgentEvalCase row) {
        return new EvalDtos.GoldEvalCaseVO(row.getId(), row.getDatasetVersion(), row.getCaseCode(), row.getIntent(),
                row.getUserMessage(), parseTools(row.getExpectedTools()), row.getExpectedOutcome(), row.getAttackType(),
                row.getAnnotationStatus(), row.getAnnotatedBy(), row.getAnnotatedAt(), row.getAnnotationNote(),
                row.getEnabled(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private String toToolsJson(List<String> tools) {
        var normalized = tools == null ? List.<String>of() : tools.stream()
                .filter(tool -> tool != null && !tool.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.size() > 12 || normalized.stream().anyMatch(tool -> !SAFE_TOOL.matcher(tool).matches())) {
            throw badRequest("expectedTools 包含非法工具名或超过 12 个");
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception error) {
            throw badRequest("expectedTools 无法序列化");
        }
    }

    private List<String> parseTools(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
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

    private String safeCode(String value, String field) {
        if (value == null || !SAFE_CODE.matcher(value.trim()).matches()) {
            throw badRequest(field + " 只能包含字母、数字、点、下划线和短横线");
        }
        return value.trim();
    }

    private String requiredText(String value, String field, int max) {
        var normalized = optionalText(value, max);
        if (normalized == null) {
            throw badRequest(field + " 不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.trim();
        if (normalized.length() > max) {
            throw badRequest("字段长度不能超过 " + max);
        }
        return normalized;
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}
