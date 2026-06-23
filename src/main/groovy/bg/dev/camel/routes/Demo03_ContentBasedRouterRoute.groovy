package bg.dev.camel.routes

import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 03 — Content-Based Router (EIP)
 *
 * Concepts shown:
 *  - Enterprise Integration Pattern: Content-Based Router
 *  - choice() / when() / otherwise() / end()
 *  - header-based routing predicate
 *  - process() for custom logic inside a route
 *
 * Run with: -Dspring.profiles.active=demo03
 */
@Component
@Profile('demo03')
class Demo03_ContentBasedRouterRoute extends RouteBuilder {

  private static final List<String> ORDER_TYPES =
    ['ELECTRONICS', 'CLOTHING', 'FOOD', 'DIGITAL', 'FURNITURE']

  private final Random random = new Random()

  @Override
  void configure() {

    // ── Simulate an incoming order stream ────────────────────────────────
    from('timer:orders?period=3000')
      .routeId('order-ingestion')
      .process { exchange ->
        String type = ORDER_TYPES[random.nextInt(ORDER_TYPES.size())]
        String orderId = 'ORD-' + (1000 + random.nextInt(9000))
        exchange.in.setHeader('orderType', type)
        exchange.in.body = orderId
      }
      .log('╔══ New order ${body} [type=${header.orderType}]')
      .to('direct:route-order')

    // ── Content-Based Router ─────────────────────────────────────────────
    from('direct:route-order')
      .routeId('content-based-router')
      .choice()
        .when(header('orderType').isEqualTo('ELECTRONICS'))
          .log('╠─ → Electronics warehouse  📦')
          .to('log:electronics?level=INFO&showBody=true')
        .when(header('orderType').isEqualTo('CLOTHING'))
          .log('╠─ → Clothing warehouse     👗')
          .to('log:clothing?level=INFO&showBody=true')
        .when(header('orderType').isEqualTo('FOOD'))
          .log('╠─ → Food depot (priority!) 🍎')
          .to('log:food?level=WARN&showBody=true')
        .when(header('orderType').isEqualTo('DIGITAL'))
          .log('╠─ → Instant digital delivery 💾')
          .to('log:digital?level=INFO&showBody=true')
        .otherwise()
          .log('╠─ → General warehouse      🏭')
          .to('log:general?level=INFO&showBody=true')
      .end()
      .log('╚══ Routing complete for ${body}')
  }
}
