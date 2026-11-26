package dev.sangwoo.celestialfall.combat;
import static dev.sangwoo.celestialfall.combat.MotionPose.*;

/** Distance-driven foot planting, counter-rotation and authored anticipation/contact/recovery poses. */
public final class MotionClips {
    private MotionClips() {}
    public static float smooth(float x) {x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    /** A leg's length (px: thigh and shin 6 each), how far each hip is from the middle, and how high the hips stand over the ground. */
    static final float LEG=12,HIP=1.9f;
    /** The walk's and the run's: the part of a stride a foot is on the ground, how far a step reaches (px), how high the swinging foot lifts (px). */
    static final float WALK_STANCE=.62f,RUN_STANCE=.36f,WALK_STRIDE=4,RUN_STRIDE=6.2f,WALK_LIFT=1.8f,RUN_LIFT=4.6f;
    /**
     * The body's own poses (2.141.0, user request: "이제 기본 애니메이션들을 바꾸자 평타 모션 가만히 서있는 모션 걷는 모션 달리는
     * 모션 대쉬 모션 싹 다 갈아엎고 멋있게 만들어줘 특히 서있는 모션이 지금 무릎을 내밀고 있는데 이게 꼴보기가 싫어 정자세로
     * 서있게"): standing, walking, running. All of it is new.
     * <p>
     * Standing ({@code amount} 0) they stand at attention: the legs straight under the hips (no bend at the knee: the
     * old stance held them some 23° bent, the hips back), the back straight, the arms hanging a little off the body, only
     * the chest and the shoulders breathing. {@code combat} is no longer a pose: nobody stands with the fists up after a
     * blow (the stance of the blows themselves is {@link #stance}).
     * <p>
     * Going ({@code amount} to 1, {@code sprint} 0 a walk and 1 a run) each leg is a foot set on the ground ahead and
     * carried back under the hips at the ground's pace, then lifted and swung through ({@link #stride}); the hips are
     * as high as the legs reach (low as the feet are apart, up over the standing leg; in a run down into the landing
     * and up into the flight), the shoulders turn against the legs, the arms swing with the other leg (a walk's long and
     * loose, a run's bent and pumping), the body leans into a run.
     */
    public static MotionPose locomotion(float phase,float amount,float forward,float side,float sprint,float combat,float time) {
        MotionPose p=stand(time);
        amount=Math.max(0,Math.min(1,amount));if(amount<=0)return p;
        sprint=Math.max(0,Math.min(1,sprint))*Math.max(0,forward);
        float go=smooth(amount),back=Math.max(0,-forward),strafe=Math.abs(side);
        // how far a step reaches the way they go: shorter backwards and sideways (the feet do not cross)
        float reach=lerp(WALK_STRIDE,RUN_STRIDE,sprint)*(1-.35f*back)*go;
        float sideReach=1.7f*go,lift=lerp(WALK_LIFT,RUN_LIFT,sprint)*go,stance=lerp(WALK_STANCE,RUN_STANCE,sprint);
        // (a run sets its foot down nearer under the body than it leaves it behind)
        float front=lerp(1,.55f,sprint);
        float c2=(float)Math.cos(2*phase),s1=(float)Math.sin(phase),c1=(float)Math.cos(phase);
        // the hips: a walk's lowest as the feet are furthest apart, a run's as it lands on the foot under it
        p.bob=go*lerp(.3f+.45f*c2,1.25f-.55f*c2,sprint);
        p.shift=go*(-side*.35f+.3f*s1*(1-sprint)*Math.abs(forward));
        p.lean=-go*forward*(.25f+.9f*sprint);
        // the shoulders against the legs, the body into the run, a little roll over the standing leg
        float lean=forward>0?3+15*sprint:-2*back;
        p.rot(BODY,go*lean+p.joint[BODY][0],go*c1*(5+5*sprint)*(1-.5f*strafe),go*(-side*4+s1*(1.4f+.8f*sprint)));
        p.rot(HEAD,go*(1.5f*sprint),-go*c1*1.2f,0);
        for(int i=0;i<2;i++) {
            float q=phase+(i==0?0:(float)Math.PI);
            float[] f=stride(q,stance);
            float ahead=f[0]>0?f[0]*front:f[0];
            float x=(i==0?-HIP:HIP)-ahead*sideReach*side,z=-ahead*reach*forward;
            plant(p,i,x,z,f[1]*lift,0);
            // the arm with the other leg: back as its own leg is forward; a walk's hangs long, a run's is bent and pumps
            float swing=(float)Math.cos(q+.2f),fwd=Math.max(0,-swing);
            float open=(i==0?1:-1);
            float pitch=lerp(2+21*swing,-6+44*swing,sprint)*(forward>=0?forward:-.5f*forward)+lerp(0,6,strafe)*swing;
            p.rot(RIGHT_ARM+i,lerp(p.joint[RIGHT_ARM+i][0],pitch,go),go*open*lerp(-2*fwd,-9*fwd,sprint),open*lerp(3.5f,5+2.5f*sprint,go));
            p.elbow[i]=lerp(p.elbow[i],lerp(-8-12*fwd,-84-14*fwd+8*Math.max(0,swing),sprint),go);
        }
        return p;
    }
    /**
     * Standing at attention (2.141.0): the legs straight, the feet under the hips, the back straight, the arms down a
     * little off the body with the elbows just off straight; the chest rises with the breath and the arms with it, the
     * head barely moves. Nothing of it moves the hips or the legs.
     */
    public static MotionPose stand(float time) {
        MotionPose p=new MotionPose();
        float breath=(float)Math.sin(time*.085f),slow=(float)Math.sin(time*.031f+.7f);
        p.rot(BODY,-.6f-.45f*breath,.5f*slow,0).rot(HEAD,-.5f+.3f*breath,1.2f*slow,0);
        p.rot(RIGHT_ARM,.3f*breath,0,3.5f+.5f*breath).rot(LEFT_ARM,.3f*breath,0,-3.5f-.5f*breath);
        p.elbow[0]=p.elbow[1]=-6;
        plant(p,0,-HIP,0,0,0);plant(p,1,HIP,0,0,0);
        return p;
    }
    /**
     * Where a foot is in its stride at {@code q} (radians; a stride is 2π): {how far ahead of the hips, 1 as it is set
     * down to -1 as it leaves the ground; how high it is lifted, 0 to 1}. For {@code stance} of the stride it is on the
     * ground, carried back evenly; then it lifts (quickly, the knee leading) and swings through to be set down again.
     */
    public static float[] stride(float q,float stance) {
        float u=q/(float)(Math.PI*2);u-=Math.floor(u);
        if(u<stance)return new float[]{1-2*u/stance,0};
        float v=(u-stance)/(1-stance),through=smooth((float)Math.pow(v,1.15));
        return new float[]{-1+2*through,(float)Math.pow(Math.sin(Math.PI*Math.pow(v,.85)),.9)};
    }
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    /**
     * Sets leg {@code i} (0 right, 1 left) of {@code p} with its foot at ({@code x}, {@code z}) on the ground (px from
     * under the middle of the hips as they stand: the right hip is at -HIP, ahead is -z), {@code lift} px off it, the
     * toes turned {@code yaw} degrees: wherever the pose's hips are (its bob, shift and lean, set before this), so a
     * foot stays where it is as the weight moves over it. Out of the leg's reach it points straight at the place.
     */
    public static void plant(MotionPose p,int i,float x,float z,float lift,float yaw) {
        leg(p,i,x-(i==0?-HIP:HIP)-p.shift,LEG-p.bob-lift,z-p.lean,yaw);
    }
    /** Two-segment leg solve: the foot at ({@code x}, {@code y}, {@code z}) from the hip (y down), the toes turned {@code yaw} degrees about the leg's own length. */
    private static void leg(MotionPose p,int i,float x,float y,float z,float yaw) {
        double psi=Math.toRadians(yaw),a=z*Math.tan(psi),wz=z/Math.cos(psi);
        double b=Math.sqrt(Math.max(1e-6,x*x+y*y-a*a)),roll=Math.atan2(y,x)-Math.atan2(b,a);
        double d=Math.min(LEG,Math.hypot(b,wz)),bend=Math.acos(Math.min(1,d/LEG));
        p.joint[RIGHT_LEG+i][0]=(float)Math.toDegrees(Math.atan2(wz,b)-bend);
        p.joint[RIGHT_LEG+i][1]=yaw;
        p.joint[RIGHT_LEG+i][2]=(float)Math.toDegrees(roll);
        p.knee[i]=(float)Math.toDegrees(2*bend);
    }
    /** As it was named before 2.141.0: the pose the others start from, standing ({@link #stand}). */
    public static MotionPose ready(float time) {return stand(time);}
    /**
     * A pose as it is authored for the blows and the dash (2.141.0): the head, the torso and the arms as angles, the
     * hips as where they are (bob down, shift, lean back; px), and each foot as where it stands on the ground (px from
     * under the hips as they stand at rest, ahead is -z), how high it is lifted and how its toes are turned. The legs
     * are solved from it ({@link #plant}) at every sampled moment, so a foot set down stays where it is while the
     * weight moves over it.
     */
    static final class Ctl {
        static final int N=25;
        final float[] v=new float[N];
        Ctl(){}
        Ctl(float[] from){System.arraycopy(from,0,v,0,N);}
        Ctl copy(){return new Ctl(v);}
        Ctl head(float pitch,float yaw,float roll){v[0]=pitch;v[1]=yaw;v[2]=roll;return this;}
        Ctl body(float pitch,float yaw,float roll){v[3]=pitch;v[4]=yaw;v[5]=roll;return this;}
        Ctl right(float pitch,float yaw,float roll,float elbow){v[6]=pitch;v[7]=yaw;v[8]=roll;v[12]=elbow;return this;}
        Ctl left(float pitch,float yaw,float roll,float elbow){v[9]=pitch;v[10]=yaw;v[11]=roll;v[13]=elbow;return this;}
        /** The arm that strikes ({@code side} 1 the right, -1 the left), written as the right arm's (mirrored for the left). */
        Ctl arm(int side,float pitch,float yaw,float roll,float elbow){return side>0?right(pitch,yaw,roll,elbow):left(pitch,-yaw,-roll,elbow);}
        Ctl hips(float bob,float shift,float lean){v[14]=bob;v[15]=shift;v[16]=lean;return this;}
        Ctl rfoot(float x,float z,float lift,float yaw){v[17]=x;v[18]=z;v[19]=lift;v[20]=yaw;return this;}
        Ctl lfoot(float x,float z,float lift,float yaw){v[21]=x;v[22]=z;v[23]=lift;v[24]=yaw;return this;}
        MotionPose pose(){return build(v);}
    }
    static MotionPose build(float[] v) {
        MotionPose p=new MotionPose();
        p.rot(HEAD,v[0],v[1],v[2]).rot(BODY,v[3],v[4],v[5]).rot(RIGHT_ARM,v[6],v[7],v[8]).rot(LEFT_ARM,v[9],v[10],v[11]);
        p.elbow[0]=v[12];p.elbow[1]=v[13];p.bob=v[14];p.shift=v[15];p.lean=v[16];
        plant(p,0,v[17],v[18],v[19],v[20]);plant(p,1,v[21],v[22],v[23],v[24]);
        return p;
    }
    /** Standing at attention ({@link #stand}) as it is authored, still (the breath is the standing pose's own). */
    static Ctl atRest() {
        return new Ctl().body(-.6f,0,0).head(-.5f,0,0).right(0,0,3.5f,-6).left(0,0,-3.5f,-6).rfoot(-HIP,0,0,0).lfoot(HIP,0,0,0);
    }
    /**
     * The stance the blows are thrown from and come back to (2.141.0): the left foot forward and the right back and
     * turned out, the weight low between them, the left shoulder forward, the fists up by the chin. Only the blows stand
     * in it: once they are over the body stands at attention again.
     */
    static Ctl stanceCtl() {
        // (a yaw turns to the right: the left shoulder forward is the torso turned right)
        return new Ctl().head(0,3,0).body(4,17,0).right(-26,-6,13,-106).left(-40,8,-12,-95)
            .hips(.3f,0,0).rfoot(-2.6f,2.0f,0,22).lfoot(2.3f,-2.2f,0,6);
    }
    /** The stance of the blows as a pose. */
    public static MotionPose stance(float time) {
        Ctl c=stanceCtl();float breath=(float)Math.sin(time*.09f);
        c.v[6]+=breath;c.v[9]-=breath;return c.pose();
    }
    /**
     * A bare-handed blow (2.141.0, all four new; their times are the server's, CombatState.WINDUP and DURATION): thrown
     * from the stance and back in it, the feet where they stand and the weight moving over them, the hips and the
     * shoulders turning before the arm goes, the fist passing through the contact at speed, then a follow-through and
     * the recovery.
     * <ol>
     * <li>the left jab: a short step, the left shoulder thrown in, the fist straight out and snapped back;</li>
     * <li>the right cross: coiled over the back foot, then the back heel turning out as the hips and the shoulders
     * come all the way round and the weight goes onto the front foot;</li>
     * <li>the left hook: a dip, the elbow up level with the shoulder, the whole body turning it through as it rises;</li>
     * <li>the right finisher: drawn far back over the back leg, the left hand reaching out to measure, then a lunging
     * step and the fist driven straight through, the body over the front knee and the back leg stretched out.</li>
     * </ol>
     */
    public static MotionPose attack(int strike,float age) {
        if(strike<1||strike>4)return stance(age);
        float hit=CombatState.WINDUP[strike],end=CombatState.DURATION[strike];
        // Wind-up and follow-through settle; the fist passes through contact at speed (no dead stop on the hit),
        // then a short hit-stop (longer on the fourth punch) and a catch-up.
        age=hitStop(age,hit,strike==4?1.2f:.7f,1.6f);
        Ctl a=stanceCtl(),wind=a.copy(),contact=a.copy(),follow,back=a.copy();
        float[] t;
        switch(strike){
            case 1->{
                // the jab: the left shoulder drawn back a little, then thrown in behind the fist
                wind.body(2,9,-1).hips(.45f,0,.45f).arm(-1,-36,-10,16,-118).right(-28,-8,14,-110);
                contact.body(7,36,-2).head(1,6,0).hips(.3f,.15f,-1.0f).arm(-1,-91,27,3,-4).right(-34,-10,13,-114).lfoot(2.3f,-3.1f,0,6);
                follow=contact.copy().body(8,40,-2).hips(.3f,.15f,-1.15f).arm(-1,-90,31,3,-1);
                back.arm(-1,-46,-8,12,-102);
                t=new float[]{0,hit*.45f,hit,hit+1.2f,hit+4,end};
            }
            case 2->{
                // the cross: coiled over the back foot, then everything turned through onto the front one
                wind.body(1,31,2).head(0,6,0).hips(.6f,-.25f,.8f).arm(1,-18,-14,18,-122).left(-42,6,-10,-100);
                contact.body(10,-38,3).head(1,-5,0).hips(.45f,.3f,-1.4f).arm(1,-91,29,3,-4).left(-44,10,-10,-114)
                    .rfoot(-2.6f,1.5f,.35f,-8).lfoot(2.3f,-2.4f,0,10);
                follow=contact.copy().body(12,-45,4).hips(.5f,.35f,-1.65f).arm(1,-89,34,4,-1).rfoot(-2.6f,1.3f,.45f,-12);
                back.arm(1,-32,-6,13,-112);
                t=new float[]{0,hit*.5f,hit,hit+1.3f,hit+4.5f,end};
            }
            case 3->{
                // the hook: a dip with the elbow out, then the upper arm level and the forearm across as the body turns it through
                wind.body(6,-18,-4).head(0,-3,0).hips(1.2f,.35f,.3f).arm(-1,-42,0,56,-98).right(-32,-8,14,-112).lfoot(2.3f,-2.2f,0,-6);
                contact.body(8,44,-5).head(0,8,0).hips(.35f,-.2f,-.85f).arm(-1,-86,4,80,-86).right(-36,-10,12,-114)
                    .lfoot(2.4f,-2.4f,.25f,30).rfoot(-2.6f,2.0f,0,26);
                follow=contact.copy().body(9,58,-6).hips(.4f,-.3f,-.9f).arm(-1,-84,8,84,-96).lfoot(2.4f,-2.4f,.2f,38);
                back.arm(-1,-44,-6,12,-100);
                t=new float[]{0,hit*.5f,hit,hit+1.4f,hit+5,end};
            }
            default->{
                // the finisher: far back over the back leg, the left hand out to measure, then the lunge and the fist straight through
                wind.body(-3,42,3).head(1,8,0).hips(.8f,-.3f,1.6f).arm(1,-56,-24,48,-124).left(-74,20,-6,-22)
                    .lfoot(2.3f,-3.0f,1.3f,6).rfoot(-2.7f,2.3f,0,30);
                contact.body(17,-48,4).head(3,-6,0).hips(1.3f,.3f,-2.7f).arm(1,-93,38,3,-4).left(26,6,-18,-92)
                    .lfoot(2.4f,-4.7f,0,6).rfoot(-2.6f,3.1f,.3f,-6);
                follow=contact.copy().body(21,-57,5).hips(1.45f,.35f,-3.1f).arm(1,-88,44,5,-1).left(36,4,-20,-80).rfoot(-2.6f,3.3f,.5f,-12);
                back.arm(1,-30,-6,13,-110);
                t=new float[]{0,hit*.5f,hit,hit+1.6f,hit+7,end};
            }
        }
        float[][] keys={a.v,wind.v,contact.v,follow.v,back.v,a.v};
        // (the key before the last is the arm already back by the chin while the body is still coming up: the hand
        // returns first, the weight after)
        float[] late=keys[4];float[] hold=follow.v;
        for(int i=14;i<Ctl.N;i++)late[i]=lerp(hold[i],a.v[i],.55f);
        for(int i=3;i<6;i++)late[i]=lerp(hold[i],a.v[i],.6f);
        return build(curve(age,t,keys,2));
    }
    /** Clip time for a hit-stop at {@code at}: nearly frozen for {@code hold} ticks, then catching up over {@code recover}. */
    static float hitStop(float age,float at,float hold,float recover) {
        float a=age-at;if(a<0||a>=hold+recover)return age;
        float lag=a<hold?a*.88f:hold*.88f*(1-smooth((a-hold)/recover));
        return age-lag;
    }
    /**
     * Cubic curve through key poses: every channel settles at the keys, except at key {@code flow} where each channel
     * keeps a monotone speed through (zero where it turns back), so the motion carries through that key.
     */
    static MotionPose sequence(float age,float[] t,MotionPose[] k,int flow) {
        float[][] keys=new float[k.length][];for(int i=0;i<k.length;i++)keys[i]=channels(k[i]);
        return pose(curve(age,t,keys,flow));
    }
    /** The same curve over any channels ({@link Ctl}'s for the blows and the dash). */
    static float[] curve(float age,float[] t,float[][] k,int flow) {
        if(age<=t[0])return k[0].clone();
        if(age>=t[t.length-1])return k[k.length-1].clone();
        int i=1;while(age>=t[i])i++;
        float h=t[i]-t[i-1],u=(age-t[i-1])/h;
        float[] p0=k[i-1],p1=k[i],out=new float[p0.length];
        float[] m0=i-1==flow?speeds(t,k,flow):new float[p0.length],m1=i==flow?speeds(t,k,flow):new float[p0.length];
        for(int c=0;c<out.length;c++){
            float u2=u*u,u3=u2*u;
            out[c]=(2*u3-3*u2+1)*p0[c]+(u3-2*u2+u)*m0[c]*h+(-2*u3+3*u2)*p1[c]+(u3-u2)*m1[c]*h;
        }
        return out;
    }
    /** Monotone (Fritsch-Carlson) speed of every channel through key {@code k}. */
    private static float[] speeds(float[] t,float[][] keys,int k) {
        float[] a=keys[k-1],b=keys[k],c=keys[k+1],m=new float[b.length];
        float h1=t[k]-t[k-1],h2=t[k+1]-t[k];
        for(int i=0;i<m.length;i++){
            float s1=(b[i]-a[i])/h1,s2=(c[i]-b[i])/h2;
            if(s1*s2<=0)continue;
            float w1=2*h2+h1,w2=h2+2*h1;m[i]=(w1+w2)/(w1/s1+w2/s2);
        }
        return m;
    }
    private static float[] channels(MotionPose p) {
        float[] c=new float[18+7];int n=0;
        for(float[] j:p.joint)for(float v:j)c[n++]=v;
        c[n++]=p.elbow[0];c[n++]=p.elbow[1];c[n++]=p.knee[0];c[n++]=p.knee[1];c[n++]=p.bob;c[n++]=p.shift;c[n++]=p.lean;
        return c;
    }
    private static MotionPose pose(float[] c) {
        MotionPose p=new MotionPose();int n=0;
        for(int j=0;j<6;j++)for(int a=0;a<3;a++)p.joint[j][a]=c[n++];
        p.elbow[0]=c[n++];p.elbow[1]=c[n++];p.knee[0]=c[n++];p.knee[1]=c[n++];p.bob=c[n++];p.shift=c[n++];p.lean=c[n++];
        return p;
    }
    /** In the air: the knees draw up while rising ({@code rise} 1 = a jump's take-off speed), the legs reach down and the arms lift while falling. */
    public static MotionPose air(float rise,float time) {
        MotionPose p=ready(time);
        float up=Math.max(0,Math.min(1,rise)),down=Math.max(0,Math.min(1,-rise)),tuck=.35f+.65f*up;
        p.rot(BODY,8*tuck-3*down,-6,0).rot(HEAD,-4*down,8,0);
        p.rot(RIGHT_ARM,-28-22*down,8,18+16*down).rot(LEFT_ARM,-34-20*down,-8,-16-16*down);
        p.elbow[0]=-70+25*down;p.elbow[1]=-64+25*down;
        p.rot(RIGHT_LEG,-30*tuck-6*down,0,2).rot(LEFT_LEG,-14*tuck+8*down,0,-2);
        p.knee[0]=60*tuck+10*down;p.knee[1]=40*tuck+6*down;
        p.bob=-.6f*tuck;
        return p;
    }
    /** Depth of a landing dip {@code since} ticks after touching down: down in 1.6 ticks, back up over 6. */
    public static float dip(float since) {
        if(since<0||since>=7.6f)return 0;
        return since<1.6f?smooth(since/1.6f):1-smooth((since-1.6f)/6);
    }
    /** Absorbs a landing ({@code k} 0..1): the hips drop, the knees bend, the torso and arms follow. */
    public static void land(MotionPose p,float k) {
        if(k<=0)return;
        p.bob+=1.8f*k;
        for(int i=0;i<2;i++){p.joint[RIGHT_LEG+i][0]-=32*k;p.knee[i]+=62*k;}
        p.joint[BODY][0]+=9*k;p.joint[HEAD][0]-=5*k;
        p.joint[RIGHT_ARM][0]-=10*k;p.joint[LEFT_ARM][0]-=10*k;p.joint[RIGHT_ARM][2]+=8*k;p.joint[LEFT_ARM][2]-=8*k;
    }
    /** Strength of a flinch {@code since} ticks after a blow: snaps in within a tick, dies away over about half a second. */
    public static float recoil(float since) {
        if(since<0)return 0;
        return since<1?smooth(since):(float)Math.exp(-(since-1)/2.5f);
    }
    /** The torso snaps back from a blow, the head and arms follow; {@code side} turns it away from a blow from the side. */
    public static void flinch(MotionPose p,float k,float side) {
        if(k<=0)return;
        p.joint[BODY][0]-=11*k;p.joint[BODY][1]+=side*6*k;p.joint[BODY][2]+=side*7*k;
        p.joint[HEAD][0]-=9*k;p.joint[HEAD][2]+=side*6*k;
        p.joint[RIGHT_ARM][2]+=10*k;p.joint[LEFT_ARM][2]-=10*k;
        p.lean+=.35f*k;
    }
    public static MotionPose guard(float time,float pressure) {
        MotionPose p=ready(time);p.rot(BODY,8,-8,0).rot(HEAD,5,8,0);
        p.rot(RIGHT_ARM,-44,-18,20).rot(LEFT_ARM,-49,18,-20);p.elbow[0]=-112;p.elbow[1]=-106;
        p.joint[BODY][2]=(float)Math.sin(time*1.7)*pressure/100*1.3f;return p;
    }
    public static MotionPose broken(float age) {
        MotionPose p=ready(age);float recoil=(float)Math.sin(Math.PI*Math.min(1,age/20));
        p.rot(BODY,-15*recoil,9*recoil,5*recoil).rot(HEAD,-9*recoil,-10*recoil,3*recoil);
        p.rot(RIGHT_ARM,-28,15,35*recoil+10).rot(LEFT_ARM,-22,-15,-35*recoil-10);
        p.elbow[0]=-35;p.elbow[1]=-50;p.lean=.5f*recoil;return p;
    }
    /**
     * Incapacitated (2.60.0, struck by Star Platinum's Star Finger): reeling back from the blow for a moment, then
     * dazed, the head hanging, the arms loose at the sides and the body swaying, coming back to itself over its last
     * few ticks ({@code left}: ticks still to go).
     */
    public static MotionPose dazed(float age,float left) {
        MotionPose p=new MotionPose();
        float reel=(float)Math.sin(Math.PI*Math.max(0,Math.min(1,age/12))),daze=smooth(age/10)*smooth(left/8);
        float sway=(float)Math.sin(age*.3f),nod=(float)Math.sin(age*.21f+1.3f);
        p.rot(BODY,-13*reel+9*daze,7*reel+5*sway*daze,4*reel+3*sway*daze).rot(HEAD,-10*reel+(18+5*nod)*daze,-6*sway*daze,5*sway*daze);
        p.rot(RIGHT_ARM,-20*reel-4*daze,8*reel,22*reel+7*daze).rot(LEFT_ARM,-16*reel-4*daze,-8*reel,-22*reel-7*daze);
        p.elbow[0]=p.elbow[1]=-30*reel-14*daze;p.lean=.35f*reel-.12f*daze;p.bob=.25f*daze;
        leg(p,0,-.4f,11.4f,1.2f,0);leg(p,1,.4f,11.6f,-1f,0);
        return p;
    }
    public static MotionPose hitReaction(float age,int strike) {
        MotionPose p=ready(age);float side=strike==2?-1:1;
        float impact=(float)Math.exp(-Math.max(0,age)*.45),hold=.35f+.65f*impact;
        p.rot(BODY,-13*hold,side*17*hold,side*7*hold).rot(HEAD,-12*hold,-side*9*hold,side*8*hold);
        p.rot(RIGHT_ARM,-26,10,18+12*impact).rot(LEFT_ARM,-31,-10,-19-10*impact);
        p.elbow[0]=-65;p.elbow[1]=-73;p.lean=.28f*hold;return p;
    }
    /**
     * The dash (2.141.0, new; CombatState.DASH_TICKS long): a push off, a low stretched flight and a braking step, by
     * the way it goes ({@code forward} 1 ahead and -1 back, {@code side} to the side; between them the poses are
     * mixed). Ahead: the body thrown low and forward, one knee driven up and the other leg stretched out behind, the
     * arms swept back, then the front foot set down ahead to brake. Back: a back step, the body kept hunched over its
     * guard, the back foot reaching behind. To the side: the body rolled over the leading leg, which reaches out, the
     * other stretched behind it.
     */
    public static MotionPose dash(float age,float forward,float side) {
        float n=(float)Math.max(1e-3,Math.hypot(forward,side));forward/=Math.max(1,n);side/=Math.max(1,n);
        float wf=Math.max(0,forward),wb=Math.max(0,-forward),ws=Math.abs(side),sum=Math.max(1e-3f,wf+wb+ws);
        Ctl rest=atRest();
        float[] out=new float[Ctl.N];
        float[] t={0,1.3f,3.6f,5.7f,CombatState.DASH_TICKS};
        for(int way=0;way<3;way++){
            float w=(way==0?wf:way==1?wb:ws)/sum;if(w<=0)continue;
            Ctl launch=rest.copy(),flight=rest.copy(),brake=rest.copy();
            if(way==0){
                launch.body(26,6,0).hips(2.1f,0,-1.2f).right(34,0,10,-28).left(-40,-8,-8,-96).rfoot(-HIP,4.2f,.2f,0).lfoot(HIP,-2.2f,3.4f,0);
                flight.body(38,-4,0).head(4,0,0).hips(1.5f,0,-2.3f).right(58,4,15,-14).left(50,-4,-15,-20).rfoot(-HIP,6.6f,2.6f,0).lfoot(HIP,-3.0f,4.2f,0);
                brake.body(9,-6,0).hips(1.7f,0,.9f).right(-18,0,16,-42).left(-26,0,-16,-48).rfoot(-2.3f,2.2f,0,14).lfoot(2.2f,-4.3f,0,-4);
            }else if(way==1){
                launch.body(12,0,0).hips(1.7f,0,1.1f).right(-30,6,12,-70).left(-36,-6,-12,-74).rfoot(-HIP,2.4f,1.6f,6).lfoot(HIP,-3.2f,.2f,0);
                flight.body(17,0,0).head(2,0,0).hips(1.25f,0,2.1f).right(-42,8,14,-86).left(-48,-8,-14,-90).rfoot(-2.2f,5.2f,1.7f,10).lfoot(2.0f,-4.6f,2.5f,0);
                brake.body(13,0,0).hips(1.6f,0,.7f).right(-34,6,12,-78).left(-40,-6,-12,-82).rfoot(-2.3f,4.0f,0,16).lfoot(2.0f,-1.4f,0,-2);
            }else{
                // (the way it goes is the model's -x for side 1: the leading leg is the one on that side)
                float d=side>0?-1:1;boolean rightLeads=d<0;
                float lead=d*4.6f,trail=-d*3.0f;
                launch.body(6,-d*8,d*14).hips(1.8f,d*.8f,0).right(-10,0,rightLeads?26:10,-40).left(-10,0,rightLeads?-10:-26,-40);
                flight.body(8,-d*12,d*22).head(0,0,d*6).hips(1.5f,d*1.7f,0).right(-14,0,rightLeads?48:6,-30).left(-14,0,rightLeads?-6:-48,-30);
                brake.body(5,-d*4,d*6).hips(1.6f,d*.4f,0).right(-12,0,14,-36).left(-12,0,-14,-36);
                if(rightLeads){
                    launch.rfoot(-HIP+lead*.5f,0,2.4f,0).lfoot(HIP+trail*.3f,0,.2f,0);
                    flight.rfoot(-HIP+lead,0,2.2f,0).lfoot(HIP+trail,.6f,1.2f,0);
                    brake.rfoot(-HIP+lead*.75f,0,0,0).lfoot(HIP+trail*.2f,0,0,0);
                }else{
                    launch.lfoot(HIP+lead*.5f,0,2.4f,0).rfoot(-HIP+trail*.3f,0,.2f,0);
                    flight.lfoot(HIP+lead,0,2.2f,0).rfoot(-HIP+trail,.6f,1.2f,0);
                    brake.lfoot(HIP+lead*.75f,0,0,0).rfoot(-HIP+trail*.2f,0,0,0);
                }
            }
            float[] c=curve(age,t,new float[][]{rest.v,launch.v,flight.v,brake.v,rest.v},-1);
            for(int i=0;i<out.length;i++)out[i]+=w*c[i];
        }
        return build(out);
    }
}
