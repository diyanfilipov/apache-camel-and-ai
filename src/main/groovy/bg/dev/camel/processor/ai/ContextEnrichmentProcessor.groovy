package bg.dev.camel.processor.ai

import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.springframework.stereotype.Component

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Stage 1 of the sequential pipeline: attaches contextual metadata to the exchange
 * before the intent router or any agent sees it.
 *
 * Headers set:
 *   EnrichedAt       — ISO-8601 timestamp of when enrichment ran (gives agents an accurate "now").
 *   UserTimezoneHint — IANA timezone derived from the user's Telegram language code.
 *                      Agents can use this to resolve relative dates like "this weekend" correctly.
 */
@Component
class ContextEnrichmentProcessor implements Processor {

  static final String CONTEXT_ENRICHMENT_PROCESSOR = 'contextEnrichmentProcessor'

  @Override
  void process(Exchange exchange) throws Exception {
    exchange.message.setHeader('EnrichedAt',
      ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))

    // Telegram sends the user's language code (BCP-47) — map it to an IANA timezone
    String langCode = exchange.message.getHeader('CamelTelegramFromLanguageCode', String) ?: 'en'
    exchange.message.setHeader('UserTimezoneHint', resolveTimezoneHint(langCode))
  }

  private static String resolveTimezoneHint(String langCode) {
    switch (langCode?.toLowerCase()) {
      case 'bg': return 'Europe/Sofia'
      case 'de': return 'Europe/Berlin'
      case 'fr': return 'Europe/Paris'
      case 'ja': return 'Asia/Tokyo'
      case 'zh': return 'Asia/Shanghai'
      case 'ru': return 'Europe/Moscow'
      default:   return 'UTC'
    }
  }
}