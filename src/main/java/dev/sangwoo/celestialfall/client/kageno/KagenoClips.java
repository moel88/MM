package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import dev.sangwoo.celestialfall.client.kageno.R6.Pose;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/** Every Kageno body clip, authored from the 60 fps reference (key times in seconds). */
public final class KagenoClips {
    private KagenoClips(){}
    /** Relaxed stance: sword hanging from the right hand, blade toward the lower right front. */
    public static final Pose IDLE=pose().rArm(-80,12).lArm(-84,-10).blade(-38,58);
    /** F: fist in front of the left shoulder, blade diagonally down across the torso to the right. */
    public static final Pose GUARD=pose().rArm(27,-52).lArm(53,-15).blade(-33,73).head(0,10,0);
    /** F: a ~200° flourish (down → up on the right → over to the cross-body diagonal) in 0.1 s. */
    public static final Anim GUARD_IN=Anim.of(
        k(0,IDLE),
        k(.016,pose().rArm(-30,7).blade(80,90),Anim.Ease.LINEAR),
        k(.033,pose().rArm(-10,0).lArm(-60,-20).blade(60,-40),Anim.Ease.LINEAR),
        k(.05,pose().rArm(10,-30).lArm(-20,-20).blade(5,80),Anim.Ease.LINEAR),
        k(.067,pose().rArm(25,-50).lArm(30,-15).blade(0,85),Anim.Ease.LINEAR),
        k(.1,GUARD,Anim.Ease.OUT));
    private static final Anim GUARD_OUT=Anim.of(k(0,GUARD),k(.12,IDLE));
    private static final java.util.Map<java.util.UUID,long[]> GUARDS=new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID,R6.Frame> COMBO_STARTS=new java.util.LinkedHashMap<>();
    /** A queued M1 starts at the pose actually on screen, including early combo inputs. */
    public static void begin(StandClient.Cast cast){
        if(cast.skill()!=M1)return;
        var previous=animated(cast.caster());
        if(previous!=null){
            var f=action(previous,0);
            COMBO_STARTS.put(cast.id(),new R6.Frame(f.joints(),f.grip(),f.fw(),f.up(),f.rt(),
                net.minecraft.util.math.MathHelper.wrapDegrees(f.yaw()),f.pitch(),f.roll()));
            while(COMBO_STARTS.size()>64)COMBO_STARTS.remove(COMBO_STARTS.keySet().iterator().next());
        }
    }
    public static void clear(){GUARDS.clear();COMBO_STARTS.clear();FACINGS.clear();}
    /** Client tick: remembers when each stand user raised and lowered the guard. */
    public static void trackGuards(net.minecraft.client.MinecraftClient mc){
        if(mc.world==null){GUARDS.clear();return;}long now=mc.world.getTime();
        for(var p:mc.world.getPlayers()){
            boolean g=StandClient.visible(p)&&dev.sangwoo.celestialfall.client.CombatClient.snapshot(p.getUuid()).guard();
            var e=GUARDS.get(p.getUuid());
            if(g){if(e==null||e[1]>=0)GUARDS.put(p.getUuid(),new long[]{now,-1});}
            else if(e!=null&&e[1]<0)e[1]=now;
        }
        GUARDS.values().removeIf(e->e[1]>=0&&now-e[1]>20);
    }
    /** Seconds since the guard went up (NaN if not guarding). */
    public static float guardSec(java.util.UUID id,float delta){var e=GUARDS.get(id);var w=net.minecraft.client.MinecraftClient.getInstance().world;return e==null||e[1]>=0||w==null?Float.NaN:(w.getTime()-e[0]+delta)/20f;}
    /** Seconds since the guard came down (NaN if not recently released). */
    public static float releaseSec(java.util.UUID id,float delta){var e=GUARDS.get(id);var w=net.minecraft.client.MinecraftClient.getInstance().world;return e==null||e[1]<0||w==null?Float.NaN:(w.getTime()-e[1]+delta)/20f;}
    /** Guard pose for a player: the flourish in, the hold, or the release back to idle; null when idle. */
    public static R6.Frame guardFrame(java.util.UUID id,float delta){
        float in=guardSec(id,delta);if(!Float.isNaN(in))return GUARD_IN.frame(in);
        float out=releaseSec(id,delta);if(!Float.isNaN(out)&&out<GUARD_OUT.length())return GUARD_OUT.frame(out);
        return null;
    }
    /** Latest cast whose body clip is still playing (the input window can end before the follow-through). */
    public static StandClient.Cast animated(java.util.UUID id){
        StandClient.Cast best=null;
        for(var c:StandClient.CASTS.values())if(c.caster().equals(id)&&c.age(0)<clip(c.skill(),c.combo()).length()*20+1)best=c;
        return best;
    }
    private static final Anim STILL=Anim.of(k(0,IDLE),k(.5,IDLE));
    public static Anim clip(int skill,int combo){var s=KagenoSkills.of(skill);var c=s==null?null:s.clip(combo);return c==null?STILL:c;}
    public static R6.Frame action(StandClient.Cast cast,float delta){
        float sec=cast.age(delta)/20f;var frame=clip(cast.skill(),cast.combo()).frame(sec);
        var start=COMBO_STARTS.get(cast.id());
        return start!=null&&sec<.08f?R6.blend(start,frame,Ease.smooth(sec,0,.08f)):frame;
    }
    public static boolean hidden(StandClient.Cast cast,float delta){var s=KagenoSkills.of(cast.skill());return s!=null&&s.hidden(cast.age(delta)/20f);}
    public static boolean walkLegs(StandClient.Cast cast,float delta){var s=KagenoSkills.of(cast.skill());return s!=null&&s.walkLegs(cast.age(delta)/20f);}
    /** Torso pivot (R6 RootJoint) above the feet, in blocks. */
    public static final float PIVOT=1.05f;
    /**
     * Applies the clip's root motion around the torso pivot and locks the rendered facing to
     * the cast direction, so the body and the world-anchored effects stay aligned.
     */
    public static void root(MatrixStack m,StandClient.Cast cast,float delta,float bodyYaw){
        var w=net.minecraft.client.MinecraftClient.getInstance().world;
        if(w!=null)FACINGS.put(cast.caster(),new float[]{cast.yaw(),w.getTime()+delta});
        var fr=action(cast,delta);Vec3d f=Vec3d.fromPolar(0,cast.yaw()),left=new Vec3d(f.z,0,-f.x);
        Vec3d off=f.multiply(fr.fw()).add(0,fr.up(),0).add(left.multiply(-fr.rt()));
        m.translate(off.x,off.y,off.z);
        if(fr.yaw()!=0||fr.pitch()!=0||fr.roll()!=0){
            float k=(float)Math.PI/180;
            var q=new Quaternionf().rotationAxis(fr.yaw()*k,0,1,0).rotateAxis(fr.pitch()*k,(float)left.x,0,(float)left.z).rotateAxis(fr.roll()*k,(float)f.x,0,(float)f.z);
            m.translate(0,PIVOT,0);m.multiply(q);m.translate(0,-PIVOT,0);
        }
        // The body turns to the cast facing over the first frames instead of snapping to it.
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(net.minecraft.util.math.MathHelper.wrapDegrees(bodyYaw-cast.yaw())*Ease.smooth(cast.age(delta)/20f,0,.08f)));
    }
    /** Cast facing of each caster's latest body clip and when it was last shown (world ticks). */
    private static final java.util.Map<java.util.UUID,float[]> FACINGS=new java.util.HashMap<>();
    /** Ticks the body takes to turn from a finished clip's facing back to its own. */
    static final float UNWIND=4;
    /**
     * After a body clip ends, turns the body from the clip's facing back to its own over {@link #UNWIND} ticks
     * (the player may have looked elsewhere meanwhile) instead of snapping.
     */
    public static void unwind(MatrixStack m,java.util.UUID caster,float delta,float bodyYaw){
        var e=FACINGS.get(caster);var w=net.minecraft.client.MinecraftClient.getInstance().world;if(e==null||w==null)return;
        float since=w.getTime()+delta-e[1];
        if(since<0||since>=UNWIND){if(since>=UNWIND)FACINGS.remove(caster);return;}
        float u=since/UNWIND,keep=1-u*u*(3-2*u);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(net.minecraft.util.math.MathHelper.wrapDegrees(bodyYaw-e[0])*keep));
    }
    /**
     * World position of the sword hand and the world blade direction for pose {@code p} of a
     * caster standing at {@code feet} facing {@code yaw}; mirrors {@link #root}.
     */
    public static Vec3d[] blade(Vec3d feet,float yaw,Pose p){
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);var q=torso(f,p);
        Vec3d local=right.multiply(.3125).add(0,1.375-PIVOT,0).add(world(p.rArm,f,right).multiply(.625));
        return new Vec3d[]{pivot(feet,f,p).add(rotate(q,local)),rotate(q,world(p.blade,f,right)).normalize()};
    }
    /** World position of the sword shoulder for pose {@code p}, as {@link #blade}. */
    public static Vec3d shoulder(Vec3d feet,float yaw,Pose p){
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        return pivot(feet,f,p).add(rotate(torso(f,p),right.multiply(.3125).add(0,1.375-PIVOT,0)));
    }
    /** The torso's rotation and the world position of its pivot for pose {@code p} facing {@code f}. */
    private static Quaternionf torso(Vec3d f,Pose p){
        Vec3d left=new Vec3d(f.z,0,-f.x);float k=(float)Math.PI/180;
        return new Quaternionf().rotationAxis(p.yaw*k,0,1,0).rotateAxis(p.pitch*k,(float)left.x,0,(float)left.z).rotateAxis(p.roll*k,(float)f.x,0,(float)f.z);
    }
    private static Vec3d pivot(Vec3d feet,Vec3d f,Pose p){return feet.add(f.multiply(p.fw)).add(0,p.up+PIVOT,0).add(new Vec3d(-f.z,0,f.x).multiply(p.rt));}
    private static Vec3d world(R6.Dir d,Vec3d f,Vec3d right){return right.multiply(d.rt()).add(0,d.up(),0).add(f.multiply(d.fw()));}
    private static Vec3d rotate(Quaternionf q,Vec3d v){var r=q.transform(new org.joml.Vector3f((float)v.x,(float)v.y,(float)v.z));return new Vec3d(r.x,r.y,r.z);}
    public static Vec3d groundOffset(StandClient.Cast cast,float delta){
        var fr=action(cast,delta);Vec3d f=Vec3d.fromPolar(0,cast.yaw()),left=new Vec3d(f.z,0,-f.x);
        return f.multiply(fr.fw()).add(left.multiply(-fr.rt()));
    }
    public static R6.Frame idle(){return R6.solve(IDLE);}
    public static R6.Frame guard(){return R6.solve(GUARD);}
}
