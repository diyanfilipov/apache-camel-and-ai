package bg.dev.camel

import bg.dev.camel.processor.ai.agent.AgentResultAggregationStrategy
import org.apache.camel.RoutesBuilder
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.test.junit5.CamelTestSupport
import org.junit.jupiter.api.Test

/**
 * Verifies the orchestrator multicast fanout (direct:invokeOrchestratorFanout) without
 * any AI, Telegram, Kafka, or database dependencies.
 *
 * Both agent sub-routes are replaced with lightweight stubs that return fixed strings,
 * and the synthesis processor is intercepted by mock:synthesis so no real LLM call occurs.
 * This isolates the Camel routing / aggregation logic from infrastructure concerns.
 */
class Demo14_OrchestrationRouteTest extends CamelTestSupport {

  @Override
  protected RoutesBuilder createRouteBuilder() {
    def strategy = new AgentResultAggregationStrategy()

    new RouteBuilder() {
      @Override
      void configure() {

        // ── Stub agent sub-routes — no LLM, no infra ─────────────────────
        from('direct:invokeWeatherAgent')
          .routeId('agent-weather')
          .setBody(constant('Weather: clear skies in Varna this weekend.'))

        from('direct:invokeSupplyChainAgent')
          .routeId('agent-supply-chain')
          .setBody(constant('Inventory: GEAR-99X has 5 units in stock at Warehouse B.'))

        // ── Orchestrator fanout — the route under test ────────────────────
        from('direct:invokeOrchestratorFanout')
          .routeId('agent-orchestrator-fanout')
          .setHeader('OrchestratorMode', constant(true))
          .multicast(strategy)
            .parallelProcessing()
            .to('direct:invokeWeatherAgent', 'direct:invokeSupplyChainAgent')
          .end()
          .to('mock:synthesis')  // captures synthesis input without calling the real LLM
      }
    }
  }

  @Test
  void 'fanout calls both agent routes and delivers combined body to synthesis'() {
    MockEndpoint synthMock = getMockEndpoint('mock:synthesis')
    synthMock.expectedMessageCount(1)

    template.sendBody('direct:invokeOrchestratorFanout',
      'Will it storm in Varna this weekend? And do we have GEAR-99X in stock?')

    synthMock.assertIsSatisfied()

    String combined = synthMock.receivedExchanges[0].in.body as String
    assert combined.contains('Weather: clear skies in Varna this weekend.')
    assert combined.contains('Inventory: GEAR-99X has 5 units in stock at Warehouse B.')
    assert combined.contains('---'), "Expected separator between agent results, got: $combined"
  }

  @Test
  void 'OrchestratorMode header is visible to the synthesis step'() {
    MockEndpoint synthMock = getMockEndpoint('mock:synthesis')
    synthMock.expectedMessageCount(1)

    template.sendBody('direct:invokeOrchestratorFanout', 'Compound query')

    synthMock.assertIsSatisfied()
    assert synthMock.receivedExchanges[0].in.getHeader('OrchestratorMode') == true
  }
}