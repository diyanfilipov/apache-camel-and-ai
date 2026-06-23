package bg.dev.camel.routes

import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 10 — Local LLM via Ollama (camel-openai component)
 *
 * Concepts shown:
 *  - camel-openai: Camel's own native OpenAI component (separate from Spring AI)
 *  - Pointing it at local Ollama via baseUrl — works with any OpenAI-compatible server
 *  - No cloud API key required; everything runs on the local machine
 *  - Two AI integration styles side-by-side: Spring AI (demos 07-09) vs Camel-native (demo 10)
 *
 * Requires Ollama running with the target model pulled:
 *   ollama pull llama3.2
 *
 * Run with: -Dspring.profiles.active=demo10
 */
@Component
@Profile('demo10')
class Demo10_OllamaRoute extends RouteBuilder {

  static final List<String> QUESTIONS = [
    'What is the Splitter EIP in Apache Camel? Answer in 2 sentences.',
    'What is the Content-Based Router EIP in Apache Camel? Answer in 2 sentences.',
    'What is the Aggregator EIP in Apache Camel? Answer in 2 sentences.',
    'How does Apache Camel differ from a message broker like RabbitMQ? Answer in 2 sentences.',
    'What is a Camel Route and what problem does it solve? Answer in 2 sentences.',
  ]

  @Override
  void configure() {

    onException(Exception)
      .handled(true)
      .log('⚠ Ollama error (is Ollama running?): ${exception.message}')

    from('timer:ollama-qa?period=8000&repeatCount=5&includeMetadata=true')
      .routeId('ollama-local-llm')
      .process { exchange ->
        long idx = exchange.getProperty('CamelTimerCounter', Long) ?: 0L
        exchange.in.body = QUESTIONS[(int)(idx % QUESTIONS.size())]
      }
      .log('❓ Q[${exchangeProperty.CamelTimerCounter}]: ${body}')
      .to('openai:chat-completion?baseUrl={{demo.ollama.base-url}}&model={{demo.ollama.model}}&apiKey={{demo.ollama.api-key}}')
      .log('🦙 A: ${body}')
      .setHeader('CamelFileName', simple('answer-${exchangeProperty.CamelTimerCounter}.txt'))
      .to('file:output/answers')
      .log('   ✓ Saved to output/answers/answer-${exchangeProperty.CamelTimerCounter}.txt')
  }
}
