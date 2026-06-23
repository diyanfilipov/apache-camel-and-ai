package bg.dev.camel.routes

import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 02 — File Processor
 *
 * Concepts shown:
 *  - File component: watch a directory for new files
 *  - convertBodyTo: type conversion built into Camel
 *  - transform + Simple/Tokenize expressions
 *  - Writing processed output to a different directory
 *  - noop=true: leave source files untouched (great for demos)
 *
 * Drop any .txt file into input/files/ and watch it process.
 * Run with: -Dspring.profiles.active=demo02
 */
@Component
@Profile('demo02')
class Demo02_FileProcessorRoute extends RouteBuilder {

  @Override
  void configure() {

    from('file:input/files?noop=true&delay=3000&include=.*\\.txt')
      .routeId('file-processor')
      .log('── Picked up file: ${header.CamelFileName} (${header.CamelFileSize} bytes)')
      .convertBodyTo(String)
      .process { exchange ->
        String content = exchange.in.getBody(String)
        long lines = content.readLines().size()
        exchange.in.setHeader('lineCount', lines)
      }
      .log('   Lines: ${header.lineCount}')
      .transform(body().regexReplaceAll('(?m)^(.+)$', '  >> $1'))
      .log('   Transformed content:\n${body}')
      .to('file:output/processed?fileName=${date:now:yyyyMMdd_HHmmss}_${header.CamelFileName}')
      .log('── Written to output/processed/')
  }
}
