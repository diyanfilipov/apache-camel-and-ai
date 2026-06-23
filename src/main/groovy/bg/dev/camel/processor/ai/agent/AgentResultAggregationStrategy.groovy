package bg.dev.camel.processor.ai.agent

import org.apache.camel.AggregationStrategy
import org.apache.camel.Exchange
import org.springframework.stereotype.Component

/**
 * Combines the string bodies of two parallel agent exchanges, separated by a
 * markdown horizontal-rule delimiter. Used by the orchestrator multicast fanout
 * to collect Weather and SupplyChain agent responses before synthesis.
 */
@Component
class AgentResultAggregationStrategy implements AggregationStrategy {

  @Override
  Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
    if (oldExchange == null) return newExchange
    String previous = oldExchange.in.getBody(String) ?: ''
    String incoming = newExchange.in.getBody(String) ?: ''
    oldExchange.in.setBody("${previous}\n\n---\n\n${incoming}")
    oldExchange
  }
}