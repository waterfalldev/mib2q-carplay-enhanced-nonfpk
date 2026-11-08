/* Refresh the live Audi directory after the cluster phone presentation changes.
 * Never retain contact records or replay a previous phone's count. */
package com.luka.carplay.telephony;

import com.luka.carplay.core.Features;
import com.luka.carplay.framework.Log;
import de.audi.app.addressbook.core.combi.bap.CombiADBHandler;
import de.audi.app.addressbook.core.combi.bap.CombiADBStateHandler;
import de.audi.app.addressbook.core.combi.bap.commands.GetCombiViewSizeCommand;
import de.audi.atip.interapp.combi.bap.phone.CombiBAPServiceAddressBook;

public final class AddressBookRefresh {
    private static volatile CombiADBHandler source;
    private static volatile boolean waitingForReady;

    private AddressBookRefresh() {}

    /** Called after stock setCombiService, including its null/unbind call. */
    public static void bind(CombiADBHandler handler, CombiBAPServiceAddressBook service) {
        if (!Features.PHONE_DATA) return;
        waitingForReady = false;
        source = service == null ? null : handler;
        if (service == null) return;
        try {
            // Stock's last-sent cache survives an absent/replaced consumer. Seed
            // the new consumer even when updateCombiPbState suppressed its update.
            CombiADBStateHandler state = handler.getStateHandler();
            service.updatePbState(state.computePbState(), state.getEntryCount());
            refresh();
        } catch (Throwable t) {
            Log.w("Directory", "bind refresh failed", t);
        }
    }

    /** Retry a startup refresh when Audi actually announces readiness. */
    public static void onStockState(int state) {
        if (state != 0 && waitingForReady) refresh();
    }

    /** One fresh stock query per CarPlay connection; readiness remains Audi's. */
    public static void refresh() {
        if (!Features.PHONE_DATA) return;
        CombiADBHandler handler = source;
        if (handler == null) {
            Log.w("Directory", "refresh: stock service not bound");
            return;
        }
        try {
            CombiADBStateHandler state = handler.getStateHandler();
            if (!state.isAdbReady()) {
                waitingForReady = true;
                // The stock readiness callback will retry with a forced notification.
                Log.w("Directory", "refresh: waiting for stock address book");
                return;
            }
            waitingForReady = false;
            Log.w("Directory", "refresh: querying live stock directory");
            // Audi's sort-order flag forces phonebookChanged even for an unchanged
            // count. Otherwise the cluster can retain its empty list after reconnect.
            // This is a query/notification flag; it does not alter the sort order.
            GetCombiViewSizeCommand.createGetCombiViewSizeCommand(handler, true, true);
        } catch (Throwable t) {
            Log.w("Directory", "query refresh failed", t);
        }
    }
}
