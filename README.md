# Apache Camel in the AI Era — Demo Project

Demo code for the dev.bg webinar: *"Apache Camel in the AI Era"*.

## Tech Stack

| Tool | Version |
|------|---------|
| Java | 21.0.5-oracle |
| Groovy | 4.0.24 |
| Spring Boot | 4.1.0 |
| Gradle | 9.2.1 |
| Apache Camel | 4.20.0 |
| Spring AI | 2.0.0 |
| MySQL | 8.0 (via Testcontainers) |
| SFTP server (tests) | Apache MINA SSHD 2.14.0 |

## Quick Start

```bash
# 1. First-time setup
./scripts/setup.sh

# 2. Set your Gemini key (required for demos 07-09, 12)
export GEMINI_API_KEY=...

# 3. Pull Ollama models (required for demos 10-11, 13)
ollama pull llama3.2
ollama pull nomic-embed-text   # demo 11 only

# 4. For demo 14 — also set OpenAI key (voice transcription + vision)
export OPENAI_API_KEY=...
export TELEGRAM_AUTH_TOKEN=...   # bot token from @BotFather

# 5. Run any demo
./scripts/run-demo.sh <1-14>
```

## Demo Map

| Profile | Run command | What it shows |
|---------|-------------|---------------|
| `demo01` | `./scripts/run-demo.sh 1` | Timer + Log — Hello World |
| `demo02` | `./scripts/run-demo.sh 2` | File Processor — watch a directory |
| `demo03` | `./scripts/run-demo.sh 3` | Content-Based Router (EIP) |
| `demo04` | `./scripts/run-demo.sh 4` | Aggregator (EIP) — micro-batching |
| `demo05` | `./scripts/run-demo.sh 5` | SFTP Poller (camel-ftp) |
| `demo06` | `./scripts/run-demo.sh 6` | MySQL — poll → process → update |
| `demo07` | `./scripts/run-demo.sh 7` | **AI** — Sentiment Classifier |
| `demo08` | `./scripts/run-demo.sh 8` | **AI** — Document Summariser |
| `demo09` | `./scripts/run-demo.sh 9` | **AI** — Full ETL Pipeline |
| `demo10` | `./scripts/run-demo.sh 10` | **AI** — Local LLM via Ollama |
| `demo11` | `./scripts/run-demo.sh 11` | **AI** — Ollama + RAG with Public APIs |
| `demo12` | `./scripts/run-demo.sh 12` | **AI** — Camel route as an LLM Tool (`spring-ai-tools:`) |
| `demo13` | `./scripts/run-demo.sh 13` | **AI + Telegram** — custom `devbg-telegram:` Camel component |
| `demo14` | `./scripts/run-demo.sh 14` | **AI + Telegram** — Multi-modal multi-agent orchestration |
| `groovy`  | `-Dspring.profiles.active=groovy` | Bonus: Groovy DSL route |

---

## Demo 12 — Camel Route as an LLM Tool

Demonstrates the `spring-ai-tools:` URI scheme — any Camel route can be registered as a
**Spring AI Function/Tool** that the LLM invokes autonomously.

- Route receives tool parameters as Camel headers (e.g. `itemSku`)
- LLM decides when to call the tool based on the user query
- In a real system the route body would query a legacy SQL DB, SAP, or an SFTP file
- REST controller (`Demo12_LogisticsAssistantController`) exposes a `/logistics` endpoint
  that accepts natural-language queries and returns AI-powered answers

```bash
./scripts/run-demo.sh 12
# Then: curl -X POST http://localhost:8080/logistics -d '{"query":"How much SKU-99 do we have?"}'
```

---

## Demo 13 — Telegram Bot + AI

Demonstrates a **custom Apache Camel component** (`devbg-telegram:`) that connects the
Telegram Bot API to a Camel route and generates AI-powered replies via Ollama.

### Architecture

```
User (Telegram) ──► Telegram Bot API ──► POST /devbg-telegram/webhook
                                                  │
                                     TelegramWebhookController
                                                  │ async
                                     TelegramWebhookRouter
                                                  │
                           from('devbg-telegram:webhook')   ← TelegramConsumer
                                                  │
                                      openai:chat-completion  ← Ollama
                                                  │
                           to('devbg-telegram:send')         ← TelegramProducer
                                                  │
                                    Telegram Bot API ──► User (Telegram)
```

No Telegram bot? Run in **simulator mode** — the demo fires three questions
through the AI pipeline and writes answers to `output/telegram/`.

