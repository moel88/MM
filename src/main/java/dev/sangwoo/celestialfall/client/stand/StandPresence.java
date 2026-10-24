package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.stand.StandHolder;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StandSummon;
import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Every Star Platinum that is out, as this client sees it (2.51.0, user request: "스타플래티나가 있는 상태에서 q 누르면
 * 플레이어 옆에 자세 잡으면서 계속 나와있고"). The server says whose stand is out (StandSummon, tracked on the player); here
 * each one comes out with its pose, floats at its user's right shoulder a little behind them and goes back
 * (StandTimeline). A stand already out when its user comes into sight is simply there. It follows its user once a tick
 * (swinging round as they turn, trailing a little as they move, never inside them) and is drawn between ticks from the
 * tick before and the tick after, with its look worked out for the frame's own moment, so it moves at the frame rate.
 * Coming out and going back, faint blue afterimages trail it. While it is out it and its user are wrapped in its aura
 * (StandAuraLayer, 2.53.0), which comes and goes with its glow.
 */
public final class StandPresence {
    /** Its place by its user (blocks): to their right, behind them, above their feet. */
    static final double RIGHT=.85,BACK=.55,UP=.2;
    /** How quickly (seconds to close two thirds of the way) it swings round as its user turns, catches up as they move, and its speed settles. */
    static final double TURN=.14,FOLLOW=.09,SETTLE=.12;
    /** Never nearer its user's middle than this (blocks): it trails them, it does not pass through them. */
    static final double GAP=.7;
    /** A user who moves further than this in a tick (blocks) was moved there: the stand goes straight to its place. */
    static final double JUMP=8;
    private static final Map<UUID,Presence> ALL=new HashMap<>();
    /** The bodies (entity ids) in sight last tick: a stand that comes out on one of them comes out with its pose. */
    private static Set<Integer> seen=new HashSet<>(),seenNow=new HashSet<>();
    private static long ticks;private static Object world;private static boolean held;
    private static final StarPlatinumLook ghost=new StarPlatinumLook(),trail=new StarPlatinumLook();
    private StandPresence(){}

    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(StandPresence::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(StandPresence::render);
    }

    /** Q for Star Platinum's user (Ctrl+Q still drops the stack): asks the server to bring it out or send it back. */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(key!=GLFW.GLFW_KEY_Q||window!=mc.getWindow().getHandle())return false;
        if(action==GLFW.GLFW_RELEASE){if(!held)return false;held=false;return true;}
        if(held)return true;
        if(action!=GLFW.GLFW_PRESS||mc.currentScreen!=null||(modifiers&GLFW.GLFW_MOD_CONTROL)!=0||!StandManager.starPlatinum(mc.player))return false;
        held=true;
        if(CombatClient.combatInput()&&ClientPlayNetworking.canSend(StandSummon.TOGGLE))ClientPlayNetworking.send(StandSummon.TOGGLE,PacketByteBufs.empty());
        return true;
    }

    /** Whether this player's stand is out, as far as this client can tell. */
    static boolean out(PlayerEntity p){return MapClient.enabled()&&StandManager.starPlatinum(p)&&!p.isSleeping()&&((StandHolder)p).botand$isStandOut();}
    /** The look this player's stand was last drawn with (null when none is drawn): for the capture tools' logs. */
    public static StarPlatinumLook shown(UUID owner){var s=ALL.get(owner);return s==null||!s.drawn?null:s.puppet.look;}
    /** For the capture tools' logs: its user's facing, the stand's own facing (degrees) and its velocity (blocks a second). */
    public static String describe(UUID owner){
        var s=ALL.get(owner);var w=MinecraftClient.getInstance().world;if(s==null||s.pos==null||w==null)return null;
        var p=w.getEntityById(s.entity);
        return String.format(java.util.Locale.ROOT,"user yaw %.1f head %.1f, stand yaw %.1f, velocity x %.2f z %.2f",p==null?Float.NaN:p.getYaw(),p==null?Float.NaN:p.getHeadYaw(),s.yaw,s.vel.x,s.vel.z);
    }
    /** How strongly this player's stand and the player are wrapped in its aura (0 to 1; for the capture tools' logs). */
    public static float aura(UUID owner){var s=ALL.get(owner);return s==null||!s.drawn&&s.puppet.look.alpha<.003f?0:auraOf(s.puppet.look);}
    /** The aura's strength for a look: its glow, up to full (the flare past it burns brighter instead, StandAuraLayer). */
    static float auraOf(StarPlatinumLook look){return MathHelper.clamp(look.glow,0,1);}
    /** Seconds since this player's stand last came out or went back, and whether it is out (null when there is none). */
    public static float[] state(UUID owner){var s=ALL.get(owner);return s==null?null:new float[]{(ticks-1-s.since)/20f,s.out?1:0};}

    private static void tick(MinecraftClient mc){
        if(mc.world!=world){ALL.clear();seen.clear();world=mc.world;}
        if(mc.world==null||mc.isPaused())return;
        ticks++;
        seenNow.clear();
        for(var p:mc.world.getPlayers()){
            seenNow.add(p.getId());
            boolean out=out(p);
            var s=ALL.get(p.getUuid());
            if(s!=null&&s.entity!=p.getId()){ALL.remove(p.getUuid());s=null;}
            if(s==null){
                if(!out)continue;
                s=new Presence(p,mc);
                // out before this body came into sight (a respawned or newly seen player): already at rest
                s.since=seen.contains(p.getId())?ticks-1:Long.MIN_VALUE/4;
                ALL.put(p.getUuid(),s);
            }else if(out!=s.out){
                // the frames to come show the tick before this one to this one (ticks-1 to ticks): the change starts
                // where they start, from the look the last one had got to there, so nothing jumps
                s.look(ticks-1,trail);s.from.set(trail);s.hasFrom=true;s.out=out;s.since=ticks-1;
            }
            s.follow(p);s.puppet.age++;
        }
        ALL.values().removeIf(s->!seenNow.contains(s.entity)||!s.out&&StandTimeline.gone((ticks-1-s.since)/20f));
        var swap=seen;seen=seenNow;seenNow=swap;
    }

    private static void render(WorldRenderContext c){
        if(ALL.isEmpty())return;
        var mc=MinecraftClient.getInstance();if(mc.world==null||c.consumers()==null)return;
        float delta=c.tickDelta();var cam=c.camera().getPos();var dispatcher=mc.getEntityRenderDispatcher();
        for(var s:ALL.values()){
            s.drawn=false;
            if(!(mc.world.getEntityById(s.entity) instanceof PlayerEntity p)||p.isInvisible())continue;
            var look=s.puppet.look;s.place(p,delta,look);
            if(look.alpha<.003f)continue;
            if(c.frustum()!=null&&!c.frustum().isVisible(s.puppet.getBoundingBox().expand(1.2)))continue;
            s.drawn=true;
            var at=s.puppet.getPos();float yaw=s.puppet.getYaw();
            dispatcher.render(s.puppet,at.x-cam.x,at.y-cam.y,at.z-cam.z,yaw,delta,c.matrixStack(),c.consumers(),dispatcher.getLight(s.puppet,delta));
            // coming out or going back: faint blue afterimages where it was a moment ago (the same pose, GeckoLib keeps it for the frame)
            float main=look.out;ghost.set(look);
            for(int i=1;i<=3;i++){
                s.look(ticks-1-i*.7+delta,trail);
                float a=ghost.alpha*Math.min(1,Math.abs(main-trail.out)*6)*(.42f-.11f*i);
                if(a<.02f)continue;
                look.set(ghost);look.out=trail.out;look.alpha=a;look.red=.62f;look.green=.78f;look.blue=1;look.shadow=0;look.glow=0;
                s.put(p,delta,look);
                var g=s.puppet.getPos();
                dispatcher.render(s.puppet,g.x-cam.x,g.y-cam.y,g.z-cam.z,yaw,delta,c.matrixStack(),c.consumers(),dispatcher.getLight(s.puppet,delta));
            }
            look.set(ghost);s.put(p,delta,look);
        }
        // the aura round each stand that shows and round its user, as strong as its glow (not the user the camera looks
        // out of: their body is not drawn)
        var bearers=new ArrayList<StandAuraLayer.Bearer>();float flare=0;
        for(var s:ALL.values()){
            if(!(mc.world.getEntityById(s.entity) instanceof PlayerEntity p)||p.isInvisible())continue;
            var look=s.puppet.look;float k=auraOf(look);
            if(k<.01f)continue;
            if(s.drawn)bearers.add(new StandAuraLayer.Bearer(s.puppet,true,k));
            boolean eyes=p==c.camera().getFocusedEntity()&&!c.camera().isThirdPerson();
            if(!eyes&&(c.frustum()==null||c.frustum().isVisible(p.getBoundingBox().expand(1))))bearers.add(new StandAuraLayer.Bearer(p,false,k));
            flare=Math.max(flare,look.glow-1);
        }
        StandAuraLayer.draw(c,bearers,(ticks-1d+delta)/20,flare);
    }

    private static final class Presence {
        final int entity;final StandPuppet puppet;
        boolean out=true,hasFrom,drawn;long since;
        /** The look when the last change came, which the next one starts from. */
        final StarPlatinumLook from=new StarPlatinumLook();
        /** Where it floats and faces (followed once a tick), and how fast it goes (blocks a second): now and a tick ago. */
        Vec3d pos,prevPos,vel=Vec3d.ZERO,prevVel=Vec3d.ZERO;float yaw,prevYaw;
        Presence(PlayerEntity p,MinecraftClient mc){entity=p.getId();puppet=new StandPuppet(mc.world);}

        /** Its look at the client clock {@code clock} (ticks): coming out, at rest, or going back. */
        StarPlatinumLook look(double clock,StarPlatinumLook into){return StandTimeline.at(out,(float)((clock-since)/20),hasFrom?from:null,into);}
        /**
         * Once a tick: swing round to its user's facing and close in on its place beside them. The facing is the head's:
         * the game sets a player's head to the body's facing every tick and sends it on its own, while another player's
         * body facing can stop halfway through a turn (a packet that moves them without turning them sets the turn's goal
         * to wherever it has got to).
         */
        void follow(PlayerEntity p){
            float target=p.getHeadYaw();
            if(pos==null){yaw=prevYaw=target;pos=prevPos=slot(p,yaw);return;}
            prevPos=pos;prevYaw=yaw;prevVel=vel;
            yaw+=MathHelper.wrapDegrees(target-yaw)*(float)(1-Math.exp(-.05/TURN));
            var slot=slot(p,yaw);
            if(pos.squaredDistanceTo(slot)>JUMP*JUMP){pos=prevPos=slot;vel=prevVel=Vec3d.ZERO;return;}
            pos=pos.add(slot.subtract(pos).multiply(1-Math.exp(-.05/FOLLOW)));
            double dx=pos.x-p.getX(),dz=pos.z-p.getZ(),d=Math.hypot(dx,dz);
            if(d<GAP){
                if(d<1e-4){dx=slot.x-p.getX();dz=slot.z-p.getZ();d=Math.max(1e-4,Math.hypot(dx,dz));}
                pos=new Vec3d(p.getX()+dx/d*GAP,pos.y,p.getZ()+dz/d*GAP);
            }
            vel=vel.add(pos.subtract(prevPos).multiply(20).subtract(vel).multiply(1-Math.exp(-.05/SETTLE)));
        }
        static Vec3d slot(PlayerEntity p,float yaw){
            double r=Math.toRadians(yaw),fx=-Math.sin(r),fz=Math.cos(r);
            // Minecraft's yaw: 0 faces south (+z); the right hand is then west (-x)
            return p.getPos().add(-fz*RIGHT-fx*BACK,UP,fx*RIGHT-fz*BACK);
        }
        /** This frame: the look for the frame's moment, how it leans as it follows, and the body put where it floats. */
        void place(PlayerEntity p,float delta,StarPlatinumLook look){
            look(ticks-1d+delta,look);
            var v=prevVel.lerp(vel,delta);float y=MathHelper.lerpAngleDegrees(delta,prevYaw,yaw);
            double r=Math.toRadians(y),fx=-Math.sin(r),fz=Math.cos(r);
            double forward=v.x*fx+v.z*fz,side=-v.x*fz+v.z*fx;
            look.lean=MathHelper.clamp((float)forward*2.4f,-8,16);
            look.roll=MathHelper.clamp((float)side*-2f,-10,10);
            look.speed=(float)Math.hypot(v.x,v.z);
            put(p,delta,look);
        }
        /** The body where the look puts it: {@code out} of the way from its user's feet to its place, facing as it follows. */
        void put(PlayerEntity p,float delta,StarPlatinumLook look){
            var home=p.getLerpedPos(delta);var at=prevPos.lerp(pos,delta);
            var where=home.lerp(at,look.out);float y=MathHelper.lerpAngleDegrees(delta,prevYaw,yaw);
            var b=puppet;
            // setPosition moves its box too (the frustum test and the light go by it)
            b.setPosition(where.x,where.y,where.z);b.prevX=b.lastRenderX=where.x;b.prevY=b.lastRenderY=where.y;b.prevZ=b.lastRenderZ=where.z;
            b.setYaw(y);b.prevYaw=y;b.setBodyYaw(y);b.prevBodyYaw=y;b.setHeadYaw(y);b.prevHeadYaw=y;b.setPitch(0);b.prevPitch=0;
        }
    }
}
