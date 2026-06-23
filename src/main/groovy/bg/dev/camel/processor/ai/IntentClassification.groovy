package bg.dev.camel.processor.ai

class IntentClassification {

  enum AgentType {
    WEATHER, PROCUREMENT, GENERAL_QA
  }

  /** Primary intent category for the user message. */
  AgentType agentType

  /** Non-null when the message genuinely requires both WEATHER and PROCUREMENT to answer. */
  AgentType secondaryAgent

  /** Classification certainty in range 0.0–1.0. */
  double confidence

  /** True when the query is ambiguous or could reasonably map to a different category. */
  boolean ambiguous
}