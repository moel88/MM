package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.title.GachaCinema;

/**
 * The ten-pull's opening (2.41.0, user request): up on a step stool, Bomi sets the hourglass she has carried into the
 * cupboard's last empty place; she turns round, steps down and wipes her brow, as if that was the last of them; she walks
 * back to her chair, sits down and looks up at the cupboard, and then its hourglasses light as before ({@link GachaStage}).
 * Her every part, the hourglass in her hands, the lamp that follows her and the camera are smooth functions of the
 * scene's age, so they move on every frame at any frame rate; the beats meet with no jump between them.
 */
final class GachaTen {
    private GachaTen(){}
    private static final float S=GachaStage.SECOND;

    // ---- when (ticks) ----
    /** The hourglass held out over the shelf, set down on it, slid home and let go; a nod as it sits there. */
    static final float SET=.8f*S,PUSHED=1.25f*S,LET_GO=1.45f*S,NODDED=1.85f*S;
    /** She turns round on the stool toward the camera, stepping down twice as she finishes turning. */
    static final float TURN=1.8f*S,TURNED=2.4f*S,STEP_DOWN=2.25f*S,STEP_ONE=2.65f*S,LANDED=3.05f*S;
    /** The brow wipe. */
    static final float WIPE=3.1f*S,WIPED=4.35f*S;
    /** The walk back to the chair, turning round in front of it, and down onto it. */
    static final float WALK=4.25f*S,ARRIVED=6.15f*S,SIT=6.48f*S,SEATED=7.08f*S;

    // ---- where (metres) ----
    /** Where she stands: the stool's top step, its lower step, the floor before it, and before the chair. */
    static final float TOP_Z=GachaCinema.STOOL_BACK-GachaCinema.STOOL_STEP/2,LOW_Z=TOP_Z-GachaCinema.STOOL_STEP,
        FLOOR_Z=GachaCinema.STOOL_BACK-2*GachaCinema.STOOL_STEP-.19f,CHAIR_Z=.37f,X=GachaCinema.STOOL_X;
    /** Her heading in degrees (0 faces the cupboard; turning right is negative): toward the camera off the stool, toward the chair. */
    static final float FACE_CAMERA=-138,FACE_CHAIR=(float)Math.toDegrees(Math.atan2(-X,CHAIR_Z-FLOOR_Z))-360;
    /** The walk: its length, a stride, and how much of its time she speeds up and slows down. */
    static final float PATH=(float)Math.hypot(X,CHAIR_Z-FLOOR_Z),STRIDE=.62f,SPEED_UP=.2f,SLOW_DOWN=.26f;

    // ---- the hourglass she carries (keys in seconds: how far above the shelf its foot is, how deep in) ----
    private static final float[] FOOT={0,.07f, .35f,.085f, .8f,0},DEPTH={0,3.92f, .35f,3.935f, .8f,3.97f, 1.25f,GachaStage.shelf(9)[2]};
    /** Its middle at {@code age}; it stays in its place on the shelf once she has slid it home. */
    static float[] carried(float age){
        float s=age/S;float[] home=GachaStage.shelf(9);
        float half=.546f*GachaStage.BIG/2;
        return new float[]{home[0],GachaCinema.SHELF_LOW+half+keys(s,FOOT),keys(s,DEPTH)};
    }
    /** A little twist as she works it into its place, settling as it goes home. */
    static float twist(float age){return age>=PUSHED?0:4.5f*(float)Math.sin(age/S*9.5f)*GachaStage.smooth(.3f*S,.6f*S,age)*(1-GachaStage.smooth(1.0f*S,PUSHED,age));}

