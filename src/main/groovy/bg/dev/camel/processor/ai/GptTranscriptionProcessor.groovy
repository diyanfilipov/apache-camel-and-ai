package bg.dev.camel.processor.ai

import groovy.util.logging.Slf4j
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component

@Slf4j
@Component
class GptTranscriptionProcessor implements Processor {

  static final String GPT_TRANSCRIPTION_PROCESSOR = 'gptTranscriptionProcessor'

  private final OpenAiAudioTranscriptionModel transcriptionModel

  GptTranscriptionProcessor(OpenAiAudioTranscriptionModel transcriptionModel) {
    this.transcriptionModel = transcriptionModel
  }

  @Override
  void process(Exchange exchange) throws Exception {
    try {
      byte[] audio = exchange.in.getBody(byte[])
      // Spring AI resolves filename from the resource; ByteArrayResource uses its description as filename
      def audioResource = new ByteArrayResource(audio) {
        @Override String getFilename() { 'voice.ogg' }
      }
      String transcribed = transcriptionModel
        .call(new AudioTranscriptionPrompt(audioResource))
        .getResult().output
      log.info("GPT heard: {}", transcribed)
      exchange.in.body = transcribed
    } catch (Exception e) {
      log.warn("[demo14] GPT transcription failed: ${e.message}")
      exchange.in.setHeader(Exchange.EXCEPTION_CAUGHT, e)
      exchange.in.body = "Sorry, I couldn't transcribe your voice message. Please send a text message instead."
    }
  }
}