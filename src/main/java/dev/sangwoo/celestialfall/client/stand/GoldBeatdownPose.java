package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.stand.GoldBeatdown.Beat;
import dev.sangwoo.celestialfall.combat.MotionPose;
import net.minecraft.entity.player.PlayerEntity;
import static dev.sangwoo.celestialfall.client.stand.GoldBeatdown.*;

/**
 * The bodies of Gold Experience's beatdown's cutscene (2.150.0): its user's and, if a player or the training dummy, the
 * one struck's, as smooth functions of the beatdown's clock (BeatdownPose's ways).
 * <p>
 * Its user: as the punch lands they lean in, the right fist thrust out at the one struck, the left clenched at the
 * hip, shaking with the shout all through "muda muda"; at the "WRYYY" they arch right back, the arms flung down and
 * out behind, the head thrown back to the sky; high up they stand looking up at them, the right fist up; as the stand
 * draws back so does their arm, and on the last "muda!" they throw it out; then they watch the one struck fly and stand.
 * <p>
 * The one struck: their arms flung forward by the punch, then flailing back with every fist; tipped over above the
 * stand their arms and legs hang down and jerk; they trail as they fly; lying, then getting up. (The cutscene turns and
 * moves their whole body, GoldBeatdown.victim; this is their limbs.)
 */
public final class GoldBeatdownPose {
    private GoldBeatdownPose(){}
    static final R6.Pose STAND=R6.pose().rArm(-84,10).lArm(-84,-10);
    /** "Muda muda": leaning in, the right fist thrust out at the one struck, the left clenched at the hip. */
    static final R6.Pose MUDA=R6.pose().body(0,9,0).rArm(2,4).lArm(-62,-22).rLeg(-84,8).lLeg(-96,-6).head(0,-6,0);
    /** "WRYYY": arched right back, the arms flung down and out behind, the head thrown back. */
    static final R6.Pose WRYYY=R6.pose().body(0,-26,0).rArm(-58,58).lArm(-58,-58).rLeg(-84,10).lLeg(-84,-10).head(0,-34,0);
    /** Looking up at the one struck high in the air, the right fist up at them. */
    static final R6.Pose UPWARD=R6.pose().body(0,-8,0).rArm(48,10).lArm(-70,-20).head(0,-30,0);
    /** The arm drawn back as the stand draws its fist back. */
    static final R6.Pose DRAWN=R6.pose().body(-22,-4,0).rArm(-20,62).lArm(-30,-12).head(0,-24,0);
    /** The last "muda!": the right arm thrown out up at them. */
    static final R6.Pose THROWN=R6.pose().body(20,8,0).rArm(34,-6).lArm(-72,-26).head(0,-22,0);
    static final R6.Pose WATCH=R6.pose().rArm(-82,14).lArm(-82,-14).head(0,-16,0);
    static final Anim USER=Anim.of(
        Anim.k(CUT,STAND),Anim.k(CUT+.3,MUDA),Anim.k(WRY-.15,MUDA),Anim.k(WRY+.3,WRYYY,Anim.Ease.OUT),Anim.k(WRY_END-.35,WRYYY),
        Anim.k(WRY_END+.25,UPWARD),Anim.k(WIND,UPWARD),Anim.k(WIND+.5,DRAWN),Anim.k(FINAL-.17,DRAWN),Anim.k(FINAL+.04,THROWN,Anim.Ease.OUT),
        Anim.k(FINAL+.55,THROWN),Anim.k(LAND,WATCH),Anim.k(LAND+.6,STAND));
    /** The right elbow's bend at the keys (degrees): straight thrust out, bent as it is drawn back. */
    static final float[][] USER_ELBOW={{CUT,0},{CUT+.3f,-10},{WRY-.15f,-10},{WRY+.3f,-14},{WRY_END-.35f,-14},{WRY_END+.25f,-30},{WIND,-30},{WIND+.5f,-78},{FINAL-.17f,-78},{FINAL+.04f,-4},{FINAL+.55f,-4},{LAND,-8},{LAND+.6f,0}};
    static final float[][] USER_LEFT_ELBOW={{CUT,0},{CUT+.3f,-70},{WRY-.15f,-70},{WRY+.3f,-12},{WRY_END-.35f,-12},{WRY_END+.25f,-50},{FINAL+.55f,-50},{LAND+.6f,0}};

