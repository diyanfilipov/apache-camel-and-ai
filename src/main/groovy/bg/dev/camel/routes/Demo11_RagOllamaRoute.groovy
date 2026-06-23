package bg.dev.camel.routes

import bg.dev.camel.service.OllamaRagService
import groovy.json.JsonSlurper
import org.apache.camel.builder.RouteBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 11 — Ollama + RAG with Public APIs (no API keys required)
 *
 * What it shows:
 *  - RAG (Retrieval-Augmented Generation) pattern with a local LLM
 *  - Camel as ETL backbone: fetching, shaping, and routing data from the web
 *  - Live ingestion from 3 public REST APIs at startup (GitHub + Wikipedia ×2)
 *  - In-memory vector store with cosine-similarity search (OllamaRagService)
 *  - Two-phase Camel pipeline: Phase 1 ingest → Phase 2 Q&A
 *
 * Public APIs used (zero authentication):
 *  - https://api.github.com/repos/apache/camel   — live repo metadata (stars, topics…)
 *  - https://en.wikipedia.org/api/rest_v1/page/summary/Apache_Camel
 *  - https://en.wikipedia.org/api/rest_v1/page/summary/Enterprise_Integration_Patterns  (title-case — lowercase gives 404)
 *
 * Requires Ollama running locally with both models pulled:
 *   ollama pull llama3.2
 *   ollama pull nomic-embed-text
 *
 * Run: ./scripts/run-demo.sh 11
 */
@Component
@Profile('demo11')
class Demo11_RagOllamaRoute extends RouteBuilder {

  @Autowired
  OllamaRagService ragService

  static final List<String> QUESTIONS = [
    'What is Apache Camel and what problems does it solve?',
    'What are Enterprise Integration Patterns?',
    'How many GitHub stars does Apache Camel have?',
    'What is the license of the Apache Camel project?',
    'What is the history and origin of Enterprise Integration Patterns?',
  ]

  @Override
  void configure() {

    onException(Exception)
      .handled(true)
      .log('⚠ [Demo11] ${exception.class.simpleName}: ${exception.message}')

    // ── Phase 1: Ingest knowledge base from public APIs (runs once at startup) ──

    from('timer:rag-ingest?repeatCount=1&delay=1000')
      .routeId('rag-phase1-ingest')
      .log('📚 Phase 1 — Fetching knowledge base from public APIs...')
      .to('direct:ingest-github')
      .to('direct:ingest-wiki-camel')
      .to('direct:ingest-wiki-eip')
      .process { exchange ->
        exchange.in.body = "✅ Knowledge base ready — ${ragService.storeSize()} documents loaded. Q&A starts in ~10 s..."
      }
      .log('${body}')

    // Sub-route: GitHub — Apache Camel repo metadata
    from('direct:ingest-github')
      .removeHeaders('Accept,User-Agent')
      .setHeader('CamelHttpMethod', constant('GET'))
      .setHeader('Accept', constant('application/vnd.github.v3+json'))
      .setHeader('User-Agent', constant('CamelRagDemo/1.0'))
      .setHeader('Accept-Encoding', constant('identity'))
      .to('https://api.github.com/repos/apache/camel')
      .process { exchange ->
        def j = new JsonSlurper().parseText(exchange.in.getBody(String))
        def text = """\
Apache Camel GitHub Repository
Full name   : ${j.full_name}
Description : ${j.description}
Stars       : ${j.stargazers_count}
Forks       : ${j.forks_count}
Open issues : ${j.open_issues_count}
Language    : ${j.language}
License     : ${j.license?.name}
Topics      : ${j.topics?.join(', ')}
Homepage    : ${j.homepage}
""".trim()
        ragService.ingest(text, 'github:apache/camel')
        exchange.in.body = "GitHub repo metadata (${text.length()} chars)"
      }
      .log('  ✓ ${body}')

    // Sub-route: Wikipedia — Apache Camel article
    from('direct:ingest-wiki-camel')
      .removeHeaders('Accept,User-Agent')
      .setHeader('CamelHttpMethod', constant('GET'))
      .setHeader('User-Agent', constant('CamelRagDemo/1.0'))
      .setHeader('Accept-Encoding', constant('identity'))
      .to('https://en.wikipedia.org/api/rest_v1/page/summary/Apache_Camel')
      .process { exchange ->
        def j = new JsonSlurper().parseText(exchange.in.getBody(String))
        def text = "${j.title}\n\n${j.extract}"
        ragService.ingest(text, 'wikipedia:Apache_Camel')
        exchange.in.body = "Wikipedia/Apache_Camel (${text.length()} chars)"
      }
      .log('  ✓ ${body}')

    // Sub-route: Wikipedia — Enterprise Integration Patterns
    from('direct:ingest-wiki-eip')
      .removeHeaders('Accept,User-Agent')
      .setHeader('CamelHttpMethod', constant('GET'))
      .setHeader('User-Agent', constant('CamelRagDemo/1.0'))
      .setHeader('Accept-Encoding', constant('identity'))
      .to('https://en.wikipedia.org/api/rest_v1/page/summary/Enterprise_Integration_Patterns')
      .process { exchange ->
        def j = new JsonSlurper().parseText(exchange.in.getBody(String))
        def text = "${j.title}\n\n${j.extract}"
        ragService.ingest(text, 'wikipedia:Enterprise_Integration_Patterns')
        exchange.in.body = "Wikipedia/EIP (${text.length()} chars)"
      }
      .log('  ✓ ${body}')

    // ── Phase 2: RAG Q&A loop (5 questions, one every 15 s) ──────────────────

    from('timer:rag-query?period=15000&repeatCount=5&delay=12000&includeMetadata=true')
      .routeId('rag-phase2-query')
      .process { exchange ->
        long idx = exchange.getProperty('CamelTimerCounter', Long) ?: 0L
        exchange.in.body = QUESTIONS[(int) (idx % QUESTIONS.size())]
      }
      .log('❓ Q[${exchangeProperty.CamelTimerCounter}]: ${body}')
      .process { exchange ->
        String question = exchange.in.body as String
        exchange.in.body = ragService.ragQuery(question)
      }
      .log('🦙 A: ${body}')
      .setHeader('CamelFileName', simple('rag-answer-${exchangeProperty.CamelTimerCounter}.txt'))
      .to('file:output/rag-answers')
      .log('   ✓ Saved → output/rag-answers/rag-answer-${exchangeProperty.CamelTimerCounter}.txt')
  }
}
