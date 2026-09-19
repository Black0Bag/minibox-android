# Changelog

All notable changes to this project are documented in this file.

## 0.5.0 - 2026-09-19

- F3a (device WS transport, stage-2 leftover per ROADMAP):
  - core/model: RpcRequest/RpcResponse/RpcError (JSON-RPC 2.0), RpcErrorCodes
    (-32001..-32005), DeviceOuterRequest, DeviceHelloParams, RpcFrames
    (encode/decode/redactAuth — logs never print auth token)
  - core/network: DeviceWsClient — state machine (Disconnected→Connecting→
    Handshaking→Ready→Reconnecting), unique-id pending map (responses resolve
    by id, not arrival order), single read loop + serialized writer via
    Channel, heartbeat.ping every 30s (10s timeout → close → reconnect with
    exponential backoff 1s→30s, re-handshake + re-hello), connect() first
    frame {client, protocol 1.0, auth}
  - core/security: CredentialStore.deviceToken (Keystore-backed)
  - navigation: DeviceKey; device entry on connection screen (after diagnose)
  - feature/device: token input (masked) + connect/disconnect + state label
    + heartbeat RTT display
  - Tests: RPC frame fixtures (8), MockWebServer WS upgrade integration
    (handshake+hello → Ready, pending by id, not-connected → -32003,
    server close → leaves Ready)
- Out of scope (F3 main): 18 device executors, foreground service, command
  approval UI, real-device side effects

## 0.4.0 - 2026-09-19

- F2b (knowledge base, plan.md F2 second half):
  - core/model: KnowledgeEntry, KbSearchHit (flattened Hit), KbSearchRequest,
    KbListData, KbOkResult, CompileJob (pending/processing/ready/failed),
    KbCompileRequest, KbUpdateRequest
  - core/network: RestClient.delete (generic DELETE)
  - data: KnowledgeRepository (search/list/get/create/update/delete/compile/job)
  - navigation: KnowledgeKey + KnowledgeEntryKey; knowledge card on settings
  - feature/knowledge: three-tab screen (search with score/match_type badges,
    paged entries with load-more, compile with bounded polling 2s x90),
    entry detail (edit content/source/tags/importance + delete confirm),
    create dialog (content required)
- Explicitly out of plan scope: /kb/distill, /kb/snapshots, /kb/rollback
- Tests: Knowledge DTO fixtures, MockWebServer (search body, query params,
  PATCH/DELETE methods, compile job paths)

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
