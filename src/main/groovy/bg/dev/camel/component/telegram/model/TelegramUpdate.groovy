package bg.dev.camel.component.telegram.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.Canonical

/** Top-level object Telegram POSTs to our webhook for every event. */
@Canonical
@JsonIgnoreProperties(ignoreUnknown = true)
class TelegramUpdate {

  @JsonProperty('update_id') long updateId
  TelegramMessage message
  // edited_message, channel_post, callback_query, etc. are intentionally ignored
}
