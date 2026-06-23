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

# 2. Set your Gemini key (required for demos 07-09)
export GEMINI_API_KEY=...

# 3. Pull Ollama models (required for demos 10-11)
ollama pull llama3.2
ollama pull nomic-embed-text   # demo 11 only

# 4. Run any demo
./scripts/run-demo.sh <1-13>
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
| `demo13` | `./scripts/run-demo.sh 13` | **AI + Telegram** — custom `devbg-telegram:` Camel component |
| `groovy`  | `-Dspring.profiles.active=groovy` | Bonus: Groovy DSL route |

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

### Environment variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `TELEGRAM_AUTH_TOKEN` | live mode only | *(empty)* | Bot token from @BotFather |
| `TELEGRAM_SECRET_TOKEN` | no | *(empty)* | Secret token for webhook request validation |
| `OLLAMA_MODEL` | no | `llama3.2` | Ollama model for AI replies |
| `OLLAMA_BASE_URL` | no | `http://localhost:11434/v1` | Ollama OpenAI-compatible endpoint |

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

## Presentation

See [`presentation/TALK_SCRIPT.md`](presentation/TALK_SCRIPT.md) for the full 60-minute
talk script with speaker notes, slide descriptions, and Q&A prep.

See [`presentation/SLIDES_OUTLINE.md`](presentation/SLIDES_OUTLINE.md) for the slide-by-slide
visual design guide.

## Project Structure

```
src/
├── main/
│   └── groovy/bg/dev/camel/
│       ├── CamelAiDemoApplication.groovy
│       ├── routes/
│       │   ├── Demo01_HelloWorldRoute.groovy
│       │   ├── Demo02_FileProcessorRoute.groovy
│       │   ├── Demo03_ContentBasedRouterRoute.groovy
│       │   ├── Demo04_AggregatorRoute.groovy
│       │   ├── Demo05_SftpPollerRoute.groovy
│       │   ├── Demo06_DatabaseRoute.groovy
│       │   ├── Demo07_AiSentimentRoute.groovy
│       │   ├── Demo08_AiSummarizerRoute.groovy
│       │   ├── Demo09_AiEtlRoute.groovy
│       │   ├── Demo10_OllamaRoute.groovy
│       │   ├── Demo11_RagOllamaRoute.groovy
│       │   ├── Demo12_SpringAiTools.groovy
│       │   └── Demo13_TelegramRoute.groovy
│       ├── component/telegram/          ← custom devbg-telegram: component
│       │   ├── TelegramComponent.groovy
│       │   ├── TelegramEndpoint.groovy
│       │   ├── TelegramConsumer.groovy
│       │   ├── TelegramProducer.groovy
│       │   ├── TelegramWebhookRouter.groovy
│       │   ├── TelegramWebhookController.groovy
│       │   └── model/
│       └── service/
│           ├── AiSentimentService.groovy
│           ├── AiSummarizerService.groovy
│           ├── AiEnricherService.groovy
│           └── OllamaRagService.groovy
│   └── resources/
│       ├── application.yml
│       ├── sql/schema.sql
│       └── sql/data.sql
├── test/
│   └── groovy/bg/dev/camel/
│       ├── Demo05_SftpRouteTest.groovy       ← Apache MINA SSHD
│       ├── Demo06_DatabaseRouteTest.groovy   ← Testcontainers MySQL
│       ├── Demo12_SpringAiToolsTest.groovy
│       └── Demo13_TelegramRouteTest.groovy
presentation/
├── TALK_SCRIPT.md    ← full 60-min speaker script
└── SLIDES_OUTLINE.md ← visual design guide
scripts/
├── setup.sh          ← first-time setup
└── run-demo.sh       ← launch a demo by number
```
