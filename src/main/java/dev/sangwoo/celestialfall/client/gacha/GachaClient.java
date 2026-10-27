package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.menu.MenuClient;
import dev.sangwoo.celestialfall.gacha.GachaRules;
import dev.sangwoo.celestialfall.gacha.GachaServer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import java.util.*;

/** The client's side of the gacha (2.39.0): asks the server for the count or for pulls, and hands what it rolled to the screen. */
public final class GachaClient {
    public static GachaRules.Count count=GachaRules.Count.NEW;
    public static String notice="";
    public static boolean ready,pending;
    private static int serial,ticks,waiting;
    private GachaClient(){}
    public static void initialize(){
        GachaShaders.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(mc->{ticks++;if(pending&&ticks-waiting>100){pending=false;notice="응답이 늦어지고 있어요. 잠시 후 다시 눌러 주세요.";}});
        ClientPlayNetworking.registerGlobalReceiver(GachaServer.RESULT,(mc,h,b,r)->{
            int id=b.readInt();var c=new GachaRules.Count(b.readVarInt(),b.readVarInt(),b.readVarInt());
            int n=b.readUnsignedByte();if(n>GachaRules.TEN)return;
            var grades=new ArrayList<GachaRules.Grade>(n);
            for(int i=0;i<n;i++){int g=b.readUnsignedByte();if(g>=GachaRules.Grade.values().length)return;grades.add(GachaRules.Grade.values()[g]);}
            boolean granted=b.readBoolean();String message=b.readString(160);
            mc.execute(()->{
                if(id!=serial)return;
                count=c;ready=true;pending=false;notice=message;
                if(mc.currentScreen instanceof GachaScreen s)s.answered(List.copyOf(grades),granted);
            });
        });
        // A disconnect can be reported on the network thread (leaving a world does): the screen and the scenes' GPU
        // resources are only touched on the render thread, or the game aborts.
        ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->mc.execute(GachaClient::reset));
    }
    public static void reset(){count=GachaRules.Count.NEW;ready=pending=false;notice="";serial++;var mc=MinecraftClient.getInstance();if(mc.currentScreen instanceof GachaScreen)mc.setScreen(null);GachaStage.end();GachaFont.clear();}
    /** Asks for {@code n} pulls (0: only the count). */
    public static void request(int n){
        if(pending)return;
        if(!ClientPlayNetworking.canSend(GachaServer.REQUEST)){notice="서버에도 보탠드 2.39.0 이상이 필요해요.";return;}
        var b=PacketByteBufs.create();b.writeInt(++serial);b.writeByte(n);pending=true;waiting=ticks;notice="";
        ClientPlayNetworking.send(GachaServer.REQUEST,b);
    }
    public static void open(){
        if(!MenuClient.available())return;
        KeyBinding.unpressAll();MinecraftClient.getInstance().setScreen(new GachaScreen());request(0);
    }
}
