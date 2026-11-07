package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The formation's lights (2.98.0, user request: "이제 H 스킬도 너가 꾸밀수 있는 모든 걸 꾸며줘 이펙트를 대폭 강화하고 아주
 * 예쁘게 만들어봐"): its five-second beat seen exactly as the server strikes, the shimmer as the swords turn silver
 * running out from the middle, the constellation's threads between swords near one another.
 */
class FormationLightsTest {
    @Test void theBeatIsSeenJustAsTheServerStrikesAgain(){
        float end=IsosangRules.H_END;int beats=0;
        for(int age=0;age<IsosangRules.LENGTH[IsosangRules.FORMATION];age++){
            float t=age/20f,s=FormationLights.pulseAge(t,end);
            boolean strikes=IsosangRules.formationStrikes(age)&&age!=IsosangRules.FORMATION_IMPACT_AT;
            if(strikes){assertEquals(0,s,1e-4,"a beat at "+t);beats++;}
            // never before the first beat (the rain of swords is not one of them), none after the end
            if(age<IsosangRules.FORMATION_IMPACT_AT+IsosangRules.FORMATION_EVERY)assertEquals(-1,s,1e-6,"none yet at "+t);
            if(s>=0)assertTrue(s<IsosangRules.FORMATION_EVERY/20f+1e-4,"since the last beat at "+t+": "+s);
        }
        assertEquals(7,beats,"its beats: 6, 11, ... 36 s");
        // a formation ended early: no beat after its end
        assertEquals(-1,FormationLights.pulseAge(16.2f,15f),1e-6);
        assertEquals(.2f,FormationLights.pulseAge(11.2f,15f),1e-4);
    }
    @Test void theShimmerRunsOutFromTheMiddleAsTheSwordsTurnSilver(){
        var all=SwordFormation.layout(UUID.fromString("00000000-0000-0000-0000-00000000c0de"));
        for(var b:all){
            float s=FormationLights.shimmerAt(b);
            assertTrue(s>=SwordFormation.SILVER0&&s<=SwordFormation.SILVER0+FormationLights.SHIMMER_SPREAD,"while they turn silver: "+s);
            for(var o:all)if(Math.hypot(o.fw(),o.rt())>Math.hypot(b.fw(),b.rt())+.01)assertTrue(FormationLights.shimmerAt(o)>=s,"the farther out, the later");
        }
        assertTrue(SwordFormation.SILVER0+FormationLights.SHIMMER_SPREAD+FormationLights.SHIMMER<=SwordFormation.SILVER1+.25f,"done about as they are silver");
    }
    @Test void theThreadsJoinSwordsNearOneAnother(){
        UUID id=UUID.fromString("00000000-0000-0000-0000-0000000000a7");
        var all=SwordFormation.layout(id);int[] near=FormationLights.nearest(id,all);
        assertEquals(all.length,near.length);
        for(int i=0;i<all.length;i++){
            assertNotEquals(i,near[i],"another sword");
            double d=Math.hypot(all[i].fw()-all[near[i]].fw(),all[i].rt()-all[near[i]].rt());
            assertTrue(d<5,"near it: "+d);
        }
    }
    @Test void sparksLeaveTheirPlaceAndFallBack(){
        Vec3d o=new Vec3d(3,64,-2);
        assertEquals(0,FormationLights.spark(o,1,4,5,0).subtract(o.add(0,.2,0)).length(),1e-9,"out of its place");
        assertTrue(FormationLights.spark(o,1,4,5,.5).y>o.y,"up first");
        assertTrue(FormationLights.spark(o,1,4,5,1.2).y<FormationLights.spark(o,1,4,5,.6).y,"then falling back");
    }
}
