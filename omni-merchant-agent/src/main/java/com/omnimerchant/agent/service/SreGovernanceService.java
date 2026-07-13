package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.GovernanceDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AlertEvent;
import com.omnimerchant.agent.entity.RolloutConfig;
import com.omnimerchant.agent.entity.SloPolicy;
import com.omnimerchant.agent.entity.SloSnapshot;
import com.omnimerchant.agent.mapper.AlertEventMapper;
import com.omnimerchant.agent.mapper.RolloutConfigMapper;
import com.omnimerchant.agent.mapper.SloPolicyMapper;
import com.omnimerchant.agent.mapper.SloSnapshotMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.knowledge.service.RagGovernanceService;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SreGovernanceService {

    private static final Set<String> CONFIG_TYPES = Set.of("PROMPT", "MODEL", "TOOL_POLICY", "RAG_INDEX");
    private static final Set<String> ENFORCEMENT_MODES = Set.of("OBSERVE_ONLY", "RUNTIME_ENFORCED");
    private static final Pattern SAFE_VERSION = Pattern.compile("[A-Za-z0-9._:-]{1,96}");

    private final SloSnapshotMapper snapshotMapper;
    private final AlertEventMapper alertMapper;
    private final RolloutConfigMapper rolloutMapper;
    private final SloPolicyMapper policyMapper;
    private final OperationsAnalyticsService operationsAnalyticsService;
    private final RagGovernanceService ragGovernanceService;

    @Transactional
    public void evaluateCurrentTenant() {
        var tenantId = requireTenant();
        var capturedAt = LocalDateTime.now();
        var metrics = operationsAnalyticsService.currentSloMetrics();
        var policies = policyMapper.selectList(new LambdaQueryWrapper<SloPolicy>()
                .eq(SloPolicy::getActive, 1));
        for (var metric : metrics) {
            var policy = policies.stream().filter(p -> metric.key().equals(p.getSloKey())).findFirst().orElse(null);
            var snapshot = new SloSnapshot();
            snapshot.setTenantId(tenantId);
            snapshot.setSloKey(metric.key());
            snapshot.setSloLabel(metric.label());
            snapshot.setTargetValue(metric.target());
            snapshot.setActualValue(metric.actual());
            snapshot.setUnit(metric.unit());
            snapshot.setStatus(metric.status());
            snapshot.setWindowMinutes(policy == null ? 60 : policy.getWindowMinutes());
            snapshot.setCapturedAt(capturedAt);
            snapshotMapper.insert(snapshot);
        }

        var observedKeys = new HashSet<String>();
        for (var alert : operationsAnalyticsService.currentAlerts(metrics)) {
            var key = alertKey(alert);
            observedKeys.add(key);
            upsertAlert(tenantId, key, alert, runbookFor(alert, policies), capturedAt);
        }
        closeRecoveredAlerts(tenantId, observedKeys, capturedAt);
    }

    public List<GovernanceDtos.SloSnapshotVO> snapshots(String sloKey, int limit) {
        requireTenant();
        return snapshotMapper.selectList(new LambdaQueryWrapper<SloSnapshot>()
                        .eq(sloKey != null && !sloKey.isBlank(), SloSnapshot::getSloKey, sloKey)
                        .orderByDesc(SloSnapshot::getCapturedAt)
                        .last("LIMIT " + clamp(limit)))
                .stream().map(this::toSnapshot).toList();
    }

    public List<GovernanceDtos.AlertEventVO> alerts(String status, int limit) {
        requireTenant();
        var normalized = optionalUpper(status);
        return alertMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                        .eq(normalized != null, AlertEvent::getStatus, normalized)
                        .orderByDesc(AlertEvent::getLastObservedAt)
                        .last("LIMIT " + clamp(limit)))
                .stream().map(this::toAlert).toList();
    }

    @Transactional
    public GovernanceDtos.AlertEventVO acknowledgeAlert(Long id, Long actorId, String note) {
        var row = requireAlert(id);
        if ("CLOSED".equals(row.getStatus())) {
            throw badRequest("已恢复的告警不能确认");
        }
        row.setStatus("ACKNOWLEDGED");
        row.setAcknowledgedBy(requireActor(actorId));
        row.setAcknowledgedAt(LocalDateTime.now());
        row.setResolutionNote(trim(note, 512));
        alertMapper.updateById(row);
        return toAlert(row);
    }

    @Transactional
    public GovernanceDtos.AlertEventVO closeAlert(Long id, Long actorId, String note) {
        var row = requireAlert(id);
        row.setStatus("CLOSED");
        row.setClosedAt(LocalDateTime.now());
        row.setResolutionNote(trim(note, 512));
        if (row.getAcknowledgedBy() == null) {
            row.setAcknowledgedBy(requireActor(actorId));
            row.setAcknowledgedAt(LocalDateTime.now());
        }
        alertMapper.updateById(row);
        return toAlert(row);
    }

    public List<GovernanceDtos.RolloutConfigVO> rollouts() {
        requireTenant();
        return rolloutMapper.selectList(new LambdaQueryWrapper<RolloutConfig>()
                        .orderByDesc(RolloutConfig::getUpdatedAt))
                .stream().map(this::toRollout).toList();
    }

    @Transactional
    public GovernanceDtos.RolloutConfigVO createRollout(GovernanceDtos.RolloutCreateRequest request) {
        var tenantId = requireTenant();
        if (request == null) {
            throw badRequest("灰度配置不能为空");
        }
        var configType = requiredUpper(request.configType(), CONFIG_TYPES, "configType");
        var configKey = safeVersion(request.configKey(), "configKey");
        var stableVersion = safeVersion(request.stableVersion(), "stableVersion");
        var candidateVersion = optionalVersion(request.candidateVersion(), "candidateVersion");
        var traffic = request.trafficPercentage() == null ? 0 : request.trafficPercentage();
        if (traffic < 0 || traffic > 100 || (traffic > 0 && candidateVersion == null)) {
            throw badRequest("trafficPercentage 必须在 0-100，且灰度流量需要 candidateVersion");
        }
        var enforcement = requiredUpper(request.enforcementMode(), ENFORCEMENT_MODES, "enforcementMode");
        if ("RUNTIME_ENFORCED".equals(enforcement)
                && (!"RAG_INDEX".equals(configType) || traffic != 100)) {
            throw badRequest("当前只有 RAG_INDEX 支持 100% 运行时生效；其余配置必须标记 OBSERVE_ONLY");
        }
        var existing = rolloutMapper.selectOne(new LambdaQueryWrapper<RolloutConfig>()
                .eq(RolloutConfig::getConfigType, configType)
                .eq(RolloutConfig::getConfigKey, configKey)
                .last("LIMIT 1"));
        if (existing != null) {
            throw badRequest("同类型同键的灰度配置已存在");
        }
        var row = new RolloutConfig();
        row.setTenantId(tenantId);
        row.setConfigType(configType);
        row.setConfigKey(configKey);
        row.setStableVersion(stableVersion);
        row.setCandidateVersion(candidateVersion);
        row.setTrafficPercentage(traffic);
        row.setEnforcementMode(enforcement);
        row.setStatus("DRAFT");
        row.setNotes(trim(request.notes(), 512));
        rolloutMapper.insert(row);
        return toRollout(row);
    }

    @Transactional
    public GovernanceDtos.RolloutConfigVO activateRollout(Long id, Long actorId) {
        var row = requireRollout(id);
        if ("RUNTIME_ENFORCED".equals(row.getEnforcementMode())) {
            ragGovernanceService.activateIndex(row.getCandidateVersion(), requireActor(actorId));
        }
        row.setStatus("ACTIVE");
        row.setActivatedBy(requireActor(actorId));
        row.setActivatedAt(LocalDateTime.now());
        row.setRolledBackBy(null);
        row.setRolledBackAt(null);
        rolloutMapper.updateById(row);
        return toRollout(row);
    }

    @Transactional
    public GovernanceDtos.RolloutConfigVO rollbackRollout(Long id, Long actorId) {
        var row = requireRollout(id);
        if (!"ACTIVE".equals(row.getStatus())) {
            throw badRequest("只有 ACTIVE 灰度配置可以回滚");
        }
        if ("RUNTIME_ENFORCED".equals(row.getEnforcementMode())) {
            ragGovernanceService.rollbackActiveIndex(requireActor(actorId));
        }
        row.setStatus("ROLLED_BACK");
        row.setRolledBackBy(requireActor(actorId));
        row.setRolledBackAt(LocalDateTime.now());
        rolloutMapper.updateById(row);
        return toRollout(row);
    }

    private void upsertAlert(Long tenantId, String key, GovernanceDtos.AlertVO source,
                             String runbook, LocalDateTime now) {
        var row = alertMapper.selectOne(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getAlertKey, key)
                .last("LIMIT 1"));
        if (row == null) {
            row = new AlertEvent();
            row.setTenantId(tenantId);
            row.setAlertKey(key);
            row.setSeverity(source.severity());
            row.setCategory(source.category());
            row.setStatus("OPEN");
            row.setMessage(source.message());
            row.setRunbook(runbook);
            row.setOccurrenceCount(1L);
            row.setFirstObservedAt(now);
            row.setLastObservedAt(now);
            alertMapper.insert(row);
            return;
        }
        if ("CLOSED".equals(row.getStatus())) {
            row.setStatus("OPEN");
            row.setFirstObservedAt(now);
            row.setAcknowledgedBy(null);
            row.setAcknowledgedAt(null);
            row.setClosedAt(null);
            row.setResolutionNote(null);
        }
        row.setSeverity(source.severity());
        row.setMessage(source.message());
        row.setRunbook(runbook);
        row.setLastObservedAt(now);
        row.setOccurrenceCount((row.getOccurrenceCount() == null ? 0 : row.getOccurrenceCount()) + 1);
        alertMapper.updateById(row);
    }

    private void closeRecoveredAlerts(Long tenantId, Set<String> observedKeys, LocalDateTime now) {
        var active = alertMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getTenantId, tenantId)
                .in(AlertEvent::getStatus, List.of("OPEN", "ACKNOWLEDGED")));
        for (var row : active) {
            if (!observedKeys.contains(row.getAlertKey())) {
                row.setStatus("CLOSED");
                row.setClosedAt(now);
                row.setResolutionNote("指标已恢复，系统自动关闭");
                alertMapper.updateById(row);
            }
        }
    }

    private String alertKey(GovernanceDtos.AlertVO alert) {
        if ("SLO".equals(alert.category())) {
            return "SLO:" + alert.message().replace(" 未达目标", "").replace(' ', '_');
        }
        return alert.category();
    }

    private String runbookFor(GovernanceDtos.AlertVO alert, List<SloPolicy> policies) {
        if ("SLO".equals(alert.category())) {
            return policies.stream()
                    .filter(p -> alert.message().startsWith(p.getSloLabel()))
                    .map(SloPolicy::getRunbook).findFirst().orElse(null);
        }
        return switch (alert.category()) {
            case "SHOPIFY_WEBHOOK" -> "检查 webhook_event、签名失败、重试次数和 DLQ，再按事件 ID 安全重放。";
            case "APPROVAL" -> "由具有 action:approve 权限的人员核对证据与订单身份后处理。";
            default -> null;
        };
    }

    private AlertEvent requireAlert(Long id) {
        var row = alertMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "告警不存在");
        }
        return row;
    }

    private RolloutConfig requireRollout(Long id) {
        var row = rolloutMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "灰度配置不存在");
        }
        return row;
    }

    private GovernanceDtos.SloSnapshotVO toSnapshot(SloSnapshot row) {
        return new GovernanceDtos.SloSnapshotVO(row.getId(), row.getSloKey(), row.getSloLabel(),
                row.getTargetValue(), row.getActualValue(), row.getUnit(), row.getStatus(),
                row.getWindowMinutes(), row.getCapturedAt());
    }

    private GovernanceDtos.AlertEventVO toAlert(AlertEvent row) {
        return new GovernanceDtos.AlertEventVO(row.getId(), row.getAlertKey(), row.getSeverity(), row.getCategory(),
                row.getStatus(), row.getMessage(), row.getRunbook(), row.getOccurrenceCount(),
                row.getFirstObservedAt(), row.getLastObservedAt(), row.getAcknowledgedBy(),
                row.getAcknowledgedAt(), row.getClosedAt(), row.getResolutionNote());
    }

    private GovernanceDtos.RolloutConfigVO toRollout(RolloutConfig row) {
        var enforced = "ACTIVE".equals(row.getStatus()) && "RUNTIME_ENFORCED".equals(row.getEnforcementMode());
        var effective = enforced ? row.getCandidateVersion() : row.getStableVersion();
        return new GovernanceDtos.RolloutConfigVO(row.getId(), row.getConfigType(), row.getConfigKey(),
                row.getStableVersion(), row.getCandidateVersion(), row.getTrafficPercentage(),
                row.getEnforcementMode(), row.getStatus(), effective, enforced, row.getNotes(),
                row.getActivatedBy(), row.getActivatedAt(), row.getRolledBackBy(), row.getRolledBackAt(), row.getUpdatedAt());
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少租户上下文");
        }
        return tenantId;
    }

    private Long requireActor(Long actorId) {
        if (actorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少操作者身份");
        }
        return actorId;
    }

    private String requiredUpper(String value, Set<String> values, String field) {
        var normalized = optionalUpper(value);
        if (normalized == null || !values.contains(normalized)) {
            throw badRequest(field + " 不合法");
        }
        return normalized;
    }

    private String optionalUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeVersion(String value, String field) {
        if (value == null || !SAFE_VERSION.matcher(value.trim()).matches()) {
            throw badRequest(field + " 只能包含字母、数字、点、冒号、下划线和短横线");
        }
        return value.trim();
    }

    private String optionalVersion(String value, String field) {
        return value == null || value.isBlank() ? null : safeVersion(value, field);
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}
