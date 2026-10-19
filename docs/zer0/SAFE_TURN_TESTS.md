# Safe turn and audio admission regression matrix

Scope: host-JVM checks of the **existing public state APIs**; no service mocks, physical microphone/speaker, WebRTC, Android lifecycle or provider calls. Run only against the integration branch containing the canonical `Z0LiveTurnState` and `Z0LiveAudioState` sources.

## Gate (Hermes execution)

```sh
bash tools/zer0/test-safe-turn-matrix.sh
```

Requires a JDK (`javac`, `java`). The runner compiles three canonical files into a disposable directory, cleans it on exit/failure, and returns nonzero on a failed assertion or compiler error. Existing voice JVM runners are untouched.

**Execution: NOT RUN — ChatGPT cannot execute code in this lane.** Hermes should run the new script, the existing voice tests, and the appropriate repository gates against the exact PR SHA. Negative-control qualification: temporarily invert the expected value of one labeled assertion **without committing the inversion**, confirm the script exits nonzero, restore it, then verify normal execution. Record both outcomes and the tested SHA in the task issue body.

## Labeled expectations

| Boundary | Expected continuation | Inbox admission |
| --- | --- | --- |
| No tool completion, otherwise idle | Rejected | Allowed |
| Tool completion queued, response active | Rejected | Rejected |
| Tool completion queued, user speaking | Rejected | Rejected |
| Tool completion queued, playback active | Rejected | Rejected |
| Tool completion queued, tools still pending | Rejected | Rejected |
| Completion queued, fully safe, no tools pending | Taken exactly once; starts response | Rejected until response ends |
| Response ends with no queued completion | Rejected | Allowed if speech/playback inactive |
| User speech or playback begins after idle | Rejected without completion | Rejected immediately |
| Duplicate `toolCompleted()` before admission | Coalesced; one take | Rejected until continuation resolves |
| Inbox playback active | N/A | Microphone and realtime speaker flags disabled |
| Owner muted during/before inbox playback | N/A | Microphone remains disabled after playback |
| Audio focus lost during playback | N/A | Microphone remains disabled until focus returns |
| Audio disconnected, then late focus/playback/mute callbacks | N/A | Microphone remains disabled |

The matrix exhausts 32 combinations of response, user speech, playback, queued completion and outstanding tools, and 16 combinations of audio connection, focus, owner mute and inbox playback. Each test result includes the labeled state mask so failing boundaries can be identified.

## Gaps and non-claims

- The turn state has **no hangup/teardown/reset API**. These tests cannot prove a queued completion is canceled on hangup; this must be handled and verified by the service owner. They test only the representable response/user/playback gates.
- The audio state exposes `setConnected(false)` and gates microphone after disconnect. `realtimeSpeakerEnabled()` is a focus/playback flag independent of connection, not proof an output route is alive.
- Completions coalesce **while pending**. A distinct *late* `toolCompleted()` after consumption could re-arm the flag; call-ID deduplication and post-teardown callback disposal live outside this state API.
- Test matrix alone does not verify real audio echo, barge-in, race/thread safety, Android permissions or physical device behavior. **Physical mic/speaker/barge-in: NOT RUN.**
- No direct consumer ChatGPT Voice hook, device approval, or inferred execution authority is claimed.

Related: [Lawnchair task #17](https://github.com/kvnloo/lawnchair/issues/17), [z0live API](https://github.com/kvnloo/z0live/blob/main/docs/API.md).
