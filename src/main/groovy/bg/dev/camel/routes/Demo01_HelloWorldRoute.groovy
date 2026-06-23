package bg.dev.camel.routes

import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 01 — Hello World
 *
 * Concepts shown:
 *  - from() / to() / log() — the simplest possible route
 *  - Timer component: generate events on a schedule
 *  - setBody / setHeader: manipulate the exchange
 *  - Simple expression language: ${body}, ${header.X}
 *
 * Run with: -Dspring.profiles.active=demo01
 */
@Component
@Profile('demo01')
class Demo01_HelloWorldRoute extends RouteBuilder {

  @Override
  void configure() {

    // ── Route A: dead-simple ticker ─────────────────────────────────────
    from('timer:hello?period=5000')
      .routeId('hello-world')
      .setBody(constant('Hello from Apache Camel! 🐪'))
      .log('>>> ${body}')

    // ── Route B: enriched ticker showing header & Simple EL ─────────────
    from('timer:enriched?period=8000&includeMetadata=true')
      .routeId('enriched-hello')
      .setBody(constant('Camel is alive'))
      .setHeader('ts', simple('${date:now:HH:mm:ss}'))
      .setHeader('count', simple('${exchangeProperty.CamelTimerCounter}'))
      .log('Message #${header.count} | body=\'${body}\' | time=${header.ts}')
  }
}
