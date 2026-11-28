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
 * which comes and goes with its glow. It has no sound of its own coming out or going back (the user's answer when asked).
 * <p>
 * (2.147.0) Its muda rush (E; the rush is heard by StandRush, what it does through it is GoldRush): it goes round to
 * the front of its user on an arc past their shoulder (never through them), facing where they face, throws its fists,
 * afterimages of its arms flying with it (each a body of its own: GeckoLib poses a body once a frame), and goes back.
 */
public final class GoldPresence {
    private static final Map<UUID,Presence> ALL=new HashMap<>();
    private static Set<Integer> seen=new HashSet<>(),seenNow=new HashSet<>();
    private static long ticks;private static Object world;
    private static final GoldExperienceLook ghost=new GoldExperienceLook(),trail=new GoldExperienceLook();
    /** Frames drawn (a body's place is this frame's only: {@link #body}). */
    private static long frames;
    private GoldPresence(){}
    public static void initialize(){ClientTickEvents.END_CLIENT_TICK.register(GoldPresence::tick);}

    /** Whether this player's Gold Experience is out, as far as this client can tell. */
    static boolean out(PlayerEntity p){return MapClient.enabled()&&StandManager.goldExperience(p)&&!p.isSleeping()&&((StandHolder)p).botand$isStandOut();}
    /** The look this player's stand was last drawn with (null when none is drawn): for the capture tools' logs. */
    public static GoldExperienceLook shown(UUID owner){var s=ALL.get(owner);return s==null||!s.drawn?null:s.puppet.look;}
    /** Seconds since this player's stand last came out or went back, and whether it is out (null when there is none). */
    public static float[] state(UUID owner){var s=ALL.get(owner);return s==null?null:new float[]{(ticks-1-s.since)/20f,s.out?1:0};}
    /** Where this player's stand was drawn this frame (null if it was not): for its rush's light (GoldRushFx). */
    static StandPresence.Body body(UUID owner){var s=ALL.get(owner);return s==null||s.bodyFrame!=frames?null:s.body;}
    /** For the capture tools: where this player's stand was last drawn (null if it was not), whatever the frame. */
    public static StandPresence.Body lastDrawn(UUID owner){var s=ALL.get(owner);return s==null?null:s.body;}
    /** For the capture tools: where this player's stand was last drawn from them (blocks ahead of them and to their right, the way they face) and how many of its rush's arms' bodies it has. */
    public static String describe(PlayerEntity p,float delta){
        var s=ALL.get(p.getUuid());if(s==null||s.body==null)return "no stand drawn";
        var d=s.body.at().subtract(p.getLerpedPos(delta));double r=Math.toRadians(p.getHeadYaw()),fx=-Math.sin(r),fz=Math.cos(r);int arms=0;for(var a:s.arms)if(a!=null)arms++;
        return String.format(java.util.Locale.ROOT,"stand drawn %.2f ahead, %.2f to the right, %.2f up, %d arms' bodies",d.x*fx+d.z*fz,-d.x*fz+d.z*fx,d.y,arms);
    }
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
        frames++;
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
            var rush=s.place(p,delta,look);
            s.held.set(look);s.heldAt=s.puppet.getPos();s.heldYaw=s.puppet.getYaw();s.heldDelta=delta;
            if(look.alpha<.003f)continue;
            if(c.frustum()!=null&&!c.frustum().isVisible(s.puppet.getBoundingBox().expand(1.2)))continue;
            s.drawn=true;
            var at=s.puppet.getPos();float yaw=s.puppet.getYaw();
            dispatcher.render(s.puppet,at.x-cam.x,at.y-cam.y,at.z-cam.z,yaw,delta,c.matrixStack(),c.consumers(),dispatcher.getLight(s.puppet,delta));
            drawn.add(new StandPresence.Drawn(s.puppet,p,delta));
            s.body=new StandPresence.Body(at,yaw,look.scale);s.bodyFrame=frames;
            // the muda rush (2.147.0): the afterimages of its flying arms, golden, each a body of its own
            if(rush!=null){
                for(int i=0;i<GoldRush.PHANTOMS.length;i++){
                    if(s.arms[i]==null)s.arms[i]=new GoldPuppet(mc.world);
                    var arm=s.arms[i];
                    float a=GoldRush.phantom(rush,i,delta,arm.look)*look.alpha;if(a<.02f)continue;
                    arm.look.armsOnly=true;arm.look.alpha=a;arm.look.out=1;arm.look.scale=look.scale;arm.look.shadow=0;arm.look.glow=0;arm.look.lean=arm.look.roll=0;
                    arm.look.red=GoldRush.RED;arm.look.green=GoldRush.GREEN;arm.look.blue=GoldRush.BLUE;
                    arm.age=s.puppet.age;
                    var b=GoldRush.phantomBody(s.body,i);arm.place(b.at(),b.yaw());
                    dispatcher.render(arm,b.at().x-cam.x,b.at().y-cam.y,b.at().z-cam.z,b.yaw(),delta,c.matrixStack(),c.consumers(),dispatcher.getLight(arm,delta));
                }
            }
            // (2.150.0) the beatdown's rush: the same afterimages of its arms, leaning as it leans
            if(rush==null&&s.beat!=null){
                for(int i=0;i<GoldRush.PHANTOMS.length;i++){
                    if(s.arms[i]==null)s.arms[i]=new GoldPuppet(mc.world);
                    var arm=s.arms[i];
                    float a=GoldBeatdown.phantom(s.beat,i,delta,arm.look)*look.alpha;if(a<.02f)continue;
                    arm.look.armsOnly=true;arm.look.alpha=a;arm.look.out=1;arm.look.scale=look.scale;arm.look.shadow=0;arm.look.glow=0;arm.look.lean=look.lean;arm.look.roll=0;
                    arm.look.red=GoldRush.RED;arm.look.green=GoldRush.GREEN;arm.look.blue=GoldRush.BLUE;
                    arm.age=s.puppet.age;
                    var b=GoldRush.phantomBody(s.body,i);arm.place(b.at(),b.yaw());
                    dispatcher.render(arm,b.at().x-cam.x,b.at().y-cam.y,b.at().z-cam.z,b.yaw(),delta,c.matrixStack(),c.consumers(),dispatcher.getLight(arm,delta));
                }
            }
            // (2.154.0) the basic attack's strikes: faint golden afterimages of the whole stand a moment behind it, each a
            // body of its own (GeckoLib poses a body once a frame)
            float strike=s.combo?GoldCombo.ghost(s.owner,delta)*look.alpha:0;
            if(strike>.02f)for(int i=0;i<s.ghosts.length;i++){
                if(s.ghosts[i]==null)s.ghosts[i]=new GoldPuppet(mc.world);
                var ghostPuppet=s.ghosts[i];var gl=ghostPuppet.look;
                s.look(ticks-1d+delta,gl);gl.armL=gl.armR=1;gl.showL=gl.showR=1;gl.armsOnly=false;
                s.ghostShape.clear();
                if(!GoldCombo.pose(s.owner,delta,GoldCombo.GHOST_BACK[i],gl,s.ghostShape,s.scratch))continue;
                gl.alpha=strike*GoldCombo.GHOST_ALPHA[i];gl.out=look.out;gl.scale=look.scale;gl.shadow=0;gl.glow=0;gl.lean=look.lean;gl.roll=look.roll;
                gl.red=GoldRush.RED;gl.green=GoldRush.GREEN;gl.blue=GoldRush.BLUE;
                ghostPuppet.age=s.puppet.age;
                var b=s.where(p,delta,gl,s.ghostShape);ghostPuppet.place(b.at(),b.yaw());
                dispatcher.render(ghostPuppet,b.at().x-cam.x,b.at().y-cam.y,b.at().z-cam.z,b.yaw(),delta,c.matrixStack(),c.consumers(),dispatcher.getLight(ghostPuppet,delta));
            }
            // coming out or going back: faint golden afterimages where it was a moment ago (the same pose: GeckoLib
            // poses a body once a frame)
            float main=look.out;ghost.set(look);
            for(int i=1;i<=3;i++){
                s.look(ticks-1-i*.7+delta,trail);
                float a=ghost.alpha*Math.min(1,Math.abs(main-trail.out)*6)*(.42f-.11f*i);
                if(a<.02f)continue;
                look.set(ghost);look.out=trail.out;look.alpha=a;look.red=1;look.green=.86f;look.blue=.42f;look.shadow=0;look.glow=0;
                s.put(p,delta,look,s.front?s.shape:null);
                var g=s.puppet.getPos();
                dispatcher.render(s.puppet,g.x-cam.x,g.y-cam.y,g.z-cam.z,yaw,delta,c.matrixStack(),c.consumers(),dispatcher.getLight(s.puppet,delta));
            }
            look.set(ghost);s.put(p,delta,look,s.front?s.shape:null);
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
        /** Where it was last drawn and the frame it was drawn in; the bodies of its rush's flying arms; the rush's place for it this frame. */
        StandPresence.Body body;long bodyFrame=-1;final GoldPuppet[] arms=new GoldPuppet[GoldRush.PHANTOMS.length];
        final GoldRush.Shape shape=new GoldRush.Shape(),shape2=new GoldRush.Shape();
        /** Whether it stands in front of its user this frame (its rush's, its punch's or its healing's place: {@code shape}). */
        boolean front;
        /** Its user's healing if it shows this frame (2.149.0). */
        GoldHeal.Heal heal;
        /** Its user's beatdown if it shows this frame (2.150.0). */
        GoldBeatdown.Beat beat;
        /** Its user's giving life if it shows this frame (2.151.0). */
        GoldLife.Life life;
        /** Its user's tree of life if it shows this frame (2.152.0). */
        GoldTree.Tree tree;
        /** Whether its user's punches show this frame (2.154.0), and the bodies of a strike's afterimages. */
        boolean combo;final GoldPuppet[] ghosts=new GoldPuppet[GoldCombo.GHOST_BACK.length];
        final GoldExperienceLook ghostLook=new GoldExperienceLook();final GoldRush.Shape ghostShape=new GoldRush.Shape();
final float[] scratch=new float[dev.sangwoo.celestialfall.stand.GoldExperienceStance.NAMES.size()];
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
        /**
         * This frame: the look for the frame's moment with its rush, how it leans as it follows, and the body put where it
         * floats. Returns its user's rush if it shows (null if none does).
         */
        StandRush.Rush place(PlayerEntity p,float delta,GoldExperienceLook look){
            if(pos==null)follow(p);
            look(ticks-1d+delta,look);
            look.armL=look.armR=1;look.showL=look.showR=1;look.armsOnly=false;
            shape.lean=0;shape2.lean=0;
            // the stand's skills, each with its stances over the pose it has and where it stands (2.154.0: laid in the
            // order they began, the newest taking over from those before it, as its punches come between the others):
            // (2.147.0) the muda rush; (2.148.0) the overdrive punch; (2.149.0) the healing (the tap in front of its user;
            // the hold at their front right, turned on them, by the way they face as it follows it); (2.150.0) the
            // beatdown (in front of the one struck, then under them, leaning back); (2.151.0) giving life's punch (the
            // frog it plays no part in, 2.151.1: the item held up turns of itself, the stand where it was); (2.152.0) the
            // tree of life (down on its knee at its user's front right, its palm on the ground, pinned where it touched it
            // while the tree bursts up and flings them); (2.154.0) the basic attack's punches
            var rush=StandRush.of(owner);if(rush!=null&&(!rush.gold()||!out))rush=null;
            var punch=out?GoldPunch.of(owner):null;
            heal=out?GoldHeal.of(owner):null;beat=out?GoldBeatdown.of(owner):null;life=out?GoldLife.of(owner):null;tree=out?GoldTree.of(owner):null;
            float[] ages={rush==null?Float.NaN:rush.age(delta),punch==null?Float.NaN:punch.age(delta),heal==null?Float.NaN:heal.age(delta),
                beat==null?Float.NaN:beat.age(delta),life==null?Float.NaN:life.age(delta),tree==null?Float.NaN:tree.age(delta),out?GoldCombo.newest(owner,delta):Float.NaN};
            Integer[] order={0,1,2,3,4,5,6};
            // oldest first (the longest since it began), the newest last, over the rest
            java.util.Arrays.sort(order,(x,y)->Float.compare(Float.isNaN(ages[y])?-1:ages[y],Float.isNaN(ages[x])?-1:ages[x]));
            boolean rushing=false,punching=false,healing=false,beating=false,living=false,treeing=false;combo=false;
            shape.clear();boolean first=true;
            float yawNow=MathHelper.lerpAngleDegrees(delta,prevYaw,yaw);
            for(int k:order){
                if(Float.isNaN(ages[k]))continue;
                var into=first?shape:shape2;if(!first)shape2.clear();
                boolean shown=switch(k){
                    case 0->GoldRush.pose(rush,delta,look,into,scratch);
                    case 1->GoldPunch.pose(punch,delta,look,into,scratch);
                    case 2->GoldHeal.pose(heal,delta,look,into,scratch,yawNow);
                    case 3->GoldBeatdown.pose(beat,delta,look,into,scratch);
                    case 4->GoldLife.pose(life,delta,look,into,scratch);
                    case 5->GoldTree.pose(tree,delta,look,into,scratch);
                    default->GoldCombo.pose(owner,delta,0,look,into,scratch);
                };
                if(!shown)continue;
                if(!first)shape.over(shape2);
                first=false;
                switch(k){case 0->rushing=true;case 1->punching=true;case 2->healing=true;case 3->beating=true;case 4->living=true;case 5->treeing=true;default->combo=true;}
            }
            if(!rushing)rush=null;if(!healing)heal=null;if(!beating)beat=null;if(!living)life=null;if(!treeing)tree=null;
            front=!first;
            float calm=front?1-shape.engage:1;
            var v=prevVel.lerp(vel,delta);float y=MathHelper.lerpAngleDegrees(delta,prevYaw,yaw);
            double r=Math.toRadians(y),fx=-Math.sin(r),fz=Math.cos(r);
            double forward=v.x*fx+v.z*fz,side=-v.x*fz+v.z*fx;
            look.lean=MathHelper.clamp((float)forward*2.4f,-8,16)*look.out*calm;
            look.roll=MathHelper.clamp((float)side*-2f,-10,10)*look.out*calm;
            if(front)look.lean+=shape.lean*shape.engage;
            // its aura burns a little brighter through the barrage
            if(rush!=null)look.glow=Math.max(look.glow,look.out*(1+.45f*GoldRush.on(rush,rush.age(delta))));
            // and brighter still as its fist's light gathers for the overdrive punch
            if(punching)look.glow=Math.max(look.glow,look.out*(1+.7f*GoldPunch.charge(punch,punch.age(delta))));
            // and a little brighter as it heals
            if(heal!=null)look.glow=Math.max(look.glow,look.out*(1+.4f*GoldHeal.glow(heal,heal.age(delta))));
            // and through its beatdown
            if(beat!=null)look.glow=Math.max(look.glow,look.out*(1+GoldBeatdown.glow(beat,beat.age(delta))));
            // and as it gives life
            if(life!=null)look.glow=Math.max(look.glow,look.out*(1+.5f*GoldLife.glow(life,life.age(delta))));
            // and as life goes into the ground for the tree
            if(tree!=null)look.glow=Math.max(look.glow,look.out*(1+.7f*GoldTree.glow(tree,tree.age(delta))));
            // (2.154.0) and as the light gathers in its fist for the basic attack's fourth punch
            if(combo)look.glow=Math.max(look.glow,look.out*(1+.5f*GoldCombo.charge(owner,delta)));
            put(p,delta,look,front?shape:null);
            return rush;
        }
        void put(PlayerEntity p,float delta,GoldExperienceLook look){put(p,delta,look,null);}
        /**
         * The body where the look puts it: {@code out} of the way from its user's feet to its place, facing as it
         * follows; through its rush ({@code shape}), round to the front of its user on an arc past their shoulder (never
         * through them), to stand right in front of them facing where they face (as Star Platinum does, StandPresence).
         */
        void put(PlayerEntity p,float delta,GoldExperienceLook look,GoldRush.Shape shape){
            var b=where(p,delta,look,shape);puppet.place(b.at(),b.yaw());
        }
        /** Where the look and the shape put the body this frame (as {@link #put} does), without moving it there. */
        StandPresence.Body where(PlayerEntity p,float delta,GoldExperienceLook look,GoldRush.Shape shape){
            var home=p.getLerpedPos(delta);var at=prevPos.lerp(pos,delta);float y=MathHelper.lerpAngleDegrees(delta,prevYaw,yaw);
            // (2.152.0) kneeling where it touched the ground for the tree, while its user is flung up off it
            if(shape!=null&&shape.engage>0&&shape.pin>0)home=home.lerp(new Vec3d(shape.gx,shape.gy,shape.gz),Math.min(1,shape.pin)*Math.min(1,shape.engage*4));
            if(shape!=null&&shape.engage>0){
                float e=shape.engage;
                double r=Math.toRadians(shape.yaw),fx=-Math.sin(r),fz=Math.cos(r);
                // where it is now and where it strikes from, as a turn round its user and a distance from them
                double ax=at.x-home.x,az=at.z-home.z;
                double fromAngle=Math.atan2(ax*-fz+az*fx,ax*fx+az*fz),fromDist=Math.hypot(ax,az);
                double ang=fromAngle+wrapRad(Math.toRadians(shape.side)-fromAngle)*e,dist=fromDist+(shape.ahead-fromDist)*e;
                double ux=Math.cos(ang),us=Math.sin(ang);
                at=new Vec3d(home.x+fx*ux*dist-fz*us*dist,at.y+(home.y+StandPresence.UP+shape.raise-at.y)*e,home.z+fz*ux*dist+fx*us*dist);
                // the way it faces: where its user faces, or (the healing of its user) in on them
                float to=shape.yaw;
                if(shape.inward>0)to=MathHelper.lerpAngleDegrees(shape.inward,to,(float)Math.toDegrees(Math.atan2(-(home.x-at.x),home.z-at.z)));
                y=y+MathHelper.wrapDegrees(to-y)*e;
            }
            return new StandPresence.Body(home.lerp(at,look.out),y,look.scale);
        }
        static double wrapRad(double a){a%=Math.PI*2;if(a>Math.PI)a-=Math.PI*2;if(a<-Math.PI)a+=Math.PI*2;return a;}
    }
}
