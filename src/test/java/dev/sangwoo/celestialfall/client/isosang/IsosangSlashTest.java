package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's basic attack as drawn (2.77.0; rebuilt after the user's videos of each hit in 2.77.2): four hits, each
 * blade cutting through the server's reach on the server's blows, the strokes the videos show, no joint jumping or
 * flipping between frames, each hit beginning exactly where the one before stands as it hands over, every slash sweeping
 * on with its blade and over before its swing is, the thrown slash flying where the server strikes, nothing in the
 * ground (the feet on it, never in it), the light along every slash's outer edge with the ink spreading in from its
 * tail, the ink on the ground gone within a second, and the finisher's camera drawn back and in smoothly.
 */
class IsosangSlashTest {
    static Quaternionf q(float[] e){float k=(float)Math.PI/180;return new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k);}
    static double turn(R6.Frame a,R6.Frame b,int j){return Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(q(a.joints().joint[j]).dot(q(b.joints().joint[j]))))));}
    /** The sword's point in the swing's frame (forward, up, right) for hit {@code combo} at {@code sec}, the feet at the origin. */
    static double[] tip(int combo,float sec){
        var hb=IsosangSlash.blade(Vec3d.ZERO,0,combo,sec);
        Vec3d t=hb[0].add(hb[1].multiply(IsosangSlash.TIP));return new double[]{t.z,t.y,-t.x};
    }
    static float blow(int combo,int i){return IsosangRules.blowAt(combo,i)/20f;}
    static double azimuth(double[] p){return Math.toDegrees(Math.atan2(p[2],p[0]));}
    static String s(double[] p){return java.util.Arrays.toString(p);}

    @Test void noClipJumpsOrFlipsItsJoints(){
        for(int combo=1;combo<=4;combo++){
            Anim a=IsosangSlash.hit(combo);double h=1/960.0;
            for(double t=h;t+2*h<=a.length();t+=h){
                var p0=a.frame((float)(t-h));var p1=a.frame((float)t);var p2=a.frame((float)(t+h));var p3=a.frame((float)(t+2*h));
                for(int j=0;j<6;j++){
                    double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                    assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"hit "+combo+" joint "+j+" jumps "+now+" deg at "+t);
                }
            }
            for(double t=0;t+1/60.0<=a.length();t+=1/60.0)
                for(int j=0;j<6;j++)assertTrue(turn(a.frame((float)t),a.frame((float)(t+1/60.0)),j)<125,"hit "+combo+" joint "+j+" flips at "+t);
        }
    }
    @Test void hitStopsSitOnBlowsAndAreOverBeforeTheNext(){
        for(int combo=1;combo<=4;combo++){
            var a=IsosangSlash.hit(combo);
            for(var s:a.stops()){
                assertEquals(s.sec(),a.warp(s.sec()),1e-6,"hit "+combo+": the impact itself is on time");
                boolean on=false;for(int i=0;i<IsosangRules.blows(combo);i++)on|=Math.abs(blow(combo,i)-s.sec())<1e-4;
                assertTrue(on,"hit "+combo+": hit-stop at "+s.sec()+" is not on a blow");
                for(int i=0;i<IsosangRules.blows(combo);i++)if(blow(combo,i)>s.sec()+1e-4)assertTrue(s.sec()+s.hold()+s.recover()<=blow(combo,i),"hit "+combo+": still catching up at the next blow");
                if(combo<4)assertTrue(s.sec()+s.hold()+s.recover()<=IsosangRules.STEP[combo]/20f,"hit "+combo+": still catching up as the next takes over");
            }
        }
    }
    @Test void theFirstStartsAndEveryOneEndsInTheStanceAndRunsAsLongAsTheHit(){
        var stance=IsosangSlash.stance();
        var first=IsosangSlash.hit(1).frame(0);
        for(int j=0;j<6;j++)assertTrue(turn(first,stance,j)<.5,"hit 1 starts in the stance");
        for(int combo=1;combo<=4;combo++){
            var a=IsosangSlash.hit(combo);var last=a.frame(a.length());
            for(int j=0;j<6;j++)assertTrue(turn(last,stance,j)<.5,"hit "+combo+" ends in the stance");
            assertEquals(0,MathHelper.wrapDegrees(last.yaw()),1e-3,"hit "+combo+" ends facing the way it began");
            assertEquals(0,last.fw(),1e-6);assertEquals(0,last.up(),1e-6);
            assertEquals(stance.grip(),last.grip(),.01,"hit "+combo+" gives the sword back to the game's hold");
            assertEquals(IsosangRules.LENGTH[combo]/20f,a.length(),.11,"hit "+combo+" runs as long as the server says");
        }
        // the stance is how the game holds the sword (a turn of 12° about the wrist past R6's grip: DISPLAY_TILT)
        assertEquals(IsosangSlash.DISPLAY_TILT,stance.grip(),.5);
    }
    @Test void everyBladeCutsThroughTheServersReachOnItsBlow(){
        for(int combo=1;combo<=4;combo++)for(int i=0;i<IsosangRules.blows(combo);i++){
            float at=blow(combo,i);double[] p=tip(combo,at);
            assertTrue(p[1]>.1&&p[1]<3.4,"hit "+combo+" blow "+i+" at a body's height: "+s(p));
            if(combo==2){
                if(i==0){assertTrue(p[1]<.9,"the ring sweeps low over the ground: "+s(p));assertTrue(Math.hypot(p[0]-.38,p[2])>2,"and far out round them: "+s(p));}
                continue;
            }
            // (2.79.2 draft: hit 3's four cuts follow the user's videos; the server's five blows keep their old times until
            // the user decides whether they follow the cuts)
            if(combo==3)continue;
            assertTrue(p[0]>1.2,"hit "+combo+" blow "+i+" ahead: "+s(p));
            assertTrue(IsosangRules.lane(combo,i,p[0],p[2],0,.3),"hit "+combo+" blow "+i+" inside its way: "+s(p));
        }
    }
    /** How far the point of hit {@code combo} turns round the feet from {@code from} to {@code to} (degrees, clockwise seen from above positive). */
    static double turned(int combo,float from,float to,double aboutFw){
        double swept=0,prev=Double.NaN;
        for(float t=from;t<=to+1e-6;t+=1/240f){
            double[] p=tip(combo,t);double az=Math.toDegrees(Math.atan2(p[2],p[0]-aboutFw));
            if(!Double.isNaN(prev))swept+=MathHelper.wrapDegrees(az-prev);prev=az;
        }
        return swept;
    }
    @Test void eachHitCutsTheWayItsVideoShows(){
        // (2.78.0, the user's video from above: every sweep goes round to the right, clockwise seen from above)
        // 1: the sword flashed out low at the left; the spin cut from the front-left across the front, round the right and
        // behind to the back-left, high at the front-right and low behind; the thin cut rising back from the low left to
        // the high right; held up there
        double[] a=tip(1,.05f);assertTrue(a[1]<1.6&&a[2]<-.3,"hit 1 begins with the sword out low at the left: "+s(a));
        // (2.79.0, the videos from above and behind: one cut, on a plane low behind the left and high ahead to the right,
        // rising from the ground up across the front to end high at the front-right)
        assertTrue(turned(1,.25f,.32f,.36)>240,"hit 1's cut goes round them to the right: "+turned(1,.25f,.32f,.36));
        a=tip(1,.263f);double[] b=tip(1,.32f);
        assertTrue(a[1]<1.0&&a[2]<-1.0,"hit 1 comes down low at the left: "+s(a));
        assertTrue(b[1]>2.0&&b[2]>1.0&&b[1]>a[1]+1.2,"and rises across the front to end high at the right: "+s(b));
        b=tip(1,.55f);assertTrue(b[1]>2.4&&b[2]>.8,"hit 1 holds the sword up at the right: "+s(b));
        // 2 (2.79.2 draft, the user's videos): two cuts to the left, anticlockwise seen from above: behind them at the hip,
        // then in front low over the ground, the blade far out round them
        assertTrue(turned(2,.08f,.135f,0)<-200,"hit 2's cut behind them goes round to the left: "+turned(2,.08f,.135f,0));
        assertTrue(turned(2,.235f,.3f,.3)<-200,"hit 2's cut in front goes round to the left: "+turned(2,.235f,.3f,.3));
        for(float t=.235f;t<=.28f;t+=1/240f){
            double[] p=tip(2,t);
            assertTrue(p[1]<.8,"the front cut sweeps low at "+t+": "+p[1]);assertTrue(Math.hypot(p[0]-.3,p[2])>1.8,"and far out round them at "+t);
        }
        // 3 (2.79.2 draft, the user's videos and words): down from her left across the front to her right, the same place
        // back up the other way, the mirror of the first, and the biggest rising from her left round to her right
        assertTrue(turned(3,.08f,.12f,.3)>200,"hit 3's first cut goes round to the right: "+turned(3,.08f,.12f,.3));
        assertTrue(turned(3,.36f,.41f,.3)<-200,"the second goes back the other way: "+turned(3,.36f,.41f,.3));
        assertTrue(turned(3,.54f,.58f,.3)<-200,"the third, the first's mirror, goes round to the left: "+turned(3,.54f,.58f,.3));
        assertTrue(turned(3,.84f,.9f,.3)>200,"the fourth goes round to the right: "+turned(3,.84f,.9f,.3));
        a=tip(3,.08f);b=tip(3,.12f);assertTrue(a[1]>b[1]+.8&&b[2]>.5,"the first comes down from high to low at her right: "+s(a)+" "+s(b));
        a=tip(3,.36f);b=tip(3,.41f);assertTrue(b[1]>a[1]+.8,"the second rises: "+s(a)+" "+s(b));
        // 4: the twirl goes round over the head; the low cut goes from the right to the left, low, as it throws the slash
        double swept=0,prev=Double.NaN;
        for(float t=.12f;t<=.32f;t+=1/240f){
            double[] p=tip(4,t);assertTrue(p[1]>2,"the twirl is over the head at "+t);
            if(!Double.isNaN(prev))swept+=MathHelper.wrapDegrees(azimuth(p)-prev);prev=azimuth(p);
        }
        assertTrue(Math.abs(swept)>300,"the twirl goes round: "+swept);
        // (after the blow's hit-stop has caught up)
        a=tip(4,.68f);b=tip(4,.92f);
        assertTrue(a[2]>b[2]+3&&tip(4,blow(4,0))[1]<1.2,"the low cut goes from the right to the left: "+s(a)+" "+s(b));
    }
    @Test void eachHitBeginsWhereTheOneBeforeStandsAsItHandsOver(){
        // the next hit begins where the one before stands as it may begin (IsosangRules.STEP; held or pressed early,
        // the server starts it then): the same pose, the body as far ahead, turned the same way
        for(int combo=1;combo<4;combo++){
            var now=IsosangSlash.hit(combo).frame(IsosangRules.STEP[combo]/20f);var next=IsosangSlash.hit(combo+1).frame(0);
            for(int j=0;j<6;j++)assertTrue(turn(now,next,j)<1,"hit "+combo+" hands joint "+j+" on to hit "+(combo+1)+": "+turn(now,next,j));
            assertEquals(now.fw(),next.fw(),.01,"hit "+combo+" hands its step on to hit "+(combo+1));
            assertEquals(now.up(),next.up(),.01,"hit "+combo+" height");
            assertEquals(0,MathHelper.wrapDegrees(now.yaw()-next.yaw()),1,"hit "+combo+" facing");
            assertEquals(now.grip(),next.grip(),.5,"hit "+combo+" grip");
        }
        // within a clip the body never jumps: no faster than the lunges (blocks a second at 60 fps; a jump of half a block
        // in a frame is 30)
        for(int combo=1;combo<=4;combo++){
            var a=IsosangSlash.hit(combo);
            for(float t=0;t+1/60f<=a.length();t+=1/60f){
                double v=Math.abs(a.frame(t+1/60f).fw()-a.frame(t).fw())*60;
                assertTrue(v<32,"hit "+combo+" jumps "+v+" blocks/s at "+t);
            }
        }
    }
    @Test void theBladeAndItsSlashesStayAboveTheGround(){
        // the point never goes into the floor; the slashes (well past it) graze it at most; the thrown slash flies at the waist
        for(int combo=1;combo<=4;combo++){
            var a=IsosangSlash.hit(combo);
            for(float t=0;t<=a.length();t+=1/120f)assertTrue(tip(combo,t)[1]>.02,"hit "+combo+"'s point in the ground at "+t+": "+tip(combo,t)[1]);
            for(var s:IsosangSlash.arcs(combo)){
                var sw=IsosangSlash.band(Vec3d.ZERO,0,s,s.t1());
                for(int i=0;i<sw.n();i++){
                    assertTrue(sw.inner()[i].y>-.08,"hit "+combo+"'s slash from "+s.t0()+" under the ground: "+sw.inner()[i].y);
                    assertTrue(sw.outer()[i].y>-.08,"hit "+combo+"'s slash from "+s.t0()+" under the ground: "+sw.outer()[i].y);
                    assertTrue(sw.outer()[i].y<6,"hit "+combo+"'s slash from "+s.t0()+" up in the sky: "+sw.outer()[i].y);
                }
            }
        }
        for(float t=blow(4,0);t<IsosangSlash.WAVE_EAT1;t+=.02f){
            var sw=IsosangSlash.wave(Vec3d.ZERO,0,t);
            for(int i=0;i<sw.n();i++)assertTrue(sw.inner()[i].y>.3&&sw.outer()[i].y>sw.inner()[i].y+.2&&sw.outer()[i].y<1.6,"the thrown slash lies nearly flat at the waist, its leading edge a little up (2.78.0, the video from above): "+sw.inner()[i].y+" "+sw.outer()[i].y);
        }
    }
    @Test void everySlashSweepsWithItsBladeAndIsOverBeforeItsSwingIs(){
        float life=new IsosangSlash().lifetime();
        for(int combo=1;combo<=4;combo++){
            var arcs=IsosangSlash.arcs(combo);
            for(var s:arcs){
                assertTrue(s.t0()<s.t1()&&s.t1()<=s.eat1()&&s.eat0()<s.eat1()&&s.ink0()<s.ink1(),"hit "+combo);
                assertTrue(s.eat1()+IsosangSlash.DROP_LIFE+IsosangSlash.STAIN_LIFE<=life,"hit "+combo+"'s drops and their stains are gone in time");
                boolean thin=s.style()==6||s.style()==7;
                assertTrue(s.outer()-s.inner()>(thin?.2:.75),"hit "+combo+"'s slash is a broad band: "+(s.outer()-s.inner()));
                // hits 1 and 3 sweep round to the right (2.78.0, the user's video from above), the chords aside; hit 2's two
                // cuts to the left (2.79.2 draft); hit 4's low cut is a backhand from the right to the left
                if(s.style()!=6&&combo==1)assertTrue(s.to()>s.from(),"hit "+combo+"'s slash from "+s.t0()+" sweeps clockwise seen from above");
                if(combo==2)assertTrue(s.to()<s.from(),"hit 2's cut from "+s.t0()+" sweeps anticlockwise seen from above");
            }
            // each blow lands while its slash sweeps through (2.79.2 draft: not hit 3 yet; its five blows wait for the user)
            for(int i=0;i<IsosangRules.blows(combo);i++){
                float at=blow(combo,i);
                if(combo==3){var s=arcs[IsosangSlash.arcOf(combo,i)];assertTrue(at>=s.t0()&&at<s.eat1(),"hit 3 blow "+i+" lands while its cut is out");continue;}
                if(combo==2&&i==1)continue;// the ring's burst
                var s=arcs[IsosangSlash.arcOf(combo,i)];
                assertTrue(at>=s.t0()&&at<=s.t1()+.04f,"hit "+combo+" blow "+i+" lands as its slash sweeps: "+at+" in "+s.t0()+".."+s.t1());
                // and the blade is ahead then (the server's lane is ahead): the head of the sweep passes the front near the blow
                double deg=s.from()+(s.to()-s.from())*IsosangSlash.head(s,at);
                if(combo!=2)assertTrue(Math.cos(Math.toRadians(deg))>.3,"hit "+combo+" blow "+i+": its slash's head is ahead at the blow: "+deg);
            }
        }
        assertTrue(IsosangSlash.WAVE_EAT1+IsosangSlash.DROP_LIFE+IsosangSlash.STAIN_LIFE<=life,"the thrown slash's drops and their stains are gone in time");
        for(int combo=1;combo<=4;combo++)for(var l:IsosangSlash.loops(combo))assertTrue(l.d0()<l.d1()&&l.d1()<l.e0()&&l.e0()<l.e1()&&l.e1()<=life,"hit "+combo+"'s loops of ink are drawn, then eaten away in time");
        // hit 4's ring of ink lies on the ground round the cut (2.78.0; no strokes loop in the air any more)
        assertEquals(1,IsosangSlash.loops(4).length);assertEquals(0,IsosangSlash.loops(4)[0].tilt(),1e-9);assertTrue(IsosangSlash.loops(4)[0].centre().y<.1);
        // hit 2's thin line of ink round them at the waist (2.79.2 draft: the videos' black circle inside the cuts)
        assertEquals(1,IsosangSlash.loops(2).length);assertTrue(IsosangSlash.loops(2)[0].centre().y>.6&&IsosangSlash.loops(2)[0].radius()<2,"at the waist, inside the cuts");
        // the spin cut wraps round them: more than three quarters of a turn, from the front-left to the back-left, fast
        // at first (its head past the front within a frame of the blow)
        var g=IsosangSlash.H1_ARCS[0];assertTrue(g.to()-g.from()>250&&g.to()-g.from()<300,"hit 1's crescent: three quarters round, open at the right (the video): "+(g.to()-g.from()));
        assertEquals(1,IsosangSlash.H1_ARCS.length,"one cut");
        assertTrue(g.from()>=160&&g.from()<=200&&g.to()>=430&&g.to()<=470,"from behind round the left and the front to the right: "+g.from()+".."+g.to());
        assertTrue(IsosangSlash.point(g,90,g.outer()).y>IsosangSlash.point(g,270,g.outer()).y+2.0,"high at the right, low at the left");assertTrue(g.centre().x>.6,"standing out ahead of them");
        assertTrue(IsosangSlash.point(g,270,(g.inner()+g.outer())/2).y<.8&&IsosangSlash.point(g,270,(g.inner()+g.outer())/2).y>0,"low at the left");
        assertEquals(2,g.pace());
        // its band is thin at its tail and thick at its head
        assertTrue(IsosangSlash.profile(0,.05)<.15&&IsosangSlash.profile(0,.8)>.7&&IsosangSlash.profile(0,.8)>IsosangSlash.profile(0,.4),"thin tail, thick head");
        // hit 1's great crescent (2.79.1): thin only where it began, thick through its second half
        assertEquals(9,g.style());assertTrue(IsosangSlash.profile(9,.05)<.15&&IsosangSlash.profile(9,.33)<.4&&IsosangSlash.profile(9,.7)>.95&&IsosangSlash.profile(9,.98)<.15,"a crescent: pointed tips, thin along its start, thickest at its belly");
        // hit 2 (2.79.2 draft): the cut behind them first, at the hip, then the one in front, low; the one behind gone first;
        // each thickest where it cuts (behind, ahead)
        var ba=IsosangSlash.H2_ARCS[0];var fr=IsosangSlash.H2_ARCS[1];
        assertEquals(2,IsosangSlash.H2_ARCS.length);
        assertTrue(ba.t1()<fr.t0()&&ba.eat1()<fr.eat1(),"behind first, in front after; the one behind gone first");
        double bf=ba.from()+(ba.to()-ba.from())*.7,ff=fr.from()+(fr.to()-fr.from())*.7;
        assertTrue(IsosangSlash.point(ba,bf,ba.outer()).x<-1&&IsosangSlash.point(fr,ff,fr.outer()).x>1.5,"one behind them, one in front");
        assertTrue(ba.centre().y>.5&&fr.centre().y<.3,"the one behind at the hip, the one in front low");
        // hit 3 (2.79.2 draft): four cuts; the second in the first's place the other way; the third the first's mirror
        // (high at her right); the fourth the biggest and nearly level; the first two high at her left; the second lasting
        // until the third crosses it, both gone as the fourth comes
        var c1=IsosangSlash.H3_ARCS[0];var c2=IsosangSlash.H3_ARCS[1];var c3=IsosangSlash.H3_ARCS[2];var c4=IsosangSlash.H3_ARCS[3];
        assertEquals(4,IsosangSlash.H3_ARCS.length);
        assertTrue(c1.to()>c1.from()&&c2.to()<c2.from()&&c3.to()<c3.from()&&c4.to()>c4.from(),"down, back up, the mirror down, the big one round to the right");
        assertTrue(c1.centre().equals(c2.centre())&&c1.a().equals(c2.a())&&c1.from()==c2.to()&&c1.to()==c2.from(),"the second in the first's place, the other way");
        assertTrue(IsosangSlash.point(c1,-90,c1.outer()).y>IsosangSlash.point(c1,90,c1.outer()).y+2,"the first high at her left");
        assertTrue(IsosangSlash.point(c3,90,c3.outer()).y>IsosangSlash.point(c3,-90,c3.outer()).y+2,"the third high at her right");
        assertTrue(c4.outer()>c3.outer()+1&&Math.abs(IsosangSlash.point(c4,90,c4.outer()).y-IsosangSlash.point(c4,-90,c4.outer()).y)<2,"the fourth the biggest, nearly level");
        assertTrue(c2.eat1()>c3.t1()&&c2.eat1()<=c4.t0()+.05f&&c3.eat1()<=c4.t0()+.05f,"the second lasts until the third crosses it; both are gone as the fourth comes");
        assertTrue(c4.eat1()>c1.eat1()+.5f,"the fourth lasts longest");
        assertTrue(IsosangSlash.profile(11,.3)>.95&&IsosangSlash.profile(11,.95)<.2,"the cuts down thickest near their start");
    }
    @Test void theFeetStayOnTheGround(){
        // the legs are rigid: a body brought down is a wide stance or a lunge, never a foot in the ground; one foot always on it
        for(int combo=1;combo<=4;combo++){
            var a=IsosangSlash.hit(combo);
            for(float t=0;t<=a.length();t+=1/120f){
                double[] f=IsosangSlash.feet(a.frame(t));
                assertTrue(f[0]>-.02&&f[1]>-.02,"hit "+combo+" puts a foot into the ground at "+t+": "+f[0]+" "+f[1]);
                assertTrue(Math.min(f[0],f[1])<.05,"hit "+combo+" has no foot on the ground at "+t+": "+f[0]+" "+f[1]);
            }
        }
    }
    @Test void theCuttingEdgeShinesOutermostAndTheTailGoesToInkFirst(){
        // the bright line along every slash's outer edge (the videos': light outside, ink inside)
        for(int style:new int[]{0,1,3,6})assertTrue(IsosangSlash.lineAt(style,1)>.9,"style "+style);
        // each part of a slash turns to ink after its blade swept it: the tail before the head
        var s=IsosangSlash.H1_ARCS[0];
        for(float t=s.t1();t<s.ink1()+(s.t1()-s.t0());t+=.01f){
            var sw=IsosangSlash.band(Vec3d.ZERO,0,s,t);float[] ink=IsosangSlash.inks(s,sw,t);
            for(int i=1;i<ink.length;i++)assertTrue(ink[i]<=ink[i-1]+1e-6,"its head goes to ink before its tail at "+t);
            if(t>s.ink0()+.05f&&t<s.ink1())assertTrue(ink[0]>ink[ink.length-1],"its tail is further gone than its head at "+t);
        }
    }
    @Test void theInkSoaksIntoTheGroundAndIsGoneWithinASecond(){
        assertEquals(0,IsosangSlash.dried(0),1e-6);assertEquals(1,IsosangSlash.dried(IsosangSlash.STAIN_LIFE),1e-6);
        assertTrue(IsosangSlash.STAIN_LIFE<1,"gone within a second, as in the videos");
        // the pools under the slashes (2.78.0) go sooner still: half a second, as the video from above has them
        assertTrue(IsosangSlash.POOL_LIFE<=.6f);assertEquals(1,IsosangSlash.dried(IsosangSlash.POOL_LIFE,IsosangSlash.POOL_LIFE),1e-6);
        // the ink front of a band: deep into a thick part, barely into a thin one, spreading out as it ages
        assertTrue(IsosangSlash.inkFront(1,0)<.45&&IsosangSlash.inkFront(.1,0)>.85,"ink fills the thick parts, not the thin");
        assertTrue(IsosangSlash.inkFront(1,1)<IsosangSlash.inkFront(1,0)-.12,"and spreads outward as it dries");
        assertTrue(IsosangSlash.inkFront(1,1.0,0)>.85&&IsosangSlash.inkFront(1,1.0,1)<.4,"the ring is light all through until it dries");
        // a drop lands where its flight meets the ground (and stains it there); one that cannot reach it does not
        var d=new IsosangSlash.Drop(new Vec3d(0,1.6,0),new Vec3d(2,1,0),3.2,3.4,0,IsosangSlash.DROP_LIFE,.1,7,0);
        double land=IsosangSlash.lands(d,.03);
        assertTrue(land>0&&land<IsosangSlash.DROP_LIFE);assertEquals(.03,IsosangSlash.flight(d,land).y,1e-3);
        assertTrue(IsosangSlash.flight(d,land*.9).y>.03);
        assertEquals(-1,IsosangSlash.lands(new IsosangSlash.Drop(new Vec3d(0,40,0),new Vec3d(0,1,0),3.2,3.4,0,.5f,.1,7,0),.03),1e-9);
    }
    @Test void flyingInkShrinksAndFadesByTheCamera(){
        // 2.77.6: larger now, a drop by the eye covered the screen like a slab; the struck see none splashed from their own face
        Vec3d cam=new Vec3d(0,1.6,0);
        assertEquals(0,IsosangSlash.near(cam.add(.9,0,0),cam),1e-6,"gone by the eye");
        assertEquals(1,IsosangSlash.near(cam.add(0,0,2.7),cam),1e-6,"as it was further out");
        float prev=0;
        for(double r=1;r<=2.6;r+=.05){float n=IsosangSlash.near(cam.add(r,0,0),cam);assertTrue(n>=prev-1e-6&&n-prev<.1f,"fading smoothly as it nears: "+r);prev=n;}
    }
    @Test void theThrownSlashFliesWhereTheServerStrikes(){
        float at=blow(4,0);
        assertNull(IsosangSlash.wave(Vec3d.ZERO,0,at-.01f),"not before it is thrown");
        for(int k=2;k<=IsosangRules.WAVE_TICKS;k++){
            var sw=IsosangSlash.wave(Vec3d.ZERO,0,at+k/20f);
            Vec3d mid=sw.outer()[sw.n()/2],tipL=sw.outer()[0],tipR=sw.outer()[sw.n()-1];
            assertEquals(IsosangRules.waveFront(k),mid.z,.01,"its leading edge where the server has it after "+k+" ticks");
            assertEquals(IsosangRules.waveHalf(k),-tipR.x,.01,"its right tip where the server has it");
            assertEquals(IsosangRules.waveHalf(k),tipL.x,.01,"its left tip where the server has it");
            assertEquals(IsosangRules.waveFront(k)-IsosangRules.waveCurve(k),tipR.z,.01,"its tips trailing");
        }
        // the client shows each struck as the band reaches them, the moment the server strikes them
        for(double fw=1;fw<IsosangRules.WAVE_RANGE-.5;fw+=1.5){
            float when=IsosangSlash.reaches(fw,0,.3);int tick=-1;
            for(int k=0;k<=IsosangRules.WAVE_TICKS&&tick<0;k++)if(IsosangRules.waveSweeps(Math.max(0,k-1),k,fw,0,1,.3))tick=k;
            assertTrue(when>=at&&tick>=0,"reached at "+fw);
            assertTrue(Math.abs((when-at)*20-tick)<=1,"shown "+(when-at)*20+" ticks in, struck on tick "+tick+" at "+fw);
        }
        assertEquals(-1,IsosangSlash.reaches(IsosangRules.WAVE_RANGE+2,0,.3),1e-6);
        assertNull(IsosangSlash.wave(Vec3d.ZERO,0,IsosangSlash.WAVE_EAT1),"gone once it has dried away");
    }
    @Test void aSlashSeenEdgeOnDoesNotStandUpAsAPillarOfLight(){
        // a flat band on the ground: its glow (turned to the camera) whole from above, gone to its least seen edge on
        var sw=new IsosangSlash.Swept(new Vec3d[]{new Vec3d(0,0,0),new Vec3d(1,0,0)},new Vec3d[]{new Vec3d(0,0,1),new Vec3d(1,0,1)},new float[]{0,1},2);
        Vec3d o=new Vec3d(0,0,.5);
        assertEquals(1,IsosangSlash.faceOn(sw,0,o.add(0,5,0)),1e-6,"from above");
        assertEquals(1,IsosangSlash.faceOn(sw,0,o.add(0,-5,0)),1e-6,"from below");
        assertEquals(0,IsosangSlash.faceOn(sw,0,o.add(9,0,0)),1e-6,"edge on");
        assertEquals(1,IsosangSlash.faceOn(sw,0,o.add(5*Math.cos(Math.toRadians(25)),5*Math.sin(Math.toRadians(25)),0)),1e-6,"25° out of its plane");
        float prev=0;
        for(int deg=0;deg<=30;deg++){
            float f=IsosangSlash.faceOn(sw,0,o.add(5*Math.cos(Math.toRadians(deg)),5*Math.sin(Math.toRadians(deg)),0));
            assertTrue(f>=prev-1e-6&&f-prev<.2f,"brightening smoothly as the view leaves its plane: "+deg+"° "+f);prev=f;
        }
        // the band itself: a third of it edge on, all of it from 9° out of its plane
        assertEquals(.35,IsosangSlash.shown(sw,o.add(9,0,0)),1e-6,"edge on");
        assertEquals(1,IsosangSlash.shown(sw,o.add(5*Math.cos(Math.toRadians(9)),5*Math.sin(Math.toRadians(9)),0)),1e-6,"9° out of its plane");
        assertEquals(1,IsosangSlash.shown(sw,o.add(0,5,0)),1e-6,"from above");
    }
    @Test void theStarsShineOnTheSlashesWhereTheirLightAndInkAre(){
        for(int combo=1;combo<=4;combo++)for(var s:IsosangSlash.arcs(combo)){
            assertTrue(IsosangSlash.stars(s,s.t0()-.01f).isEmpty(),"none before it sweeps");
            assertTrue(IsosangSlash.stars(s,s.eat1()).isEmpty(),"none once it is eaten away");
            int light=0,night=0;
            for(float t=s.t0();t<s.eat1();t+=1/120f){
                float h=IsosangSlash.head(s,t);
                for(var st:IsosangSlash.stars(s,t)){
                    double f=(st.deg()-s.from())/(s.to()-s.from());
                    assertTrue(f>=0&&f<=h,"hit "+combo+": a star only where the blade has passed");
                    assertTrue(st.radius()>=s.inner()-1e-9&&st.radius()<=s.outer()+1e-9,"hit "+combo+": within the band");
                    assertTrue(st.alpha()>0&&st.alpha()<=1);
                    // the light's stars outward of the shader's ink front there, the night's inward of it
                    float swept=s.t0()+(s.t1()-s.t0())*(float)f,ink=Ease.smooth(t,swept+s.ink0()-s.t0(),swept+s.ink1()-s.t0());
                    double prof=IsosangSlash.profile(s.style(),f/h),inkAt=IsosangSlash.inkFront(s.style(),prof,ink),d=(1-(st.radius()-s.inner())/(s.outer()-s.inner()))/prof;
                    if(st.night()){night++;assertTrue(d>inkAt+.05,"hit "+combo+": a night star in the ink");}
                    else{light++;assertTrue(d<inkAt-.05,"hit "+combo+": a star in the light");}
                }
            }
            assertTrue(light>0&&night>0,"hit "+combo+" has stars in its light and in its ink: "+light+", "+night);
        }
        for(int seed=0;seed<50;seed++)for(float t=0;t<2;t+=1/60f){
            float w=IsosangSlash.twinkle(t,seed);assertTrue(w>=.3f-1e-6&&w<=1+1e-6);
            assertTrue(Math.abs(IsosangSlash.twinkle(t+1/60f,seed)-w)<.5f,"twinkling, not flashing on and off from frame to frame");
        }
    }
    @Test void theFinishersCameraDrawsBackAndComesInSmoothly(){
        for(int combo=1;combo<=3;combo++)for(float t=0;t<2;t+=.05f)assertEquals(0,IsosangSlash.pull(combo,t),1e-9);
        double most=0,jump=0;
        for(float t=0;t<2;t+=1/60f){most=Math.max(most,IsosangSlash.pull(4,t));jump=Math.max(jump,Math.abs(IsosangSlash.pull(4,t+1/60f)-IsosangSlash.pull(4,t)));}
        assertEquals(IsosangSlash.PULL,most,.01,"all the way back while the slash flies");
        assertTrue(jump<.2,"no jump in a frame: "+jump);
        assertEquals(0,IsosangSlash.pull(4,0),1e-9);assertEquals(0,IsosangSlash.pull(4,IsosangRules.LENGTH[4]/20f),1e-9);
        assertEquals(IsosangSlash.PULL,IsosangSlash.pull(4,blow(4,0)+.2f),.05,"drawn back by the time the slash is out");
    }
}