    // ---- her ----
    /**
     * How far along the walk to the chair she is, 0 to 1: speeding up, steady, slowing down, her speed itself easing in
     * and out ({@link #pace}), so nothing about her stride changes in a step.
     */
    static float walked(float age){
        if(age<=WALK)return 0;if(age>=ARRIVED)return 1;
        float u=(age-WALK)/(ARRIVED-WALK),a=SPEED_UP,b=SLOW_DOWN,top=1/(1-a/2-b/2);
        if(u<a)return top*a*eased(u/a);
        if(u>1-b)return 1-top*b*eased((1-u)/b);
        return top*(a/2+u-a);
    }
    /** The area under the smoothest step from 0 to {@code x} (half of it at 1). */
    private static float eased(float x){float x4=x*x*x*x;return x4*x*x-3*x4*x+2.5f*x4;}
    /** Her walking speed against its steadiest, 0 to 1. */
    static float pace(float age){
        if(age<=WALK||age>=ARRIVED)return 0;
        float u=(age-WALK)/(ARRIVED-WALK);
        return u<SPEED_UP?GachaStage.smooth(0,SPEED_UP,u):u>1-SLOW_DOWN?GachaStage.smooth(1,1-SLOW_DOWN,u):1;
    }
    /** The walk's phase in radians: a foot comes down at every multiple of pi. */
    static float stride(float age){return (float)Math.PI*walked(age)*PATH/STRIDE;}
    /** Her heading at {@code age}, in degrees. */
    static float heading(float age){
        return FACE_CAMERA*GachaStage.smooth(TURN,TURNED,age)
            +(FACE_CHAIR-FACE_CAMERA)*GachaStage.smooth(WALK-.15f*S,WALK+.4f*S,age)
            +(-360-FACE_CHAIR)*GachaStage.smooth(ARRIVED-.3f*S,SIT,age);
    }
    /** Where her feet are (the rig's root) at {@code age}, standing. */
    static float[] feet(float age){
        float one=GachaStage.smooth(STEP_DOWN,STEP_ONE,age),two=GachaStage.smooth(STEP_ONE,LANDED,age),walk=walked(age);
        float x=X*(1-walk);
        float z=TOP_Z+(LOW_Z-TOP_Z)*one+(FLOOR_Z-LOW_Z)*two+(CHAIR_Z-FLOOR_Z)*walk;
        float y=GachaCinema.STOOL_TOP+(GachaCinema.STOOL_LOW-GachaCinema.STOOL_TOP)*one-GachaCinema.STOOL_LOW*two;
        // a little hop off each step, a dip as she lands, and the walk's rise and fall
        y+=.045f*(float)Math.sin(Math.PI*one)+.045f*(float)Math.sin(Math.PI*two)-.03f*bump(LANDED,LANDED+.22f*S,age);
        float ph=stride(age);y-=.02f*pace(age)*(float)Math.pow(Math.sin(ph),2);
        return new float[]{x,y,z};
    }

