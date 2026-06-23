package bg.dev.camel.component.telegram.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.Canonical

@Canonical
@JsonIgnoreProperties(ignoreUnknown = true)
class TelegramUser {
  long id

  @JsonProperty('is_bot')
  boolean bot

  @JsonProperty('first_name')
  String firstName

  @JsonProperty('last_name')
  String lastName

  String username

  @JsonProperty('language_code')
  String languageCode
}
