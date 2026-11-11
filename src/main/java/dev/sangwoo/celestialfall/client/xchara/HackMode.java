package dev.sangwoo.celestialfall.client.xchara;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.stand.StandHolder;
import dev.sangwoo.celestialfall.xchara.XChara;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * X Chara's hack as this client knows it (2.115.0, their Q; the server's XCharaHack): who has it on, since when, and
 * where its knife and its panel were drawn this frame. The server keeps it on the player (tracked data), so this client
 * sees it change each tick: one seen a moment ago with it the other way has it come on or go off now (its coming and
 * going drawn from this client's clock, HackLook, and its sound played, loud for its user and fading out to 40 blocks
 * for the rest); one who comes into sight with it already on is just so. Its knife (XCharaKnifeRenderer, HackKnife) and
 * its panel (the item drawn in their other hand: {@link #panel}, HackPanel) are drawn wherever a hand is: in the world
 * (StandHeldItemFeatureMixin, which has their places kept here for the effects, HackFx) and from their own eyes
 * (HeldItemRendererMixin).
 */
public final class HackMode {
    private static final ItemStack PANEL=new ItemStack(XChara.PANEL);
    static final class State{
        boolean on;float changed=-1e6f,seen=-1e6f;
        /** Where the knife was drawn in the world: its heel's middle, its point, and the way from its edge to its spine. */
        Vec3d heel,tip,spine;long knifeFrame=-1;
        /** Where the panel was drawn in the world: its middle, its half width's and half depth's ways (as long as they are, open), its face's way. */
        Vec3d panel,across,along,face;long panelFrame=-1;
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    /** For the capture tools: what this client heard of it (who, on or off, how loud). */
    public static final List<String> HEARD=new ArrayList<>();
    private static long frames;
    private static UUID recording;
    private static Object world;
    private HackMode(){}

    public static void initialize(){
        ClientTickEvents.START_CLIENT_TICK.register(HackMode::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(HackMode::clear));
    }
    static void clear(){STATES.clear();world=null;recording=null;}
    /** This client's clock (ticks, with the frame's share of one). */
    static float clock(float delta){return StandClient.ticks()+delta;}
    /** Whether this player's hack is on, as the server last said. */
    public static boolean on(PlayerEntity p){return p!=null&&MapClient.enabled()&&StandClient.xChara(p)&&p instanceof StandHolder h&&h.botand$isHacking();}

    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!STATES.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        float now=clock(0);
        for(var p:mc.world.getPlayers()){
            UUID id=p.getUuid();var s=STATES.get(id);
            if(s==null&&!StandClient.xChara(p))continue;
            boolean d=on(p);
            // as first seen: just so (one who comes into sight with it on has it on, no coming)
            if(s==null){s=new State();s.on=d;STATES.put(id,s);}
            else if(d!=s.on){
                s.on=d;
                if(now-s.seen<5&&p.isAlive()){s.changed=now;sound(mc,p,d);}else s.changed=-1e6f;
            }
            s.seen=now;
        }
        STATES.values().removeIf(s->now-s.seen>200);
    }
    /** Its sound as it comes on or goes off: loud for its user, fading out to 40 blocks from them for the rest. */
    private static void sound(MinecraftClient mc,PlayerEntity p,boolean on){
        if(mc.player==null)return;
        UUID id=p.getUuid();Vec3d at=p.getPos();
        java.util.function.Supplier<Float> loud=id.equals(mc.player.getUuid())?()->(float)CelestialFall.CONFIG.effectVolume:()->{
            var m=MinecraftClient.getInstance();var user=m.world==null?null:m.world.getPlayerByUuid(id);
            return KnifeClient.loudness(m,user==null?at:user.getPos());
        };
        var v=new XCharaClient.Voice(CelestialFall.id(on?"xchara_q_on":"xchara_q_off"),loud);
        HEARD.add(String.format(java.util.Locale.ROOT,"%s %s at %.2f",p.getGameProfile().getName(),on?"on":"off",v.full()));
        while(HEARD.size()>64)HEARD.remove(0);
        if(v.full()>.01f)mc.getSoundManager().play(v);
    }

    /** This player's hack this frame, or null if nothing of it shows. */
    public static HackLook.Frame look(UUID id,float delta){
        var s=STATES.get(id);if(s==null)return null;
        float now=clock(delta),t=Math.max(0,Math.min((now-s.changed)/20f,1e3f));
        if(!s.on&&t>=HackLook.OFF)return null;
        return new HackLook.Frame(s.on,t,now/20f,(int)(id.getLeastSignificantBits()^id.getMostSignificantBits()>>>17));
    }
    /** What is drawn in this player's other hand: the hack's panel while any of it shows (null: whatever the hand holds). */
    public static ItemStack panel(PlayerEntity p){
        if(p==null||!StandClient.xChara(p)||p.isUsingItem())return null;
        // (the meteor pickaxe takes both hands: no panel then)
        if(p instanceof net.minecraft.client.network.AbstractClientPlayerEntity holder&&dev.sangwoo.celestialfall.client.meteor.MiningPose.holding(holder))return null;
        var f=look(p.getUuid(),MinecraftClient.getInstance().getTickDelta());
        return f!=null&&HackPanel.shown(f)?PANEL:null;
    }
    public static boolean isPanel(ItemStack stack){return stack!=null&&stack.isOf(XChara.PANEL);}
    /** For the capture tools: this player's hack (on, seconds since it changed), or null. */
    public static String describe(PlayerEntity p,float delta){
        var s=STATES.get(p.getUuid());if(s==null)return null;
        return String.format(java.util.Locale.ROOT,"%d,%.2f",s.on?1:0,Math.min(99,(clock(delta)-s.changed)/20f));
    }

    // ───────────────────────────── where it is drawn in the world ─────────────────────────────

    /** The player whose knife or panel the held-item feature is about to draw in the world (null: none). */
    public static void recording(UUID id){recording=id;}
    private static Vec3d world(MatrixStack matrices,float x,float y,float z,Vec3d camera){
        var o=matrices.peek().getPositionMatrix().transformPosition(new Vector3f(x,y,z));
        // back from the view's frame to the world's (the stack's base turns the world to the view)
        RenderSystem.getInverseViewRotationMatrix().transform(o);
        return camera.add(o.x,o.y,o.z);
    }
    private static State recorded(Vec3d at){
        if(recording==null)return null;
        var mc=MinecraftClient.getInstance();if(mc.world==null)return null;
        // (a shadow pass of a shader pack draws from somewhere else: only where the holder is)
        var p=mc.world.getPlayerByUuid(recording);
        if(p==null||at.squaredDistanceTo(p.getLerpedPos(mc.getTickDelta()).add(0,1,0))>16)return null;
        return STATES.get(recording);
    }
    /** The knife as it is being drawn (XCharaKnifeRenderer, at its model's origin): kept for its holder, if it is drawn in the world for one. */
    static void recordKnife(MatrixStack matrices){
        if(recording==null)return;
        Vec3d camera=MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        Vec3d heel=world(matrices,HackKnife.rx(HackKnife.SPINE_X-HackKnife.DEPTH*.5),HackKnife.u(HackKnife.Y_HEEL),0,camera);
        var s=recorded(heel);if(s==null)return;
        Vec3d tip=world(matrices,HackKnife.rx(HackKnife.SPINE_X),HackKnife.u(HackKnife.Y_TIP),0,camera);
        Vec3d edge=world(matrices,HackKnife.rx(HackKnife.SPINE_X-HackKnife.DEPTH),HackKnife.u(HackKnife.Y_HEEL),0,camera);
        Vec3d back=world(matrices,HackKnife.rx(HackKnife.SPINE_X),HackKnife.u(HackKnife.Y_HEEL),0,camera);
        s.heel=heel;s.tip=tip;s.spine=back.subtract(edge);s.knifeFrame=frames;
    }
    /** The panel as it is being drawn (HackPanel, at its middle, its half extents open as given): kept for its holder the same way. */
    static void recordPanel(MatrixStack matrices,float halfWide,float halfDeep){
        if(recording==null)return;
        Vec3d camera=MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        Vec3d mid=world(matrices,0,0,0,camera);
        var s=recorded(mid);if(s==null)return;
        s.panel=mid;s.across=world(matrices,halfWide,0,0,camera).subtract(mid);s.along=world(matrices,0,0,halfDeep,camera).subtract(mid);
        Vec3d up=world(matrices,0,1,0,camera).subtract(mid);s.face=up.lengthSquared()<1e-12?new Vec3d(0,1,0):up.normalize();
        s.panelFrame=frames;
    }
    /** Every frame, before the effects: the players whose hack shows and whose knife (or panel) was drawn in the world this frame. */
    static List<HackFx.Shown> shown(MinecraftClient mc,float delta,Vec3d camera){
        var out=new ArrayList<HackFx.Shown>();
        if(mc.world!=null)for(var e:STATES.entrySet()){
            var s=e.getValue();var f=look(e.getKey(),delta);if(f==null)continue;
            boolean knife=s.knifeFrame==frames&&s.heel!=null,panel=s.panelFrame==frames&&s.panel!=null;
            if(!knife&&!panel)continue;
            if((knife?s.heel:s.panel).squaredDistanceTo(camera)>96*96)continue;
            out.add(new HackFx.Shown(f,knife?s.heel:null,s.tip,s.spine,panel?s.panel:null,s.across,s.along,s.face));
        }
        frames++;
        return out;
    }
}
