# Changelog

All notable changes to this project are documented in this file.

## 0.5.8 - 2026-09-26

- fix: 0.5.7 installed APK crashed on launch (regression from the Bug#3 fix)
  - root cause (code analysis of the startup path — its only 0.5.7 change was
    the decorator; logcat confirmation welcome): rememberViewModelStoreNavEntryDecorator
    supplies an entry-scoped ViewModelStoreOwner whose default factory cannot
    instantiate AndroidViewModel(application) — first frame (ConnectionScreen's
    ConnectionViewModel) threw before any UI rendered
  - revert NavDisplay entryDecorators to NavDisplay defaults (the 0.5.6-verified
    behavior for Connection/Settings/Knowledge/Conversations screens)
  - Bug#3 (conversation screens showing the first conversation) re-fixed without
    the decorator: ChatScreen now uses `viewModel(key = "chat:$sessionId")`,
    isolating one ViewModel per session under the activity-scoped store
  - known trade-off (f4-integration deviation log): a per-session VM keeps its
    SSE collector alive until process death; lifecycle-aware subscription is the
    next task — no local UI-test infra for that path yet
  - no new automated tests this round: verified by full local unit tests +
    assembleDebug + on-device run (local APK toolchain)

- fix: knowledge screen crash on open (f4-integration Bug#1) — backend could
  answer `data.entries = null`; strict kotlinx decode threw an uncaught
  SerializationException inside viewModelScope and killed the process.
  - RestClient: envelope and data decode now guarded; new `Result.DecodeFailure`
    error category (rules.md: parse failures are their own class, never crash)
  - all 22 result-`when` branches handle DecodeFailure (diagnostics add
    `ErrorKind.DECODE`); paired with backend fix that now always returns arrays
    (minibox kb_handlers nil→[], regression-tested)
- fix: permission mode chips vanished after switching (Bug#2) — PATCH
  `/permissions/mode` returns only `{mode}` (api.md §5); SettingsViewModel
  overwrote the whole PermissionsData and wiped `modes`. New `applyModePatch`
  keeps GET's `modes` as source of truth (unit-tested pure function)
- fix: every conversation screen showed the first conversation (Bug#3) —
  NavDisplay lacked `rememberViewModelStoreNavEntryDecorator`, so all ChatKey
  entries shared one activity-scoped ViewModelStore with default class-name
  keys: the first ChatViewModel (sessionId frozen) was reused and its SSE
  subscription outlived the screen. Entry-scoped stores isolate ViewModels and
  clear them (stopping SSE collectors) when the entry pops

## 0.5.6 - 2026-09-25

- ci: add Release APK workflow — every push to main (merge) builds the debug APK
  and publishes a GitHub Release: tag v{VERSION}, CHANGELOG section as notes,
  APK + sha256 assets; idempotent re-runs only refresh assets; build failure or
  version-contract failure produces no Release
- ci: persist ~/.android/debug.keystore via actions cache (run_id primary key +
  prefix restore) so consecutive releases keep one signature and install as
  updates; after 7 idle days cache eviction forces one uninstall/reinstall
- fix: release_version.py apk_name template leftover `minibile-v{V}-arm64-v8a.apk`
  -> `minibox-v{V}.apk` (wrong project name; builds are not ABI-split)

## 0.5.5 - 2026-09-25

- docs: add f4-integration L3 task record (four-level integration checklist, scope,
  step-by-step status and deviation log table)
- docs: fix 13 stale statements contradicting the actual code: README/docs
  "no source yet" vs delivered v0.5.0, ROADMAP/plan/TODO index vs CHANGELOG,
  structure/ARCHITECTURE trees vs actual packages, minSdk record 26 to 29
- docs: restore CHANGELOG header line accidentally dropped in the 0.5.4 entry

## 0.5.4 - 2026-09-25

- Fix: all connection diagnostics failed with `CLEARTEXT ... not permitted by
  network security policy` against the local backend (found in f4-integration L1):
  - AndroidManifest: `application android:usesCleartextTraffic="true"`
  - Root cause: backend serves plain http/ws only (no TLS) and backend addresses
    are raw IPs (127.0.0.1 / LAN); `network_security_config` domain rules cannot
    whitelist IPs, and targetSdk>=28 blocks cleartext before any connection leaves
    the device.
  - No behavior change beyond allowing http/ws to user-configured backend;
    REST/SSE auth (Bearer) and WS handshake remain enforced server-side.

## 0.5.3 - 2026-09-20

- DeviceWsClient: add onClosing override to handle server-initiated close immediately
- DeviceWsClientTest: robust polling for Reconnecting/Disconnected state

## 0.5.2 - 2026-09-20

- DeviceWsClientTest: fix flaky server-close test (poll for Reconnecting/Disconnected state, increase pre-close delay)

## 0.5.1 - 2026-09-20

- Remove ConnectionPool(0,0,NANOSECONDS) from DeviceWsClientTest that caused IllegalArgumentException on CI

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
