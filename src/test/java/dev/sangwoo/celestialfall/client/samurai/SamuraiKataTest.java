package dev.sangwoo.celestialfall.client.samurai;

import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The samurai's body through their G (2.144.0, on jointed limbs): the feet stand on the ground but in the leap, nothing
 * pops between frames (but across the dash, its one cut), the elbows and knees bend, the hands are on the sword and the
 * scabbard where the moment has them, the cuts pass through the one marked, and the sword goes home in two.
 */
class SamuraiKataTest {
    static final Vec3d FEET=new Vec3d(3.5,70,-8.5);
    static final float YAW=120;
    // KataPreviewDump.points: head, sR, eR, hR, sL, eL, hL, hipR, kR, fR, hipL, kL, fL (right, up, ahead of the feet)
    static final int HAND_R=3,HAND_L=6,KNEE_R=8,FOOT_R=9,KNEE_L=11,FOOT_L=12;
    static double dist(double[] a,double[] b){return Math.sqrt((a[0]-b[0])*(a[0]-b[0])+(a[1]-b[1])*(a[1]-b[1])+(a[2]-b[2])*(a[2]-b[2]));}
    static double[] of(Vec3d v){return new double[]{-v.x,v.y,v.z};}

    @Test void theFeetStandOnTheGroundButInTheLeap(){
        for(float s=0;s<SamuraiKata.LENGTH;s+=1/60f){
            double[][] p=KataPreviewDump.points(s);
            double low=Math.min(p[FOOT_R][1],p[FOOT_L][1]);
            assertTrue(low>-.035,"no foot under the ground at "+s+": "+low);
            boolean air=s>2.08f&&s<2.5f;
            if(!air)assertTrue(low<.06,"a foot on the ground at "+s+": "+low);
        }
        // the leap leaves it
        double[][] up=KataPreviewDump.points(SamuraiKata.WHIRL);
        assertTrue(Math.min(up[FOOT_R][1],up[FOOT_L][1])>.25,"both feet in the air in the whirl");
        assertEquals(SamuraiKata.LENGTH,SamuraiKata.CLIP.length(),1e-6);
    }
    @Test void nothingPopsButAcrossTheDash(){
        double[][] last=null;Vec3d tipLast=null;
        String[] names={"head","sR","eR","hR","sL","eL","hL","hipR","kR","fR","hipL","kL","fL"};
        for(float s=0;s<SamuraiKata.LENGTH;s+=1/60f){
            double[][] p=KataPreviewDump.points(s);Vec3d tip=SamuraiKata.tip(Vec3d.ZERO,0,s);
            boolean cut=s>=SamuraiKata.DASH-1/60f&&s<=SamuraiKata.DASH+1/60f;
            if(last!=null&&!cut){
                for(int i=0;i<p.length;i++)assertTrue(dist(p[i],last[i])<(i>=2&&i<=6&&i!=4?.56:.42),names[i]+" in one frame at "+s+": "+dist(p[i],last[i]));
                assertTrue(tip.distanceTo(tipLast)<1.9,"the point in one frame at "+s+": "+tip.distanceTo(tipLast));
            }
            last=p;tipLast=tip;
        }
    }
    @Test void theLimbsBendAtTheirJoints(){
        // the draw's crouch: both knees bent, the elbows bent to the hilt and the scabbard
        MotionPose deep=SamuraiKata.CLIP.frame(.56f).joints();
        assertTrue(Math.abs(deep.knee[0])>35&&Math.abs(deep.knee[1])>35,"knees "+deep.knee[0]+" "+deep.knee[1]);
        assertTrue(Math.abs(deep.elbow[0])>30&&Math.abs(deep.elbow[1])>30,"elbows "+deep.elbow[0]+" "+deep.elbow[1]);
        // over the clip the elbows and knees go from nearly straight to well bent
        float[] most=new float[4],least={180,180,180,180};
        for(float s=0;s<SamuraiKata.LENGTH;s+=1/60f){
            MotionPose m=SamuraiKata.CLIP.frame(s).joints();
            float[] b={Math.abs(m.elbow[0]),Math.abs(m.elbow[1]),Math.abs(m.knee[0]),Math.abs(m.knee[1])};
            for(int i=0;i<4;i++){most[i]=Math.max(most[i],b[i]);least[i]=Math.min(least[i],b[i]);}
        }
        for(int i=0;i<4;i++)assertTrue(most[i]-least[i]>50,"joint "+i+" from "+least[i]+" to "+most[i]);
    }
    @Test void theHandsAreOnTheSwordAndTheScabbard(){
        // sheathed (before the dash, and after the sheathing): the sword in the scabbard, the left hand on the scabbard by its mouth
        for(float s:new float[]{.3f,.56f,.9f,3.3f,5f,7.4f}){
            var hb=SamuraiKata.blade(Vec3d.ZERO,0,s);var sh=SamuraiKata.sheath(Vec3d.ZERO,0,s);
            assertEquals(1,SamuraiKata.along(s),1e-6);
            double out=SamuraiKata.TIP-(SamuraiKata.TIP-SamuraiKata.GUARD)*SamuraiKata.home(s);
            assertTrue(sh[0].distanceTo(hb[0].add(hb[1].multiply(out)))<1e-6,"the mouth on the blade at "+s);
            assertTrue(sh[1].dotProduct(hb[1])>.999999);
            double[] left=KataPreviewDump.points(s)[HAND_L];
            Vec3d hold=sh[0].add(sh[1].multiply(SamuraiKata.HOLD_SCABBARD));
            assertTrue(dist(left,of(hold))<.09,"the left hand on the scabbard at "+s+": "+dist(left,of(hold)));
            // the sword at the left hip, pointing back
            assertTrue(hb[1].z<-.5,"the sheathed sword pointing back at "+s);
        }
        // the two-handed cuts: the left hand on the hilt under the right
        for(float s=1.49f;s<1.93f;s+=.02f){
            var hb=SamuraiKata.blade(Vec3d.ZERO,0,s);
            double[] left=KataPreviewDump.points(s)[HAND_L];
            Vec3d hilt=hb[0].subtract(hb[1].multiply(SamuraiKata.HOLD_HILT));
            assertTrue(dist(left,of(hilt))<.1,"the left hand on the hilt at "+s+": "+dist(left,of(hilt)));
        }
        // through the cuts the scabbard is worn at the hip, the sword out of it
        for(float s=1.2f;s<2.3f;s+=.1f){assertEquals(0,SamuraiKata.along(s),1e-6);assertEquals(0,SamuraiKata.home(s),1e-6);}
        // nothing of it jumps
        Vec3d before=null;
        for(float s=1.01f;s<SamuraiKata.LENGTH;s+=1/120f){
            Vec3d mouth=SamuraiKata.sheath(Vec3d.ZERO,0,s)[0];
            if(before!=null)assertTrue(mouth.distanceTo(before)<.2,"the mouth in one frame at "+s+": "+mouth.distanceTo(before));
            before=mouth;
        }
        assertTrue(SamuraiKata.SHEATH>=SamuraiKata.TIP-SamuraiKata.GUARD);
    }
    /** The blade's azimuth to the right of the clip's facing (degrees) at {@code s}. */
    static double azimuth(float s){Vec3d b=SamuraiKata.blade(Vec3d.ZERO,0,s)[1];return Math.toDegrees(Math.atan2(-b.x,b.z));}
    @Test void theCutsPassThroughTheOneMarked(){
        // the one marked stands behind them to the right: 1.4 right, 1.3 behind
        double foe=Math.toDegrees(Math.atan2(1.4,-1.3));
        for(float[] at:new float[][]{{1.05f,1.25f},{SamuraiKata.WHIRL-.09f,SamuraiKata.WHIRL+.09f}}){
            boolean through=false;double before=MathHelper.wrapDegrees(azimuth(at[0])-foe);
            for(float s=at[0];s<=at[1];s+=1/240f){
                double now=MathHelper.wrapDegrees(azimuth(s)-foe);
                Vec3d b=SamuraiKata.blade(Vec3d.ZERO,0,s)[1];
                if(Math.abs(now)<30&&Math.signum(now)!=Math.signum(before)&&Math.abs(b.y)<.35)through=true;
                before=now;
            }
            assertTrue(through,"the blade passes through the one marked between "+at[0]+" and "+at[1]);
        }
        // each cut is fast at its middle, and the fastest is the whirl or the draw
        for(float[] c:SamuraiKata.CUTS){
            double speed=SamuraiKata.tip(Vec3d.ZERO,0,c[0]-1/120f).distanceTo(SamuraiKata.tip(Vec3d.ZERO,0,c[0]+1/120f))*60;
            assertTrue(speed>14,"the cut at "+c[0]+": "+speed+" blocks a second");
        }
        // between the cuts and after them the blade rests
        for(float s:new float[]{1.34f,2.72f,3.4f,5f}){
            double speed=SamuraiKata.tip(Vec3d.ZERO,0,s-1/120f).distanceTo(SamuraiKata.tip(Vec3d.ZERO,0,s+1/120f))*60;
            assertTrue(speed<3,"at rest at "+s+": "+speed);
        }
    }
    @Test void theSwordGoesHomeInTwo(){
        // an inch out at the burst, drawn in the dash, slid home but for its last inch, which goes as the world comes back
        assertEquals(1,SamuraiKata.home(.4f),1e-6);
        assertTrue(SamuraiKata.home(.7f)<.95&&SamuraiKata.home(.7f)>.9);
        assertEquals(0,SamuraiKata.home(1.3f),1e-6);
        float last=0;
        for(float s=2.3f;s<SamuraiKata.LENGTH;s+=1/120f){float h=SamuraiKata.home(s);assertTrue(h>=last-1e-6&&h<=1,"never out again at "+s);last=h;}
        assertEquals(SamuraiKata.SHORT,SamuraiKata.home(SamuraiKata.CLICK),1e-4);
        assertEquals(SamuraiKata.SHORT,SamuraiKata.home(6.7f),1e-4);
        assertEquals(1,SamuraiKata.home(SamuraiKata.SNAP+.02f),1e-4);
        // the sword's point is at the mouth as the sheathing starts
        var hb=SamuraiKata.blade(Vec3d.ZERO,0,SamuraiKata.NOTO);var sh=SamuraiKata.sheath(Vec3d.ZERO,0,SamuraiKata.NOTO);
        assertTrue(sh[0].distanceTo(hb[0].add(hb[1].multiply(SamuraiKata.TIP)))<.02,"the point at the mouth");
        // and the blade comes down to it from over the right shoulder: the hand above the mouth, to the right of it
        assertTrue(hb[0].y>sh[0].y+.5&&-hb[0].x>-sh[0].x+.6,"laid from over the right shoulder");
    }
    @Test void theHeadTurnsNoFurtherThanANeckDoes(){
        for(float s=0;s<SamuraiKata.LENGTH;s+=1/30f){
            R6.Pose p=SamuraiKata.pose(s);
            assertTrue(Math.abs(MathHelper.wrapDegrees(p.headYaw))<=80,"at "+s+": the head turned "+p.headYaw);
        }
    }
    @Test void itEndsAsItStarted(){
        // the clip's last frame is the standing they began in but for the hands on the sword (the rig's settle takes it from there)
        var end=SamuraiKata.pose(SamuraiKata.LENGTH);
        assertTrue(Math.abs(end.pitch)<4&&Math.abs(end.yaw)<8&&Math.abs(end.up)<.1);
    }
}
