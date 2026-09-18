# Changelog

All notable changes to this project are documented in this file.

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