### Simulator mode (no credentials needed)

```bash
./scripts/run-demo.sh 13
```

Requires Ollama running with `llama3.2` pulled (same as demos 10–11).

### Live Telegram bot mode

#### Step 1 — Create a bot with BotFather

1. Open Telegram → search for **@BotFather** → `/start`
2. Send `/newbot` → follow the prompts (choose a name and username)
3. Copy the **bot token** (format: `123456789:AAF...`)

#### Step 2 — Expose your local server

```bash
ngrok http 8080
# Example output: https://abc123.ngrok.io  → forwarding to localhost:8080
```

#### Step 3 — Register the webhook with Telegram

```bash
curl -X POST "https://api.telegram.org/bot<TOKEN>/setWebhook" \
     -H "Content-Type: application/json" \
     -d '{
       "url": "https://abc123.ngrok.io/devbg-telegram/webhook",
       "secret_token": "<TELEGRAM_SECRET_TOKEN>"
     }'
```

> `secret_token` is optional but recommended — the app validates it against the
> `X-Telegram-Bot-Api-Secret-Token` header and rejects requests that don't match.

#### Step 4 — Start the app

```bash
export TELEGRAM_AUTH_TOKEN=<bot-token-from-step-1>
export TELEGRAM_SECRET_TOKEN=<secret-token-from-step-3>   # optional

./scripts/run-demo.sh 13
```

#### Step 5 — Chat

Open Telegram → start a conversation with your bot → send any text → the bot
replies with an AI-generated answer powered by your local Ollama instance.

Check the webhook status at: `GET /devbg-telegram/status`

### Custom component layout

```
component/telegram/
├── TelegramComponent.groovy         ← registers devbg-telegram: scheme with Camel
├── TelegramEndpoint.groovy          ← URI config (authToken, secretToken, apiBaseUrl)
├── TelegramConsumer.groovy          ← webhook consumer; one exchange per update
├── TelegramProducer.groovy          ← sends a text message to a Telegram chat
├── TelegramWebhookRouter.groovy     ← Spring bridge between HTTP and Camel
├── TelegramWebhookController.groovy ← POST (updates) + GET /status (health check)
└── model/
    ├── TelegramUpdate.groovy
    ├── TelegramMessage.groovy
    ├── TelegramChat.groovy
    └── TelegramUser.groovy
```

---

## Demo 14 — Multi-Modal Multi-Agent Orchestration via Telegram

The most advanced demo. Builds on the native `camel-telegram` component to create a
**full multi-agent AI assistant** with multi-modal input, explicit ReAct reasoning,
orchestrator fanout, agent-to-agent delegation, and per-user conversation memory.

### Camel patterns demonstrated

| Pattern | Where |
|---------|-------|
| Content-Based Router | Multi-modal input split (text / voice / photo) |
| Sequential pipeline | Five named `direct:` stages in `Demo14_AiPipelineRoutes` |
| `loopDoWhile` | Explicit ReAct Reason+Act cycle in `Demo14_AgentRoutes` |
| `multicast().parallelProcessing()` | Orchestrator fanout to both agents simultaneously |
| Custom aggregation strategy | `AgentResultAggregationStrategy` joins multicast results |
| `spring-ai-tools:` routes | Five Camel routes exposed as LLM tools |
| Agent-to-agent delegation | `SupplyChainAgent` delegates to `WeatherAgent` via tool |
| Chain-of-thought summariser | Long responses compressed by a second LLM call |
| Conditional retry | Low-confidence replies re-invoked with richer context |

### Message flow

```
User (Telegram)
      │ text / voice / photo
      ▼
from('telegram:bots')
      │
      ├── voice/audio ──► TelegramFileDownloadProcessor
      │                   └──► GptTranscriptionProcessor (OpenAI GPT)  → text
      ├── photo        ──► TelegramFileDownloadProcessor
      │                   └──► GptVisionProcessor (Gemini Vision)      → text
      └── text ──────────────────────────────────────────────────────► text
                                          │
                          direct:invokeSpringAiPipeline
                                          │
          Stage 1 ── direct:enrichContext (timezone + timestamp)
                                          │
          Stage 2 ── IntentRouterProcessor (LLM structured classification)
                                          │
                    ┌─────────────────────┼─────────────────────────────┐
              NeedsClarification       WEATHER               PROCUREMENT / COMPOUND
                    │                    │                              │
            clarification reply  direct:invokeWeatherAgentReact  direct:invokeSupplyChainAgent
                               (loopDoWhile ReAct loop, max 5)         │
                               WeatherReActStepProcessor          direct:invokeOrchestratorFanout
                               ├── CALL_TOOL → checkWeather        multicast().parallelProcessing()
                               └── FINAL_ANSWER → body set         ├─► WeatherAgentProcessor
                                                                    └─► SupplyChainAgentProcessor
                                                                    AgentResultAggregationStrategy
                                                                    OrchestratorSynthesisProcessor
                                          │
          Stage 4 ── retryWithBroaderContext (if "I'm not sure" / "I don't know")
                                          │
          Stage 5 ── formatForTelegram → summariseForTelegram (if > 300 chars)
                                          │
                               to('telegram:bots') ──► User
```

