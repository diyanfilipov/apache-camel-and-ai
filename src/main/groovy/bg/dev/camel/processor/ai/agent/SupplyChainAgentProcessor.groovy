package bg.dev.camel.processor.ai.agent

import org.apache.camel.Exchange
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class SupplyChainAgentProcessor extends AgentProcessor {

  static final String SUPPLY_CHAIN_AGENT_PROCESSOR = 'supplyChainAgentProcessor'

  SupplyChainAgentProcessor(ChatClient.Builder builder) {
    super(builder)
  }

  @Override
  String getSystemPrompt(Exchange exchange) {
    def base = "You are an autonomous supply chain recovery agent. " +
      "You help logistics workers resolve mechanical and inventory issues. " +
      "Always use your provided tools to verify inventory, place orders, and alert the team before answering."
    if (!exchange.message.getHeader('OrchestratorMode', Boolean)) {
      base += " When a rerouting or operational decision depends on weather conditions at a specific location, " +
        "use the callWeatherAgent tool to delegate the weather query to the weather specialist."
    }
    base
  }

  @Override
  List<String> getToolNames(Exchange exchange) {
    def tools = ["checkInventory", "orderFromSupplier", "broadcastEmergencyAlert"]
    // Inside the orchestrator fanout the weather branch already runs in parallel,
    // so delegation would duplicate that work.
    if (!exchange.message.getHeader('OrchestratorMode', Boolean)) {
      tools << 'callWeatherAgent'
    }
    tools
  }

}