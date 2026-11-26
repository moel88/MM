package dev.sangwoo.celestialfall.combat;

import dev.sangwoo.celestialfall.client.MotionPlayerModel;
import dev.sangwoo.celestialfall.client.MotionRig;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.combat.MotionPose.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The body's own motions (2.141.0, user request: "평타 모션 가만히 서있는 모션 걷는 모션 달리는 모션 대쉬 모션 싹 다 갈아엎고
 * 멋있게 만들어줘 특히 서있는 모션이 지금 무릎을 내밀고 있는데 이게 꼴보기가 싫어 정자세로 서있게"): standing at attention,
 * the walk, the run, the dash and the four bare-handed blows, as the model draws them.
 */
class BaseMotionTest {
    static final MotionPlayerModel MODEL=new MotionPlayerModel(false);
    /** Where a foot of {@code p} is as the model draws it (px: x to the body's left, y down from the neck's height at rest, z back), {@code i} 0 the right. */
    static Vector3f foot(MotionPose p,int i){
        MotionRig.reset(MODEL);MotionRig.apply(MODEL,p,0,0);
        var leg=i==0?MODEL.rightLeg:MODEL.leftLeg;
        MatrixStack m=new MatrixStack();leg.rotate(m);leg.getChild("knee").rotate(m);
        return new Vector3f(0,6/16f,0).mulPosition(m.peek().getPositionMatrix()).mul(16);
    }
    /** The wrist of arm {@code i} (0 the right) the same way. */
    static Vector3f wrist(MotionPose p,int i){
        MotionRig.reset(MODEL);MotionRig.apply(MODEL,p,0,0);
        var arm=i==0?MODEL.rightArm:MODEL.leftArm;
        MatrixStack m=new MatrixStack();arm.rotate(m);arm.getChild("elbow").rotate(m);
        return new Vector3f(i==0?-1:1,6/16f*16/16f,0).mul(1/16f,1,1/16f).mulPosition(m.peek().getPositionMatrix()).mul(16);
    }
    /** The most any joint of the two poses differs (degrees; the hips' place in px counted as degrees). */
    static float apart(MotionPose a,MotionPose b){
        float most=0;
        for(int i=0;i<6;i++)for(int j=0;j<3;j++)most=Math.max(most,Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(a.joint[i][j]-b.joint[i][j])));
        for(int i=0;i<2;i++){most=Math.max(most,Math.abs(a.elbow[i]-b.elbow[i]));most=Math.max(most,Math.abs(a.knee[i]-b.knee[i]));}
        return Math.max(most,Math.max(Math.abs(a.bob-b.bob),Math.max(Math.abs(a.shift-b.shift),Math.abs(a.lean-b.lean))));
    }

    @Test void standingTheyStandAtAttentionWithTheLegsStraight(){
        for(float time=0;time<400;time+=3.7f){
            var p=MotionClips.locomotion(0,0,0,0,0,0,time);
            for(int i=0;i<2;i++){
                assertEquals(0,p.knee[i],1e-3,"no bend at the knee");
                assertEquals(0,p.joint[RIGHT_LEG+i][0],1e-3,"the leg straight down, not forward of the hip");
                assertEquals(0,p.joint[RIGHT_LEG+i][2],1e-3);
                Vector3f f=foot(p,i);
                assertEquals(24,f.y,1e-3,"the foot on the ground");assertEquals(i==0?-1.9f:1.9f,f.x,1e-3,"under its hip");assertEquals(0,f.z,1e-3);
            }
            assertEquals(0,p.bob,1e-6,"the hips do not move with the breath");assertEquals(0,p.lean,1e-6);
            assertTrue(Math.abs(p.joint[BODY][0])<1.5&&Math.abs(p.joint[BODY][1])<1&&Math.abs(p.joint[BODY][2])<1,"the back straight");
            for(int i=0;i<2;i++){
                assertTrue(Math.abs(p.joint[RIGHT_ARM+i][0])<1,"the arms down");assertTrue(p.elbow[i]>-10,"and nearly straight");
                assertTrue(Math.abs(p.joint[RIGHT_ARM+i][2])>2&&Math.abs(p.joint[RIGHT_ARM+i][2])<6,"a little off the body");
            }
        }
        // a blow just over does not leave them standing with the fists up: the fighting look is no pose of standing
        assertEquals(0,apart(MotionClips.locomotion(0,0,0,0,0,1,12),MotionClips.locomotion(0,0,0,0,0,0,12)),1e-6);
        // it breathes: something moves, a little, and never in a jump
        float moved=0;
        for(float time=0;time<200;time+=1/3f){float d=apart(MotionClips.stand(time),MotionClips.stand(time+1/3f));moved=Math.max(moved,d);assertTrue(d<.05,"the breath is slow");}
        assertTrue(moved>.003);
    }
    @Test void aFootIsSetWhereItIsAskedWhereverTheHipsAre(){
        var r=new java.util.Random(7);
        for(int n=0;n<400;n++){
            var p=new MotionPose();p.bob=r.nextFloat()*2;p.shift=r.nextFloat()*2-1;p.lean=r.nextFloat()*4-2;
            for(int i=0;i<2;i++){
                float x=(i==0?-1.9f:1.9f)+r.nextFloat()*5-2.5f,z=r.nextFloat()*9-4.5f,lift=r.nextFloat()*3,yaw=r.nextFloat()*60-30;
                MotionClips.plant(p,i,x,z,lift,yaw);
                // (within the leg's reach)
                if(Math.hypot(Math.hypot(x-(i==0?-1.9f:1.9f)-p.shift,z-p.lean),12-p.bob-lift)>11.9)continue;
                Vector3f f=foot(p,i);
                assertEquals(x,f.x,2e-2,"x");assertEquals(24-lift,f.y,2e-2,"on the ground, or as high as it is lifted");assertEquals(z,f.z,2e-2,"z");
                assertEquals(yaw,p.joint[RIGHT_LEG+i][1],1e-4,"the toes turned as asked");assertTrue(p.knee[i]>=0,"the knee bends its own way");
            }
        }
    }
    @Test void walkingAndRunningAFootOnTheGroundIsCarriedBackUnderTheHipsThenSwungThrough(){
        for(float sprint:new float[]{0,1}){
            float stance=sprint>0?MotionClips.RUN_STANCE:MotionClips.WALK_STANCE;
            float low=99,high=0,lift=0;
            for(float q=0;q<Math.PI*2;q+=.02f){
                var p=MotionClips.locomotion(q,1,1,0,sprint,0,0);
                float u=q/(float)(Math.PI*2);
                Vector3f f=foot(p,0);
                assertTrue(f.y<24.05,"never under the ground: "+f.y);
                if(u<stance-.03f){
                    // on the ground (a run's reaching leg is a hair short of it as it lands and leaves), going back
                    assertTrue(f.y>24-(sprint>0?1.6:.35),"the standing foot is on the ground: "+f.y+" at "+u);
                    var next=foot(MotionClips.locomotion(q+.02f,1,1,0,sprint,0,0),0);
                    assertTrue(next.z>f.z-1e-3,"carried back under the hips");
                }else lift=Math.max(lift,24-f.y);
                low=Math.min(low,f.z);high=Math.max(high,f.z);
                // the arm with the other leg: the right arm is back as the right foot is forward
                if(f.z<-1.5f)assertTrue(p.joint[RIGHT_ARM][0]>0,"the arm back as its own leg is forward");
                if(f.z>1.5f&&u<stance)assertTrue(p.joint[RIGHT_ARM][0]<p.joint[LEFT_ARM][0],"and forward of the other as its leg is back");
                // the head stays up whatever the body leans
                assertTrue(Math.abs(p.joint[HEAD][0])<3);
            }
            assertTrue(low<-(sprint>0?2.5:3)&&high>(sprint>0?4.5:3),"a real stride: "+low+" to "+high);
            assertTrue(lift>(sprint>0?3.5:1.2),"the swinging foot is lifted: "+lift);
            // a stride comes round to where it began, and never jumps on the way
            assertEquals(0,apart(MotionClips.locomotion(0,1,1,0,sprint,0,0),MotionClips.locomotion((float)(Math.PI*2),1,1,0,sprint,0,0)),1e-2);
            float step=(float)(Math.PI*2)/(sprint>0?16:22)/3;   // one frame at 60 a second of a stride at its pace
            for(float q=0;q<Math.PI*2;q+=step)assertTrue(apart(MotionClips.locomotion(q,1,1,0,sprint,0,0),MotionClips.locomotion(q+step,1,1,0,sprint,0,0))<(sprint>0?34:20),"no jump in a frame at "+q);
        }
        // the run leans into it and bends its arms; the walk is upright with long arms
        var run=MotionClips.locomotion(1,1,1,0,1,0,0);var walk=MotionClips.locomotion(1,1,1,0,0,0,0);
        assertTrue(run.joint[BODY][0]>12&&walk.joint[BODY][0]<6);
        assertTrue(run.elbow[0]<-60&&run.elbow[1]<-60&&walk.elbow[0]>-30&&walk.elbow[1]>-30);
        // from standing into the walk and back there is no jump
        for(float a=0;a<1;a+=.02f)assertTrue(apart(MotionClips.locomotion(1.3f,a,1,0,0,0,0),MotionClips.locomotion(1.3f,a+.02f,1,0,0,0,0))<3.5,"easing in at "+a);
        assertEquals(0,apart(MotionClips.locomotion(1.3f,0,1,0,0,0,5),MotionClips.stand(5)),1e-6);
        // backwards and sideways the feet still stay over the ground and do not cross
        for(float q=0;q<Math.PI*2;q+=.05f){
            for(float[] way:new float[][]{{-1,0},{0,1},{0,-1},{.7f,.7f}}){
                var p=MotionClips.locomotion(q,1,way[0],way[1],0,0,0);
                assertTrue(foot(p,0).y<24.05&&foot(p,1).y<24.05);
                assertTrue(foot(p,0).x<foot(p,1).x-.2f,"the right foot stays on the right");
            }
        }
    }
    @Test void everyBlowIsThrownFromTheStanceAndComesBackToIt(){
        var stance=MotionClips.stance(0);
        // the stance: the left foot forward, the right back and turned out, the fists up
        assertTrue(foot(stance,1).z<-1.5&&foot(stance,0).z>1.5);
        assertTrue(stance.elbow[0]<-90&&stance.elbow[1]<-90,"the fists up");
        assertTrue(stance.knee[0]<22&&stance.knee[1]<22,"the knees only a little bent: "+stance.knee[0]+", "+stance.knee[1]);
        for(int strike=1;strike<=4;strike++){
            float hit=CombatState.WINDUP[strike],end=CombatState.DURATION[strike];
            int arm=strike%2==1?1:0;
            assertTrue(apart(MotionClips.attack(strike,0),MotionClips.stance(0))<1.2,"from the stance");
            assertTrue(apart(MotionClips.attack(strike,end),MotionClips.stance(end))<1.2,"back in the stance");
            // the fist is furthest forward about the contact, and not before the wind-up is over
            float best=99,bestAt=0,wound=wrist(MotionClips.attack(strike,hit*.5f),arm).z;
            for(float t=0;t<=end;t+=.05f){float z=wrist(MotionClips.attack(strike,t),arm).z;if(z<best){best=z;bestAt=t;}}
            if(strike!=3){
                assertTrue(bestAt>=hit-.3f&&bestAt<hit+3.2f,"strike "+strike+": the fist is furthest out at "+bestAt+", the blow lands at "+hit);
                assertTrue(best<wound-6,"it goes out from the wind-up: "+wound+" to "+best);
            }else{
                // the hook goes across, not out: from out at the left to across the front of the body
                float from=wrist(MotionClips.attack(3,hit*.5f),1).x,to=wrist(MotionClips.attack(3,hit+1.2f),1).x;
                assertTrue(to<from-5,"the hook's fist swings across: "+from+" to "+to);
            }
            if(strike!=3)assertTrue(MotionClips.attack(strike,hit+.9f).elbow[arm]>-12,"the arm straight through the contact");
            else assertTrue(MotionClips.attack(strike,hit+.9f).elbow[arm]<-70,"the hook's elbow stays bent");
            // the weight moves onto the front foot through the blow, and the shoulders turn behind the fist
            var wind=MotionClips.attack(strike,hit*.5f);var contact=MotionClips.attack(strike,hit+.9f);
            assertTrue(contact.lean<wind.lean-.9f,"the weight goes forward: "+wind.lean+" to "+contact.lean);
            float turned=contact.joint[BODY][1]-wind.joint[BODY][1];
            assertTrue(arm==1?turned>20:turned<-20,"the shoulder comes round behind the fist: "+turned);
            // the feet stay on the ground and where they stand (the fourth steps in)
            for(float t=0;t<=end;t+=.1f){
                var p=MotionClips.attack(strike,t);
                for(int i=0;i<2;i++){
                    Vector3f f=foot(p,i);
                    assertTrue(f.y<24.05&&f.y>24-1.6f,"strike "+strike+" at "+t+": foot "+i+" at "+f.y);
                    if(strike<4)assertTrue(new Vector3f(f.x,24,f.z).distance(foot(stance,i).x,24,foot(stance,i).z)<1.3f,"the foot stays where it stands");
                }
                assertTrue(foot(p,1).z<foot(p,0).z,"the left foot stays the front one");
            }
            // no joint jumps in a frame (60 a second: a third of a tick)
            for(float t=0;t<end;t+=1/3f)assertTrue(apart(MotionClips.attack(strike,t),MotionClips.attack(strike,t+1/3f))<46,"strike "+strike+" at "+t+": "+apart(MotionClips.attack(strike,t),MotionClips.attack(strike,t+1/3f)));
        }
        // the fourth is the big one: wound up further back and driven further in than the cross
        assertTrue(MotionClips.attack(4,CombatState.WINDUP[4]*.5f).lean>MotionClips.attack(2,CombatState.WINDUP[2]*.5f).lean+.5f);
        assertTrue(MotionClips.attack(4,CombatState.WINDUP[4]+1).lean<MotionClips.attack(2,CombatState.WINDUP[2]+1).lean-1);
        assertTrue(foot(MotionClips.attack(4,CombatState.WINDUP[4]+1),1).z<foot(stance,1).z-1.5f,"it steps in");
    }
    @Test void theDashPushesOffStretchesOutAndBrakes(){
        float n=CombatState.DASH_TICKS;
        for(float[] way:new float[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1}}){
            assertTrue(apart(MotionClips.dash(0,way[0],way[1]),MotionClips.Ctl.class.cast(MotionClips.atRest()).pose())<1e-3,"from standing");
            assertTrue(apart(MotionClips.dash(n,way[0],way[1]),MotionClips.atRest().pose())<1e-3,"back to standing");
            for(float t=0;t<n;t+=1/3f)assertTrue(apart(MotionClips.dash(t,way[0],way[1]),MotionClips.dash(t+1/3f,way[0],way[1]))<50,"no jump at "+t);
            for(float t=0;t<=n;t+=.1f)for(int i=0;i<2;i++)assertTrue(foot(MotionClips.dash(t,way[0],way[1]),i).y<24.05,"no foot under the ground");
            // low through the middle of it
            assertTrue(MotionClips.dash(n*.5f,way[0],way[1]).bob>1,"the body low");
        }
        var ahead=MotionClips.dash(n*.5f,1,0);var back=MotionClips.dash(n*.5f,-1,0);
        assertTrue(ahead.joint[BODY][0]>30,"thrown forward");assertTrue(ahead.lean<-1.5f);
        assertTrue(ahead.joint[RIGHT_ARM][0]>40&&ahead.joint[LEFT_ARM][0]>40,"the arms swept back");
        assertTrue(foot(ahead,0).z>4,"a leg stretched out behind");
        assertTrue(back.lean>1.5f,"the back step's hips go back");assertTrue(foot(back,0).z>3,"its back foot reaching behind");
        // to the side the body rolls over the leading leg, which reaches out the way it goes (side 1 is the model's -x)
        var right=MotionClips.dash(n*.5f,0,1);var left=MotionClips.dash(n*.5f,0,-1);
        assertTrue(right.joint[BODY][2]<-15&&left.joint[BODY][2]>15);
        assertTrue(foot(right,0).x<-4.5f,"the right foot reaches out to the right");assertTrue(foot(left,1).x>4.5f);
        assertEquals(right.joint[BODY][2],-left.joint[BODY][2],1e-3,"the two sides mirror each other");
    }
}
