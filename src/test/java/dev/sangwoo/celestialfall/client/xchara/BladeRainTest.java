package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.xchara.RainRules;
import dev.sangwoo.celestialfall.xchara.ShotRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's H with their hack off, the rain of knives, as drawn (2.132.0, the user's words: a run, a stab at whoever is
 * ahead, the knife kept in them while a great many knives lit as the hack lights them pour in from every side): the
 * run leans far forward and stands up again; the stab ends with the knife out ahead and stays there until the last
 * volley, borne on standing and straining with both hands on it (2.132.1); the knives come out of the sky straight
 * down like rain, not at the one stabbed (2.132.4: "비처럼 적을 향하게 만들지말고"), one of each volley on them and the
 * rest all over the ground 30 blocks round them (none where its user stands), not too fast, and stand where they land a
 * moment; what the camera cannot see is not drawn; nothing jumps.
 */
class BladeRainTest {
    static final Vec3d FEET=new Vec3d(10,64,5),USER=new Vec3d(-2.7,0,0);static final float NEVER=Float.MAX_VALUE;static final int SEED=4242;
    static BladeRain.Spot spot(int k){return BladeRain.spot(k,SEED,FEET,.3,USER);}
    static double land(int k){return BladeRain.landY(spot(k),FEET,1.8,FEET.y);}
    static BladeRain.Pose pose(int k,float age){return pose(k,age,NEVER);}
    static BladeRain.Pose pose(int k,float age,float stop){return BladeRain.pose(k,SEED,age,stop,spot(k),land(k));}

