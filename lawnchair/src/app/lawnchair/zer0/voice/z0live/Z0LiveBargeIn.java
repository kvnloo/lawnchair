package app.lawnchair.zer0.voice.z0live;

import org.json.JSONArray;

/** WebRTC interruption uses server output-buffer clear, which also truncates unheard conversation audio. */
public final class Z0LiveBargeIn {
    private String responseId;
    private boolean playing;

    public void responseStarted(String id) { responseId = id; }
    public void responseDone() { responseId = null; }
    public void playback(boolean value) { playing = value; }

    public JSONArray interrupt() {
        JSONArray commands = new JSONArray();
        if (responseId != null && !responseId.isEmpty()) {
            commands.put(Z0LiveEventsObject.object("type", "response.cancel", "response_id", responseId));
        }
        if (playing || responseId != null && !responseId.isEmpty()) commands.put(Z0LiveEventsObject.object("type", "output_audio_buffer.clear"));
        responseId = null;
        playing = false;
        return commands;
    }

    public static String vadConfiguration() {
        return Z0LiveEventsObject.object("type", "session.update", "session", Z0LiveEventsObject.object(
                "type", "realtime", "audio", Z0LiveEventsObject.object("input", Z0LiveEventsObject.object(
                        "turn_detection", Z0LiveEventsObject.object("type", "server_vad", "silence_duration_ms", 450,
                                "interrupt_response", true, "create_response", true))))).toString();
    }
}
