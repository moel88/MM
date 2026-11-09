package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * (2.104.0, the user: "R6을 빼고 모든 모션을 전부 자연스럽게 만들어줘", G aside) Lee So-sang's limbs bent as a person's:
 * every clip but G's keeps the hand on the line its keys put it and the blade the way they point it, the elbows and
 * knees bent, the feet on what she stands on and never in it, no bend jumping; G keeps the R6 kit's straight limbs; the
 * stance is the game's own hold.
 */
class IsosangLimbsTest {
    /** Every clip that bends, with the ground under it. */
    static Map<String,Object[]> clips(){
        Map<String,Object[]> m=new LinkedHashMap<>();
        for(int c=1;c<=4;c++)m.put("hit "+c,new Object[]{IsosangSlash.hit(c),IsosangLimbs.FLOOR});
        m.put("throws",new Object[]{IsosangSlash.hit(IsosangRules.THROWS),IsosangLimbs.FLOOR});
        m.put("thrust",new Object[]{IsosangSlash.hit(IsosangRules.THRUST),IsosangLimbs.FLOOR});
        m.put("sweep",new Object[]{IsosangSlash.hit(IsosangRules.SWEEP),IsosangLimbs.FLOOR});
        m.put("formation",new Object[]{IsosangSlash.hit(IsosangRules.FORMATION),IsosangLimbs.FORMATION});
        m.put("lunge",new Object[]{IsosangSlash.hit(IsosangRules.LUNGE),IsosangLimbs.LUNGE});
        m.put("cage",new Object[]{IsosangSlash.hit(IsosangRules.CAGE),IsosangLimbs.CAGE});
        m.put("finale",new Object[]{IsosangSlash.hit(IsosangRules.FINALE),IsosangLimbs.FINALE});
        return m;
    }
    /** The seconds to look at in clip {@code a} (the formation's long middle only now and then). */
    static double step(Anim a,double t,double h){return a.length()>30&&t>3&&t<a.length()-1.5?.25:h;}
    static double angle(R6.Dir a,R6.Dir b){return Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,a.norm().dot(b.norm())))));}

    @Test void theHandStaysOnItsLineAndTheBladeItsWay(){
        for(var e:clips().entrySet()){
            Anim a=(Anim)e.getValue()[0],raw=a.unfixed();
            for(double t=0;t<=a.length();t+=step(a,t,1/120.0)){
                var bent=R6.poseOf(a.frame((float)t));var keyed=R6.poseOf(raw.frame((float)t));
                assertTrue(angle(bent.blade,keyed.blade)<.25,e.getKey()+": the blade turned "+angle(bent.blade,keyed.blade)+" deg at "+t);
                assertTrue(angle(bent.rArm,keyed.rArm)<.25,e.getKey()+": the sword hand off its line by "+angle(bent.rArm,keyed.rArm)+" deg at "+t);
                assertTrue(angle(bent.lArm,keyed.lArm)<.25,e.getKey()+": the free hand off its line by "+angle(bent.lArm,keyed.lArm)+" deg at "+t);
                // the elbows bent (folding the forearm toward its front), never straight past it, never folded flat
                assertTrue(bent.rElbow<=0&&bent.rElbow>-93&&bent.lElbow<0&&bent.lElbow>-89,e.getKey()+": elbows "+bent.rElbow+" "+bent.lElbow+" at "+t);
                assertTrue(bent.rKnee>=0&&bent.rKnee<150&&bent.lKnee>=0&&bent.lKnee<150,e.getKey()+": knees "+bent.rKnee+" "+bent.lKnee+" at "+t);
            }
        }
    }
    @Test void theFeetStandOnWhatSheStandsOnAndNeverGoIntoIt(){
        for(var e:clips().entrySet()){
            Anim a=(Anim)e.getValue()[0],raw=a.unfixed();var g=(IsosangLimbs.Ground)e.getValue()[1];
            for(double t=0;t<=a.length();t+=step(a,t,1/240.0)){
                double ground=g.at((float)t);double[] f=IsosangSlash.feet(a.frame((float)t)),k=IsosangSlash.feet(raw.frame((float)t));
                for(int i=0;i<2;i++){
                    assertTrue(f[i]-ground>-.005,e.getKey()+": foot "+i+" "+(f[i]-ground)+" into what she stands on at "+t);
                    // a foot the keys stand on the ground still stands on it (drawn in under her as she comes down)
                    if(Math.abs(k[i]-ground)<.004&&hip(raw.frame((float)t),i)-ground<.7)
                        assertTrue(f[i]-ground<.01,e.getKey()+": foot "+i+" off the ground ("+(f[i]-ground)+") where the keys stand on it, at "+t);
                }
            }
        }
    }
    /** How high hip {@code i} of frame {@code f} is over the frame's feet. */
    static double hip(R6.Frame f,int i){
        var q=IsosangLimbs.torso(f);return IsosangLimbs.hip(q,f,i).y;
    }
    @Test void aCrouchBendsTheKneesOverTheFeet(){
        // the low crouches (the root well down): the knees bent, both feet on the ground, under her rather than splayed out
        Object[][] crouches={{IsosangRules.SWEEP,.64f},{IsosangRules.SWEEP,.88f},{IsosangRules.LUNGE,IsosangRules.LUNGE_LAND/20f},
            {IsosangRules.FORMATION,.14f},{IsosangRules.FINALE,IsosangRules.FINALE_LAND}};
        for(var c:crouches){
            int combo=(int)c[0];float t=(float)c[1];var f=IsosangSlash.hit(combo).frame(t);
            assertTrue(f.up()<-.2,"a crouch at "+combo+"/"+t);
            assertTrue(f.joints().knee[0]>35&&f.joints().knee[1]>25,"knees bent in the crouch at "+combo+"/"+t+": "+f.joints().knee[0]+" "+f.joints().knee[1]);
            var keyed=IsosangSlash.hit(combo).unfixed().frame(t);
            for(int i=0;i<2;i++){
                var q=IsosangLimbs.torso(f);var hip=IsosangLimbs.hip(q,f,i);
                double out=reachOut(f,i),keyedOut=reachOut(keyed,i);
                assertTrue(out<keyedOut-.05||keyedOut<.2,"foot "+i+" drawn in under the hip at "+combo+"/"+t+": "+out+" (the keys' "+keyedOut+")");
            }
        }
    }
    /** How far out along the ground foot {@code i} of {@code f} is from its hip (blocks). */
    static double reachOut(R6.Frame f,int i){
        var p=R6.poseOf(f);var q=IsosangLimbs.torso(f);
        var leg=IsosangLimbs.hit(q,IsosangLimbs.V.of(i==0?p.rLeg:p.lLeg)).mul(R6.legReach(i==0?p.rKnee:p.lKnee));
        return Math.hypot(leg.x,leg.z);
    }
    @Test void inALeapTheKneesTuck(){
        // H's hang in the air and her leap off the pommel in Y: both knees bent
        for(float t:new float[]{.4f,.5f,.6f}){var f=IsosangSlash.hit(IsosangRules.FORMATION).frame(t);assertTrue(f.joints().knee[0]>25&&f.joints().knee[1]>25,"tucked in H's leap at "+t+": "+f.joints().knee[0]+" "+f.joints().knee[1]);}
        for(float t:new float[]{.25f,.32f}){var f=IsosangSlash.hit(IsosangRules.CAGE).frame(t);assertTrue(f.joints().knee[0]>25&&f.joints().knee[1]>25,"tucked in Y's leap at "+t+": "+f.joints().knee[0]+" "+f.joints().knee[1]);}
    }
    @Test void noElbowOrKneeJumps(){
        double h=1/960.0;
        for(var e:clips().entrySet()){
            Anim a=(Anim)e.getValue()[0];
            for(double t=h;t+2*h<=a.length();t+=a.length()>30&&t>3&&t<a.length()-1.5?.25:h){
                var p0=a.frame((float)(t-h)).joints();var p1=a.frame((float)t).joints();var p2=a.frame((float)(t+h)).joints();var p3=a.frame((float)(t+2*h)).joints();
                for(int i=0;i<4;i++){
                    float[] b0=i<2?p0.elbow:p0.knee,b1=i<2?p1.elbow:p1.knee,b2=i<2?p2.elbow:p2.knee,b3=i<2?p3.elbow:p3.knee;int j=i%2;
                    double before=Math.abs(b1[j]-b0[j]),now=Math.abs(b2[j]-b1[j]),after=Math.abs(b3[j]-b2[j]);
                    assertFalse(now>3&&now>4*Math.max(before,after)+1e-9,e.getKey()+": "+(i<2?"elbow ":"knee ")+j+" jumps "+now+" deg at "+t);
                }
            }
            for(double t=0;t+1/60.0<=a.length();t+=a.length()>30&&t>3&&t<a.length()-1.5?.25:1/60.0){
                var p=a.frame((float)t).joints();var q=a.frame((float)(t+1/60.0)).joints();
                for(int j=0;j<2;j++){
                    assertTrue(Math.abs(q.elbow[j]-p.elbow[j])<75,e.getKey()+": elbow "+j+" snaps "+(q.elbow[j]-p.elbow[j])+" deg in a frame at "+t);
                    assertTrue(Math.abs(q.knee[j]-p.knee[j])<55,e.getKey()+": knee "+j+" snaps "+(q.knee[j]-p.knee[j])+" deg in a frame at "+t);
                }
            }
        }
    }
    @Test void gKeepsTheR6KitsStraightLimbs(){
        var g=IsosangSlash.hit(IsosangRules.ULTIMATE);
        for(double t=0;t<=g.length();t+=1/60.0){
            MotionPose m=g.frame((float)t).joints();
            for(int j=0;j<2;j++)assertTrue(m.elbow[j]==0&&m.knee[j]==0,"G bends a limb at "+t);
        }
    }
    @Test void theStanceIsTheGamesHold(){
        var s=IsosangSlash.stance();float[] arm=s.joints().joint[MotionPose.RIGHT_ARM];
        assertEquals(Math.toDegrees(IsosangSlash.HOLD_PITCH),arm[0],.05);
        assertEquals(Math.toDegrees(IsosangSlash.HOLD_YAW),arm[1],.05);
        assertEquals(Math.toDegrees(IsosangSlash.HOLD_ROLL),arm[2],.05);
        assertEquals(Math.toDegrees(IsosangSlash.HOLD_ELBOW),s.joints().elbow[0],.05,"the sword arm's elbow as the game's hold bends it");
        assertEquals(IsosangSlash.DISPLAY_TILT,s.grip(),.05,"the sword turned in the hand as the game holds it");
        for(int j=0;j<2;j++)assertTrue(s.joints().knee[j]<.5,"standing straight");
        // and every clip but G's begins and ends there
        for(var e:clips().entrySet()){
            Anim a=(Anim)e.getValue()[0];
            var first=a.frame(0);var last=a.frame(a.length());
            // (hits 2 and 3 begin where the one before handed over, H again on the pommel)
            boolean begins=!e.getKey().equals("finale")&&!e.getKey().equals("hit 2")&&!e.getKey().equals("hit 3");
            for(var f:new R6.Frame[]{begins?first:null,last}){
                if(f==null)continue;
                assertEquals(s.joints().elbow[0],f.joints().elbow[0],.05,e.getKey()+" in the hold's elbow");
                assertEquals(s.grip(),f.grip(),.05,e.getKey()+" in the hold's grip");
            }
        }
        assertEquals(0,R6.solve(IsosangSlash.G_STANCE).joints().elbow[0],0,"G's stance is its own, straight");
    }
    @Test void theSwordArmBendsTheMoreItReachesAcrossOrUp(){
        // its elbow bent a little when the hand hangs at her side, more reaching across her to the left or up
        IsosangLimbs.V hang=IsosangLimbs.V.of(R6.dir(-80,5)).unit(),across=IsosangLimbs.V.of(R6.dir(0,-70)).unit(),up=IsosangLimbs.V.of(R6.dir(60,10)).unit(),ahead=IsosangLimbs.V.of(R6.dir(0,0)).unit();
        double h=IsosangLimbs.swordBend(hang),a=IsosangLimbs.swordBend(across),u=IsosangLimbs.swordBend(up),f=IsosangLimbs.swordBend(ahead);
        assertTrue(h>3&&h<12,"hanging: "+h);assertTrue(a>50,"across: "+a);assertTrue(u>30,"up: "+u);assertTrue(f<h,"straight out ahead: "+f);
    }
}
