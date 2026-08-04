# Multi-platform streaming architecture

## Scope

LAIVE treats one `broadcast_sessions` row as the logical analysis session and
creates one `broadcast_platform_sessions` child for every connected provider
account. CHZZK remains backward compatible and YouTube can be enabled with
OAuth configuration. Adding another provider requires a platform adapter and a
collector strategy; the logical session and report APIs do not depend on a
provider enum.

```text
broadcast_sessions
  +-- broadcast_platform_sessions (CHZZK)
  +-- broadcast_platform_sessions (YOUTUBE)
  +-- broadcast_platform_sessions (future adapter)
```

## API compatibility

- `GET /api/v1/platforms` returns configured, enabled adapters and capabilities.
- Account OAuth routes remain under `/api/v1/platform-accounts/{platformCode}`.
- Session start accepts either legacy `platformAccountId` or the new
  `platformAccountIds`; supplying both is rejected.
- Calendar create/update accepts the same legacy/new account selection and the
  response contains both the legacy primary ID and `platformAccountIds`.
- Session responses contain `platformSessions`.
- Session reports contain `platformMetrics` while preserving aggregate fields.
- Growth reports accept optional `platformCode`; omitted or `ALL` preserves the
  existing aggregate response.

## Storage and migrations

- V17 adds the platform catalog, encrypted credential envelope, provider child
  sessions, resources, capability runs, calendar assignments and per-platform
  report metrics.
- V18 adds provider provenance and normalized provider event fields to the
  TimescaleDB collection tables and rebuilds the aggregate required by the new
  source key.
- V19 makes historical provider-account cleanup follow the logical session
  ownership safely and keeps legacy calendar writes synchronized with the
  multi-account join table during the expand/contract transition.

Legacy token columns remain available during this expand phase. The credential
table is dual-written so a later deployment can switch readers and only then
remove the legacy columns.

## Lifecycle

1. Starting a session stores the logical session, provider children and one
   preparation Outbox event per child in one transaction.
2. Each provider handler resolves its adapter, discovers provider resources
   when supported, and starts a collector keyed by `platformSessionId`.
3. Provider failures are isolated. The parent remains active while at least one
   child is live.
4. Manual end stops LAIVE collection only; it does not stop an external stream.
5. Finalization aggregates all children, stores aggregate report metrics and
   stores a provider breakdown used by report and growth AI prompts.

## Aggregation rules

- Viewer counts use the latest valid measurement per provider and UTC minute,
  then sum providers. A single-provider session retains the legacy raw-sample
  average behavior.
- Chat is summed across providers and retains provider message identity and
  normalized event type (`TEXT`, `DONATION`, `MEMBERSHIP`, `GIFT`, etc.).
- Logical duration is the union of live intervals, not the sum of child
  durations.
- Unsupported metrics remain unavailable; they are not converted to zero.

## YouTube operation

Set the following Spring environment variables to enable the adapter:

```dotenv
YOUTUBE_CLIENT_ID=...
YOUTUBE_CLIENT_SECRET=...
YOUTUBE_REDIRECT_URI=https://your-api.example.com/api/v1/platform-accounts/youtube/callback
```

The Google OAuth client must enable the YouTube Data API v3 and the redirect URI
must match exactly. The collector defaults to `https://www.googleapis.com`; use
`YOUTUBE_API_BASE` only for an API-compatible proxy or test server.

YouTube v1 observes an already-running broadcast. It does not create, start or
stop a provider broadcast, upload clips, or deduplicate the same viewer across
providers. Viewer counts may be absent when the provider hides them.

## Verification

Backend:

```powershell
$env:JAVA_HOME='C:\Users\SSAFY\.jdks\ms-21.0.12'
.\gradlew.bat test --no-daemon
```

Collector:

```powershell
$env:PYTHONPATH=(Get-Location).Path
uv run --project .. pytest tests -p no:cacheprovider
```

An external YouTube smoke test additionally requires real Google credentials
and an active, chat-enabled test broadcast.
