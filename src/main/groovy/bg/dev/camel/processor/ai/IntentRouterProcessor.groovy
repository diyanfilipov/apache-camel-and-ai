package bg.dev.camel.processor.ai

import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class IntentRouterProcessor implements Processor {

  static final String INTENT_ROUTER_PROCESSOR = 'intentRouterProcessor'
  static final String HEADER_AGENT_TYPE = 'AgentType'
  static final String HEADER_SECONDARY_AGENT_TYPE = 'SecondaryAgentType'
  static final String HEADER_CONFIDENCE = 'Confidence'
  static final String HEADER_NEEDS_CLARIFICATION = 'NeedsClarification'

  private static final double CONFIDENCE_THRESHOLD = 0.7

  private final ChatClient chatClient

  IntentRouterProcessor(ChatClient.Builder builder) {
    chatClient = builder.build()
  }

  @Override
  void process(Exchange exchange) throws Exception {
    String userMessage = exchange.message.getBody(String)

    IntentClassification result = chatClient
      .prompt(buildPrompt(userMessage))
      .call()
      .entity(IntentClassification)

    exchange.message.setHeader(HEADER_AGENT_TYPE, result.agentType?.name())
    exchange.message.setHeader(HEADER_CONFIDENCE, result.confidence)
    exchange.message.setHeader(HEADER_NEEDS_CLARIFICATION, false)

    if (result.confidence < CONFIDENCE_THRESHOLD || result.ambiguous) {
      exchange.message.setHeader(HEADER_NEEDS_CLARIFICATION, true)
      return
    }

    if (result.secondaryAgent != null) {
      exchange.message.setHeader(HEADER_SECONDARY_AGENT_TYPE, result.secondaryAgent.name())
    }
  }

  private static String buildPrompt(String userMessage) {
    """\
    Classify the user message for a supply-chain and weather assistant.

    Categories:
    - WEATHER: questions about weather, temperature, forecast, or climate for a location.
    - PROCUREMENT: inventory checks, ordering parts, emergency alerts, supply chain logistics.
    - GENERAL_QA: off-topic queries unrelated to weather or supply-chain operations.

    Classification rules:
    - Set secondaryAgent when the query genuinely requires BOTH weather and procurement to answer it.
    - Set confidence to your certainty that agentType is correct (0.0 – 1.0).
    - Set ambiguous=true when the query could reasonably map to a different category or intent is unclear.

    Few-shot examples:
    "What is the forecast for Chicago this weekend?"
      → agentType=WEATHER, secondaryAgent=null, confidence=0.98, ambiguous=false
    "Is there going to be rain in Sofia tomorrow?"
      → agentType=WEATHER, secondaryAgent=null, confidence=0.97, ambiguous=false
    "Do we have GEAR-99X in stock at the Sofia warehouse?"
      → agentType=PROCUREMENT, secondaryAgent=null, confidence=0.97, ambiguous=false
    "Order 10 units of MOTOR-15C from the supplier."
      → agentType=PROCUREMENT, secondaryAgent=null, confidence=0.99, ambiguous=false
    "Should I reroute the Chicago shipment given the storm forecast?"
      → agentType=PROCUREMENT, secondaryAgent=WEATHER, confidence=0.91, ambiguous=false
    "Is the warehouse in Chicago cold right now?"
      → agentType=WEATHER, secondaryAgent=null, confidence=0.68, ambiguous=true
    "Tell me a joke."
      → agentType=GENERAL_QA, secondaryAgent=null, confidence=0.99, ambiguous=false

    User message: "${userMessage}"
    """.stripIndent()
  }
}