package bg.dev.camel.config

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Optional per-user conversation memory for demo14 agents.
 *
 * Enabled by default. Disable via:
 *   demo14.memory.enabled=false
 *
 * When active, both WeatherAgentProcessor and SupplyChainAgentProcessor share a
 * single in-memory store keyed by Telegram chatId, so the LLM can refer back to
 * earlier turns in the same conversation regardless of which agent handles each
 * message.
 */
@Configuration
@ConditionalOnProperty(name = 'demo14.memory.enabled', havingValue = 'true', matchIfMissing = true)
class Demo14MemoryConfig {

  @Bean
  MessageChatMemoryAdvisor demo14MemoryAdvisor(
      @Value('${demo14.memory.window-size:20}') int windowSize) {

    def chatMemory = MessageWindowChatMemory.builder()
      .chatMemoryRepository(new InMemoryChatMemoryRepository())
      .maxMessages(windowSize)
      .build()
    MessageChatMemoryAdvisor.builder(chatMemory).build()
  }
}