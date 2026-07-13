package com.omnimerchant.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.knowledge.dto.RagGovernanceDtos;
import com.omnimerchant.knowledge.entity.RagDatasetVersion;
import com.omnimerchant.knowledge.entity.RagFeedback;
import com.omnimerchant.knowledge.entity.RagIndexRelease;
import com.omnimerchant.knowledge.mapper.RagDatasetVersionMapper;
import com.omnimerchant.knowledge.mapper.RagFeedbackMapper;
import com.omnimerchant.knowledge.mapper.RagIndexReleaseMapper;
import com.omnimerchant.knowledge.mapper.RagRetrievalExperimentMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RagGovernanceService {

    private static final Set<String> DATASET_KINDS = Set.of("CONTRACT", "GOLD");
    private static final Set<String> FEEDBACK_TYPES = Set.of(
            "CITATION_ERROR", "RETRIEVAL_ERROR", "POLICY_STALE", "ANSWER_ERROR");
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._-]{1,96}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?\\d[ -]?){7,15}(?!\\d)");
    private static final Pattern ORDER = Pattern.compile("#\\d{3,}");

    private final RagDatasetVersionMapper datasetMapper;
    private final RagFeedbackMapper feedbackMapper;
    private final RagIndexReleaseMapper indexReleaseMapper;
    private final RagRetrievalExperimentMapper experimentMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("pgVectorJdbcTemplate")
    private JdbcTemplate pgVectorJdbcTemplate;

    public List<RagGovernanceDtos.DatasetVO> listDatasets(String kind) {
        requireTenant();
        var normalized = optionalUpper(kind);
        return datasetMapper.selectList(new LambdaQueryWrapper<RagDatasetVersion>()
                        .eq(normalized != null, RagDatasetVersion::getDatasetKind, normalized)
                        .orderByDesc(RagDatasetVersion::getCreatedAt))
                .stream().map(this::toDatasetVO).toList();
    }

    @Transactional
    public RagGovernanceDtos.DatasetVO createDataset(RagGovernanceDtos.DatasetCreateRequest request) {
        var tenantId = requireTenant();
        if (request == null) {
            throw badRequest("数据集请求不能为空");
        }
        var datasetKey = safeKey(request.datasetKey(), "datasetKey");
        var version = safeKey(request.version(), "version");
        var kind = requiredUpper(request.datasetKind(), DATASET_KINDS, "datasetKind");
        var duplicate = datasetMapper.selectCount(new LambdaQueryWrapper<RagDatasetVersion>()
                .eq(RagDatasetVersion::getTenantId, tenantId)
                .eq(RagDatasetVersion::getDatasetKey, datasetKey)
                .eq(RagDatasetVersion::getVersion, version));
        if (duplicate > 0) {
            throw badRequest("数据集版本已存在");
        }
        var dataset = new RagDatasetVersion();
        dataset.setTenantId(tenantId);
        dataset.setDatasetKey(datasetKey);
        dataset.setDatasetKind(kind);
        dataset.setVersion(version);
        dataset.setStatus("DRAFT");
        dataset.setCaseCount(countCases(tenantId, kind, version, false));
        dataset.setLanguageDistribution(redactAndLimit(request.languageDistribution(), 1000));
        datasetMapper.insert(dataset);
        return toDatasetVO(dataset);
    }

    @Transactional
    public RagGovernanceDtos.DatasetVO publishDataset(Long id, Long actorId) {
        var tenantId = requireTenant();
        var dataset = datasetMapper.selectOne(new LambdaQueryWrapper<RagDatasetVersion>()
                .eq(RagDatasetVersion::getTenantId, tenantId)
                .eq(RagDatasetVersion::getId, id)
                .last("FOR UPDATE"));
        if (dataset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "RAG 数据集不存在");
        }
        var total = countCases(tenantId, dataset.getDatasetKind(), dataset.getVersion(), false);
        var approved = countCases(tenantId, dataset.getDatasetKind(), dataset.getVersion(), true);
        if (total == 0) {
            throw badRequest("数据集没有可评测用例，不能发布");
        }
        if ("GOLD".equals(dataset.getDatasetKind()) && approved != total) {
            throw badRequest("GOLD 数据集必须由人工逐条审核后才能发布");
        }
        dataset.setCaseCount(total);
        dataset.setStatus("PUBLISHED");
        dataset.setChecksum(datasetChecksum(tenantId, dataset.getDatasetKind(), dataset.getVersion()));
        dataset.setApprovedBy(requireActor(actorId));
        dataset.setApprovedAt(LocalDateTime.now());
        datasetMapper.updateById(dataset);
        return toDatasetVO(dataset);
    }

    public List<RagGovernanceDtos.FeedbackVO> listFeedback(String status, String type) {
        requireTenant();
        return feedbackMapper.selectList(new LambdaQueryWrapper<RagFeedback>()
                        .eq(optionalUpper(status) != null, RagFeedback::getStatus, optionalUpper(status))
                        .eq(optionalUpper(type) != null, RagFeedback::getFeedbackType, optionalUpper(type))
                        .orderByDesc(RagFeedback::getCreatedAt)
                        .last("LIMIT 200"))
                .stream().map(this::toFeedbackVO).toList();
    }

    @Transactional
    public RagGovernanceDtos.FeedbackVO submitFeedback(RagGovernanceDtos.FeedbackCreateRequest request, Long actorId) {
        var tenantId = requireTenant();
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw badRequest("反馈问题不能为空");
        }
        var type = requiredUpper(request.feedbackType(), FEEDBACK_TYPES, "feedbackType");
        var feedback = new RagFeedback();
        feedback.setTenantId(tenantId);
        feedback.setFeedbackUuid(UUID.randomUUID().toString());
        feedback.setConversationUuid(trimToNull(request.conversationUuid(), 64));
        feedback.setTraceId(trimToNull(request.traceId(), 64));
        feedback.setQuestionHash(sha256(request.question()));
        feedback.setFeedbackType(type);
        feedback.setDocUuid(trimToNull(request.docUuid(), 64));
        feedback.setChunkUuid(trimToNull(request.chunkUuid(), 64));
        feedback.setCommentRedacted(redactAndLimit(request.comment(), 1000));
        feedback.setStatus("OPEN");
        feedback.setSubmittedBy(requireActor(actorId));
        feedbackMapper.insert(feedback);
        return toFeedbackVO(feedback);
    }

    @Transactional
    public RagGovernanceDtos.FeedbackVO resolveFeedback(Long id,
                                                         RagGovernanceDtos.FeedbackResolveRequest request,
                                                         Long actorId) {
        var tenantId = requireTenant();
        var feedback = feedbackMapper.selectOne(new LambdaQueryWrapper<RagFeedback>()
                .eq(RagFeedback::getTenantId, tenantId)
                .eq(RagFeedback::getId, id)
                .last("FOR UPDATE"));
        if (feedback == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "RAG 反馈不存在");
        }
        var status = request == null ? null : optionalUpper(request.status());
        if (status == null || !Set.of("RESOLVED", "REJECTED").contains(status)) {
            throw badRequest("反馈状态只能是 RESOLVED 或 REJECTED");
        }
        feedback.setStatus(status);
        feedback.setResolvedBy(requireActor(actorId));
        feedback.setResolvedAt(LocalDateTime.now());
        feedback.setResolutionNote(redactAndLimit(request.resolutionNote(), 1000));
        feedbackMapper.updateById(feedback);
        return toFeedbackVO(feedback);
    }

    public List<RagGovernanceDtos.IndexReleaseVO> listIndexReleases() {
        requireTenant();
        return indexReleaseMapper.selectList(new LambdaQueryWrapper<RagIndexRelease>()
                        .orderByDesc(RagIndexRelease::getCreatedAt))
                .stream().map(this::toIndexVO).toList();
    }

    public List<RagGovernanceDtos.RetrievalExperimentVO> listExperiments(String datasetVersion) {
        requireTenant();
        return experimentMapper.selectList(new LambdaQueryWrapper<com.omnimerchant.knowledge.entity.RagRetrievalExperiment>()
                        .eq(datasetVersion != null && !datasetVersion.isBlank(),
                                com.omnimerchant.knowledge.entity.RagRetrievalExperiment::getDatasetVersion,
                                datasetVersion)
                        .orderByDesc(com.omnimerchant.knowledge.entity.RagRetrievalExperiment::getStartedAt)
                        .last("LIMIT 100"))
                .stream()
                .map(e -> new RagGovernanceDtos.RetrievalExperimentVO(e.getId(), e.getRunUuid(), e.getDatasetKey(),
                        e.getDatasetKind(), e.getDatasetVersion(), e.getIndexVersion(), e.getRetrievalMode(),
                        e.getStatus(), e.getCaseCount() == null ? 0 : e.getCaseCount(), e.getContextPrecision(),
                        e.getContextRecall(), e.getMrr(), e.getNdcgAtK(), e.getCitationCoverage(),
                        e.getFaithfulness(), e.getNoAnswerAccuracy(), e.getPoisoningBlockRate(),
                        e.getP95RetrievalLatencyMs(), e.getStartedAt(), e.getFinishedAt()))
                .toList();
    }

    @Transactional
    public RagGovernanceDtos.IndexReleaseVO createIndexRelease(RagGovernanceDtos.IndexReleaseCreateRequest request) {
        var tenantId = requireTenant();
        if (request == null) {
            throw badRequest("索引发布请求不能为空");
        }
        var indexVersion = safeKey(request.indexVersion(), "indexVersion");
        if (indexReleaseMapper.selectCount(new LambdaQueryWrapper<RagIndexRelease>()
                .eq(RagIndexRelease::getTenantId, tenantId)
                .eq(RagIndexRelease::getIndexVersion, indexVersion)) > 0) {
            throw badRequest("索引版本已存在");
        }
        var release = new RagIndexRelease();
        release.setTenantId(tenantId);
        release.setIndexVersion(indexVersion);
        release.setStatus("DRAFT");
        release.setEmbeddingModel(requiredText(request.embeddingModel(), "embeddingModel", 128));
        release.setRerankerMode(requiredText(request.rerankerMode(), "rerankerMode", 64));
        release.setQueryPlannerVersion(requiredText(request.queryPlannerVersion(), "queryPlannerVersion", 64));
        release.setReleaseNote(redactAndLimit(request.releaseNote(), 1000));
        indexReleaseMapper.insert(release);
        return toIndexVO(release);
    }

    @Transactional
    public RagGovernanceDtos.IndexReleaseVO activateIndex(String version, Long actorId) {
        var tenantId = requireTenant();
        lockTenant(tenantId);
        var target = findReleaseForUpdate(tenantId, version);
        ensureIndexed(tenantId, target.getIndexVersion());
        var active = activeReleaseForUpdate(tenantId);
        if (active != null && active.getId().equals(target.getId())) {
            return toIndexVO(active);
        }
        if (active != null) {
            active.setStatus("SUPERSEDED");
            indexReleaseMapper.updateById(active);
            target.setPreviousVersion(active.getIndexVersion());
        }
        target.setStatus("ACTIVE");
        target.setActivatedBy(requireActor(actorId));
        target.setActivatedAt(LocalDateTime.now());
        indexReleaseMapper.updateById(target);
        return toIndexVO(target);
    }

    @Transactional
    public RagGovernanceDtos.IndexReleaseVO rollbackActiveIndex(Long actorId) {
        var tenantId = requireTenant();
        lockTenant(tenantId);
        var active = activeReleaseForUpdate(tenantId);
        if (active == null || active.getPreviousVersion() == null) {
            throw badRequest("当前索引没有可回滚的上一版本");
        }
        var previous = findReleaseForUpdate(tenantId, active.getPreviousVersion());
        ensureIndexed(tenantId, previous.getIndexVersion());
        active.setStatus("ROLLED_BACK");
        active.setRolledBackBy(requireActor(actorId));
        active.setRolledBackAt(LocalDateTime.now());
        indexReleaseMapper.updateById(active);
        previous.setStatus("ACTIVE");
        previous.setActivatedBy(actorId);
        previous.setActivatedAt(LocalDateTime.now());
        indexReleaseMapper.updateById(previous);
        return toIndexVO(previous);
    }

    public String activeIndexVersion(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        var release = indexReleaseMapper.selectOne(new LambdaQueryWrapper<RagIndexRelease>()
                .eq(RagIndexRelease::getTenantId, tenantId)
                .eq(RagIndexRelease::getStatus, "ACTIVE")
                .orderByDesc(RagIndexRelease::getActivatedAt)
                .last("LIMIT 1"));
        return release == null ? null : release.getIndexVersion();
    }

    public void assertRunnableDataset(Long tenantId, String kind, String version) {
        if (!"GOLD".equals(kind)) {
            return;
        }
        var published = datasetMapper.selectCount(new LambdaQueryWrapper<RagDatasetVersion>()
                .eq(RagDatasetVersion::getTenantId, tenantId)
                .eq(RagDatasetVersion::getDatasetKind, "GOLD")
                .eq(RagDatasetVersion::getVersion, version)
                .eq(RagDatasetVersion::getStatus, "PUBLISHED"));
        if (published == 0) {
            throw badRequest("GOLD 数据集必须完成逐条人工审核并发布后才能运行");
        }
    }

    public String writeIndexVersion(Long tenantId, String fallback) {
        var draft = indexReleaseMapper.selectOne(new LambdaQueryWrapper<RagIndexRelease>()
                .eq(RagIndexRelease::getTenantId, tenantId)
                .eq(RagIndexRelease::getStatus, "DRAFT")
                .orderByDesc(RagIndexRelease::getCreatedAt)
                .last("LIMIT 1"));
        if (draft != null) {
            return draft.getIndexVersion();
        }
        var active = activeIndexVersion(tenantId);
        return active == null || active.isBlank() ? fallback : active;
    }

    private RagIndexRelease activeReleaseForUpdate(Long tenantId) {
        return indexReleaseMapper.selectOne(new LambdaQueryWrapper<RagIndexRelease>()
                .eq(RagIndexRelease::getTenantId, tenantId)
                .eq(RagIndexRelease::getStatus, "ACTIVE")
                .last("LIMIT 1 FOR UPDATE"));
    }

    private RagIndexRelease findReleaseForUpdate(Long tenantId, String version) {
        var target = indexReleaseMapper.selectOne(new LambdaQueryWrapper<RagIndexRelease>()
                .eq(RagIndexRelease::getTenantId, tenantId)
                .eq(RagIndexRelease::getIndexVersion, safeKey(version, "indexVersion"))
                .last("FOR UPDATE"));
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "RAG 索引版本不存在");
        }
        return target;
    }

    private void ensureIndexed(Long tenantId, String version) {
        var count = pgVectorJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM policy_vectors WHERE tenant_id = ? AND index_version = ?",
                Long.class, tenantId, version);
        if (count == null || count == 0) {
            throw badRequest("索引版本没有可检索 chunk，不能激活");
        }
    }

    private void lockTenant(Long tenantId) {
        jdbcTemplate.queryForObject("SELECT id FROM tenant WHERE id = ? FOR UPDATE", Long.class, tenantId);
    }

    private int countCases(Long tenantId, String kind, String version, boolean approvedOnly) {
        var sql = "SELECT COUNT(*) FROM agent_eval_case WHERE tenant_id = ? AND dataset_kind = ? AND dataset_version = ?"
                + (approvedOnly ? " AND annotation_status = 'APPROVED'" : "");
        var count = jdbcTemplate.queryForObject(sql, Integer.class, tenantId, kind, version);
        return count == null ? 0 : count;
    }

    private String datasetChecksum(Long tenantId, String kind, String version) {
        var rows = jdbcTemplate.queryForList("""
                SELECT case_code, intent, expected_tools, expected_outcome, annotation_status
                FROM agent_eval_case
                WHERE tenant_id = ? AND dataset_kind = ? AND dataset_version = ?
                ORDER BY case_code
                """, tenantId, kind, version);
        var canonical = rows.stream()
                .map(row -> row.values().stream().map(String::valueOf).reduce((a, b) -> a + "|" + b).orElse(""))
                .reduce((a, b) -> a + "\n" + b).orElse("");
        return sha256(canonical);
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private Long requireActor(Long actorId) {
        if (actorId == null) {
            throw new org.springframework.security.access.AccessDeniedException("令牌缺少用户身份");
        }
        return actorId;
    }

    private String requiredUpper(String value, Set<String> allowed, String field) {
        var normalized = optionalUpper(value);
        if (normalized == null || !allowed.contains(normalized)) {
            throw badRequest(field + " 非法，可选值: " + String.join(", ", allowed));
        }
        return normalized;
    }

    private String optionalUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeKey(String value, String field) {
        if (value == null || !SAFE_KEY.matcher(value.trim()).matches()) {
            throw badRequest(field + " 只能包含字母、数字、点、下划线和连字符");
        }
        return value.trim();
    }

    private String requiredText(String value, String field, int max) {
        var result = trimToNull(value, max);
        if (result == null) {
            throw badRequest(field + " 不能为空");
        }
        return result;
    }

    private String trimToNull(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String redactAndLimit(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var redacted = EMAIL.matcher(value).replaceAll("[email]");
        redacted = PHONE.matcher(redacted).replaceAll("[phone]");
        redacted = ORDER.matcher(redacted).replaceAll("[order]");
        redacted = redacted.replaceAll("\\s+", " ").trim();
        return redacted.length() <= max ? redacted : redacted.substring(0, max);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private RagGovernanceDtos.DatasetVO toDatasetVO(RagDatasetVersion d) {
        return new RagGovernanceDtos.DatasetVO(d.getId(), d.getDatasetKey(), d.getDatasetKind(), d.getVersion(),
                d.getStatus(), d.getCaseCount() == null ? 0 : d.getCaseCount(), d.getLanguageDistribution(),
                d.getChecksum(), d.getApprovedBy(), d.getApprovedAt(), d.getCreatedAt());
    }

    private RagGovernanceDtos.FeedbackVO toFeedbackVO(RagFeedback f) {
        return new RagGovernanceDtos.FeedbackVO(f.getId(), f.getFeedbackUuid(), f.getConversationUuid(), f.getTraceId(),
                f.getQuestionHash(), f.getFeedbackType(), f.getDocUuid(), f.getChunkUuid(), f.getCommentRedacted(),
                f.getStatus(), f.getSubmittedBy(), f.getResolvedBy(), f.getResolvedAt(), f.getResolutionNote(),
                f.getCreatedAt());
    }

    private RagGovernanceDtos.IndexReleaseVO toIndexVO(RagIndexRelease r) {
        return new RagGovernanceDtos.IndexReleaseVO(r.getId(), r.getIndexVersion(), r.getStatus(),
                r.getEmbeddingModel(), r.getRerankerMode(), r.getQueryPlannerVersion(), r.getPreviousVersion(),
                r.getReleaseNote(), r.getActivatedBy(), r.getActivatedAt(), r.getRolledBackBy(),
                r.getRolledBackAt(), r.getCreatedAt());
    }
}
