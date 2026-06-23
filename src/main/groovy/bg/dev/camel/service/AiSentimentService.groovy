package bg.dev.camel.service

import groovy.transform.Immutable
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

@Service
class AiSentimentService {

  private final ChatClient chatClient

  AiSentimentService(ChatClient.Builder builder) {
    this.chatClient = builder
      .defaultSystem('''
        You are a precise sentiment classifier.
        Respond with EXACTLY one word: POSITIVE, NEGATIVE, or NEUTRAL.
        No explanation, no punctuation, just the single word.
        ''')
      .build()
  }

  SentimentResult analyze(String text) {
    String raw = chatClient.prompt()
      .user("Classify the sentiment of this text:\n\n${text}")
      .call()
      .content()

    String sentiment = raw == null ? 'NEUTRAL' : raw.strip().toUpperCase()

    if (sentiment != 'POSITIVE' && sentiment != 'NEGATIVE') {
      sentiment = 'NEUTRAL'
    }

    new SentimentResult(text, sentiment)
  }

  @Immutable
  static class SentimentResult {
    String text
    String sentiment
  }
}
