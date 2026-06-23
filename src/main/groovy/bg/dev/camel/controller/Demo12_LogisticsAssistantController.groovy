package bg.dev.camel.controller

import groovy.util.logging.Slf4j
import org.apache.camel.component.springai.tools.spec.CamelToolExecutorCache
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.tool.ToolCallback
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@Slf4j
@RestController
@Profile('demo12')
class Demo12_LogisticsAssistantController {

  private final ChatClient chatClient

  Demo12_LogisticsAssistantController(ChatClient.Builder builder) {
    this.chatClient = builder
      .defaultSystem("You are a helpful logistics assistant. Use the tools provided to check warehouse data.")
      .build()
  }

  @GetMapping("/assistant")
  String askAssistant(@RequestParam("prompt") String prompt) {
    ToolCallback[] toolCallbacks = getToolCallbacks()

    String response = this.chatClient.prompt(prompt)
      .tools(toolCallbacks)
      .call()
      .content()

    log.info("Response: ${response}")
    response
  }

  @GetMapping(value = "/assistant/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  Flux<String> streamAssistant(@RequestParam("prompt") String prompt) {
    ToolCallback[] toolCallbacks = getToolCallbacks()

    this.chatClient.prompt(prompt)
      .tools(toolCallbacks)
      .stream()
      .content()
      .doOnNext { chunk -> log.info("Stream chunk: {}", chunk) }
      .doOnComplete { log.info("Stream complete") }
  }

  private static ToolCallback[] getToolCallbacks() {
    CamelToolExecutorCache.getInstance()
      .getTools()
      .get("checkStock")
      .collect { it.toolCallback }
      .toArray(new ToolCallback[0])
  }
}