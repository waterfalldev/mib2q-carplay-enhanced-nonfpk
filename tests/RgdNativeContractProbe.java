
import com.luka.carplay.rgd.*;
import com.luka.carplay.bus.CarplayBus;
import com.luka.carplay.framework.Log;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.Arrays;
public class RgdNativeContractProbe {
    static void parse(RouteGuidance rg,String file) throws Exception {
        byte[] bytes=Files.readAllBytes(Paths.get(file));
        Method m=RouteGuidance.class.getDeclaredMethod("parse",CarplayBus.Data.class);m.setAccessible(true);
        m.invoke(rg,CarplayBus.parseText(bytes,bytes.length));
    }
    static RouteGuidance.State state(RouteGuidance rg) throws Exception {return (RouteGuidance.State)ManeuverChainAudit.get(rg,"state");}
    static byte[] hud(ManeuverChainAudit audit,RouteGuidance rg) throws Exception {
        audit.sendBap(state(rg));return audit.sender.input[0].sideStreets;
    }
    public static void main(String[] args) throws Exception {
        Log.setLevel(-1);ManeuverChainAudit audit=new ManeuverChainAudit();
        RouteGuidance old=new RouteGuidance();parse(old,args[0]);parse(old,args[1]);
        RouteGuidance.State s=state(old);
        System.out.println("ACTUAL C -> JAVA, missing angle: angle="+s.mTurnAngle[0]+", exit="+s.mExitAngle[0]+", HUD roads="+Arrays.toString(hud(audit,old)));
        if(s.mTurnAngle[0]!=1000 || s.mExitAngle[0]!=1000)throw new AssertionError("C sentinel lost");
        parse(old,args[2]);RouteGuidance fresh=new RouteGuidance();parse(fresh,args[2]);
        byte[] inherited=hud(audit,old),clean=hud(audit,fresh);
        System.out.println("ACTUAL C -> JAVA, known new angle: angle="+s.mTurnAngle[0]+", inherited roads="+Arrays.toString(s.mJunctionAngles[0])+", road="+s.mAfterRoad[0]+", step="+s.mDistance[0]);
        System.out.println("ACTUAL JAVA -> BAP, same new input: reused-slot sideStreets="+Arrays.toString(inherited)+", fresh-slot sideStreets="+Arrays.toString(clean));
        if(!Arrays.equals(inherited,clean) || s.mJunctionAngles[0]!=null || s.mAfterRoad[0]!=null || s.mDistance[0]!=-1)
            throw new AssertionError("new native slot inherited omitted fields");
        RouteGuidance direct=new RouteGuidance();parse(direct,args[0]);parse(direct,args[2]);
        if(!Arrays.equals(hud(audit,direct),clean))throw new AssertionError("known-angle slot reassignment differs from a fresh slot");
        RouteGuidance reset=new RouteGuidance();parse(reset,args[0]);parse(reset,args[3]);
        if(state(reset).routeGeneration!=101 || state(reset).mVer[0]!=2
                || state(reset).mAfterRoad[0]!=null || state(reset).mJunctionAngles[0]!=null)
            throw new AssertionError("native reset generation not received");
        for(int i=4;i<6;i++) {
            // Replace a record that had a junction type with one that omits it.
            parse(reset,args[i]);RouteGuidance.State missing=state(reset);
            audit.reset();audit.sendBap(missing);
            int direction=i==4?ManeuverMapper.DIR_LEFT:ManeuverMapper.DIR_RIGHT;
            if(missing.mJunctionType[0]!=-1 || missing.mTurnAngle[0]!=1000
                    || audit.sender.input[0].mainElement!=ManeuverMapper.TURN
                    || audit.sender.input[0].direction!=direction)
                throw new AssertionError("native missing junction lost the typed HUD turn");
            if(!audit.sendRenderer(missing,0))throw new AssertionError("missing-junction renderer send failed");
            byte[] packet=audit.pendingPacket();
            int angle=(short)(((packet[4]&255)<<8)|(packet[5]&255));
            if(packet[2]!=RendererMapper.ICON_TURN || angle!=(i==4?-180:180))
                throw new AssertionError("native missing junction lost the typed renderer turn");
        }
        System.out.println("Native RGI contract: source sentinel, slot replacement, same-clock route reset, clean HUD descriptors and type-only turns on HUD/renderer PASS");
    }
}
