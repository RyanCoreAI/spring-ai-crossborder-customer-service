package com.omnimerchant.knowledge.tool;

import com.omnimerchant.knowledge.dto.PolicyAnswer;
import com.omnimerchant.knowledge.service.HybridRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI Tool: refund policy RAG retrieval.
 * LLM calls refundPolicyRAG when customer asks about refunds, returns, or exchanges.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyTools {

    private final HybridRagService hybridRagService;

    @Tool(description = """
            Search the refund policy knowledge base for relevant policy excerpts \
            related to the customer's question. Returns the most relevant policy \
            chunks with citations (source document and chunk index). \
            Use this tool when the customer asks about: refunds, returns, \
            exchanges, money-back guarantees, warranty claims, return windows, \
            refund processing time, return shipping costs, or any \
            refund/return-related policies.
            """)
    public PolicyAnswer refundPolicyRAG(
            @ToolParam(description = "The customer's question about refund or return policies, in the customer's language")
            String question) {
        try {
            log.info("refundPolicyRAG invoked: question='{}'", question);
            var answer = hybridRagService.retrieve(question);
            if (answer.citations() != null) {
                log.info("refundPolicyRAG returned {} citations", answer.citations().size());
            }
            return answer;
        } catch (Exception e) {
            log.error("refundPolicyRAG failed: {}", e.getMessage());
            return PolicyAnswer.error("Unable to retrieve refund policy information: " + e.getMessage());
        }
    }
}
