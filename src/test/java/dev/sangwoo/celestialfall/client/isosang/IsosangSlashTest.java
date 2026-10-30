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
            assertTrue(p[0]>1.2,"hit "+combo+" blow "+i+" ahead: "+s(p));
            assertTrue(IsosangRules.lane(combo,i,p[0],p[2],0,.3),"hit "+combo+" blow "+i+" inside its way: "+s(p));
        }
    }
    @Test void eachHitCutsTheWayItsVideoShows(){
        // 1: the sword flashed up at the right side; the great cut from the high right down to the low left; the long cut
        // rising straight back up to the high right; held up there
        double[] a=tip(1,.05f);assertTrue(a[1]>2.2&&a[2]>.6,"hit 1 begins with the sword up at the right: "+s(a));
        a=tip(1,.27f);double[] b=tip(1,.33f);
        assertTrue(a[1]>b[1]+1.2&&a[2]>b[2]+2.5,"hit 1 comes down from the high right to the low left: "+s(a)+" "+s(b));
        a=tip(1,.33f);b=tip(1,.4f);
        assertTrue(b[1]>a[1]+1.6&&b[2]>a[2]+2.5,"hit 1 cuts back up to the high right: "+s(a)+" "+s(b));
        b=tip(1,.55f);assertTrue(b[1]>2.4&&b[2]>.8,"hit 1 holds the sword up at the right: "+s(b));
        // 2: a whole spin low over the ground, far out round them
        double swept=0,prev=Double.NaN;
        for(float t=.1f;t<=.19f;t+=1/240f){
            double[] p=tip(2,t);
            assertTrue(Math.hypot(p[0]-.38,p[2])>2,"hit 2 holds the blade far out round them at "+t);assertTrue(p[1]<.9,"hit 2 sweeps low at "+t+": "+p[1]);
            if(!Double.isNaN(prev))swept+=MathHelper.wrapDegrees(azimuth(p)-prev);prev=azimuth(p);
        }
        assertTrue(Math.abs(swept)>330,"hit 2 turns the blade all the way round them: "+swept);
        // 3: the thrust straight ahead; the cut down from the high right to the low left; back up from the low left to the
        // high right; the sweep low from the right to the left; the wide sweep at the head from the left to the right
        var hb=IsosangSlash.blade(Vec3d.ZERO,0,3,blow(3,0));
        assertTrue(hb[1].z>.97,"the thrust points ahead: "+hb[1]);
        a=tip(3,.44f);b=tip(3,.54f);
        assertTrue(a[1]>b[1]+1.5&&a[2]>b[2]+2,"the cut comes down from the high right to the low left: "+s(a)+" "+s(b));
        a=tip(3,.64f);b=tip(3,.72f);
        assertTrue(b[1]>a[1]+1.5&&b[2]>a[2]+2.5,"the cut rises back to the high right: "+s(a)+" "+s(b));
        a=tip(3,.77f);b=tip(3,.82f);
        assertTrue(a[2]>b[2]+2.5&&tip(3,blow(3,3))[1]<1,"the sweep goes low from the right to the left: "+s(a)+" "+s(b));
        a=tip(3,.86f);b=tip(3,.99f);
        assertTrue(b[2]>a[2]+3&&tip(3,blow(3,4))[1]>1.1,"the wide sweep goes across at the head from the left to the right: "+s(a)+" "+s(b));
        // 4: the twirl goes round over the head; the low cut goes from the right to the left, low, as it throws the slash
        prev=Double.NaN;swept=0;
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
            for(int i=0;i<sw.n();i++)assertTrue(sw.inner()[i].y>.5&&sw.outer()[i].y<1.6,"the thrown slash flies at the waist");
        }
    }
    @Test void everySlashSweepsWithItsBladeAndIsOverBeforeItsSwingIs(){
        float life=new IsosangSlash().lifetime();
        for(int combo=1;combo<=4;combo++){
            var arcs=IsosangSlash.arcs(combo);
            for(var s:arcs){
                assertTrue(s.t0()<s.t1()&&s.t1()<=s.eat1()&&s.eat0()<s.eat1()&&s.ink0()<s.ink1(),"hit "+combo);
                assertTrue(s.eat1()+IsosangSlash.DROP_LIFE+IsosangSlash.STAIN_LIFE<=life,"hit "+combo+"'s drops and their stains are gone in time");
                assertTrue(s.outer()-s.inner()>.9,"hit "+combo+"'s slash is a broad band: "+(s.outer()-s.inner()));
            }
            // each blow lands while its slash sweeps through (the thrust's beam is lit for it)
            for(int i=0;i<IsosangRules.blows(combo);i++){
                float at=blow(combo,i);
                if(combo==3&&i==0){assertTrue(IsosangSlash.beamLit(at+.01f)>.9f,"the beam is out for the thrust");continue;}
                if(combo==2&&i==1)continue;// the ring's burst
                var s=arcs[combo==3?i-1:Math.min(i,arcs.length-1)];
                assertTrue(at>=s.t0()&&at<=s.t1()+.04f,"hit "+combo+" blow "+i+" lands as its slash sweeps: "+at+" in "+s.t0()+".."+s.t1());
            }
        }
        assertTrue(IsosangSlash.WAVE_EAT1+IsosangSlash.DROP_LIFE+IsosangSlash.STAIN_LIFE<=life,"the thrown slash's drops and their stains are gone in time");
        for(int combo=1;combo<=4;combo++)for(var l:IsosangSlash.loops(combo))assertTrue(l.d0()<l.d1()&&l.d1()<l.e0()&&l.e0()<l.e1()&&l.e1()<=life,"hit "+combo+"'s loops of ink are drawn, then eaten away in time");
        assertEquals(0,IsosangSlash.beamLit(IsosangSlash.BEAM_OFF),1e-6);
        float step=0;for(float t=0;t<1;t+=1/240f)step=Math.max(step,Math.abs(IsosangSlash.beamLit(t+1/60f)-IsosangSlash.beamLit(t)));
        assertTrue(step<.75f,"the beam pops in a single frame: "+step);
        // the great cut wraps round them: more than three quarters of a turn
        var g=IsosangSlash.H1_ARCS[0];assertTrue(Math.abs(g.to()-g.from())>250,"hit 1's crescent wraps round: "+(g.to()-g.from()));
        // the ring goes all the way round
        var r=IsosangSlash.H2_ARCS[0];assertEquals(360,Math.abs(r.to()-r.from()),1e-6);
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
        // a drop lands where its flight meets the ground (and stains it there); one that cannot reach it does not
        var d=new IsosangSlash.Drop(new Vec3d(0,1.6,0),new Vec3d(2,1,0),3.2,3.4,0,IsosangSlash.DROP_LIFE,.1,7,0);
        double land=IsosangSlash.lands(d,.03);
        assertTrue(land>0&&land<IsosangSlash.DROP_LIFE);assertEquals(.03,IsosangSlash.flight(d,land).y,1e-3);
        assertTrue(IsosangSlash.flight(d,land*.9).y>.03);
        assertEquals(-1,IsosangSlash.lands(new IsosangSlash.Drop(new Vec3d(0,40,0),new Vec3d(0,1,0),3.2,3.4,0,.5f,.1,7,0),.03),1e-9);
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
        for(double fw=1;fw<12;fw+=1.5){
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
                    double inkAt=.8-.63*(1-(1-ink)*(1-ink)),d=(1-(st.radius()-s.inner())/(s.outer()-s.inner()))/IsosangSlash.profile(s.style(),f/h);
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
