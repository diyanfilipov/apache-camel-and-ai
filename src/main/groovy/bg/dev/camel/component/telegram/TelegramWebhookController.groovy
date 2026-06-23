package bg.dev.camel.component.telegram

import bg.dev.camel.component.telegram.model.TelegramUpdate
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import java.util.concurrent.CompletableFuture

/**
 * Receives Telegram Bot API webhook callbacks.
 *
 * Telegram pushes one update per POST to this endpoint after you register
 * the webhook URL via the Bot API:
 *   POST https://api.telegram.org/bot{TOKEN}/setWebhook
 *   { "url": "https://<your-host>/devbg-telegram/webhook",
 *     "secret_token": "<TELEGRAM_SECRET_TOKEN>" }
 *
 * We return HTTP 200 immediately and dispatch asynchronously so that
 * AI route latency doesn't block the HTTP thread (Telegram re-delivers on timeout).
 *
 * For local development:
 *   ngrok http 8080
 * then call setWebhook with https://<id>.ngrok.io/devbg-telegram/webhook
 */
@Slf4j
@RestController
@RequestMapping('/devbg-telegram')
class TelegramWebhookController {

  @Autowired
  TelegramWebhookRouter webhookRouter

  @Value('${demo.telegram.secret-token:}')
  String configuredSecretToken

  // ── POST: incoming updates ────────────────────────────────────────────────

  @PostMapping('/webhook')
  ResponseEntity<?> receiveUpdate(
    @RequestBody TelegramUpdate update,
    @RequestHeader(name = 'X-Telegram-Bot-Api-Secret-Token', required = false) String secretToken
  ) {
    if (configuredSecretToken && secretToken != configuredSecretToken) {
      log.warn('Telegram webhook rejected — secret token mismatch')
      return ResponseEntity.status(403).body('Forbidden')
    }
    // Dispatch asynchronously — Telegram expects 200 quickly or it retries
    CompletableFuture.runAsync {
      if (update.message) {
        log.debug('Telegram inbound message from {}', update.message.from?.firstName)
        webhookRouter.dispatch(update)
      }
    }
    ResponseEntity.ok().build()
  }

  // ── GET: health check ─────────────────────────────────────────────────────

  @GetMapping('/status')
  ResponseEntity<Map> status() {
    ResponseEntity.ok([status: 'ok', consumers: webhookRouter.consumers.size()])
  }
}
