package bg.dev.camel.component.telegram

import bg.dev.camel.component.telegram.model.TelegramUpdate
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Service

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Spring singleton that bridges the Spring MVC webhook controller to the Camel consumer.
 *
 * {@link TelegramWebhookController} calls {@link #dispatch} for each inbound update.
 * {@link TelegramConsumer} registers/unregisters via {@link #register}/{@link #unregister}.
 *
 * CopyOnWriteArrayList keeps dispatch lock-free on the HTTP thread.
 */
@Slf4j
@Service('telegramWebhookRouter')
class TelegramWebhookRouter {

  final List<TelegramConsumer> consumers = new CopyOnWriteArrayList<>()

  void register(TelegramConsumer consumer) {
    consumers.add(consumer)
    log.info('TelegramConsumer registered (active: {})', consumers.size())
  }

  void unregister(TelegramConsumer consumer) {
    consumers.remove(consumer)
    log.info('TelegramConsumer unregistered (active: {})', consumers.size())
  }

  void dispatch(TelegramUpdate update) {
    if (consumers.isEmpty()) {
      log.warn('Received Telegram update but no consumer is registered — ignoring')
      return
    }
    consumers.each { consumer ->
      try {
        consumer.dispatch(update)
      } catch (Exception e) {
        log.error('TelegramConsumer.dispatch threw: {}', e.message, e)
      }
    }
  }
}
