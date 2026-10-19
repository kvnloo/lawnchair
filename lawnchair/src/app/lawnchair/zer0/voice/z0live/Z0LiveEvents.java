package app.lawnchair.zer0.voice.z0live;

import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/** Converts local state and provider events into the broker's closed event vocabulary. */
public final class Z0LiveEvents {
    public static JSONObject create(String sessionId, String source, String text, JSONObject data) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        try {
            String type;
            switch (source) {
                case "conversation.item.input_audio_transcription.completed": type = "user_transcript"; break;
                case "response.audio_transcript.done":
                case "response.output_audio_transcript.done":
                case "response.output_text.done": type = "assistant_transcript"; break;
                case "tool_call": case "tool_result": case "feedback": case "summary": type = source; break;
                default: type = "app_state";
            }
            data.put("event_id", UUID.randomUUID().toString()).put("source", source);
            if ("session_end".equals(source)) data.put("session_end", true);
            return new JSONObject().put("session_id", sessionId).put("ts", System.currentTimeMillis() / 1000.0)
                    .put("type", type).put("text", text).put("data", data);
        } catch (JSONException e) { throw new IllegalArgumentException("Invalid event", e); }
    }
}
