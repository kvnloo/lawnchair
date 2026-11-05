package app.lawnchair.zer0.voice.z0live;

/** Process-local, single-use pairing admission shared by scanned QR codes and VIEW links. */
public final class PendingPairing {
    private PairingValidation.Pairing pending;

    public synchronized void offer(String link) {
        // Validate before replacing a valid pending request. Never persist an unconfirmed credential.
        pending = PairingValidation.parse(link);
    }

    public synchronized PairingValidation.Pairing take() {
        PairingValidation.Pairing value = pending;
        pending = null;
        return value;
    }
}
