package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T's three slashes made bigger and grander (2.100.1, user request: "T스킬 3참격을 조금만 더 키우고 이펙트를 더 크고 멋있게
 * 바꿔줘 3참격에 신경을 엄청 써줘 멋있게"): their aura of their own outline, their lightning kept off their middle, the
 * snap as each is cut in a few frames, the last the grandest, and the impact frame every other T in a row.
 */
class LungeSlashesTest {
    @Test void theAuraIsTheSlashsOwnOutlineSharpAtBothTipsFullestPastItsMiddle(){
        assertTrue(LungeSlashes.profile(0)<=.03&&LungeSlashes.profile(1)<=.03,"sharp at both tips");
        double best=0,at=0;for(int i=0;i<=1000;i++){double p=LungeSlashes.profile(i/1000.0);if(p>best){best=p;at=i/1000.0;}}
        assertEquals(1,best,1e-3,"its full width at its fullest");
        assertTrue(at>.5&&at<.62,"fullest some 55% along, as its band in the shader: "+at);
        // ever wider and fainter round it, the innermost wider than the blade itself
        for(int i=0;i<LungeSlashes.AURA.length;i++){
            assertTrue(LungeSlashes.AURA[i]>1.5,"wider than the blade");
            if(i>0){assertTrue(LungeSlashes.AURA[i]>LungeSlashes.AURA[i-1]);assertTrue(LungeSlashes.AURA_ALPHA[i]<LungeSlashes.AURA_ALPHA[i-1]);}
        }
    }
    @Test void itsLightningRunsAlongItOffItsMiddleAndGoesWithItsLight(){
        for(int j=0;j<IsosangRules.LUNGE_SLASHES;j++)for(int q=0;q<40;q++)for(int s=0;s<2;s++){
            double[] u=LungeSlashes.boltSpan(j,s,q);
            assertTrue(u[0]>=.05-1e-9&&u[1]<=.95+1e-9,"off its tips");
            assertTrue(s==0?u[1]<=.42+1e-9:u[0]>=.58-1e-9,"off its middle, where it crosses the others before the foe");
            assertTrue(u[1]-u[0]>=.16-1e-9,"a sixth of it at least");
        }
        assertTrue(LungeSlashes.BOLT1<=SwordLunge.DIM,"gone as its light goes");
        // their ends just outside its edges, either side (so they wrap round it, not hidden under its band)
        int left=0;for(int i=0;i<400;i++){double o=LungeSlashes.off(i*7+3);assertTrue(Math.abs(o)>=LungeSlashes.OFF0-1e-9&&Math.abs(o)<=LungeSlashes.OFF1+1e-9);if(o<0)left++;}
        assertTrue(LungeSlashes.OFF0>.6&&left>120&&left<280,"outside its edge, on both sides: "+left);
    }
    @Test void theSnapAsItIsCutComesInAFrameOrTwoAndIsGoneInAFifthOfASecond(){
        for(int j=0;j<IsosangRules.LUNGE_SLASHES;j++){
            float at=IsosangRules.lungeSlash(j);
            assertEquals(0,LungeSlashes.snap(j,at-.02f),1e-6,"not before it is cut");
            assertTrue(LungeSlashes.snap(j,at+LungeSlashes.SNAP_AT+.015f)>.95f,"in a frame or two");
            assertTrue(LungeSlashes.snap(j,at+.25f)<.1f,"gone by a quarter of a second");
        }
    }
    @Test void theLastSlashIsTheGrandest(){
        int last=IsosangRules.LUNGE_SLASHES-1;
        for(int j=0;j<last;j++){
            assertTrue(LungeSlashes.burst(j)>.35,"each burst bigger than 2.100.0's");
            assertTrue(LungeSlashes.burst(last)>LungeSlashes.burst(j));
            assertTrue(LungeSlashes.ring(last)>LungeSlashes.ring(j));
            assertTrue(LungeSlashes.jolt(j)>.3f&&LungeSlashes.jolt(last)>LungeSlashes.jolt(j));
            assertTrue(LungeSlashes.lightReach(last)>LungeSlashes.lightReach(j)&&LungeSlashes.lightBright(last)>LungeSlashes.lightBright(j));
        }
        // all of it gone while T is drawn
        assertTrue(IsosangRules.lungeSlash(last)+LungeSlashes.RING_LIFE<=SwordLunge.LIFE);
        assertTrue(IsosangRules.lungeSlash(last)+.125f<=SwordLunge.LIFE,"its impact frame");
    }
    @Test void theImpactFrameComesOnItsOwnAndEveryOtherTInARow(){
        assertTrue(LungeSlashes.after(null),"one on its own, or the first of a row");
        Boolean before=null;boolean[] row=new boolean[6];
        for(int i=0;i<row.length;i++){row[i]=LungeSlashes.after(before);before=row[i];}
        assertArrayEquals(new boolean[]{true,false,true,false,true,false},row,"every other one in a row (0.7 s apart: else a flicker)");
    }
    @Test void itIsEatenFromBothTipsTowardItsMiddleAsItsBand(){
        assertEquals(0,LungeLights.eaten(0,true),1e-9);assertEquals(1,LungeLights.eaten(0,false),1e-9);
        assertEquals(.5,LungeLights.eaten(1,true),1e-9);assertEquals(.5,LungeLights.eaten(1,false),1e-9);
        for(float e=0;e<=1;e+=.05f){
            assertEquals(1,LungeLights.eaten(e,true)+LungeLights.eaten(e,false),1e-9,"the same from either tip");
            assertEquals(Math.max(0,Math.min(.5,(e*1.08-.04)*.5)),LungeLights.eaten(e,true),1e-9,"its front in the shader");
        }
    }
}
