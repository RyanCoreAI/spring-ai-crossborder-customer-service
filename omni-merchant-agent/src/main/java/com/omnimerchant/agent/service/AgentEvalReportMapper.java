package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.EvalDtos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AgentEvalResult;
import com.omnimerchant.agent.entity.AgentEvalRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AgentEvalReportMapper {

    private final ObjectMapper objectMapper;

    public EvalDtos.EvalRunResult toRunResult(AgentEvalResult result) {
        return new EvalDtos.EvalRunResult(result.getCaseCode(), result.getIntent(), result.getStatus(),
                result.getExpectedOutcome(), result.getActualObservation(), "PASS".equals(result.getStatus()),
                result.getExpectedTools(), result.getActualTools(), result.getForbiddenTools(),
                decimal(result.getToolPrecision()), decimal(result.getToolRecall()),
                Integer.valueOf(1).equals(result.getArgumentMatch()),
                Integer.valueOf(1).equals(result.getForbiddenToolViolation()), result.getTraceId(),
                result.getFailureCategory(), result.getRerankerMode(), result.getRetrievalRank(),
                result.getRetrievalLatencyMs(), decimal(result.getReciprocalRank()), decimal(result.getNdcgScore()),
                Integer.valueOf(1).equals(result.getNoAnswerExpected()),
                Integer.valueOf(1).equals(result.getNoAnswerPassed()));
    }

    public EvalDtos.EvalRunVO toRunView(AgentEvalRun run) {
        return new EvalDtos.EvalRunVO(run.getId(), run.getRunUuid(), run.getRunMode(),
                run.getDatasetKind(), run.getDatasetVersion(), run.getIndexVersion(), run.getRetrievalMode(), run.getStatus(),
                integer(run.getTotalCases()), integer(run.getPassedCases()), integer(run.getFailedCases()),
                run.getPassRate(), run.getToolPrecision(), run.getToolRecall(), run.getCitationCoverage(),
                run.getPoisoningBlockRate(), decimal(run.getRetrievalPrecisionAtK()), decimal(run.getRecallAtK()),
                decimal(run.getMrr()), decimal(run.getNdcgAtK()), decimal(run.getUnsupportedClaimRate()),
                decimal(run.getNoAnswerAccuracy()), run.getP95RetrievalLatencyMs(), run.getFailureSummary(),
                run.getStartedAt(), run.getFinishedAt());
    }

    public EvalDtos.EvalResultVO toResultView(AgentEvalResult result) {
        return new EvalDtos.EvalResultVO(result.getCaseCode(), result.getIntent(), result.getStatus(),
                result.getExpectedOutcome(), result.getActualObservation(), result.getExpectedTools(),
                result.getActualTools(), result.getForbiddenTools(), result.getToolPrecision(), result.getToolRecall(),
                Integer.valueOf(1).equals(result.getArgumentMatch()),
                Integer.valueOf(1).equals(result.getForbiddenToolViolation()),
                Integer.valueOf(1).equals(result.getCitationRequired()),
                Integer.valueOf(1).equals(result.getCitationPassed()),
                Integer.valueOf(1).equals(result.getPoisoningCase()),
                Integer.valueOf(1).equals(result.getSafetyPassed()), result.getTraceId(), result.getRerankerMode(),
                result.getRetrievalRank(), result.getRetrievalLatencyMs(), result.getReciprocalRank(), result.getNdcgScore(),
                Integer.valueOf(1).equals(result.getNoAnswerExpected()),
                Integer.valueOf(1).equals(result.getNoAnswerPassed()), result.getExpectedEvidence(),
                result.getActualEvidence(), result.getFailureCategory());
    }

    public String failureSummary(List<AgentEvalResult> results) {
        var failed = results.stream().filter(result -> !"PASS".equals(result.getStatus())).toList();
        if (failed.isEmpty()) {
            return "All cases passed.";
        }
        try {
            return objectMapper.writeValueAsString(failed.stream()
                    .collect(Collectors.groupingBy(AgentEvalResult::getFailureCategory, Collectors.counting())));
        } catch (Exception ignored) {
            return failed.size() + " failed cases";
        }
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int integer(Integer value) {
        return value == null ? 0 : value;
    }
}
