package bg.dev.camel.routes.ai.telegram

import bg.dev.camel.processor.ai.agent.AgentResultAggregationStrategy
import bg.dev.camel.processor.ai.agent.OrchestratorSynthesisProcessor
import bg.dev.camel.processor.ai.agent.SupplyChainAgentProcessor
import bg.dev.camel.processor.ai.agent.WeatherAgentProcessor
import bg.dev.camel.processor.ai.agent.WeatherReActStepProcessor
import bg.dev.camel.service.WeatherService
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile('demo14')
class Demo14_AgentRoutes extends RouteBuilder {

  private final WeatherService weatherService
  private final AgentResultAggregationStrategy agentResultAggregationStrategy

  Demo14_AgentRoutes(WeatherService weatherService,
                     AgentResultAggregationStrategy agentResultAggregationStrategy) {
    this.weatherService = weatherService
    this.agentResultAggregationStrategy = agentResultAggregationStrategy
  }

  @Override
  void configure() {
    onException(Exception)
      .handled(true)
      .process { exchange ->
        Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception)
        String raw = "${ex.class.simpleName}: ${ex.message ?: ''}"
        exchange.in.setHeader('_safeErrMsg', raw.replaceAll(/(?<=\/bot)[^\/]+(?=\/)/, '[REDACTED]'))
      }
      .log('⚠ [demo14] ${header._safeErrMsg}')

    // ── Named agent sub-routes ────────────────────────────────────────────
    from('direct:invokeWeatherAgent')
      .routeId('agent-weather')
      .process(WeatherAgentProcessor.WEATHER_AGENT_PROCESSOR)

    from('direct:invokeSupplyChainAgent')
      .routeId('agent-supply-chain')
      .process(SupplyChainAgentProcessor.SUPPLY_CHAIN_AGENT_PROCESSOR)

    // ── Explicit ReAct loop — Reason + Act cycle visible in the route ─────
    // loopDoWhile re-evaluates the predicate before every iteration:
    //   • WeatherReActStepProcessor asks the LLM: CALL_TOOL or FINAL_ANSWER?
    //   • CALL_TOOL  → execute checkWeather directly, append result to ReActObservations.
    //   • FINAL_ANSWER → processor sets the body; condition becomes false; loop exits.
    // Max 5 iterations prevents runaway loops on edge-case queries.
    from('direct:invokeWeatherAgentReact')
      .routeId('agent-weather-react')
      .log('🔄 Starting ReAct loop for weather query: \${body}')
      .setHeader(WeatherReActStepProcessor.HEADER_REACT_ACTION,       constant('START'))
      .setHeader(WeatherReActStepProcessor.HEADER_REACT_ITERATION,    constant(0))
      .setHeader(WeatherReActStepProcessor.HEADER_REACT_OBSERVATIONS, constant(''))
      .loopDoWhile(simple("\${header.${WeatherReActStepProcessor.HEADER_REACT_ACTION}} != 'FINAL_ANSWER' && \${header.${WeatherReActStepProcessor.HEADER_REACT_ITERATION}} < 5"))
        .log('🤔 ReAct iteration \${header.' + WeatherReActStepProcessor.HEADER_REACT_ITERATION + '}: reasoning about the query')
        .process(WeatherReActStepProcessor.WEATHER_REACT_STEP_PROCESSOR)
        .choice()
          .when(header(WeatherReActStepProcessor.HEADER_REACT_ACTION).isEqualTo('CALL_TOOL'))
            .log('⚙️ ReAct Act: calling checkWeather for city=\${header.' + WeatherReActStepProcessor.HEADER_REACT_CITY + '}')
            .process { exchange ->
              String result = weatherService.checkWeather(
                exchange.message.getHeader(WeatherReActStepProcessor.HEADER_REACT_CITY,       String),
                exchange.message.getHeader(WeatherReActStepProcessor.HEADER_REACT_START_DATE, String),
                exchange.message.getHeader(WeatherReActStepProcessor.HEADER_REACT_END_DATE,   String)
              )
              int    iter = exchange.message.getHeader(WeatherReActStepProcessor.HEADER_REACT_ITERATION,    Integer) ?: 1
              String prev = exchange.message.getHeader(WeatherReActStepProcessor.HEADER_REACT_OBSERVATIONS, String)  ?: ''
              exchange.message.setHeader(WeatherReActStepProcessor.HEADER_REACT_OBSERVATIONS,
                "${prev}\n[step ${iter}] checkWeather → ${result}")
            }
        .end()
      .end()
      .log('✅ ReAct loop complete after \${header.' + WeatherReActStepProcessor.HEADER_REACT_ITERATION + '} iteration(s)')

    // ── Orchestrator fanout — multicast to both agents in parallel, then synthesize ──
    from('direct:invokeOrchestratorFanout')
      .routeId('agent-orchestrator-fanout')
      .log('🧩 Orchestrator: fanning out to Weather and SupplyChain agents in parallel')
      .setHeader('OrchestratorMode', constant(true))
      .multicast(agentResultAggregationStrategy)
        .parallelProcessing()
        .to('direct:invokeWeatherAgent', 'direct:invokeSupplyChainAgent')
      .end()
      .log('🔗 Orchestrator: synthesizing parallel agent results')
      .process(OrchestratorSynthesisProcessor.ORCHESTRATOR_SYNTHESIS_PROCESSOR)
  }
}