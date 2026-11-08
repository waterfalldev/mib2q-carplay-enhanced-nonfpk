/*
 * GatedAddressBook - stock CombiBAPServiceAddressBook adapter (cluster Directory).
 *
 * The directory remains stock during CarPlay: iAP2 recents and favorites do not
 * replace contacts already supplied by Audi's address-book service. A CarPlay
 * connect/disconnect must neither clear those entries nor suppress later updates.
 * GatedPhoneService owns only the independent call/recents/favorites presentation.
 *
 * Copyright (c) 2026 LuKa (@LuKa_dev)
 */
package com.luka.carplay.telephony;

import de.audi.atip.interapp.combi.bap.phone.CombiBAPServiceAddressBook;
import de.audi.atip.interapp.combi.bap.phone.data.CombiBAPPhonebookEntry;
import de.audi.atip.interapp.combi.bap.phone.data.CombiBAPPhonebookEntryDetails;

public class GatedAddressBook implements CombiBAPServiceAddressBook {
    private final CombiBAPServiceAddressBook real;

    public GatedAddressBook(CombiBAPServiceAddressBook r) {
        this.real = r;
    }

    /** Wraps once: a second registration must not stack wrappers. */
    public static Object wrap(Object service) {
        if (!com.luka.carplay.core.Features.PHONE_DATA) return service;
        if (service instanceof GatedAddressBook || !(service instanceof CombiBAPServiceAddressBook)) return service;
        return new GatedAddressBook((CombiBAPServiceAddressBook)service);
    }

    public void updateMobileServiceSupport(boolean pbState, boolean phonebook, boolean speller) {
        real.updateMobileServiceSupport(pbState, phonebook, speller);
    }

    public void updatePbState(int state, int entries) {
        com.luka.carplay.framework.Log.w("Directory", "state=" + state + " entries=" + entries);
        real.updatePbState(state, entries);
        AddressBookRefresh.onStockState(state);
    }

    public void updatePhonebookDownloadProgress(int a, int b, int c, int d) {
        real.updatePhonebookDownloadProgress(a, b, c, d);
    }

    public void responsePhonebookListElements(int i, CombiBAPPhonebookEntry[] e) {
        com.luka.carplay.framework.Log.w("Directory", "list ta=" + i + " entries=" + (e == null ? -1 : e.length));
        real.responsePhonebookListElements(i, e);
    }
    public void responsePhonebookEntryDetails(long l, CombiBAPPhonebookEntryDetails[] d) { real.responsePhonebookEntryDetails(l, d); }
    public void phonebookChanged(int i) {
        com.luka.carplay.framework.Log.w("Directory", "changed entries=" + i);
        real.phonebookChanged(i);
    }
    public void pbSpellerResult(int i, int j, int k) { real.pbSpellerResult(i, j, k); }
}