    // the one struck
    static final R6.Pose STRUCK=R6.pose().rArm(-30,40).lArm(-30,-40).head(0,-12,0);
    static final R6.Pose RUSHED=R6.pose().rArm(-18,98).lArm(-18,-98).rLeg(-74,12).lLeg(-68,-10).head(0,-12,0);
    /** Tipped over above the stand: the arms and legs hanging down under them (ahead of their body), the head down. */
    static final R6.Pose HANGING=R6.pose().rArm(-24,70).lArm(-24,-70).rLeg(-58,14).lLeg(-50,-12).head(0,14,0);
    static final R6.Pose LIMP=R6.pose().rArm(-50,80).lArm(-50,-80).rLeg(-64,10).lLeg(-58,-8).head(0,24,0);
    static final R6.Pose FLYING=R6.pose().rArm(38,150).lArm(38,-150).rLeg(-62,20).lLeg(-52,-20).head(0,-20,0);
    static final R6.Pose LYING=R6.pose().rArm(-80,28).lArm(-80,-28).head(0,-10,0);
    static final Anim STRUCK_BODY=Anim.of(
        Anim.k(CUT,STAND),Anim.k(CUT+STOP+.08,STRUCK,Anim.Ease.OUT),Anim.k(CUT+.55,RUSHED),Anim.k(WRY-.1,RUSHED),Anim.k(WRY+.5,HANGING),Anim.k(WIND,HANGING),
        Anim.k(WIND+.4,LIMP),Anim.k(FINAL,LIMP),Anim.k(FINAL+.22,FLYING),Anim.k(LAND,FLYING),Anim.k(LAND+.25,LYING),Anim.k(GETUP,LYING),Anim.k(STOOD,STAND));
    static final float[][] STRUCK_ELBOW={{CUT,0},{CUT+.55f,-24},{WRY-.1f,-24},{WRY+.5f,-38},{WIND+.4f,-20},{FINAL,-20},{FINAL+.22f,-10},{LAND+.25f,0}};
    static final float[][] STRUCK_KNEE={{CUT,0},{CUT+.55f,30},{WRY-.1f,30},{WRY+.5f,46},{WIND+.4f,36},{FINAL,36},{FINAL+.22f,22},{LAND+.25f,0}};

    static float keyed(float[][] keys,float t){return BeatdownPose.keyed(keys,t);}
    /** Its user's body at {@code t} on the beatdown's clock: the keys, shaking with the shout through the rush. */
    public static MotionPose user(float t){
        var frame=USER.frame(t);var p=frame.joints();
        // the torso's lean and turn (the keys' body: the rig turns the torso about the hips, the arms with it)
        p.joint[MotionPose.BODY][0]=frame.pitch();p.joint[MotionPose.BODY][1]=-frame.yaw();
        p.elbow[0]=keyed(USER_ELBOW,t);p.elbow[1]=keyed(USER_LEFT_ELBOW,t);
        // shaking with the shout: the thrust arm and the head, a fist's beat
        float shout=barrage(t)*(1-.6f*smooth((t-WRY)/.3f)*(1-smooth((t-WRY_END)/.3f)));
        if(shout>0){
            double w=t*Math.PI*2/GoldRush.HALF*.5;
            p.joint[MotionPose.RIGHT_ARM][0]+=shout*3.5f*(float)Math.sin(w);
            p.joint[MotionPose.HEAD][0]+=shout*2.2f*(float)Math.sin(w+.7);p.joint[MotionPose.HEAD][1]+=shout*1.6f*(float)Math.sin(w*.5+1.1);
            p.joint[MotionPose.BODY][0]+=shout*1.2f*(float)Math.sin(w+.3);
        }
        return p;
    }
    /** The one struck's limbs at {@code t}: their arms, legs and head jerked by each of the rush's fists as it lands. */
    public static MotionPose struck(Beat b,float t){
        var p=STRUCK_BODY.frame(t).joints();
        float e=keyed(STRUCK_ELBOW,t),k=keyed(STRUCK_KNEE,t);
        p.elbow[0]=p.elbow[1]=e;p.knee[0]=k;p.knee[1]=k*.8f;
        float jr=0,jl=0,lr=0,ll=0,pitch=0,yaw=0,roll=0;
        int n=fists(t);
        for(int i=Math.max(0,n-3);i<n;i++){
            float j=kick(t,fistAt(i),.06f);int s=b.seed+i*131;
            int side=fistSide(i),h=fistHeight(fistStance(b,i));
            jr+=(float)(Ease.hash(s)-.5)*44*j;jl+=(float)(Ease.hash(s+1)-.5)*44*j;
            lr+=(float)(Ease.hash(s+2)-.5)*24*j;ll+=(float)(Ease.hash(s+3)-.5)*24*j;
            float x=(float)Ease.hash(s+4);
            if(h==1){yaw+=side*(16+8*x)*j;pitch-=(7+5*x)*j;roll-=side*(5+4*x)*j;}
            else if(h==0){pitch+=(6+5*x)*j;yaw+=side*5*j;}
            else{pitch+=(11+6*x)*j;yaw+=side*4*j;}
        }
        p.joint[MotionPose.RIGHT_ARM][0]+=jr;p.joint[MotionPose.LEFT_ARM][0]+=jl;
        p.joint[MotionPose.RIGHT_LEG][0]+=lr;p.joint[MotionPose.LEFT_LEG][0]+=ll;
        p.joint[MotionPose.HEAD][0]+=pitch;p.joint[MotionPose.HEAD][1]+=yaw;p.joint[MotionPose.HEAD][2]+=roll;
        return p;
    }
    /** The body of {@code player} this frame if a cutscene has them (its user or the one struck; null if none does). */
    public static MotionPose of(PlayerEntity player,float delta){
        var b=GoldBeatdown.of(player.getUuid());
        if(b!=null&&b.scene){float t=b.age(delta);if(t>=CUT+.02f&&t<b.over()+.1f)return user(t);}
        return struckOf(player,delta);
    }
    /** The body of {@code e} this frame if a cutscene has them as the one struck (null if none does). */
    public static MotionPose struckOf(net.minecraft.entity.Entity e,float delta){
        var s=GoldBeatdown.struck(e);if(s==null)return null;
        float t=s.age(delta);
        if(GoldBeatdown.killed(e,t))return struck(s,Math.min(t,GETUP));
        return t>=CUT&&t<s.over()?struck(s,t):null;
    }
}
