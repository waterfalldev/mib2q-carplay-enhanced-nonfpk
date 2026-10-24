/* NativeRouteStop: Audi route guidance stops on each change of CarPlay's navigation owner to
 * the phone, never on the car taking it or on a repeated report (logs latest, 2026-10-24). */
package com.luka.carplay.rgd;

public final class NativeRouteStopTest {
    private static int stops;
    private static boolean guiding;

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what + " (stops " + stops + ")");
    }

    /* One owner report, the stop run inline as the worker thread would. */
    private static void report(int owner) {
        if (NativeRouteStop.phoneTookNavigation(owner)) NativeRouteStop.stopNative();
    }

    public static void main(String[] args) {
        NativeRouteStop.setNativeNavigation(new NativeRouteStop.NativeNavigation() {
            public boolean stopIfGuiding() {
                if (!guiding) return false;
                guiding = false;
                stops++;
                return true;
            }
        });

        report(2);                                   /* CarPlay route at connect, no Audi route */
        check(stops == 0, "no Audi route: nothing to stop");
        guiding = true;                              /* the MMI resumes its saved route ... */
        report(1);                                   /* ... and takes navigation: the phone yields */
        check(stops == 0 && guiding, "the car taking navigation keeps the Audi route");
        report(2);                                   /* the phone takes it back */
        check(stops == 1 && !guiding, "the phone taking navigation stops the Audi route");
        guiding = true;
        report(2);                                   /* a repeated report is no new edge */
        check(stops == 1, "a repeated phone report stops nothing");
        report(1);
        report(2);
        check(stops == 2, "every new edge to the phone stops again");
        guiding = true;
        report(0);                                   /* disconnect */
        report(2);                                   /* next session */
        check(stops == 3, "a new session starts a new edge");

        NativeRouteStop.setNativeNavigation(new NativeRouteStop.NativeNavigation() {
            public boolean stopIfGuiding() throws Exception { throw new IllegalStateException("navigation not up"); }
        });
        report(1);
        report(2);                                   /* a failing stop is logged, not thrown */
        System.out.println("NativeRouteStopTest: Audi guidance stops on each phone takeover PASS");
    }
}
