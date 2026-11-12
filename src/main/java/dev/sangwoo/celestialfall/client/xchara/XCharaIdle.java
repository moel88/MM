package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import java.util.UUID;
import org.joml.Quaternionf;

/**
 * X Chara standing at rest (2.116.0, user request: "엑스 차라 기본 자세를 만들자 다리와 팔과 손을 쭉 펴서 정자세로 있고
 * 다리를 살짝 벌리고 검들고 있는 팔도 살짝 벌리고 있고 패널 부분 팔은 몸에 붙이고 있다가 패널 소환하면 팔 벌리게"; the
 * user's video of their Q shows the stance from in front and from the side): upright at attention, the legs and both
 * arms straight. The legs are set a little apart; the arm that holds the knife hangs a little out from the body, the
 * knife level ahead off its fist (its display, models/item/x_chara_knife.json); the other arm hangs against the body,
 * and comes out from it, straight still, as the hack's panel is called (Q: HackLook.arm), the panel by its hand
 * (HackPanel).
 *
 * <p>Walking, the legs take the walk's own stride and knees ({@link #stand} eases out as they get going); the knife's
 * arm stays out and straight and keeps a quarter of the walk's swing (the knife is three blocks long); the other arm
 * swings as anyone's does, or, holding the panel, stays out and keeps a quarter of it too. A blow, a guard, a dash or a
 * flinch has the body as the rig poses it. Their knife's hits begin and end in this stance (KnifeCombo.AT_REST).
 */
public final class XCharaIdle {
    private XCharaIdle(){}
    /** How far the knife's arm hangs out from the body, how far the other arm comes out as it holds the panel, and how far each leg is set out (degrees). */
    public static final float KNIFE_OUT=14,PANEL_OUT=22,LEGS_OUT=5;
    /** How much of the walk's swing the knife's arm keeps (and the panel's, holding it). */
    public static final float SWING=.25f;
    /** How fast they go before the stance's legs and free arm are gone (CombatClient: 1 at walking pace), and below which they are whole. */
    static final float GOING=.45f,STILL=.06f;
    /** Their breath's clock, as the rig's (MotionClips.locomotion: sin(time · 0.085)), and how far it moves the knife's arm and the panel's (degrees out and in). */
    static final float BREATH=.085f,KNIFE_BREATH=.4f,PANEL_BREATH=.5f;