    @Test void theRunLeansFarForwardAndStandsUpAgain(){
        var run=BladeRain.RUN;
        assertSame(run,KnifeCombo.clipOf(RUSH));assertSame(BladeRain.STAB,KnifeCombo.clipOf(RAIN));
        assertEquals(LENGTH[RUSH]/20f,run.length(),1e-4);assertEquals(LENGTH[RAIN]/20f,BladeRain.STAB.length(),1e-4);
        assertEquals(BladeRain.RUSH_LIFE,KnifeCombo.life(RUSH),1e-6);assertEquals(BladeRain.LIFE,KnifeCombo.life(RAIN),1e-6);
        var rest=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(KnifeCombo.AT_REST));
        for(var clip:new dev.sangwoo.celestialfall.client.kageno.Anim[]{run,BladeRain.STAB}){
            var end=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(clip.length())));
            assertEquals(0,rest[0].distanceTo(end[0]),1e-4,"back to the stance");assertEquals(0,rest[1].distanceTo(end[1]),1e-4);
        }
        var start=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(run.frame(0)));
        assertEquals(0,rest[0].distanceTo(start[0]),1e-4,"from the stance");
        for(float t=.2f;t<=.6f;t+=.05f){
            var f=run.frame(t);
            assertTrue(f.pitch()>40,"thrown far forward at "+t+": "+f.pitch());assertTrue(f.up()<-.2);
            var b=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(f));
            assertTrue(b[1].z<-.5,"the knife trails behind");
        }
        assertTrue(run.frame(RainRules.RUSH_HOLD/20f).pitch()<10,"stood up by the time they are free");
    }
    @Test void theKnifeGoesInAheadAndStaysThereUntilTheLastVolley(){
        var clip=BladeRain.STAB;float stab=RainRules.STAB/20f;
        var wound=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(.21f)));
        var in=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(stab+.02f)));
        assertTrue(in[0].z>wound[0].z+.8,"the hand driven out ahead: "+wound[0].z+" to "+in[0].z);
        assertTrue(clip.frame(.21f).yaw()<-25&&clip.frame(stab).yaw()>10,"wound one way and driven round the other");
        for(float t=stab+.05f;t<=RainRules.WITHDRAW/20f;t+=.05f){
            var b=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(t)));
            assertTrue(b[1].z>.9,"the knife ahead at "+t+": "+b[1].z);assertTrue(Math.abs(b[1].y)<.25,"level");
            Vec3d tip=b[0].add(b[1].multiply(ShotRules.TIP));
            assertTrue(tip.z>RainRules.KEEP+.6&&tip.z<RainRules.KEEP+1.3,"its point through the one they stopped short of at "+t+": "+tip.z);
            assertTrue(tip.y>.7&&tip.y<1.6,"at the body's height: "+tip.y);
        }
        // (2.132.1, user requests: "버틸때 양손으로 버티고 정자세로 서서 버텨", "서서 버티는데 버티고 있다는 자세를
        // 만들어줘 힘 줘서 버티고 있다는") borne on through the rain standing and straining: square to them and leaning
        // into the knife, the knees bent and set, the feet planted apart (the left behind to push from), the head down,
        // both hands on the knife's rod, the body shaking with the strain
        double shook=0;R6.Frame was=null;
        for(float t=stab+.3f;t<=RainRules.WITHDRAW/20f;t+=1/60f){
            var fr=clip.frame(t);var p=R6.poseOf(fr);
            assertTrue(fr.pitch()>9&&fr.pitch()<21,"leaning into it at "+t+": "+fr.pitch());
            assertTrue(Math.abs(fr.yaw())<5&&Math.abs(fr.roll())<3,"square to them at "+t+": "+fr.yaw()+", "+fr.roll());
            assertTrue(fr.up()<-.07&&fr.up()>-.17,"standing, a little sunk at "+t+": "+fr.up());
            assertTrue(p.rKnee>8&&p.rKnee<50&&p.lKnee>8&&p.lKnee<50,"the knees bent and set at "+t+": "+p.rKnee+", "+p.lKnee);
            double[] r=KnifeComboTest.foot(fr,0),l=KnifeComboTest.foot(fr,1);
            assertTrue(r[2]-l[2]>.35&&r[2]-l[2]<.8,"the left foot behind to push from at "+t+": "+r[2]+", "+l[2]);
            assertTrue(r[0]-l[0]>.38&&r[0]-l[0]<.62,"planted apart at "+t+": "+(r[0]-l[0]));
            assertTrue(p.headPitch+fr.pitch()>8,"the head down at them at "+t);
            var b=KagenoClips.blade(Vec3d.ZERO,0,p);Vec3d rod=b[0].subtract(b[1].multiply(GiantCleave.GRIP_BACK));
            assertTrue(KagenoClips.freeHand(Vec3d.ZERO,0,p).distanceTo(rod)<.04,"the other hand on the rod at "+t+": "+KagenoClips.freeHand(Vec3d.ZERO,0,p).distanceTo(rod));
            assertTrue(b[0].y>.8&&b[0].y<1.15&&Math.abs(b[0].x)<.15,"the hands low before the middle of the body at "+t+": "+b[0]);
            if(was!=null){
                double d=Math.abs(fr.pitch()-was.pitch())+Math.abs(fr.roll()-was.roll());shook=Math.max(shook,d);
                assertTrue(d<4,"shaking, not jumping at "+t+": "+d);
            }
            was=fr;
        }
        assertTrue(shook>.6,"the strain shakes them: "+shook);
        assertTrue(BladeRain.strain(stab)==0&&BladeRain.strain(stab+.4f)>.5f&&BladeRain.strain(RainRules.WITHDRAW/20f+.1f)==0,"shaking only while they bear on it");
        // the free arm thrown back as it goes in; off the rod again once it is torn out
        var thrown=R6.poseOf(clip.frame(stab+.05f));var drawn=R6.poseOf(clip.frame(RainRules.WITHDRAW/20f+.12f));
        for(var p:new R6.Pose[]{thrown,drawn}){
            var b=KagenoClips.blade(Vec3d.ZERO,0,p);
            assertTrue(KagenoClips.freeHand(Vec3d.ZERO,0,p).distanceTo(b[0].subtract(b[1].multiply(GiantCleave.GRIP_BACK)))>.5,"one hand on it");
        }
        assertEquals(0,BladeRain.both(stab),1e-6);assertEquals(1,BladeRain.both(stab+.3f),1e-6);assertEquals(1,BladeRain.both(RainRules.WITHDRAW/20f),1e-6);assertEquals(0,BladeRain.both(RainRules.WITHDRAW/20f+.1f),1e-6);
        // drawn out: the hand back
        var out=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(RainRules.HOLD/20f)));
        assertTrue(out[0].z<in[0].z-.6);
        // lit while it is in, black before and after
        assertNull(BladeRain.lit(stab-.01f,Float.NaN));
        assertEquals(1,BladeRain.lit(stab+.3f,Float.NaN)[0]);assertEquals(.3f,BladeRain.lit(stab+.3f,Float.NaN)[1],1e-5);
        assertEquals(0,BladeRain.lit(RainRules.WITHDRAW/20f+.1f,Float.NaN)[0]);assertNull(BladeRain.lit(RainRules.WITHDRAW/20f+.5f,Float.NaN));
        assertEquals(0,BladeRain.lit(1f,.9f)[0],"stopped short: it goes out then");assertNull(BladeRain.lit(.1f,.05f));
    }
    @Test void nothingOfTheBodyJumpsAndTheKnifeKeepsOffTheGround(){
        for(var clip:new dev.sangwoo.celestialfall.client.kageno.Anim[]{BladeRain.RUN,BladeRain.STAB}){
            Vec3d lastFist=null,lastHand=null;float lastYaw=0,lastPitch=0;
            for(float t=0;t<=clip.length();t+=1/60f){
                var fr=clip.frame(t);var b=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(fr));Vec3d hand=KagenoClips.freeHand(Vec3d.ZERO,0,R6.poseOf(fr));
                assertTrue(b[0].add(b[1].multiply(ShotRules.TIP)).y>.1,"the knife's point clear of the ground at "+t);
                if(lastFist!=null){
                    assertTrue(b[0].distanceTo(lastFist)<.45,"the knife's hand at "+t+": "+b[0].distanceTo(lastFist));
                    assertTrue(hand.distanceTo(lastHand)<.45,"the free hand at "+t+": "+hand.distanceTo(lastHand));
                    assertTrue(Math.abs(fr.yaw()-lastYaw)<16&&Math.abs(fr.pitch()-lastPitch)<12,"the body at "+t);
                }
                lastFist=b[0];lastHand=hand;lastYaw=fr.yaw();lastPitch=fr.pitch();
            }
        }
    }
    @Test void theBodyIsDrawnAlongTheRunAndTheStabsLastWay(){
        Vec3d from=new Vec3d(0,64,0),to=new Vec3d(0,64,8);
        assertEquals(0,BladeRain.ran(from,to,0).distanceTo(from),1e-9);
        Vec3d last=from;
        for(float t=0;t<=30;t+=.25f){
            Vec3d at=BladeRain.ran(from,to,t);
            assertTrue(at.z>=last.z-1e-9&&at.z<=8+1e-9);assertEquals(RainRules.ran(t,8),at.z,1e-6);
            assertTrue(at.distanceTo(last)<=RainRules.SPEED*.25+1e-6,"no jump");last=at;
        }
        assertEquals(from,BladeRain.ran(from,from,9),"nowhere to run: where they are");
        Vec3d a=new Vec3d(0,64,5),b=new Vec3d(0,64.5,7);
        assertEquals(0,BladeRain.lunged(a,b,0).distanceTo(a),1e-9);assertEquals(0,BladeRain.lunged(a,b,RainRules.ARRIVE).distanceTo(b),1e-9);
        assertEquals(1,BladeRain.held(RainRules.ARRIVE,RainRules.ARRIVE));assertEquals(0,BladeRain.held(RainRules.ARRIVE+10,RainRules.ARRIVE));
    }
    @Test void theKnivesComeDownOnThemAndAllOverTheGroundRoundThemButNotWhereTheUserStands(){
        // (2.132.1, user request: "칼이 더 많이 떨어지게 바꾸고 적 방향으로 떨어지게 하지 말고 비 처럼 일자로 떨어지게";
        // 2.132.2: "15블록으로"; 2.132.4: "아니다 걍 주변 30블록으로 검들이 떨어지게 바꾸자 비처럼 적을 향하게 만들지말고")
        assertEquals(30,BladeRain.ZONE,1e-9,"the ground 30 blocks round (2.132.2: 15)");
        assertEquals(6480,RainRules.BLADES,"as many more as the ground is wider (2.132.2's 1620 over 15 blocks round)");
        assertEquals(.6,BladeRain.SIZE,1e-9,"as big as 2.132.2's: .6 of the knife in the hand");
        // as thick on the ground as 2.132.2's were (knives on the ground a volley over the ground they come down on)
        double was=53/(Math.PI*(15*15-.55*.55)),now=(RainRules.PER-1)/(Math.PI*(BladeRain.ZONE*BladeRain.ZONE-.55*.55));
        assertTrue(now>=was*.97,"as thick on the ground: "+now+" a square block a volley, 2.132.2 "+was);
        int[] quarter=new int[4],ring=new int[3];int on=0;Vec3d user=FEET.add(USER);
        for(int k=0;k<RainRules.BLADES;k++){
            var s=spot(k);double dx=s.x()-FEET.x,dz=s.z()-FEET.z,r=Math.hypot(dx,dz);
            if(s.body()){
                on++;assertTrue(Math.abs(dx)<=.3&&Math.abs(dz)<=.3,"on them: "+dx+", "+dz);
                assertEquals(FEET.y+1.8-BladeRain.BODY_DEEP,land(k),1e-9,"into their head and shoulders");
                continue;
            }
            assertTrue(r>.5&&r<=BladeRain.ZONE+1e-6,"on the ground round them: "+r);
            assertTrue(Math.hypot(s.x()-user.x,s.z()-user.z)>=BladeRain.CLEAR-1e-6,"none where its user stands: knife "+k);
            assertEquals(FEET.y-BladeRain.GROUND_DEEP,land(k),1e-9,"into the ground");
            quarter[(int)Math.floor((Math.atan2(dz,dx)+Math.PI)/(Math.PI/2))%4]++;
            ring[r<10?0:r<20?1:2]++;
        }
        assertEquals(RainRules.VOLLEYS,on,"one on them with each volley (as many as the server's blows)");
        for(int q:quarter)assertTrue(q>=1450,"all over the ground round them: "+java.util.Arrays.toString(quarter));
        // evenly over it: as many on each ring as its share of the ground (1, 3 and 5 ninths)
        int all=ring[0]+ring[1]+ring[2];
        assertEquals(1/9.0,ring[0]/(double)all,.02,"near them: "+java.util.Arrays.toString(ring));
        assertEquals(3/9.0,ring[1]/(double)all,.02);assertEquals(5/9.0,ring[2]/(double)all,.02,"out to 30 blocks");
        assertEquals(spot(5),spot(5),"the same on every client");
    }
    @Test void eachComesOutOfTheSkyFallsStraightDownAndStandsWhereItLands(){
        for(int k:new int[]{0,1,31,62,119,240,359,479,700,1049,1350,1619,2160,3333,4321,5000,6047,6479}){
            float lands=(float)RainRules.landsAt(k),comes=lands-BladeRain.BEFORE;
            assertNull(pose(k,comes-.05f),"nothing of it before it comes");
            var top=pose(k,comes+.01f);
            assertNotNull(top);assertTrue(top.flying()&&!top.in());
            double high=top.tip().y-land(k);
            assertTrue(high>=BladeRain.SKY-.1&&high<=BladeRain.SKY+BladeRain.SKY_MORE,"out of the sky high over where it lands: "+high);
            // straight down, not at the one stabbed: point down, its line never off where it lands, a little quicker as it goes
            double last=Double.MAX_VALUE,step=0;
            for(float t=comes+.25f;t<lands;t+=.25f){
                var p=pose(k,t);assertTrue(p.flying());
                assertEquals(0,p.dir().distanceTo(BladeRain.DOWN),1e-9,"pointing straight down");
                assertEquals(spot(k).x(),p.tip().x,1e-9);assertEquals(spot(k).z(),p.tip().z,1e-9);
                double y=p.tip().y;assertTrue(y<last,"falling");
                if(last!=Double.MAX_VALUE){assertTrue(last-y>=step-1e-6,"a little quicker as it goes");step=last-y;}
                last=y;
            }
            var in=pose(k,lands+1.5f);
            assertTrue(in.in()&&!in.flying());assertEquals(land(k),in.tip().y,.01,"its point in where it landed");
            assertEquals(spot(k).body(),in.body());
            assertNull(pose(k,lands+BladeRain.STUCK+.01f),"and gone");
        }
        // a great many at once through the rain
        int most=0;
        for(float t=RainRules.FIRST;t<=RainRules.LAST;t+=.5f){int n=0;for(int k=0;k<RainRules.BLADES;k++)if(pose(k,t)!=null)n++;most=Math.max(most,n);}
        assertTrue(most>=2000,"pouring like rain: "+most+" at once");
        // and wide: some out near the edge of the ground they come down on
        double far=0;for(int k=0;k<RainRules.BLADES;k++){var s=spot(k);far=Math.max(far,Math.hypot(s.x()-FEET.x,s.z()-FEET.z));}
        assertTrue(far>BladeRain.ZONE-.3,"out to the edge of it: "+far);
    }
    /** (2.132.4) "칼이 너무 빠르게 떨어져" (said of 2.132.2's, which landed at some three blocks a tick): as slow as 2.132.3's at its quickest. */
    @Test void aKnifeFallsNotTooFastAndNoFurtherInAFrameThanItFalls(){
        double fastest=0,slowest=Double.MAX_VALUE;
        for(int k=0;k<RainRules.BLADES;k+=7){
            BladeRain.Pose last=null;float lands=(float)RainRules.landsAt(k);
            for(float t=lands-BladeRain.BEFORE;t<lands+BladeRain.STUCK;t+=1/3f){
                var p=pose(k,t);
                if(p!=null&&last!=null){
                    double d=p.tip().distanceTo(last.tip());
                    assertTrue(d<.6,"knife "+k+" at "+t+": "+d+" in a third of a tick");
                    if(p.flying()&&last.flying()){fastest=Math.max(fastest,d*3);slowest=Math.min(slowest,d*3);}
                }
                last=p;
            }
        }
        assertTrue(fastest<1.75,"at most some 1.7 blocks a tick (2.132.2: 3): "+fastest);
        assertTrue(slowest>.9,"falling all the way, as rain does (not hanging): "+slowest);
    }
    @Test void stoppedShortTheKnivesNotYetInAreGoneAndNoMoreCome(){
        float stop=RainRules.FIRST+10.2f;
        for(int k=0;k<RainRules.BLADES;k+=3){
            float lands=(float)RainRules.landsAt(k),comes=lands-BladeRain.BEFORE;
            var after=pose(k,stop+BladeRain.VANISH+.01f,stop);
            if(lands>stop)assertNull(after,"knife "+k+" not yet in is gone");
            if(comes>=stop)assertNull(pose(k,comes+3,stop),"none comes after it stopped");
            if(lands>stop&&comes<stop){
                BladeRain.Pose a=pose(k,stop+.5f,stop),b=pose(k,stop+1.5f,stop);
                if(a!=null&&b!=null){assertEquals(0,a.tip().distanceTo(b.tip()),.3,"it hangs where it was");assertTrue(b.shown()<a.shown());}
            }
        }
    }
    /** (2.132.4) Each frame only the knives falling then are looked at: every one that is anything to draw is among them. */
    @Test void theKnivesLookedAtAFrameAreAllThatAreFalling(){
        for(float age=0;age<RainRules.LENGTH+5;age+=.25f){
            int[] w=RainFx.window(age);
            assertTrue(w[0]>=0&&w[1]<=RainRules.BLADES&&w[0]<=w[1]);
            for(int k=0;k<RainRules.BLADES;k++){
                float lands=(float)RainRules.landsAt(k);
                boolean any=age>=lands-BladeRain.BEFORE-.5f&&age<=lands+BladeRain.STUCK+12;
                if(any)assertTrue(k>=w[0]&&k<w[1],"knife "+k+" at "+age+" not looked at: "+w[0]+".."+w[1]);
            }
            assertTrue(w[1]-w[0]<=26*RainRules.PER,"not all of them every frame: "+(w[1]-w[0]));
        }
    }
    /** (2.132.4) Some two thousand at once: what the camera cannot see is not drawn, and nothing it can see is left out. */
    @Test void whatTheCameraCannotSeeIsNotDrawnAndAllItCanIs(){
        // a camera at the user's shoulder some way back, looking at the one stabbed: 16:9, 70 degrees up and down
        double ty=Math.tan(Math.toRadians(35)),tx=ty*16/9.0;
        Vec3d eye=FEET.add(USER.multiply(2.8)).add(0,2.6,0),ahead=FEET.add(0,1,0).subtract(eye).normalize();
        Vec3d left=new Vec3d(0,1,0).crossProduct(ahead).normalize(),up=ahead.crossProduct(left).normalize();
        var view=new RainFx.View(eye,ahead,up,tx*RainFx.WIDER,ty*RainFx.WIDER);
        var rng=new java.util.Random(7);
        for(int i=0;i<20000;i++){
            Vec3d p=eye.add((rng.nextDouble()-.5)*80,(rng.nextDouble()-.5)*40,(rng.nextDouble()-.5)*80);
            Vec3d d=p.subtract(eye);double z=d.dotProduct(ahead),x=d.dotProduct(left),y=d.dotProduct(up);
            boolean onScreen=z>.05&&Math.abs(x)<=z*tx&&Math.abs(y)<=z*ty;
            if(onScreen)assertTrue(view.sees(p,0),"on the screen, so seen: "+p);
            if(z<-2||Math.abs(x)>z*tx*RainFx.WIDER+3||Math.abs(y)>z*ty*RainFx.WIDER+3)assertFalse(view.sees(p,1),"well off the screen, not: "+p);
        }
        // a line across the screen whose ends are both off it is seen (its middle is on it)
        Vec3d mid=eye.add(ahead.multiply(10));
        assertTrue(view.sees(mid.add(left.multiply(30)),mid.subtract(left.multiply(30)),.5));
        // through the rain: well under all of them are drawn, all the near ones it looks at are
        int all=0,seen=0;float t=RainRules.FIRST+12;
        for(int k=0;k<RainRules.BLADES;k++){
            var p=pose(k,t);if(p==null)continue;all++;
            Vec3d tip=p.tip(),top=p.grip().add(0,p.flying()?RainFx.STREAK+RainFx.STREAK_MORE:0,0);
            if(view.sees(tip,top,RainFx.KNIFE_REACH))seen++;
        }
        assertTrue(all>=2000&&seen<all*.6,"drawn: "+seen+" of "+all);
        assertTrue(seen>all*.15,"those ahead of it are: "+seen+" of "+all);
    }
    @Test void itsSoundsAreOnesTheGameHasThroughTheRain(){
        var cues=BladeRain.cues();int come=0,strike=0;
        for(var c:cues){
            int at=(Integer)c[1];float part=(Float)c[2];
            assertTrue(at>RainRules.STAB&&at<=RainRules.LAST,"after the knife is in, by the last volley: "+at);assertTrue(part>0&&part<=1);
            if(c[0].equals(BladeRain.COME_SOUND))come++;else{assertEquals(BladeRain.STRIKE_SOUND,c[0]);strike++;}
        }
        assertTrue(come>=8&&strike>=10,"through all of it: "+come+" and "+strike);
        var last=cues.get(cues.size()-1);
        assertEquals(BladeRain.STRIKE_SOUND,last[0]);assertEquals(RainRules.LAST-1,(Integer)last[1]);assertEquals(BladeRain.LAST_PART,(Float)last[2]);
        assertEquals("xchara_jab",BladeRain.STAB_SOUND);
    }
    /** (2.132.2) The rain is wider than the user's camera is from the one stabbed: knives come down by cameras, and fade there, not cutting across the lens. */
    @Test void aKnifeFadesByTheCameraNotAcrossTheLens(){
        Vec3d camera=new Vec3d(0,66,0);
        assertEquals(0,RainFx.near(new Vec3d(.5,60,0),new Vec3d(.5,70,0),camera),1e-6,"its line passing half a block from the lens: none of it");
        assertEquals(0,RainFx.near(new Vec3d(1.1,64,0),new Vec3d(1.1,67,0),camera),1e-6);
        assertEquals(0,RainFx.near(new Vec3d(1.4,64,0),new Vec3d(1.4,67,0),camera),1e-6);
        assertEquals(1,RainFx.near(new Vec3d(4.6,60,0),new Vec3d(4.6,70,0),camera),1e-6,"four and a half blocks off: all of it");
        assertEquals(1,RainFx.near(new Vec3d(0,71,0),new Vec3d(0,74,0),camera),1e-6,"over the camera by five: all of it (its line's nearest end)");
        float last=0;
        for(double x=0;x<=5;x+=.01){float v=RainFx.near(new Vec3d(x,60,0),new Vec3d(x,70,0),camera);assertTrue(v>=last-1e-6,"coming in as it is further off");last=v;}
        // drawn round the knife's own outline, with fewer points: the same shape to the eye (far off fewer still)
        double fine=Math.abs(HackKnife.area2(HackKnife.OUTLINE));
        for(var o:new double[][][]{RainFx.OUTLINE,RainFx.OUTLINE_FAR}){
            double coarse=Math.abs(HackKnife.area2(o));
            assertTrue(Math.abs(coarse/fine-1)<(o==RainFx.OUTLINE?.02:.05),"fewer points, the same shape: "+o.length+" points, "+coarse/fine+" of its area");
            for(int i:new int[]{0,1,2,3})assertArrayEquals(HackKnife.OUTLINE[i],o[i],1e-12,"the rod and the spine's corners kept");
            assertArrayEquals(HackKnife.OUTLINE[HackKnife.OUTLINE.length-1],o[o.length-1],1e-12);
        }
        assertTrue(RainFx.OUTLINE.length<=16&&RainFx.OUTLINE_FAR.length<=9&&RainFx.OUTLINE_FARTHEST.length<=8);
        // farthest off (some four degrees long) its belly is two points: still the knife, a little less of it
        double farthest=Math.abs(HackKnife.area2(RainFx.OUTLINE_FARTHEST));
        assertTrue(farthest/fine>.9&&farthest/fine<1.0001,"the farthest outline: "+farthest/fine+" of its area");
    }
    /**
     * (2.132.4) Some two thousand at once: one far off is drawn with fewer points on only the one of its two planes more
     * across the camera's sight, its glow laid along it; near, both planes and the glow round its outline, as before.
     */
    @Test void aKnifeFarOffIsDrawnLighterOnThePlaneFacingTheCamera(){
        var p=pose(7,(float)RainRules.landsAt(7)-3);assertTrue(p!=null&&p.flying());
        float roll=30;Vec3d y=p.dir(),sp=BladeShot.spine(y,roll),across=y.crossProduct(sp).normalize();
        var buf=new net.minecraft.client.render.BufferBuilder(1<<16);
        int[][] count=new int[3][2];
        for(int lod=0;lod<3;lod++)for(int l=0;l<2;l++){
            buf.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS,net.minecraft.client.render.VertexFormats.POSITION_COLOR);
            var m=new dev.sangwoo.celestialfall.client.EffectMesh(buf,new org.joml.Matrix4f(),p.grip().add(across.multiply(20)));
            RainFx.blade(m,p,roll,1,l==1,lod,p.grip().add(across.multiply(20)));
            var b=buf.end();count[lod][l]=b.getParameters().vertexCount();b.release();
        }
        assertEquals(2*4*(3*RainFx.OUTLINE.length-2),count[0][0],"near: both planes, the fan, the rod and two bands of rim");
        assertEquals(2*4*RainFx.OUTLINE.length,count[0][1],"near: the glow round its outline on both planes");
        assertEquals(4*(2*RainFx.OUTLINE_FAR.length-2),count[1][0],"far: one plane, one band of rim");
        assertEquals(4*(2*RainFx.OUTLINE_FARTHEST.length-2),count[2][0],"farthest: fewer points still");
        assertEquals(8,count[1][1],"far: its glow one strip along it");assertEquals(8,count[2][1]);
        assertTrue(count[1][0]+count[1][1]<(count[0][0]+count[0][1])/6,"far off it costs a sixth of near");
        // the plane drawn far off is the one more across the camera's sight: seen along `across`, the plane of `sp`
        for(Vec3d look:new Vec3d[]{across,sp}){
            buf.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS,net.minecraft.client.render.VertexFormats.POSITION_COLOR);
            Vec3d camera=p.grip().add(look.multiply(20));
            RainFx.blade(new dev.sangwoo.celestialfall.client.EffectMesh(buf,new org.joml.Matrix4f(),camera),p,roll,1,false,1,camera);
            var b=buf.end();var data=b.getVertexBuffer();int stride=16,n=b.getParameters().vertexCount();
            double most=0;
            for(int i=0;i<n;i++){
                Vec3d v=new Vec3d(data.getFloat(i*stride),data.getFloat(i*stride+4),data.getFloat(i*stride+8)).subtract(p.grip());
                most=Math.max(most,Math.abs(v.dotProduct(look)));
            }
            b.release();
            assertTrue(most<1e-3,"seen along "+(look==across?"across":"its spine")+", the plane square to it: "+most);
        }
    }
    /** (2.132.1) The screen inside the rain is for whoever stands in it (their body, not their camera), and for no one else; it comes and goes smoothly. */
    @Test void theScreenInsideTheRainIsForWhoeverStandsInIt(){
        Vec3d feet=new Vec3d(10,64,5),user=new Vec3d(10-(RainRules.KEEP+.3),64,5);
        float mid=RainRules.FIRST+10;
        assertEquals(1,RainFx.inside(user,feet,mid,NEVER),1e-6,"the user stands in the rain where they stab from");
        assertEquals(1,RainFx.inside(feet,feet,mid,NEVER),1e-6,"so does the one stabbed");
        // (2.132.4: 30 blocks round: one well off from them is still in it; it is their body that counts)
        assertEquals(1,RainFx.inside(new Vec3d(10,64,5+20),feet,mid,NEVER),1e-6,"twenty blocks off: in it");
        assertEquals(1,RainFx.inside(new Vec3d(10,64,5+BladeRain.ZONE-1.6),feet,mid,NEVER),1e-6,"one just inside its edge is under it all");
        assertEquals(0,RainFx.inside(new Vec3d(10,64,5+BladeRain.ZONE+.6),feet,mid,NEVER),1e-6,"one standing outside it gets none");
        assertEquals(0,RainFx.inside(user.add(0,BladeRain.SKY+3,0),feet,mid,NEVER),1e-6,"nor one over the circle in the sky");
        assertEquals(0,RainFx.inside(user,feet,RainRules.FIRST-BladeRain.BEFORE-.01f,NEVER),1e-6,"none before the first knife comes");
        assertEquals(0,RainFx.inside(user,feet,RainRules.LAST+10.01f,NEVER),1e-6,"none a little after the last lands");
        assertEquals(0,RainFx.inside(user,feet,RainRules.FIRST+15,RainRules.FIRST+4),1e-6,"stopped short, gone after it");
        float prev=0,most=0;
        for(float a=0;a<RainRules.LENGTH;a+=1/3f){float v=RainFx.inside(user,feet,a,NEVER);most=Math.max(most,Math.abs(v-prev));prev=v;}
        assertTrue(most<=.11,"it comes and goes over frames at 60 fps, not at once: at most "+most+" a frame");
    }
}
