package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.StandHolder;
import dev.sangwoo.celestialfall.stand.StandManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Every Gold Experience that is out, as this client sees it (2.146.0, user request: "이제 이걸 플레이어 q스킬로 이식하고
 * q누르면 나오게 바꾸고 골익과 플레이어에게 황금색 아우라가 생기게"). It is a stand as Star Platinum is
 * ({@link StandPresence}, whose place beside its user and whose way of following them this keeps): the server says whose
 * is out (StandSummon, tracked on the player: the same Q and the same word); here each one comes out of its user with
 * its pose, floats at their right shoulder a little behind them and goes back ({@link GoldTimeline}). One already out
 * when its user comes into sight is simply there. It follows its user once a tick and is drawn between ticks from the
 * tick before and the tick after, its look worked out for the frame's own moment. Coming out and going back, faint
 * golden afterimages trail it. While it is out it and its user are wrapped in a golden aura (StandAuraLayer's gold),
 * which comes and goes with its glow. It has no skills yet, and no sound (the user's answer when asked).
 */
public final class GoldPresence {
    private static final Map<UUID,Presence> ALL=new HashMap<>();
    private static Set<Integer> seen=new HashSet<>(),seenNow=new HashSet<>();
    private static long ticks;private static Object world;
    private static final GoldExperienceLook ghost=new GoldExperienceLook(),trail=new GoldExperienceLook();
    private GoldPresence(){}
    public static void initialize(){ClientTickEvents.END_CLIENT_TICK.register(GoldPresence::tick);}

    /** Whether this player's Gold Experience is out, as far as this client can tell. */
    static boolean out(PlayerEntity p){return MapClient.enabled()&&StandManager.goldExperience(p)&&!p.isSleeping()&&((StandHolder)p).botand$isStandOut();}
    /** The look this player's stand was last drawn with (null when none is drawn): for the capture tools' logs. */
    public static GoldExperienceLook shown(UUID owner){var s=ALL.get(owner);return s==null||!s.drawn?null:s.puppet.look;}
    /** Seconds since this player's stand last came out or went back, and whether it is out (null when there is none). */
    public static float[] state(UUID owner){var s=ALL.get(owner);return s==null?null:new float[]{(ticks-1-s.since)/20f,s.out?1:0};}
    /** How many are out or going back in this client's sight (for the capture tools' logs). */
    public static int count(){return ALL.size();}

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
                // the frames to come show the tick before this one to this one: the change starts where they start,
                // from the look the last one had got to there, so nothing jumps
                s.look(ticks-1,trail);s.from.set(trail);s.hasFrom=true;s.out=out;s.since=ticks-1;
            }
            // its user stopped in a time stop: it stands as it stood, neither following nor stirring
            if(StandTimeStop.stopped(p))continue;
            s.follow(p);s.puppet.age++;
        }
        ALL.values().removeIf(s->!seenNow.contains(s.entity)||!s.out&&GoldTimeline.gone((ticks-1-s.since)/20f));
        var swap=seen;seen=seenNow;seenNow=swap;
    }

    /**
     * Draws them this frame (StandPresence calls it before the auras are laid, so theirs wrap bodies already drawn);
     * {@code hidden}: none show (a strange space's or a flat frame's cutscene has the screen).
     */
    static void render(MinecraftClient mc,WorldRenderContext c,boolean hidden,List<StandPresence.Drawn> drawn){
        for(var s:ALL.values())s.drawn=false;
        if(hidden||ALL.isEmpty())return;
        float delta=c.tickDelta();var cam=c.camera().getPos();var dispatcher=mc.getEntityRenderDispatcher();
        for(var s:ALL.values()){
            if(!(mc.world.getEntityById(s.entity) instanceof PlayerEntity p)||p.isInvisible())continue;
            var look=s.puppet.look;
            // its user stopped in a time stop: as it stood on the last frame drawn before, nothing of it stirring
            if(StandTimeStop.stopped(p)&&s.heldAt!=null){
                look.set(s.held);s.puppet.place(s.heldAt,s.heldYaw);
                if(look.alpha<.003f||c.frustum()!=null&&!c.frustum().isVisible(s.puppet.getBoundingBox().expand(1.2)))continue;
                s.drawn=true;
                dispatcher.render(s.puppet,s.heldAt.x-cam.x,s.heldAt.y-cam.y,s.heldAt.z-cam.z,s.heldYaw,s.heldDelta,c.matrixStack(),c.consumers(),dispatcher.getLight(s.puppet,s.heldDelta));
                drawn.add(new StandPresence.Drawn(s.puppet,p,s.heldDelta));
                continue;
            }
            s.place(p,delta,look);
            s.held.set(look);s.heldAt=s.puppet.getPos();s.heldYaw=s.puppet.getYaw();s.heldDelta=delta;
            if(look.alpha<.003f)continue;
            if(c.frustum()!=null&&!c.frustum().isVisible(s.puppet.getBoundingBox().expand(1.2)))continue;
            s.drawn=true;
            var at=s.puppet.getPos();float yaw=s.puppet.getYaw();
            dispatcher.render(s.puppet,at.x-cam.x,at.y-cam.y,at.z-cam.z,yaw,delta,c.matrixStack(),c.consumers(),dispatcher.getLight(s.puppet,delta));
            drawn.add(new StandPresence.Drawn(s.puppet,p,delta));
            // coming out or going back: faint golden afterimages where it was a moment ago (the same pose: GeckoLib
            // poses a body once a frame)
            float main=look.out;ghost.set(look);
            for(int i=1;i<=3;i++){
                s.look(ticks-1-i*.7+delta,trail);
                float a=ghost.alpha*Math.min(1,Math.abs(main-trail.out)*6)*(.42f-.11f*i);
                if(a<.02f)continue;
                look.set(ghost);look.out=trail.out;look.alpha=a;look.red=1;look.green=.86f;look.blue=.42f;look.shadow=0;look.glow=0;
                s.put(p,delta,look);
                var g=s.puppet.getPos();
                dispatcher.render(s.puppet,g.x-cam.x,g.y-cam.y,g.z-cam.z,yaw,delta,c.matrixStack(),c.consumers(),dispatcher.getLight(s.puppet,delta));
            }
            look.set(ghost);s.put(p,delta,look);
        }
    }
    /**
     * The golden aura's bearers this frame: each stand that shows and its user, as strong as its glow (none round one
     * stopped in a time stop: its flames would stir). Returns how much brighter it burns as one opens into its pose.
     */
    static float bearers(MinecraftClient mc,WorldRenderContext c,List<StandAuraLayer.Bearer> into){
        float flare=0;
        for(var s:ALL.values()){
            if(!(mc.world.getEntityById(s.entity) instanceof PlayerEntity p)||p.isInvisible()||StandTimeStop.stopped(p))continue;
            var look=s.puppet.look;float k=MathHelper.clamp(look.glow,0,1);
            if(k<.01f)continue;
            if(s.drawn)into.add(new StandAuraLayer.Bearer(s.puppet,StandAuraLayer.STAND,k,false,true));
            if(StandPresence.shows(c,p))into.add(new StandAuraLayer.Bearer(p,StandAuraLayer.USER,k,false,true));
            flare=Math.max(flare,look.glow-1);
        }
        return flare;
    }

    private static final class Presence {
        final int entity;final UUID owner;final GoldPuppet puppet;
        boolean out=true,hasFrom,drawn;long since;
        /** The look when the last change came, which the next one starts from. */
        final GoldExperienceLook from=new GoldExperienceLook();
        /** Where it floats and faces (followed once a tick), and how fast it goes (blocks a second): now and a tick ago. */
        Vec3d pos,prevPos,vel=Vec3d.ZERO,prevVel=Vec3d.ZERO;float yaw,prevYaw;
        /** Its look, place and facing on the last frame drawn and that frame's moment: how it stands while its user is stopped in a time stop. */
        final GoldExperienceLook held=new GoldExperienceLook();Vec3d heldAt;float heldYaw,heldDelta=1;
        Presence(PlayerEntity p,MinecraftClient mc){entity=p.getId();owner=p.getUuid();puppet=new GoldPuppet(mc.world);}

        /** Its look at the client clock {@code clock} (ticks): coming out, at rest, or going back. */
        GoldExperienceLook look(double clock,GoldExperienceLook into){return GoldTimeline.at(out,(float)((clock-since)/20),hasFrom?from:null,into);}
        /** Once a tick: swing round to its user's facing (their head's, as Star Platinum's does) and close in on its place beside them. */
        void follow(PlayerEntity p){
            float target=p.getHeadYaw();
            if(pos==null){yaw=prevYaw=target;pos=prevPos=slot(p,yaw);return;}
            prevPos=pos;prevYaw=yaw;prevVel=vel;
            yaw+=MathHelper.wrapDegrees(target-yaw)*(float)(1-Math.exp(-.05/StandPresence.TURN));
            var slot=slot(p,yaw);
            if(pos.squaredDistanceTo(slot)>StandPresence.JUMP*StandPresence.JUMP){pos=prevPos=slot;vel=prevVel=Vec3d.ZERO;return;}
            pos=pos.add(slot.subtract(pos).multiply(1-Math.exp(-.05/StandPresence.FOLLOW)));
            double dx=pos.x-p.getX(),dz=pos.z-p.getZ(),d=Math.hypot(dx,dz);
            if(d<StandPresence.GAP){
                if(d<1e-4){dx=slot.x-p.getX();dz=slot.z-p.getZ();d=Math.max(1e-4,Math.hypot(dx,dz));}
                pos=new Vec3d(p.getX()+dx/d*StandPresence.GAP,pos.y,p.getZ()+dz/d*StandPresence.GAP);
            }
            vel=vel.add(pos.subtract(prevPos).multiply(20).subtract(vel).multiply(1-Math.exp(-.05/StandPresence.SETTLE)));
        }
        static Vec3d slot(PlayerEntity p,float yaw){
            double r=Math.toRadians(yaw),fx=-Math.sin(r),fz=Math.cos(r);
            // Minecraft's yaw: 0 faces south (+z); the right hand is then west (-x)
            return p.getPos().add(-fz*StandPresence.RIGHT-fx*StandPresence.BACK,StandPresence.UP,fx*StandPresence.RIGHT-fz*StandPresence.BACK);
        }
        /** This frame: the look for the frame's moment, how it leans as it follows, and the body put where it floats. */
        void place(PlayerEntity p,float delta,GoldExperienceLook look){
            if(pos==null)follow(p);
            look(ticks-1d+delta,look);
            var v=prevVel.lerp(vel,delta);float y=MathHelper.lerpAngleDegrees(delta,prevYaw,yaw);
            double r=Math.toRadians(y),fx=-Math.sin(r),fz=Math.cos(r);
            double forward=v.x*fx+v.z*fz,side=-v.x*fz+v.z*fx;
            look.lean=MathHelper.clamp((float)forward*2.4f,-8,16)*look.out;
            look.roll=MathHelper.clamp((float)side*-2f,-10,10)*look.out;
            put(p,delta,look);
        }
        /** The body where the look puts it: {@code out} of the way from its user's feet to its place, facing as it follows. */
        void put(PlayerEntity p,float delta,GoldExperienceLook look){
            var home=p.getLerpedPos(delta);var at=prevPos.lerp(pos,delta);
            puppet.place(home.lerp(at,look.out),MathHelper.lerpAngleDegrees(delta,prevYaw,yaw));
        }
    }
}
