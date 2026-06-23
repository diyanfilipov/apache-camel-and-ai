package bg.dev.camel.service

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory RAG (Retrieval-Augmented Generation) service backed by Ollama's native HTTP API.
 *
 * Keeps a simple list of {text, source, embedding} tuples.
 * Uses cosine similarity for nearest-neighbour retrieval, then asks Ollama to synthesise
 * an answer from the top-k matching chunks.
 *
 * Calls Ollama's native REST API directly (not the OpenAI-compatible shim) so that
 * both /api/embed (nomic-embed-text) and /api/chat (llama3.x) work without any
 * extra Spring AI beans or dependency conflicts.
 */
@Service
class OllamaRagService {

  @Value('${demo.ollama.native-url:http://localhost:11434}')
  private String ollamaNativeUrl

  @Value('${demo.ollama.embed-model:nomic-embed-text}')
  private String embedModel

  @Value('${demo.ollama.model:llama3.2}')
  private String chatModel

  // Each entry: [text: String, source: String, embedding: List<Double>]
  private final List<Map> store = new CopyOnWriteArrayList<>()

  void ingest(String text, String source) {
    def embedding = embed(text)
    store << [text: text, source: source, embedding: embedding]
  }

  int storeSize() { store.size() }

  String ragQuery(String question) {
    if (store.isEmpty()) return '⚠ Knowledge base is empty — Phase 1 ingestion may still be running.'

    def contexts = findSimilar(question, 3)
    def contextText = contexts.withIndex()
      .collect { Map doc, int i -> "[${i + 1}] (${doc.source})\n${doc.text}" }
      .join('\n\n')

    def prompt = """
      You are a helpful assistant. Use only the provided context to answer the question.
      If the answer is not in the context, say "I don't know based on the provided information."
      
      === CONTEXT ===
      ${contextText}
      === END CONTEXT ===
      
      Question: ${question}
      Answer:
    """

    chat(prompt)
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  private List<Map> findSimilar(String query, int k) {
    def qEmb = embed(query)
    store
      .collect { doc -> [doc: doc, score: cosineSimilarity(qEmb, doc.embedding as List<Double>)] }
      .sort { -it.score }
      .take(k)
      .collect { it.doc as Map }
  }

  private List<Double> embed(String text) {
    def response = postJson("${ollamaNativeUrl}/api/embed", [model: embedModel, input: text])
    (response.embeddings as List)[0] as List<Double>
  }

  private String chat(String prompt) {
    def response = postJson("${ollamaNativeUrl}/api/chat", [
      model   : chatModel,
      messages: [[role: 'user', content: prompt]],
      stream  : false
    ])
    (response.message as Map).content as String
  }

  private static double cosineSimilarity(List<Double> a, List<Double> b) {
    double dot = 0, normA = 0, normB = 0
    int n = Math.min(a.size(), b.size())
    for (int i = 0; i < n; i++) {
      dot += a[i] * b[i]
      normA += a[i] * a[i]
      normB += b[i] * b[i]
    }
    (normA == 0 || normB == 0) ? 0.0 : dot / (Math.sqrt(normA) * Math.sqrt(normB))
  }

  private static Map postJson(String url, Map body) {
    def connection = (HttpURLConnection) new URL(url).openConnection()
    connection.requestMethod = 'POST'
    connection.setRequestProperty('Content-Type', 'application/json')
    connection.doOutput = true
    connection.connectTimeout = 30_000
    connection.readTimeout = 120_000
    connection.outputStream.withWriter('UTF-8') { it << JsonOutput.toJson(body) }
    int code = connection.responseCode
    if (code != 200) {
      def err = connection.errorStream?.text ?: 'no error body'
      throw new RuntimeException("Ollama API ${url} returned HTTP ${code}: ${err}")
    }
    new JsonSlurper().parseText(connection.inputStream.text) as Map
  }
}
