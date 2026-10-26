package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.stand.ArrowStabPose.Fate;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.stand.StandArrow;
import dev.sangwoo.celestialfall.stand.StandArrowRules;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The stand arrow as this client hears of it (2.70.0, the server's StandArrow): whose stab, on its own clock (the
 * server's age of it when heard, then this client's ticks and the frame between them, as the stand's other clocks),
 * what the arrow chose once heard and when, or that it was cut short. Their body (ArrowStabPose: MotionPlayerModel),
 * the arrow drawn in their hands and chest (StandArrowFeature, and HeldItemRendererMixin from their own eyes), the
 * light (StandArrowFx), the sounds and, for the one stabbed, the words of what it chose (StandArrowHud) all follow it.
 * <p>
 * Its sounds: the stab's (the arrow lifted, the breath, the blow, the heart beating faster and the swell to its
 * choice) a tick into it, then, as the choice is heard, the awakening's or the rejection's (the knees and the body on
 * the ground in it, as ArrowStabPose has them fall from the choice); cut short, the stab's fades out. Loud for the one
 * stabbed and those near, fading out to CARRY blocks.
 */
public final class StandArrowClient {
    /** The stab's sound starts a tick into it (the files are cut so). */
    public static final int LEAD=1;
    static final double CARRY=40;
    static final float VOLUME=1;
    /** A stab as heard. */
    public static final class Stab {
        public final UUID user;public final int entity;public final long start;final long serverAge,received;
        /** What the arrow chose (UNKNOWN until heard) and its word for it (a stand's id, or StandArrowRules.DIE), and when it was heard (its clock, seconds). */
        public Fate fate=Fate.UNKNOWN;public String outcome="";public float known=Float.NaN;
        /** When it was cut short (its clock), NaN if it was not. */
        public float cut=Float.NaN;
        StandInhale.Sound sound,after;
        Stab(UUID user,int entity,long start,long serverAge,long received){this.user=user;this.entity=entity;this.start=start;this.serverAge=serverAge;this.received=received;}
        /** Seconds on its clock this frame. */
        public float age(float delta){return (serverAge+ticks-received+Math.max(0,Math.min(1,delta)))/20f;}
        public boolean cutShort(){return !Float.isNaN(cut);}
        /** When its choice shows (its clock): as it was heard, never before REVEAL. */
        public float shows(){return Float.isNaN(known)?Float.MAX_VALUE:Math.max(ArrowStabPose.REVEAL,known);}
    }
    private static final Map<UUID,Stab> STABS=new HashMap<>();
    static long ticks;
    private static Object world;
    private StandArrowClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(StandArrow.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();int entity=b.readVarInt();Identifier dimension=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),now=b.readLong();String outcome=b.readString(32);
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){clear();world=mc.world;}
                event(mc,user,entity,type,start,now,outcome);
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(StandArrowClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(StandArrowClient::clear));
        StandArrowHud.initialize();
    }
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var s:STABS.values())for(var sound:new StandInhale.Sound[]{s.sound,s.after})if(sound!=null&&!sound.isDone())mc.getSoundManager().stop(sound);
        STABS.clear();world=null;
    }
    private static void event(MinecraftClient mc,UUID user,int entity,int type,long start,long now,String outcome){
        var s=STABS.get(user);
        if(type!=StandArrow.START&&(s==null||s.start!=start))return;
        switch(type){
            case StandArrow.START->{
                if(s!=null)for(var sound:new StandInhale.Sound[]{s.sound,s.after})if(sound!=null&&!sound.isDone())mc.getSoundManager().stop(sound);
                s=new Stab(user,entity,start,now-start,ticks);
                STABS.put(user,s);
                if(s.serverAge<=LEAD)s.sound=play(mc,user,"stand_arrow_stab",(int)(LEAD-s.serverAge));
            }
            case StandArrow.REVEALED->{
                if(s.cutShort()||!Float.isNaN(s.known))return;
                s.outcome=outcome;s.fate=StandArrowRules.dies(outcome)?Fate.DIE:Fate.AWAKEN;
                // on this client's clock as the word comes (so nothing jumps), never before its choice shows
                s.known=Math.max(ArrowStabPose.REVEAL,s.age(0));
                // the choice's sound, unless heard far too late to go with the body
                if(s.known-ArrowStabPose.REVEAL<.6f)s.after=play(mc,user,s.fate==Fate.DIE?"stand_arrow_reject":"stand_arrow_awaken",0);
                if(mc.player!=null&&user.equals(mc.player.getUuid()))StandArrowHud.chosen(s);
            }
            case StandArrow.CUT->{
                if(s.cutShort())return;
                s.cut=s.age(0);
                if(s.sound!=null)s.sound.fade();
            }
            default->{}
        }
    }
    /** How loud this user's stab sounds here (0 if too far). */
    private static float volume(MinecraftClient mc,UUID id){
        if(mc.player==null||mc.world==null)return 0;
        var user=mc.world.getPlayerByUuid(id);
        double far=user==null||user==mc.player?0:user.getPos().distanceTo(mc.player.getPos());
        return (float)(CelestialFall.CONFIG.effectVolume*VOLUME*Math.max(0,1-far/CARRY));
    }
    private static StandInhale.Sound play(MinecraftClient mc,UUID id,String name,int delay){
        float volume=volume(mc,id);if(volume<=.01f)return null;
        var sound=new StandInhale.Sound(CelestialFall.id(name),volume);
        if(delay>0)mc.getSoundManager().play(sound,delay);else mc.getSoundManager().play(sound);
        return sound;
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!STABS.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        ticks++;
        STABS.values().removeIf(s->{
            float t=s.age(0);
            if(s.cutShort())return t>s.cut+1;
            var body=mc.world.getEntityById(s.entity);
            // one it chose is long free; one it rejects, once their body is gone (or up again: they lived, or it is a new one)
            if(s.fate==Fate.AWAKEN)return t>ArrowStabPose.FREE+2;
            if(s.fate==Fate.DIE)return t>ArrowStabPose.DEATH+12||body==null&&t>ArrowStabPose.DEATH+.5f||body instanceof LivingEntity l&&l.isAlive()&&t>ArrowStabPose.DEATH+.75f;
            // (the word of its choice lost: over once it has run well past it)
            return t>ArrowStabPose.REVEAL+3;
        });
    }

    /** The stab {@code user} is in (null if none heard). */
    public static Stab of(UUID user){return STABS.get(user);}
    public static Map<UUID,Stab> all(){return Map.copyOf(STABS);}

    /**
     * The stab's frame for {@code p}'s body this frame (null if none holds it): from the start until one it chose is
     * free, or, for one it rejects, until their body is gone. Only for the body the stab was heard for (one respawned
     * is a new body).
     */
    public static ArrowStabPose.Frame frame(PlayerEntity p,float delta){
        var s=STABS.get(p.getUuid());
        if(s==null||s.entity!=p.getId()||s.cutShort())return null;
        float t=s.age(delta);
        if(t<0)return null;
        if(s.fate==Fate.AWAKEN&&t>=ArrowStabPose.FREE)return null;
        if(s.fate==Fate.UNKNOWN&&t>ArrowStabPose.REVEAL+2.5f)return null;
        if(s.fate==Fate.DIE&&p.isAlive()&&t>ArrowStabPose.DEATH+.75f)return null;
        return ArrowStabPose.frame(t,s.fate,s.shows());
    }
    /** Poses {@code model} as {@code f} has the body (the shoulders lifted along the torso, as the mining hold lifts them). */
    public static void apply(net.minecraft.client.render.entity.model.BipedEntityModel<?> model,ArrowStabPose.Frame f){
        dev.sangwoo.celestialfall.client.MotionRig.apply(model,f.pose(),0,0);
        float[] b=f.pose().joint[MotionPose.BODY];
        var torso=new Quaternionf().rotationZYX(b[2]*(float)Math.PI/180,b[1]*(float)Math.PI/180,b[0]*(float)Math.PI/180);
        for(int i=0;i<2;i++){
            float lift=i==0?f.liftR():f.liftL();if(lift==0)continue;
            Vector3f up=torso.transform(new Vector3f(0,-lift,0));var arm=i==0?model.rightArm:model.leftArm;
            arm.pivotX+=up.x;arm.pivotY+=up.y;arm.pivotZ+=up.z;
        }
    }
    /** Whether {@code e} is held where they stand by an arrow in their chest (for its movement, as the server holds them). */
    public static boolean holds(LivingEntity e){
        if(!(e instanceof PlayerEntity p))return false;
        var s=STABS.get(p.getUuid());
        if(s==null||s.entity!=p.getId()||s.cutShort())return false;
        float t=s.age(0);
        return t>=0&&!(s.fate==Fate.AWAKEN&&t>=ArrowStabPose.FREE)&&t<ArrowStabPose.DEATH+2;
    }
    /** Whether {@code e} lies dead where an arrow that rejected them felled them (their body is not turned on its side as it dies). */
    public static boolean lying(LivingEntity e){
        if(!(e instanceof PlayerEntity p)||e.deathTime<=0)return false;
        var s=STABS.get(p.getUuid());
        return s!=null&&s.entity==p.getId()&&s.fate==Fate.DIE&&!s.cutShort();
    }

    /**
     * Where a point of {@code p}'s torso (px, as ArrowStabPose has it posed in {@code pose}) is in the world this frame,
     * as the player renderer draws the body (at their feet, turned to their body's yaw, the model 15/16 of its size).
     */
    public static Vec3d world(PlayerEntity p,float delta,MotionPose pose,Vector3f torso){
        Vector3f m=ArrowStabPose.toModel(pose,torso);
        float yaw=MathHelper.lerpAngleDegrees(delta,p.prevBodyYaw,p.bodyYaw);
        // the model's pixels to the body's blocks: y down from 1.501 blocks over the feet, x to the body's left, scaled
        Vector3f local=new Vector3f(-m.x/16f,1.501f-m.y/16f,m.z/16f).mul(.9375f);
        new Quaternionf().rotationY((180-yaw)*(float)Math.PI/180).transform(local);
        return p.getLerpedPos(delta).add(local.x,local.y,local.z);
    }
    /** A way in {@code p}'s torso (unit) as a way in the world this frame. */
    public static Vec3d way(PlayerEntity p,float delta,MotionPose pose,Vector3f torso){
        Vec3d a=world(p,delta,pose,new Vector3f()),b=world(p,delta,pose,new Vector3f(torso).mul(16));
        return b.subtract(a).normalize();
    }
}
