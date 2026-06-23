package bg.dev.camel.routes

import org.apache.camel.AggregationStrategy
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 04 — Aggregator (EIP)
 *
 * Concepts shown:
 *  - Enterprise Integration Pattern: Aggregator
 *  - aggregate() with a correlation expression
 *  - Completion conditions: completionSize and completionTimeout
 *  - Custom AggregationStrategy: how to merge exchanges
 *  - split() to fan back out after aggregation
 *
 * Run with: -Dspring.profiles.active=demo04
 */
@Component
@Profile('demo04')
class Demo04_AggregatorRoute extends RouteBuilder {

  private final Random random = new Random()

  @Override
  void configure() {

    // ── Sensor reading producer ──────────────────────────────────────────
    from('timer:sensor?period=1000')
      .routeId('sensor-producer')
      .process { exchange ->
        double temp = 18 + random.nextDouble() * 12
        exchange.in.body = String.format('%.2f°C', temp)
        String sensor = random.nextBoolean() ? 'SENSOR-A' : 'SENSOR-B'
        exchange.in.setHeader('sensorId', sensor)
      }
      .log('  [${header.sensorId}] reading: ${body}')
      .to('direct:aggregate-readings')

    // ── Aggregator: collect 5 readings per sensor then forward ───────────
    from('direct:aggregate-readings')
      .routeId('sensor-aggregator')
      .aggregate(header('sensorId'), new ReadingListAggregator())
      .completionSize(5)
      .completionTimeout(15_000)
      .log('══ Batch ready for ${header.sensorId}: ${body}')
      .to('log:batch-processor?showBody=true&level=INFO')
  }

  static class ReadingListAggregator implements AggregationStrategy {

    @Override
    Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
      String incoming = newExchange.in.getBody(String)
      if (oldExchange == null) {
        newExchange.in.body = incoming
        return newExchange
      }
      String accumulated = oldExchange.in.getBody(String)
      newExchange.in.body = accumulated + ' | ' + incoming
      newExchange
    }
  }
}
