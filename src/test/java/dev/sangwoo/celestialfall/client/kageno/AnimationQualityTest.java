package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.stand.KagenoRules;
import java.util.ArrayList;
import java.util.List;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Every Kageno body clip, sampled like a 60 fps renderer and finer: no pose jumps or limb flips (only camera cuts
 * may jump), every authored key reached on its time, hit-stops sitting on hits and over before the next one, and a
 * cheap frame. Hit timing is the server's ({@link KagenoRules#damage}); the clips only follow it.
 */
class AnimationQualityTest {
    record Clip(String name,Anim anim,int skill,int combo){}
    static List<Clip> clips(){
        List<Clip> out=new ArrayList<>();
        for(int c=1;c<=4;c++)out.add(new Clip("M1-"+c,KagenoSkills.of(KagenoRules.M1).clip(c),KagenoRules.M1,c));
        for(int s:new int[]{KagenoRules.E,KagenoRules.R,KagenoRules.T,KagenoRules.RUSH,KagenoRules.Y,KagenoRules.H,KagenoRules.P})
            out.add(new Clip(KagenoRules.NAMES[s],KagenoSkills.of(s).clip(0),s,0));
        out.add(new Clip("guard",KagenoClips.GUARD_IN,-1,0));
        return out;
    }
    static Quaternionf q(float[] e){float k=(float)Math.PI/180;return new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k);}
    static double turn(R6.Frame a,R6.Frame b,int j){return Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(q(a.joints().joint[j]).dot(q(b.joints().joint[j]))))));}
    static boolean nearCut(Anim a,double t,double within){
        float[] k=a.times();for(int i=1;i<k.length;i++)if(a.ease(i)==Anim.Ease.CUT&&Math.abs(k[i]-t)<within)return true;return false;
    }
    static boolean inStop(Anim a,double t){for(var s:a.stops())if(t>=s.sec()&&t<s.sec()+s.hold()+s.recover())return true;return false;}

    @Test void noClipJumpsOrFlipsItsLimbsExceptOnACameraCut(){
        for(var c:clips()){
            Anim a=c.anim();double h=1/960.0;
            for(double t=h;t+2*h<=a.length();t+=h){
                if(nearCut(a,t,3*h))continue;
                var p0=a.frame((float)(t-h));var p1=a.frame((float)t);var p2=a.frame((float)(t+h));var p3=a.frame((float)(t+2*h));
                for(int j=0;j<6;j++){
                    double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                    assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,c.name()+" joint "+j+" jumps "+now+" deg at "+t);
                }
            }
            // Between two 60 fps frames no joint turns more than a third of a turn (fast cuts carry afterimages).
            for(double t=0;t+1/60.0<=a.length();t+=1/60.0){
                if(nearCut(a,t,1/30.0))continue;
                for(int j=0;j<6;j++)assertTrue(turn(a.frame((float)t),a.frame((float)(t+1/60.0)),j)<125,c.name()+" joint "+j+" flips at "+t);
            }
        }
    }
    @Test void everyAuthoredKeyIsReachedOnItsTime(){
        for(var c:clips()){
            Anim a=c.anim();float[] k=a.times();
            for(int i=0;i<k.length;i++){
                if(inStop(a,k[i])&&a.stops()[0].sec()!=k[i])continue;
                if(i+1<k.length&&k[i+1]==k[i])continue;
                var f=a.frame(k[i]);var key=a.key(i);
                for(int j=0;j<6;j++)assertTrue(turn(f,key,j)<.1,c.name()+" key "+i+" joint "+j);
                assertEquals(key.fw(),f.fw(),1e-4,c.name()+" key "+i);assertEquals(key.up(),f.up(),1e-4,c.name()+" key "+i);
                assertEquals(key.yaw(),f.yaw(),.01,c.name()+" key "+i);
            }
        }
    }
    @Test void hitStopsSitOnHitsAndAreOverBeforeTheNextHit(){
        for(var c:clips()){
            if(c.skill()<0)continue;
            List<Float> hits=new ArrayList<>();
            for(int age=0;age<=KagenoRules.DURATION[c.skill()];age++)if(KagenoRules.damage(c.skill(),age,c.combo())>0)hits.add(age/20f);
            for(var s:c.anim().stops()){
                assertEquals(s.sec(),c.anim().warp(s.sec()),1e-6,c.name()+": the impact itself is on time");
                float end=s.sec()+s.hold()+s.recover();
                assertTrue(hits.stream().anyMatch(h->Math.abs(h-s.sec())<=.05f),c.name()+": hit-stop at "+s.sec()+" is not on a hit "+hits);
                for(float h:hits)if(h>s.sec()+.05f)assertTrue(end<=h,c.name()+": hit-stop at "+s.sec()+" still catching up at the next hit "+h);
            }
        }
    }
    @Test void aFrameIsCheapToSample(){
        var all=clips();int n=0;
        for(int warm=0;warm<3;warm++)for(var c:all)for(float t=0;t<c.anim().length();t+=.01f)c.anim().frame(t+warm*.001f);
        long start=System.nanoTime();
        for(var c:all)for(float t=0;t<c.anim().length();t+=1/240f){c.anim().frame(t);n++;}
        double micros=(System.nanoTime()-start)/1000.0/n;
        assertTrue(micros<100,"one frame takes "+micros+" microseconds");
    }
}
