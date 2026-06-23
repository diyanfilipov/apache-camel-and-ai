package bg.dev.camel.routes

import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 06 — Database Integration
 *
 * Concepts shown:
 *  - camel-sql component: SELECT → split → UPDATE pattern
 *  - split() to process result rows individually
 *  - Named parameter binding with :#${header.X}
 *  - Transactional batch processing with repeatCount
 *  - Using the DB as both source and sink in one route
 *
 * The schema is applied by schema.sql on startup (see resources).
 * Run with: -Dspring.profiles.active=demo06
 */
@Component
@Profile('demo06')
class Demo06_DatabaseRoute extends RouteBuilder {

  @Override
  void configure() {

    // ── Poll unprocessed customers every 30 s, then stop after 3 cycles ──
    from('timer:db-poll?period=30000&repeatCount=3')
      .routeId('db-processor')
      .log('══ Starting DB processing cycle')
      .to('sql:SELECT id, name, email FROM customers WHERE processed = 0 LIMIT 5')
      .split(body())
      .log('  Processing: ${body[name]} <${body[email]}>')
      .process { exchange ->
        Map<String, Object> row = exchange.in.getBody(Map)
        exchange.in.setHeader('customerId', row.get('id'))
        String code = 'WC-' + Integer.toHexString(row.get('id').hashCode()).toUpperCase()
        exchange.in.setHeader('welcomeCode', code)
      }
      .to('sql:UPDATE customers SET processed = 1, welcome_code = :#${header.welcomeCode} ' +
        'WHERE id = :#${header.customerId}')
      .log('    ✓ Marked processed, code=${header.welcomeCode}')
      .end()
      .log('══ Cycle complete — processed ${header.CamelSqlUpdateCount} rows total')
  }
}
