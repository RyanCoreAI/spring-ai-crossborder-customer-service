package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.GovernanceDtos;

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
import com.omnimerchant.knowledge.service.RagGovernanceService;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SreGovernanceServiceTest {

    private SloSnapshotMapper snapshotMapper;
    private AlertEventMapper alertMapper;
    private RolloutConfigMapper rolloutMapper;
    private SloPolicyMapper policyMapper;
    private OperationsAnalyticsService operationsAnalyticsService;
    private RagGovernanceService ragGovernanceService;
    private SreGovernanceService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(1001L);
        snapshotMapper = mock(SloSnapshotMapper.class);
        alertMapper = mock(AlertEventMapper.class);
        rolloutMapper = mock(RolloutConfigMapper.class);
        policyMapper = mock(SloPolicyMapper.class);
        operationsAnalyticsService = mock(OperationsAnalyticsService.class);
        ragGovernanceService = mock(RagGovernanceService.class);
        service = new SreGovernanceService(snapshotMapper, alertMapper, rolloutMapper, policyMapper,
                operationsAnalyticsService, ragGovernanceService);
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(alertMapper.selectList(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void evaluationPersistsMeasurementsAndDeduplicatedAlert() {
        var metric = new GovernanceDtos.SloMetricVO("tool_success", "工具成功率",
                BigDecimal.valueOf(99), BigDecimal.valueOf(80), "%", "BREACH");
        var alert = new GovernanceDtos.AlertVO("WARN", "SLO", "工具成功率 未达目标", LocalDateTime.now());
        when(operationsAnalyticsService.currentSloMetrics()).thenReturn(List.of(metric));
        when(operationsAnalyticsService.currentAlerts(any())).thenReturn(List.of(alert));
        when(alertMapper.selectOne(any())).thenReturn(null);

        service.evaluateCurrentTenant();

        var snapshot = ArgumentCaptor.forClass(SloSnapshot.class);
        verify(snapshotMapper).insert(snapshot.capture());
        assertThat(snapshot.getValue().getTenantId()).isEqualTo(1001L);
        assertThat(snapshot.getValue().getSloKey()).isEqualTo("tool_success");

        var storedAlert = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertMapper).insert(storedAlert.capture());
        assertThat(storedAlert.getValue().getAlertKey()).isEqualTo("SLO:工具成功率");
        assertThat(storedAlert.getValue().getStatus()).isEqualTo("OPEN");
        assertThat(storedAlert.getValue().getOccurrenceCount()).isEqualTo(1L);
    }

    @Test
    void recoveredAlertIsClosedAutomatically() {
        when(operationsAnalyticsService.currentSloMetrics()).thenReturn(List.of());
        when(operationsAnalyticsService.currentAlerts(any())).thenReturn(List.of());
        var open = new AlertEvent();
        open.setId(7L);
        open.setTenantId(1001L);
        open.setAlertKey("SLO:工具成功率");
        open.setStatus("OPEN");
        when(alertMapper.selectList(any())).thenReturn(List.of(open));

        service.evaluateCurrentTenant();

        assertThat(open.getStatus()).isEqualTo("CLOSED");
        assertThat(open.getResolutionNote()).contains("自动关闭");
        verify(alertMapper).updateById(open);
    }

    @Test
    void nonRagRuntimeRolloutIsRejectedInsteadOfPretendingToBeApplied() {
        var request = new GovernanceDtos.RolloutCreateRequest(
                "PROMPT", "support", "prompt-v1", "prompt-v2", 100, "RUNTIME_ENFORCED", null);

        assertThatThrownBy(() -> service.createRollout(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有 RAG_INDEX");
    }

    @Test
    void activeRagRolloutDelegatesToRealIndexActivation() {
        var row = new RolloutConfig();
        row.setId(5L);
        row.setTenantId(1001L);
        row.setConfigType("RAG_INDEX");
        row.setConfigKey("policy-index");
        row.setStableVersion("index-v1");
        row.setCandidateVersion("index-v2");
        row.setTrafficPercentage(100);
        row.setEnforcementMode("RUNTIME_ENFORCED");
        row.setStatus("DRAFT");
        when(rolloutMapper.selectById(5L)).thenReturn(row);

        var result = service.activateRollout(5L, 9L);

        verify(ragGovernanceService).activateIndex("index-v2", 9L);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.runtimeEnforced()).isTrue();
        assertThat(result.effectiveVersion()).isEqualTo("index-v2");
    }
}
