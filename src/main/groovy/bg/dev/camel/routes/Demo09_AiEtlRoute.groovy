package bg.dev.camel.routes

import bg.dev.camel.service.AiEnricherService
import org.apache.camel.builder.RouteBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 09 — AI-Powered ETL Pipeline
 *
 * Full pipeline: SFTP (or simulated) → CSV parse → AI enrich → MySQL
 *
 * Concepts shown:
 *  - Combining ALL previous concepts in one route: SFTP + split + AI + SQL
 *  - Camel as an orchestration layer: no framework-specific glue code
 *  - throttle() to avoid hammering the AI API during bulk loads
 *  - Error handling on individual records without stopping the batch
 *  - The power of Camel: what would take 200+ lines in plain Java = ~35 lines
 *
 * Run with: -Dspring.profiles.active=demo09
 * (Simulates SFTP fetch with a timer for the live demo)
 */
@Component
@Profile('demo09')
class Demo09_AiEtlRoute extends RouteBuilder {

  @Autowired
  AiEnricherService enricher

  @Override
  void configure() {

    onException(Exception)
      .handled(true)
      .maximumRedeliveries(2)
      .log('⚠ Record failed: ${body} — ${exception.message}')

    // ── Simulate: every 30s fetch a "CSV file" from SFTP ─────────────────
    // In production: replace timer with sftp://... endpoint
    from('timer:etl-trigger?period=30000&repeatCount=2')
      .routeId('ai-etl-pipeline')
      .log('🚀 ETL cycle starting...')
      .process { exchange ->
        exchange.in.body = '''
          Alice Nguyen,Staff Engineer,5 years at Google,Designed Spanner distributed lock service
          Bruno Martins,ML Engineer,PhD Stanford,Published 3 NeurIPS papers on LLM fine-tuning
          Chloe Osei,Platform Architect,Ex-Meta,Led migrating 50M users to k8s
          David Park,Senior SRE,AWS certified,Reduced P99 latency by 60% via async queuing
          Elena Kovač,Backend Developer,Kotlin expert,OSS maintainer camel-kotlin-dsl
          '''
      }
    // ── Split CSV into individual lines ──────────────────────────────
      .split(body().tokenize('\n'))
      .filter(simple('${body.trim().length} > 0'))
    // ── Throttle to 1 req/s — respect AI API rate limits ─────────
      .throttle(1).timePeriodMillis(1000)
      .log('  Enriching: ${body.split(\',\')[0]}')
    // ── AI enrichment via Spring AI ──────────────────────────────
      .process { exchange ->
        String csv = exchange.in.getBody(String)
        def enriched = enricher.enrich(csv)
        exchange.in.setHeaders(enriched)
        exchange.in.body = enriched
      }
      .log('    ✓ ${header.name} → ${header.role} | skills: ${header.skills}')
    // ── Upsert into MySQL ─────────────────────────────────────────
      .to('sql:INSERT INTO professionals (name, role, summary, skills, processed_at) ' +
        'VALUES (:#${header.name}, :#${header.role}, :#${header.summary}, :#${header.skills}, NOW()) ' +
        'ON DUPLICATE KEY UPDATE ' +
        '  summary = VALUES(summary), ' +
        '  skills  = VALUES(skills), ' +
        '  processed_at = NOW()')
      .end()
      .log('✅ ETL cycle complete — all records upserted')
  }
}
