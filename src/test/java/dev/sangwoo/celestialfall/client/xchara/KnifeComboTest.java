package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.isosang.IsosangSlash;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
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
 */
class KnifeComboTest {
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
        for(int hit=CUT;hit<=DELETE;hit++){
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
        for(int hit=CUT;hit<=DELETE;hit++){
            var a=KnifeCombo.clipOf(hit);
            for(double t=0;t<=a.length();t+=1/240.0){
                float yaw=MathHelper.wrapDegrees(a.frame((float)t).yaw());
                assertTrue(Math.abs(yaw)<=40,"hit "+hit+" turns "+yaw+" deg away from the front at "+t);
            }
        }
    }
    @Test void hitStopsSitOnBlowsAndAreOverBeforeTheNext(){
        for(int hit=CUT;hit<=DELETE;hit++){
            var a=KnifeCombo.clipOf(hit);
            for(var s:a.stops()){
                assertEquals(s.sec(),a.warp(s.sec()),1e-6,"hit "+hit+": the impact itself is on time");
                boolean on=false;for(int i=0;i<blows(hit);i++)on|=Math.abs(blow(hit,i)-s.sec())<1e-4;
                assertTrue(on,"hit "+hit+": hit-stop at "+s.sec()+" is not on a blow");
                for(int i=0;i<blows(hit);i++)if(blow(hit,i)>s.sec()+1e-4)assertTrue(s.sec()+s.hold()+s.recover()<=blow(hit,i)+1e-6,"hit "+hit+": still catching up at the next blow");
                if(hit<DELETE)assertTrue(s.sec()+s.hold()+s.recover()<=STEP[hit]/20f,"hit "+hit+": still catching up as the next takes over");
            }
        }
    }
    @Test void theFirstStartsAndEveryOneEndsInTheStanceAndRunsAsLongAsTheHit(){
        var stance=KnifeCombo.stance();
        var first=KnifeCombo.clipOf(CUT).frame(0);
        for(int j=0;j<6;j++)assertTrue(turn(first,stance,j)<.5,"hit 1 starts in the stance");
        for(int hit=CUT;hit<=DELETE;hit++){
            var a=KnifeCombo.clipOf(hit);var last=a.frame(a.length());
            for(int j=0;j<6;j++)assertTrue(turn(last,stance,j)<.5,"hit "+hit+" ends in the stance");
            assertEquals(0,MathHelper.wrapDegrees(last.yaw()),1e-3,"hit "+hit+" ends facing the way it began");
            assertEquals(0,last.fw(),1e-6);assertEquals(0,last.up(),1e-6);
            assertEquals(stance.grip(),last.grip(),.01,"hit "+hit+" gives the knife back to the game's hold");
            assertEquals(LENGTH[hit]/20f,a.length(),.011,"hit "+hit+" runs as long as the server says");
        }
    }
    @Test void theStanceIsTheGamesHoldOfTheKnife(){
        // the arm a little forward, its elbow bent as the model's hold bends it, the knife level ahead (its display's tilt)
        var s=KnifeCombo.stance();var hold=KnifeCombo.hold();
        assertTrue(turn(s,hold,2)<1.5,"the arm as the game holds it");
        assertEquals(hold.joints().elbow[0],s.joints().elbow[0],1.5,"the elbow as the game bends it");
        assertEquals(KnifeCombo.DISPLAY_TILT,s.grip(),1.5,"the knife as its display turns it");
        var k=knife(CUT,0);
        assertTrue(Math.abs(k[3].y)<.2&&k[3].z>.95,"the knife about level, pointing ahead: "+k[3]);
    }
    @Test void eachHitBeginsWhereTheOneBeforeStandsAsItHandsOver(){
        for(int hit=CUT;hit<DELETE;hit++){
            var before=KnifeCombo.clipOf(hit).frame(STEP[hit]/20f);var after=KnifeCombo.clipOf(hit+1).frame(0);
            for(int j=0;j<6;j++)assertTrue(turn(before,after,j)<1,"hit "+(hit+1)+" joint "+j+" begins "+turn(before,after,j)+" deg off hit "+hit);
            assertEquals(before.fw(),after.fw(),.02);assertEquals(before.up(),after.up(),.02);
            assertEquals(MathHelper.wrapDegrees(before.yaw()),MathHelper.wrapDegrees(after.yaw()),1);
        }
    }
    @Test void theKnifeAndTheFeetStayOutOfTheGround(){
        for(int hit=CUT;hit<=DELETE;hit++){
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
        for(int hit=CUT;hit<=DELETE;hit++)assertTrue(blow(hit,blows(hit)-1)+.75f<KnifeCombo.LIFE);
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
}
