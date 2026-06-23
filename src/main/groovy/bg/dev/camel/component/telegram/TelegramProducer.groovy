package bg.dev.camel.component.telegram

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.camel.Exchange
import org.apache.camel.support.DefaultProducer
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

/**
 * Camel producer for 'devbg-telegram:send'.
 *
 * send
 *   Sends a text message via the Telegram Bot API.
 *   Body    → the text to send
 *   Header  → TelegramChatId (required) — the target chat
 *   After success, exchange body is replaced with the Telegram API response map.
 */
@Slf4j
class TelegramProducer extends DefaultProducer {

  private final RestTemplate restTemplate = new RestTemplate()
  private final JsonSlurper slurper = new JsonSlurper()

  TelegramProducer(TelegramEndpoint endpoint) {
    super(endpoint)
  }

  @Override
  void process(Exchange exchange) throws Exception {
    def ep = (TelegramEndpoint) getEndpoint()
    switch (ep.command) {
      case 'send':
        sendMessage(exchange, ep)
        break
      default:
        throw new IllegalArgumentException(
          "Unknown devbg-telegram: command '${ep.command}'. Use 'send'."
        )
    }
  }

  // ── send ─────────────────────────────────────────────────────────────────

  private void sendMessage(Exchange exchange, TelegramEndpoint ep) {
    def chatId = exchange.in.getHeader('TelegramChatId', String)

    if (!chatId) throw new IllegalStateException(
      "TelegramChatId header is required for devbg-telegram:send"
    )

    def text = exchange.in.getBody(String)
    def payload = [chat_id: chatId, text: text, parse_mode: 'Markdown']

    log.debug('devbg-telegram:send → chatId={} text={}', chatId, text?.take(60))
    def url = "${ep.apiBaseUrl}/bot${ep.authToken}/sendMessage"
    def response = post(url, payload)
    exchange.in.body = response
  }

  // ── HTTP helper ───────────────────────────────────────────────────────────

  private Map post(String url, Map payload) {
    def headers = new HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON

    def request = new HttpEntity<>(JsonOutput.toJson(payload), headers)
    try {
      def raw = restTemplate.postForEntity(url, request, String)
      slurper.parseText(raw.body) as Map
    } catch (HttpClientErrorException e) {
      throw new RuntimeException("Telegram API HTTP error ${e.statusCode}: ${e.responseBodyAsString}", e)
    }
  }
}