### Spring AI tools (invoked dynamically by the LLM)

| Tool route | Transport | What it does |
|------------|-----------|--------------|
| `spring-ai-tools:checkWeather` | Camel HTTP | Open-Meteo API — current conditions or multi-day forecast |
| `spring-ai-tools:checkInventory` | Camel SQL | `SELECT` from MySQL `parts` table |
| `spring-ai-tools:orderFromSupplier` | Camel HTTP | `POST /mock/supplier-order` — B2B purchase order |
| `spring-ai-tools:broadcastEmergencyAlert` | Camel Kafka | Publish to `operational-alerts` topic (degrades gracefully) |
| `spring-ai-tools:callWeatherAgent` | `direct:invokeWeatherAgent` | Agent-to-agent delegation (stripped in COMPOUND mode) |

### Per-user conversation memory

`Demo14MemoryConfig` registers a `MessageChatMemoryAdvisor`
(`MessageWindowChatMemory`, window = 20) keyed by the Telegram chat ID. Each user
gets independent conversation history across both agents.

```yaml
demo14:
  memory:
    enabled: true        # set to false to disable
    window-size: 20
```

### Environment variables (demo 14)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `TELEGRAM_AUTH_TOKEN` | yes | *(empty)* | Bot token from @BotFather |
| `TELEGRAM_SECRET_TOKEN` | no | *(empty)* | Webhook request validation token |
| `GEMINI_API_KEY` | yes | `REPLACE-ME` | Gemini Flash — intent classification + vision |
| `OPENAI_API_KEY` | yes (voice) | `sk-placeholder` | GPT transcription for voice messages |
| `OPENAI_BASE_URL` | no | `https://api.openai.com` | OpenAI API base URL |
| `OPENAI_AUDIO_MODEL` | no | `gpt-4o-transcribe` | Transcription model |

### Route file layout

```
routes/ai/telegram/
├── Demo14_TelegramInboundRoutes.groovy  ← CBR: text / voice / photo lanes
├── Demo14_AiPipelineRoutes.groovy       ← five-stage pipeline + retry + format
├── Demo14_AgentRoutes.groovy            ← agent sub-routes + ReAct loop + orchestrator fanout
└── Demo14_SpringAiToolRoutes.groovy     ← five spring-ai-tools: routes
```

---

## Demos 05 & 06 (SFTP + MySQL)

These run with embedded infrastructure in tests:

```bash
# SFTP demo test (uses Apache MINA SSHD — no Docker needed)
./gradlew test --tests "*Demo05*"

# DB demo test (uses Testcontainers MySQL 8 — Docker required)
./gradlew test --tests "*Demo06*"
```

For demo05 standalone: set `SFTP_HOST`, `SFTP_PORT`, `SFTP_USER`, `SFTP_PASS` env vars
to point at a real SFTP server.

For demo06 standalone: set `DB_USER`, `DB_PASSWORD` and have MySQL 8 running on port 3306.

## Project Structure

