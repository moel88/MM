package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.stand.StandRushTest.FRAME;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Star Platinum's beatdown (2.64.0; 2.65.0 its blow slower and bigger, the rush on through the line to one heavy blow
 * on its "DA!", the camera's new shots and the blows' jolts): its blow, its cutscene's stand from its going back in to
 * its going back to its user's shoulder, the one struck as the cutscene shows them, its shots, its black bars and its
 * two bodies, all smooth at 60 fps but where it is meant to cut: no bone of the stand turns 40 degrees in a frame while
 * it is seen, the one struck never jumps but as the blows drive them, the camera moves within each shot and cuts on the
 * beats, every blow's jolt comes in over two frames, the bars slide in and out.
 */
class StandBeatdownTest {
    static final UUID USER=new UUID(1,2),VICTIM=new UUID(3,4);
    /** Sets this client's clock (the beatdown's, the rush's) to {@code t} seconds into the beatdown; returns the frame's part of a tick. */
    static float clock(float t){long whole=(long)Math.floor(t*20+1e-4);StandBeatdown.ticks=whole;StandRush.ticks=whole;return Math.max(0,t*20-whole);}
    /**
     * A beatdown as this client hears of it the tick its blow lands: its user at the origin facing south, the one struck
     * {@code ahead} blocks in front of them, held a little further on, sent {@code fly} blocks on by the last blow.
     */
    static StandBeatdown.Beat beat(double ahead,double fly){
        clock(BEAT_CUT/20f);
        Vec3d victimFrom=new Vec3d(0,0,ahead),hold=new Vec3d(0,0,beatHold(ahead));
        return new StandBeatdown.Beat(USER,7,VICTIM,0,BEAT_CUT,0,Vec3d.ZERO,victimFrom,hold,BEAT_DRIFT,hold.add(0,0,BEAT_DRIFT+fly),60);
    }
    static StandPunch.Punch blow(){return new StandPunch.Punch(BEATDOWN,0,Float.NaN,Float.NaN);}

    /**
     * The stand's look at {@code t} as StandPresence draws it through its user's beatdown: its blow (a punch) until it is
     * sent back in, going back in from the look the blow left it in, out again at "Star Platinum!", and the cutscene's
     * own shape from the grab.
     */
    static final class Stand {
        final StandBeatdown.Beat b;final StarPlatinumLook from=new StarPlatinumLook(),look=new StarPlatinumLook();final StandPunch.Shape shape=new StandPunch.Shape(),punch=new StandPunch.Shape();
        boolean hasFrom;
        Stand(StandBeatdown.Beat b){this.b=b;}
        StarPlatinumLook at(float t){
            float delta=clock(t);
            float[] o=StandBeatdown.standOut(b,t);
            if(o==null){
                look.only(IDLE);
                var p=StandPunch.at(blow(),t,null,0,punch);
                if(p!=null&&p.engage>0){p.onto(look);look.wind=.35f*p.engage;}
                if(p!=null)look.gust=Math.max(look.gust,p.gust);
            }else if(o[0]>0)StandTimeline.at(true,t-o[1],null,look);
            else{
                if(!hasFrom){
                    // the look its blow left it in as it was sent back (StandPresence.beatFrom)
                    from.only(IDLE);var p=StandPunch.at(blow(),StandBeatdown.RETREAT,null,0,punch);
                    if(p!=null&&p.engage>0){p.onto(from);from.wind=.35f*p.engage;}
                    if(p!=null)from.gust=Math.max(from.gust,p.gust);hasFrom=true;
                }
                StandTimeline.dismiss(t-o[1],from,look);
            }
            var s=StandBeatdown.shape(b,delta,0,shape);
            if(s!=null&&(o==null||o[0]>0)&&s.engage>0){s.onto(look);look.wind=Math.max(look.wind,.35f*s.engage);}
            if(s!=null)look.gust=Math.max(look.gust,s.gust);
            return look;
        }
    }
    /** Follows the drawn body frame by frame while it is seen: no bone turns 40 degrees or more in a frame. */
    static final class Seen {
        final StarPlatinumPoses poses=new StarPlatinumPoses();final java.util.List<String> names;final float[][] last;final float[] p=new float[6];
        float fastest;String where="";
        Seen(java.util.List<String> names){this.names=names;last=new float[names.size()][];}
        void see(StarPlatinumLook look,float t,String what){
            if(look.alpha<.02f){java.util.Arrays.fill(last,null);return;}
            for(int b=0;b<names.size();b++){
                poses.pose(names.get(b),look,t,p);
                if(last[b]!=null){
                    float d=StandPunchTest.turn(names.get(b),last[b],p);
                    if(d>fastest){fastest=d;where=names.get(b)+" at "+t;}
                    assertTrue(d<40,names.get(b)+" turns "+d+" degrees in a frame at "+t+" ("+what+")");
                }
                last[b]=p.clone();
            }
        }
    }

