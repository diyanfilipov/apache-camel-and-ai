package bg.dev.camel.processor.ai.agent

import org.apache.camel.Exchange
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

/**
 * Stage 5 of the sequential pipeline: a lightweight second LLM call that reformats
 * and compresses verbose agent responses for Telegram.
 *
 * This is the "formatter agent" in the chain-of-thought chaining pattern:
 *   Agent (reasoning) → Agent (formatting + compression)
 *
 * It adds Telegram-friendly Markdown (*bold*, _italic_) and emojis, and trims
 * the output to ≤ 300 characters. It extends AgentProcessor so the same
 * ChatClient wiring is reused, but declares no tools — pure text-to-text.
 */
@Component
class SummariserAgentProcessor extends AgentProcessor {

  static final String SUMMARISER_AGENT_PROCESSOR = 'summariserAgentProcessor'

  SummariserAgentProcessor(ChatClient.Builder builder) {
    super(builder)
  }

  @Override
  String getSystemPrompt(Exchange exchange) {
    "Reformat the following text for Telegram Markdown: add relevant emojis, " +
    "use *single asterisks* for bold key facts and place names (NOT double asterisks — Telegram uses *bold* not **bold**), " +
    "use _underscores_ for italic supplementary details, " +
    "and compress to at most 300 characters. Preserve key numbers, locations, and decisions."
  }

  @Override
  List<String> getToolNames(Exchange exchange) { [] }
}