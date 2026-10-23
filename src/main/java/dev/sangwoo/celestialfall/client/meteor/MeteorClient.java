package dev.sangwoo.celestialfall.client.meteor;

import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.commerce.CommerceClient;
import dev.sangwoo.celestialfall.client.loot.FieldLootClient;
import dev.sangwoo.celestialfall.meteor.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

/**
 * The meteors on the client (2.49.0): their falls as the server announces them, their rocks, the effects, mining with
 * the pickaxe (a left click swings at the rock aimed at, and the blow goes to the server as the pickaxe lands, 2.49.1;
 * holding the button keeps striking, one blow every {@link MeteorRules#HIT_GAP} ticks; the pickaxe never breaks the
 * map's blocks and never throws the stand's attack), the prompt by a rock and a short notice of where one is falling.
 */
public final class MeteorClient {
    private static long ticks,lastBlow=-100,lastHint=-100;
    /** The rock the last swing went for, sent to the server as the pickaxe comes down ({@link MiningPose#STRIKE_TICKS}). */
    private static int blowRock=-1;private static long blowAt;
    private static Vec3d noticeAt;private static long noticeTick=-1000;
    private static final long NOTICE_TICKS=110;
    private MeteorClient(){}

    public static void initialize(){
        MeteorFx.initialize();
        EntityRendererRegistry.register(MeteorRegistry.METEOR,MeteorRenderer::new);
        MeteorEntity.EVENTS=MeteorFx::event;
        ClientPlayNetworking.registerGlobalReceiver(MeteorManager.FALL,(mc,handler,b,response)->{
            int slot=b.readVarInt();long cycle=b.readLong();int seed=b.readInt();
            Vec3d from=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()),to=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
            long start=b.readLong();int fall=b.readVarInt();
            mc.execute(()->{
                if(mc.world==null)return;
                double age=Math.max(0,mc.world.getTime()-start);if(age>fall+200)return;
                MeteorFx.fall(slot,cycle,seed,from,to,fall,age);
                noticeAt=to;noticeTick=ticks;
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc->{if(!mc.isPaused())ticks++;strike(mc);MeteorFx.tick(mc);MeteorFx.sounds();});
        ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->{MeteorFx.clear();noticeAt=null;blowRock=-1;});
        WorldRenderEvents.LAST.register(MeteorFx::render);
        HudRenderCallback.EVENT.register(MeteorClient::hud);
    }
    /** The client's own clock in ticks (client ticks, held while the game is paused, plus the part of the next): smooth, never jumps. */
    static double clock(float delta){return ticks+delta;}

    private static boolean pickaxe(MinecraftClient mc){return mc.player!=null&&MapClient.enabled()&&mc.player.getMainHandStack().isOf(MeteorRegistry.PICKAXE);}
    /** Whether the local player holds the meteor pickaxe (its left button is the pickaxe's, not the stand's attack). */
    public static boolean pickaxeInHand(){return pickaxe(MinecraftClient.getInstance());}
    /** A left click: with the pickaxe a blow at the rock aimed at (or a swing at nothing) and nothing else; without it a hint over a rock. */
    public static boolean attack(){
        var mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.world==null||!MapClient.enabled())return false;
        if(!pickaxe(mc)){
            if(aimed(mc)!=null&&ticks-lastHint>40){lastHint=ticks;mc.player.sendMessage(Text.literal("운석 곡괭이를 들고 캐 주세요. 아이템 판매 · 로카에게서 살 수 있어요."),true);}
            return false;
        }
        BotandCamera.prepareInteraction();blow(mc);
        return true;
    }
    /** The attack button held down: with the pickaxe, a blow every few ticks and no block breaking at all. */
    public static boolean breaking(boolean held){
        var mc=MinecraftClient.getInstance();
        if(!pickaxe(mc))return false;
        if(held&&mc.currentScreen==null&&!CombatClient.actionLocked()){BotandCamera.prepareInteraction();blow(mc);}
        return true;
    }
    private static void blow(MinecraftClient mc){
        if(ticks-lastBlow<MeteorRules.HIT_GAP)return;
        // the swing (every client sees it) is the two-handed chop; the blow reaches the rock as the pickaxe lands on it
        lastBlow=ticks;mc.player.swingHand(Hand.MAIN_HAND);
        var rock=aimed(mc);
        blowRock=rock==null?-1:rock.getId();blowAt=ticks+MiningPose.STRIKE_TICKS;
    }
    /** Sends the swing's blow when the pickaxe comes down, if the player still holds it and the rock is still there. */
    private static void strike(MinecraftClient mc){
        if(blowRock<0||ticks<blowAt)return;
        int id=blowRock;blowRock=-1;
        if(mc.world==null||!pickaxe(mc)||!ClientPlayNetworking.canSend(MeteorManager.MINE))return;
        if(!(mc.world.getEntityById(id) instanceof MeteorEntity rock)||!rock.isAlive())return;
        var b=PacketByteBufs.create();b.writeInt(id);ClientPlayNetworking.send(MeteorManager.MINE,b);
    }
    /** The rock under the crosshair, or the nearest one along the player's look, within reach. */
    static MeteorEntity aimed(MinecraftClient mc){
        if(mc.crosshairTarget instanceof EntityHitResult h&&h.getEntity() instanceof MeteorEntity m&&reach(mc,m))return m;
        Vec3d eye=mc.player.getEyePos(),end=eye.add(mc.player.getRotationVec(1).multiply(MeteorRules.REACH+1));
        MeteorEntity best=null;double bestD=1e9;
        for(var e:mc.world.getEntities()){
            if(!(e instanceof MeteorEntity m)||!m.isAlive()||!reach(mc,m))continue;
            var hit=m.getBoundingBox().expand(.2).raycast(eye,end);
            if(hit.isPresent()){double d=hit.get().squaredDistanceTo(eye);if(d<bestD){bestD=d;best=m;}}
        }
        return best;
    }
    private static boolean reach(MinecraftClient mc,MeteorEntity m){return m.getBoundingBox().squaredMagnitude(mc.player.getEyePos())<=MeteorRules.REACH*MeteorRules.REACH;}
    private static MeteorEntity near(MinecraftClient mc,double range){
        MeteorEntity best=null;double bestD=range*range;
        for(var e:mc.world.getEntities())if(e instanceof MeteorEntity m&&m.isAlive()&&!MeteorFx.falling(m.slot(),m.cycle(),0)){double d=m.getBoundingBox().squaredMagnitude(mc.player.getEyePos());if(d<bestD){bestD=d;best=m;}}
        return best;
    }

