package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlashTest.turn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's T as drawn (2.96.0): the body's motion (from the stance back to it as long as the server holds her, no
 * joint jumping or flipping between frames, never a foot in the ground, low in a crouch where she lands). 2.96.2 (user
 * request: "3참격 느낌이 영상이랑 너무 달라 ... 스킬 이펙트를 싹 다 갈아엎고 영상이랑 비슷하게 만들어봐", the video read again
 * and confirmed): the drop of light her dash lays from behind her to round the foe, and the three slashes as long thin
 * blades crossing on the foe and reaching toward where she stands, short of her and above the ground; her afterimages
 * full as she dashes; all of it gone while T is drawn.
 */
class SwordLungeTest {
    static final Anim CLIP=SwordLunge.CLIP;
    @Test void itRunsAsLongAsTheServerHoldsHerFromTheStanceBackToIt(){
        var stance=IsosangSlash.stance();
        var first=CLIP.frame(0);var last=CLIP.frame(CLIP.length());
        for(int j=0;j<6;j++){assertTrue(turn(first,stance,j)<.5,"T starts in the stance");assertTrue(turn(last,stance,j)<.5,"T ends in the stance (the formation's goes on from it)");}
        assertEquals(IsosangRules.LENGTH[IsosangRules.LUNGE]/20f,CLIP.length(),1e-6);
        assertSame(CLIP,IsosangSlash.hit(IsosangRules.LUNGE),"its swing is drawn with its own clip");
        assertEquals(IsosangRules.LUNGE_LENGTH/20f+.25f,SwordLunge.LIFE,1e-6);
    }
    @Test void noJointJumpsOrFlips(){
        double h=1/960.0;
        for(double t=h;t+2*h<=CLIP.length();t+=h){
            var p0=CLIP.frame((float)(t-h));var p1=CLIP.frame((float)t);var p2=CLIP.frame((float)(t+h));var p3=CLIP.frame((float)(t+2*h));
            for(int j=0;j<6;j++){
                double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"joint "+j+" jumps "+now+" deg at "+t);
            }
        }
        for(double t=0;t+1/60.0<=CLIP.length();t+=1/60.0)
            for(int j=0;j<6;j++)assertTrue(turn(CLIP.frame((float)t),CLIP.frame((float)(t+1/60.0)),j)<125,"joint "+j+" flips at "+t);
    }
    @Test void neverAFootInTheGroundAndLowWhereSheLands(){
        for(float t=0;t<=CLIP.length();t+=1/120f){
            double[] f=IsosangSlash.feet(CLIP.frame(t));
            assertTrue(f[0]>-.02&&f[1]>-.02,"a foot in the ground at "+t+": "+f[0]+" "+f[1]);
        }
        // seen in a low crouch just in front of the foe (the video: down, her sword swept through)
        for(float t=IsosangRules.LUNGE_LAND/20f;t<IsosangRules.LUNGE_HOP/20f;t+=.01f)assertTrue(CLIP.frame(t).up()<-.2,"crouched where she lands at "+t+": "+CLIP.frame(t).up());
    }
    @Test void itsDropOfLightRunsFromBehindHerToRoundTheFoe(){
        // (the video: laid from behind where she set out to the foe in a blink, far wider than she is, pointed at the foe
        // as it is laid and opening round it)
        Vec3d tail=new Vec3d(3,70,-2),along=new Vec3d(.96,-.08,.26).normalize(),side=new Vec3d(-.26,0,.96).normalize();
        double length=11;
        var round=new SwordLunge.Drop(tail,along,side,length,1);
        assertEquals(length+SwordLunge.HALF,round.tip(),1e-9,"its end round the foe");
        assertTrue(round.width(0)<.25*SwordLunge.HALF,"its tail a point");
        assertEquals(SwordLunge.HALF,round.width(length*.5),1e-9,"far wider than she is along it");
        assertEquals(SwordLunge.HALF,round.width(length),1e-9,"as wide where the foe is");
        assertEquals(0,round.width(round.tip()),1e-6,"closed past the foe");
        for(double s=length;s<round.tip();s+=.05){double r=Math.hypot(s-length,round.width(s));assertEquals(SwordLunge.HALF,r,1e-6,"round about the foe");}
        var laid=new SwordLunge.Drop(tail,along,side,length,0);
        assertEquals(length+.35,laid.tip(),1e-9,"pointed at the foe as it is laid");
        assertTrue(laid.width(length)<.3*SwordLunge.HALF,"narrow at the foe as it is laid: "+laid.width(length));
        double[] s=round.samples();assertEquals(0,s[0],1e-9);assertEquals(round.tip(),s[s.length-1],1e-9);
        for(int i=1;i<s.length;i++)assertTrue(s[i]>s[i-1],"along it in order");
        assertTrue(SwordLunge.HALF*2>=2&&SwordLunge.HALF*2<=3.5,"some two to three blocks across");
        assertTrue(SwordLunge.RIM>.2&&SwordLunge.RIM<.7&&SwordLunge.RIM<SwordLunge.HALF*.5,"a band round it, not filling it");
        // laid in a blink as she sets off, opening round the foe at once; cooled to ink and gone by some 0.6 s
        float go=IsosangRules.LUNGE_GO/20f;
        assertTrue(go+SwordLunge.RACE<=.22f,"on the foe by the video's 0.24 s");
        assertTrue(go+SwordLunge.FILL_INK1<=.45f&&go+SwordLunge.RIM_EAT1<=.66f&&go+SwordLunge.RIM_EAT1>=.55f,"gone by some 0.6 s");
        assertTrue(SwordLunge.AIM1<go&&SwordLunge.AIM_GONE1<=go+SwordLunge.FILL_EAT0+.01f,"its line of ink drawn before she goes, gone as the light cools");
    }
    @Test void itsThreeSlashesAreLongThinBladesCrossingOnTheFoeAndReachingTowardHer(){
        Vec3d foe=new Vec3d(10,71,-4),feet=foe.subtract(0,SwordLunge.FOE_MID,0);
        for(double yaw=0;yaw<360;yaw+=23)for(double far:new double[]{4,6,9,14,22}){
            Vec3d toward=Vec3d.fromPolar(0,(float)yaw).multiply(far),fw=toward.normalize(),her=feet.add(toward);
            // her camera behind her, over her right shoulder (the formation's 3.2 blocks back, a block to her right, high)
            Vec3d rt=new Vec3d(-fw.z,0,fw.x),camera=her.add(fw.multiply(3.2)).add(rt.multiply(1)).add(0,2.6,0);
            SwordLunge.Blade[] b=new SwordLunge.Blade[IsosangRules.LUNGE_SLASHES];
            for(int j=0;j<b.length;j++){
                b[j]=SwordLunge.blade(foe,toward,j);var s=b[j];double len=s.far().distanceTo(s.near());
                // through the foe: its line comes within a fifth of a block of the foe's column
                assertTrue(column(s,feet)<.2,"slash "+j+" misses the foe: "+column(s,feet));
                // reaching toward her, a little past the foe on the far side
                assertTrue(s.way().dotProduct(fw)>.25,"slash "+j+" toward her");
                assertTrue(s.far().subtract(foe).dotProduct(fw)<-.5,"slash "+j+" reaches past the foe");
                // long and thin, never under the ground, by her but never through her, nor through her camera
                assertTrue(len>=4.5&&len<=far+SwordLunge.FAR+9,"slash "+j+" "+len+" blocks long");
                assertTrue(s.width()/len<.12,"slash "+j+" thin");
                assertTrue(s.far().y>=feet.y+.1&&s.near().y>=feet.y+.1,"slash "+j+" in the ground");
                assertTrue(level(s,her)>=2.5,"slash "+j+" through her: "+level(s,her)+" at "+far);
                assertTrue(near(s,camera)>=2.5,"slash "+j+" through her camera: "+near(s,camera)+" at "+far);
            }
            // the diagonals reach on past her, out at her sides (out of the picture behind her); the level one runs off across
            assertTrue(b[0].near().subtract(her).dotProduct(fw)>0&&b[2].near().subtract(her).dotProduct(fw)>0,"past her");
            assertTrue(b[0].near().subtract(her).dotProduct(rt)>2.5&&b[2].near().subtract(her).dotProduct(rt)<-2.5,"out at her sides");
            // each a way of its own, well apart: two diagonals crossing and one nearly level through its legs
            for(int a=0;a<b.length;a++)for(int c=a+1;c<b.length;c++){
                double deg=Math.toDegrees(Math.acos(Math.min(1,Math.abs(b[a].way().dotProduct(b[c].way())))));
                assertTrue(deg>12,"slashes "+a+" and "+c+" "+deg+" deg apart at "+far);
            }
            assertTrue(b[0].way().dotProduct(rt)*b[2].way().dotProduct(rt)<0,"the diagonals cross");
            assertTrue(b[0].way().y<-.01&&b[2].way().y<-.01,"the diagonals come down toward her");
            assertTrue(Math.abs(b[1].way().y)<.05&&b[1].way().dotProduct(rt)<-.6,"one nearly level, across");
            double legs=b[1].point(.2).y-feet.y;assertTrue(legs>.2&&legs<.7,"through its legs: "+legs);
        }
        // drawn on in a breath, bright a moment, ink, and gone while T is drawn (the video: some 0.4 s each)
        float last=IsosangRules.lungeSlash(IsosangRules.LUNGE_SLASHES-1);
        assertTrue(SwordLunge.DRAW<=.06f&&SwordLunge.LIT>=.1f&&SwordLunge.EAT1>=.38f&&SwordLunge.EAT1<=.5f);
        assertTrue(last+SwordLunge.EAT1<=SwordLunge.LIFE,"gone while T is drawn");
        assertTrue(SwordLunge.INK0_FIRST<SwordLunge.INK0,"the level one goes to ink first");
    }
    @Test void herAfterimagesAreFullAsSheDashes(){
        float go=IsosangRules.LUNGE_GO/20f,land=IsosangRules.LUNGE_LAND/20f;
        for(float t=go;t<=land;t+=.01f)assertEquals(1,SwordLunge.afterimages(t),1e-6,"full as she dashes");
        assertEquals(.25f,SwordLunge.afterimages(0),1e-6);assertEquals(.25f,SwordLunge.afterimages(.8f),1e-6);
        assertNotEquals(0x4a7cff,SwordLunge.GHOST,"violet, not the sword's hits' blue");
    }
    /** How near slash {@code s} comes to {@code p} (level). */
    static double level(SwordLunge.Blade s,Vec3d p){
        double best=99;for(int i=0;i<=400;i++){Vec3d q=s.point(i/400.0);best=Math.min(best,Math.hypot(q.x-p.x,q.z-p.z));}
        return best;
    }
    /** How near slash {@code s} comes to {@code p}. */
    static double near(SwordLunge.Blade s,Vec3d p){
        double best=99;for(int i=0;i<=400;i++)best=Math.min(best,s.point(i/400.0).distanceTo(p));
        return best;
    }
    /** How near slash {@code s}'s line comes to the vertical line up from {@code feet} (level). */
    static double column(SwordLunge.Blade s,Vec3d feet){
        double best=99;for(int i=0;i<=400;i++){Vec3d p=s.point(i/400.0);best=Math.min(best,Math.hypot(p.x-feet.x,p.z-feet.z));}
        return best;
    }
}