    @Test void itsStandMovesWithoutAJumpThroughTheWholeCutsceneAt60fps()throws Exception{
        var names=StandInhaleTest.bones();
        for(double ahead:new double[]{1.2,2.6,4.1}){
            var b=beat(ahead,12);var stand=new Stand(b);var seen=new Seen(names);
            float lastPlace=Float.NaN,lastRaise=Float.NaN,lastEngage=0;double fastestPlace=0;String what="the one struck "+ahead+" blocks ahead";
            for(int f=0;f<=(int)((StandBeatdown.END+StandBeatdown.RETURN+.3f)/FRAME);f++){
                float t=f*FRAME;var look=stand.at(t);
                seen.see(look,t,what);
                var s=StandBeatdown.shape(b,clock(t),0,new StandPunch.Shape());
                if(s!=null){
                    assertTrue(Math.abs(s.engage-lastEngage)<.25f,"comes round in a jump at "+t+" ("+what+")");lastEngage=s.engage;
                    float place=s.ahead+s.lunge;
                    if(!Float.isNaN(lastPlace)){
                        assertTrue(Math.abs(place-lastPlace)<.4f,"moves "+(place-lastPlace)+" blocks in a frame at "+t+" ("+what+")");
                        assertTrue(Math.abs(s.raise-lastRaise)<.2f,"rises "+(s.raise-lastRaise)+" blocks in a frame at "+t+" ("+what+")");
                        fastestPlace=Math.max(fastestPlace,Math.abs(place-lastPlace));
                    }
                    lastPlace=place;lastRaise=s.raise;
                }else{lastPlace=Float.NaN;lastEngage=0;}
            }
            System.out.printf(java.util.Locale.ROOT,"%s: the stand's fastest turn in a frame %.1f (%s), its fastest move %.3f blocks%n",what,seen.fastest,seen.where,fastestPlace);
            assertTrue(seen.fastest>15,"its fists are fast: "+seen.fastest);
        }
    }
    @Test void itGoesBackInAfterItsBlowAndBurstsOutAtStarPlatinum(){
        var b=beat(2.6,12);var stand=new Stand(b);
        assertNull(StandBeatdown.standOut(b,StandBeatdown.RETREAT-.01f),"its blow holds first");
        assertTrue(stand.at(StandBeatdown.RETREAT+StandTimeline.GONE+.05f).alpha<.01f,"gone back into its user");
        assertTrue(stand.at(StandBeatdown.REVEAL-.05f).alpha<.01f,"in its user while they say yare yare daze");
        assertEquals(1,stand.at(StandBeatdown.REVEAL+.5f).alpha,1e-3,"out at Star Platinum!");
        assertEquals(BEAT_REVEAL/20f,StandBeatdown.REVEAL,1e-6);
        // the look it goes back with is the blow's, as it held: nothing jumps as it goes
        var held=new Stand(b).at(StandBeatdown.RETREAT-.001f);var going=new Stand(b).at(StandBeatdown.RETREAT+.001f);
        for(int i=0;i<held.weights.length;i++)assertEquals(held.share(i),going.share(i),.02f,"stance "+NAMES.get(i)+" as it goes back");
        // through the grab, the uppercut, the rush through the line and the last blow it is in front of its user, out by the grab
        for(float t:new float[]{StandBeatdown.GRAB,StandBeatdown.UPPER,StandBeatdown.RUSH_FROM+1,StandBeatdown.JUDGE,StandBeatdown.MINE,StandBeatdown.FINAL}){
            var s=StandBeatdown.shape(b,clock(t),0,new StandPunch.Shape());
            assertNotNull(s,"its shape at "+t);assertTrue(s.engage>.98f,"round in front at "+t);
        }
        // and goes back to its user's shoulder once it is over
        assertNull(StandBeatdown.shape(b,clock(StandBeatdown.END+StandBeatdown.RETURN+.2f),0,new StandPunch.Shape()),"back at its user's shoulder");
    }
    @Test void itGripsTheCollarUppercutsRushesAndThrowsItsLastBlow(){
        var b=beat(2.6,12);var w=new StandPunch.Shape();
        StandBeatdown.shape(b,clock(StandBeatdown.GRAB),0,w);assertEquals(1,w.weights[COLLAR],1e-3,"the collar gripped");
        StandBeatdown.shape(b,clock(StandBeatdown.UPPER+.01f),0,w);assertEquals(1,w.weights[UPPERCUT],1e-3,"the uppercut thrown");
        // the rush: its fists out left and right in turn
        float left=0,right=0;
        for(float t=StandBeatdown.RUSH_FROM;t<StandBeatdown.RUSH_TO;t+=.01f){
            StandBeatdown.shape(b,clock(t),0,w);
            for(int i:StandRush.LEFT)left=Math.max(left,w.weights[i]);for(int i:StandRush.RIGHT)right=Math.max(right,w.weights[i]);
            assertTrue(w.armL>1.05f&&w.armR>1.05f,"its arms bigger through the rush at "+t);
        }
        assertTrue(left>.9f&&right>.9f,"both fists out in turn");
        for(int i=0;i<StandBeatdown.STRIKES.length;i++){
            StandBeatdown.shape(b,clock(StandBeatdown.STRIKES[i]+.001f),0,w);
            assertTrue(w.weights[StandBeatdown.STRIKE_STANCE[i]]>.97f,"strike "+i+" thrown as it lands: "+w.weights[StandBeatdown.STRIKE_STANCE[i]]);
        }
        StandBeatdown.shape(b,clock(StandBeatdown.FINAL+.3f),0,w);assertEquals(1,w.weights[PUNCH_HEAVY],1e-3,"its last blow held out as they fly");
        StandBeatdown.shape(b,clock(StandBeatdown.LEAVE),0,w);assertEquals(1,w.weights[GUARD],1e-3,"in its guard as it goes back");
        assertEquals(1,w.armL,1e-3);assertEquals(1,w.armR,1e-3);
        // its afterimage arms only through the rush
        var ghost=new StarPlatinumLook();
        assertEquals(0,StandBeatdown.phantom(b,0,clock(StandBeatdown.UPPER),ghost),1e-6);
        assertTrue(StandBeatdown.phantom(b,0,clock(StandBeatdown.RUSH_FROM+1),ghost)>.1f,"its afterimage arms in the rush");
        assertTrue(StandBeatdown.phantom(b,0,clock(StandBeatdown.MINE),ghost)>.1f,"on through the line");
        assertEquals(0,StandBeatdown.phantom(b,0,clock(StandBeatdown.FINAL-.3f),ghost),1e-6,"none as it draws back for the last blow");
    }
    @Test void theOneStruckIsShownWithoutAJumpButAsTheBlowsDriveThem(){
        for(double ahead:new double[]{1.2,2.6,4.1})for(double fly:new double[]{0,4,12.7}){
            var b=beat(ahead,fly);String what=ahead+" blocks ahead, sent "+fly+" blocks";
            StandBeatdown.Body last=null;double fastest=0,turnFastest=0;float fastAt=0;
            for(int f=0;f<=(int)(StandBeatdown.END/FRAME);f++){
                float t=f*FRAME;var v=t<StandBeatdown.FINAL?StandBeatdown.victim(b,t):StandBeatdown.flight(b,t);
                if(last!=null){
                    double d=v.feet().distanceTo(last.feet());
                    // the fastest are the last blow sending them flying (some 25 blocks a second as they go) and the rush's jolts
                    assertTrue(d<.6,"moves "+d+" blocks in a frame at "+t+" ("+what+")");
                    // (2.65.0: the uppercut snaps their head back harder, over two frames: under the stand's 40)
                    float turn=Math.max(Math.abs(v.lean()-last.lean()),Math.max(Math.abs(v.roll()-last.roll()),Math.abs(v.spin()-last.spin())));
                    assertTrue(turn<40,"turns "+turn+" degrees in a frame at "+t+" ("+what+")");
                    assertTrue(Math.abs(v.hold()-last.hold())<.2f,"turned round to face its user in a jump at "+t);
                    if(d>fastest){fastest=d;fastAt=t;}turnFastest=Math.max(turnFastest,turn);
                }
                last=v;
            }
            if(fly==12.7&&ahead==2.6)System.out.printf(java.util.Locale.ROOT,"the one struck: fastest %.3f blocks a frame (at %.2f s), fastest turn %.1f degrees%n",fastest,fastAt,turnFastest);
            // they end where the server puts them down, lying, then up
            var down=StandBeatdown.flight(b,StandBeatdown.LAND+.25f);
            assertEquals(450,down.lean(),1e-3,"on their back once down ("+what+")");
            var up=StandBeatdown.flight(b,StandBeatdown.END);
            assertEquals(360,up.lean(),1e-3,"up again by the end");
            assertTrue(up.feet().distanceTo(b.land)<.05,"where they end: "+up.feet()+" for "+b.land+" ("+what+")");
        }
    }
    @Test void theUppercutThrowsThemUpAndTheLastBlowSendsThemFlying(){
        var b=beat(2.6,12);
        double ground=StandBeatdown.victim(b,StandBeatdown.UPPER-.01f).feet().y;
        double air=StandBeatdown.victim(b,StandBeatdown.UPPER+.4f).feet().y;
        assertTrue(air-ground>1.1,"up in the air: "+(air-ground));
        assertTrue(StandBeatdown.victim(b,StandBeatdown.RUSH_FROM+2).feet().y>1,"held up there through the rush");
        // driven on by the rush
        double held=StandBeatdown.victim(b,StandBeatdown.RUSH_FROM).feet().z,last=StandBeatdown.launch(b).feet().z;
        assertTrue(last-held>BEAT_DRIFT*.8,"driven on "+(last-held)+" blocks before the last blow");
        // flying: high in the middle of the way
        var mid=StandBeatdown.flight(b,(StandBeatdown.FINAL+StandBeatdown.LAND)/2);
        assertTrue(mid.feet().y>StandBeatdown.launch(b).feet().y,"arcs up as it flies");
        assertTrue(mid.feet().z>last+4,"far on by the middle of the flight");
    }
    @Test void theCameraMovesWithinEachShotAndCutsOnTheBeats(){
        var b=beat(2.6,12);
        StandBeatdownCamera.Shot last=null;int lastShot=-1;double fastEye=0,fastLook=0,fastFov=0;
        for(int f=(int)(StandBeatdown.CUT/FRAME)+1;f<=(int)(StandBeatdown.END/FRAME);f++){
            float t=f*FRAME;var s=StandBeatdownCamera.at(b,t);
            int shot=0;while(shot+1<StandBeatdown.CUTS.length&&t>=StandBeatdown.CUTS[shot+1])shot++;
            assertTrue(s.look().distanceTo(s.eye())>.8,"the eye too near what it looks at at "+t);
            // (2.66.0: never at a point in the floor under them as they lie: the eye, kept out of the walls along the
            // way from what it looks at, was pulled right down onto it)
            if(shot==StandBeatdown.CUTS.length-1)assertTrue(s.look().y>=b.land.y+.4,"looking into the floor at "+t);
            if(last!=null&&shot==lastShot){
                double eye=s.eye().distanceTo(last.eye());
                Vec3d a=s.look().subtract(s.eye()).normalize(),c=last.look().subtract(last.eye()).normalize();
                double turn=Math.toDegrees(Math.acos(Math.min(1,a.dotProduct(c))));
                assertTrue(eye<.3,"the eye moves "+eye+" blocks in a frame at "+t+" (shot "+shot+")");
                assertTrue(turn<8,"the camera swings "+turn+" degrees in a frame at "+t+" (shot "+shot+")");
                assertTrue(Math.abs(s.fov()-last.fov())<4,"the lens jumps at "+t);
                fastEye=Math.max(fastEye,eye);fastLook=Math.max(fastLook,turn);fastFov=Math.max(fastFov,Math.abs(s.fov()-last.fov()));
            }
            last=s;lastShot=shot;
        }
        assertEquals(StandBeatdown.CUTS.length-1,lastShot,"every shot shown");
        for(int i=1;i<StandBeatdown.CUTS.length;i++)assertTrue(StandBeatdown.CUTS[i]>StandBeatdown.CUTS[i-1],"the cuts in order");
        System.out.printf(java.util.Locale.ROOT,"the camera within its shots: fastest eye %.3f blocks, look %.2f degrees, lens %.2f degrees a frame%n",fastEye,fastLook,fastFov);
        // the line's words each have a shot of their own, and the last blow cuts in as it lands
        for(float at:new float[]{StandBeatdown.JUDGE,StandBeatdown.MINE,StandBeatdown.FINAL}){
            boolean cut=false;for(float c:StandBeatdown.CUTS)cut|=Math.abs(c-at)<1e-4;
            assertTrue(cut,"a cut at "+at);
        }
    }
    /**
     * The camera's eye keeps clear of the bodies and the fists from the rush on (2.66.0, the shots from the user's video
     * close in among them): of every arm of the stand and of its afterimages as drawn (from the shoulder to the fist,
     * 0.3 blocks: the fists come right up to the lens from the one struck's eyes but never cut through it), of the
     * stand's body and head (its mane) and, where they are drawn, of the one struck's, however each blow shakes it.
     * (2.65.0's first capture's "俺の" shot sat right beside the one struck, where the fists fly, and showed nothing but
     * a dark blur of them.)
     */
    @Test void theCameraKeepsClearOfTheBodiesAndTheFists(){
        var b=beat(2.6,12);
        int n=StandBeatdown.CUTS.length;double[] arms=new double[n],stand=new double[n],victim=new double[n];
        java.util.Arrays.fill(arms,99);java.util.Arrays.fill(stand,99);java.util.Arrays.fill(victim,99);
        boolean[] inside=new boolean[n];
        for(int f=(int)(StandBeatdown.CUTS[6]/FRAME)+1;f<=(int)(StandBeatdown.CUTS[n-1]/FRAME);f++){
            float t=f*FRAME;var s=StandBeatdownCamera.at(b,t);var j=StandBeatdownCamera.jolt(b,t);
            int shot=0;while(shot+1<n&&t>=StandBeatdown.CUTS[shot+1])shot++;
            // (each blow shakes the eye this far from the shot's)
            double shake=Math.hypot(j.right(),j.up()),eyeArms=99;
            for(var a:arms(b,t))eyeArms=Math.min(eyeArms,fromSegment(s.eye(),a[0],a[1]));
            arms[shot]=Math.min(arms[shot],eyeArms-shake);
            Vec3d feet=StandBeatdown.standFeet(b,t);
            stand[shot]=Math.min(stand[shot],fromSegment(s.eye(),feet.add(0,.4,0),StandBeatdownCamera.crown(b,feet).add(0,.2,0))-shake);
            inside[shot]|=s.inside();
            if(!s.inside()){
                var v=t<StandBeatdown.FINAL?StandBeatdown.victim(b,t):StandBeatdown.flight(b,t);
                victim[shot]=Math.min(victim[shot],fromSegment(s.eye(),v.feet().add(0,.2,0),StandBeatdownCamera.head(b,v))-shake);
            }
        }
        for(int i=6;i<n-1;i++){
            System.out.printf(java.util.Locale.ROOT,"shot %d (from %.2f s)%s: the eye at least %.2f blocks from an arm, %.2f from the stand, %.2f from the one struck%n",
                i,StandBeatdown.CUTS[i],inside[i]?" from their eyes":"",arms[i],stand[i],victim[i]);
            // (from their eyes the fists come right up to the lens; elsewhere an arm this near fills the frame: the
            // first capture's close-up of its face, among its afterimages' arms, showed nothing but a purple blur)
            assertTrue(arms[i]>=(inside[i]?.3:.4),"shot "+i+": the eye "+arms[i]+" blocks from an arm");
            assertTrue(stand[i]>=.55,"shot "+i+": the eye "+stand[i]+" blocks from the stand's body");
            assertTrue(inside[i]||victim[i]>=.5,"shot "+i+": the eye "+victim[i]+" blocks from the one struck");
        }
        assertTrue(inside[7]&&inside[8],"its fists into the lens from the one struck's eyes");
    }
    /** The stand's arms where they are drawn at {@code t} once it is round in front, and its afterimages' through the rush (each from its shoulder to its fist, world). */
    static java.util.List<Vec3d[]> arms(StandBeatdown.Beat b,float t){
        var out=new java.util.ArrayList<Vec3d[]>();float delta=clock(t);
        var s=StandBeatdown.shape(b,delta,0,new StandPunch.Shape());if(s==null||s.engage<.98f)return out;
        Vec3d feet=StandBeatdown.standFeet(b,t);
        float[] w=new float[s.weights.length];for(int i=0;i<w.length;i++)w[i]=s.share(i);
        arm(out,w,feet,b.f,s.armL,s.armR,1,1);
        var look=new StarPlatinumLook();
        for(int i=0;i<StandRush.phantoms();i++){
            if(StandBeatdown.phantom(b,i,delta,look)<=.02f)continue;
            Vec3d at=feet.add(b.r.multiply(StandRush.PHANTOMS[i][1])).add(0,StandRush.PHANTOMS[i][2],0);
            arm(out,look.weights,at,Vec3d.fromPolar(0,b.yaw+StandRush.PHANTOMS[i][3]),look.armL,look.armR,look.showL,look.showR);
        }
        return out;
    }
    static void arm(java.util.List<Vec3d[]> out,float[] w,Vec3d feet,Vec3d f,float sizeL,float sizeR,float showL,float showR){
        Vec3d r=new Vec3d(-f.z,0,f.x);
        for(int side=0;side<2;side++){
            if((side==0?showL:showR)<.05f)continue;
            float[] fist=new float[3],shoulder=new float[3];
            for(int i=0;i<w.length;i++){
                if(w[i]==0)continue;float[] g=StandPunch.grown(i,side,side==0?sizeL:sizeR),q=StandPunch.SHOULDERS[i][side];
                for(int k=0;k<3;k++){fist[k]+=w[i]*g[k];shoulder[k]+=w[i]*q[k];}
            }
            out.add(new Vec3d[]{on(feet,f,r,shoulder),on(feet,f,r,fist)});
        }
    }
    static Vec3d on(Vec3d feet,Vec3d f,Vec3d r,float[] p){return feet.add(f.multiply(p[0])).add(r.multiply(p[1])).add(0,p[2],0);}
    /** How far {@code p} is from the segment from {@code a} to {@code c}. */
    static double fromSegment(Vec3d p,Vec3d a,Vec3d c){
        Vec3d d=c.subtract(a);double k=Math.max(0,Math.min(1,p.subtract(a).dotProduct(d)/Math.max(1e-9,d.lengthSquared())));
        return p.distanceTo(a.add(d.multiply(k)));
    }
    @Test void everyBlowJoltsTheCameraComingInOverTwoFrames(){
        var b=beat(2.6,12);
        assertEquals(0,StandBeatdownCamera.jolt(b,StandBeatdown.FINAL-.02f).lens(),.05,"still just before the last blow");
        assertTrue(StandBeatdownCamera.jolt(b,StandBeatdown.FINAL+.02f).lens()>8,"the lens punches in as it lands");
        assertTrue(StandBeatdownCamera.jolt(b,StandBeatdown.UPPER+.02f).lens()>4,"and at the uppercut");
        assertTrue(StandBeatdownCamera.jolt(b,StandBeatdown.blowAt(20)+.01f).lens()>.6,"a little at every blow of the rush");
        double last=0,fastest=0,most=0;float roll=0;
        for(int f=(int)(StandBeatdown.CUT/FRAME);f<=(int)(StandBeatdown.END/FRAME);f++){
            float t=f*FRAME;var j=StandBeatdownCamera.jolt(b,t);
            fastest=Math.max(fastest,Math.abs(j.lens()-last));last=j.lens();
            most=Math.max(most,Math.hypot(j.right(),j.up()));roll=Math.max(roll,Math.abs(j.roll()));
        }
        assertTrue(fastest<10,"the lens comes in over two frames, never all in one: "+fastest);
        assertTrue(most<.3,"shaken at most "+most+" blocks");assertTrue(roll<6,"rolled at most "+roll+" degrees");
        assertEquals(0,StandBeatdownCamera.jolt(b,StandBeatdown.END).lens(),.1,"still again by the end");
        System.out.printf(java.util.Locale.ROOT,"the jolts: the lens at most %.2f degrees a frame, the eye shaken %.3f blocks, rolled %.2f degrees%n",fastest,most,roll);
    }
    @Test void theBlackBarsSlideInAsTheBlowLandsAndOutAsItEnds(){
        var b=beat(2.6,12);
        assertEquals(0,StandBeatdown.bars(b,StandBeatdown.CUT),1e-6,"none before the blow lands");
        float top=0,last=0;boolean settled=true;
        for(float t=StandBeatdown.CUT;t<=StandBeatdown.END+.1f;t+=FRAME){
            float h=StandBeatdown.bars(b,t);
            assertTrue(Math.abs(h-last)<.03f,"the bars jump at "+t);
            top=Math.max(top,h);last=h;
            if(t>StandBeatdown.CUT+StandBeatdown.BARS_IN&&t<StandBeatdown.END-StandBeatdown.BARS_OUT)settled&=Math.abs(h-StandBeatdown.BAR)<1e-4;
        }
        assertTrue(settled,"held still between");
        assertTrue(top>StandBeatdown.BAR&&top<StandBeatdown.BAR*1.15f,"slightly past and settling as they come in: "+top);
        assertEquals(0,StandBeatdown.bars(b,StandBeatdown.END+.06f),1e-4,"gone as it ends");
    }
    @Test void itsUserHoldsTheirHeadAndShakesItAndTheOneStruckMovesWithoutAJump(){
        var b=beat(2.6,12);
        MotionPose last=null,lastStruck=null;float fastest=0,fastestStruck=0;
        for(float t=.3f;t<=StandBeatdown.END;t+=FRAME){
            var p=BeatdownPose.user(t);var q=BeatdownPose.struck(b,t);
            if(last!=null){fastest=Math.max(fastest,change(last,p));fastestStruck=Math.max(fastestStruck,change(lastStruck,q));}
            assertTrue(change(last==null?p:last,p)<20,"its user's body jumps "+change(last==null?p:last,p)+" degrees at "+t);
            assertTrue(t<StandBeatdown.CUT+.05f||change(lastStruck==null?q:lastStruck,q)<30,"the one struck's body jumps "+change(lastStruck==null?q:lastStruck,q)+" degrees at "+t);
            last=p;lastStruck=q;
        }
        System.out.printf(java.util.Locale.ROOT,"its user's fastest joint in a frame: %.1f degrees; the one struck's %.1f%n",fastest,fastestStruck);
        // the hand up on the head through yare yare daze (on its forehead, as the rig puts it), the head shaking under it
        var rig=new dev.sangwoo.celestialfall.client.MotionPlayerModel(false);float farthest=0;
        for(float t=StandBeatdown.rec(1.08f);t<=StandBeatdown.rec(2.47f);t+=.01f){
            var p=BeatdownPose.user(t);dev.sangwoo.celestialfall.client.MotionRig.reset(rig);dev.sangwoo.celestialfall.client.MotionRig.apply(rig,p,0,0);
            var arm=new net.minecraft.client.util.math.MatrixStack();rig.rightArm.rotate(arm);rig.rightArm.getChild("elbow").rotate(arm);
            var hand=arm.peek().getPositionMatrix().transformPosition(new org.joml.Vector3f(-1/16f,6.5f/16,0)).mul(16);
            var head=new net.minecraft.client.util.math.MatrixStack();rig.head.rotate(head);
            // (model pixels: x the left, y down, z back; the forehead's front a little right of the middle)
            var brow=head.peek().getPositionMatrix().transformPosition(new org.joml.Vector3f(-1.2f/16,-6.3f/16,-4.6f/16)).mul(16);
            farthest=Math.max(farthest,hand.distance(brow));
            assertTrue(hand.distance(brow)<2.6f,"the hand off the forehead by "+hand.distance(brow)+" pixels at "+t);
        }
        System.out.printf(java.util.Locale.ROOT,"the hand at most %.2f pixels from the forehead while it holds it%n",farthest);
        float least=0,most=0;
        for(float t=StandBeatdown.rec(1.12f);t<StandBeatdown.rec(1.98f);t+=.01f){float s=BeatdownPose.shake(t);least=Math.min(least,s);most=Math.max(most,s);}
        assertTrue(most>12&&least<-12,"the head shakes from side to side: "+least+" to "+most);
        assertEquals(0,BeatdownPose.shake(StandBeatdown.rec(.35f)),1e-6,"still before yare yare");assertEquals(0,BeatdownPose.shake(StandBeatdown.rec(2.2f)),1e-6,"and after it");
        var point=BeatdownPose.user(StandBeatdown.rec(3f));
        assertTrue(point.elbow[0]>-20,"the arm flung out straight at Star Platinum!");
        // 2.65.0: pointing at the one struck again through "裁くのは俺のスタンドだ"
        var judge=BeatdownPose.user(StandBeatdown.MINE);
        assertTrue(judge.elbow[0]>-10&&change(BeatdownPose.user(StandBeatdown.JUDGE-.15f),judge)>40,"its arm up pointing at them through the line");
    }
    /** The largest turn of any of a body's joints from one frame to the next (degrees: the angle between the two whole turns, as the model part turns, roll-yaw-pitch). */
    static float change(MotionPose a,MotionPose b){
        float m=0;
        for(int i=0;i<6;i++){
            float[] x=a.joint[i],y=b.joint[i];float k=(float)Math.PI/180;
            var qa=new org.joml.Quaternionf().rotationZYX(x[2]*k,x[1]*k,x[0]*k);var qb=new org.joml.Quaternionf().rotationZYX(y[2]*k,y[1]*k,y[0]*k);
            m=Math.max(m,(float)Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(qa.dot(qb))))));
        }
        for(int i=0;i<2;i++){m=Math.max(m,Math.abs(a.elbow[i]-b.elbow[i]));m=Math.max(m,Math.abs(a.knee[i]-b.knee[i]));}
        return m;
    }
    @Test void itsBlowMovesWithoutAJumpAndLandsOnTheRaOfItsOra()throws Exception{
        var names=StandInhaleTest.bones();var seen=new Seen(names);var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        for(int f=0;f<=(int)(1.6f/FRAME);f++){
            float t=f*FRAME;var s=StandPunch.at(blow(),t,null,0,shape);
            StandRushTest.look(s,look);seen.see(look,t,"the beatdown's blow");
        }
        assertEquals(StarPlatinumRules.CONTACT[BEATDOWN]/20f,StandPunch.HIT[BEATDOWN],1e-6,"it lands on its contact");
        StandPunch.at(blow(),StandPunch.HIT[BEATDOWN],null,0,shape);assertTrue(shape.weights[BEAT_STRIKE]>.97f,"thrown as it lands");
        // 2.65.0: drawn back far and held there a while before it strikes, slower than the heavy punch, so it can be seen coming
        StandPunch.at(blow(),.55f,null,0,shape);assertTrue(shape.weights[BEAT_WIND]>.97f,"coiled before it strikes");
        assertTrue(StandPunch.HIT[BEATDOWN]>StandPunch.HIT[HEAVY],"slower than the heavy punch");
        assertTrue(seen.fastest>15,"a fast blow: "+seen.fastest);
        System.out.println("the beatdown's blow's fastest turn in a frame: "+seen.fastest+" ("+seen.where+")");
    }
}
