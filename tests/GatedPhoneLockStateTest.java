/* GatedPhoneService status fields as stock gives an HFP phone.  LockState: stock's "SIM not
 * available" for the HFP phone CarPlay switched off stays off the cluster while CarPlay owns
 * the tab, which shows "no lock" and declares LockState supported as stock always does, and
 * stock's last value comes back when the gate goes off (logs new, 2026-10-19).  RegisterState:
 * the packet data type stays the car modem's (stock's value, else 255 unknown), never 0
 * "no data service" (logs 01, 2026-10-20). */
package com.luka.carplay.telephony;

import de.audi.atip.interapp.combi.bap.phone.CombiBAPServicePhone;
import de.audi.atip.interapp.combi.bap.phone.data.FsgSetup;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class GatedPhoneLockStateTest {
    private static final List<Integer> sent = new ArrayList<Integer>();
    private static final List<Integer> dataTypes = new ArrayList<Integer>();
    private static boolean[] support;
    private static int missedCalls = -1;

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what + " (lock states " + sent + ", data types " + dataTypes + ")");
    }

    private static FsgSetup setup(int mct) {
        return FsgSetup.builder().setHfpConnectionPossible(true).setMobileConnectionType(mct).build();
    }

    private static GatedPhoneService gate() {
        sent.clear();
        dataTypes.clear();
        support = null;
        CombiBAPServicePhone stock = (CombiBAPServicePhone)Proxy.newProxyInstance(
            CombiBAPServicePhone.class.getClassLoader(), new Class[] { CombiBAPServicePhone.class },
            new InvocationHandler() {
                public Object invoke(Object proxy, Method m, Object[] a) {
                    if (m.getName().equals("updateLockState")) sent.add((Integer)a[0]);
                    if (m.getName().equals("updateRegisterState")) dataTypes.add((Integer)a[2]);
                    if (m.getName().equals("updateMobileServiceSupport")) support = (boolean[])a[0];
                    if (m.getName().equals("updateMissedCallIndication")) missedCalls = ((Integer)a[0]).intValue();
                    return null;
                }
            });
        return new GatedPhoneService(stock);
    }

    private static int lastDataType() { return dataTypes.get(dataTypes.size() - 1); }

    /* A hook EVT_TEL_LISTS frame: recents as {type, ts} pairs, newest first; arrived=false
     * leaves the entries empty, as after the phone's counts-only first ListUpdate. */
    private static void lists(GatedPhoneService g, long[][] recents, boolean arrived) throws Exception {
        StringBuffer t = new StringBuffer("r.n:i:" + recents.length + "\nf.n:i:0\n");
        for (int i = 0; i < recents.length; i++) {
            t.append("r." + i + ".a:s:" + (arrived ? "+100" + i : "") + "\n");
            t.append("r." + i + ".type:i:" + (arrived ? recents[i][0] : 0) + "\n");
            t.append("r." + i + ".ts:u:" + (arrived ? recents[i][1] : 0) + "\n");
        }
        byte[] b = t.toString().getBytes("UTF-8");
        g.setLists(com.luka.carplay.bus.CarplayBus.parseText(b, b.length));
    }

    public static void main(String[] args) throws Exception {
        GatedPhoneService g = gate();
        g.updateFsgSetup(setup(3));                 /* BT phone connected first */
        g.updateLockState(0);
        g.updateRegisterState(1, 0, 6);             /* stock: registered, car modem on LTE */
        check(sent.size() == 1 && sent.get(0) == 0, "gate off: stock's lock state passes");

        g.updateFsgSetup(setup(5));                 /* CarPlay takes the phone */
        check(sent.size() == 2 && sent.get(1) == 0, "gate on: no lock published");
        check(support != null && support[CombiBAPServicePhone.MOBILE_SERVICE_SUPPORT_LOCK_STATE],
              "gate on: LockState declared supported, as stock does");
        check(lastDataType() == 6, "gate on: the car modem's data type kept");

        g.updateRegisterState(0, 0, 2);             /* BT gone; the modem moved */
        check(lastDataType() == 2, "gate on: a new modem data type follows");

        g.updateLockState(8);                       /* CarPlay turned BT off */
        check(sent.size() == 2, "gate on: SIM not available kept from the cluster");

        g.updateFsgSetup(setup(0));                 /* CarPlay gone */
        check(sent.size() == 3 && sent.get(2) == 8, "gate off: stock's last lock state replayed");

        g.updateLockState(1);
        check(sent.size() == 4 && sent.get(3) == 1, "gate off: passes again");

        g = gate();                                 /* CarPlay first: stock sent no RegisterState */
        g.updateFsgSetup(setup(5));
        check(lastDataType() == 255, "gate on without stock's register state: data type unknown");

        /* Missed calls: only those missed during the session are new (logs 2026-10-24). */
        long[][] atConnect = { { 3, 2000 }, { 1, 1000 } };          /* an old missed call on top */
        lists(g, new long[0][], true);                               /* the hook's Identify reset */
        lists(g, atConnect, false);                                  /* counts first */
        check(missedCalls == 0, "counts-only list: no missed calls");
        lists(g, atConnect, true);
        check(missedCalls == 0, "a missed call already in the list at connect is not new");
        lists(g, new long[][] { { 3, 3000 }, { 3, 2000 }, { 1, 1000 } }, true);
        check(missedCalls == 1, "a call missed during the drive is new");
        lists(g, new long[0][], true);                               /* next session */
        lists(g, new long[][] { { 3, 3000 }, { 3, 2000 }, { 1, 1000 } }, true);
        check(missedCalls == 0, "a new session takes its list as seen");
        System.out.println("GatedPhoneLockStateTest: lock state, data type and missed calls as stock gives an HFP phone PASS");
    }
}
