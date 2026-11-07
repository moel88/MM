package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlashTest.turn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's T as drawn (2.96.0): the body's motion (from the stance back to it as long as the server holds her, no
 * joint jumping or flipping between frames, never a foot in the ground, low in a crouch where she lands). 2.96.2 (user
 * request: "3참격 느낌이 영상이랑 너무 달라 ... 스킬 이펙트를 싹 다 갈아엎고 영상이랑 비슷하게 만들어봐", the video read again
 * and confirmed): the drop of light her dash lays from behind her to round the foe. 2.96.3 (user request: "3참격이 너무 크고
 * 이펙트가 여전히 조금 이상해 돌진도 그렇고", the size "약 5블록·굵기 0.25" and then "크기 조금만 더 키워도 될듯", and "저거
 * 3참격 랜덤으로 나오게"): its light a twentieth of a second later and hiding her, its rim a broad band; the three slashes
 * 6.5 blocks long and a third of a block wide (2.96.6, "3참격 더 커도 될듯": 8.5 and 0.42),
 * crossing just in front of the foe each at an angle of its own rolled anew for every T, leaning toward the side her camera
 * stays on (2.96.6: where she began her row of T's; 2.96.3 where she stands as they cut), short of it and above the ground;
 * all of it gone while T is drawn.
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
        assertTrue(SwordLunge.RIM>=.6&&SwordLunge.RIM<SwordLunge.HALF*.5,"a broad band round it, not filling it");
        // laid in a blink a twentieth of a second after she sets off (the video's 0.2 s), on the foe by the video's 0.24 s,
        // opening round it at once; a broad band of ink by 0.37 s, gone by some 0.5 s
        float go=IsosangRules.LUNGE_GO/20f,l=SwordLunge.light();
        assertEquals(.2f,l,1e-6,"its light comes at the video's 0.2 s");
        assertTrue(l+SwordLunge.RACE<=.25f,"on the foe by the video's 0.24 s");
        assertTrue(l+SwordLunge.FILL_INK1<=.4f&&l+SwordLunge.RIM_INK1<=.38f,"cooled, its rim a band of ink by 0.37 s");
        assertTrue(l+SwordLunge.RIM_EAT1>=.48f&&l+SwordLunge.RIM_EAT1<=.55f,"gone by some 0.5 s");
        assertTrue(SwordLunge.AIM1<go&&SwordLunge.AIM_GONE1<=l+SwordLunge.FILL_EAT0+.01f,"its line of ink drawn before she goes, gone under its light");
    }
    @Test void itsThreeSlashesAreShortThinBladesRolledAnewJustInFrontOfTheFoe(){
        Vec3d foe=new Vec3d(10,71,-4),feet=foe.subtract(0,SwordLunge.FOE_MID,0);
        java.util.Random r=new java.util.Random(7);java.util.Set<Long> seen=new java.util.HashSet<>();double lo=99,hi=-99;
        double[] fars={4,6,9,14,22};double spread=0;int casts=0;
        for(int n=0;n<300;n++){
            UUID id=new UUID(r.nextLong(),r.nextLong());
            double far=fars[n%fars.length];
            Vec3d toward=Vec3d.fromPolar(0,(float)(r.nextDouble()*360)).multiply(far),fw=toward.normalize(),her=feet.add(toward);
            // (2.96.6) toward the side her camera stays on, the sword she began her row of T's from; her camera behind it, over
            // her right shoulder (the formation's 3.2 blocks back, a block to her right, high), standing still there through them
            Vec3d rt=new Vec3d(-fw.z,0,fw.x),camera=her.add(fw.multiply(3.2)).add(rt.multiply(1)).add(0,2.6,0);
            double[] ang=SwordLunge.angles(id);
            for(int j=0;j<IsosangRules.LUNGE_SLASHES;j++){
                var s=SwordLunge.blade(id,foe,toward,j);
                // the user's choice: some 5 blocks long and a quarter of a block wide, then "a little bigger": 6.5 and 0.32, then
                // (2.96.6) "3참격 더 커도 될듯": a third bigger again, 8.5 and 0.42
                assertEquals(8.5,s.far().distanceTo(s.near()),1e-6,"slash "+j+" 8.5 blocks long");
                assertEquals(.42,s.width(),1e-9,"slash "+j+" 0.42 of a block wide");
                // the same every time it is drawn (and on every client: rolled from the swing's id)
                assertEquals(0,SwordLunge.blade(id,foe,toward,j).far().distanceTo(s.far()),1e-12);
                // on the foe: crossing just in front of it, its line passing by its column
                assertTrue(SwordLunge.level(s,feet)<1.4,"slash "+j+" misses the foe: "+SwordLunge.level(s,feet));
                if(far>=6){double front=s.point(.5).subtract(feet).dotProduct(fw);assertTrue(front>-1.2&&front<3,"slash "+j+" in front of the foe: "+front);}
                // its lower end toward her; never under the ground, nor through her or her camera
                assertTrue(s.way().dotProduct(fw)>.15,"slash "+j+" toward her");
                assertTrue(s.near().y<=s.far().y+.2,"slash "+j+" coming down toward her");
                assertTrue(Math.min(s.far().y,s.near().y)>=feet.y+SwordLunge.GROUND-1e-6,"slash "+j+" in the ground");
                assertTrue(SwordLunge.level(s,her)>=2.5,"slash "+j+" through her: "+SwordLunge.level(s,her)+" at "+far);
                assertTrue(near(s,camera)>=2.5,"slash "+j+" through her camera: "+near(s,camera)+" at "+far);
                assertTrue(Math.abs(ang[j])<=SwordLunge.STEEP+1e-9,"slash "+j+" too steep: "+ang[j]);
                lo=Math.min(lo,ang[j]);hi=Math.max(hi,ang[j]);seen.add(Math.round(ang[j]));
            }
            // each a way of its own: any two well apart
            for(int a=0;a<ang.length;a++)for(int c=a+1;c<ang.length;c++)
                assertTrue(Math.abs(ang[a]-ang[c])>=SwordLunge.APART-1e-9,"slashes "+a+" and "+c+" alike: "+ang[a]+" "+ang[c]);
            // crossing one another here and there, not all through one point (not a star on the foe: 2.96.0 to 2.96.1's)
            if(far>=6){
                var b0=SwordLunge.blade(id,foe,toward,0);var b1=SwordLunge.blade(id,foe,toward,1);var b2=SwordLunge.blade(id,foe,toward,2);
                spread+=Math.max(b0.nearest(foe).distanceTo(b1.nearest(foe)),Math.max(b1.nearest(foe).distanceTo(b2.nearest(foe)),b0.nearest(foe).distanceTo(b2.nearest(foe))));casts++;
            }
        }
        assertTrue(spread/casts>.6,"where they pass the foe some way apart on average: "+spread/casts);
        // rolled anew: all sorts of angles over many casts, steep one way to steep the other
        assertTrue(seen.size()>60,"angles seen: "+seen.size());assertTrue(lo<-40&&hi>40,"from "+lo+" to "+hi);
        // drawn on in a breath, bright a moment, ink, and gone while T is drawn (the video: some 0.3 s each)
        float last=IsosangRules.lungeSlash(IsosangRules.LUNGE_SLASHES-1);
        assertTrue(SwordLunge.DRAW<=.06f&&SwordLunge.LIT>=.1f&&SwordLunge.EAT1>=.38f&&SwordLunge.EAT1<=.5f);
        assertTrue(last+SwordLunge.EAT1<=SwordLunge.LIFE,"gone while T is drawn");
    }
    @Test void herAfterimagesAreFullAsSheSetsOffThenHerLightHidesHer(){
        float go=IsosangRules.LUNGE_GO/20f,l=SwordLunge.light(),land=IsosangRules.LUNGE_LAND/20f;
        for(float t=go;t<=l+.015f;t+=.005f)assertEquals(1,SwordLunge.afterimages(t),1e-6,"full as she sets off");
        // hidden in its light until just before she is down in front of the foe (the video), seen there
        for(float t=l+.005f;t<land-.015f;t+=.005f)assertTrue(IsosangRules.lungeHidden(t*20),"hidden in its light at "+t);
        assertFalse(IsosangRules.lungeHidden(land*20),"seen where she lands");
        assertEquals(.25f,SwordLunge.afterimages(land),1e-6,"faint again where she lands");
        assertEquals(.25f,SwordLunge.afterimages(0),1e-6);assertEquals(.25f,SwordLunge.afterimages(.8f),1e-6);
        assertNotEquals(0x4a7cff,SwordLunge.GHOST,"violet, not the sword's hits' blue");
    }
    /** How near slash {@code s} comes to {@code p}. */
    static double near(SwordLunge.Blade s,Vec3d p){
        double best=99;for(int i=0;i<=400;i++)best=Math.min(best,s.point(i/400.0).distanceTo(p));
        return best;
    }
}
