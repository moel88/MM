package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.xchara.SlamRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's G with their hack off, the slam, as drawn (2.128.0, the user's video and words: "검이 q활성화 상태 색깔로
 * 변하고 보라색 네온 빛 원을 소환하고 공중에 올라가서 내가 보는 방향으로 내려찍는"): the knife is the hack's from just after
 * the press until it is over; the circle blinks on a third of a second in and out as it ends, and is cut round at
 * its rim; the squares of light rise off it from the leap and are gone with it; the body is held to the plan from the
 * leap until a little after the landing; nothing of the clip steps from one frame to the next; and the landing's
 * burst and its crater are there and gone.
 */
class OverwriteSlamTest {
    static final float NONE=Float.NaN;

    @Test void itsTimesAreTheServers(){
        assertEquals(SlamRules.LIT/20f,OverwriteSlam.LIT,1e-6);assertEquals(SlamRules.CIRCLE/20f,OverwriteSlam.CIRCLE,1e-6);assertEquals(SlamRules.LEAP/20f,OverwriteSlam.LEAP,1e-6);
        assertEquals(SlamRules.DIVE/20f,OverwriteSlam.DIVE,1e-6);assertEquals(SlamRules.LAND/20f,OverwriteSlam.LAND,1e-6);assertEquals(SlamRules.END/20f,OverwriteSlam.END,1e-6);
        assertEquals(LENGTH[SLAM]/20f,OverwriteSlam.CLIP.length(),1e-4,"the clip is as long as the server holds its motion");
        assertSame(OverwriteSlam.CLIP,KnifeCombo.clipOf(SLAM));assertEquals(OverwriteSlam.LIFE,KnifeCombo.life(SLAM),1e-6);
        assertSame(GiantCleave.CLIP,KnifeCombo.clipOf(CLEAVE));assertSame(CorruptionJab.CLIP,KnifeCombo.clipOf(JAB));
        assertTrue(OverwriteSlam.LIFE>=OverwriteSlam.LAND+OverwriteSlam.GROUND_LIFE,"what the landing does to the floor is drawn until it is gone");
        assertTrue(new KnifeCombo().lifetime()>=OverwriteSlam.LIFE);
    }
    @Test void theKnifeIsTheHacksFromThePressUntilItIsOver(){
        assertNull(OverwriteSlam.lit(0,NONE),"its own black as G is pressed");
        float[] on=OverwriteSlam.lit(OverwriteSlam.LIT,NONE);assertNotNull(on);assertEquals(1,on[0],0);assertEquals(0,on[1],1e-6);
        for(float t=OverwriteSlam.LIT;t<OverwriteSlam.END-1e-4f;t+=1/120f){float[] l=OverwriteSlam.lit(t,NONE);assertNotNull(l,"lit at "+t);assertEquals(1,l[0],0);assertEquals(t-OverwriteSlam.LIT,l[1],1e-5);}
        // (the video: the knife is black again as the circle goes) it goes out as the hack does
        float[] off=OverwriteSlam.lit(OverwriteSlam.END+.05f,NONE);assertNotNull(off);assertEquals(0,off[0],0);assertEquals(.05f,off[1],1e-5);
        assertNull(OverwriteSlam.lit(OverwriteSlam.END+HackLook.OFF+.01f,NONE));
        // stopped short: out from then
        float[] cut=OverwriteSlam.lit(.62f,.6f);assertNotNull(cut);assertEquals(0,cut[0],0);assertEquals(.02f,cut[1],1e-5);
        assertEquals(1,OverwriteSlam.lit(.59f,.6f)[0],0);assertNull(OverwriteSlam.lit(.6f+HackLook.OFF+.01f,.6f));
        assertNull(OverwriteSlam.lit(.3f,.02f),"stopped before it lit: never lit");
    }
    @Test void theCircleBlinksOnAndOut(){
        assertEquals(0,OverwriteSlam.circle(OverwriteSlam.CIRCLE-.01f,NONE),0);assertEquals(1,OverwriteSlam.circle(OverwriteSlam.CIRCLE+.01f,NONE),0);
        assertEquals(0,OverwriteSlam.circle(OverwriteSlam.CIRCLE+.07f,NONE),0,"one blink as it comes");assertEquals(1,OverwriteSlam.circle(OverwriteSlam.CIRCLE+.1f,NONE),0);
        for(float t=OverwriteSlam.CIRCLE+.1f;t<OverwriteSlam.END-.1f;t+=1/60f)assertEquals(1,OverwriteSlam.circle(t,NONE),0,"there at "+t);
        assertEquals(0,OverwriteSlam.circle(OverwriteSlam.END-.06f,NONE),0,"one blink as it goes");assertEquals(1,OverwriteSlam.circle(OverwriteSlam.END-.02f,NONE),0);
        assertEquals(0,OverwriteSlam.circle(OverwriteSlam.END,NONE),0);assertEquals(0,OverwriteSlam.circle(OverwriteSlam.END+1,NONE),0);
        // stopped short, gone at once
        assertEquals(1,OverwriteSlam.circle(.79f,.8f),0);assertEquals(0,OverwriteSlam.circle(.8f,.8f),0);
        // it is there before the leap and all through the air
        assertTrue(OverwriteSlam.CIRCLE<OverwriteSlam.LEAP&&OverwriteSlam.END>OverwriteSlam.LAND);
    }
    @Test void aBlocksTopIsCutRoundAtTheRim(){
        // well inside: the whole square
        double[][] whole=OverwriteSlam.cut(2,3,0,0,18);assertEquals(4,whole.length);
        // well outside: nothing
        assertNull(OverwriteSlam.cut(30,0,0,0,18));assertNull(OverwriteSlam.cut(14,14,0,0,18));
        // on the rim: what is left lies within the circle (the tangent's cut, a hair over at most), and is some of the square
        int cutOnes=0;
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
            double[][] p=OverwriteSlam.cut(x,z,.3,.7,18);if(p==null)continue;
            double area=0;
            for(int i=0;i<p.length;i++){
                double[] a=p[i],b=p[(i+1)%p.length];area+=a[0]*b[1]-b[0]*a[1];
                assertTrue(Math.hypot(a[0]-.3,a[1]-.7)<=18+.03,"a corner outside the circle at "+x+","+z);
                assertTrue(a[0]>=x-1e-9&&a[0]<=x+1+1e-9&&a[1]>=z-1e-9&&a[1]<=z+1+1e-9,"a corner outside its block");
            }
            area=Math.abs(area)/2;assertTrue(area>1e-6&&area<=1+1e-9,"an area of "+area);
            if(area<1-1e-9)cutOnes++;
        }
        assertTrue(cutOnes>=80,"the rim's blocks are cut: "+cutOnes);
        // all its pieces together are the circle's area
        double all=0;
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
            double[][] p=OverwriteSlam.cut(x,z,.3,.7,18);if(p==null)continue;double area=0;
            for(int i=0;i<p.length;i++){double[] a=p[i],b=p[(i+1)%p.length];area+=a[0]*b[1]-b[0]*a[1];}
            all+=Math.abs(area)/2;
        }
        assertEquals(Math.PI*18*18,all,Math.PI*18*18*.01,"the circle's area within a hundredth");
    }
    @Test void theSquaresOfLightRiseOffTheCircleFromTheLeapAndAreGoneWithIt(){
        int seed=1234,early=0,most=0;
        for(int j=0;j<OverwriteSlam.CUBES;j++){
            assertNull(OverwriteSlam.cube(j,seed,OverwriteSlam.LEAP-.2f),"none before the leap");
            if(OverwriteSlam.cube(j,seed,OverwriteSlam.LEAP+.5f)!=null)early++;
            double[] end=OverwriteSlam.cube(j,seed,OverwriteSlam.END);assertTrue(end==null||end[7]<=1e-6,"gone as the circle goes");
            double high=-1;double[] was=null;
            for(float t=OverwriteSlam.LEAP-.1f;t<OverwriteSlam.END;t+=1/60f){
                double[] q=OverwriteSlam.cube(j,seed,t);if(q==null){was=null;continue;}
                assertTrue(Math.hypot(q[0],q[1])<=SlamRules.RADIUS-1,"within the circle");assertTrue(q[2]>0,"over the ground");
                assertTrue(q[7]>=0&&q[7]<=1);assertTrue(q[3]>.05&&q[4]>.05&&q[5]>.05&&q[3]<1.2&&q[4]<1.6&&q[5]<1.2,"a box of a sensible size");
                if(was!=null){assertTrue(q[2]>=was[2]-1e-9,"it rises");assertTrue(q[2]-was[2]<.12,"a step of "+(q[2]-was[2]));assertTrue(Math.abs(q[7]-was[7])<.25,"it pops");}
                high=Math.max(high,q[2]);was=q;
            }
            assertTrue(high>1,"square "+j+" rises");
        }
        assertTrue(early>=30,"a flock comes with the leap: "+early);
        for(float t=OverwriteSlam.LEAP+.6f;t<OverwriteSlam.LAND;t+=.1f){
            int n=0;for(int j=0;j<OverwriteSlam.CUBES;j++){var q=OverwriteSlam.cube(j,seed,t);if(q!=null&&q[7]>.5)n++;}
            assertTrue(n>=25,"squares in the air at "+t+": "+n);most=Math.max(most,n);
        }
        assertTrue(most<=OverwriteSlam.CUBES);
    }
    @Test void theBodyIsHeldToThePlanFromTheLeapUntilALittleAfterTheLanding(){
        assertEquals(0,OverwriteSlam.held(0),0);assertEquals(0,OverwriteSlam.held(SlamRules.LEAP),0);
        for(float a=SlamRules.LEAP+.01f;a<=SlamRules.LAND+4;a+=.5f)assertEquals(1,OverwriteSlam.held(a),0,"held at "+a);
        assertEquals(0,OverwriteSlam.held(SlamRules.LAND+10),0);assertTrue(SlamRules.LAND+10<=SlamRules.FREE,"let go before they are free to walk");
        float was=1;for(float a=SlamRules.LAND+4;a<=SlamRules.LAND+10;a+=.1f){float h=OverwriteSlam.held(a);assertTrue(h<=was+1e-6);assertTrue(was-h<.06);was=h;}
        // (at the leap itself the plan is where they stand: holding the body to it moves nothing)
        assertArrayEquals(new double[]{1,2,3},SlamRules.planned(SlamRules.LEAP,new double[]{1,2,3},12,null),1e-12);
    }
    @Test void theClipBeginsAndEndsInTheStanceAndStepsNowhere(){
        R6.Frame first=OverwriteSlam.CLIP.frame(0),last=OverwriteSlam.CLIP.frame(OverwriteSlam.END);
        for(int i=0;i<6;i++)for(int k=0;k<3;k++){
            assertEquals(KnifeCombo.AT_REST.joints().joint[i][k],first.joints().joint[i][k],.05f);assertEquals(KnifeCombo.AT_REST.joints().joint[i][k],last.joints().joint[i][k],.05f);
        }
        R6.Frame was=first;
        for(float t=1/240f;t<=OverwriteSlam.END;t+=1/240f){
            R6.Frame f=OverwriteSlam.CLIP.frame(t);
            // (the landing itself aside: the legs come under them in the frame they touch down)
            boolean landing=t>OverwriteSlam.LAND-.06f&&t<OverwriteSlam.LAND+.03f;
            for(int i=0;i<6;i++){
                double turn=turn(was.joints().joint[i],f.joints().joint[i]);
                assertTrue(turn<(landing?60:16),"joint "+i+" turns "+turn+" at "+t);
            }
            assertTrue(Math.abs(f.up()-was.up())<.05&&Math.abs(f.fw()-was.fw())<.05,"the body jumps at "+t);
            assertTrue(Math.abs(f.pitch()-was.pitch())<(landing?12:4),"the body's lean jumps at "+t);
            was=f;
        }
    }
    /** The angle between two joint rotations (degrees). */
    static double turn(float[] a,float[] b){
        float k=(float)Math.PI/180;
        var qa=new org.joml.Quaternionf().rotationZYX(a[2]*k,a[1]*k,a[0]*k);var qb=new org.joml.Quaternionf().rotationZYX(b[2]*k,b[1]*k,b[0]*k);
        double d=Math.abs(qa.dot(qb));return Math.toDegrees(2*Math.acos(Math.min(1,d)));
    }
    @Test void theyCrouchSpringAndLandDeepTheKnifeInTheGroundAhead(){
        // crouched lowest just before the leap
        double low=0;float at=0;
        for(float t=0;t<OverwriteSlam.LEAP;t+=1/120f){double up=OverwriteSlam.CLIP.frame(t).up();if(up<low){low=up;at=t;}}
        assertTrue(low<-.3,"a crouch of "+low);assertTrue(at>OverwriteSlam.LEAP-.2f,"deepest just before the spring, at "+at);
        // in the air the clip's own height is its feet's (the rise is the plan's)
        for(float t=OverwriteSlam.LEAP+.1f;t<OverwriteSlam.LAND-.05f;t+=.05f)assertEquals(0,OverwriteSlam.CLIP.frame(t).up(),.02,"at "+t);
        // through the rise the knife is out to their right, about level; it goes up over their head before the dive
        Vec3d[] rising=OverwriteSlam.blade(Vec3d.ZERO,0,2.0f);assertTrue(Math.abs(rising[1].y)<.4,"the knife about level in the rise");
        Vec3d[] up=OverwriteSlam.blade(Vec3d.ZERO,0,3.04f);assertTrue(up[0].y>1.7,"the fist over the head before the dive: "+up[0].y);
        // landed: low, the knife's point down in the ground ahead of them
        R6.Frame down=OverwriteSlam.CLIP.frame(OverwriteSlam.LAND+.08f);assertTrue(down.up()<-.3);
        Vec3d[] in=OverwriteSlam.blade(Vec3d.ZERO,0,OverwriteSlam.LAND+.08f);Vec3d tip=in[0].add(in[1].multiply(KnifeCombo.TIP));
        assertTrue(tip.y<0,"the point under the ground: "+tip.y);assertTrue(tip.z>1.2,"ahead of them: "+tip.z);assertTrue(in[0].y>.2,"the fist over the ground");
        // before the landing no point of the knife is under where they stood while they stand there
        for(float t=0;t<OverwriteSlam.LEAP;t+=1/60f){var hb=OverwriteSlam.blade(Vec3d.ZERO,0,t);assertTrue(hb[0].add(hb[1].multiply(KnifeCombo.TIP)).y>.05,"the point in the ground at "+t);}
        // the ground under the clip's feet is far below in the air, and the floor again as they land
        assertEquals(0,OverwriteSlam.GROUND.at(0),0);assertEquals(0,OverwriteSlam.GROUND.at(OverwriteSlam.LAND),1e-9);assertTrue(OverwriteSlam.GROUND.at(2.5f)<-10);
    }
    @Test void theLandingsBurstAndItsCraterAreThereAndGone(){
        int seed=77;
        // spikes: white, magenta and dark by turns, long, all round
        int[] kinds=new int[3];
        for(int j=0;j<OverwriteSlam.SPIKES;j++){var q=OverwriteSlam.spike(j,seed);kinds[(int)q[3]]++;assertTrue(q[1]>=1.8&&q[1]<=6.5,"a spike of "+q[1]);}
        assertTrue(kinds[0]>=8&&kinds[1]>=8&&kinds[2]>=8);
        // the bars flicker: many at first, none by the end
        int first=0,late=0;
        for(int j=0;j<OverwriteSlam.BARS;j++){if(OverwriteSlam.bar(j,seed,.05f)!=null)first++;if(OverwriteSlam.bar(j,seed,OverwriteSlam.BAR_LIFE-.03f)!=null)late++;assertNull(OverwriteSlam.bar(j,seed,OverwriteSlam.BAR_LIFE));assertNull(OverwriteSlam.bar(j,seed,-.01f));}
        assertTrue(first>=12,"bars at first: "+first);assertTrue(late<=3,"bars at the end: "+late);
        // the crater: nothing right under them, a rim thrown up at once, sunk back by its end; a ripple beyond
        assertEquals(0,OverwriteSlam.heave(.5,.3f,.5,.5)[0],0);
        assertTrue(OverwriteSlam.heave(2,.2f,.5,.5)[0]>.5,"the rim two blocks out");assertTrue(OverwriteSlam.heave(2,.2f,.5,.5)[1]>10,"tipped away");
        assertEquals(0,OverwriteSlam.heave(2,OverwriteSlam.GROUND_LIFE,.5,.5)[0],0);assertTrue(OverwriteSlam.heave(2,OverwriteSlam.SINK1,.5,.5)[0]<1e-6);
        double was=0;for(float s=0;s<OverwriteSlam.GROUND_LIFE;s+=1/120f){double h=OverwriteSlam.heave(2,s,.5,.5)[0];assertTrue(Math.abs(h-was)<.2,"the floor jumps "+(h-was)+" at "+s);was=h;}
        double ripple=0;for(float s=0;s<1.2f;s+=1/60f)ripple=Math.max(ripple,OverwriteSlam.heave(6,s,.5,.5)[0]);
        assertTrue(ripple>.2,"a ripple runs out: "+ripple);assertEquals(0,OverwriteSlam.heave(OverwriteSlam.RIPPLE+.5,.4f,.5,.5)[0],0);
        // its pieces are flung up and out and come down on the ground, and are gone
        Vec3d land=new Vec3d(3,70,-2);int flung=0;
        for(int j=0;j<OverwriteSlam.CHUNKS;j++){
            double top=0;
            for(float s=0;s<2.3f;s+=1/30f){var q=OverwriteSlam.chunk(land,j,seed,s);if(q==null)continue;assertTrue(q[1]>=70-1e-6,"a piece under the ground");top=Math.max(top,q[1]-70);assertTrue(Math.hypot(q[0]-3,q[2]+2)<26);}
            if(top>1)flung++;assertNull(OverwriteSlam.chunk(land,j,seed,2.3f));
        }
        assertTrue(flung>=OverwriteSlam.CHUNKS*3/4,"pieces flung: "+flung);
    }
}
