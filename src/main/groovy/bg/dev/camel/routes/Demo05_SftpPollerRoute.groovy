package bg.dev.camel.routes

import org.apache.camel.builder.RouteBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 05 — SFTP Poller
 *
 * Concepts shown:
 *  - camel-ftp component in SFTP mode
 *  - Externalising endpoint config via @Value / application.yml
 *  - knownHostsFile=false — convenient for demo/dev environments
 *  - move vs delete vs noop strategies
 *  - Connecting real infrastructure (SFTP server) via Camel
 *
 * Run with: -Dspring.profiles.active=demo05
 */
@Component
@Profile('demo05')
class Demo05_SftpPollerRoute extends RouteBuilder {

  @Value('${demo.sftp.host:localhost}')
  String host

  @Value('${demo.sftp.port:2222}')
  int port

  @Value('${demo.sftp.username:demo}')
  String username

  @Value('${demo.sftp.password:demo}')
  String password

  @Value('${demo.sftp.directory:upload}')
  String directory

  @Override
  void configure() {
    String uri = "sftp://${username}@${host}:${port}/${directory}" +
      '?password=' + password +
      '&delay=10000' +
      '&delete=false' +
      '&noop=true' +
      '&knownHostsFile=false' +
      '&strictHostKeyChecking=no'

    from(uri)
      .routeId('sftp-poller')
      .log('── SFTP download: ${header.CamelFileName} (${header.CamelFileSize} bytes)')
      .convertBodyTo(String)
      .log('   Content preview: ${body.length() > 120 ? body.substring(0, 120) : body}')
      .to('file:output/sftp-received?fileName=${date:now:yyyyMMdd_HHmmss}_${header.CamelFileName}')
      .log('── Saved to output/sftp-received/')
  }
}
