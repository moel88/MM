package dev.sangwoo.celestialfall.client.xchara;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What is about X Chara the boss (2.137.0, user request: "몸에 전체적으로 글리치 아우라가 둘러져 있게 ... 눈에는 붉은색
 * 빛과 아우라"): the glitch stays round its body and never lies on it, its silhouettes are a little bigger than it and
 * split pink to one side and cyan to the other, what shines of it breathes and dips but never goes out, and the red
 * eye has no light of its own (2.137.1).
 */
class BossAuraTest {
    static final double H=1.8,W=.6;

    @Test void theVioletSilhouetteIsNearlyAlwaysThereAndTheOthersSplitToEitherSide(){
        int violet=0,pink=0,cyan=0,n=14*120;
        for(int s=0;s<n;s++){
            float clock=(s+.5f)/BossAura.STEP_RATE;
            var v=BossAura.rim(0,clock,77,1);
            if(v!=null){violet++;assertTrue(v[2]>=BossAura.RIM_SWELL&&v[2]<=BossAura.RIM_SWELL+.031f,"a little bigger than the body");assertTrue(Math.abs(v[0])<=.26f,"thrown no further than a quarter block");}
            var p=BossAura.rim(1,clock,77,1);if(p!=null){pink++;assertTrue(p[0]<0&&p[0]>-.5f,"pink to the left");}
            var c=BossAura.rim(2,clock,77,1);if(c!=null){cyan++;assertTrue(c[0]>0&&c[0]<.5f,"cyan to the right");}
        }
        assertTrue(violet>n*.8,"round it nearly always: "+violet);
        assertTrue(pink>n*.3&&pink<n*.9,"the pink comes and goes: "+pink);assertTrue(cyan>n*.3&&cyan<n*.9,"the cyan too: "+cyan);
        assertEquals(GlitchFx.PURPLE,BossAura.rimColour(0));assertEquals(GlitchFx.PINK,BossAura.rimColour(1));assertEquals(GlitchFx.CYAN,BossAura.rimColour(2));
    }
    @Test void aSilhouetteHoldsThroughAStepAndChangesOnlyBetweenSteps(){
        for(int s=0;s<200;s++){
            float a=(s+.05f)/BossAura.STEP_RATE,b=(s+.95f)/BossAura.STEP_RATE;
            for(int which=0;which<3;which++)assertArrayEquals(BossAura.rim(which,a,5,1),BossAura.rim(which,b,5,1),"the same through step "+s);
        }
    }
    @Test void itsPixelsAndBarsAreRoundTheBodyNeverOnIt(){
        int seen=0,over=0,bars=0;
        for(int f=0;f<60*20;f++){
            float clock=f/60f;
            for(int j=0;j<BossAura.PIXELS;j++){
                double[] p=BossAura.pixel(j,clock,123,H,W);if(p==null)continue;
                seen++;
                boolean above=p[0]>=H;if(above)over++;
                assertTrue(above||Math.abs(p[1])>=W*.5,"pixel "+j+" outside the body's outline at "+clock+": "+p[0]+","+p[1]);
                assertTrue(Math.abs(p[1])<=W*.5+.55&&p[0]>=-.1&&p[0]<=H+1.6,"and near it");
                assertTrue(p[4]>=0&&p[4]<=1);assertTrue(p[2]>0&&p[2]<=.08);
            }
            for(int j=0;j<BossAura.BARS;j++){
                double[] b=BossAura.bar(j,clock,123,H,W);if(b==null)continue;
                bars++;
                assertTrue(Math.abs(b[1])-b[2]>=W*.5-.05,"a bar starts at the body's side and reaches outward: "+b[1]+" ± "+b[2]);
                assertTrue(b[0]>=0&&b[0]<=H,"along its height");assertTrue(b[2]<=.32&&b[3]<=.05,"short and thin");
            }
        }
        assertTrue(seen>60*20*BossAura.PIXELS*.5,"most of its pixels show at any time: "+seen);
        assertTrue(over>0,"some over its head");assertTrue(bars>60*20,"bars torn out of it all the while: "+bars);
    }
    @Test void aPixelRisesWithoutAJumpWhileItLives(){
        // 60 frames a second: while a pixel is the same one (its place across is the same), it climbs a grid step at most
        double worst=0;
        for(int j=0;j<BossAura.PIXELS;j++){
            double[] last=null;
            for(int f=0;f<60*6;f++){
                double[] p=BossAura.pixel(j,f/60f,9,H,W);
                if(p!=null&&last!=null&&p[1]==last[1]&&p[3]==last[3]&&p[0]>=last[0])worst=Math.max(worst,p[0]-last[0]);
                last=p;
            }
        }
        assertTrue(worst<=.0626,"a grid step a frame at most: "+worst);assertTrue(worst>0,"they do rise");
    }
    @Test void whatShinesBreathesAndDipsButNeverGoesOutAndIsBrighterAngry(){
        double calm=0,angry=0;float low=9,high=0;int n=60*60;
        for(int f=0;f<n;f++){
            float c=BossAura.shine(f/60f,31,0),a=BossAura.shine(f/60f,31,1);
            calm+=c;angry+=a;low=Math.min(low,c);high=Math.max(high,a);
            assertEquals(c*1.25f,a,1e-4,"a quarter brighter angry");
        }
        assertTrue(low>.25f,"never out: "+low);assertTrue(high<=1.25f,"never past its light: "+high);
        assertTrue(calm/n>.7&&calm/n<.95,"mostly lit: "+calm/n);assertTrue(angry>calm);
    }
    @Test void itsOwnLightShowsAThirdByDayAndAllOfItInTheDark(){
        float day=BossAura.byDark(0,15,1),night=BossAura.byDark(0,15,.2f),cave=BossAura.byDark(0,0,1),torch=BossAura.byDark(15,0,.2f);
        assertEquals(.34f,day,1e-4,"a third by day");assertEquals(1,cave,1e-4,"all of it in the dark");assertEquals(.34f,torch,1e-4,"a third by a torch");
        assertTrue(night>.7f&&night<1,"most of it under a night sky: "+night);
        float last=BossAura.byDark(0,15,.2f);
        for(float b=.2f;b<=1.001f;b+=.02f){float k=BossAura.byDark(0,15,b);assertTrue(k<=last+1e-5,"less as the day comes");assertTrue(last-k<.05f,"evenly");last=k;}
    }
}
