package bg.dev.camel.processor.ai.agent

import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

/**
 * Final step of the orchestrator fanout: receives the aggregated output from
 * both the Weather and SupplyChain agents (separated by ---) and asks the LLM
 * to synthesize a single coherent response for the user.
 */
@Component
class OrchestratorSynthesisProcessor implements Processor {

  static final String ORCHESTRATOR_SYNTHESIS_PROCESSOR = 'orchestratorSynthesisProcessor'

  private final ChatClient chatClient

  OrchestratorSynthesisProcessor(ChatClient.Builder builder) {
    chatClient = builder.build()
  }

  @Override
  void process(Exchange exchange) throws Exception {
    String combinedReports = exchange.message.getBody(String) ?: ''

    String synthesized = chatClient.prompt()
      .system(
        'You are a logistics coordinator assistant. ' +
        'You have received parallel reports from two specialist AI agents. ' +
        'Synthesize them into a single, coherent, concise response for the user. ' +
        'Do not mention internal agent names or technical details — just answer naturally.'
      )
      .user(
        "Specialist reports:\n\n${combinedReports}\n\n" +
        'Please combine the above into one clear, unified answer.'
      )
      .call()
      .content()

    exchange.message.setBody(synthesized)
  }
}