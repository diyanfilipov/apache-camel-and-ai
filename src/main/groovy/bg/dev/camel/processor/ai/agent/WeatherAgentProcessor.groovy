package bg.dev.camel.processor.ai.agent

import org.apache.camel.Exchange
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

import java.time.LocalDate

@Component
class WeatherAgentProcessor extends AgentProcessor {

  static final String WEATHER_AGENT_PROCESSOR = 'weatherAgentProcessor'

  WeatherAgentProcessor(ChatClient.Builder builder) {
    super(builder)
  }

  @Override
  String getSystemPrompt(Exchange exchange) {
    "You are a helpful weather assistant. Use the checkWeather tool to answer weather questions. " +
      "Today is ${LocalDate.now()}. " +
      "When the user mentions relative dates ('this weekend', 'next week', '10 days'), " +
      "resolve them to exact ISO dates (YYYY-MM-DD) before calling the tool. " +
      "For current conditions, omit startDate and endDate."
  }

  @Override
  List<String> getToolNames(Exchange exchange) {
    ['checkWeather']
  }
}