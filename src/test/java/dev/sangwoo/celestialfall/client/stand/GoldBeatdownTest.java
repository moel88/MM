package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.stand.GoldBeatdown.*;
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gold Experience's beatdown as it is drawn (2.150.0): its opening punch, whether a cutscene comes of it or not; the
 * cutscene's stand, the one struck, its shots, its bars and its two bodies, all smooth at 60 fps but where it is meant
 * to cut; the moments of the video it is cut to.
 */
class GoldBeatdownTest {
    static final float FRAME=1/60f;
    static final UUID USER=new UUID(1,2),VICTIM=new UUID(3,4);
    static float clock(float t){long whole=(long)Math.floor(t*20+1e-4);GoldBeatdown.ticks=whole;StandRush.ticks=whole;return Math.max(0,t*20-whole);}
    /** The bones of a skin's skeleton (2.156.0: each skin its own, GoldSkin). */
    private static List<String> bones(GoldSkin skin)throws Exception{
        try(var in=GoldBeatdownTest.class.getResourceAsStream(skin.geo())){
            var list=new ArrayList<String>();
            for(var b:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    /** The opening punch as this client hears of it as it begins: its user at the origin facing south, someone {@code aim} blocks ahead (NaN: no one). */
    static Beat punch(float aim){clock(0);return new Beat(USER,0,0,0,aim);}
    /** A cutscene come of it: the one struck {@code ahead} blocks in front, room to hold them {@code lift} up and drive them {@code high} up, sent {@code fly} blocks on. */
    static Beat scene(double ahead,double lift,double high,double fly){
        var b=punch((float)(ahead-.3));
        b.scene=true;b.sceneAt=CUT;b.landedAt=CUT;b.victim=7;b.met=7;b.victimId=VICTIM;b.from=Vec3d.ZERO;b.victimFrom=new Vec3d(0,0,ahead);
        b.hold=new Vec3d(0,0,beatHold(ahead));b.drift=BEAT_DRIFT;b.lift=lift;b.high=high;b.land=b.hold.add(0,0,BEAT_DRIFT+fly);b.damage=45;
        return b;
    }
    static Beat scene(){return scene(2.6,BEAT_LIFT,BEAT_HIGH,BEAT_FLY);}

    /** Runs a beatdown frame by frame from the stand at rest beside its user: no bone, arm or place jumps, and it is at rest again after. */
    private void watch(Beat b,float until,String what)throws Exception{
        // (2.156.0) in every skin, each with its own skeleton, stances and fists
        for(var skin:GoldSkin.values()){b.skin=skin;watch(skin,b,until,what);}
        b.skin=GoldSkin.ORIGINAL;
    }
    private void watch(GoldSkin skin,Beat b,float until,String what)throws Exception{
        what=skin+": "+what;
        var names=bones(skin);var poses=new GoldExperiencePoses(skin);var look=new GoldExperienceLook();var shape=new GoldRush.Shape();
        float[] scratch=new float[NAMES.size()],q=new float[6];float[][] last=new float[names.size()][];
        float armL=1,armR=1,engage=0,ahead=0,raise=0,lean=0;boolean shown=false,was=false;
        for(float t=0;t<=until;t+=FRAME){
            float delta=clock(t);
            look.only(IDLE);look.armL=look.armR=1;shape.lean=0;
            boolean on=GoldBeatdown.pose(b,delta,look,shape,scratch);shown|=on;
            if(!on)shape.engage=0;
            float sum=0;for(float w:look.weights){assertTrue(w>=-1e-5,what+" at "+t);sum+=w;}
            assertEquals(1,sum,1e-4,"weights at "+t+" ("+what+")");
            for(int i=0;i<names.size();i++){
                poses.blend(names.get(i),look.weights,t,q);
                if(last[i]!=null)assertTrue(GoldRushTest.turn(last[i],q)<45,names.get(i)+" turns "+GoldRushTest.turn(last[i],q)+" degrees in a frame at "+t+" ("+what+")");
                last[i]=q.clone();
            }
            assertTrue(Math.abs(look.armR-armR)<.08&&Math.abs(look.armL-armL)<.08,"an arm jumps in size at "+t+" ("+what+")");
            assertTrue(Math.abs(shape.engage-engage)<.25,"it jumps round at "+t+" ("+what+")");
            if(on&&was){
                // (the last blow's dash is its fastest: some 0.45 blocks in two frames)
                assertTrue(Math.abs(shape.ahead-ahead)<.35,"it jumps "+(shape.ahead-ahead)+" blocks along the way at "+t+" ("+what+")");
                assertTrue(Math.abs(shape.raise-raise)<.2,"it jumps "+(shape.raise-raise)+" blocks up at "+t+" ("+what+")");
                assertTrue(Math.abs(shape.lean-lean)<4,"it leans "+(shape.lean-lean)+" degrees in a frame at "+t+" ("+what+")");
            }
            armL=look.armL;armR=look.armR;engage=shape.engage;ahead=shape.ahead;raise=shape.raise;lean=shape.lean;was=on;
        }
        assertTrue(shown,what);
        assertEquals(0,engage,.02,"back at its user's shoulder ("+what+")");assertEquals(1,armR,.01);assertEquals(1,armL,.01);
        assertEquals(1,look.weights[IDLE],.02,"at rest again ("+what+")");
    }
    @Test void itsOpeningPunchIsWoundFarBackAndLandsOnTheServersTick()throws Exception{
        assertEquals(BEAT_CUT/20f,CUT,1e-6);
        var b=punch(Float.NaN);float[] w=new float[NAMES.size()];
        stances(b,0,w);assertEquals(1,w[GUARD],1e-5,"from its guard");
        w=new float[NAMES.size()];stances(b,LOAD,w);assertEquals(1,w[OVER_WIND],1e-5,"wound up, loaded");
        w=new float[NAMES.size()];stances(b,CUT,w);assertEquals(1,w[OVER_PUNCH],1e-5,"thrown as the server's blow lands");
        // fastest as it lands
        assertTrue(strike(CUT-.01f)-strike(CUT-.03f)>strike(LOAD+.05f)-strike(LOAD+.03f),"faster and faster");
        // nothing coming of it: held out, back in its guard, back to its user's shoulder
        float until=GUARDED+LINGER+RETURN+.3f;
        watch(punch(Float.NaN),until,"no one in its way");
        var met=punch(1.4f);met.landedAt=CUT;met.met=7;met.broke=true;
        watch(met,until,"a guard broken");
        for(float at:new float[]{0,.2f,.5f,.7f}){var s=punch(1.5f);s.stopped=at;watch(s,until,"stopped at "+at);}
    }
    @Test void itsStandMovesWithoutAJumpThroughTheWholeCutsceneAt60fps()throws Exception{
        float until=END+RETURN+.6f;
        watch(scene(),until,"the cutscene");
        watch(scene(1.2,BEAT_LIFT,BEAT_HIGH,3),until,"close, sent a little way");
        watch(scene(4.1,.5,.5,BEAT_FLY),until,"under a low roof");
        watch(scene(2.6,0,0,0),until,"no room at all");
        // heard of late, its stances take over from the punch held out
        var late=scene();late.sceneAt=CUT+.3f;watch(late,until,"heard of 0.3 s late");
        // cut short at any moment
        for(float at:new float[]{CUT+.05f,3,rec(14.9f),rec(22),FINAL-.3f,FINAL+.05f}){
            var b=scene();b.ended=at;b.cut=true;
            watch(b,at+RETURN+1f,"cut short at "+at);
        }
    }
    @Test void itRushesFromThePunchUntilItDrawsBackGoesUnderThemAtTheWryyyAndThrowsTheLastOnItsBeat(){
        var b=scene();float[] w=new float[NAMES.size()];
        // the rush all the way: no guard, no punch's stance in it
        for(float t:new float[]{CUT+.5f,5,rec(12),rec(17),rec(25),WIND-.2f}){
            w=new float[NAMES.size()];stances(b,t,w);
            float rush=0;for(int i:GoldRush.LEFT)rush+=w[i];for(int i:GoldRush.RIGHT)rush+=w[i];
            assertTrue(rush>.6f,"rushing at "+t+": "+rush);
        }
        assertEquals(1,barrage(rec(10)),1e-5);assertEquals(0,barrage(WIND+.2f),1e-5);
        // its fists: one every HALF from just after the punch, the last before it draws back
        assertTrue(fistAt(0)>CUT&&fistAt(0)<CUT+.2f,"the first fist just after the punch");
        assertTrue(fistAt(FISTS-1)<=WIND+1e-4&&fistAt(FISTS-1)>WIND-.1f,"the last as it draws back");
        assertEquals(FISTS,fists(WIND+.01f));assertEquals(0,fists(CUT));
        // drawn far back and loaded, the last thrown on the video's last blow
        w=new float[NAMES.size()];stances(b,FINAL-.17f,w);assertEquals(1,w[OVER_WIND],1e-4,"wound up and loaded for the last");
        w=new float[NAMES.size()];stances(b,FINAL,w);assertEquals(1,w[OVER_PUNCH],1e-4,"thrown on the last muda");
        assertEquals(BEAT_FINAL/20f,FINAL,1e-6);assertEquals(rec(28.95f),FINAL,1e-4,"the video's 29.58 s");
        assertEquals(rec(14.65f),WRY,1e-4,"the video's 15.28 s");
        // in front of them, face to face; then under them, leaning back
        assertEquals(0,lean(b,rec(10)),1e-4);
        assertTrue(lean(b,rec(17))>LEAN-1,"leaning back under them: "+lean(b,rec(17)));
        Vec3d before=standFeet(b,rec(10)),under=standFeet(b,rec(18.5f));
        var v0=victim(b,rec(10),false);var v1=victim(b,rec(18.5f),false);
        assertTrue(Math.abs(before.y-v0.feet().y)<.5,"face to face: "+(before.y-v0.feet().y));
        assertTrue(v1.feet().y-under.y>1.0,"under them: "+(v1.feet().y-under.y));
        // its fist at full stretch lands on them, in front of them and under them (2.156.0: in every skin)
        for(var skin:GoldSkin.values()){
            b.skin=skin;
            for(float t:new float[]{rec(5),rec(10),rec(17),rec(22),rec(27)}){
                double[] reach=reach(skin,lean(b,t),ARMS*(1+GoldRush.STRETCH),RUSH_L,0);
                Vec3d fist=standFeet(b,t).add(b.f.multiply(reach[0])).add(0,reach[1],0);
                assertTrue(fist.distanceTo(target(b,t))<.05,skin+": its fist on them at "+t+": "+fist.distanceTo(target(b,t)));
            }
        }
        b.skin=GoldSkin.ORIGINAL;
        // no room to get under them: it does not lean, and never goes under the ground
        var low=scene(2.6,.5,.5,5);
        assertEquals(0,lean(low,rec(18)),1e-3);
        for(float t=CUT;t<END;t+=.05f)assertTrue(standFeet(low,t).y>=low.from.y+.049,"under the ground at "+t);
    }
    @Test void theOneStruckIsShownWithoutAJumpDrivenUpAtTheWryyyAndSentFlying(){
        for(double ahead:new double[]{1.2,2.6,4.1})for(double high:new double[]{0,1.5,BEAT_HIGH})for(double fly:new double[]{0,6,BEAT_FLY}){
            var b=scene(ahead,Math.min(BEAT_LIFT,high),high,fly);String what=ahead+" ahead, "+high+" high, sent "+fly;
            StandBeatdown.Body last=null;
            for(int f=0;f<=(int)(END/FRAME);f++){
                float t=f*FRAME;var v=t<FINAL?victim(b,t):flight(b,t);
                if(last!=null){
                    double d=v.feet().distanceTo(last.feet());
                    assertTrue(d<.75,"moves "+d+" blocks in a frame at "+t+" ("+what+")");
                    float turn=Math.max(Math.abs(v.lean()-last.lean()),Math.max(Math.abs(v.roll()-last.roll()),Math.abs(v.spin()-last.spin())));
                    assertTrue(turn<40,"turns "+turn+" degrees in a frame at "+t+" ("+what+")");
                    assertTrue(Math.abs(v.hold()-last.hold())<.2f,"turned round in a jump at "+t);
                }
                last=v;
            }
            assertEquals(450,flight(b,LAND+.25f).lean(),1e-3,"on their back once down ("+what+")");
            var up=flight(b,END);
            assertEquals(360,up.lean(),1e-3,"up again by the end");
            assertTrue(up.feet().distanceTo(b.land)<.05,"where they end ("+what+")");
        }
        var b=scene();
        assertTrue(victim(b,rec(3)).feet().y>BEAT_LIFT-.3,"held up in the air through the rush");
        assertTrue(victim(b,rec(12),false).feet().y<BEAT_LIFT+.2);
        assertEquals(BEAT_HIGH,victim(b,rec(21),false).feet().y,.15,"driven up by the WRYYY");
        assertTrue(victim(b,rec(17),false).feet().y>victim(b,rec(15.5f),false).feet().y+.8,"rising through it");
        assertEquals(-PRONE,victim(b,rec(21),false).lean(),4,"tipped over face down above the stand");
        assertTrue(victim(b,rec(14),false).feet().z-b.hold.z>BEAT_DRIFT*.8,"driven on along the way");
        var mid=flight(b,(FINAL+LAND)/2);
        assertTrue(mid.feet().z>launch(b).feet().z+6,"far on by the middle of the flight");
    }
    @Test void theCameraMovesWithinEachShotAndCutsWhereTheVideoCuts(){
        for(var b:new Beat[]{scene(),scene(1.2,BEAT_LIFT,3,4),scene(4.1,.5,.5,BEAT_FLY)}){
            StandBeatdownCamera.Shot last=null;int lastShot=-1;double fastEye=0,fastLook=0;java.util.Set<Integer> seen=new java.util.HashSet<>();
            for(int f=(int)(CUT/FRAME)+1;f<=(int)(END/FRAME);f++){
                float t=f*FRAME;var s=GoldBeatdownCamera.at(b,t);int shot=GoldBeatdownCamera.shot(t);seen.add(Math.min(shot,1000));
                assertTrue(s.look().distanceTo(s.eye())>.7,"the eye too near what it looks at at "+t+": "+s.look().distanceTo(s.eye()));
                assertTrue(s.eye().y>b.from.y+.15,"the eye in the ground at "+t);
                if(last!=null&&shot==lastShot){
                    double eye=s.eye().distanceTo(last.eye());
                    Vec3d a=s.look().subtract(s.eye()).normalize(),c=last.look().subtract(last.eye()).normalize();
                    double turn=Math.toDegrees(Math.acos(Math.min(1,a.dotProduct(c))));
                    assertTrue(eye<.35,"the eye moves "+eye+" blocks in a frame at "+t+" (shot "+shot+")");
                    assertTrue(turn<8,"the camera swings "+turn+" degrees in a frame at "+t+" (shot "+shot+")");
                    assertTrue(Math.abs(s.fov()-last.fov())<4,"the lens jumps at "+t);
                    fastEye=Math.max(fastEye,eye);fastLook=Math.max(fastLook,turn);
                }
                // the eye keeps clear of the two bodies it shows
                var v=t<FINAL?victim(b,t):flight(b,t);
                if(!s.inside())assertTrue(s.eye().distanceTo(chest(b,v))>.55,"the eye in the one struck at "+t+" (shot "+shot+"): "+s.eye().distanceTo(chest(b,v)));
                if(t<FINAL+.5f)assertTrue(s.eye().distanceTo(standPoint(b,t,.1,1.2))>.6,"the eye in the stand at "+t+" (shot "+shot+"): "+s.eye().distanceTo(standPoint(b,t,.1,1.2)));
                last=s;lastShot=shot;
            }
            for(int i=0;i<GoldBeatdownCamera.CUTS.length;i++)assertTrue(i==1?seen.contains(1000):seen.contains(i),"shot "+i+" shown");
            if(b.high==BEAT_HIGH)System.out.printf(java.util.Locale.ROOT,"gold beatdown's camera within its shots: fastest eye %.3f blocks, look %.2f degrees a frame%n",fastEye,fastLook);
        }
        for(int i=1;i<GoldBeatdownCamera.CUTS.length;i++)assertTrue(GoldBeatdownCamera.CUTS[i]>GoldBeatdownCamera.CUTS[i-1],"the cuts in order");
        // the video's cuts (0.633 s earlier than in it): from behind them at 4 s, the WRYYY at 15.28, from over them at 20.2, the last blow's at 28.28
        assertEquals(rec(4-.633f),GoldBeatdownCamera.C3,.01);assertEquals(rec(15.28f-.633f),GoldBeatdownCamera.C6,.01);
        assertEquals(rec(20.2f-.633f),GoldBeatdownCamera.C7,.02);assertEquals(rec(28.28f-.633f),GoldBeatdownCamera.C8,.01);
        // from their eyes they are not drawn
        var b=scene();
        assertTrue(GoldBeatdownCamera.at(b,rec(12)).inside());assertFalse(GoldBeatdownCamera.at(b,rec(17)).inside());
    }
    @Test void everyBlowJoltsTheCameraComingInOverTwoFrames(){
        var b=scene();StandBeatdownCamera.Jolt last=StandBeatdownCamera.Jolt.NONE;double most=0;
        for(int f=(int)(CUT/FRAME)-2;f<=(int)(END/FRAME);f++){
            float t=f*FRAME;var j=GoldBeatdownCamera.jolt(b,t);
            double d=Math.hypot(j.right()-last.right(),j.up()-last.up());
            assertTrue(d<.16,"the camera is shaken "+d+" blocks in a frame at "+t);
            assertTrue(Math.abs(j.lens()-last.lens())<9,"the lens punches in "+(j.lens()-last.lens())+" degrees in a frame at "+t);
            most=Math.max(most,Math.hypot(j.right(),j.up()));last=j;
        }
        assertTrue(most>.08,"the last blow shakes it hard: "+most);
        assertSame(StandBeatdownCamera.Jolt.NONE,GoldBeatdownCamera.jolt(b,CUT-.2f));
    }
    @Test void theBlackBarsSlideInAsThePunchLandsAndOutAsItEnds(){
        var b=scene();float last=0;
        for(int f=0;f<=(int)((END+.5f)/FRAME);f++){
            float t=f*FRAME,bar=bars(b,t);
            assertTrue(Math.abs(bar-last)<.03,"the bars jump at "+t);last=bar;
        }
        assertEquals(0,bars(b,CUT-.01f),1e-6);assertEquals(StandBeatdown.BAR,bars(b,rec(10)),1e-4);assertEquals(0,bars(b,END+.1f),1e-4);
    }
    @Test void itsUserShoutsArchesBackAtTheWryyyAndThrowsTheLastAndTheOneStruckMovesWithoutAJump(){
        var b=scene();MotionPose lastUser=null,lastStruck=null;
        for(int f=(int)(CUT/FRAME);f<=(int)(END/FRAME);f++){
            float t=f*FRAME;var u=GoldBeatdownPose.user(t);var s=GoldBeatdownPose.struck(b,t);
            if(lastUser!=null)for(int i=0;i<6;i++)for(int j=0;j<3;j++){
                assertTrue(Math.abs(wrap(u.joint[i][j]-lastUser.joint[i][j]))<30,"its user's joint "+i+" turns "+(u.joint[i][j]-lastUser.joint[i][j])+" at "+t);
                assertTrue(Math.abs(wrap(s.joint[i][j]-lastStruck.joint[i][j]))<40,"the one struck's joint "+i+" turns "+(s.joint[i][j]-lastStruck.joint[i][j])+" at "+t);
            }
            lastUser=u;lastStruck=s;
        }
        // leaning in through the rush, arched back at the WRYYY, the head thrown back
        assertTrue(GoldBeatdownPose.user(rec(5)).joint[MotionPose.BODY][0]>GoldBeatdownPose.user(rec(17)).joint[MotionPose.BODY][0]+20,"arched back at the WRYYY");
        assertTrue(GoldBeatdownPose.user(rec(17)).joint[MotionPose.HEAD][0]<GoldBeatdownPose.user(rec(5)).joint[MotionPose.HEAD][0]-10,"the head thrown back");
    }
    static float wrap(float d){d%=360;if(d>180)d-=360;if(d<-180)d+=360;return d;}
}
