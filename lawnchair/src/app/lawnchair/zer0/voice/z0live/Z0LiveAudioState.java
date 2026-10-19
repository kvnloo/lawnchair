package app.lawnchair.zer0.voice.z0live;

/** Keeps the owner's mute choice independent of temporary playback/focus suppression. */
public final class Z0LiveAudioState {
    private boolean muted;
    private boolean playingInbox;
    private boolean focus = true;
    private boolean connected;

    public boolean isMuted() { return muted; }
    public void toggleMute() { muted = !muted; }
    public void setPlayingInbox(boolean value) { playingInbox = value; }
    public void setFocus(boolean value) { focus = value; }
    public void setConnected(boolean value) { connected = value; }
    public boolean microphoneEnabled() { return connected && focus && !muted && !playingInbox; }
    public boolean realtimeSpeakerEnabled() { return focus && !playingInbox; }
}
