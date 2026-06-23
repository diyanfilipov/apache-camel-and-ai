package bg.dev.camel.processor.ai

import groovy.util.logging.Slf4j
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeTypeUtils

@Slf4j
@Component
class GptVisionProcessor implements Processor {

  static final String GPT_VISION_PROCESSOR = 'gptVisionProcessor'

  private final ChatModel chatModel

  GptVisionProcessor(ChatModel chatModel) {
    this.chatModel = chatModel
  }

  @Override
  void process(Exchange exchange) throws Exception {
    try {
      byte[] imageBytes = exchange.in.getBody(byte[])
      def media = new Media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(imageBytes))
      def message = UserMessage.builder()
        .text('Describe what you see in this image in the context of supply chain operations.')
        .media(media)
        .build()
      String description = chatModel.call(new Prompt([message]))
        .getResult().output.getText()
      log.info("Vision model described: {}", description)
      exchange.in.body = description
    } catch (Exception e) {
      log.warn("[demo14] Vision analysis failed: ${e.message}")
      exchange.in.setHeader(Exchange.EXCEPTION_CAUGHT, e)
      exchange.in.body = "Sorry, I couldn't analyse your image. Please send a text message instead."
    }
  }
}