    private static void hud(DrawContext draw,float delta){
        var mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.world==null||mc.options.hudHidden||!MapClient.enabled())return;
        notice(mc,draw,delta);
        if(mc.currentScreen!=null||CommerceClient.nearest()!=null||FieldLootClient.preferred()!=null)return;
        var rock=near(mc,MeteorRules.REACH+1);if(rock==null)return;
        boolean pick=pickaxe(mc);
        // a little higher than the merchants' prompt: every fragment brings a message to the action bar under it
        int x=draw.getScaledWindowWidth()/2,y=draw.getScaledWindowHeight()-106,w=118;
        draw.fill(x-w,y-9,x+w,y+26,0xdb10253d);draw.fill(x-w,y-9,x-w+2,y+26,0xffffa04a);
        // no count of what is left (2.49.1): the rock may shatter after any crystal but the last
        String line=pick?"[ 좌클릭 ]  운석 캐기":"운석 곡괭이를 들면 캘 수 있어요";
        draw.drawCenteredTextWithShadow(mc.textRenderer,Text.literal(line),x,y-1,0xfffff3e4);
        // the blows into the next crystal
        int segments=MeteorRules.HITS_PER_FRAGMENT,gap=3,width=(w*2-20-gap*(segments-1))/segments;
        for(int i=0;i<segments;i++){int sx=x-w+10+i*(width+gap);draw.fill(sx,y+16,sx+width,y+20,i<rock.hits()?0xffffb24d:0xff34384a);}
    }
    /** "운석이 떨어지고 있어요 · 북동쪽 230m" for a few seconds after one starts to fall, fading in and out. */
    private static void notice(MinecraftClient mc,DrawContext draw,float delta){
        if(noticeAt==null)return;double age=ticks-noticeTick+delta;if(age>NOTICE_TICKS)return;
        double alpha=Math.min(1,age/6)*Math.min(1,(NOTICE_TICKS-age)/14);if(alpha<=.02)return;
        double dx=noticeAt.x-mc.player.getX(),dz=noticeAt.z-mc.player.getZ();
        double bearing=(Math.toDegrees(Math.atan2(dx,-dz))+360)%360;
        String[] names={"북쪽","북동쪽","동쪽","남동쪽","남쪽","남서쪽","서쪽","북서쪽"};
        long metres=Math.round(Math.hypot(dx,dz)/10)*10;
        String text="운석이 떨어지고 있어요  ·  "+(metres<15?"바로 여기":names[(int)Math.round(bearing/45)%8]+" "+metres+"m");
        int x=draw.getScaledWindowWidth()/2,y=(int)(draw.getScaledWindowHeight()*.22);
        int a=(int)(alpha*255),w=mc.textRenderer.getWidth(text)/2+12;
        draw.fill(x-w,y-6,x+w,y+14,(int)(alpha*.8*255)<<24|0x2a1208);
        draw.fill(x-w,y+13,x+w,y+14,a<<24|0xffa04a);
        draw.drawCenteredTextWithShadow(mc.textRenderer,Text.literal(text),x,y,a<<24|0xfff1dc);
    }
}
