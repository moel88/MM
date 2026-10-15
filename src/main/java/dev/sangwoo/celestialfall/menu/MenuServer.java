package dev.sangwoo.celestialfall.menu;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.progression.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.*;
public final class MenuServer {
    public static final Identifier REQUEST=CelestialFall.id("menu_request_v1"),STATE=CelestialFall.id("menu_state_v1");
    private static final Map<UUID,Long> LAST=new HashMap<>();
    private MenuServer(){}
    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(REQUEST,(s,p,h,b,r)->{if(b.readableBytes()!=6)return;int serial=b.readInt(),action=b.readUnsignedByte(),choice=b.readByte();if(action>3||choice< -1||choice>3)return;s.execute(()->request(p,serial,action,choice));});
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->LAST.remove(h.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->LAST.clear());
    }
    public static void observe(ServerPlayerEntity p){if(!MapGate.enabled(p.getWorld()))return;var s=MenuState.get(p.getServer());s.set(p.getUuid(),s.get(p.getUuid()).observe(ProgressManager.profile(p)));}
    /** Returns a result code; the same validation is used for packet and live-server verification. */
    public static String perform(ServerPlayerEntity p,int action,int choice){
        if(!MapGate.enabled(p.getWorld()))return "전용 맵에서만 사용할 수 있어요.";
        observe(p);if(action==0)return "";
        if(!ProgressManager.canChange(p))return "전투나 컷씬이 끝난 뒤 사용할 수 있어요.";
        if(action==1||action==2){
            var profile=ProgressManager.profile(p);boolean transcend=action==2;
            if(!(transcend?profile.canTranscend():profile.canRebirth()))return transcend?"초월에는 환생 15회가 필요해요.":"환생에는 레벨 50이 필요해요. 환생 15회라면 먼저 초월하세요.";
            ProgressManager.set(p,transcend?profile.transcend():profile.rebirth());
            return transcend?"초월했어요! 환생 횟수가 0으로 돌아갔어요.":"환생했어요! 레벨이 1로 돌아갔어요.";
        }
        if(action==3){
            var s=MenuState.get(p.getServer());var v=s.get(p.getUuid());
            if(choice!= -1&&!v.earned(choice))return "아직 달성하지 않은 업적이에요.";
            s.set(p.getUuid(),v.equip(choice));return choice==-1?"칭호를 해제했어요.":"칭호를 적용했어요.";
        }
        return "지원하지 않는 요청이에요.";
    }
    private static void request(ServerPlayerEntity p,int serial,int action,int choice){
        if(!MapGate.enabled(p.getWorld()))return;
        long now=p.getServerWorld().getTime();Long last=LAST.put(p.getUuid(),now);
        String message=last!=null&&now-last<2?"잠시 후 다시 눌러 주세요.":perform(p,action,choice);
        var value=MenuState.get(p.getServer()).get(p.getUuid());
        if(ServerPlayNetworking.canSend(p,STATE)){
            var b=PacketByteBufs.create();b.writeIdentifier(p.getWorld().getRegistryKey().getValue());b.writeInt(serial);b.writeByte(value.unlocked());b.writeByte(value.equipped());b.writeString(message,160);ServerPlayNetworking.send(p,STATE,b);
        }
    }
}
