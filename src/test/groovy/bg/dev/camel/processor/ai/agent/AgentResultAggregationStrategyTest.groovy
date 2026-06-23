package bg.dev.camel.processor.ai.agent

import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import org.junit.jupiter.api.Test

class AgentResultAggregationStrategyTest {

  AgentResultAggregationStrategy strategy = new AgentResultAggregationStrategy()
  DefaultCamelContext ctx = new DefaultCamelContext()

  @Test
  void 'returns incoming exchange unchanged when old is null (first in multicast)'() {
    def first = new DefaultExchange(ctx)
    first.in.body = 'Weather: sunny in Sofia.'

    assert strategy.aggregate(null, first).is(first)
  }

  @Test
  void 'combines two agent bodies with separator'() {
    def old = new DefaultExchange(ctx)
    old.in.body = 'Weather: sunny in Sofia.'
    def incoming = new DefaultExchange(ctx)
    incoming.in.body = 'Inventory: GEAR-99X has 5 units.'

    def result = strategy.aggregate(old, incoming)
    assert result.in.body == "Weather: sunny in Sofia.\n\n---\n\nInventory: GEAR-99X has 5 units."
  }

  @Test
  void 'preserves headers from the first exchange so chatId survives aggregation'() {
    def old = new DefaultExchange(ctx)
    old.in.body = 'Weather result'
    old.in.setHeader('CamelTelegramChatId', '99887766')
    def incoming = new DefaultExchange(ctx)
    incoming.in.body = 'Supply chain result'

    def result = strategy.aggregate(old, incoming)
    assert result.in.getHeader('CamelTelegramChatId') == '99887766'
  }

  @Test
  void 'handles null body on either exchange without throwing'() {
    def old = new DefaultExchange(ctx)
    old.in.body = null
    def incoming = new DefaultExchange(ctx)
    incoming.in.body = 'Only supply chain responded.'

    def result = strategy.aggregate(old, incoming)
    assert result.in.body == "\n\n---\n\nOnly supply chain responded."
  }
}