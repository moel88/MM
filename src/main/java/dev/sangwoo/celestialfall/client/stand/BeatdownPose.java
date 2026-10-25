package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import net.minecraft.entity.player.PlayerEntity;
import static dev.sangwoo.celestialfall.client.stand.StandBeatdown.*;

/**
 * The bodies of a Star Platinum beatdown's cutscene (2.64.0): its user's and, if a player, the one struck's, as smooth
 * functions of the beatdown's clock (Anim's keyed poses, the elbows and knees eased between their own keys).
 * <p>
 * Its user (user request: "야레야레라고 할때 플레이어가 손으로 머리를 잡고 머리를 흔들어"): stands as the blow lands; as
 * "yare yare" begins the right hand goes up onto their forehead and holds it there while the head shakes from side to side,
 * once for each "yare"; at "daze" the head drops a little under the hand; at "Star Platinum!" the hand comes down and
 * the right arm is flung out pointing at the one struck as the stand bursts out; then they stand and watch them up in
 * the air; at "裁くのは俺のスタンドだ" (2.65.0) the right arm goes up again pointing at them, held there through the line,
 * jerked on its "DA!"; and they look on as the one struck flies off. (Every moment is one of the recording's, rec.)
 * <p>
 * The one struck: their arms flung forward by the blow, then hanging, dazed, the head down; grabbed by the collar, their
 * hands up at the fist that has them and their legs dangling; flung up by the uppercut, the arms thrown out wide and
 * back; jerking with every blow of the rush; their arms trailing as they fly; lying, then getting up. (The cutscene
 * turns and moves their whole body, StandBeatdown.victim; this is their limbs.)
 */
public final class BeatdownPose {
    private BeatdownPose(){}
    static final R6.Pose STAND=R6.pose().rArm(-84,10).lArm(-84,-10);
    /** The right hand up on the head, on its forehead (pulling a cap's brim down), the left hanging; the head a little down under it. */
    static final R6.Pose HAND=R6.pose().rArm(72,-18).lArm(-80,-12).head(0,10,0);
    /** "daze": the head dropped further under the hand, the hand going down with it. */
    static final R6.Pose DAZE=R6.pose().rArm(66,-15).lArm(-82,-12).head(0,17,0);
    /** "Star Platinum!": the right arm flung out pointing at the one struck, the chin up. */
    static final R6.Pose POINT=R6.pose().rArm(4,6).lArm(-70,-24).head(0,-7,0);
    /** Watching the one struck up in the air. */
    static final R6.Pose WATCH=R6.pose().rArm(-82,14).lArm(-82,-14).head(0,-17,0);
    /** "裁くのは俺のスタンドだ": the right arm up pointing at the one struck in the air, the chin up, the left hand at the side. */
    static final R6.Pose JUDGE_POINT=R6.pose().rArm(22,8).lArm(-76,-16).head(0,-14,0);
    /** "DA!": the pointing arm jerked forward a little more as the last blow lands. */
    static final R6.Pose JUDGE_DA=R6.pose().rArm(30,10).lArm(-74,-18).head(0,-10,0);
    static final Anim USER=Anim.of(
        Anim.k(CUT+.1,STAND),Anim.k(rec(.77f),STAND),Anim.k(rec(1.08f),HAND),Anim.k(rec(2.01f),HAND),Anim.k(rec(2.25f),DAZE),Anim.k(rec(2.47f),DAZE),
        Anim.k(rec(2.65f),POINT,Anim.Ease.OUT),Anim.k(rec(3.45f),POINT),Anim.k(rec(4.1f),WATCH),
        Anim.k(JUDGE-.2,WATCH),Anim.k(JUDGE+.25,JUDGE_POINT,Anim.Ease.OUT),Anim.k(FINAL-.06,JUDGE_POINT),Anim.k(FINAL+.06,JUDGE_DA,Anim.Ease.OUT),
        Anim.k(FINAL+.5,JUDGE_DA),Anim.k(FINAL+.9,WATCH),Anim.k(LAND+.3,STAND.head(0,-6,0)),Anim.k(LAND+1.1,STAND));
    /** The right elbow's bend (degrees) at the keys: the forearm bent in onto the forehead (the arm and the bend worked out on the rig: the hand 6.5 pixels down the forearm on the forehead's front). */
    static final float[][] USER_ELBOW={{rec(.77f),0},{rec(1.08f),-40},{rec(2.47f),-40},{rec(2.65f),-8},{rec(3.45f),-8},{rec(4.1f),-12},
        {JUDGE-.2f,-12},{JUDGE+.25f,-4},{FINAL+.5f,-4},{FINAL+.9f,-12}};

