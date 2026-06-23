package bg.dev.camel

import bg.dev.camel.component.telegram.TelegramWebhookRouter
import bg.dev.camel.component.telegram.model.TelegramChat
import bg.dev.camel.component.telegram.model.TelegramMessage
import bg.dev.camel.component.telegram.model.TelegramUpdate
import bg.dev.camel.component.telegram.model.TelegramUser
import groovy.json.JsonOutput
import org.apache.camel.CamelContext
import org.apache.camel.test.spring.junit5.CamelSpringBootTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@CamelSpringBootTest
@ActiveProfiles('demo13')
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Demo13_TelegramRouteTest {

  @LocalServerPort
  int port

  @Autowired
  TelegramWebhookRouter webhookRouter

  @Autowired
  CamelContext camelContext

  WebTestClient client

  @BeforeEach
  void setup() {
    client = WebTestClient.bindToServer().baseUrl("http://localhost:${port}").build()
  }

  // ── Status endpoint ───────────────────────────────────────────────────────

  @Test
  void statusEndpointReturnsOk() {
    client.get()
      .uri('/devbg-telegram/status')
      .exchange()
      .expectStatus().isOk()
      .expectBody()
      .jsonPath('$.status').isEqualTo('ok')
  }

  // ── Event delivery (POST) ─────────────────────────────────────────────────

  @Test
  void webhookPostWithTextMessageReturns200() {
    def payload = [
      update_id: 123456789,
      message  : [
        message_id: 1,
        from      : [id: 12345678, first_name: 'Test', username: 'testuser', is_bot: false],
        chat      : [id: 12345678, type: 'private', first_name: 'Test'],
        date      : 1718000000,
        text      : 'Hello bot!'
      ]
    ]

    client.post()
      .uri('/devbg-telegram/webhook')
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(JsonOutput.toJson(payload))
      .exchange()
      .expectStatus().isOk()
  }

  @Test
  void webhookPostWithNoMessageReturns200() {
    // Updates without a message field (e.g. edited_message, channel_post) should be silently ignored
    def payload = [update_id: 987654321]

    client.post()
      .uri('/devbg-telegram/webhook')
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(JsonOutput.toJson(payload))
      .exchange()
      .expectStatus().isOk()
  }

  // ── TelegramWebhookRouter ─────────────────────────────────────────────────

  @Test
  void routerDispatchWithNoConsumersIsSafeNoOp() {
    def router = new TelegramWebhookRouter()
    def update = new TelegramUpdate(
      updateId: 1,
      message: new TelegramMessage(
        messageId: 1,
        from: new TelegramUser(id: 123, firstName: 'Test'),
        chat: new TelegramChat(id: 123, type: 'private'),
        text: 'hi'
      )
    )
    router.dispatch(update)   // must not throw
  }

  // ── Camel route registration ──────────────────────────────────────────────

  @Test
  void allThreeDemo13RoutesAreRegistered() {
    def ids = camelContext.routes*.id as Set
    assert ids.containsAll(['tg-inbound', 'tg-ai-reply', 'tg-deliver'])
  }
}
