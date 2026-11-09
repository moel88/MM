package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.stand.ArrowStabPose;
import dev.sangwoo.celestialfall.combat.MotionPose;
import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Lee So-sang standing at rest (2.105.0, user request "이제 이소상 가만히 있을때 자세를 만들자 왼쪽 팔을 굽어서 손을
 * 엉덩이쪽에 가져다대서 뒷짐 지는 자세를 취하소 오른쪽손은 오른쪽으로 내밀어 이게 기본적으로 서 있는 자세고 검은 평타나
 * 스킬을 쓸때만 나오게", with two pictures of her standing in the game she is from, from in front and from behind): her
 * left arm bent, its hand behind her at the left of the small of her back; her right arm held out down and to the
 * right, a little before her, its hand open, the elbow just soft; no sword in her hands (IsosangSheath). Walking she
 * swings her arms as anyone does: the stance eases out as she gets going and back in as she stops ({@link #weight},
 * on how fast she goes over the ground, so a jump on the spot keeps it and a running jump does not bring it back).
 *
 * <p>The arms are set by reaching each hand for its place (ArrowStabPose.reach: the two-bone arm of the player model,
 * the elbow toward a pole), in the torso's own space (model px from the neck's middle: x to her left, y down, z back),
 * so they keep to her body as it breathes, leans or turns. Minecraft's arms are short against its body: a hand bent
 * back behind her reaches the small of her back, not her hip (the pictures' hand is at the hip, the elbow bent a
 * quarter turn and more; both cannot be had with these arms: the hand is put at her waist behind her, the elbow bent
 * some 60°, just clear of her back).
 */
public final class IsosangIdle {
    private IsosangIdle(){}
    /**
     * Each arm turns about its own middle here, not about its pivot (which the player model puts a pixel off the
     * middle of the arm, toward the body: a bent arm turned round to put its hand behind her would swing its whole
     * upper arm forward and into her side about that pivot): the stance's shoulders are the arms' middles at the
     * pivot's height, {@link #pivots} moves the pivots so the arms turn so. {@code half} is how far an arm's middle is
     * off its pivot (1 px, 0.5 for slim arms).
     */
    static Vector3f shoulder(int arm,float half){return new Vector3f(arm==0?-5-half:5+half,2,0);}
    /**
     * Where the right fist is, from its shoulder (torso px): out to her right a little over a third of the way to level
     * (35°), a little before her, about as high as her hip; the elbow just soft (15°), turned back (its pole), so the
     * forearm comes a little forward.
     */
    static final Vector3f R_REACH=new Vector3f(-4.72f,6.86f,-1.29f),R_POLE=new Vector3f(-.3f,0,1);
    /**
     * Where the left fist is, from its shoulder (torso px): behind her at the left of the small of her back, just clear
     * of it (her back is 2 px behind her middle, the arm 4 px thick); the elbow out to the left a little and back (its
     * pole), bent some 60°, the upper arm back only a third of the way to level (any further and the top of the arm,
     * above its pivot, comes up out of her shoulder in front).
     */
    static final Vector3f L_REACH=new Vector3f(-2.8f,5f,4.45f),L_POLE=new Vector3f(1,.8f,.2f);
    /**
     * Her breath's clock, as the rig's (MotionClips.locomotion: sin(time · 0.085)), and how far it moves each hand (torso
     * px): the right one out and up a little, square to its arm (the arm nearly straight, a hand pushed along it would
     * straighten it out and its elbow would snap), the left one a little down her back.
     */
    static final float BREATH=.085f;
    static final Vector3f R_BREATH=new Vector3f(-.15f,-.1f,0),L_BREATH=new Vector3f(.04f,.14f,-.04f);
    /** How fast she goes before the stance is gone (CombatClient.travel: 1 at walking pace), and below which it is whole. */
    static final float GOING=.45f,STILL=.06f;
    /** Ticks the body takes to settle into the stance from anything else (a swing's end, a dash, a flinch): PoseBlend. */
    public static final float SETTLE=6;

    /**
     * The stance's arms at {@code time} (ticks, the breath's clock) for arms whose middles are {@code half} px off their
     * pivots: {pitch, yaw, roll, elbow} (degrees) for the right arm, then the left, each about its own middle.
     */
    public static float[][] arms(float time,float half){
        Vector3f[] f=fists(time,half);
        float[] r=ArrowStabPose.reach(shoulder(0,half),f[0],R_POLE,0);
        float[] l=ArrowStabPose.reach(shoulder(1,half),f[1],L_POLE,0);
        return new float[][]{r,l};
    }
    /** Where each fist is in the stance at {@code time} (torso px): the right one, then the left. */
    public static Vector3f[] fists(float time,float half){
        float breath=(float)Math.sin(time*BREATH);
        return new Vector3f[]{new Vector3f(R_BREATH).mul(breath).add(R_REACH).add(shoulder(0,half)),new Vector3f(L_BREATH).mul(breath).add(L_REACH).add(shoulder(1,half))};
    }
    /**
     * Where her open right hand's fingers end in the stance at {@code time} (torso px): past the fist along the forearm to
     * the end of the arm (what the empowered glow sits at: the fist is inside the arm's block).
     */
    public static Vector3f fingers(float time,float half){
        float[] r=arms(time,half)[0];Vector3f fist=fists(time,half)[0];
        Vector3f elbow=quat(r).transform(new Vector3f(0,4,0)).add(shoulder(0,half));
        return new Vector3f(fist).sub(elbow).normalize().mul(1.6f).add(fist);
    }
    /**
     * Turns the arms of {@code pose} toward the stance's by {@code w} (0 leaves them, 1 is the stance): each shoulder
     * along the shortest turn, each elbow straight across.
     */
    public static void apply(MotionPose pose,float time,float w,float half){
        if(w<=0)return;w=Math.min(1,w);
        float[][] arms=arms(time,half);
        for(int i=0;i<2;i++){
            int part=MotionPose.RIGHT_ARM+i;float[] a=arms[i];
            if(w>=1)pose.joint[part]=new float[]{a[0],a[1],a[2]};
            else{
                var from=quat(pose.joint[part]);var to=quat(a);
                pose.joint[part]=angles(from.slerp(to,w));
            }
            pose.elbow[i]=MotionPose.lerp(pose.elbow[i],a[3],w);
        }
    }
    /**
     * How far each arm's pivot moves (torso px, the right arm's then the left's) for it to turn about its own middle as
     * posed in {@code pose} (its turn in the torso), by {@code w}: where the turn takes the arm's middle at the pivot's
     * height, back to where it is unturned. The rig adds these to the pivots it puts at the shoulders (turned with the
     * torso).
     */
    public static Vector3f[] pivots(MotionPose pose,float w,float half){
        Vector3f[] out=new Vector3f[2];
        for(int i=0;i<2;i++){
            Vector3f mid=new Vector3f(i==0?-half:half,0,0);
            out[i]=new Vector3f(mid).sub(quat(pose.joint[MotionPose.RIGHT_ARM+i]).transform(new Vector3f(mid))).mul(Math.max(0,Math.min(1,w)));
        }
        return out;
    }
    /**
     * Straightens her legs as she stands (2.105.0, user request "다리는 왜 구부리고 있어 펴"): the rig stands everyone with
     * the knees a little bent (MotionClips.locomotion, some 23°, the hips back half that); she stands straight-legged,
     * by how little she walks ({@code amount}, the rig's own stride measure, so her steps keep their knees). Before the
     * air's and the landing's (CombatClient.pose): a jump still tucks her legs and a landing still takes it in the knees.
     * Her feet come to the ground (the bent legs held them a quarter of a pixel above it).
     */
    public static void straighten(MotionPose pose,float amount){
        float w=1-Ease.smooth(amount,STILL,GOING);if(w<=0)return;
        for(int i=0;i<2;i++){
            pose.joint[MotionPose.RIGHT_LEG+i][0]=MotionPose.lerp(pose.joint[MotionPose.RIGHT_LEG+i][0],0,w);
            pose.knee[i]=MotionPose.lerp(pose.knee[i],0,w);
        }
    }
    /**
     * How much of the stance her arms take now (0 to 1): all of it while she stands (or jumps on the spot), easing out as
     * she gets going and none at walking pace or faster.
     */
    public static float weight(UUID id,float delta){return 1-Ease.smooth(CombatClient.travel(id,delta),STILL,GOING);}

    private static final float RAD=(float)Math.PI/180;
    static Quaternionf quat(float[] e){return new Quaternionf().rotationZYX(e[2]*RAD,e[1]*RAD,e[0]*RAD);}
    /** A turn back to pitch, yaw and roll (degrees) as a model part takes them (Rz·Ry·Rx). */
    static float[] angles(Quaternionf q){
        float pitch=(float)Math.atan2(2*(q.w*q.x+q.y*q.z),1-2*(q.x*q.x+q.y*q.y));
        float yaw=(float)Math.asin(Math.max(-1,Math.min(1,2*(q.w*q.y-q.z*q.x))));
        float roll=(float)Math.atan2(2*(q.w*q.z+q.x*q.y),1-2*(q.y*q.y+q.z*q.z));
        return new float[]{pitch/RAD,yaw/RAD,roll/RAD};
    }
}
