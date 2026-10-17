package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** M1 "검술의 대가": four original hits, each blade cutting on the server's hit, and the combo handing over smoothly. */
class ComboSkillTest {
    static final ComboSkill SKILL=new ComboSkill();
    /** The sword tip in the cast frame (forward, up, right) for hit {@code combo} at {@code sec}, the feet at the origin. */
    static double[] tip(int combo,float sec){
        var hb=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(SKILL.clip(combo).frame(sec)));
        Vec3d t=hb[0].add(hb[1].multiply(ComboSkill.TIP));return new double[]{t.z,t.y,-t.x};
    }
    @Test void everyBladeCutsThroughTheFrontAsTheServerStrikes(){
        for(int combo=1;combo<=4;combo++){
            double[] p=tip(combo,ComboSkill.HIT);
            assertTrue(p[0]>.5,"hit "+combo+" tip ahead: "+p[0]);
            assertTrue(Math.hypot(p[0],p[2])<3.4,"hit "+combo+" within the server's reach");
            assertTrue(p[1]>-.25&&p[1]<2.4,"hit "+combo+" height "+p[1]);
            for(int age=0;age<=KagenoRules.DURATION[KagenoRules.M1];age++)
                if(KagenoRules.damage(KagenoRules.M1,age,combo)>0)assertEquals(ComboSkill.HIT,age/20f,1e-6);
        }
        assertEquals(KagenoRules.M1_LAUNCH/20f,ComboSkill.LAUNCH,1e-6);
    }
    @Test void eachHitSweepsItsOwnWay(){
        double[] a=tip(1,.21f),b=tip(1,.385f);
        assertTrue(b[1]>a[1]+.8&&b[2]>a[2]+1,"hit 1 rises from the low left to the high right");
        a=tip(2,.21f);b=tip(2,.40f);
        assertTrue(b[1]<a[1]-.8&&b[2]<a[2]-1,"hit 2 comes down from the high right to the low left");
        a=tip(3,.225f);b=tip(3,ComboSkill.HIT);
        assertTrue(a[1]>b[1]+1.2&&b[1]<.35&&b[0]>1,"hit 3 comes down from overhead into the floor ahead");
        a=tip(4,.24f);b=tip(4,ComboSkill.HIT);
        assertTrue(b[0]>a[0]+.6,"hit 4 thrusts");
        a=tip(4,.585f);b=tip(4,.8f);
        assertTrue(b[1]>a[1]+1.5,"hit 4's last cut goes straight up");
    }
    @Test void theCombosHandOverWithoutJumps(){
        // A click at 0.4 s or later starts the next hit from the pose on screen; each next clip starts close to it.
        for(int combo=1;combo<4;combo++){
            var next=SKILL.clip(combo+1).frame(0);
            for(float at:new float[]{.42f,.5f,.62f}){
                var now=SKILL.clip(combo).frame(at);
                for(int j=0;j<6;j++)assertTrue(TimeWalkerSkillTest.angle(now.joints().joint[j],next.joints().joint[j])<35,"hit "+combo+" at "+at+" joint "+j);
                assertEquals(0,MathHelper.wrapDegrees(now.yaw()-next.yaw()),12,"hit "+combo+" at "+at+" facing");
                assertEquals(now.fw(),next.fw(),.15);assertEquals(now.up(),next.up(),.15);
            }
        }
    }
    @Test void theThirdHitLeapsAWholeTurnAndLandsFacingTheFront(){
        var h3=SKILL.clip(3);
        assertEquals(360,h3.frame(h3.length()).yaw(),1e-3);
        assertTrue(h3.frame(.2f).up()>.2,"in the air while it turns");
        assertTrue(h3.frame(ComboSkill.HIT).up()<0,"down for the slam");
        assertEquals(0,MathHelper.wrapDegrees(h3.frame(ComboSkill.HIT).yaw()),15);
    }
    @Test void theSealHoldsWhileTheServerHoldsTheTarget(){
        assertEquals(0,ComboSkill.sealed(ComboSkill.HIT-.02f),1e-6);
        for(float t=ComboSkill.HIT+.04f;t<ComboSkill.LAUNCH-.005f;t+=.01f)assertTrue(ComboSkill.sealed(t)>.95f,"at "+t);
        assertEquals(0,ComboSkill.sealed(ComboSkill.LAUNCH+.04f),1e-6);
        float step=0;for(float t=0;t<1.2f;t+=1/240f)step=Math.max(step,Math.abs(ComboSkill.sealed(t+1/60f)-ComboSkill.sealed(t)));
        assertTrue(step<.75f,"the seal pops in a single frame: "+step);
    }
    @Test void everythingIsOverBeforeTheCastEnds(){
        float end=KagenoRules.DURATION[KagenoRules.M1]/20f;
        for(int combo=1;combo<=4;combo++){var s=ComboSkill.slash(combo);assertTrue(s.from()<s.to()&&s.to()<=s.eat1()&&s.eat1()<end,"slash "+combo);}
        assertTrue(SKILL.clip(4).length()<=end);
    }
}
