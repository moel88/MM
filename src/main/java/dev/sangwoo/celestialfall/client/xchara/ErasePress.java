package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.xchara.EraseGround;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * X Chara's terrain delete as its user does it (2.133.0, H with the hack on; EraseRules, the server's XCharaAttack and
 * XCharaErase): a press of the panel (the user's "패널을 건들이고"). Its body's motion is a cast of the knife's skill
 * (KnifeCombo, {@link #CLIP}): they stand as they stand with the knife, the panel's arm comes up in front of them and
 * its hand taps the panel as the grounds are marked (EraseRules.MARK), as their T's press does (WeakenClient), lit as it
 * is tapped ({@link #press}: HackPanel), looking down at it; then the arm goes back out. Its one sound of its own is the
 * panel's click as the hand taps it ({@link #CUES}); the grounds' "!!!" and cubes are EraseClient's and EraseFx's. As H
 * is pressed the point the middle of the screen aims at goes with it ({@link #aimPoint}, XCharaClient): the ground
 * marked where they look is found there.
 */
public final class ErasePress {
    private ErasePress(){}
    static final float LENGTH_S=EraseRules.LENGTH/20f;
    /** How long the cast is drawn (seconds): its motion, and a little. */
    public static final float LIFE=LENGTH_S+.3f;
    /** The tap (seconds): the hand on the panel as the grounds are marked. */
    static final float TAP=EraseRules.MARK/20f;
    /** Its sounds, by its clock (each {name, tick, part}): the panel's click as the hand taps it (the jab's press, the user's own "띡"). */
    static final Object[][] CUES={{"xchara_jab_press",EraseRules.MARK-1,1f}};
    static float stopped(StandClient.Cast c){return KnifeClient.stoppedAt(c);}
    /**
     * The panel pressed {@code sec} seconds in (stopped short at {@code stopped}, NaN if not): {how far the arm and the
     * panel have come before the body, how far the hand is down on it, how lit the panel is, 1 for violet}. As their T's:
     * the arm up by 0.1 s, the tap from 0.04 to 0.22 s (on it at the mark), lit as it is tapped, the arm down from 0.34 s
     * to 0.55 s.
     */
    static float[] pressAt(float sec,float stopped){
        float lift=Ease.smooth(sec,0,.1f)*(1-Ease.smooth(sec,.34f,.55f));
        if(!Float.isNaN(stopped))lift*=1-Ease.smooth(sec,stopped,stopped+.15f);
        float tap=Ease.smooth(sec,.04f,TAP)*(1-Ease.smooth(sec,.13f,.22f));
        float lit=Ease.smooth(sec,TAP-.04f,TAP)*(1-Ease.smooth(sec,TAP+.08f,TAP+.36f));
        return new float[]{lift,tap,Float.isNaN(stopped)||stopped>TAP?lit:0,1};
    }
    /** This player's press of the panel for their terrain delete this frame (HackMode.press), or null. */
    public static float[] press(UUID id,float delta){
        var c=KnifeClient.eraseOf(id);if(c==null)return null;
        float sec=c.age(delta)/20f;if(sec<0||sec>LIFE)return null;
        return pressAt(sec,stopped(c));
    }
    /** The motion: their stance all through, the panel's arm and their look as the press has them ({@link #fix}). */
    static final Anim CLIP=Anim.joints(k(0,KnifeCombo.STANCE),k(LENGTH_S,KnifeCombo.STANCE)).after(ErasePress::fix);
    /**
     * A frame of it: the stance as the model stands it (every limb straight), the panel's arm out with the panel as it is
     * held standing and up for the press and its tap (XCharaIdle.arms, their E's and T's arm), and the head bowed to the
     * panel and turned a little to its side as the arm is up.
     */
    static R6.Frame fix(float sec,R6.Frame f){
        var rest=KnifeCombo.AT_REST;
        float[] press=pressAt(sec,Float.NaN);MotionPose idle=new MotionPose();
        XCharaIdle.arms(idle,true,1,1,0,press[0],press[1]);
        MotionPose m=rest.joints().copy();
        m.joint[MotionPose.LEFT_ARM]=idle.joint[MotionPose.LEFT_ARM].clone();m.elbow[1]=idle.elbow[1];
        float[] head=m.joint[MotionPose.HEAD];
        m.joint[MotionPose.HEAD]=new float[]{head[0]+press[0]*(XCharaIdle.LOOK_DOWN-head[0]),head[1]+press[0]*(XCharaIdle.LOOK_ASIDE-head[1]),head[2]};
        return new R6.Frame(m,rest.grip(),rest.fw(),rest.up(),rest.rt(),rest.yaw(),rest.pitch(),rest.roll());
    }
    /** For the capture tools: a point the local player's terrain delete is aimed at whatever their screen shows (a filming camera is not theirs); null, the screen's middle. */
    public static Vec3d aimAt;
    /** The point the middle of this client's screen aims at now (EraseGround.aimed, from its camera): sent with H, the ground marked where they look is found there. */
    public static Vec3d aimPoint(MinecraftClient mc){
        if(aimAt!=null)return aimAt;
        var cam=mc.gameRenderer.getCamera();
        return EraseGround.aimed(mc.world,mc.player,mc.player.getPos(),cam.getPos(),Vec3d.fromPolar(cam.getPitch(),cam.getYaw()));
    }
}
