package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.xchara.XCharaClone;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.xchara.CloneRules.*;

/**
 * X Chara's R with the hack on, the double, as this client knows it (2.125.0; the server's XCharaClone, CloneRules for its
 * timing). R on the map with the hack on asks for it with the one the camera's middle looks at (XCharaClient.key, as
 * G). The server tells when it starts, when the one marked is seized (and where the wall will stand), when they are in
 * the wall, and when it is blocked, called off or over; everything between is drawn from its clock (its cast's:
 * {@link #CASTS}, drawn with the effects, {@link CloneFling}).
 * <p>
 * What this draws itself: the double, a second drawing of the one marked ({@link #render}: the same entity through its
 * own renderer, stood beside its user facing the one marked, in the double's own pose, {@link #CLIP}: it stands, winds
 * up and throws; a player's or a training dummy's limbs take the clip, anything else leans and turns with it), blinking
 * in and blinking out as a thing on a screen; the one seized drawn where the throw has them ({@link #offset}: lifted,
 * turning over in the air, flung into the wall, held in it, dropped from it; {@link #turn}, {@link #struckPose}) while
 * the server holds them where they stood and then at the wall's foot; its user's press of their panel ({@link #press}).
 * Its three sounds are the user's recording's (tools/xchara/extract_clone.py): the double coming, its throw, the wall.
 */
