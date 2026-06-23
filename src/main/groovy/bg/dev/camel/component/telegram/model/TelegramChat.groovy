package bg.dev.camel.component.telegram.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.Canonical

@Canonical
@JsonIgnoreProperties(ignoreUnknown = true)
class TelegramChat {
  long id
  String type              // private | group | supergroup | channel
  String title             // for groups and channels

  @JsonProperty('first_name') String firstName
  String username
}
