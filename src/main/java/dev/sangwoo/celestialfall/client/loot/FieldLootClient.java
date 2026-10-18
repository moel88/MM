package dev.sangwoo.celestialfall.client.loot;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.client.commerce.CommerceClient;
import dev.sangwoo.celestialfall.loot.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.Comparator;
public final class FieldLootClient {
    private static boolean captured,held,success;
    private static int target=-1,ticks,noticeTicks;
    private static String notice="";
    public static void initialize(){
        EntityRendererRegistry.register(LootRegistry.FIELD_LOOT,FieldLootRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(LootServer.RESULT,(mc,h,b,r)->{
            boolean ok=b.readBoolean();String text=b.readString(256);mc.execute(()->{held=false;target=-1;ticks=0;success=ok;notice=text;noticeTicks=100;});
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->{captured=held=false;target=-1;ticks=noticeTicks=0;notice="";});
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(noticeTicks>0)noticeTicks--;
            if(!held)return;
            var loot=preferred();
            if(!available()||mc.currentScreen!=null||!mc.isWindowFocused()||loot==null||loot.getId()!=target){cancel();return;}
            ticks++;if(ticks%4==0)send(LootServer.PULSE,target);
            if(ticks>100)cancel();
        });
        HudRenderCallback.EVENT.register((draw,delta)->{
            var mc=MinecraftClient.getInstance();if(!available()||mc.currentScreen!=null||mc.options.hudHidden)return;
            int x=draw.getScaledWindowWidth()/2,y=draw.getScaledWindowHeight()-92;
            var loot=preferred();
            if(loot!=null){
                int w=128;boolean locked=loot.treasure()&&!hasKey();int color=loot.treasure()?0xffffd475:0xffa9dfff;
                draw.fill(x-w,y-11,x+w,y+29,0xe51a2634);draw.fill(x-w,y-11,x-w+2,y+29,color);
                draw.drawCenteredTextWithShadow(mc.textRenderer,Text.literal("[ E 길게 누르기 ]  "+(loot.treasure()?"보물상자":"박스")),x,y-3,0xffffffff);
                draw.drawCenteredTextWithShadow(mc.textRenderer,Text.literal(loot.treasure()?(locked?"상자 키 1개 필요":"상자 키 1개 사용") :"돈 · 경험치 · 랜덤 아이템"),x,y+9,locked?0xffffa39a:0xffb4c7d7);
                draw.fill(x-w+10,y+23,x+w-10,y+25,0xff344859);
                draw.fill(x-w+10,y+23,x-w+10+(int)((w*2-20)*Math.min(1,ticks/(double)LootRules.HOLD_TICKS)),y+25,color);
            }
            if(noticeTicks>0){
                int textWidth=mc.textRenderer.getWidth(notice),w=Math.min(draw.getScaledWindowWidth()-24,textWidth+24),ny=loot==null?y:y-40;
                draw.fill(x-w/2,ny-8,x+w/2,ny+15,0xe519293c);
                // Scale long reward lists so they remain entirely visible at any GUI scale.
                double scale=Math.min(1,(w-20.)/Math.max(1,textWidth));var m=draw.getMatrices();m.push();m.translate(x,ny-1,0);m.scale((float)scale,(float)scale,1);
                draw.drawTextWithShadow(mc.textRenderer,notice,-textWidth/2,0,success?0xffb9edff:0xffffc0ac);m.pop();
            }
        });
    }
    public static boolean available(){return CommerceClient.available()&&ClientPlayNetworking.canSend(LootServer.REQUEST);}
    private static FieldLootEntity nearest(){
        var mc=MinecraftClient.getInstance();if(!available()||mc.currentScreen!=null)return null;
        return mc.world.getEntitiesByClass(FieldLootEntity.class,mc.player.getBoundingBox().expand(LootRules.REACH),e->e.isAlive()&&!e.opened()&&mc.player.squaredDistanceTo(e)<=LootRules.REACH*LootRules.REACH&&mc.player.canSee(e)).stream().min(Comparator.comparingDouble(mc.player::squaredDistanceTo)).orElse(null);
    }
    /** One shared E target: the closer of a loot container and the existing merchant. */
    public static FieldLootEntity preferred(){
        var loot=nearest();if(loot==null)return null;var merchant=CommerceClient.nearest();
        var p=MinecraftClient.getInstance().player;return merchant!=null&&p.squaredDistanceTo(merchant)<=p.squaredDistanceTo(loot)?null:loot;
    }
    public static boolean hasKey(){var p=MinecraftClient.getInstance().player;if(p==null)return false;for(int i=0;i<36;i++)if(p.getInventory().getStack(i).isOf(LootRegistry.CHEST_KEY))return true;return p.getOffHandStack().isOf(LootRegistry.CHEST_KEY);}
    public static boolean key(long window,int key,int action){
        var mc=MinecraftClient.getInstance();if(window!=mc.getWindow().getHandle()||key!=GLFW.GLFW_KEY_E)return false;
        if(action==GLFW.GLFW_RELEASE&&captured){captured=false;cancel();return true;}
        if(captured)return true;
        if(action==GLFW.GLFW_PRESS&&mc.currentScreen==null){var loot=preferred();if(loot!=null){captured=held=true;target=loot.getId();ticks=0;send(LootServer.START,target);return true;}}
        return false;
    }
    private static void cancel(){if(held)send(LootServer.CANCEL,target);held=false;target=-1;ticks=0;}
    public static void send(int action,int id){if(!ClientPlayNetworking.canSend(LootServer.REQUEST))return;var b=PacketByteBufs.create();b.writeByte(action);b.writeInt(id);ClientPlayNetworking.send(LootServer.REQUEST,b);}
    private FieldLootClient(){}
}
