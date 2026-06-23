package bg.dev.camel.processor.ai.agent

import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.component.springai.tools.spec.CamelToolExecutorCache
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.tool.ToolCallback
import org.springframework.beans.factory.annotation.Autowired

import java.time.LocalDate

abstract class AgentProcessor implements Processor {

  @Autowired(required = false)
  private MessageChatMemoryAdvisor memoryAdvisor

  private final ChatClient chatClient

  AgentProcessor(ChatClient.Builder builder) {
    chatClient = builder.build()
  }

  abstract String getSystemPrompt(Exchange exchange)
  abstract List<String> getToolNames(Exchange exchange)

  @Override
  void process(Exchange exchange) throws Exception {

    ToolCallback[] toolCallbacks = getToolNames(exchange)
      .collectMany { toolName ->
        CamelToolExecutorCache.getInstance().getTools().get(toolName).collect { it.toolCallback }
      }
      .toArray(new ToolCallback[0])

    String chatId = exchange.message.getHeader('CamelTelegramChatId', String) ?: 'default'

    String userText = exchange.message.getBody(String) ?: exchange.message.body?.toString() ?: ''

    def promptSpec = chatClient.prompt()
      .system(getSystemPrompt(exchange))
      .user(userText)
      .tools(toolCallbacks)

    if (memoryAdvisor) {
      promptSpec = promptSpec
        .advisors(memoryAdvisor)
        .advisors { spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId) }
    }

    exchange.message.setBody(promptSpec.call().content())
  }
}
