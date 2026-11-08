/* Stock directory updates survive CarPlay startup and reconnect; the phone adapter
 * still presents CarPlay calls independently. Counts are synthetic regression input. */
package com.luka.carplay.telephony;

import de.audi.atip.interapp.combi.bap.phone.CombiBAPServiceAddressBook;
import de.audi.atip.interapp.combi.bap.phone.CombiBAPServicePhone;
import de.audi.atip.interapp.combi.bap.phone.data.CombiBAPPhonebookEntry;
import de.audi.atip.interapp.combi.bap.phone.data.CombiBAPPhonebookEntryDetails;
import de.audi.atip.interapp.combi.bap.phone.data.FsgSetup;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GatedAddressBookTest {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static final class Recorder implements InvocationHandler {
        final List<String> names = new ArrayList<String>();
        final List<Object[]> values = new ArrayList<Object[]>();
        public Object invoke(Object proxy, Method method, Object[] args) {
            names.add(method.getName());
            values.add(args);
            return null;
        }
        CombiBAPServiceAddressBook service() {
            return (CombiBAPServiceAddressBook)Proxy.newProxyInstance(
                CombiBAPServiceAddressBook.class.getClassLoader(),
                new Class[] { CombiBAPServiceAddressBook.class }, this);
        }
        void last(String name, Object... args) {
            int n = names.size() - 1;
            check(n >= 0 && names.get(n).equals(name), "forward " + name);
            check(Arrays.deepEquals(values.get(n), args), "preserve " + name + " arguments");
        }
    }

    private static FsgSetup setup(int connection) {
        return FsgSetup.builder().setHfpConnectionPossible(true).setMobileConnectionType(connection).build();
    }

    private static void scenario(boolean phoneRegistersFirst) {
        final int[] phoneType = { -1 };
        GatedPhoneService phone = new GatedPhoneService((CombiBAPServicePhone)Proxy.newProxyInstance(
            CombiBAPServicePhone.class.getClassLoader(), new Class[] { CombiBAPServicePhone.class },
            new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    if (method.getName().equals("updateFsgSetup"))
                        phoneType[0] = ((FsgSetup)args[0]).getMobileConnectionType();
                    return null;
                }
            }));
        if (phoneRegistersFirst) phone.updateFsgSetup(setup(5));
        Recorder stock = new Recorder();
        GatedAddressBook book = (GatedAddressBook)GatedAddressBook.wrap(stock.service());
        check(GatedAddressBook.wrap(book) == book, "registration must not stack wrappers");
        Object unrelated = new Object();
        check(GatedAddressBook.wrap(unrelated) == unrelated, "unrelated services unchanged");
        book.updateMobileServiceSupport(true, true, true);
        stock.last("updateMobileServiceSupport", true, true, true);
        book.updatePbState(2, 37);
        stock.last("updatePbState", 2, 37);
        int count = stock.names.size();
        if (!phoneRegistersFirst) phone.updateFsgSetup(setup(5));
        check(phoneType[0] == 3, "CarPlay phone adapter still publishes an HFP phone");
        check(stock.names.size() == count, "CarPlay connect must not replace the stock directory");
        phone.updateFsgSetup(setup(5));
        check(stock.names.size() == count, "repeated CarPlay setup must not alter directory");
        book.updatePbState(2, 38);
        stock.last("updatePbState", 2, 38);
        book.updatePhonebookDownloadProgress(1, 2, 3, 4);
        stock.last("updatePhonebookDownloadProgress", 1, 2, 3, 4);
        book.updateMobileServiceSupport(true, false, false);
        stock.last("updateMobileServiceSupport", true, false, false);
        book.updatePbState(0, 0);
        stock.last("updatePbState", 0, 0);
        book.updatePbState(2, 17);
        stock.last("updatePbState", 2, 17);
        count = stock.names.size();
        phone.updateFsgSetup(setup(0));
        check(stock.names.size() == count, "disconnect must not replay or fabricate directory state");
        phone.updateFsgSetup(setup(5));
        check(stock.names.size() == count, "reconnect must not clear stock directory entries");
        book.updateMobileServiceSupport(false, false, false);
        stock.last("updateMobileServiceSupport", false, false, false);
        book.updatePbState(0, 0);
        stock.last("updatePbState", 0, 0);
        book.updatePbState(2, 29);
        stock.last("updatePbState", 2, 29);
        book.updatePhonebookDownloadProgress(5, 6, 7, 8);
        stock.last("updatePhonebookDownloadProgress", 5, 6, 7, 8);
        CombiBAPPhonebookEntry[] entries = new CombiBAPPhonebookEntry[0];
        book.responsePhonebookListElements(9, entries);
        stock.last("responsePhonebookListElements", 9, entries);
        CombiBAPPhonebookEntryDetails[] details = new CombiBAPPhonebookEntryDetails[0];
        book.responsePhonebookEntryDetails(10L, details);
        stock.last("responsePhonebookEntryDetails", 10L, details);
        book.phonebookChanged(11);
        stock.last("phonebookChanged", 11);
        book.pbSpellerResult(12, 13, 14);
        stock.last("pbSpellerResult", 12, 13, 14);
        phone.updateFsgSetup(setup(0));
        book.updatePbState(2, 31);
        stock.last("updatePbState", 2, 31);
    }


    // Exercise Audi's actual count cache, query command and PhonebookHandler.
    // Only the DSI/dispatcher boundaries are replaced; no vehicle runtime starts.
    private static final de.audi.atip.log.LogChannel LOG = new de.audi.atip.log.LogChannel() {
        public void log(int l, String s, Object a, Object b, Object c, Object d,
                        long x, long y, long z, int flags, Throwable t) { }
        public void log(int l, int s, Object a, Object b, Object c, Object d,
                        long x, long y, long z, int flags, Throwable t) { }
    };
    private static final class Dispatcher extends de.esolutions.fw.util.commons.job.DispatcherBase {
        final List<de.audi.tghu.command.CommandList> queued = new ArrayList<de.audi.tghu.command.CommandList>();
        Dispatcher() { super("unused", null, null, null); }
        public de.esolutions.fw.util.commons.job.Job execute(Object value) {
            queued.add((de.audi.tghu.command.CommandList)value);
            return null;
        }
    }
    private static final class Dsi extends de.audi.app.addressbook.core.common.commands.ADBDSIAccess {
        int queries;
        Dsi() { super(null, null); }
        public boolean getViewWindow(long id, int source, int view, int size) {
            check(id == 0L && source == 4 && view == 1 && size == 1, "query the stock phone view");
            queries++;
            return true;
        }
    }
    private static void stockRefresh() throws Exception {
        de.audi.app.addressbook.core.combi.bap.CombiADBHandler handler =
            (de.audi.app.addressbook.core.combi.bap.CombiADBHandler)testsupport.TestSupport.alloc(
                de.audi.app.addressbook.core.combi.bap.CombiADBHandler.class);
        testsupport.TestSupport.set(handler, "log", LOG);
        de.audi.app.addressbook.core.combi.bap.CombiADBStateHandler state =
            new de.audi.app.addressbook.core.combi.bap.CombiADBStateHandler(handler);
        testsupport.TestSupport.set(handler, "combiStateHandler", state);
        de.audi.app.addressbook.core.combi.bap.CombiBAPServiceHandler service =
            new de.audi.app.addressbook.core.combi.bap.CombiBAPServiceHandler(handler, LOG);
        testsupport.TestSupport.set(handler, "combiService", service);
        de.audi.tghu.command.CommandListManager manager = (de.audi.tghu.command.CommandListManager)
            testsupport.TestSupport.alloc(de.audi.tghu.command.CommandListManager.class);
        testsupport.TestSupport.set(manager, "log", LOG);
        // Use an ordinary capturing dispatcher; its constructor starts no thread.
        Dispatcher dispatcher = new Dispatcher();
        testsupport.TestSupport.set(manager, "dispatcher", dispatcher);
        testsupport.TestSupport.set(handler, "cmdListMgr", manager);
        Dsi dsi = (Dsi)testsupport.TestSupport.alloc(Dsi.class);
        testsupport.TestSupport.set(handler, "adbDSIAccess", dsi);
        final de.audi.app.combi.bap.app.phone.list.PhonebookHandler directory =
            (de.audi.app.combi.bap.app.phone.list.PhonebookHandler)testsupport.TestSupport.alloc(
                de.audi.app.combi.bap.app.phone.list.PhonebookHandler.class);
        testsupport.TestSupport.set(directory, "logChannel", LOG);
        testsupport.TestSupport.set(directory, "listAdapters", new ArrayList<Object>());
        final int[] reported = { -1, -1 };
        CombiBAPServiceAddressBook consumer = (CombiBAPServiceAddressBook)GatedAddressBook.wrap(
            Proxy.newProxyInstance(CombiBAPServiceAddressBook.class.getClassLoader(),
                new Class[] { CombiBAPServiceAddressBook.class }, new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) {
                        if (m.getName().equals("updatePbState")) {
                            reported[0] = ((Integer)a[0]).intValue();
                            reported[1] = ((Integer)a[1]).intValue();
                        }
                        if (m.getName().equals("phonebookChanged"))
                            directory.notifyPhonebookChanged(((Integer)a[0]).intValue());
                        return null;
                    }
                }));
        // Audi caches this state while no consumer exists. A later bind must seed
        // both readiness and list size instead of suppressing the unchanged state.
        state.setAdbReady(true);
        state.setEntryCount(37);
        service.setCombiService(consumer);
        check(reported[0] == 2 && reported[1] == 37, "late binding receives current stock state");
        check(dispatcher.queued.size() == 1, "binding queues one fresh query");
        finish(dispatcher.queued.remove(0), 37);
        check(directory.getCurrentListSize() == 37, "binding seeds Audi's separate directory size");
        int firstQueries = dsi.queries;
        // Reproduce the stock gap: the consumer is empty but the provider still has
        // the same count. Stock true/false query never calls phonebookChanged.
        directory.notifyPhonebookChanged(0);
        de.audi.app.addressbook.core.combi.bap.commands.GetCombiViewSizeCommand.createGetCombiViewSizeCommand(
            handler, true, false);
        finish(dispatcher.queued.remove(0), 37);
        check(directory.getCurrentListSize() == 0, "stock control reproduces missing unchanged-count notification");
        GatedPhoneService phone = new GatedPhoneService((CombiBAPServicePhone)Proxy.newProxyInstance(
            CombiBAPServicePhone.class.getClassLoader(), new Class[] { CombiBAPServicePhone.class },
            new InvocationHandler() { public Object invoke(Object p, Method m, Object[] a) { return null; } }));
        phone.updateFsgSetup(setup(5));
        check(dispatcher.queued.size() == 1, "CarPlay connection queues a fresh directory query");
        phone.updateFsgSetup(setup(5));
        check(dispatcher.queued.size() == 1, "repeated setup does not queue more queries");
        finish(dispatcher.queued.remove(0), 37);
        check(directory.getCurrentListSize() == 37, "unchanged live count repopulates the directory");
        phone.updateFsgSetup(setup(0));
        phone.updateFsgSetup(setup(5));
        finish(dispatcher.queued.remove(0), 0);
        check(directory.getCurrentListSize() == 0 && reported[1] == 0, "empty live phone stays empty, no old count replay");
        check(dsi.queries == firstQueries + 3, "each refresh goes through the actual stock DSI query");
        service.setCombiService(null);
        AddressBookRefresh.refresh();
        check(dispatcher.queued.isEmpty(), "unbound source cannot receive refresh queries");
        testsupport.TestSupport.set(state, "adbReady", false);
        service.setCombiService(consumer);
        AddressBookRefresh.refresh();
        check(dispatcher.queued.isEmpty(), "not-ready stock address book is not queried");
        check(reported[0] == 0 && reported[1] == 0, "binding preserves stock not-ready state");
        state.setAdbReady(true);
        check(dispatcher.queued.size() == 1, "stock readiness retries a deferred refresh");
        finish(dispatcher.queued.remove(0), 37);
        check(directory.getCurrentListSize() == 37, "deferred refresh publishes the live directory");
        service.setCombiService(null);
    }
    private static void finish(de.audi.tghu.command.CommandList list, int liveCount) {
        de.audi.app.addressbook.core.combi.bap.commands.GetCombiViewSizeCommand command =
            (de.audi.app.addressbook.core.combi.bap.commands.GetCombiViewSizeCommand)list.getCommands().iterator().next();
        command.setCommandList((de.audi.tghu.command.ICommandList)Proxy.newProxyInstance(
            de.audi.tghu.command.ICommandList.class.getClassLoader(),
            new Class[] { de.audi.tghu.command.ICommandList.class },
            new InvocationHandler() { public Object invoke(Object p, Method m, Object[] a) { return null; } }));
        command.execute();
        command.getViewWindowResult(0, new org.dsi.ifc.organizer.DataSet[0], liveCount);
    }

    public static void main(String[] args) throws Exception {
        stockRefresh();
        scenario(false); // address book registered before the first CarPlay activation
        scenario(true);  // phone activated before the address book registers
        System.out.println("GatedAddressBookTest: " + checks
            + " checks; stock contacts, support, progress and responses survive both registration orders and reconnect PASS");
    }
}
