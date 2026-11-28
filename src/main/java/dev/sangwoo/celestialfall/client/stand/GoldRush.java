package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import net.minecraft.util.math.MathHelper;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;

/**
 * Gold Experience's muda rush as it is drawn (2.147.0, user request: "E스킬 무다러시 부터 만들자 효과음은 이거야 그리고
 * 러시 속도는 스타플래티나보다 미세하게 느리게 데미지도 조금 더 약하게"). The rush itself is heard by StandRush (the
 * server's one rush runs Star Platinum's and this one: its clock, its blows, where it faces and how far in front of its
 * user it stands); here is what Gold Experience does through it, the way Star Platinum does through its own
 * (StandRush), a little slower: it comes round to the front of its user, and from its guard drives its fists out left
 * and right in turn, each at the middle, high or low (the generator's rush stances), a fist every HALF seconds, each
 * snapping out and stopping dead at full stretch while the other is torn back; its arms a little bigger through it and
 * bigger still as each stretches out; afterimages of its arms alone, golden and see-through, fly with it a little out
 * of step and to every side. Let go, its fists go back to the guard and it goes back to its place at its user's
 * shoulder. Everything is a function of the rush's clock.
 */
public final class GoldRush {
    /** A fist every HALF seconds: a little slower than Star Platinum's (StandRush.HALF, .08), as its blows are. */
    public static final float HALF=.088f;
    /** Seconds: it comes round to the front; once the rush ends its fists go back to the guard, it stays a moment and goes back (Star Platinum's). */
    static final float RISE=StandRush.RISE,SETTLE=StandRush.SETTLE,LINGER=StandRush.LINGER,RETURN=StandRush.RETURN;
    /**
     * How much higher than at its user's shoulder it floats in front (blocks): lower, so that its fists land on a body's
     * face, chest and belly rather than over its head (the first take in the game had them at a player's crown).
     */
    static final float RAISE=-.15f;
    /** How big its arms are through the rush, and how much bigger still an arm grows as it stretches out to its fist. */
    static final float ARMS=1.15f,STRETCH=.12f;
    /** Seconds its stances take to take over from the pose it had beside its user. */
    static final float TAKE=.1f;
    /** The stances it throws a fist in, by side and by height (the middle twice as often as high or low). */
    static final int[] LEFT={RUSH_L,RUSH_L_HI,RUSH_L_LO},RIGHT={RUSH_R,RUSH_R_HI,RUSH_R_LO};
    /** The afterimages of its flying arms (Star Platinum's ten: how far behind, where beside the body, how turned, how strong). */
    static final float[][] PHANTOMS=StandRush.PHANTOMS;
    /** The afterimages' colour: gold. */
    static final float RED=1f,GREEN=.84f,BLUE=.34f;
    private GoldRush(){}

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** The stance of the rush's {@code k}-th fist (from 0): left and right in turn, each at the middle, high or low as its seed picks. */
    static int fist(int k,int seed){
        int[] side=(k&1)==0?LEFT:RIGHT;double h=Ease.hash(seed*7919+k*104729);
        return side[h<.5?0:h<.75?1:2];
    }
    /**
     * Adds the barrage's stances {@code t} seconds into it into {@code w} ({@code share} of them): from the guard the
     * first fist goes out, then each next fist goes out as the one before comes back, eased to a dead stop at each end.
     */
    public static void barrage(float t,int seed,float[] w,float share){
        if(t<=0){w[GUARD]+=share;return;}
        float u=t/HALF;int k=(int)Math.floor(u);float s=smooth(u-k);
        int from=k==0?GUARD:fist(k-1,seed),to=fist(k,seed);
        w[from]+=share*(1-s);w[to]+=share*s;
    }
    /** How big its arms are {@code t} seconds into rush {@code r} before either stretches out. */
    static float armSize(StandRush.Rush r,float t){return 1+(ARMS-1)*smooth(t/RISE)*(1-smooth((t-r.ends())/(SETTLE+LINGER)));}
    /** How far out arm {@code side} (the stances that throw its fist) is in these stances (0 to 1). */
    static float out(float[] w,int[] side){float s=0;for(int i:side)s+=w[i];return s;}
    /** Whether rush {@code r} shows {@code t} seconds into it (never, if it was let go before it began). */
    static boolean shows(StandRush.Rush r,float t){float end=r.ends();return t>=0&&end>0&&t<=end+LINGER+RETURN;}
    /** How far round to the front of its user it is {@code t} seconds in (0 at their shoulder, 1 in front). */
    static float engage(StandRush.Rush r,float t){return StandPunch.burst(t/RISE)*(1-smooth((t-r.ends()-LINGER)/RETURN));}
    /** How much the barrage is on (0 to 1): in once it has come round, out as it ends. */
    static float on(StandRush.Rush r,float t){return Ease.smooth(t,RISE*.6f,RISE)*(1-Ease.smooth(t,r.ends(),r.ends()+.08f));}

