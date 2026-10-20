package app.lawnchair.zer0.voice.z0live;

/** The owner's voice connection setting: Auto by default, Live only when offered, honest failures. */
public final class Z0LiveTransportTest {
    private static int checks;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }

    public static void main(String[] args) {
        check(Z0LiveTransport.parse(null) == Z0LiveTransport.AUTO, "no saved choice means Auto");
        check(Z0LiveTransport.parse("garbage") == Z0LiveTransport.AUTO, "an unknown saved value means Auto");
        for (Z0LiveTransport transport : Z0LiveTransport.values()) {
            check(Z0LiveTransport.parse(transport.key) == transport, "saved key round-trips: " + transport.key);
        }
        check(Z0LiveTransport.parse(" BIDI ") == Z0LiveTransport.BIDI, "keys are case- and space-tolerant");
        check(Z0LiveTransport.AUTO.offerBidi() && Z0LiveTransport.AUTO.fallbackToRealtime(),
                "Auto asks for Live and falls back to Realtime when Live setup fails");
        check(!Z0LiveTransport.REALTIME.offerBidi(), "Realtime never asks the broker for Live");
        check(Z0LiveTransport.BIDI.offerBidi() && !Z0LiveTransport.BIDI.fallbackToRealtime(),
                "Live asks for Live and reports a failed setup instead of switching voices");
        System.out.println("PASS: " + checks + " voice transport setting checks");
    }
}