public final class CloneClient {
    /** Its effects' casts (CloneFling through KagenoRenderer), one for each this client knows of. */
    public static final Map<UUID,StandClient.Cast> CASTS=new LinkedHashMap<>();
    /** One of them as this client heard of it. */
    public static final class Throw {
        public final UUID user,targetId;public final int target;public final float yaw;public final Vec3d from,f,r;
        /** Where the one marked stood (as it began, then as they were seized) and the wall's foot (once they are seized). */
        public Vec3d targetFrom,wall;
        public final StandClient.Cast cast;public final int seed;
        /** Which side of its user the double stands (1 their left, -1 their right: the left unless a block is there). */
        final int side;
        /** When they were seized, when they went into the wall, and when it ended (ticks on its clock; NaN until the server says), and how it ended. */
        float seized=Float.NaN,struck=Float.NaN,ended=Float.NaN;int how=-1;float dealt;
        XCharaClient.Voice come,thrown,crash;
        Throw(UUID user,int target,UUID targetId,float yaw,Vec3d from,Vec3d targetFrom,StandClient.Cast cast,int side){
            this.user=user;this.target=target;this.targetId=targetId;this.yaw=yaw;this.from=from;this.targetFrom=targetFrom;this.wall=targetFrom;this.cast=cast;this.side=side;
            f=Vec3d.fromPolar(0,yaw);r=new Vec3d(-f.z,0,f.x);seed=(int)(cast.start()*31+target*7);
        }
        public float age(float delta){return cast.age(delta);}
        public boolean ended(){return !Float.isNaN(ended);}
        /** Whether it was blocked by a guard or called off (not done). */
        public boolean stopped(){return how==XCharaClone.BLOCKED||how==XCharaClone.CUT;}
        public boolean blocked(){return how==XCharaClone.BLOCKED;}
        public boolean seized(){return !Float.isNaN(seized);}
        public boolean struck(){return !Float.isNaN(struck);}
        public boolean isTarget(Entity e){return e!=null&&e.getId()==target&&e.getUuid().equals(targetId);}
        public boolean has(Entity e){return e!=null&&(e.getUuid().equals(user)||isTarget(e));}
        /** Where the double stands: beside its user, a little ahead of them. */
        public Vec3d cloneAt(){return from.add(r.multiply(-side*BESIDE)).add(f.multiply(AHEAD));}
        /** How far along the way the wall's foot is from where they were seized, and how much higher. */
        double far(){return wall.subtract(targetFrom).dotProduct(f);}
        double rise(){return wall.y-targetFrom.y;}
        /** The wall's middle on the ground: behind the one held in it. */
        public Vec3d wallBase(){return wall.add(f.multiply(WALL_BEHIND));}
    }
    /** The double stands this far to its user's side and this far ahead of them (blocks); the wall's middle this far behind the one held in it. */
    static final double BESIDE=1.3,AHEAD=.35,WALL_BEHIND=.95;
    /** A sound asked for {@code n} ticks from now is heard nearly a tick sooner than that (the game counts the tick it is asked in: measured in the game's own recording, 2.125.0): asked for this much later. */
    static final int SOUND_LAG=1;
    private static final Map<UUID,Throw> THROWS=new HashMap<>();
    private static final Map<UUID,Long> READY=new HashMap<>();
    private static Object world;
    private CloneClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaClone.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),now=b.readLong();int target=b.readVarInt();UUID targetId=b.readUuid();
            Vec3d from=vec(b),targetFrom=vec(b),wall=vec(b);float yaw=b.readFloat(),dealt=b.readFloat();long ready=b.readLong();
            if(!Float.isFinite(yaw)||!Float.isFinite(dealt)||now<start||now-start>END+400||type>XCharaClone.END_OF)return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                if(world!=mc.world){clear();world=mc.world;}
                if(type>=XCharaClone.BLOCKED)READY.put(user,ready);
                if(type==XCharaClone.START){start(mc,user,target,targetId,start,now,yaw,from,targetFrom);return;}
                var d=THROWS.get(user);
                if(d==null||d.cast.start()!=start||d.ended())return;
                if(type==XCharaClone.SEIZED){
                    if(d.seized())return;
                    d.seized=d.age(0);d.targetFrom=targetFrom;d.wall=wall;
                    // the wall's crash, a tick before they are in it (none if heard too late to come with it)
                    int wait=Math.round(IMPACT-1-d.seized)+SOUND_LAG;
                    if(wait>=1)d.crash=play(mc,d,CelestialFall.id("xchara_clone_wall"),wait);
                    return;
                }
                if(type==XCharaClone.IMPACT_HIT){
                    if(d.struck())return;
                    d.struck=d.age(0);d.wall=wall;d.dealt=dealt;
                    // its user's damage number, as their other skills' (the hit count is the server's; no streak of blood:
                    // this client has them where they stood yet, the server's word of the wall's foot a moment behind)
                    if(mc.player!=null&&mc.player.getUuid().equals(user)&&dealt>0)dev.sangwoo.celestialfall.client.kageno.HitFeedback.hit(mc,d.target,dealt,d.yaw,false);
                    return;
                }
                d.ended=d.age(0);d.how=type;
                // blocked or called off: what has not sounded yet does not (the double's coming too, blocked before it came)
                if(d.stopped()){fade(d.come);fade(d.thrown);fade(d.crash);}
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(CloneClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(CloneClient::clear));
        WorldRenderEvents.AFTER_ENTITIES.register(CloneClient::render);
    }
    private static Vec3d vec(net.minecraft.network.PacketByteBuf b){return new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());}
    private static void fade(XCharaClient.Voice v){if(v!=null)v.fade();}
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var d:THROWS.values())for(var v:new XCharaClient.Voice[]{d.come,d.thrown,d.crash})if(v!=null&&!v.isDone())mc.getSoundManager().stop(v);
        THROWS.clear();CASTS.clear();READY.clear();world=null;
    }
    private static void start(MinecraftClient mc,UUID user,int target,UUID targetId,long start,long now,float yaw,Vec3d from,Vec3d targetFrom){
        var old=THROWS.remove(user);
        if(old!=null){CASTS.remove(old.cast.id());fade(old.come);fade(old.thrown);fade(old.crash);}
        var cast=new StandClient.Cast(UUID.randomUUID(),user,mc.world.getRegistryKey().getValue(),SKILL,0,start,now,StandClient.ticks(),from,yaw,
            List.of(new StandClient.Target(targetId,target,targetFrom)));
        // the double to its user's left, or to their right if a block stands where it would
        Vec3d f=Vec3d.fromPolar(0,yaw),r=new Vec3d(-f.z,0,f.x);
        int side=blocked(mc,from.add(r.multiply(-BESIDE)).add(f.multiply(AHEAD)))&&!blocked(mc,from.add(r.multiply(BESIDE)).add(f.multiply(AHEAD)))?-1:1;
        var d=new Throw(user,target,targetId,yaw,from,targetFrom,cast,side);
        THROWS.put(user,d);CASTS.put(cast.id(),cast);
        // its user looks at the one marked (the server turned them too)
        if(mc.player!=null&&mc.player.getUuid().equals(user)){mc.player.setYaw(yaw);mc.player.setHeadYaw(yaw);mc.player.setBodyYaw(yaw);mc.player.prevYaw=yaw;}
        // the double coming, and its throw a tick before it (none of either if heard too late to come with it)
        float age=d.age(0);
        if(age<=CLONE+3)d.come=play(mc,d,CelestialFall.id("xchara_clone"),Math.max(0,Math.round(CLONE-age)+SOUND_LAG));
        if(age<=THROW-1)d.thrown=play(mc,d,CelestialFall.id("xchara_clone_throw"),Math.max(0,Math.round(THROW-1-age)+SOUND_LAG));
    }
    private static boolean blocked(MinecraftClient mc,Vec3d feet){
        for(double up:new double[]{.4,1.4}){
            BlockPos pos=BlockPos.ofFloored(feet.x,feet.y+up,feet.z);
            if(!mc.world.getBlockState(pos).getCollisionShape(mc.world,pos).isEmpty())return true;
        }
        return false;
    }
    /** Plays one of its sounds for this client, {@code delay} ticks from now: loud for its two, fading out to 40 blocks from the nearer of them for the rest. */
    private static XCharaClient.Voice play(MinecraftClient mc,Throw d,Identifier id,int delay){
        var v=new XCharaClient.Voice(id,()->loudness(mc,d));
        if(delay>0)mc.getSoundManager().play(v,delay);else mc.getSoundManager().play(v);
        return v;
    }
    static float loudness(MinecraftClient mc,Throw d){
        if(mc.player==null||mc.world==null)return 0;
        if(d.has(mc.player))return (float)CelestialFall.CONFIG.effectVolume;
        Vec3d me=mc.player.getPos();
        double far=Math.min(me.distanceTo(d.from),Math.min(me.distanceTo(d.targetFrom),me.distanceTo(d.wall)));
        return (float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/40));
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!THROWS.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        THROWS.values().removeIf(d->{boolean gone=d.age(0)>END+60;if(gone)CASTS.remove(d.cast.id());return gone;});
        READY.values().removeIf(at->at<mc.world.getTime()-20);
    }

    // ───────────────────────────── who is in one ─────────────────────────────

    static java.util.Collection<Throw> all(){return THROWS.values();}
    /** This player's (as its user), while it shows. */
    public static Throw of(UUID user){return THROWS.get(user);}
    static Throw ofCast(StandClient.Cast cast){var d=THROWS.get(cast.caster());return d!=null&&d.cast==cast?d:null;}
    /** The one that has seized {@code e}, or null. */
    static Throw seizing(Entity e){
        if(e==null||THROWS.isEmpty())return null;
        for(var d:THROWS.values())if(d.seized()&&d.isTarget(e))return d;
        return null;
    }
    /** When this player's is ready again (the server's world time; long ago if never). */
    public static long ready(UUID player){return READY.getOrDefault(player,Long.MIN_VALUE);}
    /**
     * Whether this one is held where they stand by one now, on this client: its user until RELEASE, the one marked from
     * the throw until they are in the wall. (In the wall the server still holds them; this client lets them be moved
     * there: one held does not follow the server's word of where they are, KagenoFreezeMixin, and they cannot walk
     * anyway, incapacitated.)
     */
    public static boolean holds(Entity e){
        if(e==null||THROWS.isEmpty()||!MapClient.enabled())return false;
        for(var d:THROWS.values()){
            float t=d.age(0);if(t<0||d.ended()&&t>=d.ended)continue;
            if(e.getUuid().equals(d.user)&&t<RELEASE)return true;
            if(d.isTarget(e)&&d.seized()&&t<IMPACT)return true;
        }
        return false;
    }

    // ───────────────────────────── the one seized ─────────────────────────────

    /** Whether the one seized is drawn where the throw has them at {@code t}: seized, and not let go early (called off: they are where they stand at once). */
    private static boolean carried(Throw d,float t){
        if(!d.seized()||t<THROW)return false;
        return !(d.ended()&&d.how!=XCharaClone.END_OF&&t>=d.ended);
    }
    /** Where the one seized is drawn at {@code t} ticks (their feet), or null when they are where they stand. */
    static Vec3d drawn(Throw d,float t){
        if(!carried(d,t))return null;
        double[] at=dev.sangwoo.celestialfall.xchara.CloneRules.drawn(t,d.far(),d.rise());
        return at==null?null:d.targetFrom.add(d.f.multiply(at[2])).add(0,at[1],0);
    }
    /** Whether {@code e} is drawn where a throw has them now (in the air or in the wall): the wall is the mark of their being held, no stars turn over them. */
    public static boolean carries(Entity e){
        var d=seizing(e);return d!=null&&drawn(d,d.age(0))!=null;
    }
    /** Where one seized is drawn, as an offset from where they are (null: where they are). Not for the double's own drawing of them. */
    public static Vec3d offset(Entity e,float delta){
        if(drawing!=null)return null;
        var d=seizing(e);if(d==null||!e.isAlive())return null;
        Vec3d at=drawn(d,d.age(delta));
        return at==null?null:at.subtract(e.getLerpedPos(delta));
    }
    /**
     * The turn of a body this draws (after its renderer's own, about its feet, local +Z behind it): the double's, as its
     * clip turns and leans it; the one seized, turned to face its user and over backwards about their middle
     * (CloneRules.tumble), swaying a little as they hang.
     */
    public static void turn(LivingEntity e,float delta,float bodyYaw,MatrixStack m){
        if(drawing!=null){
            if(drawing.entity!=e)return;
            var fr=drawing.frame;
            if(fr.yaw()!=0||fr.pitch()!=0||fr.roll()!=0){
                float k=(float)Math.PI/180;
                // (the clip's: yaw to the left, pitch forward, roll to the right; here +X is the body's right, +Z behind it)
                var q=new Quaternionf().rotationY(fr.yaw()*k).rotateX(-fr.pitch()*k).rotateZ(-fr.roll()*k);
                m.translate(0,KagenoClips.PIVOT,0);m.multiply(q);m.translate(0,-KagenoClips.PIVOT,0);
            }
            return;
        }
        var d=seizing(e);if(d==null||!e.isAlive())return;
        float t=d.age(delta);if(drawn(d,t)==null)return;
        float facing=Ease.smooth(t,THROW,THROW+6);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.wrapDegrees(bodyYaw-(d.yaw+180))*facing));
        float hang=Ease.smooth(t,THROW,THROW+4)*(1-Ease.smooth(t,FLING-1,FLING+1));
        float mid=e.getHeight()*.5f;
        m.translate(0,mid,0);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tumble(t)));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(7*hang*(float)Math.sin(t*.55f+d.seed)));
        m.translate(0,-mid,0);
    }
    /** Hanging in the air: the arms up and out, the legs loose, the head back. Flung: the limbs left behind. In the wall: the arms out against it, the head down. */
    static final R6.Pose HUNG=R6.pose().rArm(32,72).lArm(32,-72).rLeg(-72,14).lLeg(-64,-16).head(0,-20,0);
    static final R6.Pose FLOWN=R6.pose().rArm(14,24).lArm(14,-24).rLeg(-48,8).lLeg(-42,-8).head(0,26,0);
    static final R6.Pose STUCK_POSE=R6.pose().rArm(16,104).lArm(22,-98).rLeg(-80,12).lLeg(-74,-16).head(0,30,0);
    private static final MotionPose HUNG_M=R6.solve(HUNG).joints(),FLOWN_M=R6.solve(FLOWN).joints(),STUCK_M=R6.solve(STUCK_POSE).joints();
    private static Quaternionf quat(float[] e){float k=(float)Math.PI/180;return new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k);}
    /** {@code w} of the way from one pose to another, each limb turned the short way round (their angles blended one by one can spin a limb through a half turn). */
    static MotionPose turned(MotionPose a,MotionPose b,float w){
        if(w<=0)return a.copy();if(w>=1)return b.copy();
        MotionPose p=MotionPose.blend(a,b,w);
        for(int i=0;i<p.joint.length;i++)p.joint[i]=R6.angles(quat(a.joint[i]).slerp(quat(b.joint[i]),w));
        return p;
    }
    /** The limbs of one seized at {@code t} ticks (a player's or a training dummy's), or null when they are their own. */
    static MotionPose struckAt(Throw d,float t){
        MotionPose p=turned(HUNG_M,FLOWN_M,Ease.smooth(t,FLING-1,FLING+1.5f));
        p=turned(p,STUCK_M,Ease.smooth(t,IMPACT-1,IMPACT+2));
        // flailing as they hang, less once they are in the wall (a twitch now and then)
        float loose=1-.75f*Ease.smooth(t,FLING,IMPACT),w=t*.5f+d.seed;
        p.joint[MotionPose.RIGHT_ARM][0]+=16*loose*(float)Math.sin(w*1.7);p.joint[MotionPose.LEFT_ARM][0]+=16*loose*(float)Math.sin(w*1.7+2.1);
        p.joint[MotionPose.RIGHT_LEG][0]+=13*loose*(float)Math.sin(w*1.3+.8);p.joint[MotionPose.LEFT_LEG][0]+=13*loose*(float)Math.sin(w*1.3+3.6);
        p.elbow[0]=-34-10*loose*(float)Math.sin(w*2.1);p.elbow[1]=-34-10*loose*(float)Math.sin(w*2.1+1.4);
        p.knee[0]=34+10*loose*(float)Math.sin(w*1.9+.5);p.knee[1]=34+10*loose*(float)Math.sin(w*1.9+2.7);
        return p;
    }
    /** The limbs of {@code e} this frame if one has them seized (hanging, flung, in the wall), or null. */
    public static MotionPose struckPose(LivingEntity e,float delta){
        if(drawing!=null)return null;
        var d=seizing(e);if(d==null||!e.isAlive())return null;
        float t=d.age(delta);
        return drawn(d,t)==null?null:struckAt(d,t);
    }

    // ───────────────────────────── the double ─────────────────────────────

    /** The double begins to go at this tick (the video: 1.1 s), gone by CLONE_GONE; called off, it goes over this many ticks. */
    static final float GOING=22,CUT_GONE=3;
    /**
     * Whether the double is drawn at {@code t} ticks (it is there or it is not, as a thing on a screen): blinking in over
     * its first tenth of a second, there through its throw, then blinking out, on for less of each blink.
     */
    static boolean cloneShown(float t,float stopped){
        if(t<CLONE||t>=CLONE_GONE)return false;
        if(!Float.isNaN(stopped)&&t>=stopped){
            float s=t-stopped;return s<CUT_GONE&&(int)Math.floor(s*2)%2==0;
        }
        float s=t-CLONE;
        if(s<2.4f)return (int)Math.floor(s*2.5f)%2==0;
        if(t<GOING)return true;
        float u=(t-GOING)/(CLONE_GONE-GOING),blink=(t-GOING)*.85f;
        return blink-Math.floor(blink)>=u;
    }
    static boolean cloneShown(Throw d,float t){return cloneShown(t,d.stopped()?d.ended:Float.NaN);}
    private static double[] none(){return d(0,0);}
    /** Standing as anyone stands; wound up: turned to the right, the right hand drawn back up behind the shoulder, the left out at the one marked, the weight back; thrown: whipped round to the left over the front foot, the right arm flung out and up at them, the left thrown back; carried on a little. */
    static final R6.Pose STAND=KnifeCombo.on(0,0,0,0,0,0,d(-86,8),none(),d(-86,-8),.12,0,-.12,0);
    static final R6.Pose READY_POSE=KnifeCombo.on(.02,-.04,-.06,-12,0,0,d(-70,40),none(),d(-60,-10),.14,-.08,-.13,.1);
    static final R6.Pose WOUND=KnifeCombo.on(.06,-.14,-.16,-40,-5,0,d(18,152),none(),d(-8,-14),.2,-.3,-.14,.22);
    static final R6.Pose THROWN=KnifeCombo.on(-.04,.3,-.2,40,16,0,d(26,-20),none(),d(-44,-160),.16,-.28,-.14,.5);
    static final R6.Pose FOLLOWED=KnifeCombo.on(-.05,.34,-.16,50,12,0,d(46,-46),none(),d(-54,-166),.16,-.24,-.14,.5);
    /** The double's body through its throw (seconds from the press): standing as it comes, wound up, thrown at THROW, carried on until it is gone. */
    static final Anim CLIP=Anim.joints(
        k(0,STAND),
        k(CLONE/20f+.12f,STAND),
        k(.46,READY_POSE),
        k(.70,WOUND),
        k(.77,WOUND),
        k(THROW/20f,THROWN),
        k(1.08,FOLLOWED),
        k(CLONE_GONE/20f,FOLLOWED)).stops(Anim.stop(THROW/20f,.04,.07)).after(IsosangLimbs.natural(IsosangLimbs.FLOOR));
    /** The double's right hand as its clip has it at {@code sec}, from its feet: {right, up, ahead} (for the streak off its throw). */
    static double[] hand(float sec){
        var p=CLIP.pose(sec);float k=(float)Math.PI/180;
        var q=new Quaternionf().rotationY(-p.yaw*k).rotateX(p.pitch*k);
        var v=q.transform(new org.joml.Vector3f((float)(.31+p.rArm.rt()*.62),(float)(.32+p.rArm.up()*.62),(float)(p.rArm.fw()*.62)));
        return new double[]{p.rt+v.x,p.up+KagenoClips.PIVOT+v.y,p.fw+v.z};
    }
    /** The double being drawn now: whose body, and its clip's frame (null between drawings). */
    private record Drawing(LivingEntity entity,R6.Frame frame){}
    private static Drawing drawing;
    /** Whether {@code e} is being drawn as the double now (its renderer's own hooks for the one it copies stand aside). */
    public static boolean doubling(Entity e){return drawing!=null&&drawing.entity==e;}
    /** The double's limbs while {@code e} is drawn as it, or null. */
    public static MotionPose doublePose(LivingEntity e){return doubling(e)?drawing.frame.joints():null;}
    /** Where the double's feet are this frame (its clip steps it about where it stands). */
    static Vec3d cloneFeet(Throw d,R6.Frame fr){return d.cloneAt().add(d.f.multiply(fr.fw())).add(0,fr.up(),0).add(d.r.multiply(fr.rt()));}
    /** Draws the doubles: each the one marked again, through their own renderer, beside its user. */
    private static void render(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();if(THROWS.isEmpty()||mc.world==null||ctx.consumers()==null)return;
        float delta=ctx.tickDelta();Vec3d cam=ctx.camera().getPos();var dispatcher=mc.getEntityRenderDispatcher();
        for(var d:THROWS.values()){
            float t=d.age(delta);if(!cloneShown(d,t))continue;
            if(!(mc.world.getEntityById(d.target) instanceof LivingEntity e)||!d.isTarget(e)||!e.isAlive())continue;
            var fr=CLIP.frame(t/20f);Vec3d at=cloneFeet(d,fr);
            if(at.squaredDistanceTo(cam)>96*96)continue;
            float by=e.bodyYaw,pby=e.prevBodyYaw,hy=e.headYaw,phy=e.prevHeadYaw,y=e.getYaw(),py=e.prevYaw,pt=e.getPitch(),ppt=e.prevPitch;int hurt=e.hurtTime;
            float face=d.yaw;
            e.bodyYaw=e.prevBodyYaw=face;e.headYaw=e.prevHeadYaw=face;e.setYaw(face);e.prevYaw=face;e.setPitch(0);e.prevPitch=0;e.hurtTime=0;
            drawing=new Drawing(e,fr);
            try{
                dispatcher.render(e,at.x-cam.x,at.y-cam.y,at.z-cam.z,face,delta,ctx.matrixStack(),ctx.consumers(),dispatcher.getLight(e,delta));
            }finally{
                drawing=null;
                e.bodyYaw=by;e.prevBodyYaw=pby;e.headYaw=hy;e.prevHeadYaw=phy;e.setYaw(y);e.prevYaw=py;e.setPitch(pt);e.prevPitch=ppt;e.hurtTime=hurt;
            }
        }
    }

    // ───────────────────────────── its user's press ─────────────────────────────

    /** The press of the panel {@code sec} seconds in as {lift, tap, lit, 1} (HackPanel.press; the last marks it violet): the arm up at once, the hand down on it a tenth of a second in and the arm down again by half a second (they watch the double), the panel lit at the press and again as the double throws. */
    static float[] pressAt(float sec,float stopped){
        float lift=Ease.smooth(sec,0,.1f)*(1-Ease.smooth(sec,.3f,.5f));
        if(!Float.isNaN(stopped))lift*=1-Ease.smooth(sec,stopped,stopped+.15f);
        float tap=Ease.smooth(sec,.04f,.1f)*(1-Ease.smooth(sec,.13f,.22f));
        float lit=Math.max(Ease.smooth(sec,.08f,.1f)*(1-Ease.smooth(sec,.16f,.4f)),Ease.smooth(sec,THROW/20f-.04f,THROW/20f)*(1-Ease.smooth(sec,THROW/20f+.06f,THROW/20f+.3f)));
        return new float[]{lift,tap,Float.isNaN(stopped)?lit:0,1};
    }
    /** This player's press of the panel for their double this frame, or null. */
    public static float[] press(UUID id,float delta){
        var d=THROWS.get(id);if(d==null)return null;
        float sec=d.age(delta)/20f;if(sec>RELEASE/20f+.1f)return null;
        float[] p=pressAt(sec,d.stopped()?d.ended/20f:Float.NaN);
        return p;
    }

    /** For the checks: which of this player's double's sounds have begun on this client, as "coming,throw,wall" (1 or 0 each; - if it was never asked for), or null. */
    public static String sounds(UUID user){
        var d=THROWS.get(user);if(d==null)return null;
        return begun(d.come)+","+begun(d.thrown)+","+begun(d.crash);
    }
    private static String begun(XCharaClient.Voice v){return v==null?"-":v.begun()?"1":"0";}
    /** For the capture tools: this player's as "age,seized,struck,ended,how", or null. */
    public static String describe(PlayerEntity p,float delta){
        var d=THROWS.get(p.getUuid());if(d==null)return null;
        return String.format(java.util.Locale.ROOT,"%.2f,%d,%d,%d,%d",d.age(delta),d.seized()?1:0,d.struck()?1:0,d.ended()?1:0,d.how);
    }
}
