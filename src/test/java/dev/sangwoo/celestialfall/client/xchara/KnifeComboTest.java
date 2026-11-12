package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.isosang.IsosangSlash;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's basic attack as drawn: the body's motion through its four swings (2.117.0, user request: "이번에는 Q 껐을때
 * 평타를 만들어줘 이펙트 없이 그냥 휘두르기만해 그대신 모션을 좋게 만들어봐"), the same with their hack on or off (2.118.0).
 * No joint jumping or flipping between frames, the body facing the front all through, each swing beginning where the
 * one before stands as it hands over, nothing in the ground (the feet on it, never in it); each lands on the middle
 * line at a body's height within the server's reach, its edge leading from as it is let go until it is past its blow,
 * the body turning before the arm goes, quickest as it lands and its wind-up slower, carried on past the blow and
 * never frozen until the next takes over; the feet stand where they are put while the body moves over them, and step
 * in. (What a swing begun with the hack on draws: SlashFxTest.)
 */
class KnifeComboTest {
    /** Every hit: the four begun with the hack on, then the plain four (the same swings). */
    static final int[] ALL={HACK_SLASH,HACK_RISE,HACK_CHOP,HACK_LUNGE,SLASH,RISE,CHOP,LUNGE};
    static Quaternionf q(float[] e){float k=(float)Math.PI/180;return new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k);}
    static double turn(R6.Frame a,R6.Frame b,int j){return Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(q(a.joints().joint[j]).dot(q(b.joints().joint[j]))))));}
    static float blow(int hit,int i){return blowAt(hit,i)/20f;}
    /** A world point as (forward, up, right) of a user at the origin facing yaw 0 (the game's +z). */
    static double[] local(Vec3d p){return new double[]{p.z,p.y,-p.x};}
    /** The knife's fist, heel and point in the hit's frame at {@code sec}. */
    static Vec3d[] knife(int hit,float sec){
        var hb=KnifeCombo.blade(Vec3d.ZERO,0,hit,sec);
        return new Vec3d[]{hb[0],hb[0].add(hb[1].multiply(KnifeCombo.HEEL)),hb[0].add(hb[1].multiply(KnifeCombo.TIP)),hb[1]};
    }

    @Test void noClipJumpsOrFlipsItsJoints(){
        for(int hit:ALL){
            Anim a=KnifeCombo.clipOf(hit);double h=1/960.0;
            for(double t=h;t+2*h<=a.length();t+=h){
                var p0=a.frame((float)(t-h));var p1=a.frame((float)t);var p2=a.frame((float)(t+h));var p3=a.frame((float)(t+2*h));
                for(int j=0;j<6;j++){
                    double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                    assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"hit "+hit+" joint "+j+" jumps "+now+" deg at "+t);
                }
            }
            for(double t=0;t+1/60.0<=a.length();t+=1/60.0){
                for(int j=0;j<6;j++)assertTrue(turn(a.frame((float)t),a.frame((float)(t+1/60.0)),j)<125,"hit "+hit+" joint "+j+" flips at "+t);
                var f0=a.frame((float)t).joints();var f1=a.frame((float)(t+1/60.0)).joints();
                for(int i=0;i<2;i++){
                    assertTrue(Math.abs(f1.elbow[i]-f0.elbow[i])<75,"hit "+hit+" elbow "+i+" snaps at "+t);
                    assertTrue(Math.abs(f1.knee[i]-f0.knee[i])<55,"hit "+hit+" knee "+i+" snaps at "+t);
                }
            }
        }
    }
    @Test void everyHitFacesTheFront(){
        // the body never spins round: it only twists with the swing, and the arms and the wrist do the cutting
        for(int hit:ALL){
            var a=KnifeCombo.clipOf(hit);
            for(double t=0;t<=a.length();t+=1/240.0){
                float yaw=MathHelper.wrapDegrees(a.frame((float)t).yaw());
                assertTrue(Math.abs(yaw)<=40,"hit "+hit+" turns "+yaw+" deg away from the front at "+t);
            }
        }
    }
    @Test void hitStopsSitOnBlowsAndAreOverBeforeTheNext(){
        for(int hit:ALL){
            var a=KnifeCombo.clipOf(hit);
            for(var s:a.stops()){
                assertEquals(s.sec(),a.warp(s.sec()),1e-6,"hit "+hit+": the impact itself is on time");
                boolean on=false;for(int i=0;i<blows(hit);i++)on|=Math.abs(blow(hit,i)-s.sec())<1e-4;
                assertTrue(on,"hit "+hit+": hit-stop at "+s.sec()+" is not on a blow");
                for(int i=0;i<blows(hit);i++)if(blow(hit,i)>s.sec()+1e-4)assertTrue(s.sec()+s.hold()+s.recover()<=blow(hit,i)+1e-6,"hit "+hit+": still catching up at the next blow");
                if(!last(hit))assertTrue(s.sec()+s.hold()+s.recover()<=STEP[hit]/20f,"hit "+hit+": still catching up as the next takes over");
            }
        }
    }
    @Test void theFirstStartsAndEveryOneEndsInTheStanceAndRunsAsLongAsTheHit(){
        var stance=KnifeCombo.stance();
        for(int begins:new int[]{HACK_SLASH,SLASH}){
            var first=KnifeCombo.clipOf(begins).frame(0);
            for(int j=0;j<6;j++)assertTrue(turn(first,stance,j)<.5,"hit "+begins+" starts in the stance");
        }
        for(int hit:ALL){
            var a=KnifeCombo.clipOf(hit);var last=a.frame(a.length());
            for(int j=0;j<6;j++)assertTrue(turn(last,stance,j)<.5,"hit "+hit+" ends in the stance");
            assertEquals(0,MathHelper.wrapDegrees(last.yaw()),1e-3,"hit "+hit+" ends facing the way it began");
            assertEquals(0,last.fw(),1e-6);assertEquals(0,last.up(),1e-6);
            assertEquals(stance.grip(),last.grip(),.01,"hit "+hit+" gives the knife back to the game's hold");
            assertEquals(LENGTH[hit]/20f,a.length(),.011,"hit "+hit+" runs as long as the server says");
        }
    }
    @Test void theStanceIsTheGamesHoldOfTheKnife(){
        // (2.116.0) at attention: the arm straight down, a little out from the body, the knife square to it: level ahead
        var s=KnifeCombo.stance();var hold=KnifeCombo.hold();
        assertTrue(turn(s,hold,2)<.05,"the arm as the game holds it");
        assertEquals(0,s.joints().elbow[0],1e-4,"the elbow straight");
        assertEquals(KnifeCombo.DISPLAY_TILT,s.grip(),.05,"the knife as its display turns it");
        var k=knife(SLASH,0);
        assertTrue(Math.abs(k[3].y)<.02&&k[3].z>.999,"the knife level, pointing ahead: "+k[3]);
        var arm=R6.poseOf(s).rArm;
        assertEquals(Math.sin(Math.toRadians(XCharaIdle.KNIFE_OUT)),arm.rt(),1e-3,"the arm a little out to the right");
        assertEquals(0,arm.fw(),1e-3,"and neither forward nor back");
    }
    @Test void whereAHitStandsInTheStanceItsLimbsAreStraightAndBetweenTheyBend(){
        // the first hit begins at attention and every hit ends there: no elbow or knee bent, eased out of and into
        for(int begins:new int[]{HACK_SLASH,SLASH}){
            var first=KnifeCombo.clipOf(begins).frame(0).joints();
            for(int i=0;i<2;i++){assertEquals(0,first.elbow[i],1e-3);assertEquals(0,first.knee[i],1e-3);}
        }
        for(int hit:ALL){
            var a=KnifeCombo.clipOf(hit);var last=a.frame(a.length()).joints();
            for(int i=0;i<2;i++){assertEquals(0,last.elbow[i],1e-3,"hit "+hit);assertEquals(0,last.knee[i],1e-3,"hit "+hit);}
            // between, an elbow bends (the limbs are a person's)
            float bent=0;
            for(double t=.1;t<a.length()-.2;t+=1/60.0){var j=a.frame((float)t).joints();bent=Math.max(bent,Math.max(-j.elbow[0],-j.elbow[1]));}
            assertTrue(bent>10,"hit "+hit+" bends an elbow "+bent);
            // into the stance without a step: the last sixth of a second, frame by frame
            for(double t=a.length()-KnifeCombo.INTO_REST-.02;t+1/120.0<=a.length();t+=1/120.0){
                var j0=a.frame((float)t).joints();var j1=a.frame((float)(t+1/120.0)).joints();
                for(int i=0;i<2;i++)assertTrue(Math.abs(j1.elbow[i]-j0.elbow[i])<4,"hit "+hit+" elbow "+i+" steps at "+t);
            }
        }
    }
    @Test void eachHitBeginsWhereTheOneBeforeStandsAsItHandsOver(){
        for(int hit:ALL){
            if(last(hit))continue;
            var before=KnifeCombo.clipOf(hit).frame(STEP[hit]/20f);var after=KnifeCombo.clipOf(hit+1).frame(0);
            for(int j=0;j<6;j++)assertTrue(turn(before,after,j)<1,"hit "+(hit+1)+" joint "+j+" begins "+turn(before,after,j)+" deg off hit "+hit);
            assertEquals(before.fw(),after.fw(),.02);assertEquals(before.up(),after.up(),.02);
            assertEquals(MathHelper.wrapDegrees(before.yaw()),MathHelper.wrapDegrees(after.yaw()),1);
        }
    }
    @Test void theKnifeAndTheFeetStayOutOfTheGround(){
        for(int hit:ALL){
            var a=KnifeCombo.clipOf(hit);
            for(double t=0;t<=a.length();t+=1/240.0){
                var k=knife(hit,(float)t);
                assertTrue(k[1].y>.05&&k[2].y>.05,"hit "+hit+": the knife in the ground at "+t+" ("+k[1].y+", "+k[2].y+")");
                double[] feet=IsosangSlash.feet(a.frame((float)t));
                assertTrue(feet[0]>-.03&&feet[1]>-.03,"hit "+hit+": a foot in the ground at "+t+" ("+feet[0]+", "+feet[1]+")");
                assertTrue(Math.min(feet[0],feet[1])<.06,"hit "+hit+": both feet off the ground at "+t+" ("+feet[0]+", "+feet[1]+")");
            }
        }
    }
    @Test void aBlowShakesTheCameraOnlyALittleAndTheLungeMost(){
        for(int place=1;place<=4;place++)assertTrue(KnifeCombo.SHAKE[place]>0&&KnifeCombo.SHAKE[place]<=1.2f);
        assertTrue(KnifeCombo.SHAKE[4]>KnifeCombo.SHAKE[3]&&KnifeCombo.SHAKE[3]>KnifeCombo.SHAKE[1],"the heavier the swing the more");
        // in over a frame or two, never a jump
        float before=0;
        for(float t=.3f;t<.6f;t+=1/240f){float v=KnifeCombo.bump(t,.4f,.07f);assertTrue(Math.abs(v-before)<.3f,"no jump at "+t);before=v;}
        assertEquals(0,KnifeCombo.bump(.3f,.4f,.07f),1e-6);
    }
    @Test void theHackOnOrOffTheBodySwingsTheSame(){
        for(int place=1;place<=4;place++)assertSame(KnifeCombo.clipOf(hit(place,true)),KnifeCombo.clipOf(hit(place,false)),"place "+place);
        // and no copy of the body is ever drawn behind it
        var combo=new KnifeCombo();
        for(float t=0;t<KnifeCombo.LIFE;t+=.05f)assertEquals(0,combo.afterimages(t));
        for(int hit:ALL)assertTrue(blow(hit,0)+SlashFx.EAT1<KnifeCombo.LIFE&&LENGTH[hit]/20f<KnifeCombo.LIFE,"hit "+hit+" is drawn to its end");
    }

    // ───────────────────────────── the four swings (2.117.0) ─────────────────────────────

    /** The player model's hips: this far to each side of the torso's pivot and this far below it (IsosangSlash's, the rig's). */
    static final double HIP_SIDE=.12,HIP_DOWN=.3;
    /** Where foot {@code i} (0 right, 1 left) of a frame stands in the hit's frame: right, up, ahead (the model's own leg, its knee bent). */
    static double[] foot(R6.Frame f,int i){
        var m=f.joints();double[] hip={i==0?HIP_SIDE:-HIP_SIDE,-HIP_DOWN,0};
        var leg=ClipPreviewDump.limb(hip,m.joint[i==0?MotionPose.RIGHT_LEG:MotionPose.LEFT_LEG],m.knee[i],R6.LEG_UPPER,R6.LEG_LOWER);
        return ClipPreviewDump.world(R6.poseOf(f),leg[1][0],leg[1][1],leg[1][2]);
    }
    static double[] foot(int hit,double sec,int i){return foot(KnifeCombo.clipOf(hit).frame((float)sec),i);}
    /** How fast the knife's point goes at {@code sec} of a hit (blocks a second). */
    static double speed(int hit,double sec){double h=1/480.0;return knife(hit,(float)(sec+h))[2].distanceTo(knife(hit,(float)(sec-h))[2])/(2*h);}
    /** The knife's edge's way at a frame (the arm's way across the blade: the hand holds it with its edge on the arm's side), not made a unit: its length is how far the knife is off the arm's line. */
    static Vec3d edge(R6.Frame f){
        var pose=R6.poseOf(f);var hb=KagenoClips.blade(Vec3d.ZERO,0,pose);
        Vec3d arm=hb[0].subtract(KagenoClips.shoulder(Vec3d.ZERO,0,pose)).normalize();
        return arm.subtract(hb[1].multiply(arm.dotProduct(hb[1])));
    }
    /** When each plain cut is wound furthest (seconds into it): the slash, the rising cut, the chop. */
    static final float[] WOUND={.115f,.105f,.21f};

    @Test void aSwingsPlaneKeepsItsWaysInIt(){
        for(var s:new KnifeCombo.Swing[]{KnifeCombo.S1,KnifeCombo.S3}){
            R6.Dir ahead=R6.dir(s.at(0)[0],s.at(0)[1]),way=R6.dir(s.at(90)[0],s.at(90)[1]);
            assertEquals(0,ahead.dot(way),1e-9,"its way made square to where it points as it lands");
            R6.Dir normal=new R6.Dir(ahead.up()*way.fw()-ahead.fw()*way.up(),ahead.fw()*way.rt()-ahead.rt()*way.fw(),ahead.rt()*way.up()-ahead.up()*way.rt());
            for(double turn=-180;turn<=180;turn+=7.5){
                R6.Dir at=R6.dir(s.at(turn)[0],s.at(turn)[1]);
                assertEquals(0,at.dot(normal),1e-9,"in the plane at "+turn);
                assertEquals(Math.cos(Math.toRadians(turn)),at.dot(ahead),1e-9,"that far round at "+turn);
                assertEquals(Math.sin(Math.toRadians(turn)),at.dot(way),1e-9);
            }
        }
        // the slash goes level to the left, the chop down (a little to the left)
        R6.Dir slash=R6.dir(KnifeCombo.S1.at(90)[0],KnifeCombo.S1.at(90)[1]),chop=R6.dir(KnifeCombo.S3.at(90)[0],KnifeCombo.S3.at(90)[1]);
        assertTrue(slash.rt()<-.95&&Math.abs(slash.up())<.2,"the slash to the left");
        assertTrue(chop.up()<-.85&&chop.rt()<-.2&&chop.rt()>-.5,"the chop down, a little to the left");
    }
    @Test void aPoseStandsWhereItsFeetArePut(){
        // IsosangLimbs.standingAt against the bend it turns round: the foot comes to the point it was given
        // {the body right of, ahead of and above where it stands, its turn, lean and roll}
        double[][] poses={{0,0,-.15,0,0,0},{-.07,.3,-.24,34,13,-4},{.06,.58,-.12,-28,-4,3},{-.04,.74,-.38,29,27,-4},{-.08,.5,-.3,-38,8,3},{.02,1.2,-.36,28,23,-3}};
        double[][] at={{.21,-.03,-.20,.40},{.16,.3,-.2,.1},{.2,.1,-.22,-.15}};
        for(double[] b:poses)for(double[] f:at){
            double fw=b[1];
            R6.Pose p=KnifeCombo.on(b[0],fw,b[2],(float)b[3],(float)b[4],(float)b[5],IsosangSlash.d(-40,40),IsosangSlash.d(0,0),IsosangSlash.d(-60,-30),f[0],fw+f[1],f[2],fw+f[3]);
            assertEquals(b[0],p.rt,1e-9);
            R6.Frame bent=IsosangLimbs.bend(R6.solve(p),0);
            for(int i=0;i<2;i++){
                double[] foot=foot(bent,i);double right=f[2*i],ahead=fw+f[2*i+1];
                assertEquals(0,foot[1],.012,"on the ground");
                double[] hip=ClipPreviewDump.world(p,i==0?HIP_SIDE:-HIP_SIDE,-HIP_DOWN,0);
                double want=Math.hypot(right-hip[0],ahead-hip[2]),got=Math.hypot(foot[0]-hip[0],foot[2]-hip[2]);
                if(Math.hypot(foot[0]-right,foot[2]-ahead)<.015)continue;
                // (further than the leg reaches from that hip: short of it, on the way to it)
                assertTrue(got<want,"a foot not where it was put is short of it: "+got+" of "+want);
                assertEquals((right-hip[0])/want,(foot[0]-hip[0])/got,.03,"on the way to it");assertEquals((ahead-hip[2])/want,(foot[2]-hip[2])/got,.03);
            }
        }
        // standing as tall as the legs are long they hang straight down
        R6.Pose tall=KnifeCombo.on(0,0,0,0,0,0,IsosangSlash.d(-90,0),IsosangSlash.d(0,0),IsosangSlash.d(-90,0),.5,.5,-.5,-.5);
        assertTrue(tall.rLeg.up()<-.999&&tall.lLeg.up()<-.999);
    }
    @Test void aPlainSwingLandsOnTheMiddleLineAtABodysHeightWithinTheServersReach(){
        for(int hit=SLASH;hit<=LUNGE;hit++){
            var k=knife(hit,blow(hit,0));double[] heel=local(k[1]),tip=local(k[2]);
            assertTrue(tip[0]>3.1&&heel[0]<3,"hit "+hit+": the blade ahead of them: "+heel[0]+" to "+tip[0]);
            double u=(3-heel[0])/(tip[0]-heel[0]),side=heel[2]+(tip[2]-heel[2])*u,high=heel[1]+(tip[1]-heel[1])*u;
            assertTrue(Math.abs(side)<.2,"hit "+hit+": the blade on the middle line three blocks ahead: "+side);
            assertTrue(high>.8&&high<1.6,"hit "+hit+": the blade at a body's height there: "+high);
            assertTrue(tip[0]<=reach(hit,0)+.3&&Math.abs(tip[2])<=halfWidth(hit,0),"hit "+hit+": the point within the blow's reach: "+tip[0]+", "+tip[2]);
        }
        // the lunge's point is driven on to the end of its reach, and no further
        double far=0;for(double t=blow(LUNGE,0);t<.7;t+=1/120.0)far=Math.max(far,local(knife(LUNGE,(float)t)[2])[0]);
        assertTrue(far>reach(LUNGE,0)-.3&&far<=reach(LUNGE,0)+.1,"the lunge's point out to "+far);
        // the cuts go as they are named: the first from the right to the left, level; the second from the low left up to
        // the high right; the third from high down
        double[] a0=local(knife(SLASH,WOUND[0])[2]),a1=local(knife(SLASH,.30f)[2]);
        assertTrue(a0[2]>1&&a1[2]<-1&&Math.abs(a0[1]-a1[1])<1,"the slash level from the right to the left: "+a0[2]+" to "+a1[2]);
        double[] b0=local(knife(RISE,WOUND[1])[2]),b1=local(knife(RISE,.30f)[2]);
        assertTrue(b0[2]<-1&&b0[1]<.8&&b1[2]>.5&&b1[1]>b0[1]+2,"the rising cut from the low left to the high right: "+b0[1]+" to "+b1[1]);
        double[] c0=local(knife(CHOP,WOUND[2])[2]),c1=local(knife(CHOP,.40f)[2]);
        assertTrue(c0[1]>2.5&&c1[1]<.6&&c0[0]<0&&c1[0]>3,"the chop from high behind down ahead: "+c0[1]+" to "+c1[1]);
    }
    @Test void aPlainSwingsEdgeLeadsFromAsItIsLetGoUntilItIsPastItsBlow(){
        float[] past={.29f,.29f,.39f};
        for(int n=0;n<3;n++){
            int hit=SLASH+n,led=0,all=0;
            for(double t=WOUND[n]+.012;t<=past[n];t+=1/240.0){
                Vec3d edge=edge(KnifeCombo.clipOf(hit).frame((float)t));
                Vec3d v=knife(hit,(float)(t+1/240.0))[2].subtract(knife(hit,(float)t)[2]);
                if(v.length()<1e-4)continue;
                all++;if(edge.normalize().dotProduct(v.normalize())>.5)led++;
            }
            assertTrue(all>20&&led>=all*.9,"hit "+hit+": the edge leads "+led+" of "+all+" moments of the swing");
        }
        // and all through every plain hit the knife keeps off the arm's line (its turn in the hand never undefined)
        for(int hit=SLASH;hit<=LUNGE;hit++){
            var a=KnifeCombo.clipOf(hit);
            for(double t=0;t<=a.length();t+=1/240.0)assertTrue(edge(a.frame((float)t)).length()>.25,"hit "+hit+": the knife in line with the arm at "+t);
        }
    }
    @Test void aPlainSwingIsLetGoFromTheBodyFirst(){
        // a moment after it is wound furthest the body has turned a good part of its way to the blow, the arm hardly
        for(int n=0;n<3;n++){
            int hit=SLASH+n;var a=KnifeCombo.clipOf(hit);float w=WOUND[n],soon=w+.03f,b=blow(hit,0);
            double body=(a.frame(soon).yaw()-a.frame(w).yaw())/(a.frame(b).yaw()-a.frame(w).yaw());
            // (the arm's way in the world, which the keys write it in)
            double arm=between(a,w,soon)/between(a,w,b);
            assertTrue(body>.2,"hit "+hit+": the body has turned "+body+" of its way");
            assertTrue(arm<body-.08,"hit "+hit+": the arm has gone "+arm+" of its way against the body's "+body);
        }
    }
    /** How far (degrees) the knife's arm turns in the world between two moments of a clip. */
    static double between(Anim a,float from,float to){
        var p0=R6.poseOf(a.frame(from));var p1=R6.poseOf(a.frame(to));
        Vec3d d0=KagenoClips.blade(Vec3d.ZERO,0,p0)[0].subtract(KagenoClips.shoulder(Vec3d.ZERO,0,p0)).normalize();
        Vec3d d1=KagenoClips.blade(Vec3d.ZERO,0,p1)[0].subtract(KagenoClips.shoulder(Vec3d.ZERO,0,p1)).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,d0.dotProduct(d1)))));
    }
    @Test void aPlainSwingIsQuickestAsItLandsAndItsWindUpSlower(){
        for(int n=0;n<3;n++){
            int hit=SLASH+n;float b=blow(hit,0);double swing=0,at=0,wind=0;
            for(double t=WOUND[n];t<=b-1/240.0;t+=1/240.0){double v=speed(hit,t);if(v>swing){swing=v;at=t;}}
            for(double t=1/240.0;t<WOUND[n];t+=1/240.0)wind=Math.max(wind,speed(hit,t));
            assertTrue(at>=b-.045,"hit "+hit+" is quickest as it lands: at "+at);
            assertTrue(swing>80,"hit "+hit+": a real swing: its point at "+swing+" blocks a second");
            assertTrue(wind<swing*.8,"hit "+hit+"'s wind-up slower than its swing: "+wind+" against "+swing);
        }
        // the lunge: held drawn back, then out, still going as it lands, and driven on after
        for(double t=.2;t<=.265;t+=1/240.0)assertTrue(speed(LUNGE,t)<5,"the lunge held drawn back at "+t+": "+speed(LUNGE,t));
        double most=0;for(double t=.27;t<=blow(LUNGE,0)-1/240.0;t+=1/240.0)most=Math.max(most,speed(LUNGE,t));
        assertTrue(most>15,"the lunge out at "+most+" blocks a second");
        assertTrue(speed(LUNGE,blow(LUNGE,0)-.01)>most*.6,"still going as it lands: "+speed(LUNGE,blow(LUNGE,0)-.01)+" of "+most);
        double[] on=local(knife(LUNGE,blow(LUNGE,0))[0]),after=local(knife(LUNGE,.6f)[0]);
        assertTrue(after[0]>on[0]+.1,"driven on past its blow: "+on[0]+" to "+after[0]);
        // out along the knife's own line: its point keeps to the middle line all the way out
        for(double t=.27;t<=.6;t+=1/120.0){var k=knife(LUNGE,(float)t);double[] heel=local(k[1]),tip=local(k[2]);
            double u=(3-heel[0])/(tip[0]-heel[0]),side=heel[2]+(tip[2]-heel[2])*u;
            assertTrue(Math.abs(side)<.4,"the lunge's line off the middle at "+t+": "+side);}
    }
    @Test void aPlainSwingIsCarriedOnPastItsBlowAndNeverFrozenUntilTheNextTakesOver(){
        for(int hit=SLASH;hit<=CHOP;hit++){
            float b=blow(hit,0),step=STEP[hit]/20f;double on=0,drift=0,fastest=0;
            for(double t=b;t<step-1/240.0;t+=1/240.0)on+=knife(hit,(float)(t+1/240.0))[2].distanceTo(knife(hit,(float)t)[2]);
            assertTrue(on>1.5,"hit "+hit+": its point is carried on "+on+" blocks past the blow");
            for(double t=step-.1;t<step-1/240.0;t+=1/240.0){drift+=knife(hit,(float)(t+1/240.0))[2].distanceTo(knife(hit,(float)t)[2]);fastest=Math.max(fastest,speed(hit,t));}
            assertTrue(drift>.03,"hit "+hit+": still moving before the next takes over: "+drift);
            assertTrue(fastest<15,"hit "+hit+": settled by then: "+fastest);
        }
    }
    @Test void thePlainSwingsFeetStandWhereTheyArePutWhileTheBodyMovesOverThem(){
        // {hit, foot (0 right, 1 left), from, to (seconds), right, ahead (blocks)}
        double[][] stands={
            {SLASH,0,.09,.40,.21,-.03},{SLASH,1,.115,.40,-.20,.40},
            {RISE,1,0,.45,-.20,.40},{RISE,0,.20,.45,.16,.84},
            {CHOP,0,0,.60,.16,.84},{CHOP,1,.30,.60,-.22,.22},
            // (the lunge's furthest comes once its hit-stop has been caught up with)
            {LUNGE,1,0,.27,-.22,.22},{LUNGE,0,.56,.75,.14,1.50},{LUNGE,1,.56,.75,-.22,.68}};
        for(double[] s:stands){
            int hit=(int)s[0],i=(int)s[1];
            for(double t=s[2];t<=s[3]+1e-9;t+=1/120.0){
                double[] at=foot(hit,t,i);
                assertEquals(s[4],at[0],.05,"hit "+hit+" foot "+i+" slides sideways at "+t);
                assertEquals(s[5],at[2],.05,"hit "+hit+" foot "+i+" slides at "+t);
                assertTrue(Math.abs(at[1])<.03,"hit "+hit+" foot "+i+" off the ground at "+t+": "+at[1]);
            }
        }
        // the body goes over them: through the first swing it travels a good way while both feet stay
        assertTrue(KnifeCombo.clipOf(SLASH).frame(.30f).fw()-KnifeCombo.clipOf(SLASH).frame(WOUND[0]).fw()>.3);
        assertTrue(KnifeCombo.clipOf(LUNGE).frame(.6f).fw()-KnifeCombo.clipOf(LUNGE).frame(.27f).fw()>.6,"the lunge carries the body out");
    }
    @Test void thePlainSwingsStepIn(){
        // the first slides the left foot out as it is wound
        assertTrue(foot(SLASH,WOUND[0],1)[2]>foot(SLASH,0,1)[2]+.35,"the left foot out ahead as the first is wound");
        // the second steps the right foot through, past the left, landing as it lands
        assertTrue(foot(RISE,0,0)[2]<foot(RISE,0,1)[2]-.3&&foot(RISE,blow(RISE,0),0)[2]>foot(RISE,blow(RISE,0),1)[2]+.3,"the right foot steps through with the second");
        assertTrue(foot(RISE,blow(RISE,0)-.06,0)[2]<foot(RISE,blow(RISE,0),0)[2]-.1,"and is still coming a moment before the blow");
        // the third gathers the back foot under it at the top and shoots it back as it drops
        assertTrue(foot(CHOP,WOUND[2],1)[2]>foot(CHOP,0,1)[2]+.1&&foot(CHOP,blow(CHOP,0),1)[2]<foot(CHOP,WOUND[2],1)[2]-.25,"the back foot gathered, then shot back");
        // the lunge throws the front foot out and drags the back one after it
        assertTrue(foot(LUNGE,.6,0)[2]>foot(LUNGE,.27,0)[2]+.7,"the front foot out with the lunge");
        assertTrue(foot(LUNGE,.6,1)[2]>foot(LUNGE,.27,1)[2]+.3&&foot(LUNGE,.6,1)[2]<foot(LUNGE,.6,0)[2]-.7,"the back foot dragged after it, far behind the front");
        // every plain hit comes home to the stance's feet
        for(int hit=SLASH;hit<=LUNGE;hit++){var a=KnifeCombo.clipOf(hit);
            for(int i=0;i<2;i++){double[] at=foot(a.frame(a.length()),i);assertEquals(0,at[2],.01,"hit "+hit+" foot "+i+" home");assertEquals(i==0?.185:-.185,at[0],.02);}}
        // the body rises with the second and drops with the third
        assertTrue(KnifeCombo.clipOf(RISE).frame(.30f).up()>KnifeCombo.clipOf(RISE).frame(WOUND[1]).up()+.15,"up with the rising cut");
        assertTrue(KnifeCombo.clipOf(CHOP).frame(.40f).up()<KnifeCombo.clipOf(CHOP).frame(WOUND[2]).up()-.3,"down with the chop");
    }
}
