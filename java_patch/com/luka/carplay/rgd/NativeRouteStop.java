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
        /** Stops native route guidance; false when there was no route.  Recheck ownership
         * after any native lookup and immediately before entering the stock stop command. */
        boolean stopIfGuiding(Ownership ownership) throws Exception;
    }

    private static volatile NativeNavigation nativeNavigation = new NativeNavigation() {
        public boolean stopIfGuiding(Ownership ownership) {
            de.audi.tghu.navi.app.Navigation navi = de.audi.tghu.navi.app.Navigation.getInstance();
            de.audi.tghu.navi.app.routeguidance.IRouteManager rm = navi != null ? navi.getRouteManager() : null;
            if (rm == null || rm.getRoute() == null) return false;
            /* Native lookups can take time or re-enter HMI ownership callbacks.  Validate
             * again at the last point before entering stock's stop command list. */
            if (!ownership.isCurrent()) return false;
            rm.stopRouteGuidance();
            return true;
        }
    };

    private static int owner;
    private static Ownership currentOwnership;

    /** An identity generation: returning to phone ownership never revives an old worker. */
    static final class Ownership {
        boolean isCurrent() {
            synchronized (NativeRouteStop.class) {
                return owner == OWNER_PHONE && currentOwnership == this;
            }
        }
    }

    interface WorkerStarter { void start(Runnable work); }

    private static final WorkerStarter DEFAULT_WORKER_STARTER = new WorkerStarter() {
        public void start(Runnable work) {
            Thread t = new Thread(work, "carplay-native-route-stop");
            t.setDaemon(true);
            t.start();
        }
    };
    private static volatile WorkerStarter workerStarter = DEFAULT_WORKER_STARTER;

    private NativeRouteStop() {}

    /** CarPlay's NAVI owner (CarplayDSILifecycleController.updateMode), 0 at disconnect. */
    public static void onNavigationOwner(int now) {
        final Ownership ownership;
        synchronized (NativeRouteStop.class) {
            if (now == owner) return;
            owner = now;
            currentOwnership = now == OWNER_PHONE ? new Ownership() : null;
            ownership = currentOwnership;
        }
        if (ownership == null) return;
        /* Off the DSI dispatcher: stopRouteGuidance walks stock's whole stop command list.
         * Neither thread startup nor any native/HMI callback runs under the owner monitor. */
        workerStarter.start(new Runnable() {
            public void run() { stopNative(ownership); }
        });
    }

    static void stopNative(Ownership ownership) {
        if (!ownership.isCurrent()) return;
        /* This admits a current worker into native navigation, which checks once more before
         * stopRouteGuidance.  Once that stock command starts it cannot be revoked here. */
        try {
            if (nativeNavigation.stopIfGuiding(ownership)) {
                Log.w(TAG, "phone took CarPlay navigation: Audi route guidance stopped");
            } else {
                Log.i(TAG, "phone took CarPlay navigation: no Audi route");
            }
        } catch (Throwable t) {
            Log.w(TAG, "phone took CarPlay navigation: stopping Audi route guidance failed: " + t);
        }
    }

    static void setNativeNavigation(NativeNavigation n) { nativeNavigation = n; }
    static void setWorkerStarter(WorkerStarter s) {
        workerStarter = s != null ? s : DEFAULT_WORKER_STARTER;
    }
}
