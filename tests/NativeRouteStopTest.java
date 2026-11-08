/* NativeRouteStop: only the current phone takeover may stop Audi route guidance. */
package com.luka.carplay.rgd;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class NativeRouteStopTest {
    private static int stops;
    private static boolean guiding;
    private static Throwable callbackFailure;
    private static final List<Runnable> queued = new ArrayList<Runnable>();

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what + " (stops " + stops + ")");
    }

    private static void runNext() {
        queued.remove(0).run();
        check(callbackFailure == null, "native callback checks survive the production exception guard: " + callbackFailure);
    }

    private static void report(int owner) {
        NativeRouteStop.onNavigationOwner(owner);
        if (!queued.isEmpty()) runNext();
    }

    private static void routeStop() {
        NativeRouteStop.setNativeNavigation(new NativeRouteStop.NativeNavigation() {
            public boolean stopIfGuiding(NativeRouteStop.Ownership ownership) {
                if (!ownership.isCurrent() || Thread.holdsLock(NativeRouteStop.class)) {
                    callbackFailure = new AssertionError("stale ownership or native callback under owner monitor");
                }
                if (!guiding || !ownership.isCurrent()) return false;
                guiding = false;
                stops++;
                return true;
            }
        });
    }

    private static void delayedWorker(int ownerAfterTakeover, boolean reconnect) {
        report(0);
        guiding = true;
        int before = stops;
        NativeRouteStop.onNavigationOwner(2);
        check(queued.size() == 1, "phone edge queues one worker");
        NativeRouteStop.onNavigationOwner(ownerAfterTakeover);
        if (reconnect) NativeRouteStop.onNavigationOwner(2);
        runNext();
        check(stops == before && guiding, "delayed worker cannot stop route after owner/session changes");
        if (reconnect) {
            runNext();
            check(stops == before + 1 && !guiding, "new phone generation can stop the route");
        }
    }

    public static void main(String[] args) throws Exception {
        NativeRouteStop.setWorkerStarter(new NativeRouteStop.WorkerStarter() {
            public void start(Runnable work) {
                check(!Thread.holdsLock(NativeRouteStop.class), "worker startup runs outside owner monitor");
                queued.add(work);
            }
        });
        routeStop();
        report(2);
        check(stops == 0, "no Audi route: nothing to stop");
        guiding = true;
        report(1);
        check(stops == 0 && guiding, "the car taking navigation keeps the Audi route");
        report(2);
        check(stops == 1 && !guiding, "the phone taking navigation stops the Audi route");
        guiding = true;
        report(2);
        check(stops == 1 && queued.isEmpty(), "a repeated phone report stops nothing");
        report(1);
        report(2);
        check(stops == 2, "every new edge to the phone stops again");
        guiding = true;
        report(0);
        report(2);
        check(stops == 3, "a new session starts a new edge");

        delayedWorker(1, false);
        delayedWorker(0, false);
        delayedWorker(1, true);
        delayedWorker(0, true);

        /* Native lookup may block/re-enter HMI.  The token must remain invalid even if the
         * phone owns navigation again before the lookup returns, with no owner-lock deadlock. */
        final Throwable[] failure = new Throwable[1];
        NativeRouteStop.setNativeNavigation(new NativeRouteStop.NativeNavigation() {
            public boolean stopIfGuiding(NativeRouteStop.Ownership ownership) throws Exception {
                Thread changed = new Thread(new Runnable() {
                    public void run() {
                        try {
                            NativeRouteStop.onNavigationOwner(0);
                            NativeRouteStop.onNavigationOwner(2);
                        } catch (Throwable t) { failure[0] = t; }
                    }
                });
                changed.start();
                changed.join(2000L);
                if (changed.isAlive() || ownership.isCurrent()) {
                    failure[0] = new AssertionError("owner update blocked by native callback or stale token remained current");
                }
                return false;
            }
        });
        report(1);
        report(2);
        check(failure[0] == null, "owner callback completed");
        routeStop();
        runNext();                  /* only the new session's worker remains current */

        NativeRouteStop.setNativeNavigation(new NativeRouteStop.NativeNavigation() {
            public boolean stopIfGuiding(NativeRouteStop.Ownership ownership) throws Exception {
                throw new IllegalStateException("navigation not up");
            }
        });
        report(1);
        report(2);                  /* a failing stop is logged, not thrown */

        /* The production scheduler still runs heavy navigation off the reporting thread. */
        final Thread caller = Thread.currentThread();
        final CountDownLatch completed = new CountDownLatch(1);
        NativeRouteStop.setNativeNavigation(new NativeRouteStop.NativeNavigation() {
            public boolean stopIfGuiding(NativeRouteStop.Ownership ownership) {
                if (Thread.currentThread() == caller || Thread.holdsLock(NativeRouteStop.class)) {
                    failure[0] = new AssertionError("native stop ran on owner dispatcher/monitor");
                }
                completed.countDown();
                return false;
            }
        });
        NativeRouteStop.setWorkerStarter(null);
        NativeRouteStop.onNavigationOwner(1);
        NativeRouteStop.onNavigationOwner(2);
        check(completed.await(2L, TimeUnit.SECONDS), "production worker completed");
        check(failure[0] == null, "production worker stays off dispatcher and owner monitor");
        System.out.println("NativeRouteStopTest: current phone generation only, delayed owner/session workers fenced, off-dispatcher stop PASS");
    }
}
