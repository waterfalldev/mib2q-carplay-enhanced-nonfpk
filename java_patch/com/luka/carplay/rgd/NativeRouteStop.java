/*
 * NativeRouteStop - Audi route guidance ends when the phone takes CarPlay navigation.
 *
 * The navignore seam in CarplayDSILifecycleController keeps CarPlay's NAVI ownership (app
 * state 2) from the HMI, so stock never cancels its own route guidance when the phone takes
 * "turns".  BAPBridge cancels native guidance once at connect, but the MMI resumes a saved
 * route after that, and both then guided aloud (logs latest, 2026-10-24: the car took turns at
 * 52.8 s, the phone took them back at 62.8 s, the Audi voice kept guiding).
 *
 * Without the seam stock follows "the latest one started wins": the car taking turns makes the
 * phone end its guidance (iOS does that itself), the phone taking turns ends the car's.  This
 * restores the second half: on every change of the owner to the phone, native guidance stops
 * if it has a route.
 *
 * Copyright (c) 2026 LuKa (@LuKa_dev)
 */
package com.luka.carplay.rgd;

import com.luka.carplay.framework.Log;

public final class NativeRouteStop {
    private static final String TAG = "NativeRouteStop";
    /* CarPlay's NAVI owner as DSICarplay reports it: 0 none, 1 the car, 2 the phone. */
    static final int OWNER_PHONE = 2;

    /** The native navigation, replaceable by tests. */
    interface NativeNavigation {
        /** Stops native route guidance; false when there was no route. */
        boolean stopIfGuiding() throws Exception;
    }

    private static NativeNavigation nativeNavigation = new NativeNavigation() {
        public boolean stopIfGuiding() {
            de.audi.tghu.navi.app.Navigation navi = de.audi.tghu.navi.app.Navigation.getInstance();
            de.audi.tghu.navi.app.routeguidance.IRouteManager rm = navi != null ? navi.getRouteManager() : null;
            if (rm == null || rm.getRoute() == null) return false;
            rm.stopRouteGuidance();
            return true;
        }
    };

    private static int owner;

    private NativeRouteStop() {}

    /** CarPlay's NAVI owner (CarplayDSILifecycleController.updateMode), 0 at disconnect. */
    public static void onNavigationOwner(int now) {
        if (!phoneTookNavigation(now)) return;
        /* Off the DSI dispatcher: stopRouteGuidance walks stock's whole stop command list. */
        Thread t = new Thread(new Runnable() {
            public void run() { stopNative(); }
        }, "carplay-native-route-stop");
        t.setDaemon(true);
        t.start();
    }

    /** True on a change of the owner to the phone. */
    static synchronized boolean phoneTookNavigation(int now) {
        boolean took = now == OWNER_PHONE && owner != OWNER_PHONE;
        owner = now;
        return took;
    }

    static void stopNative() {
        try {
            if (nativeNavigation.stopIfGuiding()) {
                Log.w(TAG, "phone took CarPlay navigation: Audi route guidance stopped");
            } else {
                Log.i(TAG, "phone took CarPlay navigation: no Audi route");
            }
        } catch (Throwable t) {
            Log.w(TAG, "phone took CarPlay navigation: stopping Audi route guidance failed: " + t);
        }
    }

    static void setNativeNavigation(NativeNavigation n) { nativeNavigation = n; }
}
