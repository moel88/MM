package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.xchara.XSlashRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's H with their hack off, the great X slash, as drawn (2.134.0, the user's: "X 대참격으로 날리고 맞은 사람은
 * 글리치에 걸리고 가드 못 하게"): the knife lit until the X goes, black at once as it does; two cuts, the forehand down
 * from the high right to the low left and the backhand down from the high left to the low right, out of the stance and
 * back into it with nothing of the body jumping; the free hand sent out ahead as the X goes; the X standing crossed
 * where it was cut, then flying as far and as big as the server's, gone where its way ends; its arms crescents from
 * horn to horn; the X on the ground on the server's diagonals, burned in as the X goes over it, whole until it bursts.
 */
class XSlashTest {
    static final Vec3d STOOD=new Vec3d(3,64,-2);static final float YAW=30;
    static StandClient.Cast cast(){
        return new StandClient.Cast(new UUID(5,6),new UUID(7,8),new Identifier("minecraft","overworld"),SKILL,XSLASH,1000,1000,0,STOOD,YAW,List.of());
    }
    static Vec3d tip(float sec){var b=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(XSlash.CLIP.frame(sec)));return b[0].add(b[1].multiply(KnifeCombo.TIP));}

    @Test void itIsTheGreatXSlashsMotionFromTheStanceBackToIt(){
        assertSame(XSlash.CLIP,KnifeCombo.clipOf(XSLASH));assertEquals(LENGTH[XSLASH]/20f,XSlash.CLIP.length(),1e-4);
        assertEquals(XSlash.LIFE,KnifeCombo.life(XSLASH),1e-6);
        assertTrue(XSlash.LIFE*20>=XSlashRules.burstAt(XSlashRules.RANGE)+8,"drawn until its X on the ground has burst and what that leaves is gone");
        assertTrue(KnifeCombo.LIFE<=new KnifeCombo().lifetime()&&XSlash.LIFE<=new KnifeCombo().lifetime());
        Vec3d[] rest=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(KnifeCombo.AT_REST)),start=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(XSlash.CLIP.frame(0))),end=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(XSlash.CLIP.frame(XSlash.CLIP.length())));
        assertEquals(0,rest[0].distanceTo(start[0]),1e-4);assertEquals(0,rest[1].distanceTo(start[1]),1e-4,"from the stance");
        assertEquals(0,rest[0].distanceTo(end[0]),1e-4);assertEquals(0,rest[1].distanceTo(end[1]),1e-4,"and back into it");
    }
    @Test void twoCutsCrossing(){
        // wound up for the first: the knife's point up and to the right; for the second: up and to the left
        // (yaw 0 faces the game's +z: ahead is +z, to the right is -x)
        Vec3d right=new Vec3d(-1,0,0);
        Vec3d w1=tip(.15f),w2=tip(.36f);
        assertTrue(w1.y>1.9&&w1.dotProduct(right)>.3,"the first wound up behind the right shoulder: "+w1);
        assertTrue(w2.y>1.9&&w2.dotProduct(right)<-.3,"the second over the left shoulder: "+w2);
        // the first cut goes down to the low left; the second down to the low right
        Vec3d a=tip(XSlash.CUT1-.025f),b=tip(XSlash.CUT1+.025f),c=tip(XSlash.CUT2-.025f),e=tip(XSlash.CUT2+.025f);
        assertTrue(b.y<a.y-.2,"the first goes down");assertTrue(b.subtract(a).dotProduct(right)<-.2,"to the left");
        assertTrue(e.y<c.y-.2,"the second goes down");assertTrue(e.subtract(c).dotProduct(right)>.2,"to the right");
        // as each lands the knife is ahead of them
        assertTrue(tip(XSlash.CUT1).z>1.2&&tip(XSlash.CUT2).z>1.2,"ahead as they land");
        // the free hand sent out ahead as the X goes
        Vec3d hand=KagenoClips.freeHand(Vec3d.ZERO,0,R6.poseOf(XSlash.CLIP.frame(XSlash.FIRE)));
        assertTrue(hand.z>.55&&hand.y>1.0,"thrust out ahead at chest height: "+hand);
        // the knife's point never in the ground
        for(float t=0;t<=XSlash.CLIP.length();t+=1/120f)assertTrue(tip(t).y>.15,"clear of the ground at "+t+": "+tip(t).y);
    }
    /** As the basic attack's swings (KnifeComboTest): no joint jumps or flips between frames, no elbow or knee snaps; the hands quick in the cuts, never teleporting. */
    @Test void nothingOfTheBodyJumps(){
        var a=XSlash.CLIP;double h=1/960.0;
        for(double t=h;t+2*h<=a.length();t+=h){
            var p0=a.frame((float)(t-h));var p1=a.frame((float)t);var p2=a.frame((float)(t+h));var p3=a.frame((float)(t+2*h));
            for(int j=0;j<6;j++){
                double before=KnifeComboTest.turn(p0,p1,j),now=KnifeComboTest.turn(p1,p2,j),after=KnifeComboTest.turn(p2,p3,j);
                assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"joint "+j+" jumps "+now+" deg at "+t);
            }
        }
        Vec3d lastFist=null,lastHand=null;float lastYaw=0,lastPitch=0;
        for(float t=0;t+1/60f<=a.length();t+=1/60f){
            var f0=a.frame(t);var f1=a.frame(t+1/60f);
            for(int j=0;j<6;j++)assertTrue(KnifeComboTest.turn(f0,f1,j)<125,"joint "+j+" flips at "+t);
            for(int i=0;i<2;i++){
                assertTrue(Math.abs(f1.joints().elbow[i]-f0.joints().elbow[i])<75,"elbow "+i+" snaps at "+t);
                assertTrue(Math.abs(f1.joints().knee[i]-f0.joints().knee[i])<55,"knee "+i+" snaps at "+t);
            }
            var pose=R6.poseOf(f0);Vec3d fist=KagenoClips.blade(Vec3d.ZERO,0,pose)[0],hand=KagenoClips.freeHand(Vec3d.ZERO,0,pose);
            if(lastFist!=null){
                assertTrue(fist.distanceTo(lastFist)<.6,"the knife's hand at "+t+": "+fist.distanceTo(lastFist));
                assertTrue(hand.distanceTo(lastHand)<.5,"the free hand at "+t+": "+hand.distanceTo(lastHand));
                assertTrue(Math.abs(f0.yaw()-lastYaw)<14&&Math.abs(f0.pitch()-lastPitch)<10,"the body at "+t);
            }
            lastFist=fist;lastHand=hand;lastYaw=f0.yaw();lastPitch=f0.pitch();
        }
    }
    @Test void theKnifeIsLitUntilTheXGoesThenBlackAtOnce(){
        assertNull(XSlash.lit(0,Float.NaN),"its own black before");
        float[] on=XSlash.lit(XSlash.LIT+.1f,Float.NaN);assertNotNull(on);assertEquals(1,on[0],1e-9);assertEquals(.1f,on[1],1e-6);
        assertNotNull(XSlash.lit(XSlash.FIRE-.001f,Float.NaN));
        assertNull(XSlash.lit(XSlash.FIRE,Float.NaN),"black the moment the X goes: its light has gone into it");
        // stopped short before: it goes out as the hack does
        float[] out=XSlash.lit(.3f,.25f);assertNotNull(out);assertEquals(0,out[0],1e-9);
        assertNull(XSlash.lit(.25f+HackLook.OFF+.01f,.25f));
        assertNull(XSlash.lit(XSlash.FIRE+.1f,XSlash.FIRE+.05f),"stopped after the X went: black already");
    }
    @Test void theXStandsWhereItWasCutThenFliesAsTheServersDoes(){
        double reach=XSlashRules.RANGE;
        double[] cut=XSlash.shape(.3f,reach,Float.NaN);
        assertEquals(XSlashRules.NEAR,cut[0],1e-9);assertEquals(XSlashRules.HALF0,cut[1],1e-9);assertEquals(XSlashRules.TALL0,cut[2],1e-9);
        for(float t=XSlash.FIRE;t<XSlash.flown(reach);t+=1/60f){
            double[] x=XSlash.shape(t,reach,Float.NaN);double head=XSlashRules.head((t-XSlash.FIRE)*20);
            assertEquals(Math.min(head,reach),x[0],1e-6,"as far ahead as the server's at "+t);
            assertEquals(XSlashRules.half(x[0]),x[1],1e-9,"as wide");assertEquals(0,x[4],1e-9);
        }
        assertNotNull(XSlash.shape(XSlash.flown(reach)+XSlash.DISSOLVE*.5f,reach,Float.NaN),"breaking up where its way ends");
        assertNull(XSlash.shape(XSlash.flown(reach)+XSlash.DISSOLVE+.01f,reach,Float.NaN),"then gone");
        assertNull(XSlash.shape(XSlash.flown(6)+XSlash.DISSOLVE+.01f,6,Float.NaN),"sooner where a wall stopped it");
        // no jump of it between frames as it flies
        double last=Double.NaN;
        for(float t=XSlash.FIRE;t<XSlash.flown(reach);t+=1/60f){double fw=XSlash.shape(t,reach,Float.NaN)[0];if(!Double.isNaN(last))assertTrue(fw-last>=0&&fw-last<.8,"at "+t);last=fw;}
        // stopped short before it went: what was cut of it goes out, no X flies
        assertNotNull(XSlash.shape(.32f,reach,.3f));assertNull(XSlash.shape(.45f,reach,.3f));assertNull(XSlash.shape(XSlash.FIRE+.2f,reach,.3f));
        // each arm drawn across with its cut
        assertEquals(0,XSlash.cut(0,XSlash.ARM1_FROM),1e-9);assertEquals(1,XSlash.cut(0,XSlash.ARM1_TO),1e-9);
        assertEquals(0,XSlash.cut(1,XSlash.ARM1_TO),1e-9);assertEquals(1,XSlash.cut(1,XSlash.ARM2_TO),1e-9);
        assertTrue(XSlash.ARM1_FROM<XSlash.CUT1&&XSlash.CUT1<XSlash.ARM1_TO&&XSlash.ARM2_FROM<XSlash.CUT2&&XSlash.CUT2<XSlash.ARM2_TO&&XSlash.ARM2_TO<=XSlash.FIRE,"with the knife's cuts, both whole as it goes");
    }
    @Test void itsArmsAreCrescentsFromHornToHorn(){
        var w=XSlash.way(cast());double fw=8,half=XSlashRules.HALF,tall=XSlashRules.TALL;
        for(int a=0;a<2;a++){
            var sw=XSlash.arm(w,fw,half,tall,a,1);int n=sw.n();
            // from the high right (the first) or the high left (the second) to the low other side, on the plane fw ahead
            Vec3d first=sw.outer()[0],last=sw.outer()[n-1];
            Vec3d d0=first.subtract(STOOD),d1=last.subtract(STOOD);
            assertEquals(fw,d0.dotProduct(w.f()),1e-6);assertEquals(fw,d1.dotProduct(w.f()),1e-6);
            assertEquals(a==0?half:-half,d0.dotProduct(w.right()),1e-6);assertEquals(2*tall,d0.y,1e-6);
            assertEquals(a==0?-half:half,d1.dotProduct(w.right()),1e-6);assertEquals(0,d1.y,1e-6,"its lower horn at the ground");
            assertEquals(0,sw.inner()[0].distanceTo(first),1e-9,"pointed at its horns");assertEquals(0,sw.inner()[n-1].distanceTo(last),1e-9);
            // its cutting edge bowed up and out: at its middle it stands over the inner edge, and the band is widest there
            Vec3d om=sw.outer()[n/2],im=sw.inner()[n/2];
            assertTrue(om.y>im.y,"its cutting edge over");
            double widest=0;int at=0;for(int i=0;i<n;i++){double t=sw.outer()[i].distanceTo(sw.inner()[i]);if(t>widest){widest=t;at=i;}}
            assertTrue(Math.abs(at-n/2)<=1,"widest at its middle");assertTrue(widest>.6&&widest<1.5,"a crescent, not a bar: "+widest);
            // drawn halfway, it reaches halfway along
            var halfway=XSlash.arm(w,fw,half,tall,a,.5f);assertEquals(.5f,halfway.along()[halfway.n()-1],1e-6);
        }
        // the two cross in the middle
        var a0=XSlash.arm(w,fw,half,tall,0,1);Vec3d mid=w.at(fw,0,tall);
        double near=Double.MAX_VALUE;for(var p:a0.inner())near=Math.min(near,p.distanceTo(mid));
        assertTrue(near<1.2,"through its middle: "+near);
    }
    @Test void theXOnTheGroundLiesOnTheServersDiagonals(){
        var w=XSlash.way(cast());var ds=XSlash.diagonals(w);var raw=XSlashRules.diagonals(w.reach());
        for(int a=0;a<2;a++){
            Vec3d d0=ds[a][0].subtract(STOOD),d1=ds[a][1].subtract(STOOD);
            assertEquals(raw[a][0],d0.dotProduct(w.f()),1e-6);assertEquals(raw[a][1],d0.dotProduct(w.right()),1e-6);
            assertEquals(raw[a][2],d1.dotProduct(w.f()),1e-6);assertEquals(raw[a][3],d1.dotProduct(w.right()),1e-6);
        }
        double reach=w.reach();
        assertEquals(0,XSlash.markShown(XSlash.FIRE-.01f,reach),1e-9);assertEquals(1,XSlash.markShown(XSlash.burst(reach)-.01f,reach),1e-9,"whole until it bursts");
        assertEquals(0,XSlash.markShown(XSlash.burst(reach)+.5f,reach),1e-9,"gone soon after");
        assertEquals(XSlashRules.burstAt(reach)/20f,XSlash.burst(reach),1e-6,"when the server bursts it");
        // burned in as the X goes over each point
        double fw=9;float over=XSlash.FIRE+(float)XSlashRules.reached(fw)/20f;
        assertEquals(0,XSlash.burnt(over-.01f,fw),1e-9);assertEquals(1,XSlash.burnt(over,fw),1e-6);assertTrue(XSlash.burnt(over+.5f,fw)<.1f);
        // its pulse and its charge only while it waits, the charge rising to its burst
        assertEquals(0,XSlash.charge(XSlash.flown(reach),reach),1e-6);assertEquals(1,XSlash.charge(XSlash.burst(reach),reach),1e-6);
        assertEquals(0,XSlash.pulse(XSlash.flown(reach)-.05f,reach),1e-9);
        float last=0;for(float t=XSlash.flown(reach);t<=XSlash.burst(reach);t+=.02f){float k=XSlash.charge(t,reach);assertTrue(k>=last-1e-6);last=k;}
    }
}