```
src/
├── main/
│   ├── groovy/bg/dev/camel/
│   │   ├── CamelAiDemoApplication.groovy
│   │   ├── config/
│   │   │   ├── AppConfig.groovy
│   │   │   └── Demo14MemoryConfig.groovy              ← per-user MessageChatMemoryAdvisor
│   │   ├── component/telegram/                        ← custom devbg-telegram: component (demo13)
│   │   │   ├── TelegramComponent.groovy
│   │   │   ├── TelegramEndpoint.groovy
│   │   │   ├── TelegramConsumer.groovy
│   │   │   ├── TelegramProducer.groovy
│   │   │   ├── TelegramWebhookRouter.groovy
│   │   │   ├── TelegramWebhookController.groovy
│   │   │   └── model/
│   │   │       ├── TelegramUpdate.groovy
│   │   │       ├── TelegramMessage.groovy
│   │   │       ├── TelegramChat.groovy
│   │   │       └── TelegramUser.groovy
│   │   ├── controller/
│   │   │   ├── Demo12_LogisticsAssistantController.groovy  ← HTTP endpoint for demo12
│   │   │   └── MockSupplierController.groovy               ← mock B2B supplier REST API (demo14)
│   │   ├── processor/
│   │   │   ├── TelegramFileDownloadProcessor.groovy        ← downloads voice/photo from Telegram
│   │   │   └── ai/
│   │   │       ├── ContextEnrichmentProcessor.groovy       ← attaches timezone + timestamp
│   │   │       ├── GptTranscriptionProcessor.groovy        ← OpenAI audio → text
│   │   │       ├── GptVisionProcessor.groovy               ← Gemini Vision image → text
│   │   │       ├── IntentClassification.groovy             ← structured output POJO
│   │   │       ├── IntentRouterProcessor.groovy            ← LLM-based intent classification
│   │   │       └── agent/
│   │   │           ├── AgentProcessor.groovy               ← base agent processor
│   │   │           ├── AgentResultAggregationStrategy.groovy ← joins multicast results
│   │   │           ├── OrchestratorSynthesisProcessor.groovy ← final LLM synthesis pass
│   │   │           ├── ReActStep.groovy                    ← ReAct loop step POJO
│   │   │           ├── SummariserAgentProcessor.groovy     ← compresses long responses
│   │   │           ├── SupplyChainAgentProcessor.groovy    ← procurement specialist agent
│   │   │           ├── WeatherAgentProcessor.groovy        ← weather specialist agent
│   │   │           └── WeatherReActStepProcessor.groovy    ← drives ReAct loop iterations
│   │   ├── routes/
│   │   │   ├── Demo01_HelloWorldRoute.groovy
│   │   │   ├── Demo02_FileProcessorRoute.groovy
│   │   │   ├── Demo03_ContentBasedRouterRoute.groovy
│   │   │   ├── Demo04_AggregatorRoute.groovy
│   │   │   ├── Demo05_SftpPollerRoute.groovy
│   │   │   ├── Demo06_DatabaseRoute.groovy
│   │   │   ├── Demo07_AiSentimentRoute.groovy
│   │   │   ├── Demo08_AiSummarizerRoute.groovy
│   │   │   ├── Demo09_AiEtlRoute.groovy
│   │   │   ├── Demo10_OllamaRoute.groovy
│   │   │   ├── Demo11_RagOllamaRoute.groovy
│   │   │   ├── Demo12_SpringAiTools.groovy
│   │   │   ├── Demo13_TelegramRoute.groovy
│   │   │   └── ai/telegram/                           ← demo14 route files
│   │   │       ├── Demo14_TelegramInboundRoutes.groovy
│   │   │       ├── Demo14_AiPipelineRoutes.groovy
│   │   │       ├── Demo14_AgentRoutes.groovy
│   │   │       └── Demo14_SpringAiToolRoutes.groovy
│   │   └── service/
│   │       ├── AiEnricherService.groovy
│   │       ├── AiSentimentService.groovy
│   │       ├── AiSummarizerService.groovy
│   │       ├── OllamaRagService.groovy
│   │       └── WeatherService.groovy                  ← Open-Meteo HTTP client
│   └── resources/
│       ├── application.yml
│       ├── logback-spring.xml
│       ├── input/
│       │   ├── documents/sample_article.txt
│       │   └── sentiment/
│       │       ├── sample_positive.txt
│       │       └── sample_negative.txt
│       ├── sql/
│       │   ├── schema.sql
│       │   └── data.sql
│       └── static/
│           └── index.html
├── test/
│   └── groovy/bg/dev/camel/
│       ├── Demo05_SftpRouteTest.groovy                ← Apache MINA SSHD
│       ├── Demo06_DatabaseRouteTest.groovy            ← Testcontainers MySQL
│       ├── Demo12_SpringAiToolsTest.groovy
│       ├── Demo13_TelegramRouteTest.groovy
│       ├── Demo14_OrchestrationRouteTest.groovy
│       └── processor/ai/agent/
│           └── AgentResultAggregationStrategyTest.groovy
scripts/
├── setup.sh          ← first-time setup
└── run-demo.sh       ← launch a demo by number
```