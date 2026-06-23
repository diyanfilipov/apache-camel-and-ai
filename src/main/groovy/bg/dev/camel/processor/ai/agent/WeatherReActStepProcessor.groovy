package bg.dev.camel.processor.ai.agent

import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

import java.time.LocalDate

/**
 * One iteration of the explicit ReAct (Reason + Act) loop for the Weather Agent.
 *
 * This processor is called repeatedly inside a Camel loopDoWhile. On each call it:
 *   1. Builds a prompt from the original user query and any tool observations so far.
 *   2. Asks the LLM to decide: call the checkWeather tool OR produce a final answer.
 *   3. Writes the decision into headers so the route can dispatch accordingly.
 *
 * Headers read/written:
 *   body (String)           — original user query.
 *   ReActObservations       — newline-separated log of previous tool results.
 *   ReActIteration (int)    — incremented at the start of each invocation (route initialises to 0).
 *
 * Headers written:
 *   ReActAction             — 'CALL_TOOL' or 'FINAL_ANSWER'.
 *   ReActCity               — city name for checkWeather (CALL_TOOL only).
 *   ReActStartDate          — ISO date (CALL_TOOL only, may be null for current conditions).
 *   ReActEndDate            — ISO date (CALL_TOOL only, may be null).
 *
 * Body written:
 *   When action == FINAL_ANSWER, the body is replaced with the LLM's answer so the
 *   route can send it directly to Telegram after the loop exits.
 */
@Component
class WeatherReActStepProcessor implements Processor {

  static final String WEATHER_REACT_STEP_PROCESSOR = 'weatherReActStepProcessor'

  static final String HEADER_REACT_ACTION       = 'ReActAction'
  static final String HEADER_REACT_CITY         = 'ReActCity'
  static final String HEADER_REACT_START_DATE   = 'ReActStartDate'
  static final String HEADER_REACT_END_DATE     = 'ReActEndDate'
  static final String HEADER_REACT_OBSERVATIONS = 'ReActObservations'
  static final String HEADER_REACT_ITERATION    = 'ReActIteration'

  private final ChatClient chatClient

  WeatherReActStepProcessor(ChatClient.Builder builder) {
    chatClient = builder.build()
  }

  @Override
  void process(Exchange exchange) throws Exception {
    int iter = (exchange.message.getHeader(HEADER_REACT_ITERATION, Integer) ?: 0) + 1
    exchange.message.setHeader(HEADER_REACT_ITERATION, iter)

    String query        = exchange.message.getBody(String) ?: ''
    String observations = exchange.message.getHeader(HEADER_REACT_OBSERVATIONS, String) ?: ''

    ReActStep step = chatClient
      .prompt(buildPrompt(query, observations))
      .call()
      .entity(ReActStep)

    exchange.message.setHeader(HEADER_REACT_ACTION, step.action?.name() ?: 'FINAL_ANSWER')

    if (step.action == ReActStep.Action.CALL_TOOL) {
      exchange.message.setHeader(HEADER_REACT_CITY,       step.city)
      exchange.message.setHeader(HEADER_REACT_START_DATE, step.startDate)
      exchange.message.setHeader(HEADER_REACT_END_DATE,   step.endDate)
    } else {
      // Make the final answer available as the exchange body so no extra step is needed
      exchange.message.setBody(step.finalAnswer ?: query)
    }
  }

  private static String buildPrompt(String query, String observations) {
    """\
    You are a weather assistant operating inside a ReAct (Reason + Act) loop.
    Today's date is ${LocalDate.now()}.

    Reason about the user's query, then decide your NEXT action.

    User query: "${query}"
    ${observations ? "\nTool observations so far:\n${observations}" : ""}

    Rules:
    - Set action=CALL_TOOL when you need to fetch weather data.
      Populate city (required), startDate and endDate (YYYY-MM-DD, omit for current conditions).
    - Set action=FINAL_ANSWER only when you have enough information to fully answer the user.
      Populate finalAnswer with a clear, friendly reply.
    - Always fill in thought with your reasoning before acting.
    - Resolve relative dates ('this weekend', 'next Monday') to ISO dates before calling the tool.
    """.stripIndent()
  }
}