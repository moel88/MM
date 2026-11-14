package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import dev.sangwoo.celestialfall.client.kageno.R6.Pose;
import dev.sangwoo.celestialfall.combat.MotionPose;
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
    /**
     * A queued M1 starts at the pose actually on screen, including early combo inputs; a later hit of the combo, after
     * the last one has come to rest, starts from the rest pose instead of jumping to where the last one ended.
     */
    public static void begin(StandClient.Cast cast){
        // (Lee So-sang's sword's hits too, 2.77.0: every one of them, the first from their stance)
        boolean sword=cast.skill()==dev.sangwoo.celestialfall.isosang.IsosangRules.SKILL;
        // (X Chara's knife's hits too, 2.114.0: the first from their hold of it)
        boolean knife=cast.skill()==dev.sangwoo.celestialfall.xchara.XCharaCombo.SKILL;
        if(cast.skill()!=M1&&!sword&&!knife)return;
        var previous=animated(cast.caster());
        if(previous!=null||cast.combo()>1||sword||knife){
            var f=previous!=null?action(previous,0):sword?dev.sangwoo.celestialfall.client.isosang.IsosangSlash.start(cast.combo())
                :knife?dev.sangwoo.celestialfall.client.xchara.KnifeCombo.stance():idle();
            COMBO_STARTS.put(cast.id(),new R6.Frame(f.joints(),f.grip(),f.fw(),f.up(),f.rt(),
                net.minecraft.util.math.MathHelper.wrapDegrees(f.yaw()),f.pitch(),f.roll()));
            while(COMBO_STARTS.size()>64)COMBO_STARTS.remove(COMBO_STARTS.keySet().iterator().next());
        }
    }
    public static void clear(){GUARDS.clear();COMBO_STARTS.clear();FACINGS.clear();BodyGhost.clear();}
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
        // Lee So-sang's sword's hits (2.77.0): their swings drive the body the same way
        var swing=dev.sangwoo.celestialfall.client.isosang.IsosangClient.animated(id);
        // X Chara's knife's hits (2.114.0): the same
        if(swing==null)swing=dev.sangwoo.celestialfall.client.xchara.KnifeClient.animated(id);
        return swing!=null?swing:best;
    }
    private static final Anim STILL=Anim.of(k(0,IDLE),k(.5,IDLE));
    public static Anim clip(int skill,int combo){var s=KagenoSkills.of(skill);var c=s==null?null:s.clip(combo);return c==null?STILL:c;}
    public static R6.Frame action(StandClient.Cast cast,float delta){
        float sec=cast.age(delta)/20f;var anim=clip(cast.skill(),cast.combo());
        // (2.113.0) Lee So-sang's H: its ending (the flare, her drop) as its gauge runs dry, whenever that is
        var frame=cast.skill()==dev.sangwoo.celestialfall.isosang.IsosangRules.SKILL&&cast.combo()==dev.sangwoo.celestialfall.isosang.IsosangRules.FORMATION
            ?dev.sangwoo.celestialfall.client.isosang.IsosangClient.formationFrame(cast,anim,sec):anim.frame(sec);
        if(cast.skill()==R)frame=TimeWalkerSkill.walking(frame,walkWeight(cast,delta));
        else if(cast.skill()==E)frame=NeonThrustSkill.walking(frame,walkWeight(cast,delta));
        var start=COMBO_STARTS.get(cast.id());
        return start!=null&&sec<.08f?R6.blend(start,frame,Ease.smooth(sec,0,.08f)):frame;
    }
    public static boolean hidden(StandClient.Cast cast,float delta){var s=KagenoSkills.of(cast.skill());return s!=null&&s.hidden(cast.age(delta)/20f);}
    /** How much R's or E's caster walks (0 standing, 1 walking): the walking cast eases in and out with the steps. */
    public static float walkWeight(StandClient.Cast cast,float delta){
        return cast.skill()!=R&&cast.skill()!=E?0:Ease.smooth(dev.sangwoo.celestialfall.client.CombatClient.moving(cast.caster(),delta),.08f,.32f);
    }
    /**
     * R's walking cast: the walk cycle's legs, knees and bob blended in by {@code w}. The legs are turned back against
     * the body's turn ({@code yaw}, degrees left of the walking facing; the root turn carries the whole model, at most
     * 50 degrees are undone) so the hips keep facing the way the caster walks while the upper body cuts.
     */
    public static void walkLegs(MotionPose pose,MotionPose walk,float w,float yaw){
        if(w<=0)return;
        var turn=new Quaternionf().rotationY(Math.max(-50,Math.min(50,yaw))*(float)Math.PI/180);
        for(int i=MotionPose.RIGHT_LEG;i<=MotionPose.LEFT_LEG;i++){
            var step=new Quaternionf(turn).mul(Anim.quat(walk.joint[i]));
            pose.joint[i]=R6.euler(Anim.quat(pose.joint[i]).slerp(step,w));
        }
        for(int i=0;i<2;i++)pose.knee[i]=MotionPose.lerp(pose.knee[i],walk.knee[i],w);
        pose.bob=MotionPose.lerp(pose.bob,walk.bob,w);
    }
    /** Torso pivot (R6 RootJoint) above the feet, in blocks. */
    public static final float PIVOT=1.05f;
    /**
     * Applies the clip's root motion around the torso pivot and locks the rendered facing to
     * the cast direction, so the body and the world-anchored effects stay aligned.
     */
    public static void root(MatrixStack m,StandClient.Cast cast,float delta,float bodyYaw){
        var w=net.minecraft.client.MinecraftClient.getInstance().world;float facing=facing(cast,delta);
        if(w!=null)FACINGS.put(cast.caster(),new float[]{facing,w.getTime()+delta});
        var fr=action(cast,delta);Vec3d f=Vec3d.fromPolar(0,facing),left=new Vec3d(f.z,0,-f.x);
        Vec3d off=f.multiply(fr.fw()).add(0,fr.up(),0).add(left.multiply(-fr.rt()));
        // (2.82.0) Lee So-sang's H: the body as high as the sword formation's plan has it (the leap, the dive onto the pommel
        // of the sword struck under her, standing on it), whatever swing moves it (R may be used up there)
        if(cast.skill()==dev.sangwoo.celestialfall.isosang.IsosangRules.SKILL)off=off.add(0,dev.sangwoo.celestialfall.client.isosang.IsosangClient.lift(cast.caster(),delta),0);
        // (2.93.0) and level, over the sword Z flies her to, as she lands on it (seen a little late, she would walk there)
        if(cast.skill()==dev.sangwoo.celestialfall.isosang.IsosangRules.SKILL)off=off.add(dev.sangwoo.celestialfall.client.isosang.IsosangClient.shift(cast.caster(),delta));
        m.translate(off.x,off.y,off.z);
        if(fr.yaw()!=0||fr.pitch()!=0||fr.roll()!=0){
            float k=(float)Math.PI/180;
            var q=new Quaternionf().rotationAxis(fr.yaw()*k,0,1,0).rotateAxis(fr.pitch()*k,(float)left.x,0,(float)left.z).rotateAxis(fr.roll()*k,(float)f.x,0,(float)f.z);
            m.translate(0,PIVOT,0);m.multiply(q);m.translate(0,-PIVOT,0);
        }
        // The body turns to the cast facing over the first frames instead of snapping to it.
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(net.minecraft.util.math.MathHelper.wrapDegrees(bodyYaw-facing)*turnIn(cast,delta)));
    }
    /**
     * How far a cast's body has turned from the caster's own facing to the cast facing (0 to 1, over its first frames);
     * all the way at once for Lee So-sang's T used again as she stands on the far sword (2.96.4: its facing begins where
     * the one before left her, IsosangClient.lungeFacing).
     */
    static float turnIn(StandClient.Cast cast,float delta){
        if(cast.skill()==dev.sangwoo.celestialfall.isosang.IsosangRules.SKILL&&dev.sangwoo.celestialfall.client.isosang.IsosangClient.chainedAfter(cast)!=null)return 1;
        // (2.101.0) Lee So-sang's H again: its facing begins where her formation had her (IsosangClient.finaleFacing)
        if(cast.skill()==dev.sangwoo.celestialfall.isosang.IsosangRules.SKILL&&cast.combo()==dev.sangwoo.celestialfall.isosang.IsosangRules.FINALE)return 1;
        return Ease.smooth(cast.age(delta)/20f,0,.08f);
    }
    /** How far {@link #root} has turned the body left of its own facing {@code bodyYaw} to the cast facing (degrees). */
    public static float facingTurn(StandClient.Cast cast,float delta,float bodyYaw){
        return net.minecraft.util.math.MathHelper.wrapDegrees(bodyYaw-facing(cast,delta))*turnIn(cast,delta);
    }
    /** The way a cast's body is drawn facing (degrees): the cast facing, but T held and E turn with the caster's aim ({@link KagenoAim}). */
    public static float facing(StandClient.Cast cast,float delta){
        // (2.96.0) Lee So-sang's T: the way she dashed, turning back to her formation's on the far sword
        if(cast.skill()==dev.sangwoo.celestialfall.isosang.IsosangRules.SKILL&&cast.combo()==dev.sangwoo.celestialfall.isosang.IsosangRules.LUNGE)return dev.sangwoo.celestialfall.client.isosang.IsosangClient.lungeFacing(cast,delta);
        // (2.101.0) H again: from her formation's facing round to the way she looked as she pressed it
        if(cast.skill()==dev.sangwoo.celestialfall.isosang.IsosangRules.SKILL&&cast.combo()==dev.sangwoo.celestialfall.isosang.IsosangRules.FINALE)return dev.sangwoo.celestialfall.client.isosang.IsosangClient.finaleFacing(cast,delta);
        // (2.128.0) X Chara's slam: turned to the way it dives
        if(cast.skill()==dev.sangwoo.celestialfall.xchara.XCharaCombo.SKILL&&cast.combo()==dev.sangwoo.celestialfall.xchara.XCharaCombo.SLAM)return dev.sangwoo.celestialfall.client.xchara.OverwriteSlam.facing(cast,delta);
        // (2.132.1) X Chara's rain of knives: the stab turned square to the one it goes into
        if(cast.skill()==dev.sangwoo.celestialfall.xchara.XCharaCombo.SKILL&&cast.combo()==dev.sangwoo.celestialfall.xchara.XCharaCombo.RAIN)return dev.sangwoo.celestialfall.client.xchara.BladeRain.facing(cast,delta);
        return cast.skill()==RUSH?RushAim.facing(cast,delta):cast.skill()==E?KagenoAim.thrust(cast,delta):cast.yaw();
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
        // (2.104.0) the hand as near the shoulder as the elbow's bend brings it (straight, 0.625 blocks)
        Vec3d local=right.multiply(.3125).add(0,1.375-PIVOT,0).add(world(p.rArm,f,right).multiply(R6.armReach(p.rElbow)));
        return new Vec3d[]{pivot(feet,f,p).add(rotate(q,local)),rotate(q,world(p.blade,f,right)).normalize()};
    }
    /** World position of the free (left) hand for pose {@code p}, as {@link #blade} finds the sword hand. */
    public static Vec3d freeHand(Vec3d feet,float yaw,Pose p){
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);var q=torso(f,p);
        Vec3d local=right.multiply(-.3125).add(0,1.375-PIVOT,0).add(world(p.lArm,f,right).multiply(R6.armReach(p.lElbow)));
        return pivot(feet,f,p).add(rotate(q,local));
    }
    /** World position of the free (left) shoulder for pose {@code p}, as {@link #freeHand} (2.83.0: Lee So-sang's G points with that hand). */
    public static Vec3d freeShoulder(Vec3d feet,float yaw,Pose p){
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        return pivot(feet,f,p).add(rotate(torso(f,p),right.multiply(-.3125).add(0,1.375-PIVOT,0)));
    }
    /** World position of the head's middle for pose {@code p} (2.83.0: the camera of Lee So-sang's G frames her face). */
    public static Vec3d head(Vec3d feet,float yaw,Pose p){
        Vec3d f=Vec3d.fromPolar(0,yaw);
        return pivot(feet,f,p).add(rotate(torso(f,p),new Vec3d(0,1.5+.25-PIVOT,0)));
    }
    /** World position of the sword shoulder for pose {@code p}, as {@link #blade}. */
    public static Vec3d shoulder(Vec3d feet,float yaw,Pose p){
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        return pivot(feet,f,p).add(rotate(torso(f,p),right.multiply(.3125).add(0,1.375-PIVOT,0)));
    }
    /**
     * World position of a point fixed on the torso for pose {@code p} (2.101.0: Lee So-sang's eyes and raised fingers in
     * H again): {@code rt} to the right of its middle, {@code up} over the feet and {@code fw} ahead (blocks, as the model
     * is built), turned as the pose turns the torso.
     */
    public static Vec3d onTorso(Vec3d feet,float yaw,Pose p,double rt,double up,double fw){
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        return pivot(feet,f,p).add(rotate(torso(f,p),right.multiply(rt).add(0,up-PIVOT,0).add(f.multiply(fw))));
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
        var fr=action(cast,delta);Vec3d f=Vec3d.fromPolar(0,facing(cast,delta)),left=new Vec3d(f.z,0,-f.x);
        Vec3d off=f.multiply(fr.fw()).add(left.multiply(-fr.rt()));
        // (2.101.1) Lee So-sang's body drawn away from where she is (H again's ride, Z's and T's flights): its shadow with it
        if(cast.skill()==dev.sangwoo.celestialfall.isosang.IsosangRules.SKILL)off=off.add(dev.sangwoo.celestialfall.client.isosang.IsosangClient.shift(cast.caster(),delta));
        // (2.128.0) and X Chara's through their slam (drawn up in the air and down its dive by their renderer's offset, BeatdownRenderMixin: their shadow goes under them)
        if(cast.skill()==dev.sangwoo.celestialfall.xchara.XCharaCombo.SKILL&&cast.combo()==dev.sangwoo.celestialfall.xchara.XCharaCombo.SLAM)off=off.add(dev.sangwoo.celestialfall.client.xchara.OverwriteSlam.shift(cast,delta));
        // (2.129.0) and through their three cuts' going forward
        if(cast.skill()==dev.sangwoo.celestialfall.xchara.XCharaCombo.SKILL&&cast.combo()==dev.sangwoo.celestialfall.xchara.XCharaCombo.CUTTER_DASH)off=off.add(dev.sangwoo.celestialfall.client.xchara.EdgeCutter.shift(cast,delta));
        // (2.132.0) and through their rain of knives' run and its stab's last way
        if(cast.skill()==dev.sangwoo.celestialfall.xchara.XCharaCombo.SKILL&&dev.sangwoo.celestialfall.xchara.XCharaCombo.rain(cast.combo()))off=off.add(dev.sangwoo.celestialfall.client.xchara.BladeRain.shift(cast,delta));
        return off;
    }
    public static R6.Frame idle(){return R6.solve(IDLE);}
    public static R6.Frame guard(){return R6.solve(GUARD);}
}
