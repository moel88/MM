package dev.sangwoo.celestialfall.combat;
import static dev.sangwoo.celestialfall.combat.MotionPose.*;

/** Distance-driven foot planting, counter-rotation and authored anticipation/contact/recovery poses. */
public final class MotionClips {
    private MotionClips() {}
    public static float smooth(float x) {x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    public static MotionPose locomotion(float phase,float amount,float forward,float side,float sprint,float combat,float time) {
        MotionPose p=new MotionPose();float sin=(float)Math.sin(phase),cos=(float)Math.cos(phase);
        amount=Math.max(0,Math.min(1,amount));
        float breath=(float)Math.sin(time*.085)*.4f;
        p.bob=amount*(-.2f-.35f*Math.abs(sin))+breath*.12f;
        p.shift=-side*amount*.45f;p.lean=forward*sprint*amount*.3f;
        p.rot(BODY,forward*(3+10*sprint)*amount,cos*(4+3*sprint)*amount,-side*5*amount+sin*1.6f*amount);
        float stride=2.7f+1.8f*sprint;
        for(int i=0;i<2;i++) {
            float q=phase+(i==0?0:(float)Math.PI);
            float swing=(float)Math.sin(q),travel=(float)Math.cos(q);
            float lift=(float)Math.pow(Math.max(0,swing),1.6)*(1.5f+3*sprint)*amount;
            float y=11.75f-p.bob-lift;
            float z=-travel*stride*forward*amount;
            float x=-travel*stride*side*amount;
            leg(p,i,x,y,z);
            float arm=travel*(24+24*sprint)*forward*amount;
            p.rot(RIGHT_ARM+i,arm-6*sprint*amount,side*travel*9*amount,(i==0?1:-1)*(3+4*amount));
            p.elbow[i]=-8-(34*sprint+Math.max(0,-travel)*17)*amount;
        }
        if(combat>0) {
            MotionPose ready=ready(time);
            float weight=combat*(1-.72f*amount);
            for(int i=0;i<4;i++)for(int j=0;j<3;j++)p.joint[i][j]=lerp(p.joint[i][j],ready.joint[i][j],weight);
            for(int i=0;i<2;i++)p.elbow[i]=lerp(p.elbow[i],ready.elbow[i],combat*(1-.3f*amount));
            if(amount<.15f) {
                float w=combat*(1-amount/.15f);
                for(int i=4;i<6;i++)for(int j=0;j<3;j++)p.joint[i][j]=lerp(p.joint[i][j],ready.joint[i][j],w);
                for(int i=0;i<2;i++)p.knee[i]=lerp(p.knee[i],ready.knee[i],w);
                p.bob=lerp(p.bob,ready.bob,w);
            }
        }
        return p;
    }
    /** Two-segment leg solve. Both feet stay on the same ground plane during support. */
    private static void leg(MotionPose p,int i,float x,float y,float z) {
        float d=Math.min(11.98f,(float)Math.sqrt(x*x+y*y+z*z));
        float bend=(float)Math.acos(Math.max(0,d/12));
        p.joint[RIGHT_LEG+i][0]=(float)Math.toDegrees(Math.atan2(z,Math.hypot(x,y))-bend);
        p.joint[RIGHT_LEG+i][2]=(float)Math.toDegrees(-Math.atan2(x,y));
        p.knee[i]=(float)Math.toDegrees(2*bend);
    }
    public static MotionPose ready(float time) {
        MotionPose p=new MotionPose();float breath=(float)Math.sin(time*.09f);
        p.rot(BODY,4,-14,0).rot(HEAD,-2,14,0);
        p.rot(RIGHT_ARM,-22+breath,8,13).rot(LEFT_ARM,-37-breath,-8,-12);
        p.elbow[0]=-103;p.elbow[1]=-92;p.bob=.3f+breath*.08f;
        leg(p,0,-.4f,11.1f,1.9f);leg(p,1,.4f,11.5f,-1.6f);return p;
    }
    public static MotionPose attack(int strike,float age) {
        if(strike<1||strike>4)return ready(age);
        MotionPose a=ready(age),wind=a.copy(),contact=a.copy(),follow=a.copy();
        boolean left=strike==1||strike==3;int arm=left?LEFT_ARM:RIGHT_ARM,ei=left?1:0;float sign=left?-1:1;
        wind.rot(BODY,2,-sign*(strike==4?38:22),-sign*4);
        wind.rot(HEAD,0,sign*20,0).rot(arm,strike>=3?-48:-15,sign*24,sign*(strike>=3?48:12));
        wind.elbow[ei]=strike>=3?-115:-108;wind.bob=.6f;
        contact.rot(BODY,strike==4?14:8,sign*(strike>=3?35:21),sign*5);
        contact.rot(HEAD,-4,-sign*19,0);
        if(strike<=2)contact.rot(arm,-91,-sign*9,sign*3);
        else contact.rot(arm,-82,-sign*39,sign*57);
        contact.elbow[ei]=strike<=2?-5:-48;
        contact.rot(left?RIGHT_ARM:LEFT_ARM,-30,-sign*12,-sign*10);contact.elbow[1-ei]=-108;
        contact.lean=-.45f;contact.shift=sign*.5f;contact.bob=.25f;
        contact.joint[RIGHT_LEG+ei][1]=sign*18;contact.joint[RIGHT_LEG+ei][0]-=9;contact.knee[ei]+=10;
        follow=contact.copy();follow.joint[BODY][1]+=sign*10;follow.elbow[ei]-=12;follow.joint[arm][1]-=sign*14;follow.lean=-.6f;
        float hit=CombatState.WINDUP[strike],end=CombatState.DURATION[strike];
        if(age<hit*.48f)return MotionPose.blend(a,wind,smooth(age/(hit*.48f)));
        if(age<hit)return MotionPose.blend(wind,contact,smooth((age-hit*.48f)/(hit*.52f)));
        if(age<hit+1.25f)return MotionPose.blend(contact,follow,smooth((age-hit)/1.25f));
        return MotionPose.blend(follow,a,smooth((age-hit-1.25f)/(end-hit-1.25f)));
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
    public static MotionPose hitReaction(float age,int strike) {
        MotionPose p=ready(age);float side=strike==2?-1:1;
        float impact=(float)Math.exp(-Math.max(0,age)*.45),hold=.35f+.65f*impact;
        p.rot(BODY,-13*hold,side*17*hold,side*7*hold).rot(HEAD,-12*hold,-side*9*hold,side*8*hold);
        p.rot(RIGHT_ARM,-26,10,18+12*impact).rot(LEFT_ARM,-31,-10,-19-10*impact);
        p.elbow[0]=-65;p.elbow[1]=-73;p.lean=.28f*hold;return p;
    }
    public static MotionPose dash(float age,float forward,float side) {
        MotionPose p=ready(age);float envelope=(float)Math.sin(Math.PI*Math.max(0,Math.min(1,age/CombatState.DASH_TICKS)));
        p.rot(BODY,forward*24,side*12,-side*22).rot(HEAD,-forward*13,-side*8,side*10);
        p.rot(RIGHT_ARM,30*forward,-12,17).rot(LEFT_ARM,-32*forward,10,-15);p.elbow[0]=-55;p.elbow[1]=-100;
        p.rot(RIGHT_LEG,-25*forward,side*10,-side*24).rot(LEFT_LEG,32*forward,-side*8,side*20);
        p.knee[0]=35;p.knee[1]=25;p.bob=envelope*.65f;p.lean=-forward*.8f;p.shift=-side*.7f;
        return MotionPose.blend(ready(age),p,smooth(Math.min(age/1.5f,(CombatState.DASH_TICKS-age)/2)));
    }
}
