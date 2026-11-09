package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.XCharaRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's G, the sword dance (2.112.0, user request: "바로 G스킬 부터 만들자 상대를 쳐다보고 G스킬 누르면 !!!가 나와 이때
 * 가드로 방어하면 G스킬을 씹을 수 있고 못 막으면 그대로 G스킬 컷씬이 나오게 ... 적이 엄청난 칼을 맞아서 썰리는 스킬이야 다
 * 맞으면 날아가", and the user's video; answers: 60 in all, 3 minutes, a guard calls it off): its timing on the video's
 * moments, its blows and its damage, who it holds when.
 */
class XCharaRulesTest {
    /** The video's moment {@code s} seconds in as a tick of the G (its press 0.45 s in). */
    static double video(double s){return (s-.45)*20;}

    @Test void itsMomentsAreTheVideos(){
        // the "!!!" at 0.6 s, the strange space at 1.35 s (the two upside down from its first moment: 2.112.1), the camera
        // pulled back at 4.35 s, the two gone and the first blow at 7.4 s, the world back at 7.75 s, the 40th blow at
        // 11.3 s, sent flying at 11.4 s
        double[][] at={{MARK,.6},{WARN,1.35},{SHOT_C,4.35},{VANISH,7.4},{WORLD,7.75},{blowAt(HITS-1),11.3},{LAUNCH,11.4}};
        for(var a:at)assertEquals(video(a[1]),a[0],1.01,"tick "+a[0]+" against the video's "+a[1]+" s");
        assertTrue(MARK<WARN&&WARN<SHOT_C&&SHOT_C<VANISH&&VANISH<WORLD&&WORLD<blowAt(HITS-1)&&blowAt(HITS-1)<LAUNCH);
        assertEquals(LAUNCH,END,"it is over as they are sent flying");
    }
    @Test void fortyBlowsTenASecondSixtyInAll(){
        assertEquals(40,HITS);assertEquals(2,EVERY,"ten a second");
        float sum=0;for(int k=0;k<HITS;k++){sum+=blowDamage(k);assertEquals(1.5f,blowDamage(k),1e-6);}
        assertEquals(60,sum,1e-4,"the user's answer: 60 in all");
        assertEquals(0,blowDamage(-1));assertEquals(0,blowDamage(HITS));
        assertEquals(VANISH,blowAt(0),"the first blow as the two vanish (the video's hit count: 1 at 7.4 s)");
        // a blow lands only on its own tick, once
        int seen=0;for(long t=0;t<=END+10;t++){int k=blowIndex(t);if(k>=0){assertEquals(blowAt(k),t);assertEquals(seen,k);seen++;}}
        assertEquals(HITS,seen);
        assertEquals(0,blowsBy(VANISH-.5));assertEquals(1,blowsBy(VANISH));assertEquals(1,blowsBy(VANISH+1.9));assertEquals(2,blowsBy(VANISH+2));assertEquals(HITS,blowsBy(END+50));
    }
    @Test void aGuardBlocksItOnlyWhileTheMarkShows(){
        for(long t=-2;t<=END;t++)assertEquals(t>=0&&t<WARN,warning(t),"tick "+t);
        assertEquals(18,WARN,"0.9 s to raise a guard");
    }
    @Test void itHoldsItsUserFromThePressAndTheOneMarkedFromTheStrangeSpace(){
        for(long t=-1;t<=END+1;t++){
            assertEquals(t>=0&&t<END,userHeld(t),"its user at "+t);
            assertEquals(t>=WARN&&t<END,held(t),"the two at "+t);
        }
        assertTrue(inSpace(WARN));assertTrue(inSpace(WORLD-.01));assertFalse(inSpace(WORLD));assertFalse(inSpace(WARN-.01));
    }
    @Test void itsCooldownIsThreeMinutesAndItReachesAsFarAsTheVideosDummy(){
        assertEquals(3*60*20,COOLDOWN,"the user's answer: 3 minutes");
        assertTrue(AIM_RANGE>=8&&AIM_RANGE<=16);
        assertEquals(14,XCharaRules.SKILL,"its place among the effects' skills: Kageno's 0 to 9, Lee So-sang's 15");
    }
    /** Sent flying: as far as Star Platinum's heavy punch throws (the game's flight: gravity, then air drag, each tick). */
    @Test void sentFlyingFar(){
        double x=0,y=0,vx=FLY,vy=FLY_UP,top=0;
        for(int i=0;i<200&&(i==0||y>0);i++){x+=vx;y+=vy;top=Math.max(top,y);vy=(vy-.08)*.98;vx*=.91;}
        assertTrue(x>12&&x<40,"thrown "+x+" blocks");
        assertTrue(top>1.5,"up "+top+" blocks");
    }
}
