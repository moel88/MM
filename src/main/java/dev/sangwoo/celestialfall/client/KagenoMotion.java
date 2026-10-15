package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.combat.MotionPose;
import static dev.sangwoo.celestialfall.combat.MotionPose.*;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/** Authored poses from the supplied 30 fps footage. No repeating vanilla swing animation. */
public final class KagenoMotion {
    private KagenoMotion(){}
    public static float unit(float a,float b,float c){return Math.max(0,Math.min(1,(a-b)/(c-b)));}
    public static float smooth(float a,float b,float c){float t=unit(a,b,c);return t*t*(3-2*t);}
    private static MotionPose pose(float body,float twist,float arm,float armYaw,float armRoll,float off,float leg,float bend){
        MotionPose p=new MotionPose();p.rot(BODY,body,twist,0).rot(RIGHT_ARM,arm,armYaw,armRoll).rot(LEFT_ARM,off,0,-20).rot(RIGHT_LEG,leg,0,6).rot(LEFT_LEG,-leg,0,-6);p.elbow[0]=-bend;p.elbow[1]=-15;p.knee[0]=Math.max(0,leg)*.8f;p.knee[1]=Math.max(0,-leg)*.8f;return p;
    }
    public static MotionPose idle(){return pose(0,0,28,0,12,0,0,8);}
    private record Key(float at,MotionPose p){}
    private static Key k(float at,MotionPose p){return new Key(at,p);}
    private static MotionPose track(float a,Key... keys){
        for(int i=1;i<keys.length;i++)if(a<keys[i].at)return MotionPose.blend(keys[i-1].p,keys[i].p,smooth(a,keys[i-1].at,keys[i].at));
        return keys[keys.length-1].p.copy();
    }
    private static final MotionPose REST=idle(),WIND=pose(-8,-38,-135,-25,42,-35,-12,30),CUT=pose(18,55,-18,65,-66,-45,18,18),BACK=pose(8,52,-42,65,-74,-30,12,28),REVERSE=pose(16,-48,-105,-65,58,-45,-14,15),UP=pose(8,-10,-165,0,-8,-70,12,22),LOW=pose(42,16,-25,5,8,-35,-20,8);
    private static final MotionPose THRUST_BACK=pose(10,-16,-12,-8,-5,-12,-7,92),THRUST_OUT=pose(18,8,-96,-3,2,-22,-12,12);
    static {THRUST_OUT.lean=-1.4f;THRUST_OUT.shift=-.3f;THRUST_OUT.bob=.2f;THRUST_BACK.elbow[1]=-12;THRUST_OUT.elbow[1]=-12;}
    public static float thrust(float a){float beat=(a-8)%6;return a<8||a>=50?0:beat<1.2f?smooth(beat,0,1.2f):1-smooth(beat,2.2f,5.6f);}
    public static float wrist(float a){return -90*smooth(a,0,7)*(1-smooth(a,48,52));}
    private static final Key[][] M1_KEYS={
        {k(0,REST),k(2,pose(-3,-32,-125,-20,35,-8,-7,7)),k(5,pose(9,42,-20,48,-58,-20,12,5)),k(8,pose(6,35,-5,55,-65,-12,10,5)),k(14,REST)},
        {k(0,REST),k(2,pose(5,38,-22,52,-62,-12,9,6)),k(5,pose(8,-38,-112,-50,45,-18,-10,6)),k(8,pose(4,-32,-100,-58,50,-12,-8,5)),k(14,REST)},
        {k(0,REST),k(2,pose(8,-30,-85,-65,65,-20,12,5)),k(5,pose(10,0,-88,0,68,-28,-12,5)),k(9,pose(5,28,-25,42,-48,-15,9,5)),k(14,REST)},
        {k(0,REST),k(2,pose(-5,0,-172,0,-6,-25,-8,3)),k(5,pose(25,8,-14,4,5,-18,-15,6)),k(9,pose(18,8,10,4,5,-12,-10,6)),k(14,REST)}
    };
    private static MotionPose fireCharge(){
        MotionPose p=pose(-15,-12,20,0,16,-78,-12,10);
        p.elbow[1]=-65;p.rot(HEAD,10,8,0);p.bob=.7f;return p;
    }
    private static MotionPose firePlant(){
        MotionPose p=pose(34,-10,-12,0,6,-35,-36,7);
        p.rot(LEFT_LEG,18,0,-10);p.knee[0]=62;p.knee[1]=42;p.bob=3.7f;p.lean=-1.2f;return p;
    }
    private static MotionPose rushGlide(){
        MotionPose p=pose(29,-14,-80,-12,12,-62,-72,12);
        p.rot(LEFT_LEG,22,0,-10);p.knee[0]=88;p.knee[1]=72;p.bob=4.1f;p.elbow[1]=-60;
        p.rot(HEAD,-16,14,0);return p;
    }
    private static final Key[] T_KEYS={k(0,fireCharge()),k(3,fireCharge()),k(T_STRIKE,firePlant()),k(10,firePlant()),k(18,fireCharge()),k(28,REST)};
    /** Highest uses its own asymmetric poses; shared slash poses cannot reproduce the silhouette. */
    private static MotionPose hAirEntry(){
        MotionPose p=pose(-2,4,4,-8,-12,8,0,3);
        p.rot(LEFT_ARM,8,12,14).rot(RIGHT_LEG,-4,0,3).rot(LEFT_LEG,-58,6,-8);
        p.elbow[1]=-3;p.knee[0]=0;p.knee[1]=8;p.rot(HEAD,4,-4,0);return p;
    }
    private static MotionPose hAir(){
        MotionPose p=pose(8,-8,45,-12,-28,55,0,60);
        p.rot(LEFT_ARM,55,18,35).rot(RIGHT_LEG,-12,0,3).rot(LEFT_LEG,-72,5,-9);
        p.elbow[1]=-60;p.knee[0]=0;p.knee[1]=15;p.rot(HEAD,10,-12,-5);return p;
    }
    private static MotionPose hLand(){
        // Left knee and left palm support the landing. The raised right knee
        // leaves the right arm free to sweep the sword back horizontally.
        MotionPose p=pose(60,-8,0,0,72,-60,0,5);
        p.rot(LEFT_ARM,-60,0,-4).rot(RIGHT_LEG,-98,0,8).rot(LEFT_LEG,10,0,-7);
        p.knee[0]=98;p.knee[1]=80;p.elbow[1]=0;p.bob=6.5f;p.lean=-.4f;
        p.rot(HEAD,12,-8,0);return p;
    }
    private static MotionPose hKneel(){
        MotionPose p=pose(28,-8,0,0,75,-28,0,4);
        p.rot(LEFT_ARM,-75,0,-5).rot(RIGHT_LEG,-75,0,8).rot(LEFT_LEG,10,0,-8);
        p.knee[0]=95;p.knee[1]=75;p.elbow[1]=0;p.bob=5;
        p.rot(HEAD,6,-10,0);return p;
    }
    private static MotionPose hReady(){
        MotionPose p=pose(3,-8,42,5,12,-5,0,2);
        p.rot(RIGHT_LEG,-4,0,4).rot(LEFT_LEG,4,0,-4);p.elbow[1]=-3;
        p.rot(HEAD,-2,8,0);return p;
    }
    private static MotionPose hRaised(){
        // The wrist crosses above the forehead; a bent arm and diagonal blade
        // preserve the reference silhouette from the locked side camera.
        MotionPose p=pose(-3,0,-158,-8,12,3,0,5);
        p.elbow[1]=-4;p.rot(RIGHT_LEG,0,0,3).rot(LEFT_LEG,0,0,-3);
        p.rot(HEAD,1,-8,0);return p;
    }
    private static final Key[] H_KEYS={
        k(0,REST),k(3,hLand()),k(8,pose(-10,0,-65,-5,-10,-35,-16,8)),
        k(KagenoCameraPath.AIR_CUT,hAirEntry()),k(19,hAir()),k(30,hAir()),k(35,pose(0,0,-178,0,0,-15,-4,0)),
        k(54,pose(18,0,-80,0,-8,-38,-16,8)),k(56.5f,hLand()),k(61,hLand()),
        k(64,hKneel()),k(66,hKneel()),k(70,hReady()),
        k(72,pose(-3,0,-95,-18,32,0,0,58)),k(76,hRaised()),k(85,hRaised()),
        k(89,hReady()),k(93,REST),k(116,REST)
    };
    /** The camera stays still as the caster lowers the blade and faces it. */
    public static float hTurn(float age){return -25*smooth(age,16,19)*(1-smooth(age,30,32))-54*smooth(age,85,91)*(1-smooth(age,106,116));}
    public static float hBank(float age){return 18*smooth(age,16,19)*(1-smooth(age,30,32));}
    /** World-space landing recoil, pause, then a forward glide into the sword insert. */
    public static double hTravel(float age){
        if(age<56)return 0;
        if(age<63)return -2.35*smooth(age,56,63);
        if(age<66)return -2.35;
        if(age<72)return -2.35+2.70*smooth(age,66,72);
        return .35*(1-smooth(age,74,78));
    }
    /** Align the blade with the forearm so the raised fist actually points the sword skyward. */
    public static float hWrist(float a){float air=smooth(a,8,16)*(1-smooth(a,29,35));return (-90-20*air)*smooth(a,0,6)*(1-smooth(a,104,116));}
    public static double lift(float a){
        if(a<12)return 28*smooth(a,0,12);
        if(a<30)return 28;
        if(a<34)return 28+42*smooth(a,30,34);
        if(a<54)return 70;
        if(a<56.5f)return 70*(1-smooth(a,54,56.5f));
        return 0;
    }
    /** Rotation about the caster's local right axis; an actual held upside-down silhouette. */
    public static float flip(float a){if(a<12)return -180*smooth(a,4,12);if(a<30)return -180;if(a<34)return -180-180*smooth(a,30,34);return 0;}
    public static float spin(int skill,float a,int combo){return skill==M1&&combo==3?360*smooth(a,2,8):0;}
    public static MotionPose sample(int skill,float a,int combo){
        return switch(skill){
            case M1->track(a,M1_KEYS[Math.max(0,Math.min(3,combo-1))]);
            case E->{
                if(a<8)yield track(a,k(0,REST),k(8,THRUST_BACK));
                if(a>=50)yield track(a,k(50,THRUST_BACK),k(52,UP),k(55,UP),k(64,REST));
                yield MotionPose.blend(THRUST_BACK,THRUST_OUT,thrust(a));
            }
            case R->track(a,k(0,REST),k(8,pose(-18,-20,-30,-35,45,-35,0,30)),k(13,pose(-18,-20,-30,-35,45,-35,0,30)),k(21,BACK),k(29,REVERSE),k(36,UP),k(40,LOW),k(52,REST));
            case T->track(a,T_KEYS);
            case RUSH->{
                if(a<28)yield track(a,k(0,fireCharge()),k(7,fireCharge()),k(RUSH_IGNITE,firePlant()),k(15,firePlant()),k(26,fireCharge()),k(28,rushGlide()));
                if(a<46)yield rushGlide();
                yield track(a,k(46,rushGlide()),k(49,BACK),k(51,REVERSE),k(56,REVERSE),k(68,REST));
            }
            case Y->{
                MotionPose up=pose(-8,0,35,0,25,-55,0,15);up.rot(LEFT_LEG,-90,0,-8);up.knee[1]=95;
                MotionPose down=pose(28,0,40,0,20,-42,0,15);down.bob=4;down.knee[0]=down.knee[1]=28;
                yield track(a,k(0,REST),k(9,up),k(15,up),k(20,down),k(24,down),k(33,REST));
            }
            case H->track(a,H_KEYS);
            default->REST.copy();
        };
    }
}