    /**
     * Stands {@code pose} at attention by how little they walk ({@code amount}, the rig's own stride measure): the rig
     * stands everyone with the knees a little bent (MotionClips.locomotion, some 23°, the hips back half that); they
     * stand straight-legged, the legs set a little apart, the feet on the ground (legs set apart end a hair higher: the
     * hips come down that much). Before the air's and the landing's (CombatClient.pose): a jump still tucks the legs and
     * a landing still takes it in the knees.
     */
    public static void stand(MotionPose pose,float amount){
        float w=1-Ease.smooth(amount,STILL,GOING);if(w<=0)return;
        for(int i=0;i<2;i++){
            float[] leg=pose.joint[MotionPose.RIGHT_LEG+i];
            leg[0]=MotionPose.lerp(leg[0],0,w);leg[1]=MotionPose.lerp(leg[1],0,w);
            leg[2]=MotionPose.lerp(leg[2],(i==0?1:-1)*LEGS_OUT,w);
            pose.knee[i]=MotionPose.lerp(pose.knee[i],0,w);
        }
        pose.bob+=12*(1-(float)Math.cos(Math.toRadians(LEGS_OUT)))*w;
    }
    /**
     * The stance's arms on {@code pose} (the rig's own, as they walk or stand): the knife's arm (the right one if
     * {@code rightHanded}) straight, out from the body, a quarter of its swing kept; the other arm straight against the
     * body by {@code still} (1 standing, 0 at walking pace: its own swing then), and by {@code held} (how far the hack
     * has it out: HackLook.arm) out from the body, straight, a quarter of its swing. {@code time} is the breath's clock
     * (ticks).
     */
    public static void arms(MotionPose pose,boolean rightHanded,float still,float held,float time){arms(pose,rightHanded,still,held,time,0,0);}
    /** The panel's arm up in front of them for a press of it (2.119.0, their E: HealClient): the upper arm this far forward, this far out, the elbow bent this far; and how far a tap brings the hand down (the upper arm, the elbow; degrees). */
    static final float PRESS_FORWARD=44,PRESS_OUT=20,PRESS_ELBOW=62,TAP_ARM=6,TAP_ELBOW=14;
    /** Each half of the arm (blocks: six pixels of the model). */
    static final float HALF_ARM=.375f;
    /**
     * Where the hand of the arm up for a press is, {@code tap} of the way down on a tap (0 to 1): how far ahead of the
     * shoulder and how far below it (blocks), from the upper arm's and the elbow's angles (HackPanel keeps the panel
     * where it floats as the hand comes down on it by these).
     */
    static float[] pressHand(float tap){
        double arm=Math.toRadians(PRESS_FORWARD-TAP_ARM*tap),fore=arm+Math.toRadians(PRESS_ELBOW-TAP_ELBOW*tap);
        return new float[]{HALF_ARM*(float)(Math.sin(arm)+Math.sin(fore)),HALF_ARM*(float)(Math.cos(arm)+Math.cos(fore))};
    }
    /** How far they look down at the panel as they press it, and aside to its hand's side (degrees; the model's head). */
    public static final float LOOK_DOWN=34,LOOK_ASIDE=16;
    /**
     * The same with a press of the panel (2.119.0, their E): by {@code lift} (0 to 1) the panel's arm comes from where
     * it holds the panel up in front of them, the forearm about level ahead, and by {@code tap} (0 to 1) its hand comes
     * down onto the panel (HackPanel brings the panel up to it by as much).
     */
    public static void arms(MotionPose pose,boolean rightHanded,float still,float held,float time,float lift,float tap){
        int knife=rightHanded?0:1,off=1-knife;float out=rightHanded?1:-1;
        still=clamp(still);held=clamp(held);lift=clamp(lift);tap=clamp(tap);
        float breath=(float)Math.sin(time*BREATH),other=(float)Math.sin(time*BREATH+1.3f);
        float[] k=pose.joint[MotionPose.RIGHT_ARM+knife];
        pose.joint[MotionPose.RIGHT_ARM+knife]=new float[]{k[0]*SWING,0,out*(KNIFE_OUT+KNIFE_BREATH*breath)};
        pose.elbow[knife]=0;
        float[] o=pose.joint[MotionPose.RIGHT_ARM+off];
        float pitch=o[0],yaw=MotionPose.lerp(o[1],0,still),roll=MotionPose.lerp(o[2],0,still),elbow=MotionPose.lerp(pose.elbow[off],0,still);
        float[] at={MotionPose.lerp(pitch,pitch*SWING,held),MotionPose.lerp(yaw,0,held),MotionPose.lerp(roll,-out*(PANEL_OUT+PANEL_BREATH*other),held)};
        float bend=MotionPose.lerp(elbow,0,held);
        if(lift>0){
            at[0]=MotionPose.lerp(at[0],-PRESS_FORWARD+TAP_ARM*tap,lift);at[1]=MotionPose.lerp(at[1],0,lift);at[2]=MotionPose.lerp(at[2],-out*PRESS_OUT,lift);
            bend=MotionPose.lerp(bend,-PRESS_ELBOW+TAP_ELBOW*tap,lift);
        }
        pose.joint[MotionPose.RIGHT_ARM+off]=at;
        pose.elbow[off]=bend;
    }
    /**
     * A hit of their knife with the panel held ({@code held}, HackLook.arm): the other arm of {@code pose} (a frame of
     * the hit) swung that far out from the body about the shoulder, whatever the hit has it do, so the panel is carried
     * through the hit as it is held standing (the hit's stance has that arm against the body: with this it is the
     * stance's arm out, and nothing jumps as a hit begins or ends).
     */
    public static void carry(MotionPose pose,boolean rightHanded,float held){
        held=clamp(held);if(held<=0)return;
        int off=rightHanded?1:0;float out=rightHanded?-1:1;
        float[] e=pose.joint[MotionPose.RIGHT_ARM+off];
        var q=new Quaternionf().rotationZ(out*PANEL_OUT*held*RAD).mul(new Quaternionf().rotationZYX(e[2]*RAD,e[1]*RAD,e[0]*RAD));
        pose.joint[MotionPose.RIGHT_ARM+off]=R6.angles(q);
    }
    /** How much of the stance the free arm takes now (0 to 1): all of it while they stand (or jump on the spot), easing out as they get going, none at walking pace. */
    public static float weight(UUID id,float delta){return 1-Ease.smooth(CombatClient.travel(id,delta),STILL,GOING);}

    private static final float RAD=(float)Math.PI/180;
    private static float clamp(float v){return Math.max(0,Math.min(1,v));}
}
