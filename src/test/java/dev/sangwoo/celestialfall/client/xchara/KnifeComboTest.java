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
 * X Chara's basic attack as drawn (2.114.0, user request: "엑스차라 평타 만들자 너가 창작해줘 ... 4타로 끝나고 멋있게"): four
 * hits of the knife, no joint jumping or flipping between frames, the body facing the front all through, each hit
 * beginning where the one before stands as it hands over, the knife's edge leading through every cut and its blade
 * crossing the front on the server's blow, the thrusts out on their blows and drawn back between, nothing in the ground
 * (the feet on it, never in it), every cut drawn along the knife's own path, torn as it is cut and as it goes and whole
 * between, and everything a hit leaves gone before its swing is.
 * <p>The plain four (2.117.0, user request: "이번에는 Q 껐을때 평타를 만들어줘 이펙트 없이 그냥 휘두르기만해 그대신 모션을
 * 좋게 만들어봐"): nothing drawn; each lands on the middle line at a body's height within the server's reach, its edge
 * leading from as it is let go until it is past its blow, the body turning before the arm goes, quickest as it lands
 * and its wind-up slower, carried on past the blow and never frozen until the next takes over; the feet stand where
 * they are put while the body moves over them, and step in.
 */
class KnifeComboTest {
    /** Every hit: the hack's four, then the plain four (2.117.0). */
    static final int[] ALL={CUT,PASTE,FLOOD,DELETE,SLASH,RISE,CHOP,LUNGE};
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
        for(int begins:new int[]{CUT,SLASH}){
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
        var k=knife(CUT,0);
        assertTrue(Math.abs(k[3].y)<.02&&k[3].z>.999,"the knife level, pointing ahead: "+k[3]);
        var arm=R6.poseOf(s).rArm;
        assertEquals(Math.sin(Math.toRadians(XCharaIdle.KNIFE_OUT)),arm.rt(),1e-3,"the arm a little out to the right");
        assertEquals(0,arm.fw(),1e-3,"and neither forward nor back");
    }
    @Test void whereAHitStandsInTheStanceItsLimbsAreStraightAndBetweenTheyBend(){
        // the first hit begins at attention and every hit ends there: no elbow or knee bent, eased out of and into
        for(int begins:new int[]{CUT,SLASH}){
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
    @Test void everyCutIsDrawnAlongTheKnifesOwnPathWithItsEdgeLeading(){
        for(int hit:new int[]{CUT,PASTE,DELETE})for(var s:KnifeCombo.cuts(hit)){
            assertTrue(s.from()<s.to()&&s.to()<=s.eat0()+.2f&&s.eat0()<s.eat1()&&s.eat1()<KnifeCombo.LIFE);
            assertTrue(s.inner()>=KnifeCombo.HEEL&&s.outer()<=KnifeCombo.TIP+.4&&s.inner()<s.outer(),"the band is the blade's");
            // the band's edges are the knife's at every moment
            var sw=KnifeCombo.swept(Vec3d.ZERO,0,hit,s.from(),s.to(),s.inner(),s.outer());
            for(int i=0;i<sw.n();i++){
                float ts=s.from()+(s.to()-s.from())*i/(sw.n()-1);var hb=KnifeCombo.blade(Vec3d.ZERO,0,hit,ts);
                assertTrue(sw.outer()[i].distanceTo(hb[0].add(hb[1].multiply(s.outer())))<1e-6);
                assertTrue(sw.inner()[i].distanceTo(hb[0].add(hb[1].multiply(s.inner())))<1e-6);
                if(i>0)assertTrue(sw.along()[i]>=sw.along()[i-1],"it runs on from its tail to its head");
            }
            assertEquals(0,sw.along()[0],1e-6);assertEquals(1,sw.along()[sw.n()-1],1e-5);
            // a real sweep: its point travels some blocks
            double len=0;for(int i=1;i<sw.n();i++)len+=sw.outer()[i].distanceTo(sw.outer()[i-1]);
            assertTrue(len>4,"hit "+hit+": the knife's point sweeps "+len+" blocks");
            // through the cut the edge leads: the blade trails the arm the way it goes (the edge is on the forearm's side of the blade)
            int led=0,all=0;
            for(double t=s.from()+.012;t<=s.to()-.012;t+=1/240.0){
                var pose=R6.poseOf(KnifeCombo.clipOf(hit).frame((float)t));var hb=KagenoClips.blade(Vec3d.ZERO,0,pose);
                Vec3d arm=hb[0].subtract(KagenoClips.shoulder(Vec3d.ZERO,0,pose)).normalize(),b=hb[1];
                Vec3d edge=arm.subtract(b.multiply(arm.dotProduct(b)));
                assertTrue(edge.length()>.25,"hit "+hit+": the knife in line with the arm at "+t+" (its turn undefined)");
                var later=KnifeCombo.blade(Vec3d.ZERO,0,hit,(float)(t+1/240.0));
                Vec3d mid=hb[0].add(b.multiply(1.6)),next=later[0].add(later[1].multiply(1.6)),v=next.subtract(mid);
                if(v.length()<1e-4)continue;
                all++;if(edge.normalize().dotProduct(v.normalize())>.15)led++;
            }
            assertTrue(all>10&&led>=all*.85,"hit "+hit+": the edge leads "+led+" of "+all+" moments of the cut");
        }
    }
    @Test void everyBladeCrossesTheFrontOnItsBlowWithinTheServersReach(){
        for(int hit:new int[]{CUT,PASTE,DELETE})for(int i=0;i<blows(hit);i++){
            if(hit==PASTE&&i==1)continue;
            var k=knife(hit,blow(hit,i));double[] heel=local(k[1]),tip=local(k[2]);
            // the blade points ahead as the blow lands, and where it is three blocks ahead of them (where one they
            // strike stands) it is on the middle line at a body's height
            assertTrue(tip[0]>3.1&&heel[0]>.8&&heel[0]<3,"hit "+hit+" blow "+i+": the blade ahead of them: "+heel[0]+" to "+tip[0]);
            double u=(3-heel[0])/(tip[0]-heel[0]),side=heel[2]+(tip[2]-heel[2])*u,high=heel[1]+(tip[1]-heel[1])*u;
            assertTrue(Math.abs(side)<.3,"hit "+hit+" blow "+i+": the blade on the middle line three blocks ahead: "+side);
            assertTrue(high>.7&&high<1.8,"hit "+hit+" blow "+i+": the blade at a body's height there: "+high);
            assertTrue(tip[0]<=reach(hit,i)+.4&&Math.abs(tip[2])<=halfWidth(hit,i)+.4,"hit "+hit+" blow "+i+": the point within the blow's reach");
            // the cut is being drawn as it lands
            boolean drawn=false;for(var s:KnifeCombo.cuts(hit))drawn|=blow(hit,i)>s.from()&&blow(hit,i)<s.to();
            assertTrue(drawn,"hit "+hit+" blow "+i+" lands inside a cut");
        }
        // the finisher's two cuts come down from opposite sides
        var c=KnifeCombo.H4_CUTS;
        double[] a0=local(knife(DELETE,c[0].from())[2]),a1=local(knife(DELETE,c[0].to())[2]),b0=local(knife(DELETE,c[1].from())[2]),b1=local(knife(DELETE,c[1].to())[2]);
        assertTrue(a0[2]>.5&&a1[2]<-.5&&a0[1]>a1[1]+1,"the first from the high right down to the low left");
        assertTrue(b0[2]<-.5&&b1[2]>.5&&b0[1]>b1[1]+1,"the second from the high left down to the low right");
    }
    @Test void theThrustsAreOutOnTheirBlowsAndDrawnBackBetween(){
        double last=0;
        for(int i=0;i<blows(FLOOD);i++){
            double[] tip=local(knife(FLOOD,blow(FLOOD,i))[2]);
            assertTrue(tip[0]>3&&Math.abs(tip[2])<.8,"thrust "+i+" out ahead: "+tip[0]+", "+tip[2]);
            double[] fist=local(knife(FLOOD,blow(FLOOD,i))[0]);
            assertTrue(fist[0]<KnifeCombo.THROW_FROM&&KnifeCombo.THROW_FROM<tip[0],"its knife of light leaves from along the knife: "+fist[0]+" to "+tip[0]);
            if(i>0){
                double back=Double.MAX_VALUE;
                for(double t=blow(FLOOD,i-1);t<=blow(FLOOD,i);t+=1/240.0)back=Math.min(back,local(knife(FLOOD,(float)t)[2])[0]);
                assertTrue(back<last-.7,"drawn back before thrust "+i+": "+back+" from "+last);
            }
            last=tip[0];
        }
        assertTrue(local(knife(FLOOD,blow(FLOOD,2))[0])[0]>local(knife(FLOOD,blow(FLOOD,0))[0])[0]+.1,"the last a lunge");
    }
    @Test void theKnivesOfLightFlyOnToTheServersReach(){
        assertEquals(KnifeCombo.THROW_FROM,KnifeCombo.flown(0),1e-9);
        assertEquals(KnifeCombo.THROW_TO,KnifeCombo.flown(KnifeCombo.FLIGHT),1e-9);assertEquals(KnifeCombo.THROW_TO,KnifeCombo.flown(1),1e-9);
        assertEquals(KNIFE_RANGE,KnifeCombo.THROW_TO+KnifeCombo.KNIFE_LONG*.5,1e-9,"its point ends where the server's reach does");
        double at=0;for(float s=0;s<=KnifeCombo.FLIGHT;s+=.002f){assertTrue(KnifeCombo.flown(s)>=at);at=KnifeCombo.flown(s);}
        // how long it takes to get somewhere along its way is its flight turned round
        for(double ahead=KnifeCombo.THROW_FROM;ahead<=KnifeCombo.THROW_TO;ahead+=.2)assertEquals(ahead,KnifeCombo.flown(KnifeCombo.reaches(ahead)),1e-4);
        assertEquals(0,KnifeCombo.reaches(.5),1e-9);assertEquals(KnifeCombo.FLIGHT,KnifeCombo.reaches(9),1e-6);
        for(double[] lane:KnifeCombo.LANE)assertTrue(lane[0]>HIGH*.3&&lane[0]<HIGH&&Math.abs(lane[1])<halfWidth(FLOOD,0),"each flies in the thrusts' way");
        assertEquals(blows(FLOOD),KnifeCombo.LANE.length);
    }
    @Test void theSecondHitsCutIsPastedFurtherOn(){
        var s=KnifeCombo.H2_CUTS[0];var first=KnifeCombo.swept(Vec3d.ZERO,0,PASTE,s.from(),s.to(),s.inner(),s.outer());var copy=KnifeCombo.pasted(Vec3d.ZERO,0);
        assertEquals(first.n(),copy.n());
        double sum=0;for(int i=0;i<first.n();i++)sum+=local(copy.outer()[i])[0]-local(first.outer()[i])[0];
        assertEquals(PASTE_AHEAD,sum/first.n(),.25,"as far ahead as the server's second blow reaches further");
        for(int i=0;i<first.n();i++)assertEquals(local(first.outer()[i])[1],local(copy.outer()[i])[1],.25,"at the same height");
        assertEquals(blow(PASTE,1),KnifeCombo.PASTE_AT,1e-6,"as its blow lands");
        assertTrue(KnifeCombo.PASTE_EAT0>KnifeCombo.PASTE_AT+.1f&&KnifeCombo.PASTE_EAT1<KnifeCombo.LIFE);
        // the free hand is thrown out ahead for it
        Vec3d hand=KagenoClips.freeHand(Vec3d.ZERO,0,R6.poseOf(KnifeCombo.H2.frame(KnifeCombo.PASTE_AT)));
        Vec3d before=KagenoClips.freeHand(Vec3d.ZERO,0,R6.poseOf(KnifeCombo.H2.frame(blow(PASTE,0))));
        assertTrue(local(hand)[0]>local(before)[0]+.3&&local(hand)[0]>.8,"the free hand out ahead: "+local(hand)[0]);
    }
    @Test void aCutIsTornAsItIsCutWholeBetweenAndTornAgainAsItGoes(){
        for(int hit:new int[]{CUT,PASTE,DELETE})for(var s:KnifeCombo.cuts(hit)){
            assertTrue(KnifeCombo.tear(s.from(),s.from(),s.to(),s.eat0(),s.eat1(),s.seed())>.8,"torn as it is cut");
            assertTrue(KnifeCombo.tear(s.eat1(),s.from(),s.to(),s.eat0(),s.eat1(),s.seed())>.8,"torn apart as it goes");
            // whole between (but a twitch now and then), and never quite clean
            int whole=0,all=0;
            for(float t=s.to()+.05f;t<s.eat0()-.05f;t+=.005f){all++;float v=KnifeCombo.tear(t,s.from(),s.to(),s.eat0(),s.eat1(),s.seed());assertTrue(v>=.1f&&v<=.5f+1e-6);if(v<=.15f)whole++;}
            if(all>0)assertTrue(whole>=all*.5,"hit "+hit+": whole between ("+whole+" of "+all+")");
        }
    }
    @Test void theXHoldsThenFoldsFlatAndGoesOut(){
        assertArrayEquals(new double[]{1,1},GlitchFx.closing(0),1e-9);assertArrayEquals(new double[]{1,1},GlitchFx.closing(-1),1e-9);
        double tall=1;
        for(float s=0;s<GlitchFx.X_FOLD;s+=.002f){double[] z=GlitchFx.closing(s);assertTrue(z[1]<=tall+1e-9&&z[0]>=1-1e-9,"it folds flat, its width kept");tall=z[1];}
        assertTrue(GlitchFx.closing(GlitchFx.X_FOLD-.0005f)[1]<.08,"flat");
        double wide=2;
        for(float s=GlitchFx.X_FOLD;s<GlitchFx.X_FOLD+GlitchFx.X_OUT;s+=.002f){double[] z=GlitchFx.closing(s);assertTrue(z[0]<=wide+1e-9&&z[1]<.05,"its line draws in");wide=z[0];}
        assertArrayEquals(new double[]{0,0},GlitchFx.closing(GlitchFx.X_FOLD+GlitchFx.X_OUT),1e-9);
        // whole a moment after the second cut, and all of it gone well before the swing is
        assertTrue(KnifeCombo.X_CLOSE>blow(DELETE,1)+.08f);
        assertTrue(KnifeCombo.X_CLOSE+GlitchFx.X_FOLD+GlitchFx.X_OUT+GlitchFx.PIXEL_LIFE<KnifeCombo.LIFE-.3f);
        assertTrue(KnifeCombo.H4_CUTS[0].eat0()>blow(DELETE,1),"the first cut stays until the second has crossed it");
    }
    @Test void whatABlowLeavesIsItsOwnClocksAndSoonGone(){
        Vec3d from=new Vec3d(3,65,-2),way=new Vec3d(0,0,1);
        for(int j=0;j<24;j++){
            GlitchFx.Pixel first=null;double far=0;
            for(float s=0;s<GlitchFx.PIXEL_LIFE+.1f;s+=.004f){
                var p=GlitchFx.pixel(from,way,.75,5.5,.055,j,77,s);
                if(p==null){if(first!=null)far=Math.max(far,0);continue;}
                if(first==null){first=p;assertTrue(p.at().distanceTo(from)<.2,"it starts on them");}
                assertEquals(first.half(),p.half(),1e-9,"a pixel keeps its size");assertEquals(first.rgb(),p.rgb());
                far=Math.max(far,p.at().distanceTo(from));
            }
            assertNotNull(first);assertTrue(far>.2&&far<3,"pixel "+j+" flies "+far);
            assertNull(GlitchFx.pixel(from,way,.75,5.5,.055,j,77,GlitchFx.PIXEL_LIFE+.05f));
            // the same on every client
            assertEquals(GlitchFx.pixel(from,way,.75,5.5,.055,j,77,.1f),GlitchFx.pixel(from,way,.75,5.5,.055,j,77,.1f));
        }
        assertEquals(1,GlitchFx.boxAlpha(.01f),1e-6);assertEquals(0,GlitchFx.boxAlpha(GlitchFx.BOX_LIFE),1e-6);assertEquals(0,GlitchFx.boxAlpha(-.01f),1e-6);
        assertTrue(GlitchFx.boxGrow(0)>1.15&&Math.abs(GlitchFx.boxGrow(.06f)-1)<1e-6,"the box snaps onto them");
        assertNull(GlitchFx.tear(from,new Vec3d(1,0,0),.3,1.8,0,5,GlitchFx.TEAR_LIFE,1));
        for(int j=0;j<GlitchFx.TEARS;j++){
            var t=GlitchFx.tear(from,new Vec3d(1,0,0),.3,1.8,j,5,.02f,1);
            assertNotNull(t);assertTrue(Math.abs(t.at().y-from.y)<=.9*.9+1e-9&&t.halfWide()>t.halfHigh()*2,"a bar across them");
        }
        Vec3d d0=GlitchFx.digitAt(from,new Vec3d(1,0,0),.6,.9,2,5,.2f),d1=GlitchFx.digitAt(from,new Vec3d(1,0,0),.6,.9,2,5,.4f);
        assertNotNull(d0);assertNotNull(d1);assertTrue(d1.y>d0.y,"the digits rise");
        assertNull(GlitchFx.digitAt(from,new Vec3d(1,0,0),.6,.9,2,5,GlitchFx.DIGIT_LIFE+.2f));
        for(String[] digit:GlitchFx.DIGITS){assertEquals(5,digit.length);for(String row:digit)assertEquals(3,row.length());}
        // nothing a blow leaves outlasts the swing that left it
        for(int hit:ALL)assertTrue(blow(hit,blows(hit)-1)+.75f<KnifeCombo.LIFE);
        assertTrue(blow(FLOOD,2)+KnifeCombo.FLIGHT+KnifeCombo.AFTER+GlitchFx.PIXEL_LIFE<KnifeCombo.LIFE);
    }
    @Test void aCutsEdgeIsTurnedToTheCameraAndNeverTwists(){
        // from right behind its user a cut's band is edge-on (a hairline): its edge is drawn once more as a strip turned to
        // the camera, as wide from anywhere, to one side of the knife's point's path all along it
        Vec3d[] cameras={new Vec3d(0,3.2,-5.5),new Vec3d(6.5,1.8,2.4),new Vec3d(-4,3.6,-5),new Vec3d(.3,2.2,9),new Vec3d(0,9,1.5)};
        for(int hit:new int[]{CUT,PASTE,DELETE})for(var s:KnifeCombo.cuts(hit)){
            var sw=KnifeCombo.swept(Vec3d.ZERO,0,hit,s.from(),s.to(),s.inner(),s.outer());
            for(Vec3d camera:cameras){
                var e=KnifeCombo.edge(sw,camera,KnifeCombo.EDGE_WIDE);
                assertEquals(sw.n(),e.n());
                Vec3d before=null;
                for(int i=0;i<e.n();i++){
                    assertEquals(0,e.outer()[i].distanceTo(sw.outer()[i]),1e-9,"along the knife's point's path");
                    Vec3d side=e.inner()[i].subtract(e.outer()[i]);
                    assertEquals(KnifeCombo.EDGE_WIDE,side.length(),1e-6,"as wide all along it");
                    assertEquals(0,side.normalize().dotProduct(camera.subtract(e.outer()[i]).normalize()),1e-6,"turned to the camera");
                    if(before!=null)assertTrue(side.dotProduct(before)>0,"hit "+hit+": its edge twists at "+i+" seen from "+camera);
                    before=side;
                }
                assertArrayEquals(sw.along(),e.along());
            }
        }
        // (the band itself seen from right behind is edge-on about its blow: that is what the strip is for)
        var s=KnifeCombo.H1_CUTS[0];var sw=KnifeCombo.swept(Vec3d.ZERO,0,CUT,s.from(),s.to(),s.inner(),s.outer());
        Vec3d camera=new Vec3d(0,1.2,-5.5);double thinnest=9;
        for(int i=1;i+1<sw.n();i++){
            Vec3d along=sw.outer()[i+1].subtract(sw.outer()[i-1]).normalize(),across=sw.inner()[i].subtract(sw.outer()[i]).normalize();
            Vec3d normal=along.crossProduct(across).normalize(),view=camera.subtract(sw.outer()[i]).normalize();
            thinnest=Math.min(thinnest,Math.abs(normal.dotProduct(view)));
        }
        assertTrue(thinnest<.2,"the band seen from right behind is nearly edge-on somewhere: "+thinnest);
    }
    @Test void everyThrustStampsAPopupAndTheyPileUpThenCloseTheLastFirst(){
        // a popup snaps open (overshooting), stays, and folds flat to nothing
        assertEquals(0,GlitchFx.windowGrow(0),1e-9);assertEquals(0,GlitchFx.windowGrow(-1),1e-9);
        double most=0;for(float s=.002f;s<GlitchFx.WINDOW_OPEN;s+=.002f)most=Math.max(most,GlitchFx.windowGrow(s));
        assertTrue(most>1.05&&most<1.4,"it overshoots as it snaps open: "+most);
        assertEquals(1,GlitchFx.windowGrow(GlitchFx.WINDOW_OPEN),1e-9);assertEquals(1,GlitchFx.windowGrow(1),1e-9);
        assertEquals(1,GlitchFx.windowTall(0),1e-9);assertEquals(1,GlitchFx.windowTall(-1),1e-9);
        double tall=1;for(float s=.001f;s<GlitchFx.WINDOW_SHUT;s+=.002f){double t=GlitchFx.windowTall(s);assertTrue(t<=tall+1e-9&&t>0);tall=t;}
        assertTrue(tall<.1,"flat before it goes");assertEquals(0,GlitchFx.windowTall(GlitchFx.WINDOW_SHUT),1e-9);
        // where: this side of whom the thrust struck, each a step down, to the right and nearer than the one before
        Vec3d struck=new Vec3d(0,1,3);Vec3d before=null;
        for(int i=0;i<blows(FLOOD);i++){
            Vec3d at=KnifeCombo.windowAt(Vec3d.ZERO,0,struck,.3,i);double[] l=local(at);
            assertTrue(l[0]<3-.3&&l[0]>1.5,"popup "+i+" this side of them: "+l[0]);
            assertTrue(Math.abs(l[2])<.6&&l[1]>.5&&l[1]<1.6,"popup "+i+" on them: "+l[1]+", "+l[2]);
            if(before!=null){double[] b=local(before);assertTrue(l[1]<b[1]-.1&&l[2]>b[2]+.2&&l[0]<b[0]-.05,"popup "+i+" a step down, right and nearer");}
            before=at;
            assertTrue(KnifeCombo.windowWide(i)>KnifeCombo.windowHigh(i)&&(i==0||KnifeCombo.windowWide(i)>KnifeCombo.windowWide(i-1)),"wider than tall, each a little bigger");
            // (no one struck: where its knife of light ends)
            assertEquals(KnifeCombo.THROW_TO-.12*i,local(KnifeCombo.windowAt(Vec3d.ZERO,0,null,0,i))[0],1e-6);
        }
        // when: they close a moment after the last thrust, the last opened first, all gone well before the swing is
        assertTrue(KnifeCombo.WINDOWS_SHUT>blow(FLOOD,2)+.08f);
        float last=KnifeCombo.WINDOWS_SHUT+KnifeCombo.WINDOW_STEP*(blows(FLOOD)-1)+GlitchFx.WINDOW_SHUT+GlitchFx.PIXEL_LIFE;
        assertTrue(last<KnifeCombo.LIFE-.3f,"the popups and their pixels are gone before the swing is: "+last);
        assertTrue(KnifeCombo.WINDOWS_SHUT+KnifeCombo.WINDOW_STEP*(blows(FLOOD)-1)+GlitchFx.WINDOW_SHUT<=STEP[FLOOD]/20f+.1f,"closed as the finisher's wind-up starts");
    }
    @Test void aBlowShakesTheCameraOnlyALittleAndTheFinisherMost(){
        float most=0;
        for(int hit=CUT;hit<=DELETE;hit++)for(int i=0;i<blows(hit);i++){
            assertTrue(KnifeCombo.SHAKE[hit][i]>0&&KnifeCombo.SHAKE[hit][i]<=1.2f);
            if(!launches(hit,i))most=Math.max(most,KnifeCombo.SHAKE[hit][i]);
        }
        assertTrue(KnifeCombo.SHAKE[DELETE][1]>most*1.5f,"the last cut the hardest");
        // in over a frame or two, never a jump
        float before=0;
        for(float t=.3f;t<.6f;t+=1/240f){float v=KnifeCombo.bump(t,.4f,.07f);assertTrue(Math.abs(v-before)<.3f,"no jump at "+t);before=v;}
        assertEquals(0,KnifeCombo.bump(.3f,.4f,.07f),1e-6);
    }

    // ───────────────────────────── the plain four (2.117.0) ─────────────────────────────

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

    @Test void aPlainSwingDrawsNothingAndShakesTheCameraOnlyALittle(){
        for(int hit=SLASH;hit<=LUNGE;hit++){
            assertEquals(0,KnifeCombo.cuts(hit).length,"hit "+hit+" leaves no cut");
            assertEquals(1,blows(hit));
            assertTrue(KnifeCombo.SHAKE[hit][0]>0&&KnifeCombo.SHAKE[hit][0]<=1.2f);
        }
        assertTrue(KnifeCombo.SHAKE[LUNGE][0]>KnifeCombo.SHAKE[CHOP][0]&&KnifeCombo.SHAKE[CHOP][0]>KnifeCombo.SHAKE[SLASH][0],"the heavier the swing the more");
    }
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
