package bg.dev.camel.routes

import bg.dev.camel.service.AiSentimentService
import org.apache.camel.builder.RouteBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 07 — AI Sentiment Analyser
 *
 * Concepts shown:
 *  - Combining File component + Spring AI service inside a Camel route
 *  - process() calling an AI service and setting result as header
 *  - Content-Based Router on AI-derived data
 *  - How Camel becomes the "glue" between files and intelligence
 *
 * Drop .txt files into input/sentiment/ — each gets classified and moved
 * to output/sentiment/positive|negative|neutral/.
 *
 * Run with: -Dspring.profiles.active=demo07
 */
@Component
@Profile('demo07')
class Demo07_AiSentimentRoute extends RouteBuilder {

  @Autowired
  AiSentimentService sentimentService

  @Override
  void configure() {

    from('file:input/sentiment?noop=true&include=.*\\.txt&delay=5000')
      .routeId('ai-sentiment')
      .log('🤖 Analysing: ${header.CamelFileName}')
      .convertBodyTo(String)
      .process { exchange ->
        String text = exchange.in.getBody(String)
        def result = sentimentService.analyze(text)
        exchange.in.setHeader('sentiment', result.sentiment)
        exchange.in.body = "=== SENTIMENT: ${result.sentiment} ===\n\n${text}".toString()
      }
      .log('   Result: ${header.sentiment}')
      .choice()
        .when(header('sentiment').isEqualTo('POSITIVE'))
          .log('   😊 → positive/')
          .to('file:output/sentiment/positive')
        .when(header('sentiment').isEqualTo('NEGATIVE'))
          .log('   😟 → negative/')
          .to('file:output/sentiment/negative')
        .otherwise()
          .log('   😐 → neutral/')
          .to('file:output/sentiment/neutral')
      .end()
  }
}
