package bg.dev.camel.processor

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.component.telegram.model.IncomingMessage
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Slf4j
@Component
class TelegramFileDownloadProcessor implements Processor {

  static final String TELEGRAM_FILE_DOWNLOAD_PROCESSOR = 'telegramFileDownloadProcessor'

  @Value('${demo.telegram.auth-token}')
  private String botToken

  @Override
  void process(Exchange exchange) throws Exception {
    String filePath = ''
    String fileType = 'voice'

    try {
      // Step 1: Extract file ID from voice or audio message
      IncomingMessage msg = exchange.in.getBody(IncomingMessage)
      String fileId = msg.voice?.fileId ?: msg.audio?.fileId
      if (fileId == null) {
        fileType = 'photo'
        fileId = msg.photo.last().fileId
      }

      HttpClients.createDefault().withCloseable { client ->
        // Step 2: Resolve download path via Telegram's getFile API
        def getFileJson = client.execute(new HttpGet("https://api.telegram.org/bot${botToken}/getFile?file_id=${fileId}")) { response ->
          new JsonSlurper().parseText(EntityUtils.toString(response.entity))
        }

        filePath = getFileJson?.result?.file_path

        // Step 3: Download the raw audio binary from Telegram's CDN
        exchange.in.body = client.execute(new HttpGet("https://api.telegram.org/file/bot${botToken}/${filePath}")) { response ->
          if (response.code != 200) {
            throw new IOException("Telegram file download failed: HTTP ${response.code} for path '${filePath}'")
          }
          EntityUtils.toByteArray(response.entity)
        }
      }
    } catch (Exception e) {
      log.warn("${fileType.capitalize()} file download failed for path ${filePath} — file may have expired. ${e.message}")
      exchange.in.setHeader(Exchange.EXCEPTION_CAUGHT, e)
      exchange.in.body = "Sorry, I couldn't retrieve your ${fileType} message — the file may have expired. Please try sending it again."
    }
  }
}