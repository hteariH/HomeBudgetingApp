# Self-hosted pivot

PersonalMoney stops being a hosted service with a paid tier and becomes an open-source,
self-hosted application for personal and family finances. People run it on their own
NAS or server, and can plug in an AI model of their choosing to read receipts.

This document records the decisions that apply to the whole pivot and the order of the
work. Each sub-project gets its own design spec, plan and implementation cycle.

## Decisions

| Question | Decision |
| --- | --- |
| Domain | Personal and family finances. Not business bookkeeping: no chart of accounts, double entry, invoices or tax. |
| Monetisation | Removed completely: Play Billing, entitlements, the five-account cap and AdMob. |
| personalmoney.app | Stops serving the app. Becomes the project's landing page, leading to installation instructions for a NAS or home server. |
| Family | A shared household ledger: several logins see the same accounts, categories and transactions; each transaction records its author. |
| Deployment | `docker compose` with the app and Postgres. MySQL/MariaDB support is dropped. |
| Existing data | None to keep. The schema restarts from a clean Postgres baseline. |
| Android | Kept. The user enters their server's address at sign-in. Distributed as an APK on GitHub Releases; F-Droid later. |
| Licence | AGPL-3.0. |
| AI for receipts | A pluggable LLM provider called by the server (OpenAI-compatible endpoint, Anthropic, Gemini). On-device Tesseract is removed once this lands. |
| AI assistants | The server exposes an MCP endpoint so a user's own assistant can read the ledger and record transactions. |

## Sub-projects, in order

1. **Open-source baseline** — [2026-10-15-open-source-baseline-design.md](2026-10-15-open-source-baseline-design.md).
   Remove monetisation, rebase the schema on Postgres with households built in, make the
   instance installable and administrable by a stranger, add the server address to
   Android, move the landing page out to `site/`, licence and CI for releases.
2. **Family sharing.** Invitations into a household, a members list, roles
   (`OWNER`/`MEMBER`), leaving and removing members, and the author shown on transactions.
   The baseline already scopes all data by household, so this is mostly invitations and UI.
3. **AI receipt scanning.** A `ReceiptExtractor` interface on the server with adapters for
   an OpenAI-compatible endpoint (covers Ollama, LM Studio, vLLM, OpenRouter, OpenAI),
   Anthropic and Gemini. Configured per household with the key encrypted by
   `SecretCrypto`; an instance-wide default can come from the environment. The client
   uploads a photo, the server asks the model for JSON against a fixed schema (merchant,
   date, currency, total, line items, suggested category) and returns a draft transaction
   for the user to review. Receipt images are stored under `/data` and attached to the
   transaction. Both the web and Android clients get the scan button only when
   `/api/instance` reports a configured provider. Tesseract and its language data leave
   the Android app.
4. **MCP server.** Personal access tokens, and a Streamable HTTP MCP endpoint at `/mcp`
   with tools to list accounts and categories, search transactions, summarise spending,
   create a transaction and attach a receipt. It calls the same services as the REST API,
   so household scoping and validation are shared.

Sub-projects 3 and 4 share the service layer for creating a transaction with an attached
receipt; whichever lands first builds it.
