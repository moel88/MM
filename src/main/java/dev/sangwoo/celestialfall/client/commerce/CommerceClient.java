package dev.sangwoo.celestialfall.client.commerce;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.commerce.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.VillagerEntityRenderer;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.Comparator;
public final class CommerceClient {
    private static final String[] PROMPTS={"아이템 구매","아이템 판매","스탠드 화살 제작"};
    private static boolean captured,held;
    private static int target=-1,ticks;
    public static void initialize(){
        EntityRendererRegistry.register(CommerceRegistry.SELLER,VillagerEntityRenderer::new);
        EntityRendererRegistry.register(CommerceRegistry.BUYER,VillagerEntityRenderer::new);
        EntityRendererRegistry.register(CommerceRegistry.CRAFTER,VillagerEntityRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(CommerceServer.STATE,(mc,handler,b,response)->{
            int mode=b.readUnsignedByte();long token=b.readLong();int entity=b.readInt();int role=Math.min(2,b.readUnsignedByte());
            int count=b.readVarInt(),fragments=b.readVarInt(),sticks=b.readVarInt();String notice=b.readString(256);
            mc.execute(()->{
                held=false;target=-1;ticks=0;
                if(mode==3){if(mc.currentScreen instanceof CommerceScreen)mc.setScreen(null);if(mc.player!=null&&!notice.isEmpty())mc.player.sendMessage(Text.literal(notice),true);return;}
                if(!available()){send(CommerceServer.CLOSE,0,token);return;}
                if(mc.currentScreen instanceof CommerceScreen s&&s.confirmation==(mode==1))s.updated(token,entity,role,count,fragments,sticks,notice);
                else mc.setScreen(new CommerceScreen(mode==1,token,entity,role,count,fragments,sticks,notice));
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->{captured=held=false;target=-1;ticks=0;});
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(!held)return;
            var npc=nearest();
            if(!available()||mc.currentScreen!=null||!mc.isWindowFocused()||npc==null||npc.getId()!=target){cancel();return;}
            ticks++;if(ticks%4==0)send(CommerceServer.PULSE,target,0);
        });
        HudRenderCallback.EVENT.register((draw,delta)->{
            var mc=MinecraftClient.getInstance();if(!available()||mc.currentScreen!=null||mc.options.hudHidden)return;
            var npc=nearest();if(npc==null||dev.sangwoo.celestialfall.client.loot.FieldLootClient.preferred()!=null)return;
            int x=draw.getScaledWindowWidth()/2,y=draw.getScaledWindowHeight()-92,w=110;
            draw.fill(x-w,y-9,x+w,y+26,0xdb10253d);draw.fill(x-w,y-9,x-w+2,y+26,0xff80dbff);
            draw.drawCenteredTextWithShadow(mc.textRenderer,Text.literal("[ E 길게 누르기 ]  "+PROMPTS[npc.role]),x,y-1,0xffeffbff);
            draw.fill(x-w+10,y+17,x+w-10,y+20,0xff30465b);
            draw.fill(x-w+10,y+17,x-w+10+(int)((w*2-20)*Math.min(1,ticks/(double)TradeRules.HOLD_TICKS)),y+20,0xff7edfff);
        });
    }
    public static boolean available(){var mc=MinecraftClient.getInstance();return MapClient.enabled()&&ProgressClient.ready&&mc.player!=null&&mc.world!=null&&mc.player.isAlive()&&!mc.player.isSpectator()&&!CinematicDirector.isActive()&&!CombatClient.actionLocked()&&ClientPlayNetworking.canSend(CommerceServer.REQUEST);}
    public static MerchantEntity nearest(){
        var mc=MinecraftClient.getInstance();if(!available()||mc.currentScreen!=null)return null;
        return mc.world.getEntitiesByClass(MerchantEntity.class,mc.player.getBoundingBox().expand(TradeRules.REACH),m->m.isAlive()&&mc.player.squaredDistanceTo(m)<=TradeRules.REACH*TradeRules.REACH&&mc.player.canSee(m)).stream().min(Comparator.comparingDouble(mc.player::squaredDistanceTo)).orElse(null);
    }
    /** Consume E only for a nearby NPC. Everywhere else vanilla inventory still receives E. */
    public static boolean key(long window,int key,int action){
        var mc=MinecraftClient.getInstance();if(window!=mc.getWindow().getHandle()||key!=GLFW.GLFW_KEY_E)return false;
        if(action==GLFW.GLFW_RELEASE&&captured){captured=false;cancel();return true;}
        if(captured)return true;
        if(action==GLFW.GLFW_PRESS&&mc.currentScreen==null){var npc=nearest();if(npc!=null){captured=held=true;ticks=0;target=npc.getId();send(CommerceServer.START,target,0);return true;}}
        return false;
    }
    private static void cancel(){if(held)send(CommerceServer.CANCEL,target,0);held=false;target=-1;ticks=0;}
    public static boolean fruitInHand(){var mc=MinecraftClient.getInstance();return MapClient.enabled()&&mc.player!=null&&mc.player.getMainHandStack().isOf(CommerceRegistry.ROKAKAKA);}
    public static boolean clickFruit(){if(!fruitInHand())return false;if(available()&&MinecraftClient.getInstance().currentScreen==null)send(CommerceServer.ASK,0,0);return true;}
    public static void send(int action,int target,long nonce){if(!ClientPlayNetworking.canSend(CommerceServer.REQUEST))return;var b=PacketByteBufs.create();b.writeByte(action);b.writeInt(target);b.writeLong(nonce);ClientPlayNetworking.send(CommerceServer.REQUEST,b);}
    private CommerceClient(){}
}
