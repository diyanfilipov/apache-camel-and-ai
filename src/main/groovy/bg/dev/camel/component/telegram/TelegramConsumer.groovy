package bg.dev.camel.component.telegram

import bg.dev.camel.component.telegram.model.TelegramUpdate
import groovy.util.logging.Slf4j
import org.apache.camel.Processor
import org.apache.camel.support.DefaultConsumer

/**
 * Camel consumer for 'devbg-telegram:webhook'.
 *
 * Registers with {@link TelegramWebhookRouter} on start so that
 * {@link TelegramWebhookController} can push inbound updates here.
 *
 * Telegram delivers one update per webhook POST. This consumer fires
 * ONE Camel exchange per update.
 *
 * Headers set on each exchange:
 *   TelegramChatId, TelegramFromId, TelegramFromFirstName,
 *   TelegramFromUsername, TelegramMessageText, TelegramMessageType, TelegramMessageId
 */
@Slf4j
class TelegramConsumer extends DefaultConsumer {

  TelegramConsumer(TelegramEndpoint endpoint, Processor processor) {
    super(endpoint, processor)
  }

  @Override
  protected void doStart() throws Exception {
    super.doStart()
    webhookRouter()?.register(this)
    log.info('TelegramConsumer started — registered with TelegramWebhookRouter')
  }

  @Override
  protected void doStop() throws Exception {
    webhookRouter()?.unregister(this)
    log.info('TelegramConsumer stopped — unregistered from TelegramWebhookRouter')
    super.doStop()
  }

  /**
   * Called once per inbound Telegram update.
   * @param update the TelegramUpdate received from the webhook
   */
  void dispatch(TelegramUpdate update) {
    def msg = update.message
    if (!msg) return

    def exchange = createExchange(true)
    try {
      exchange.in.body = update
      exchange.in.setHeader('TelegramChatId', msg.chat?.id?.toString())
      exchange.in.setHeader('TelegramFromId', msg.from?.id?.toString())
      exchange.in.setHeader('TelegramFromFirstName', msg.from?.firstName)
      exchange.in.setHeader('TelegramFromUsername', msg.from?.username)
      exchange.in.setHeader('TelegramMessageText', msg.text)
      exchange.in.setHeader('TelegramMessageType', msg.text != null ? 'text' : 'other')
      exchange.in.setHeader('TelegramMessageId', msg.messageId?.toString())

      getProcessor().process(exchange)

      if (exchange.exception) {
        getExceptionHandler().handleException('Error processing Telegram event', exchange, exchange.exception)
      }
    } catch (Exception e) {
      getExceptionHandler().handleException('Error dispatching Telegram event', exchange, e)
    } finally {
      doneUoW(exchange)
      releaseExchange(exchange, false)
    }
  }

  private TelegramWebhookRouter webhookRouter() {
    endpoint.camelContext.registry.lookupByNameAndType('telegramWebhookRouter', TelegramWebhookRouter)
  }
}
