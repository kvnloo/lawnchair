# Canonical backend contract corrections

The first Android draft preceded the backend API document. The resume fixed the
client against the canonical z0live `docs/API.md` and the running loopback service,
without modifying the separately owned backend.

An authenticated live probe of `127.0.0.1:8791` returned:
- health object keys `ok`, `version`;
- inbox object keys `messages`, `after`, `has_more`;
- message fields `id` (integer), `say` (string), `action` (null for speech),
  `params` (object), `session_id` (nullable).
The probe's temporary pairing was revoked and revocation read back. No credential
or private message content is retained in these receipts.

Corrections implemented:
- Parse/validate canonical inbox pages, not a bare array; preserve returned cursor
  and pagination. Persist only handled inbox identities. Null action is speech-only.
- Dispatch `/v1/tools/tell_hermes`, `/v1/tools/get_context`, `/v1/tools/summarize`
  with `{session_id,arguments:{...}}`; reject invented generic tool endpoints.
- Use the broker-generated session ID, never a fabricated client session identity.
- Map provider/local events to the backend's closed event types, Unix seconds and
  stable UUID event IDs for retry deduplication.
- Explicit `/v1/sessions/ID/end` after bounded final event flush; final event also
  carries `data.session_end:true` for the documented fallback.
- Wait for `response.done` and pending tool completion before `response.create`;
  queue Hermes TTS until user/provider playback is idle and preserve manual mute.

JVM fixtures exercise the actual OkHttp client over local HTTP, including canonical
routes, page cursor/has_more, malformed/regressing pages, auth, error and redirect
boundaries, normalized event batches and explicit hangup. Separate JVM tests cover
queue acknowledgement by identity, safe turns and audio admission. Mock credentials
are test fixtures, not provider/service evidence. Physical-phone acceptance remains
separate; see [Z0LIVE.md](Z0LIVE.md).
