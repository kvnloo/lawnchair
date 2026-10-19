package app.lawnchair.zer0.voice.z0live;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.audio.JavaAudioDeviceModule;

/** All native WebRTC objects live on one service-owned thread. */
final class Z0LivePeer {
    interface Listener {
        void onOffer(String sdp);
        void onReady();
        void onEvent(String json);
        void onError(String message);
    }

    private final HandlerThread thread = new HandlerThread("z0live-rtc");
    private final Handler handler;
    private final Handler main;
    private final Listener listener;
    private final AtomicBoolean closed = new AtomicBoolean();
    private PeerConnectionFactory factory;
    private JavaAudioDeviceModule device;
    private AudioSource source;
    private AudioTrack microphone;
    private PeerConnection peer;
    private DataChannel channel;
    private boolean offered, iceConnected, channelOpen, notified;

    Z0LivePeer(Context context, Handler main, Listener listener) {
        this.main = main;
        this.listener = listener;
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(() -> initialize(context.getApplicationContext()));
    }

    private void emit(Runnable callback) {
        main.post(() -> { if (!closed.get()) callback.run(); });
    }
    private void fail(String message) { emit(() -> listener.onError(message)); }

    private void initialize(Context context) {
        if (closed.get()) return;
        try {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                    .builder(context).setEnableInternalTracer(false)
                    .setInjectableLogger((message, severity, tag) -> {}, Logging.Severity.LS_NONE)
                    .createInitializationOptions());
            device = JavaAudioDeviceModule.builder(context)
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setEnableVolumeLogger(false)
                    .setAudioRecordErrorCallback(new JavaAudioDeviceModule.AudioRecordErrorCallback() {
                        public void onWebRtcAudioRecordInitError(String e) { fail("Microphone initialization failed"); }
                        public void onWebRtcAudioRecordStartError(JavaAudioDeviceModule.AudioRecordStartErrorCode c, String e) { fail("Microphone could not start"); }
                        public void onWebRtcAudioRecordError(String e) { fail("Microphone recording failed"); }
                    })
                    .setAudioTrackErrorCallback(new JavaAudioDeviceModule.AudioTrackErrorCallback() {
                        public void onWebRtcAudioTrackInitError(String e) { fail("Voice playback initialization failed"); }
                        public void onWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode c, String e) { fail("Voice playback could not start"); }
                        public void onWebRtcAudioTrackError(String e) { fail("Voice playback failed"); }
                    }).createAudioDeviceModule();
            device.setMicrophoneMute(true);
            factory = PeerConnectionFactory.builder().setAudioDeviceModule(device).createPeerConnectionFactory();
            PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(Collections.emptyList());
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            peer = factory.createPeerConnection(config, new PeerConnection.Observer() {
                public void onSignalingChange(PeerConnection.SignalingState state) {}
                public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                    handler.post(() -> {
                        if (closed.get()) return;
                        iceConnected = state == PeerConnection.IceConnectionState.CONNECTED
                                || state == PeerConnection.IceConnectionState.COMPLETED;
                        if (state == PeerConnection.IceConnectionState.FAILED
                                || state == PeerConnection.IceConnectionState.DISCONNECTED) {
                            fail("Voice network connection interrupted. End and start again.");
                        }
                        ready();
                    });
                }
                public void onIceConnectionReceivingChange(boolean receiving) {}
                public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
                    if (state == PeerConnection.IceGatheringState.COMPLETE) handler.post(() -> offer());
                }
                public void onIceCandidate(IceCandidate candidate) {}
                public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
                public void onAddStream(MediaStream stream) {}
                public void onRemoveStream(MediaStream stream) {}
                public void onDataChannel(DataChannel remote) {}
                public void onRenegotiationNeeded() {}
            });
            if (peer == null) { fail("WebRTC peer could not be created"); return; }
            source = factory.createAudioSource(new MediaConstraints());
            microphone = factory.createAudioTrack("z0live-mic", source);
            microphone.setEnabled(false);
            peer.addTrack(microphone, Collections.singletonList("z0live"));
            channel = peer.createDataChannel("oai-events", new DataChannel.Init());
            if (channel == null) { fail("Realtime event channel could not be created"); return; }
            channel.registerObserver(new DataChannel.Observer() {
                public void onBufferedAmountChange(long previous) {}
                public void onStateChange() {
                    handler.post(() -> {
                        if (closed.get()) return;
                        channelOpen = channel.state() == DataChannel.State.OPEN;
                        if (notified && !channelOpen) fail("Realtime event channel closed");
                        ready();
                    });
                }
                public void onMessage(DataChannel.Buffer buffer) {
                    if (buffer.binary || buffer.data.remaining() > 131072) {
                        fail("Unsupported realtime event"); return;
                    }
                    byte[] data = new byte[buffer.data.remaining()];
                    buffer.data.get(data);
                    String json = new String(data, StandardCharsets.UTF_8);
                    emit(() -> listener.onEvent(json));
                }
            });
            MediaConstraints constraints = new MediaConstraints();
            peer.createOffer(new DescriptionObserver() {
                public void onCreateSuccess(SessionDescription description) {
                    handler.post(() -> {
                        if (closed.get()) return;
                        peer.setLocalDescription(new DescriptionObserver() {
                            public void onSetSuccess() {
                                handler.postDelayed(() -> offer(), 5000);
                            }
                        }, description);
                    });
                }
            }, constraints);
        } catch (RuntimeException | LinkageError e) {
            fail("Native voice initialization failed");
        }
    }

    private void offer() {
        if (closed.get() || offered || peer == null || peer.getLocalDescription() == null) return;
        offered = true;
        String sdp = peer.getLocalDescription().description;
        emit(() -> listener.onOffer(sdp));
    }
    private void ready() {
        if (!notified && iceConnected && channelOpen) {
            notified = true;
            emit(() -> listener.onReady());
        }
    }
    void answer(String sdp) {
        handler.post(() -> {
            if (!closed.get()) peer.setRemoteDescription(new DescriptionObserver(),
                    new SessionDescription(SessionDescription.Type.ANSWER, sdp));
        });
    }
    void audio(boolean micEnabled, boolean speakerEnabled) {
        handler.post(() -> {
            if (closed.get() || device == null) return;
            device.setMicrophoneMute(!micEnabled);
            device.setSpeakerMute(!speakerEnabled);
            if (microphone != null) microphone.setEnabled(micEnabled);
        });
    }
    void send(String json) {
        handler.post(() -> {
            if (closed.get()) return;
            if (channel == null || channel.state() != DataChannel.State.OPEN
                    || channel.bufferedAmount() > 262144
                    || !channel.send(new DataChannel.Buffer(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)), false))) {
                fail("Realtime event could not be sent");
            }
        });
    }
    void logDebugAudioStats() {
        if (!com.android.launcher3.BuildConfig.DEBUG) return;
        handler.post(() -> {
            if (closed.get() || peer == null) return;
            peer.getStats(report -> {
                for (org.webrtc.RTCStats stats : report.getStatsMap().values()) {
                    java.util.Map<String, Object> members = stats.getMembers();
                    if ("inbound-rtp".equals(stats.getType()) && "audio".equals(members.get("kind"))) {
                        android.util.Log.i("z0live", "audio_rtp bytes=" + members.get("bytesReceived")
                                + " packets=" + members.get("packetsReceived") + " energy=" + members.get("totalAudioEnergy"));
                    }
                }
            });
        });
    }

    void close(Runnable completed) {
        if (!closed.compareAndSet(false, true)) return;
        handler.post(() -> {
            handler.removeCallbacksAndMessages(null);
            if (device != null) device.setMicrophoneMute(true);
            if (microphone != null) microphone.setEnabled(false);
            if (channel != null) { channel.unregisterObserver(); channel.close(); channel.dispose(); channel = null; }
            if (peer != null) { peer.close(); peer.dispose(); peer = null; }
            if (microphone != null) { microphone.dispose(); microphone = null; }
            if (source != null) { source.dispose(); source = null; }
            if (factory != null) { factory.dispose(); factory = null; }
            if (device != null) { device.release(); device = null; }
            thread.quitSafely();
            main.post(completed);
        });
    }
    private class DescriptionObserver implements SdpObserver {
        public void onCreateSuccess(SessionDescription sdp) {}
        public void onSetSuccess() {}
        public void onCreateFailure(String error) { fail("WebRTC offer failed"); }
        public void onSetFailure(String error) { fail("WebRTC description failed"); }
    }
}
