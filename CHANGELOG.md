# Changelog

All notable changes to this project are documented in this file.

## 0.3.0 - 2026-09-19

- F2 (permissions + tools + rewind):
  - core/model: PermissionsData, ToolMetadata, ToolInfo, ToolsData
  - core/network: RestClient.patch (generic PATCH)
  - data: PermissionsRepository (get/setMode), ToolsRepository (list)
  - navigation: SettingsKey route; settings entry on conversations top bar
  - feature/settings: permission mode chips (yolo double-confirm + persistent
    high-risk banner), tools list with risk_tier coloring and metadata badges
  - feature/chat: rewind menu + rounds dialog (full refresh after rewind)
  - Tests: Permissions/Tools DTO fixtures, MockWebServer (PATCH method,
    trailing slash, invalid_mode 400 -> HttpError)
- Not exposed (by policy): /tools/acquire, /upgrade/* (high-risk, out of
  first-version UI scope per AGENTS.md)

## 0.2.0 - 2026-09-19

- F1 (conversations + SSE chat loop):
  - core/model: Session, Message, SendChatMessage, SendMessageResult, ApprovalResult,
    ChatEvent (typed SSE events) + ChatEventMapper
  - core/network: RestClient generic get/post (trailing-slash aware)
  - data: ConversationsRepository (list/create/get/sendMessage/rewind/submitApproval),
    ChatStreamRepository (auto-reconnect with exponential backoff 1s-30s,
    event_id dedup window 500, seq gap detection -> GapDetected, 401 stops reconnect)
  - navigation: ConnectionKey / ConversationsKey / ChatKey routes
  - feature/conversations: list screen + create FAB
  - feature/chat: message list + input + running banner + approval card
    (terminal state per agent.run_finished.state, not assistant arrival)
  - Tests: Session DTO + ChatEventMapper fixtures, ConversationsRepository MockWebServer

## 0.1.0 - 2026-09-19

- F0 (model + network + connection diagnostics):
  - core/model: Envelope, ProblemDetail, HealthData, ReadyData, ServerStatusData, ConnectionConfig
  - core/security: CredentialStore (EncryptedSharedPreferences, AES-256-GCM)
  - core/network: MiniboxHttpClient (Bearer interceptor + exempt paths), RestClient, ErrorResponseHandler, SseClient skeleton
  - data: ConnectionRepository (3-step diagnostic: health → ready → server/status)
  - navigation: Navigation 3 scaffold (ConnectionKey + AppNavDisplay)
  - feature/connection: ConnectionScreen + ConnectionViewModel + ConnectionUiState
  - Tests: DTO serialization fixtures, Repository MockWebServer tests

## 0.0.0 - 2026-09-18

- Initial Android project skeleton: Gradle build, CI pipeline, version contract.
