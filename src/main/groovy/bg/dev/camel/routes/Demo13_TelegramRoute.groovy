package bg.dev.camel.routes

import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 13 — Telegram Bot + AI (custom devbg-telegram Camel component)
 *
 * Concepts shown:
 *  - Custom Camel component (devbg-telegram:): bidirectional — consumer + producer
 *  - Webhook-driven consumer: Telegram updates arrive as Camel exchanges
 *  - Content-Based Router on TelegramMessageType: text vs unsupported types
 *  - AI-powered reply via camel-openai (Ollama or any OpenAI-compatible API)
 *  - Reply routing: TelegramChatId header propagates sender context to the producer
 *  - Built-in simulator so the demo runs without a live bot token
 *
 * Full flow (real Telegram bot):
 *   User sends message → Telegram Bot API → POST /devbg-telegram/webhook
 *     → TelegramWebhookController → TelegramWebhookRouter → TelegramConsumer
 *       → from('devbg-telegram:webhook') → AI → devbg-telegram:send → User
 *
 * Simulator flow (no bot token required):
 *   timer:tg-sim → direct:tg-ai-reply → openai → file:output/telegram
 *
 * To run with a real Telegram bot:
 *   1. Open Telegram → @BotFather → /newbot → copy the token
 *   2. Set TELEGRAM_AUTH_TOKEN=<your-bot-token>
 *   3. Expose port 8080:  ngrok http 8080
 *   4. Register the webhook:
 *        curl -X POST "https://api.telegram.org/bot<TOKEN>/setWebhook" \
 *             -d "url=https://<ngrok-id>.ngrok.io/devbg-telegram/webhook"
 *   5. Run: -Dspring.profiles.active=demo13
 *
 * Simulator only (no env vars needed):
 *   -Dspring.profiles.active=demo13
 */
@Component
@Profile('demo13')
class Demo13_TelegramRoute extends RouteBuilder {

  static final List<String> SIM_MESSAGES = [
    'What is Apache Camel and what problem does it solve? 2 sentences.',
    'Explain Enterprise Integration Patterns (EIPs) in 3 sentences.',
    'How does Apache Camel integrate with AI models? Give a practical example.',
  ]

  @Override
  void configure() {

    // ── Error handling ────────────────────────────────────────────────────
    onException(Exception)
      .handled(true)
      .log('⚠ [demo13] ${exception.class.simpleName}: ${exception.message}')

    // ── Route A: Receive real Telegram messages (webhook consumer) ────────
    from('devbg-telegram:webhook?authToken={{demo.telegram.auth-token}}')
      .routeId('tg-inbound')
      .log('📱 Telegram [${header.TelegramMessageType}] from ${header.TelegramFromFirstName} (chat ${header.TelegramChatId})')
      .choice()
      .when(header('TelegramMessageType').isEqualTo('text'))
        .setBody(header('TelegramMessageText'))
        .to('direct:tg-ai-reply')
      .otherwise()
        .log('   ↳ Unsupported message type: ${header.TelegramMessageType} — ignoring')
      .end()

    // ── Route B: AI reply pipeline ────────────────────────────────────────
    from('direct:tg-ai-reply')
      .routeId('tg-ai-reply')
      .log('🤔 Asking AI: ${body}')
      .to('openai:chat-completion' +
        '?baseUrl={{demo.ollama.base-url}}' +
        '&model={{demo.ollama.model}}' +
        '&apiKey={{demo.ollama.api-key}}')
      .log('🤖 AI answer: ${body}')
      .to('direct:tg-deliver')

    // ── Route C: Deliver — Telegram when chat known, else file ────────────
    from('direct:tg-deliver')
      .routeId('tg-deliver')
      .choice()
      .when(header('TelegramChatId').isNotNull())
        .to('devbg-telegram:send?authToken={{demo.telegram.auth-token}}')
        .log('✅ Reply sent to Telegram chat ${header.TelegramChatId}')
      .otherwise()
        .log('📝 [sim] Answer: ${body}')
        .setHeader('CamelFileName', simple('tg-answer-${exchangeProperty.CamelTimerCounter}.txt'))
        .to('file:output/telegram')
        .log('   ✓ Saved to output/telegram/tg-answer-${exchangeProperty.CamelTimerCounter}.txt')
      .end()

    // ── Route D: Simulator ────────────────────────────────────────────────
    //    Fires 3 questions 12 s apart; no TelegramChatId → output goes to file.
//        from('timer:tg-sim?period=12000&repeatCount=3&includeMetadata=true')
//            .routeId('tg-simulator')
//            .process { exchange ->
//                long idx = exchange.getProperty('CamelTimerCounter', Long) ?: 0L
//                exchange.in.body = SIM_MESSAGES[(int)(idx % SIM_MESSAGES.size())]
//            }
//            .log('🎭 [sim] Simulating Telegram message #${exchangeProperty.CamelTimerCounter}: ${body}')
//            .to('direct:tg-ai-reply')
  }
}