    /** Where the rush puts the body this frame: how far round to the front, the way it faces, how far ahead it stands and how much higher. */
    public static final class Shape {
        float engage,yaw,ahead,raise;
        /**
         * (2.149.0) Where round its user it stands (degrees to the right of {@code yaw}; 0, right in front) and how far it
         * turns in on them (0 facing {@code yaw}, 1 facing its user): the healing of its user stands at their front
         * right and turns on them.
         */
        float side,inward;
        /** (2.150.0) How far it leans as it stands there (degrees, forward: the beatdown's stand leans back under the one it drives up). */
        float lean;
        /**
         * (2.152.0) How far the place it stands round is pinned to a spot on the ground ({@code gx, gy, gz}) rather than
         * its user's feet (0 their feet, 1 the spot): the tree's stand kneels where it touched the ground while its user is
         * flung up.
         */
        float pin;double gx,gy,gz;
        void pin(net.minecraft.util.math.Vec3d at,float k){pin=k;gx=at.x;gy=at.y;gz=at.z;}
        void set(Shape o){engage=o.engage;yaw=o.yaw;ahead=o.ahead;raise=o.raise;side=o.side;inward=o.inward;lean=o.lean;pin=o.pin;gx=o.gx;gy=o.gy;gz=o.gz;}
        void clear(){engage=0;side=0;inward=0;lean=0;pin=0;}
        /** This place gives way to {@code o}'s as far as {@code o} is engaged beside it (two skills never run together; one just after another, the later takes over from where the earlier had got to). */
        void over(Shape o){
            float sum=engage+o.engage,k=sum<1e-4f?1:o.engage/sum;
            yaw=MathHelper.lerpAngleDegrees(k,yaw,o.yaw);ahead+=(o.ahead-ahead)*k;raise+=(o.raise-raise)*k;side+=(o.side-side)*k;inward+=(o.inward-inward)*k;lean+=(o.lean-lean)*k;
            // a spot on the ground goes with the place that has it (a place with none is its user's feet)
            if(o.pin>0&&pin>0){gx+=(o.gx-gx)*k;gy+=(o.gy-gy)*k;gz+=(o.gz-gz)*k;pin+=(o.pin-pin)*k;}
            else if(o.pin>0){gx=o.gx;gy=o.gy;gz=o.gz;pin=o.pin*k;}
            else pin*=1-k;
            engage=Math.max(engage,o.engage);
        }
    }
    /**
     * Rush {@code r} this frame on a look that already holds the stand's pose beside its user: the rush's stances take
     * over from it (and give way to it again as it goes back), its arms grow, and {@code shape} says where it stands.
     * Returns whether the rush shows.
     */
    static boolean pose(StandRush.Rush r,float delta,GoldExperienceLook look,Shape shape,float[] scratch){
        float t=r.age(delta);if(!shows(r,t))return false;
        float end=r.ends();
        java.util.Arrays.fill(scratch,0);
        if(t<=end)barrage(t,r.seed(),scratch,1);
        else{float k=smooth((t-end)/SETTLE);barrage(end,r.seed(),scratch,1-k);scratch[GUARD]+=k;}
        float in=smooth(t/TAKE)*(1-smooth((t-end-LINGER)/RETURN));
        for(int i=0;i<scratch.length;i++)look.weights[i]=look.weights[i]*(1-in)+scratch[i]*in;
        float size=armSize(r,t);
        look.armL=size*(1+STRETCH*out(scratch,LEFT));look.armR=size*(1+STRETCH*out(scratch,RIGHT));
        shape.engage=engage(r,t);
        shape.yaw=r.yaw(delta);
        shape.ahead=r.prevPlace+(r.place-r.prevPlace)*MathHelper.clamp(delta,0,1);
        shape.raise=RAISE;shape.side=0;shape.inward=0;
        return true;
    }
    /**
     * Afterimage {@code i} of its flying arms this frame: its stances and its arms' sizes into {@code into}, and how
     * strongly it shows (0 when it does not). It throws its own fists a little behind the body's.
     */
    static float phantom(StandRush.Rush r,int i,float delta,GoldExperienceLook into){
        if(i>=PHANTOMS.length)return 0;
        float t=r.age(delta),show=phantomAlpha(r,i,t);if(show<=0)return 0;
        var w=into.weights;java.util.Arrays.fill(w,0);
        barrage(t-PHANTOMS[i][0],phantomSeed(r,i),w,1);
        float size=armSize(r,t),left=out(w,LEFT),right=out(w,RIGHT);
        into.armL=size*(1+STRETCH*left);into.armR=size*(1+STRETCH*right);
        into.showL=smooth(left);into.showR=smooth(right);
        return show;
    }
    /** How strongly afterimage {@code i} shows: a little fainter than Star Platinum's (these arms are thicker, and hid its head). */
    static float phantomAlpha(StandRush.Rush r,int i,float t){return smooth((t-RISE)/.08f)*(1-smooth((t-r.ends())/.08f))*PHANTOMS[i][4]*.8f;}
    static int phantomSeed(StandRush.Rush r,int i){return r.seed()+101*(i+1);}
    /** Afterimage {@code i}'s place: beside the body where it stands (to its right and up, blocks) and turned a little. */
    static StandPresence.Body phantomBody(StandPresence.Body body,int i){
        double r=Math.toRadians(body.yaw()),fx=-Math.sin(r),fz=Math.cos(r),right=PHANTOMS[i][1];
        return new StandPresence.Body(body.at().add(-fz*right,PHANTOMS[i][2],fx*right),body.yaw()+PHANTOMS[i][3],body.scale());
    }

    /**
     * Where fist {@code side} (0 left, 1 right) is in stance {@code stance} with its arm drawn {@code size} times its size,
     * out from its shoulder that much further (blocks ahead, to its right and up from its feet, at its size 1), in the
     * skin it is drawn in (2.156.0: each skin's fists are where its own stances put them, GoldSkin).
     */
    static float[] grown(GoldSkin skin,int stance,int side,float size){
        float[] f=skin.fists()[stance][side],s=skin.shoulders()[stance][side];
        return new float[]{s[0]+(f[0]-s[0])*size,s[1]+(f[1]-s[1])*size,s[2]+(f[2]-s[2])*size};
    }
    /** How far ahead of the stand's feet its fists reach at full stretch through the rush (blocks, at its size 1). */
    static float fistReach(GoldSkin skin){return grown(skin,RUSH_L,0,ARMS*(1+STRETCH))[0];}
}
