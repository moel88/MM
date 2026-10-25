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
 * (StandAuraLayer, 2.53.0), which comes and goes with its glow. Rushing (2.57.0, StandRush), afterimages of its flying
 * arms alone, see-through and violet, throw their own fists beside it.
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
    private static long ticks,frames;private static Object world;private static boolean held;
    /** Where a stand stands this frame and which way it faces, at its size (for the light of its punches). */
    public record Body(Vec3d at,float yaw,float scale){}
    private static final StarPlatinumLook ghost=new StarPlatinumLook(),trail=new StarPlatinumLook();
    /** The look its beatdown's blow leaves it in, should the moment it went back in be past seeing (2.64.0): the blow held. */
    private static final StarPlatinumLook BLOW=new StarPlatinumLook().only(dev.sangwoo.celestialfall.stand.StarPlatinumStance.PUNCH_BIG);
    static{BLOW.glow=1;}
    /** The beatdown's clock (seconds) at this class's client clock {@code clock} (ticks; the frame's is ticks-1+delta). */
    static float beatAge(StandBeatdown.Beat b,double clock){return b.age(0)+(float)((clock-(ticks-1))/20);}
    private static final StandPunch.Shape punchShape=new StandPunch.Shape(),ghostShape=new StandPunch.Shape();
    /** The punches' afterimages (2.54.0; five since 2.55.0): how far back each is (seconds) and how bright. */
    static final float[][] PUNCH_GHOSTS={{.016f,.42f},{.032f,.32f},{.048f,.23f},{.064f,.16f},{.08f,.1f}};
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
    /** Where this player's stand stands this frame (null if it has none, or it was not placed this frame). */
    public static Body body(UUID owner){var s=ALL.get(owner);return s==null||s.bodyFrame!=frames?null:s.body;}
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
                // where they start, from the look the last one had got to there (its punches' stances too), so nothing jumps
                s.look(ticks-1,trail);s.punch(p,0,0,trail,punchShape);s.from.set(trail);s.hasFrom=true;s.out=out;s.since=ticks-1;
            }
            s.follow(p);s.puppet.age++;
        }
        ALL.values().removeIf(s->!seenNow.contains(s.entity)||!s.out&&StandTimeline.gone((ticks-1-s.since)/20f));
        var swap=seen;seen=seenNow;seenNow=swap;
    }

    private static void render(WorldRenderContext c){
        StarPlatinumRenderer.frame();
        if(ALL.isEmpty())return;
        var mc=MinecraftClient.getInstance();if(mc.world==null||c.consumers()==null)return;
        float delta=c.tickDelta();var cam=c.camera().getPos();var dispatcher=mc.getEntityRenderDispatcher();frames++;
        for(var s:ALL.values()){
            s.drawn=false;
            if(!(mc.world.getEntityById(s.entity) instanceof PlayerEntity p)||p.isInvisible())continue;
            var look=s.puppet.look;var shape=s.place(p,delta,look);
            s.body=new Body(s.puppet.getPos(),s.puppet.getYaw(),look.scale);s.bodyFrame=frames;
            if(look.alpha<.003f)continue;
            if(c.frustum()!=null&&!c.frustum().isVisible(s.puppet.getBoundingBox().expand(1.2)))continue;
            s.drawn=true;
            // rushing: the afterimages of its flying arms first (each a body of its own: GeckoLib poses a body once a
            // frame, so the afterimages of the main body below keep its pose)
            for(int i=0;i<StandRush.phantoms();i++){
                var ph=s.phantom(i,mc);var pl=ph.look;pl.set(look);
                float a=StandRush.phantom(p.getUuid(),i,delta,pl)*look.alpha;
                if(a<.02f)continue;
                // (its stances, its arms' sizes and how strongly each arm shows: StandRush.phantom)
                java.util.Arrays.fill(pl.under,0);pl.over=1;
                pl.armsOnly=true;pl.alpha=a;pl.red=.66f;pl.green=.54f;pl.blue=1;pl.shadow=0;pl.glow=0;
                var b=StandRush.phantomBody(s.body,i);
                ph.age=s.puppet.age;ph.place(b.at(),b.yaw());
                dispatcher.render(ph,b.at().x-cam.x,b.at().y-cam.y,b.at().z-cam.z,b.yaw(),delta,c.matrixStack(),c.consumers(),dispatcher.getLight(ph,delta));
            }
            var at=s.puppet.getPos();float yaw=s.puppet.getYaw();
            // (the body itself tells where its Star Finger's fingers are, for their light: StandPunchFx)
            StarPlatinumRenderer.record(p.getUuid());
            dispatcher.render(s.puppet,at.x-cam.x,at.y-cam.y,at.z-cam.z,yaw,delta,c.matrixStack(),c.consumers(),dispatcher.getLight(s.puppet,delta));
            StarPlatinumRenderer.record(null);
            // coming out or going back: faint blue afterimages where it was a moment ago (the same pose, GeckoLib keeps it for the frame)
            float main=look.out;ghost.set(look);
            for(int i=1;i<=3;i++){
                s.look(ticks-1-i*.7+delta,trail);
                float a=ghost.alpha*Math.min(1,Math.abs(main-trail.out)*6)*(.42f-.11f*i);
                if(a<.02f)continue;
                look.set(ghost);look.out=trail.out;look.alpha=a;look.red=.62f;look.green=.78f;look.blue=1;look.shadow=0;look.glow=0;
                s.put(p,delta,look,shape);
                var g=s.puppet.getPos();
                dispatcher.render(s.puppet,g.x-cam.x,g.y-cam.y,g.z-cam.z,yaw,delta,c.matrixStack(),c.consumers(),dispatcher.getLight(s.puppet,delta));
            }
            // punching: afterimages of the body a moment ago, faint violet blue, as each punch speeds in
            float flash=shape==null?0:shape.ghost;
            for(int i=0;i<PUNCH_GHOSTS.length&&flash>.02f;i++){
                // the body as it was that moment ago: its look then, its punches then
                s.look(ticks-1d+delta-PUNCH_GHOSTS[i][0]*20,look);
                var back=s.punch(p,delta,PUNCH_GHOSTS[i][0],look,ghostShape);if(back==null)break;
                look.lean=ghost.lean;look.roll=ghost.roll;look.speed=ghost.speed;
                look.alpha=ghost.alpha*flash*PUNCH_GHOSTS[i][1];look.red=.7f;look.green=.72f;look.blue=1;look.shadow=0;look.glow=0;
                if(look.alpha<.02f)continue;
                s.put(p,delta,look,back);
                var g=s.puppet.getPos();
                dispatcher.render(s.puppet,g.x-cam.x,g.y-cam.y,g.z-cam.z,s.puppet.getYaw(),delta,c.matrixStack(),c.consumers(),dispatcher.getLight(s.puppet,delta));
            }
            look.set(ghost);s.put(p,delta,look,shape);
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
        final int entity;final UUID owner;final StandPuppet puppet;
        /** The afterimages of its flying arms as it rushes (made when first needed). */
        final StandPuppet[] phantoms=new StandPuppet[StandRush.phantoms()];
        boolean out=true,hasFrom,drawn;long since,bodyFrame=-1;Body body;
        /** The look when the last change came, which the next one starts from. */
        final StarPlatinumLook from=new StarPlatinumLook();
        /** Where it floats and faces (followed once a tick), and how fast it goes (blocks a second): now and a tick ago. */
        Vec3d pos,prevPos,vel=Vec3d.ZERO,prevVel=Vec3d.ZERO;float yaw,prevYaw;
        /** The look its blow left it in as its user's beatdown sent it back in (2.64.0), and for which cutscene. */
        final StarPlatinumLook beatFrom=new StarPlatinumLook();Object beatFor;
        final StandPunch.Shape beatShape=new StandPunch.Shape();
        Presence(PlayerEntity p,MinecraftClient mc){entity=p.getId();owner=p.getUuid();puppet=new StandPuppet(mc.world);}
        StandPuppet phantom(int i,MinecraftClient mc){if(phantoms[i]==null)phantoms[i]=new StandPuppet(mc.world);return phantoms[i];}

        /**
         * Its look at the client clock {@code clock} (ticks): coming out, at rest, or going back. Its user's beatdown
         * (2.64.0) sends it back in once its blow has landed and brings it out again at "Star Platinum!"
         * (StandBeatdown.standOut): going back from the look its blow left it in, coming out as from inside its user.
         */
        StarPlatinumLook look(double clock,StarPlatinumLook into){
            var b=StandBeatdown.of(owner);
            if(b!=null){
                float t=beatAge(b,clock);float[] o=StandBeatdown.standOut(b,t);
                if(o!=null)return o[0]>0?StandTimeline.at(true,t-o[1],null,into):StandTimeline.dismiss(t-o[1],beatFor==b?beatFrom:BLOW,into);
            }
            return StandTimeline.at(out,(float)((clock-since)/20),hasFrom?from:null,into);
        }
        /** Whether it is out at the client clock {@code clock} (its user's beatdown takes it in and brings it out, 2.64.0). */
        boolean shownOut(double clock){
            var b=StandBeatdown.of(owner);
            if(b!=null){float[] o=StandBeatdown.standOut(b,beatAge(b,clock));if(o!=null)return o[0]>0;}
            return out;
        }
        /**
         * The look its blow left it in as its user's beatdown sent it back in (2.64.0), worked out once for each
         * cutscene: at rest, the blow's stances on it as it held, when it was sent back (StandBeatdown.RETREAT).
         */
        void beatFrom(PlayerEntity p,float delta){
            var b=StandBeatdown.of(owner);if(b==null||beatFor==b)return;
            float back=b.age(delta)-StandBeatdown.RETREAT;if(back<0)return;
            StandTimeline.at(out,(float)((ticks-1d+delta-back*20-since)/20),hasFrom?from:null,beatFrom);
            var shape=StandInhale.shape(p,delta,back,beatShape);
            if(shape!=null&&shape.engage>0){shape.onto(beatFrom);beatFrom.wind=Math.max(beatFrom.wind,.35f*shape.engage);}
            if(shape!=null)beatFrom.gust=Math.max(beatFrom.gust,shape.gust);
            beatFor=b;
        }
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
        /**
         * The punches on top of the look (2.54.0), and the rush (2.57.0) and the inhale (2.61.0), {@code back} seconds
         * before this frame: their stances while it is out (going back, it curls up from the look it had), the gust of
         * each blow, and what wind it fights in. Returns their shape, for where it stands (null if none shows).
         */
        StandPunch.Shape punch(PlayerEntity p,float delta,float back,StarPlatinumLook look,StandPunch.Shape into){
            // its user's beatdown leads from the grab (2.64.0); before it, its blow (a punch)
            var shape=StandBeatdown.shape(owner,delta,back,into);
            if(shape==null)shape=StandInhale.shape(p,delta,back,into);
            if(shape==null)return null;
            float e=shape.engage;
            if(shownOut(ticks-1d+delta-back*20)&&e>0){
                shape.onto(look);
                look.wind=Math.max(look.wind,.35f*e);
            }
            look.gust=Math.max(look.gust,shape.gust);
            return shape;
        }
        /** This frame: the look for the frame's moment with its punches, how it leans as it follows, and the body put where it floats. */
        StandPunch.Shape place(PlayerEntity p,float delta,StarPlatinumLook look){
            beatFrom(p,delta);
            look(ticks-1d+delta,look);
            var shape=punch(p,delta,0,look,punchShape);
            var v=prevVel.lerp(vel,delta);float y=MathHelper.lerpAngleDegrees(delta,prevYaw,yaw);
            double r=Math.toRadians(y),fx=-Math.sin(r),fz=Math.cos(r);
            double forward=v.x*fx+v.z*fz,side=-v.x*fz+v.z*fx;
            float calm=shape==null?1:1-shape.engage;
            look.lean=MathHelper.clamp((float)forward*2.4f,-8,16)*calm;
            look.roll=MathHelper.clamp((float)side*-2f,-10,10)*calm;
            look.speed=(float)Math.hypot(v.x,v.z);
            put(p,delta,look,shape);
            // the Star Finger (2.60.0): where its fingers go this frame (StarPlatinumRenderer stretches them there)
            look.hasTip=false;
            if(look.fingerOpen>.001f){
                var seen=StandAttack.finger(p.getUuid());
                if(seen!=null){Vec3d tip=StandAttack.fingerTip(MinecraftClient.getInstance(),p,seen,delta);look.fingerX=tip.x;look.fingerY=tip.y;look.fingerZ=tip.z;look.hasTip=true;}
            }
            return shape;
        }
        /**
         * The body where the look puts it: {@code out} of the way from its user's feet to its place, facing as it follows;
         * while it punches ({@code shape}), round to the front of its user on an arc past their shoulder (never through
         * them), to stand right in front of them on the punch's line (2.55.0), dashing on with each blow and facing
         * straight down the punch.
         */
        void put(PlayerEntity p,float delta,StarPlatinumLook look,StandPunch.Shape shape){
            var home=p.getLerpedPos(delta);var at=prevPos.lerp(pos,delta);float y=MathHelper.lerpAngleDegrees(delta,prevYaw,yaw);
            if(shape!=null&&shape.engage>0){
                float e=shape.engage;
                double r=Math.toRadians(shape.yaw),fx=-Math.sin(r),fz=Math.cos(r);
                // where it is now and where it punches from, as a turn round its user and a distance from them
                double ax=at.x-home.x,az=at.z-home.z;
                double fromAngle=Math.atan2(ax*-fz+az*fx,ax*fx+az*fz),fromDist=Math.hypot(ax,az);
                double ahead=shape.ahead+shape.lunge,toAngle=Math.atan2(shape.side,ahead),toDist=Math.hypot(ahead,shape.side);
                double ang=fromAngle+wrapRad(toAngle-fromAngle)*e,dist=fromDist+(toDist-fromDist)*e;
                double ux=Math.cos(ang),us=Math.sin(ang);
                at=new Vec3d(home.x+fx*ux*dist-fz*us*dist,at.y+(home.y+UP+shape.raise-at.y)*e,home.z+fz*ux*dist+fx*us*dist);
                // it turns to face straight down the punch
                y=y+MathHelper.wrapDegrees(shape.yaw-y)*e;
            }
            puppet.place(home.lerp(at,look.out),y);
        }
        static double wrapRad(double a){a%=Math.PI*2;if(a>Math.PI)a-=Math.PI*2;if(a<-Math.PI)a+=Math.PI*2;return a;}
    }
}
