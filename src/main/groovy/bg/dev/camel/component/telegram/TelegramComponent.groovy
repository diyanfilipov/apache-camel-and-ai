package bg.dev.camel.component.telegram

import org.apache.camel.Endpoint
import org.apache.camel.support.DefaultComponent
import org.springframework.stereotype.Component

/**
 * Custom Apache Camel component for the Telegram Bot API.
 *
 * URI patterns:
 *   from('devbg-telegram:webhook?authToken=...')          ← receive inbound messages
 *   to('devbg-telegram:send?authToken=...')               ← send a text message
 *
 * The Spring @Component name 'devbg-telegram' makes Camel auto-register this
 * under the 'devbg-telegram:' scheme.
 */
@Component('devbg-telegram')
class TelegramComponent extends DefaultComponent {

  @Override
  protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
    def endpoint = new TelegramEndpoint(uri, this)
    endpoint.command = remaining
    setProperties(endpoint, parameters)
    endpoint
  }
}
