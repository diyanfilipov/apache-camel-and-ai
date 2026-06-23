package bg.dev.camel.routes.ai.telegram

import bg.dev.camel.service.WeatherService
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile('demo14')
class Demo14_SpringAiToolRoutes extends RouteBuilder {

  private final WeatherService weatherService

  Demo14_SpringAiToolRoutes(WeatherService weatherService) {
    this.weatherService = weatherService
  }

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

    // ── Tool 0: Weather check — calls Open-Meteo API (no key required) ───
    from('spring-ai-tools:checkWeather' +
          '?tags=checkWeather' +
          '&description=Gets current weather or a multi-day forecast for a city via Open-Meteo' +
          '&parameter.city=string' +
          '&parameter.city.description=The name of the city' +
          '&parameter.city.required=true' +
          '&parameter.startDate=string' +
          '&parameter.startDate.description=Forecast start date (YYYY-MM-DD). Omit for current conditions.' +
          '&parameter.startDate.required=false' +
          '&parameter.endDate=string' +
          '&parameter.endDate.description=Forecast end date (YYYY-MM-DD). Defaults to startDate when omitted.' +
          '&parameter.endDate.required=false')
      .routeId('tool-check-weather')
      .log('🌤️ Tool Execution: Fetching weather for ${header.city} (${header.startDate} → ${header.endDate})')
      .process { exchange ->
        exchange.in.body = weatherService.checkWeather(
          exchange.in.getHeader('city', String),
          exchange.in.getHeader('startDate', String),
          exchange.in.getHeader('endDate', String)
        )
      }

    // ── Tool 1: Inventory check — queries real MySQL parts table via Camel SQL ──
    from('spring-ai-tools:checkInventory' +
          '?tags=checkInventory' +
          '&description=Check the spare-parts inventory database for a specific part by its part ID' +
          '&parameter.partId=string' +
          '&parameter.partId.description=The part identifier, e.g. GEAR-99X or MOTOR-15C' +
          '&parameter.partId.required=true')
      .routeId('tool-check-inventory')
      .log('🔎 Tool Execution: Querying MySQL parts inventory for part ${header.partId}')
      .to('sql:SELECT part_id, name, stock, location, unit_cost FROM parts WHERE part_id = :#${header.partId}?dataSource=#dataSource&outputType=SelectList')
      .process { ex -> if (ex.in.body == null) ex.in.body = [] }
      .marshal().json()
      .convertBodyTo(String)

    // ── Tool 2: B2B procurement — calls mock REST supplier API via Camel HTTP ──
    from('spring-ai-tools:orderFromSupplier' +
          '?tags=orderFromSupplier' +
          '&description=Places an emergency B2B purchase order for a part' +
          '&parameter.partId=string' +
          '&parameter.partId.description=The part ID to order from the external supplier' +
          '&parameter.partId.required=true')
      .routeId('tool-order-supplier')
      .log('📦 Tool Execution: Calling Supplier REST API via Camel HTTP for part ${header.partId}')
      .setHeader(Exchange.HTTP_METHOD, constant('POST'))
      .setHeader('Content-Type', constant('application/json'))
      .setBody(simple('{"partId":"${header.partId}"}'))
      .to('http://localhost:{{server.port:8080}}/mock/supplier-order')
      .convertBodyTo(String)

    // ── Tool 3: Alert broadcast — publishes to Apache Kafka cluster ───────
    from('spring-ai-tools:broadcastEmergencyAlert' +
          '?tags=broadcastEmergencyAlert' +
          '&description=Publishes a high-priority operational alert to corporate streams' +
          '&parameter.alertMessage=string' +
          '&parameter.alertMessage.description=The alert message to broadcast' +
          '&parameter.alertMessage.required=true')
      .routeId('tool-broadcast-alert')
      .log('🤖 Tool Execution: Pushing alert to Apache Kafka: ${header.alertMessage}')
      .setBody(header('alertMessage'))
      .doTry()
        .to('kafka:operational-alerts?brokers=localhost:9092')
      .doCatch(Exception)
        .log('⚠ [demo14] Kafka unavailable — alert logged only: ${body}')
      .end()
      .setBody(constant('{"status":"STREAM_NOTIFIED"}'))

    // ── Tool 4: Agent-to-agent delegation — SupplyChain delegates to Weather Agent ──
    from('spring-ai-tools:callWeatherAgent' +
          '?tags=callWeatherAgent' +
          '&description=Delegates a natural-language weather question to the specialist Weather Agent and returns its answer. Use when a supply-chain decision depends on weather conditions at a specific location.' +
          '&parameter.query=string' +
          '&parameter.query.description=A natural-language weather question, e.g. Will it rain in Sofia this weekend?' +
          '&parameter.query.required=true')
      .routeId('tool-call-weather-agent')
      .log('🤝 Tool Execution: SupplyChain → WeatherAgent delegation for: ${header.query}')
      .setBody(header('query'))
      .to('direct:invokeWeatherAgent')
      .convertBodyTo(String)
  }
}