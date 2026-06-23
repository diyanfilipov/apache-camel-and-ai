package bg.dev.camel.service

import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

@Service
class AiSummarizerService {

  private final ChatClient chatClient

  AiSummarizerService(ChatClient.Builder builder) {
    this.chatClient = builder
      .defaultSystem('''
        You are a technical document summariser.
        Always respond with exactly 3–5 bullet points.
        Each bullet starts with \'• \'.
        Be concise — one sentence per bullet.
      ''')
      .build()
  }

  String summarize(String content) {
    chatClient.prompt()
      .user("Summarise this document:\n\n${content}")
      .call()
      .content()
  }
}
