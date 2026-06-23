package bg.dev.camel.component.telegram.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.Canonical

/**
 * A single inbound Telegram message.
 * Type is inferred from which field is non-null (text, photo, sticker, etc.).
 * Only 'text' type is fully handled; others are surfaced via the TelegramMessageType header.
 */
@Canonical
@JsonIgnoreProperties(ignoreUnknown = true)
class TelegramMessage {

  @JsonProperty('message_id') long messageId
  TelegramUser from

  TelegramChat chat

  long date               // unix seconds
  String text             // non-null only for text messages
}