    /**
     * Fills {@code e} (Bomi's seven turns) and {@code root} (x, y, z) for {@code age} ticks into the scene, standing, up to
     * when she begins to sit; {@code world} receives her posed bones. Her arms reach for the hourglass while she holds it
     * and for her brow while she wipes it (pointed at those places, {@link BomiRig#point}).
     */
    static float[] standing(float age,float[][] e,float[] root){
        float s=age/S,ph=stride(age),pace=pace(age),swing=(float)Math.sin(ph)*pace;
        float[] at=feet(age);System.arraycopy(at,0,root,0,3);
        float one=GachaStage.smooth(STEP_DOWN,STEP_ONE,age),two=GachaStage.smooth(STEP_ONE,LANDED,age);
        float stepOne=(float)Math.sin(Math.PI*one),stepTwo=(float)Math.sin(Math.PI*two);
        e[BomiRig.ROOT]=new float[]{0,heading(age),0};
        // the body, bending at the hips: leaning in to set the hourglass home, upright; a slump of relief after the
        // wipe; leaning into the walk, turning a little against her stride
        float lean=keys(s,LEAN)+4*(stepOne+stepTwo)+3*bump(LANDED,LANDED+.3f*S,age)
            +6*bump(WIPED-.45f*S,WIPED+.1f*S,age)-3*bump(WIPE+.3f*S,WIPE+.8f*S,age)+4*pace;
        e[BomiRig.BODY]=new float[]{lean,-4*swing,1.5f*(float)Math.sin(ph*.5f)*pace};
        // the head: up at the shelf, a nod, down at the steps, back with relief through the wipe, ahead on the walk, round
        // to the chair as she turns to sit
        float nod=bump(LET_GO,NODDED,age),steps=bump(TURN,LANDED+.15f*S,age),wipe=bump(WIPE,WIPED,age),walk=bump(WALK-.1f*S,ARRIVED+.1f*S,age);
        float look=bump(ARRIVED-.3f*S,SIT+.2f*S,age);
        e[BomiRig.HEAD]=new float[]{-11*(1-GachaStage.smooth(LET_GO-.2f*S,LET_GO+.1f*S,age))+15*nod+20*steps-3*wipe+6*walk+12*look,
            -14*bump(TURN-.1f*S,TURNED,age)+28*look,-7*wipe};
        // the legs: together on the step, leading each step down, striding, a little step back with each foot as she turns
        // before the chair
        float backRight=bump(ARRIVED-.3f*S,SIT-.08f*S,age),backLeft=bump(ARRIVED-.08f*S,SIT+.1f*S,age);
        e[BomiRig.RIGHT_LEG]=new float[]{-22*stepOne+8*stepTwo-28*swing+10*backRight,0,0};
        e[BomiRig.LEFT_LEG]=new float[]{8*stepOne-22*stepTwo+28*swing+10*backLeft,0,0};
        // the arms at rest (a little out from her sides), swinging against her stride, out for balance on the steps
        float balance=bump(TURN,LANDED+.25f*S,age);
        float[] right={-14*balance+20*swing,0,-4-14*balance},left={-14*balance-20*swing,0,4+14*balance};
        e[BomiRig.RIGHT_ARM]=right;e[BomiRig.LEFT_ARM]=left;
        float[] world=BomiRig.pose(e,root[0],root[1],root[2],true);
        // holding the hourglass: both hands at its foot, either side; let go and easing down to rest
        float hold=1-GachaStage.smooth(LET_GO,LET_GO+.55f*S,age);
        if(hold>0){
            float[] c=carried(Math.min(age,PUSHED));float drop=.42f,yaw=(float)Math.toRadians(twist(age)),side=.26f;
            float cx=(float)Math.cos(yaw)*side,cz=-(float)Math.sin(yaw)*side;
            float[] r=BomiRig.point(world,BomiRig.RIGHT_ARM,c[0]-cx,c[1]-drop,c[2]-cz-.05f),l=BomiRig.point(world,BomiRig.LEFT_ARM,c[0]+cx,c[1]-drop,c[2]+cz-.05f);
            e[BomiRig.RIGHT_ARM]=blend(right,r,hold);e[BomiRig.LEFT_ARM]=blend(left,l,hold);
        }
        // the wipe: the right forearm drawn across her brow from its right to its left, then down; the left hand on her hip
        float up=GachaStage.smooth(WIPE,WIPE+.55f*S,age)*(1-GachaStage.smooth(WIPED-.5f*S,WIPED,age));
        if(up>0){
            float across=GachaStage.smooth(WIPE+.42f*S,WIPED-.35f*S,age);
            float[] brow=BomiRig.apply(world,BomiRig.HEAD,mix(-.17f,.13f,across),mix(1.86f,1.84f,across),.4f);
            e[BomiRig.RIGHT_ARM]=blend(right,BomiRig.point(world,BomiRig.RIGHT_ARM,brow[0],brow[1],brow[2]),up);
        }
        float hip=GachaStage.smooth(WIPE+.05f*S,WIPE+.35f*S,age)*(1-GachaStage.smooth(WIPED-.15f*S,WIPED+.2f*S,age));
        if(hip>0)e[BomiRig.LEFT_ARM]=blend(e[BomiRig.LEFT_ARM],new float[]{14,0,32},hip);
        return BomiRig.pose(e,root[0],root[1],root[2],true);
    }
    /** How far she leans in from the hips while she sets the hourglass home (keys in seconds, degrees). */
    private static final float[] LEAN={0,5, .35f,4, .8f,9, 1.25f,12, 1.5f,9, 1.85f,1};

