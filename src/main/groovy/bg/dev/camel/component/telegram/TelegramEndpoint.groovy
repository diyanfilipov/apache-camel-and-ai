package bg.dev.camel.component.telegram

import org.apache.camel.Consumer
import org.apache.camel.Processor
import org.apache.camel.Producer
import org.apache.camel.spi.UriEndpoint
import org.apache.camel.spi.UriParam
import org.apache.camel.spi.UriPath
import org.apache.camel.support.DefaultEndpoint

/**
 * Represents a configured 'devbg-telegram:' endpoint.
 *
 * Commands:
 *   webhook — consumer; receives inbound updates pushed by Telegram
 *   send    — producer; sends a text message to a Telegram chat
 *
 * Headers populated by the consumer (devbg-telegram:webhook):
 *   TelegramChatId          — chat ID to reply to (String)
 *   TelegramFromId          — sender's Telegram user ID
 *   TelegramFromFirstName   — sender's first name
 *   TelegramFromUsername    — sender's username (may be null)
 *   TelegramMessageText     — text body (only for type=text messages)
 *   TelegramMessageType     — text | other
 *   TelegramMessageId       — Telegram message ID
 *
 * Headers consumed by the producer (devbg-telegram:send):
 *   TelegramChatId — required; the target chat to send to
 */
@UriEndpoint(
  firstVersion = '1.0.0',
  scheme = 'devbg-telegram',
  title = 'Telegram Bot API',
  syntax = 'devbg-telegram:command'
)
class TelegramEndpoint extends DefaultEndpoint {

  @UriPath(description = 'Operation: webhook (consumer) | send (producer)')
  String command

  @UriParam(label = 'security', secret = true, description = 'Telegram Bot API token from @BotFather')
  String authToken

  @UriParam(label = 'security', description = 'Optional secret token validated against X-Telegram-Bot-Api-Secret-Token header')
  String secretToken

  @UriParam(description = 'Telegram Bot API base URL', defaultValue = 'https://api.telegram.org')
  String apiBaseUrl = 'https://api.telegram.org'

  TelegramEndpoint(String uri, TelegramComponent component) {
    super(uri, component)
  }

  @Override
  Producer createProducer() throws Exception {
    new TelegramProducer(this)
  }

  @Override
  Consumer createConsumer(Processor processor) throws Exception {
    def consumer = new TelegramConsumer(this, processor)
    configureConsumer(consumer)
    consumer
  }
}