    // the one struck
    static final R6.Pose STRUCK=R6.pose().rArm(-30,40).lArm(-30,-40).head(0,-12,0);
    static final R6.Pose DAZED=R6.pose().rArm(-76,16).lArm(-76,-16).head(0,18,0);
    static final R6.Pose GRABBED=R6.pose().rArm(-8,-30).lArm(-8,30).rLeg(-82,6).lLeg(-76,-8).head(0,-8,0);
    static final R6.Pose FLUNG=R6.pose().rArm(12,118).lArm(12,-118).rLeg(-66,18).lLeg(-58,-14).head(0,-28,0);
    static final R6.Pose RUSHED=R6.pose().rArm(-18,98).lArm(-18,-98).rLeg(-74,12).lLeg(-68,-10).head(0,-12,0);
    static final R6.Pose FLYING=R6.pose().rArm(38,150).lArm(38,-150).rLeg(-62,20).lLeg(-52,-20).head(0,-20,0);
    static final R6.Pose LYING=R6.pose().rArm(-80,28).lArm(-80,-28).head(0,-10,0);
    static final Anim STRUCK_BODY=Anim.of(
        Anim.k(CUT,STAND),Anim.k(CUT+STOP+.08,STRUCK,Anim.Ease.OUT),Anim.k(CUT+.7,DAZED),Anim.k(GRAB-.07,DAZED),Anim.k(GRAB+.1,GRABBED),
        Anim.k(UPPER,GRABBED),Anim.k(UPPER+.22,FLUNG),Anim.k(RUSH_FROM+.25,RUSHED),Anim.k(FINAL,RUSHED),
        Anim.k(FINAL+.22,FLYING),Anim.k(LAND,FLYING),Anim.k(LAND+.25,LYING),Anim.k(GETUP,LYING),Anim.k(STOOD,STAND));
    /** Their elbows and knees (degrees) at the keys: bent up at the fist that has them, dangling, flung. */
    static final float[][] STRUCK_ELBOW={{GRAB-.07f,0},{GRAB+.1f,-82},{UPPER,-82},{UPPER+.2f,-18},{FINAL,-24},{FINAL+.22f,-10},{LAND+.25f,0}};
    static final float[][] STRUCK_KNEE={{GRAB-.07f,0},{GRAB+.1f,28},{UPPER,28},{UPPER+.2f,42},{RUSH_FROM+.25f,30},{FINAL,30},{FINAL+.22f,22},{LAND+.25f,0}};

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** A value through keys {time, value}, eased between each two. */
    static float keyed(float[][] keys,float t){
        if(t<=keys[0][0])return keys[0][1];
        for(int i=1;i<keys.length;i++)if(t<=keys[i][0])return keys[i-1][1]+(keys[i][1]-keys[i-1][1])*smooth((t-keys[i-1][0])/(keys[i][0]-keys[i-1][0]));
        return keys[keys.length-1][1];
    }
    /** The head shaking from side to side under the hand (degrees), once for each "yare" (1.12 to 1.58 s and 1.62 to 1.98 s into the recording). */
    static float shake(float t){
        float env=smooth((t-rec(1.1f))/.08f)*(1-smooth((t-rec(1.91f))/.1f));
        return env<=0?0:18*env*(float)Math.sin((t-rec(1.12f))*Math.PI*2/.43f);
    }
    /** Its user's body at {@code t} on the beatdown's clock. */
    public static MotionPose user(float t){
        var p=USER.frame(t).joints();
        p.elbow[0]=keyed(USER_ELBOW,t);
        float s=shake(t);
        p.joint[MotionPose.HEAD][1]+=s;p.joint[MotionPose.HEAD][2]+=.25f*s;
        return p;
    }
    /** The one struck's limbs at {@code t}: their arms, legs and head jerked by each of the rush's blows (2.65.0: harder). */
    public static MotionPose struck(Beat b,float t){
        var p=STRUCK_BODY.frame(t).joints();
        float e=keyed(STRUCK_ELBOW,t),k=keyed(STRUCK_KNEE,t);
        p.elbow[0]=p.elbow[1]=e;p.knee[0]=k;p.knee[1]=k*.8f;
        // jerked by the blows: each arm and leg thrown its own way and back, the head snapped back
        float jr=0,jl=0,lr=0,ll=0,head=0;
        int n=blows(t);
        for(int i=Math.max(0,n-3);i<n;i++){
            float j=kick(t,blowAt(i),.06f);int s=b.seed+i*131;
            jr+=(float)(Ease.hash(s)-.5)*44*j;jl+=(float)(Ease.hash(s+1)-.5)*44*j;
            lr+=(float)(Ease.hash(s+2)-.5)*24*j;ll+=(float)(Ease.hash(s+3)-.5)*24*j;head+=(float)(6+10*Ease.hash(s+4))*j;
        }
        p.joint[MotionPose.RIGHT_ARM][0]+=jr;p.joint[MotionPose.LEFT_ARM][0]+=jl;
        p.joint[MotionPose.RIGHT_LEG][0]+=lr;p.joint[MotionPose.LEFT_LEG][0]+=ll;p.joint[MotionPose.HEAD][0]-=head;
        return p;
    }
    /**
     * The body of {@code player} this frame if a beatdown's cutscene has them (its user or the one struck; null if
     * none does): MotionPlayerModel draws it, fading in and out of it as it does between any two sources.
     */
    public static MotionPose of(PlayerEntity player,float delta){
        var b=StandBeatdown.of(player.getUuid());
        if(b!=null){float t=b.age(delta);if(t>=CUT+.1f&&t<b.over()+.1f)return user(t);}
        return struckOf(player,delta);
    }
    /** The body of {@code e} this frame if a beatdown's cutscene has them as the one struck (null if none does): a player's, or the training dummy's, which has the same joints. */
    public static MotionPose struckOf(net.minecraft.entity.Entity e,float delta){
        var s=StandBeatdown.struck(e);if(s==null)return null;
        float t=s.age(delta);
        if(StandBeatdown.killed(e,t))return struck(s,Math.min(t,GETUP));
        return t>=CUT&&t<s.over()?struck(s,t):null;
    }
}