    // ---- the lamp and the camera ----
    /** Where the lamp above her hangs over the floor: a little behind her, kept clear of the cupboard's crown. */
    static float[] lamp(float age){
        float[] p=feet(Math.max(0,age-.35f*S));
        float seat=GachaStage.smooth(SIT,SEATED+.4f*S,age);
        return new float[]{mix(p[0],0,seat),mix(Math.min(p[2],GachaCinema.STOOL_BACK-.33f),.02f,seat)};
    }
    /** The camera's eye before it swings round her (keys in seconds: x, y, z), ending where the swing begins. */
    private static final float[] EYE_X={0,-4.6f, 1.6f,-4.45f, 2.6f,-4.2f, 3.3f,-4.05f, 4.3f,-4.0f, 5.3f,-3.9f, 6.3f,-3.45f},
        EYE_Y={0,2.2f, 1.6f,2.1f, 2.6f,1.8f, 3.3f,1.6f, 4.3f,1.55f, 5.3f,1.45f, 6.3f,1.35f},
        EYE_Z={0,.3f, 1.6f,.4f, 2.6f,.7f, 3.3f,.95f, 4.3f,.95f, 5.3f,1.0f, 6.3f,1.2f},
        FOV={0,46, 1.6f,46, 3.3f,38, 4.3f,38, 5.3f,42};
    /**
     * The camera from the scene's start until the swing ({@link GachaStage#ORBIT}): off to her right, back far enough to
     * see her, the stool and the hourglass going home past her shoulder; in closer as she turns, steps down and wipes her
     * brow; panning after her to the chair; then on to her right front as she sits, at rest where the swing takes over
     * ({@code start}, {@code aim}).
     */
    static void camera(GachaCinema.Frame f,float age,float[] start,float[] aim){
        float s=age/S,end=GachaStage.ORBIT/S;
        float[] eye={curve(s,EYE_X,end,start[0]),curve(s,EYE_Y,end,start[1]),curve(s,EYE_Z,end,start[2])};
        // what it looks at: the hourglass going home, then her (a moment behind), then where the swing looks
        float[] c=carried(Math.min(age,PUSHED)),her=feet(Math.max(0,age-.12f*S));
        float h=mix(1.45f,1.15f,GachaStage.smooth(WALK,WALK+.6f*S,age));
        float toHer=GachaStage.smooth(LET_GO-.1f*S,TURNED+.1f*S,age),toSwing=GachaStage.smooth(ARRIVED,GachaStage.ORBIT,age);
        float[] t=new float[3];
        float[] slot={c[0]+.05f,c[1]-.55f,c[2]-.35f},person={her[0],her[1]+h,her[2]};
        for(int k=0;k<3;k++)t[k]=mix(mix(slot[k],person[k],toHer),aim[k],toSwing);
        GachaStage.set(f.eye,eye[0],eye[1],eye[2]);GachaStage.set(f.target,t[0],t[1],t[2]);
        f.fov=curve(s,FOV,end,40);f.focus=mix(GachaStage.dist(f.eye,t),GachaStage.dist(f.eye,new float[]{0,1.15f,0}),toSwing);
    }

    // ---- curves ----
    /** Piecewise smooth (resting at every key) through {@code (time, value)} keys. */
    static float keys(float t,float[] k){
        if(t<=k[0])return k[1];
        for(int i=0;i+3<k.length;i+=2)if(t<=k[i+2])return mix(k[i+1],k[i+3],GachaStage.smooth(k[i],k[i+2],t));
        return k[k.length-1];
    }
    /**
     * A curve through {@code (time, value)} keys that keeps moving through them (Catmull-Rom tangents), then on to
     * {@code last} at {@code end}, resting at both ends: smooth in its value and its speed.
     */
    static float curve(float t,float[] k,float end,float last){
        int n=k.length/2+1;float[] tt=new float[n],vv=new float[n];
        for(int i=0;i<n-1;i++){tt[i]=k[2*i];vv[i]=k[2*i+1];}tt[n-1]=end;vv[n-1]=last;
        if(t<=tt[0])return vv[0];if(t>=tt[n-1])return vv[n-1];
        int i=0;while(tt[i+1]<t)i++;
        float h=tt[i+1]-tt[i],u=(t-tt[i])/h;
        float m0=i==0?0:(vv[i+1]-vv[i-1])/(tt[i+1]-tt[i-1])*h,m1=i+2>=n?0:(vv[i+2]-vv[i])/(tt[i+2]-tt[i])*h;
        float u2=u*u,u3=u2*u;
        return (2*u3-3*u2+1)*vv[i]+(u3-2*u2+u)*m0+(-2*u3+3*u2)*vv[i+1]+(u3-u2)*m1;
    }
    /** Rises from 0 at {@code a} to 1 halfway and back to 0 at {@code b}, smoothly. */
    static float bump(float a,float b,float x){return (float)Math.sin(Math.PI*GachaStage.smooth(a,b,x));}
    static float mix(float a,float b,float t){return a+(b-a)*t;}
    static float[] blend(float[] a,float[] b,float t){return new float[]{mix(a[0],b[0],t),mix(a[1],b[1],t),mix(a[2],b[2],t)};}
}
