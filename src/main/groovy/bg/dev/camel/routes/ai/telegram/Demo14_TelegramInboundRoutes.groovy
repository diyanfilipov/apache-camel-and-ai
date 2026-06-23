package bg.dev.camel.routes.ai.telegram

import bg.dev.camel.processor.TelegramFileDownloadProcessor
import bg.dev.camel.processor.ai.GptTranscriptionProcessor
import bg.dev.camel.processor.ai.GptVisionProcessor
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 14 — Telegram Bot + Multi-Agent AI Orchestration with Spring AI Tools
 *
 * ── Camel patterns demonstrated ──────────────────────────────────────────────
 *
 *  Content-Based Router (multi-modal input)
 *    Incoming Telegram messages are split into three lanes:
 *    • Voice/audio  → TelegramFileDownloadProcessor downloads the OGG file, then
 *                     GPTTranscriptionProcessor (OpenAI GPT) returns the transcript.
 *    • Photo        → TelegramFileDownloadProcessor downloads the JPEG bytes, then
 *                     GptVisionProcessor (Gemini Vision) returns a text description.
 *    • Text         → passed directly to the intent router.
 *    All three paths converge at direct:invokeSpringAIAgent.
 *
 *  Sequential pipeline with named stages (NEW)
 *    direct:invokeSpringAIAgent is no longer a single opaque step — it is a visible
 *    five-stage pipeline, each stage a named direct: sub-route:
 *      Stage 1 — direct:enrichContext      : attach user timezone and current timestamp.
 *      Stage 2 — IntentRouterProcessor     : LLM-based structured intent classification.
 *      Stage 3 — agent dispatch            : route to the appropriate specialist agent.
 *      Stage 4 — direct:retryWithBroaderContext : conditional retry on low-confidence replies.
 *      Stage 5 — direct:formatForTelegram  : trim + optional LLM compression to ≤ 200 chars.
 *
 *  LLM-based intent classification (structured output)
 *    IntentRouterProcessor uses Spring AI structured output to return an IntentClassification
 *    object {agentType, secondaryAgent, confidence, ambiguous} instead of raw text.
 *    AgentType header values: WEATHER | PROCUREMENT | GENERAL_QA
 *    • NeedsClarification=true  — confidence < 0.7 or ambiguous=true; bot asks user to rephrase
 *    • SecondaryAgentType set   — query spans both domains; triggers orchestrator fanout
 *    Few-shot examples in the prompt reduce misclassification on ambiguous phrasing.
 *
 *  Explicit ReAct loop via loopDoWhile (NEW)
 *    direct:invokeWeatherAgentReact replaces the Spring AI black-box tool loop for weather
 *    queries with a visible Camel loopDoWhile:
 *      1. WeatherReActStepProcessor asks the LLM: call checkWeather tool OR final answer?
 *      2. If CALL_TOOL  → execute weatherService directly, append the result to ReActObservations.
 *      3. If FINAL_ANSWER → set exchange body and exit the loop.
 *    The iterative Reason + Act cycle is now legible in the route diagram.
 *
 *  Chain-of-thought with a summariser step (NEW)
 *    After each agent responds, direct:formatForTelegram checks response length.
 *    Responses > 300 chars are passed to direct:summariseForTelegram, which makes a second
 *    lightweight LLM call (SummariserAgentProcessor) to compress the text.
 *    This shows sequential agent chaining at the route level:
 *      Agent (reasoning) → Agent (compression) → telegram:bots
 *
 *  Conditional retry on low-confidence responses (NEW)
 *    After an agent returns, the exchange body is inspected for uncertainty phrases
 *    ("I'm not sure", "I don't have information", "I don't know"). If detected, the route
 *    dispatches to direct:retryWithBroaderContext, which prepends the current timestamp
 *    and user timezone to the original query and re-invokes the same agent — demonstrating
 *    a loopDoWhile / choice re-entry pattern without explicit looping.
 *
 *  Dedicated AI agents (Orchestrator → Worker)
 *    Each agent has its own ChatClient, focused system prompt, and minimal tool set.
 *    WeatherAgentProcessor  → tool: checkWeather (used by orchestrator fanout + delegation)
 *    SupplyChainAgentProcessor → tools: checkInventory, orderFromSupplier,
 *                                       broadcastEmergencyAlert, callWeatherAgent*
 *    (* callWeatherAgent is stripped when running inside the orchestrator fanout, see below)
 *
 *  Orchestrator fanout (Camel multicast() EIP)
 *    COMPOUND intents are sent to direct:invokeOrchestratorFanout, which:
 *    1. Sets OrchestratorMode=true to prevent duplicate weather calls.
 *    2. multicast().parallelProcessing() fans out to both agent sub-routes simultaneously.
 *    3. AgentResultAggregationStrategy collects both responses (joined by ---).
 *    4. OrchestratorSynthesisProcessor makes a final LLM call to produce one coherent reply.
 *
 *  Agent-to-agent delegation
 *    When running in PROCUREMENT mode (solo), SupplyChainAgent can call callWeatherAgent —
 *    a Spring AI tool that routes to direct:invokeWeatherAgent, delegating the sub-query to
 *    the weather specialist and returning its answer as a tool result.
 *
 *  Per-user conversation memory
 *    MessageChatMemoryAdvisor (MessageWindowChatMemory, window=20) is injected into every
 *    AgentProcessor and keyed by the CamelTelegramChatId header. Each user therefore gets
 *    independent conversation history. Disable with demo14.memory.enabled=false.
 *
 * ── Spring AI Tool routes (invoked dynamically by the LLM) ───────────────────
 *
 *  checkWeather            → Camel HTTP — Open-Meteo API (no key required)
 *                            Returns current conditions or a multi-day forecast JSON.
 *  checkInventory          → Camel SQL  — SELECT from the MySQL `parts` table.
 *                            Returns stock level, location, and unit cost for a part ID.
 *  orderFromSupplier       → Camel HTTP — POST to /mock/supplier-order (REST B2B).
 *                            Returns a purchase-order confirmation number.
 *  broadcastEmergencyAlert → Camel Kafka — publishes to `operational-alerts` topic.
 *                            Degrades gracefully when the broker is unavailable.
 *  callWeatherAgent        → direct:invokeWeatherAgent — agent-to-agent delegation.
 *                            Accepts a natural-language query; returns the weather
 *                            agent's full answer. Stripped from tool list in COMPOUND mode.
 *
 * ── Full message flow ────────────────────────────────────────────────────────
 *
 *  from('telegram:bots')
 *    → CBR: voice → processVoice (GPT-Transcribe) → invokeSpringAIAgent
 *           photo → processVision (GPT-Vision) → invokeSpringAIAgent
 *           text  → invokeSpringAIAgent
 *             Stage 1: enrichContext (timezone, timestamp)
 *             Stage 2: IntentRouterProcessor → structured classification
 *               NeedsClarification  → clarification reply (low confidence / ambiguous)
 *               GENERAL_QA          → graceful out-of-scope reply
 *               SecondaryAgentType  → direct:invokeOrchestratorFanout
 *                                       → multicast() parallel → [WeatherAgent, SupplyChainAgent]
 *                                       → AgentResultAggregationStrategy (joins with ---)
 *                                       → OrchestratorSynthesisProcessor (LLM synthesis)
 *               WEATHER             → direct:invokeWeatherAgentReact (explicit ReAct loop)
 *               PROCUREMENT         → direct:invokeSupplyChainAgent
 *             Stage 4: retryWithBroaderContext (if agent expressed low confidence)
 *             Stage 5: formatForTelegram → summariseForTelegram (if > 200 chars)
 *             → to('telegram:bots') — reply sent back to user
 *
 * ── Setup for a real bot ─────────────────────────────────────────────────────
 *   1. Open Telegram → @BotFather → /newbot → copy the token
 *   2. Expose locally: ngrok http 8080
 *   3. Register webhook: curl -X POST https://api.telegram.org/bot{TOKEN}/setWebhook \
 *        -d '{"url":"https://{ngrok-id}.ngrok.io/telegraf/{TOKEN}"}'
 *   4. export TELEGRAM_AUTH_TOKEN=<token>
 *   5. Run: -Dspring.profiles.active=demo14
 */
