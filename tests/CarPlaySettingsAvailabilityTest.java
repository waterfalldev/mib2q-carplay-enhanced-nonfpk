import com.luka.carplay.connectivity.CarPlaySettingsUi;
import de.audi.atip.base.IFrameworkAccess;
import de.audi.atip.hmi.HMIService;
import de.audi.atip.hmi.event.EventDispatcher;
import de.audi.atip.hmi.event.RunnableEvent;
import de.audi.atip.hmi.model.AbstractModelBank;
import de.audi.atip.hmi.model.ChoiceModel;
import de.audi.atip.hmi.view.Screen;
import de.audi.atip.log.LogChannel;
import de.audi.atip.log.LogChannelFactory;
import de.esolutions.hmi.widgets.audi.base.AbstractWidget;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont;
import de.esolutions.hmi.widgets.audi.evo.ScreenWidgetEVO;
import de.esolutions.hmi.widgets.audi.evo.widgets.ContainerController;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MenuController;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MenuItemController;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MenuItemIndex;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;

/** Real shipping screen builder/models and stock CN/US/EU widgets, without native graphics.
 * The framework event queue and fonts are substituted; no installed settings are written. */
public final class CarPlaySettingsAvailabilityTest {
    static final Class<?> UI = CarPlaySettingsUi.class;
    static final LogChannel LOG = new LogChannel() {
        public void log(int a,String b,Object c,Object d,Object e,Object f,long g,long h,long i,int j,Throwable k) {}
        public void log(int a,int b,Object c,Object d,Object e,Object f,long g,long h,long i,int j,Throwable k) {}
    };
    static final List<RunnableEvent> events = new ArrayList<RunnableEvent>();
    static boolean onHmiThread;
    static int mapMode = 1;
    static IFrameworkAccess framework;
    static ChoiceModel spatial, upmix, maps;
    static int savedMapMode = Integer.MIN_VALUE;
    static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }
    static Field field(Class<?> c, String name) throws Exception {
        while (c != null) {
            try { Field f=c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException e) { c=c.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    static void put(Class<?> c,Object target,String name,Object value) throws Exception { field(c,name).set(target,value); }
    static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m=(target == null ? UI : target.getClass()).getDeclaredMethod(name,types);
        m.setAccessible(true); return m.invoke(target,args);
    }
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> c, InvocationHandler h) {
        return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class[]{c},h);
    }
    static void setup() throws Exception {
        EventDispatcher dispatcher=proxy(EventDispatcher.class,(o,m,a)-> {
            if (m.getName().equals("isDispatchThread")) return onHmiThread;
            if (m.getName().equals("postEvent")) { events.add((RunnableEvent)a[0]); return null; }
            throw new AssertionError(m.getName());
        });
        HMIService hmi=proxy(HMIService.class,(o,m,a)-> {
            if (m.getName().equals("getEventDispatcher")) return dispatcher;
            throw new AssertionError(m.getName());
        });
        de.audi.atip.storage.IStorageAccess storage=proxy(de.audi.atip.storage.IStorageAccess.class,(o,m,a)-> {
            if (m.getName().equals("getInt"))
                return ((Integer)a[1]).intValue()==1002 && savedMapMode!=Integer.MIN_VALUE ? savedMapMode : a[2];
            if (m.getName().equals("setInt")) {
                if (((Integer)a[1]).intValue()==1002) savedMapMode=((Integer)a[2]).intValue();
                return null;
            }
            throw new AssertionError(m.getName());
        });
        framework=proxy(IFrameworkAccess.class,(o,m,a)-> {
            if (m.getName().equals("getLogChannel")) return LOG;
            if (m.getName().equals("getHMIService")) return hmi;
            if (m.getName().equals("getStorageMgr")) return storage;
            if (m.getName().equals("getBundleCxt")) return null;
            if (m.getName().equals("getSysConst") && ((Integer)a[0]).intValue()==541) return mapMode;
            if (m.getName().equals("getLanguageMgr")) return proxy(de.audi.atip.i18n.ILanguageManager.class,(x,method,args)->
                new de.audi.atip.i18n.Language(-1,"en_US","en","English",0,java.util.Locale.US));
            if (m.getReturnType()==int.class) return 1;
            if (m.getReturnType()==boolean.class) return false;
            throw new AssertionError(m.getName());
        });
        AbstractWidget.setFrameworkAccess(framework);
        AbstractModelBank.initLogging(proxy(LogChannelFactory.class,(o,m,a)->LOG));
        // Rendering is outside this host test; the actual screen builder still runs.
        IWrappedFont[][] fonts=new IWrappedFont[7][];
        for(int i=0;i<fonts.length;i++) fonts[i]=new IWrappedFont[0];
        put(Class.forName("de.audi.tghu.connectivity.hmi.evohigh.FontFactory"),null,"fontArrays",fonts);
        put(UI,null,"fw",framework);
        put(UI,null,"pendingSpatial",1);
        put(UI,null,"pendingUpmixChoice",2);
        Object bank=CarPlaySettingsUi.newBank();
        invoke(bank,"createModel",new Class[]{int.class},500);
        invoke(bank,"createModel",new Class[]{int.class},501);
        invoke(bank,"createModel",new Class[]{int.class},502);
        spatial=(ChoiceModel)field(UI,"spatialModel").get(null);
        upmix=(ChoiceModel)field(UI,"upmixModel").get(null);
        maps=(ChoiceModel)field(UI,"altScreenModel").get(null);
    }
    static final class Fixture {
        final MenuController menu=new MenuController();
        final ScreenWidgetEVO screen=new ScreenWidgetEVO(2500140);
        final MenuItemController audio, mix;
        Fixture(int amp) throws Exception {
            put(UI,null,"amplifier",amp);
            ContainerController main=new ContainerController(); main.add(menu); screen.add(main,1);
            Object factory=CarPlaySettingsUi.newFactory(framework);
            invoke(factory,"buildCarPlaySettingsScreen",new Class[]{Screen.class,int.class},screen,0);
            audio=row(CarPlaySettingsUi.MODEL_SPATIAL);
            mix=row(CarPlaySettingsUi.MODEL_UPMIX);
            put(MenuController.class,menu,"focusedIndex",new MenuItemIndex(0,0));
        }
        MenuItemController row(int id) {
            de.audi.atip.hmi.view.HMIView[] views=screen.findViews(id);
            return views==null || views.length==0 ? null : (MenuItemController)views[0];
        }
        void verify(boolean enabled) throws Exception {
            List<Integer> ids=new ArrayList<Integer>();
            if (com.luka.carplay.core.Features.UPMIX) {
                ids.add(2500500); ids.add(2500501);
                check(audio.isEnabled()==enabled && mix.isEnabled()==enabled,"wrong native audio enabled state");
            } else {
                check(audio==null && mix==null,"disabled build must not create audio rows or bindings");
            }
            ids.add(2500502);
            if (mapMode!=1) ids.add(2500504);
            ids.add(2500505);
            if (mapMode!=1) ids.add(2500503);
            ids.add(2500506);
            check(menu.getChildrenSize()==ids.size(),"wrong settings count for cluster mode "+mapMode);
            for(int i=0;i<ids.size();i++) {
                MenuItemController item=row(ids.get(i));
                check(item!=null && menu.getChild(i)==item && item.getParent()==menu,
                    "wrong menu order or binding for model "+ids.get(i));
            }
            check(row(2500502).getType()==(mapMode==1 ? MenuItemController.TYPE_COMBO_BOX : MenuItemController.TYPE_CHECKBOX),
                "MOST needs a three-choice dropdown; VC keeps its boolean control");
            if (mapMode==1) {
                check(java.util.Arrays.equals(row(2500502).getTextIds(),new int[]{2501525,2501526,2501527}),
                    "map mode labels must have the requested display order");
            }
            if (mapMode==1)
                check(row(2500503)==null && row(2500504)==null,"MOST must hide both map card settings");
            check(menu.hasFocusedItem() && menu.getFocusedIndex().equals(new MenuItemIndex(0,0)),"focus lost");
            check(spatial.getValue()==1 && upmix.getValue()==2,"hidden/disabled audio saved values overwritten");
        }
    }

    static void notifyAmp(int amp) throws Exception {
        put(UI,null,"amplifier",amp);
        invoke(null,"requestAudioRowsEnabledState",new Class[0]);
    }
    static void drain() {
        onHmiThread=true;
        try { while(!events.isEmpty()) events.remove(0).dispatch(); }
        finally { onHmiThread=false; }
    }
    static void mapPolicyPersistence() throws Exception {
        mapMode=1;
        check(CarPlaySettingsUi.ALTSCREEN_ON==CarPlaySettingsUi.ALTSCREEN_ALWAYS,
            "legacy On must mean always on, matching VC");
        de.audi.tghu.connectivity.hmi.evohigh.ConnectivityScreenFactory factory=CarPlaySettingsUi.newFactory(framework);
        String[] labels={"Off","When navigating","Always on"};
        int[] modes={CarPlaySettingsUi.ALTSCREEN_OFF,CarPlaySettingsUi.ALTSCREEN_WHEN_NAVIGATING,CarPlaySettingsUi.ALTSCREEN_ALWAYS};
        for(int choice=0;choice<3;choice++) {
            check(labels[choice].equals(factory.getText(2501525+choice)),"wrong dropdown label "+choice);
            maps.itemSelected(choice,0);
            check(savedMapMode==modes[choice] && maps.getValue()==choice,"choice-to-storage mapping failed "+choice);
            put(UI,null,"currentAltScreen",Integer.valueOf(-1));
            CarPlaySettingsUi.boot(framework);
            check(((Integer)field(UI,"currentAltScreen").get(null)).intValue()==modes[choice]
                && maps.getValue()==choice,"map preference did not survive boot "+choice);
            String file=CarPlaySettingsUi.formatSettings(0,0,modes[choice],0,0,1,0);
            check(file.indexOf("\naltscreen="+(choice==0 ? 0 : 1)+"\n")>=0,
                "native settings must retain their boolean enable contract");
        }
        maps.itemSelected(3,0);
        check(savedMapMode==CarPlaySettingsUi.ALTSCREEN_ALWAYS && maps.getValue()==2,
            "invalid choice overwrote the preference");
    }

    public static void main(String[] args) throws Exception {
        setup();
        check(CarPlaySettingsUi.isMostMapEnabled(false) && maps.getValue()==2,"fresh/legacy On defaults to Always on");
        // Early detection, including repeated screen construction, keeps the same models.
        for(int mode:new int[]{1,2}) {
            mapMode=mode;
            for(int amp:new int[]{-1,0,1,2,3,4,5,17,21})
                new Fixture(amp).verify(com.luka.carplay.core.Features.UPMIX && amp>3);
        }
        mapMode=1;
        Fixture f=new Fixture(-1); f.verify(false);
        notifyAmp(21); f.verify(false); check(events.size()==(com.luka.carplay.core.Features.UPMIX ? 1 : 0),"hidden rows must not schedule callbacks; visible rows use HMI queue");
        drain(); f.verify(com.luka.carplay.core.Features.UPMIX);
        notifyAmp(3); f.verify(com.luka.carplay.core.Features.UPMIX); drain(); f.verify(false);
        // Stale queued notifications must not re-enable after an internal/unknown result.
        notifyAmp(21); notifyAmp(-1); drain(); f.verify(false);
        notifyAmp(3); notifyAmp(21); drain(); f.verify(com.luka.carplay.core.Features.UPMIX);
        // Native disabled state must also reject a selection already queued before disable.
        notifyAmp(3);
        spatial.itemSelected(0,0); upmix.itemSelected(0,0);
        drain(); f.verify(false);
        onHmiThread=true;
        try { notifyAmp(21); check(events.isEmpty(),"HMI callback unnecessarily queued"); f.verify(com.luka.carplay.core.Features.UPMIX); }
        finally { onHmiThread=false; }
        new Fixture(3).verify(false);
        new Fixture(21).verify(com.luka.carplay.core.Features.UPMIX);
        mapPolicyPersistence();
        System.out.println("CarPlaySettingsAvailabilityTest: PASS (MOST three-choice map mode, persistence, VC On compatibility, hidden rows and amplifier callbacks)");
    }
}
