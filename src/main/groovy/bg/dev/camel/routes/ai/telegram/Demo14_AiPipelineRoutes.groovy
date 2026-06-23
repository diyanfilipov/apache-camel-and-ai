package bg.dev.camel.routes.ai.telegram

import bg.dev.camel.processor.ai.ContextEnrichmentProcessor
import bg.dev.camel.processor.ai.IntentRouterProcessor
import bg.dev.camel.processor.ai.agent.SummariserAgentProcessor
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile('demo14')
class Demo14_AiPipelineRoutes extends RouteBuilder {

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

    // ── Sequential pipeline — five named stages ───────────────────────────
    from('direct:invokeSpringAiPipeline')
      .routeId('ai-pipeline')
      .log("🧠 Classifying intent for: \${body}")
      // Stage 1: save the original user query so Stage 4 can restore it on retry,
      //          then enrich with user timezone and current timestamp.
      .setHeader('OriginalUserQuery', body())
      .to('direct:enrichContext')
      // Stage 2: structured LLM call → sets AgentType, SecondaryAgentType,
      //          NeedsClarification, and Confidence headers.
      .process(IntentRouterProcessor.INTENT_ROUTER_PROCESSOR)
      .log("🔀 Intent classified as: \${header.${IntentRouterProcessor.HEADER_AGENT_TYPE}}, " +
            "second agent: \${header.${IntentRouterProcessor.HEADER_SECONDARY_AGENT_TYPE}}, " +
            "confidence: \${header.${IntentRouterProcessor.HEADER_CONFIDENCE}}, " +
            "needs clarification: \${header.${IntentRouterProcessor.HEADER_NEEDS_CLARIFICATION}}")
      // Stage 3: route to the appropriate agent; tag the exchange so Stage 4 knows
      //          whether an actual agent ran (vs. a canned clarification message).
      .choice()
        .when(header(IntentRouterProcessor.HEADER_NEEDS_CLARIFICATION).isEqualTo(true))
          .log('❓ Low-confidence classification — asking user to clarify')
          .setBody(constant('I need a bit more clarity. Could you specify whether your question is about weather conditions or supply chain / inventory operations?'))
        .when(header(IntentRouterProcessor.HEADER_AGENT_TYPE).isEqualTo('GENERAL_QA'))
          .log('🚫 Off-topic query — sending graceful out-of-scope reply')
          .setBody(constant('I can only help with weather forecasts and supply chain topics. Please ask me about weather conditions, inventory levels, or procurement.'))
        .when(header(IntentRouterProcessor.HEADER_SECONDARY_AGENT_TYPE).isNotNull())
          .log('🔀 Compound query — fanning out to both agents in parallel')
          .to('direct:invokeOrchestratorFanout')
          .setHeader('AgentInvoked', constant(true))
        .when(header(IntentRouterProcessor.HEADER_AGENT_TYPE).isEqualTo('WEATHER'))
          .log('🌤️ Handing off to Weather Agent (ReAct loop)')
          .to('direct:invokeWeatherAgentReact')
          .setHeader('AgentInvoked', constant(true))
        .otherwise()
          .log('📦 Handing off to Procurement Agent')
          .to('direct:invokeSupplyChainAgent')
          .setHeader('AgentInvoked', constant(true))
      .end()
      // Stage 4: conditional retry — if the agent admitted it doesn't know, re-invoke
      //          with richer context (timestamp + timezone from Stage 1).
      .process { exchange ->
        if (exchange.message.getHeader('AgentInvoked', Boolean)) {
          String body = exchange.message.getBody(String) ?: ''
          boolean lowConf = body.toLowerCase().with {
            contains("i'm not sure") || contains("i don't have information") || contains("i don't know")
          }
          exchange.message.setHeader('LowConfidenceResponse', lowConf)
        }
      }
      .choice()
        .when(header('LowConfidenceResponse').isEqualTo(true))
          .log('🔁 Low-confidence agent response — retrying with broader context')
          .to('direct:retryWithBroaderContext')
      .end()
      // Stage 5: format and optionally compress the final response for Telegram.
      .to('direct:formatForTelegram')
      .log('✅ Agent finished reasoning. Sending final response back to Telegram.')
      .to('telegram:bots')

    // ── Stage 1: Context enrichment ───────────────────────────────────────
    from('direct:enrichContext')
      .routeId('enrich-context')
      .log('🌐 Enriching exchange with user context')
      .process(ContextEnrichmentProcessor.CONTEXT_ENRICHMENT_PROCESSOR)

    // ── Stage 4: Conditional retry with broader context ───────────────────
    from('direct:retryWithBroaderContext')
      .routeId('retry-with-broader-context')
      .log('🔁 Injecting broader context and re-invoking agent')
      .process { exchange ->
        String enrichedAt = exchange.message.getHeader('EnrichedAt', String) ?: ''
        String tzHint     = exchange.message.getHeader('UserTimezoneHint', String) ?: 'UTC'
        String query      = exchange.message.getHeader('OriginalUserQuery', String) ?: exchange.message.getBody(String) ?: ''
        exchange.message.setBody("[Current time: ${enrichedAt}, timezone: ${tzHint}]\n${query}")
        exchange.message.setHeader('LowConfidenceResponse', false)
        exchange.message.setHeader('AgentInvoked', false)
      }
      .choice()
        .when(header(IntentRouterProcessor.HEADER_AGENT_TYPE).isEqualTo('WEATHER'))
          .to('direct:invokeWeatherAgentReact')
        .otherwise()
          .to('direct:invokeSupplyChainAgent')
      .end()

    // ── Stage 5: Format for Telegram — chain-of-thought with summariser ───
    from('direct:formatForTelegram')
      .routeId('format-for-telegram')
      .process { exchange ->
        String body = exchange.message.getBody(String) ?: ''
        exchange.message.setHeader('ResponseLength', body.length())
      }
      .log('📝 Formatting response for Telegram (\${header.ResponseLength} chars)')
      .choice()
        .when(simple('${header.ResponseLength} > 300'))
          .log('📉 Response exceeds 300 chars — chain-of-thought: summariser agent compresses it')
          .to('direct:summariseForTelegram')
      .end()

    from('direct:summariseForTelegram')
      .routeId('summarise-for-telegram')
      .log('🗜️ Summariser agent: compressing response to ≤ 300 chars')
      .process(SummariserAgentProcessor.SUMMARISER_AGENT_PROCESSOR)
  }
}