@Component
@Profile('demo14')
class Demo14_TelegramInboundRoutes extends RouteBuilder {

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

    from('telegram:bots')
      .routeId('tg-inbound')
      .log('📱 Telegram message received')
      .choice()
        .when(simple('${body.voice} != null || ${body.audio} != null'))
          .to('direct:processVoice')
        .when(simple('${body.photo} != null'))
          .to('direct:processVision')
        .otherwise()
          .to('direct:invokeSpringAiPipeline')
      .end()

    from('direct:processVoice')
      .routeId('process-voice')
      .log('🎙️ Received voice note from Telegram. Sending to GPT Transcribe...')
      .process(TelegramFileDownloadProcessor.TELEGRAM_FILE_DOWNLOAD_PROCESSOR)
      .choice()
        .when(header(Exchange.EXCEPTION_CAUGHT).isNotNull())
          .to('telegram:bots')
          .stop()
        .otherwise()
          .process(GptTranscriptionProcessor.GPT_TRANSCRIPTION_PROCESSOR)
          .choice()
            .when(header(Exchange.EXCEPTION_CAUGHT).isNotNull())
              .to('telegram:bots')
              .stop()
          .end()
      .end()
      .to('direct:invokeSpringAiPipeline')

    from('direct:processVision')
      .routeId('process-vision')
      .log('📸 Received image from Telegram. Sending to GPT-4o Vision...')
      .process(TelegramFileDownloadProcessor.TELEGRAM_FILE_DOWNLOAD_PROCESSOR)
      .choice()
        .when(header(Exchange.EXCEPTION_CAUGHT).isNotNull())
          .to('telegram:bots')
          .stop()
        .otherwise()
          .process(GptVisionProcessor.GPT_VISION_PROCESSOR)
          .choice()
            .when(header(Exchange.EXCEPTION_CAUGHT).isNotNull())
              .to('telegram:bots')
              .stop()
          .end()
      .end()
      .to('direct:invokeSpringAiPipeline')
  }
}