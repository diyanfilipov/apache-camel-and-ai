package bg.dev.camel.processor.ai.agent

/**
 * Structured output model for one iteration of the ReAct (Reason + Act) loop.
 *
 * The LLM fills this in at each step:
 *   • thought      — the chain-of-thought reasoning before acting (always populated).
 *   • action       — CALL_TOOL when more data is needed, FINAL_ANSWER when ready to reply.
 *   • city/startDate/endDate — tool parameters; populated only when action == CALL_TOOL.
 *   • finalAnswer  — the user-facing response; populated only when action == FINAL_ANSWER.
 */
class ReActStep {

  enum Action { CALL_TOOL, FINAL_ANSWER }

  String thought

  Action action

  // Tool parameters — only when action == CALL_TOOL
  String city
  String startDate
  String endDate

  // User-facing response — only when action == FINAL_ANSWER
  String finalAnswer
}