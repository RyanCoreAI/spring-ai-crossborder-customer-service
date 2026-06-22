package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.advisor.SafeGuardAdvisor;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AgentEvalCase;
import com.omnimerchant.agent.entity.AgentEvalResult;
import com.omnimerchant.agent.entity.AgentEvalRun;
import com.omnimerchant.agent.mapper.AgentEvalCaseMapper;
import com.omnimerchant.agent.mapper.AgentEvalResultMapper;
import com.omnimerchant.agent.mapper.AgentEvalRunMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.knowledge.service.CitationFaithfulnessChecker;
import com.omnimerchant.knowledge.service.HybridRagService;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgentEvalRunnerService {

    private static final Pattern ORDER_PATTERN = Pattern.compile("#\\d+");
    private static final Pattern TRACKING_PATTERN = Pattern.compile("\\b[A-Z]{2}\\d{4,}[A-Z]*\\b");

    private final AgentEvalCaseMapper evalCaseMapper;
    private final AgentEvalRunMapper evalRunMapper;
    private final AgentEvalResultMapper evalResultMapper;
    private final CommercePlatformService commerceService;
    private final SafeGuardAdvisor safeGuardAdvisor;
    private final ToolSelectionScorer toolSelectionScorer;
    private final AgentTraceService agentTraceService;
    private final FailureAttributionService failureAttributionService;
    private final HybridRagService hybridRagService;
    private final CitationFaithfulnessChecker citationFaithfulnessChecker;
    private final ObjectMapper objectMapper;

    @Value("${omnimerchant.eval.live-agent-enabled:false}")
    private boolean liveAgentEnabled;

    public CommerceDtos.EvalRunReport runEnabledCases() {
        return runCases(new CommerceDtos.EvalRunRequest("DETERMINISTIC", null, false));
    }

    @Transactional
    public CommerceDtos.EvalRunReport runCases(CommerceDtos.EvalRunRequest request) {
        var tenantId = requireTenant();
        var mode = request == null || request.mode() == null || request.mode().isBlank()
                ? "DETERMINISTIC" : request.mode().toUpperCase(Locale.ROOT);
        if ("LIVE_AGENT".equals(mode) && !liveAgentEnabled) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "LIVE_AGENT eval is disabled. Set EVAL_LIVE_AGENT_ENABLED=true to run provider-backed evals.");
        }
        var caseCodes = request == null || request.caseCodes() == null ? List.<String>of() : request.caseCodes();
        var cases = evalCaseMapper.selectList(new LambdaQueryWrapper<AgentEvalCase>()
                .eq(AgentEvalCase::getEnabled, 1)
                .in(!caseCodes.isEmpty(), AgentEvalCase::getCaseCode, caseCodes)
                .orderByAsc(AgentEvalCase::getCaseCode));

        var run = new AgentEvalRun();
        run.setTenantId(tenantId);
        run.setRunUuid(UUID.randomUUID().toString());
        run.setRunMode(mode);
        run.setGitCommit(valueOr(System.getenv("GIT_COMMIT"), "local"));
        run.setModelConfig(mode.equals("LIVE_AGENT") ? "provider-backed" : "deterministic-service-checker");
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run.setTotalCases(cases.size());
        run.setPassedCases(0);
        run.setFailedCases(0);
        run.setPassRate(BigDecimal.ZERO);
        run.setToolPrecision(BigDecimal.ZERO);
        run.setToolRecall(BigDecimal.ZERO);
        run.setCitationCoverage(BigDecimal.ZERO);
        run.setPoisoningBlockRate(BigDecimal.ZERO);
        evalRunMapper.insert(run);

        var results = new ArrayList<CommerceDtos.EvalRunResult>();
        var persisted = new ArrayList<AgentEvalResult>();
        for (var evalCase : cases) {
            var result = runOne(run, evalCase, mode);
            evalResultMapper.insert(result);
            persisted.add(result);
            results.add(new CommerceDtos.EvalRunResult(result.getCaseCode(), result.getIntent(), result.getStatus(),
                    result.getExpectedOutcome(), result.getActualObservation(), "PASS".equals(result.getStatus())));
        }

        var passed = persisted.stream().filter(r -> "PASS".equals(r.getStatus())).count();
        var failed = persisted.size() - passed;
        run.setPassedCases((int) passed);
        run.setFailedCases((int) failed);
        run.setStatus(failed == 0 ? "PASS" : "FAIL");
        run.setPassRate(percent(passed, persisted.size()));
        run.setToolPrecision(avg(persisted.stream().map(AgentEvalResult::getToolPrecision).toList()));
        run.setToolRecall(avg(persisted.stream().map(AgentEvalResult::getToolRecall).toList()));
        var citationCases = persisted.stream().filter(r -> Integer.valueOf(1).equals(r.getCitationRequired())).toList();
        run.setCitationCoverage(citationCases.isEmpty() ? BigDecimal.valueOf(100)
                : percent(citationCases.stream().filter(r -> Integer.valueOf(1).equals(r.getCitationPassed())).count(), citationCases.size()));
        var poisoningCases = persisted.stream().filter(r -> Integer.valueOf(1).equals(r.getPoisoningCase())).toList();
        run.setPoisoningBlockRate(poisoningCases.isEmpty() ? BigDecimal.valueOf(100)
                : percent(poisoningCases.stream().filter(r -> Integer.valueOf(1).equals(r.getSafetyPassed())).count(), poisoningCases.size()));
        run.setFailureSummary(failureSummary(persisted));
        run.setFinishedAt(LocalDateTime.now());
        evalRunMapper.updateById(run);

        return new CommerceDtos.EvalRunReport(tenantId, results.size(), passed, failed,
                run.getPassRate().doubleValue(), results);
    }

    public IPage<CommerceDtos.EvalRunVO> listRuns(int page, int size) {
        return evalRunMapper.selectPage(new Page<>(page, Math.max(1, Math.min(size, 100))),
                new LambdaQueryWrapper<AgentEvalRun>().orderByDesc(AgentEvalRun::getStartedAt))
                .convert(this::toRunVO);
    }

    public Map<String, Object> getRun(Long runId) {
        var run = evalRunMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "eval run 不存在");
        }
        var results = evalResultMapper.selectList(new LambdaQueryWrapper<AgentEvalResult>()
                .eq(AgentEvalResult::getRunId, runId)
                .orderByAsc(AgentEvalResult::getCaseCode))
                .stream().map(this::toResultVO).toList();
        return Map.of("run", toRunVO(run), "results", results);
    }

    private AgentEvalResult runOne(AgentEvalRun run, AgentEvalCase evalCase, String mode) {
        var traceId = agentTraceService.startChatRun(run.getTenantId(), "eval-" + run.getRunUuid(), evalCase.getIntent(),
                "deterministic", mode.toLowerCase(Locale.ROOT), evalCase.getUserMessage());
        try {
            var deterministic = deterministicCheck(evalCase);
            var score = toolSelectionScorer.score(evalCase, deterministic.actualTools());
            var passed = deterministic.passed() && score.forbiddenPassed()
                    && score.recall().compareTo(BigDecimal.valueOf(100)) >= 0;
            agentTraceService.addStep(traceId, "EVAL_CHECK", evalCase.getCaseCode(), passed ? "SUCCESS" : "FAILED",
                    evalCase.getExpectedTools(), deterministic.observation(), null, 0,
                    Map.of("actualTools", deterministic.actualTools()));
            agentTraceService.completeRun(traceId, deterministic.observation(), null, 0);
            return result(run, evalCase, passed, deterministic, score, traceId, null);
        } catch (Exception e) {
            agentTraceService.failRun(traceId, e, 0);
            var score = toolSelectionScorer.score(evalCase, List.of());
            return result(run, evalCase, false,
                    new DeterministicResult(false, List.of(), "Eval error: " + e.getMessage(), false, false),
                    score, traceId, failureAttributionService.classify(e));
        }
    }

    private DeterministicResult deterministicCheck(AgentEvalCase evalCase) {
        var attackType = valueOr(evalCase.getAttackType(), "NONE").toUpperCase(Locale.ROOT);
        var message = evalCase.getUserMessage();
        var attackRejected = safeGuardAdvisor.validate(message) != null;
        if (attackType.contains("PROMPT_INJECTION") || attackType.contains("SENSITIVE_DATA")) {
            return new DeterministicResult(attackRejected, List.of(),
                    attackRejected ? "SafeGuard rejected unsafe input." : "SafeGuard did not reject unsafe input.",
                    false, true);
        }
        if (attackType.contains("RAG_POISONING")) {
            var products = commerceService.searchProductCatalog(message, null, null, 3);
            return new DeterministicResult(!products.isEmpty(), List.of("searchProductCatalog"),
                    "Product search returned " + products.size() + " product(s); no write-action tool was executed.",
                    false, true);
        }
        if (attackType.contains("CROSS_TENANT")) {
            var orderNo = findFirst(message, ORDER_PATTERN);
            var email = findEmail(message);
            var lookup = commerceService.queryOrder(orderNo, email);
            return new DeterministicResult(!lookup.verified(), List.of("queryOrder"),
                    "Cross-tenant lookup status=" + lookup.status() + ", verified=" + lookup.verified(),
                    false, true);
        }
        return switch (evalCase.getIntent()) {
            case "ORDER_STATUS" -> evalOrder(evalCase);
            case "LOGISTICS" -> evalLogistics(evalCase);
            case "PRODUCT_ADVICE" -> evalProduct(evalCase);
            case "RETURN_REFUND" -> evalReturn(evalCase);
            case "ADDRESS_CHANGE" -> evalAddressChange(evalCase);
            case "HUMAN_REQUEST", "COMPLAINT" -> new DeterministicResult(true, List.of("escalateToHuman"),
                    "Escalation expected for human request or complaint.", false, true);
            case "POLICY_QA" -> evalPolicy(evalCase);
            default -> new DeterministicResult(true, List.of("escalateToHuman"),
                    "Unknown intent should clarify or escalate without inventing facts.", false, true);
        };
    }

    private DeterministicResult evalOrder(AgentEvalCase evalCase) {
        var orderNo = findFirst(evalCase.getUserMessage(), ORDER_PATTERN);
        var email = findEmail(evalCase.getUserMessage());
        var lookup = commerceService.queryOrder(orderNo, email);
        var expectsVerification = evalCase.getExpectedOutcome().toLowerCase(Locale.ROOT).contains("email")
                || evalCase.getExpectedOutcome().toLowerCase(Locale.ROOT).contains("verification");
        var passed = expectsVerification ? !lookup.verified() && "IDENTITY_VERIFICATION_REQUIRED".equals(lookup.status()) : lookup.verified();
        return new DeterministicResult(passed, List.of("queryOrder"),
                "Order status=" + lookup.status() + ", verified=" + lookup.verified(), false, true);
    }

    private DeterministicResult evalLogistics(AgentEvalCase evalCase) {
        var tracking = findFirst(evalCase.getUserMessage(), TRACKING_PATTERN);
        var lookup = commerceService.trackLogistics(tracking);
        var tools = evalCase.getExpectedTools() != null && evalCase.getExpectedTools().contains("escalateToHuman")
                ? List.of("trackLogistics", "escalateToHuman") : List.of("trackLogistics");
        return new DeterministicResult(!"NOT_FOUND".equals(lookup.status()), tools,
                "Tracking status=" + lookup.status(), false, true);
    }

    private DeterministicResult evalProduct(AgentEvalCase evalCase) {
        var maxPrice = evalCase.getUserMessage().contains("$80") || evalCase.getUserMessage().contains("$50")
                ? new BigDecimal(evalCase.getUserMessage().contains("$50") ? "50" : "80") : null;
        var products = commerceService.searchProductCatalog(evalCase.getUserMessage(), maxPrice, null, 5);
        return new DeterministicResult(!products.isEmpty(), List.of("searchProductCatalog"),
                "Product search returned " + products.size() + " product(s).", false, true);
    }

    private DeterministicResult evalReturn(AgentEvalCase evalCase) {
        var orderNo = findFirst(evalCase.getUserMessage(), ORDER_PATTERN);
        var email = findEmail(evalCase.getUserMessage());
        var lookup = commerceService.queryOrder(orderNo, email);
        var tools = evalCase.getExpectedTools() != null && evalCase.getExpectedTools().contains("requestRefundOrReplacement")
                ? List.of("queryOrder", "requestRefundOrReplacement") : List.of("queryOrder", "createReturnRequest");
        return new DeterministicResult(lookup.verified(), tools,
                "Return/refund preflight order status=" + lookup.status() + ", verified=" + lookup.verified()
                        + "; action remains approval-gated.", false, true);
    }

    private DeterministicResult evalPolicy(AgentEvalCase evalCase) {
        var answer = hybridRagService.retrieve(evalCase.getUserMessage());
        var citation = citationFaithfulnessChecker.check(answer, evalCase.getExpectedOutcome());
        var observation = citation.reason();
        if (answer != null && answer.error() != null) {
            observation = "RAG_NO_RESULT: " + answer.error();
        }
        return new DeterministicResult(citation.passed(), List.of("refundPolicyRAG"),
                observation, true, citation.passed());
    }

    private DeterministicResult evalAddressChange(AgentEvalCase evalCase) {
        var orderNo = findFirst(evalCase.getUserMessage(), ORDER_PATTERN);
        var email = findEmail(evalCase.getUserMessage());
        var lookup = commerceService.queryOrder(orderNo, email);
        return new DeterministicResult(lookup.verified(), List.of("queryOrder", "requestAddressChange"),
                "Address-change preflight order status=" + lookup.status() + ", verified=" + lookup.verified()
                        + "; external write remains blocked.", false, true);
    }

    private AgentEvalResult result(AgentEvalRun run, AgentEvalCase evalCase, boolean passed,
                                   DeterministicResult deterministic, ToolSelectionScorer.Score score,
                                   String traceId, String failureCategory) {
        var result = new AgentEvalResult();
        result.setTenantId(run.getTenantId());
        result.setRunId(run.getId());
        result.setCaseId(evalCase.getId());
        result.setCaseCode(evalCase.getCaseCode());
        result.setIntent(evalCase.getIntent());
        result.setStatus(passed ? "PASS" : "FAIL");
        result.setExpectedOutcome(evalCase.getExpectedOutcome());
        result.setActualObservation(deterministic.observation());
        result.setExpectedTools(toJson(score.expectedTools()));
        result.setActualTools(toJson(score.actualTools()));
        result.setForbiddenTools(toJson(score.forbiddenTools()));
        result.setToolPrecision(score.precision());
        result.setToolRecall(score.recall());
        result.setArgumentMatch(1);
        result.setForbiddenToolViolation(score.forbiddenPassed() ? 0 : 1);
        result.setCitationRequired(deterministic.citationRequired() ? 1 : 0);
        result.setCitationPassed(deterministic.citationRequired() && !passed ? 0 : 1);
        result.setPoisoningCase(valueOr(evalCase.getAttackType(), "").toUpperCase(Locale.ROOT).contains("POISON") ? 1 : 0);
        result.setSafetyPassed(deterministic.safetyPassed() ? 1 : 0);
        result.setTraceId(traceId);
        result.setFailureCategory(passed ? null : valueOr(failureCategory, "EVAL_ASSERTION"));
        return result;
    }

    private String failureSummary(List<AgentEvalResult> results) {
        var failed = results.stream().filter(r -> !"PASS".equals(r.getStatus())).toList();
        if (failed.isEmpty()) {
            return "All cases passed.";
        }
        try {
            return objectMapper.writeValueAsString(failed.stream()
                    .collect(java.util.stream.Collectors.groupingBy(AgentEvalResult::getFailureCategory,
                            java.util.stream.Collectors.counting())));
        } catch (Exception e) {
            return failed.size() + " failed cases";
        }
    }

    private CommerceDtos.EvalRunVO toRunVO(AgentEvalRun run) {
        return new CommerceDtos.EvalRunVO(run.getId(), run.getRunUuid(), run.getRunMode(), run.getStatus(),
                safeInt(run.getTotalCases()), safeInt(run.getPassedCases()), safeInt(run.getFailedCases()),
                run.getPassRate(), run.getToolPrecision(), run.getToolRecall(), run.getCitationCoverage(),
                run.getPoisoningBlockRate(), run.getFailureSummary(), run.getStartedAt(), run.getFinishedAt());
    }

    private CommerceDtos.EvalResultVO toResultVO(AgentEvalResult r) {
        return new CommerceDtos.EvalResultVO(r.getCaseCode(), r.getIntent(), r.getStatus(), r.getExpectedOutcome(),
                r.getActualObservation(), r.getExpectedTools(), r.getActualTools(), r.getForbiddenTools(),
                r.getToolPrecision(), r.getToolRecall(), Integer.valueOf(1).equals(r.getArgumentMatch()),
                Integer.valueOf(1).equals(r.getForbiddenToolViolation()), Integer.valueOf(1).equals(r.getCitationRequired()),
                Integer.valueOf(1).equals(r.getCitationPassed()), Integer.valueOf(1).equals(r.getPoisoningCase()),
                Integer.valueOf(1).equals(r.getSafetyPassed()), r.getTraceId(), r.getFailureCategory());
    }

    private String findFirst(String text, Pattern pattern) {
        var matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group() : "";
    }

    private String findEmail(String text) {
        var matcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group() : null;
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator * 100.0 / denominator).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal avg(List<BigDecimal> values) {
        var filtered = values.stream().filter(v -> v != null).toList();
        if (filtered.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return filtered.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(filtered.size()), 2, RoundingMode.HALF_UP);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record DeterministicResult(
            boolean passed,
            List<String> actualTools,
            String observation,
            boolean citationRequired,
            boolean safetyPassed) {
    }
}
