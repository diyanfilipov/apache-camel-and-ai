package bg.dev.camel

import org.junit.jupiter.api.Test
import org.apache.camel.test.spring.junit5.CamelSpringBootTest
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@CamelSpringBootTest
@ActiveProfiles('demo12')
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Demo12_SpringAiToolsTest {

  @LocalServerPort
  int port

  WebTestClient webTestClient

  @BeforeEach
  void setup() {
    webTestClient = WebTestClient.bindToServer()
      .baseUrl("http://localhost:${port}")
      .build()
  }

  @Test
  void testAssistantEndpointWithCamelTool_InStock() {
    webTestClient.get()
      .uri(uriBuilder -> uriBuilder
        .path("/assistant")
        .queryParam("prompt", "Hey, can we ship SKU-99 immediately? What's the status?")
        .build())
      .exchange()
      .expectStatus().isOk()
  }

  @Test
  void testAssistantEndpointWithCamelTool_OutOfStock() {
    webTestClient.get()
      .uri(uriBuilder -> uriBuilder
        .path("/assistant")
        .queryParam("prompt", "A customer wants to buy 50 units of SKU-99. Do we have enough, and if not, what is our shortfall?")
        .build())
      .exchange()
      .expectStatus().isOk()
  }

}