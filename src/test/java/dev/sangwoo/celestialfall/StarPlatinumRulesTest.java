package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Star Platinum's basic attack (2.54.0): four punches, the fourth throwing. 2.56.0: quicker, and only the hook and the
 * finisher shout, each on the "RA" of its own from the user's recording. 2.57.0: its E, the rush. 2.59.0: its R, the heavy
 * punch. 2.60.0: its T, the Star Finger. 2.61.0: its Y, the inhale. 2.64.0: its G, the beatdown.
 */
class StarPlatinumRulesTest {
    /** Each clip of the hook's and the finisher's shouts has its RA this far in (cut_ora.py RA_AT) and starts StandAttack.LEAD (1) tick into its punch. */
    static final double[] RA_AT={0,0,0,.40,.45};
    /** The "O" of the long shouts starts this long before their RA (the recording, read off its loudness). */
    static final double O=.40;

    @Test void onlyTheHookAndTheFinisherShoutEachOnItsPunch(){
        for(int k=1;k<=4;k++){
            var clip=getClass().getResourceAsStream("/assets/celestialfall/sounds/star_platinum_ora_"+k+".ogg");
            if(shouts(k)){
                assertEquals(RA_AT[k],(CONTACT[k]-1)/20.0,1e-9,"punch "+k+"'s RA on its contact");
                assertTrue(RA_AT[k]>=O-.005,"punch "+k+" leaves its shout's whole O before the blow");
                assertNotNull(clip,"shout "+k);
            }else assertNull(clip,"punch "+k+" has no shout (2.56.0)");
        }
        assertFalse(shouts(1));assertFalse(shouts(2));assertTrue(shouts(3));assertTrue(shouts(4));
    }
    @Test void mashedTheyComeQuickAndTheFinishersShoutTakesOverFromTheHooks(){
        long start=0;double[] contact=new double[5];
        for(int k=1;k<=4;k++){contact[k]=(start+CONTACT[k])/20.0;if(k<4)start=nextAt(k,start);}
        // the jab and the cross three tenths of a second apart, the four landing well inside two seconds (2.54.0: 3.2 s)
        assertTrue(STEP[1]<=6&&STEP[2]<=6,"the jab and the cross come quick");
        assertTrue(contact[4]<1.8,"the finisher lands at "+contact[4]+" s");
        assertTrue(contact[1]<=.2+1e-9&&contact[2]-contact[1]<=.3+1e-9,"the first two snap in");
        // the hook's RA is heard a good while before the finisher's shout begins (its O starts a tick into its punch
        // and its clip's start is O short of its RA): the hook's shout then fades out under it
        double finisherShout=(nextAt(3,0)+1)/20.0+RA_AT[4]-O,hookRa=CONTACT[3]/20.0;
        assertTrue(finisherShout-hookRa>=.25,"the hook's RA rings "+(finisherShout-hookRa)+" s");
    }
    @Test void aLatePunchBreaksTheComboAndTheFourthRests(){
        assertEquals(-1,broken(0,0,500));
        assertEquals(-1,broken(1,100,100+STEP[1]+GRACE),"in time");
        assertEquals(100+STEP[1]+GRACE+1+REST,broken(1,100,100+STEP[1]+GRACE+1),"late: rests from the break");
        assertEquals(-1,broken(4,100,900),"the fourth ends the combo itself");
        assertEquals(100+LENGTH[4]+REST,rested(100),"rests once the fourth is over");
        assertEquals(1,next(0));assertEquals(2,next(1));assertEquals(4,next(3));assertEquals(1,next(4));
    }
    @Test void eachPunchStaggersItsTargetUntilTheNextLands(){
        // (2.68.0, Stagger: until the next punch's contact if it comes as soon as it may, and a little past; not longer)
        for(int k=1;k<4;k++){
            long start=100,hit=start+CONTACT[k],next=start+STEP[k]+CONTACT[k+1];
            long until=dev.sangwoo.celestialfall.combat.Stagger.stand(k,start,hit);
            assertTrue(until>next&&until<=next+3,"punch "+k+" staggers them past the next one's contact only a little");
        }
        assertEquals(100+dev.sangwoo.celestialfall.combat.Stagger.LAST,dev.sangwoo.celestialfall.combat.Stagger.stand(4,90,100),"the fourth throws them, a short stagger with it");
        assertTrue(FLY>1.4&&FLY_UP>0,"the fourth sends them flying");
    }
    // ---- E, the rush (2.57.0) ----

    @Test void heldToTheEndTheRushLandsFortyBlowsForFortyDamage(){
        assertEquals(40,RUSH_HITS);assertEquals(40,RUSH_TOTAL,1e-6);
        assertEquals(RUSH_TOTAL,RUSH_DAMAGE*RUSH_HITS,1e-4,"its damage split evenly among its blows");
        int n=0;float total=0;
        for(long age=-5;age<=RUSH_LENGTH+20;age++)if(rushBlow(age)){n++;total+=RUSH_DAMAGE;assertEquals(n,rushBlows(age),"blow "+n);}
        assertEquals(RUSH_HITS,n);assertEquals(RUSH_TOTAL,total,1e-3,"all of them together");
        assertTrue(rushBlow(RUSH_FIRST)&&!rushBlow(RUSH_FIRST+1)&&rushBlow(RUSH_FIRST+RUSH_EVERY),"a blow every "+RUSH_EVERY+" ticks");
        assertTrue(rushBlow(RUSH_LAST)&&!rushBlow(RUSH_LAST+RUSH_EVERY),"none past the last");
        assertEquals(10,20/RUSH_EVERY,"ten blows a second");
        assertEquals(4.2,RUSH_LENGTH/20.0,1e-9,"it runs 4.2 s held to the end");assertTrue(RUSH_LENGTH>RUSH_LAST);
    }
    @Test void letGoItStopsAtOnceAndItsCooldownRunsOnceItIsOver(){
        // let go early, only the blows so far land (the server stops it then: StarPlatinumRush.release)
        assertEquals(0,rushBlows(RUSH_FIRST-1));assertEquals(1,rushBlows(RUSH_FIRST));assertEquals(5,rushBlows(RUSH_FIRST+4*RUSH_EVERY));
        assertEquals(RUSH_HITS,rushBlows(RUSH_LENGTH+100));
        assertEquals(500+RUSH_COOLDOWN,rushReady(500),"from its end (2.44.0's rule)");
        assertTrue(RUSH_HOLD>RUSH_EVERY,"each blow holds whom it strikes past the next");
        assertEquals(.9,RUSH_SLOW,1e-9,"its user walks 90% slower (user request)");
    }
    @Test void theRushStrikesWhateverIsInFrontOfItsUser(){
        assertTrue(rushLane(2,0,1,.3));assertTrue(rushLane(RUSH_REACH,1.2,0,.3),"all over the front");
        assertFalse(rushLane(-1,0,1,.3),"not behind");assertFalse(rushLane(RUSH_REACH+.5,0,1,.3),"not out of reach");
        assertFalse(rushLane(2,2,1,.3),"not far to the side");assertFalse(rushLane(2,0,3.5,.3),"not far above");
    }
    @Test void itsTwoSoundsAreAsLongAsTheRush()throws Exception{
        // the voice from its start (a tick in) to its last ORA; the blows on it, ringing out a little after
        double voice=seconds("star_platinum_rush"),blows=seconds("star_platinum_rush_hits");
        assertEquals(RUSH_LENGTH/20.0,voice,.1,"the voice runs as long as the rush");
        assertTrue(blows>=voice,"the blows ring out after it: "+blows+" s");
    }
    /** How long an Ogg Vorbis sound of the mod's is (seconds): its last page's sample count at its rate. */
    static double seconds(String name)throws Exception{
        byte[] b;try(var in=StarPlatinumRulesTest.class.getResourceAsStream("/assets/celestialfall/sounds/"+name+".ogg")){assertNotNull(in,name);b=in.readAllBytes();}
        int rate=-1;long samples=-1;
        for(int i=0;i+27<b.length;i++){
            if(b[i]!='O'||b[i+1]!='g'||b[i+2]!='g'||b[i+3]!='S')continue;
            long granule=0;for(int k=7;k>=0;k--)granule=granule<<8|(b[i+6+k]&255);
            if(granule!=-1)samples=Math.max(samples,granule);
            int segments=b[i+26]&255,body=i+27+segments;
            // the identification header: 1 'vorbis', a version, the channels, then the rate
            if(rate<0&&body+16<b.length&&b[body]==1&&b[body+1]=='v')rate=(b[body+12]&255)|(b[body+13]&255)<<8|(b[body+14]&255)<<16|(b[body+15]&255)<<24;
        }
        assertTrue(rate>0&&samples>0,name);
        return samples/(double)rate;
    }
    // ---- R, the heavy punch (2.59.0) ----

    @Test void theHeavyPunchCoilsLongAndLandsHardest(){
        assertEquals(5,HEAVY);
        assertEquals(.6,CONTACT[HEAVY]/20.0,1e-9,"it lands 0.6 s in");
        for(int k=1;k<=4;k++){
            assertTrue(CONTACT[HEAVY]>CONTACT[k],"its coil is longer than punch "+k+"'s");
            assertTrue(DAMAGE[HEAVY]>DAMAGE[k],"it strikes harder than punch "+k);
        }
        assertEquals(15,DAMAGE[HEAVY],1e-6);
        assertTrue(LENGTH[HEAVY]>CONTACT[HEAVY]+10,"it holds its blow out a while once it lands");
        assertEquals(LENGTH[HEAVY],STEP[HEAVY],"the next punch may start once it is over");
        assertTrue(FLY_HEAVY>FLY&&FLY_HEAVY_UP>FLY_UP,"it throws them further than the finisher");
    }
    /**
     * How far a throw of {@code out} and {@code up} blocks a tick carries a body on flat ground, as the game moves it:
     * the first tick from the ground (the ground's friction then: 0.6 x 0.91), the air's drag (0.91, 0.98) and gravity
     * (0.08) after, and a slide to a stop on landing (2.59.0: 2.0 out went 11.5 blocks in the capture, as this says).
     */
    static double thrown(double out,double up){
        double x=0,y=0,vx=out,vy=up;boolean ground=true;
        for(int t=0;t<400;t++){
            double f=ground?.6*.91:.91;
            x+=vx;y+=vy;ground=y<=0;if(ground)y=0;
            vx*=f;vy=ground?0:(vy-.08)*.98;
            if(ground&&vx<.003)break;
        }
        return x;
    }
    @Test void itThrowsThemFarFurtherThanTheFinisher(){
        double heavy=thrown(FLY_HEAVY,FLY_HEAVY_UP),finisher=thrown(FLY,FLY_UP);
        assertEquals(11.5,thrown(2.0,.55),.1,"the throw the capture measured");
        assertTrue(heavy>15&&heavy<20,"the heavy punch throws them "+heavy+" blocks");
        assertTrue(heavy>finisher*1.5,"far further than the finisher's "+finisher+" blocks");
    }
    @Test void itStandsApartFromTheCombo(){
        assertEquals(1,next(HEAVY),"a punch after it starts the combo again");
        assertEquals(-1,broken(HEAVY,100,900),"no combo to break");
        assertFalse(shouts(HEAVY),"it has its own sound, not one of the combo's shouts");
        assertEquals(100+STEP[HEAVY],nextAt(HEAVY,100));
    }
    @Test void itsCooldownRunsOnceItIsOverAndItBreaksAGuardForTwoSeconds(){
        assertEquals(500+HEAVY_COOLDOWN,heavyReady(500),"from its end (2.44.0's rule)");
        assertEquals(8,HEAVY_COOLDOWN/20.0,1e-9,"eight seconds");
        assertTrue(HEAVY_BREAK>dev.sangwoo.celestialfall.combat.CombatState.BREAK_TICKS,"a guard it breaks stays broken longer than one worn down");
        assertEquals(2,HEAVY_BREAK/20.0,1e-9);
    }
    @Test void itReachesAsFarAsTheFinisher(){
        assertTrue(lane(HEAVY,4.2,0,1,.3),"as far as the finisher");assertFalse(lane(HEAVY,4.2+.5,0,1,.3),"not out of reach");
        assertFalse(lane(HEAVY,-1,0,1,.3),"not behind");assertFalse(lane(HEAVY,2,2,1,.3),"not far to the side");
    }
    @Test void itsTwoSoundsCrackAsTheFistLands()throws Exception{
        // both start StandAttack.HEAVY_LEAD ticks in, with the blow's crack cut_heavy.LEAD_IN (0.1 s) into them: on the contact
        assertEquals(CONTACT[HEAVY],dev.sangwoo.celestialfall.client.stand.StandAttack.HEAVY_LEAD+2,"the crack two ticks into the files");
        double voice=seconds("star_platinum_heavy"),blow=seconds("star_platinum_heavy_hit");
        assertTrue(voice>.8&&voice<2,"the voice: "+voice+" s");
        assertTrue(blow>=voice-.2&&blow<2.5,"the blow rings out about as long: "+blow+" s");
    }
    // ---- T, the Star Finger (2.60.0) ----

    @Test void theStarFingerStrikesOnTheCrackOfItsSound()throws Exception{
        assertEquals(6,FINGER);
        assertEquals(.45,CONTACT[FINGER]/20.0,1e-9,"its fingers strike 0.45 s in");
        assertEquals(LENGTH[FINGER],STEP[FINGER],"the next punch may start once it is over");
        assertEquals(1,next(FINGER),"a punch after it starts the combo again");
        assertEquals(-1,broken(FINGER,100,900),"no combo to break");
        assertFalse(shouts(FINGER),"its sound is its own");
        assertTrue(skill(FINGER)&&skill(HEAVY)&&!skill(4)&&!skill(1));
        // its sound starts StandAttack.FINGER_LEAD ticks in, the crack eight ticks (cut_finger.LEAD_IN, 0.4 s) into it
        assertEquals(CONTACT[FINGER],dev.sangwoo.celestialfall.client.stand.StandAttack.FINGER_LEAD+8,"the crack on the contact");
        double sound=seconds("star_platinum_finger");
        assertTrue(sound>1.2&&sound<2,"the user's recording, as it is: "+sound+" s");
    }
    @Test void itsFingersReachFiveBlocksAlongANarrowWay(){
        assertTrue(lane(FINGER,5,0,1,.3),"five blocks ahead");assertFalse(lane(FINGER,5.5,0,1,.3),"not further");
        assertTrue(lane(FINGER,4.5,.8,1,.3),"a body's breadth to either side");assertFalse(lane(FINGER,3,1.2,1,.3),"no wider: two fingers");
        assertTrue(lane(1,3,1.2,1,.3),"(a punch's way is wider)");
        assertFalse(lane(FINGER,-1,0,1,.3),"not behind");assertFalse(lane(FINGER,3,0,3.5,.3),"not far above");
        assertTrue(FINGER_REACH>REACH_BIG,"further than any punch");
    }
    @Test void itSlowsAndIncapacitatesWhomItStrikes(){
        assertEquals(.95,FINGER_SLOW,1e-9,"95% slower");
        assertEquals(2,FINGER_SLOW_TICKS/20.0,1e-9,"for two seconds");
        assertEquals(3,FINGER_DISABLE/20.0,1e-9,"and incapacitated for three");
        assertEquals(500+FINGER_COOLDOWN,fingerReady(500),"its cooldown from its end (2.44.0's rule)");
        assertTrue(DAMAGE[FINGER]>0&&DAMAGE[FINGER]<DAMAGE[HEAVY],"it strikes, not as hard as the heavy punch");
    }
    @Test void thePunchGoesWhereItsUserFaces(){
        assertTrue(lane(1,2.5,0,1,.3));assertTrue(lane(1,1,.9,0,.3));
        assertFalse(lane(1,-1,0,1,.3),"not behind");assertFalse(lane(1,4.2,0,1,.3),"not out of reach");
        assertTrue(lane(4,4.2,0,1,.3),"the fourth lunges further");assertFalse(lane(1,2,2,1,.3),"not far to the side");
        assertFalse(lane(1,2,0,3.5,.3),"not far above");
    }

    // ---- Y, the inhale (2.61.0) ----

    @Test void theInhaleCatchesWhoeverIsWithinThreeBlocksInFront(){
        assertTrue(inhaleLane(2,0,1,.3));assertTrue(inhaleLane(INHALE_REACH+.3,0,1,.3),"a body whose near side is three blocks ahead");
        assertFalse(inhaleLane(INHALE_REACH+.5,0,1,.3),"not further");
        assertTrue(inhaleLane(1.5,1.3,0,.3),"a little to either side");assertFalse(inhaleLane(1.5,1.6,0,.3),"not far to the side");
        assertFalse(inhaleLane(-1,0,1,.3),"not behind");assertFalse(inhaleLane(2,0,3.5,.3),"not far above");
        assertEquals(3,INHALE_REACH,1e-9,"three blocks (the user's word)");
    }
    /**
     * User request (2.62.0): "Y는 아무도 안 맞아도 5초동안 지속되고 E처럼 중간에 손 떼면 바로 취소 흡입 무력화는 그냥 95%
     * 슬로우에 아무 스킬 + 대쉬 평타 사용 못하게 그리고 흡입 끝나도 0.5초 정도는 더 무력화", "그리고 흡입은 최대 1명만".
     */
    @Test void itSucksForFiveSecondsAtTheMostAndHoldsOneInFrontOfIt(){
        assertEquals(5,INHALE_SUCK/20.0,1e-9,"five seconds of suction, whether it catches anyone or not");
        assertEquals(.4,INHALE_CATCH/20.0,1e-9,"it sucks once its breath is drawn in");
        assertEquals(INHALE_CATCH+INHALE_SUCK,INHALE_LENGTH);
        assertEquals(.5,INHALE_AFTER/20.0,1e-9,"incapacitated half a second more once it lets go");
        assertEquals(.95,INHALE_HELD_SLOW,1e-9,"whom it holds walk 95% slower");
        assertEquals(700+INHALE_COOLDOWN,inhaleReady(700),"its cooldown from its end (2.44.0's rule)");
        assertEquals(15,INHALE_COOLDOWN/20.0,1e-9);
        assertEquals(.5,INHALE_SLOW,1e-9,"its user walks at half their speed");
        // drawn in: still at both ends, never back, all the way
        assertEquals(0,inhalePull(0),1e-12);assertEquals(1,inhalePull(1),1e-12);
        for(double u=0;u<1;u+=.01)assertTrue(inhalePull(u+.01)>=inhalePull(u));
        assertTrue(inhalePull(.05)<.01&&1-inhalePull(.95)<.02,"still at both ends");
        // held in front of its user wherever they face
        double[] south=inhaleAnchor(0,0,0);assertEquals(0,south[0],1e-9);assertEquals(INHALE_AHEAD,south[1],1e-9,"facing south it holds them to the south");
        double[] east=inhaleAnchor(10,10,-90);
        assertEquals(10+INHALE_AHEAD,east[0],1e-9,"facing east (-90 degrees) it holds them to the east");assertEquals(10,east[1],1e-9);
        assertTrue(INHALE_AHEAD<INHALE_REACH,"whom it catches is drawn in, not pushed out");
    }
    @Test void theInhalesSoundsLastItsHold()throws Exception{
        double suction=seconds("star_platinum_inhale");
        assertTrue(suction>=INHALE_LENGTH/20.0+.2,"the suction runs past the longest hold: "+suction+" s");
        assertTrue(seconds("star_platinum_inhale_catch")<1.2&&seconds("star_platinum_inhale_release")<1.2,"the catch and the air let go are short");
    }
    // ---- G, the beatdown (2.64.0; 2.65.0 its blow slower, the rush through the line, one heavy blow on its "DA!") ----

    @Test void theBeatdownsBlowLandsOnTheRaOfItsOraAndStandsApartFromTheCombo()throws Exception{
        assertEquals(7,BEATDOWN);
        assertTrue(skill(BEATDOWN),"one of the stand's own skills, out of the combo");
        assertEquals(1,next(BEATDOWN),"a punch after it starts the combo again");
        assertEquals(-1,broken(BEATDOWN,100,900),"no combo to break");
        assertFalse(shouts(BEATDOWN),"its ORA is its own, not one of the combo's shouts");
        assertEquals(0,DAMAGE[BEATDOWN],1e-9,"the blow deals nothing itself: its cutscene deals the damage");
        // its "ORA!" starts BEAT_OPENER_SOUND ticks in, its "RA" 0.15 s into it (cut_beatdown.py): on the contact
        assertEquals(CONTACT[BEATDOWN],BEAT_OPENER_SOUND+.15*20,1e-9,"the RA on the contact");
        assertEquals(BEAT_CUT,CONTACT[BEATDOWN],"the cutscene begins as the blow lands");
        // 2.65.0: slower than the heavy punch, so that it can be seen coming ("G시전 모션이 R강펀치보다도 더 느리게")
        assertTrue(CONTACT[BEATDOWN]>CONTACT[HEAVY],"slower than the heavy punch: "+CONTACT[BEATDOWN]+" ticks");
        assertTrue(LENGTH[BEATDOWN]>CONTACT[BEATDOWN]+8,"its fist held out a while once it lands");
        assertTrue(lane(BEATDOWN,4.2,0,1,.3),"it reaches as far as the finisher");assertFalse(lane(BEATDOWN,4.2+.5,0,1,.3));
        assertFalse(lane(BEATDOWN,-1,0,1,.3),"not behind");
        double ora=seconds("star_platinum_beatdown_ora");
        assertTrue(ora>.4&&ora<.9,"the opener's ORA, as the user's recording has it: "+ora+" s");
    }
    @Test void itsCutsceneRunsOnTheRecordingsOwnTime()throws Exception{
        // a tick on for every 20th of a second of the recording, from its start BEAT_OPENER_SOUND ticks in
        int[] order={BEAT_CUT,BEAT_SOUND,BEAT_YARE,BEAT_YARE_END,BEAT_REVEAL,BEAT_GRAB,BEAT_UPPERCUT,BEAT_RUSH_FIRST,BEAT_RUSH_LAST,BEAT_FINAL,BEAT_LAND,BEAT_END};
        for(int i=1;i<order.length;i++)assertTrue(order[i]>order[i-1],"in order: "+order[i-1]+" then "+order[i]);
        assertEquals(BEAT_OPENER_SOUND+1.10*20,BEAT_SOUND,1e-9,"the cutscene's sound starts 1.1 s into the recording");
        assertEquals(BEAT_OPENER_SOUND+2.6*20,BEAT_REVEAL,1e-9,"Star Platinum! 2.6 s in");
        // 2.65.0: the rush runs on through "裁くのは俺の" (8.35 to 10.2 s in), and the one heavy blow lands on the line's
        // "DA!" (10.8 s in: "계속 오라오라 하다가 나의 스탠드다 끝날때 강펀치 한번만 꽂고 날려버려야해")
        assertTrue(BEAT_RUSH_LAST>=BEAT_OPENER_SOUND+10.0*20&&BEAT_RUSH_LAST<BEAT_OPENER_SOUND+10.22*20,"the rush on until \"スタンド\": "+BEAT_RUSH_LAST);
        assertEquals(BEAT_OPENER_SOUND+10.8*20,BEAT_FINAL,1e-9,"the last blow on \"DA!\"");
        // the rest of the recording runs to just before the cutscene's end
        double rest=seconds("star_platinum_beatdown");
        assertTrue(BEAT_SOUND/20.0+rest<=BEAT_END/20.0,"its sound is over by the end: "+(BEAT_SOUND/20.0+rest)+" s");
        assertTrue(BEAT_SOUND/20.0+rest>BEAT_FINAL/20.0+1,"and rings on past the last blow: "+rest+" s");
        assertEquals(20,BEAT_RUSH_EVERY*10,"ten blows a second");
    }
    @Test void itsBlowsDealItsDamageBetweenThemMoreTheLowerTheirHealth(){
        assertEquals(60,beatdownDamage(200,200),1e-6,"60 at full health");
        assertEquals(90,beatdownDamage(100,200),1e-6,"half more at half health");
        assertEquals(120,beatdownDamage(0,200),1e-6,"twice at none");
        assertEquals(60,beatdownDamage(250,200),1e-6,"never less than 60");
        assertTrue(beatdownDamage(20,200)>beatdownDamage(150,200),"the lower their health, the harder it is");
        // every blow's share, all of them together the whole of it
        double sum=0;int blows=0,rush=0;
        for(long age=0;age<=BEAT_END+5;age++){float s=beatShare(age);if(s>0){blows++;sum+=s;if(age>=BEAT_RUSH_FIRST&&age<=BEAT_RUSH_LAST)rush++;}assertEquals(s>0,beatBlow(age));}
        assertEquals(1,sum,1e-5,"the blows deal the whole damage between them");
        assertEquals(BEAT_RUSH_HITS,rush,"the rush's blows");
        assertEquals(1+BEAT_RUSH_HITS+1,blows,"the uppercut, the rush and the one heavy blow");
        assertTrue(BEAT_FINAL_SHARE>BEAT_UPPERCUT_SHARE*3,"the last blow by far the hardest: "+BEAT_FINAL_SHARE);
        assertEquals(0,beatShare(BEAT_LAND),1e-9,"none as they come down: only what is left of it, the fall's");
    }
    @Test void itHoldsThemInFrontOfItsUserAndItsCooldownRunsOnceItIsOver(){
        assertEquals(BEAT_HOLD_MIN,beatHold(.5),1e-9,"room for the stand between");
        assertEquals(3.4,beatHold(3.0),1e-9,"knocked back a little");
        assertEquals(BEAT_HOLD_MAX,beatHold(4.6),1e-9,"not too far");
        assertEquals(1000+BEATDOWN_COOLDOWN,beatdownReady(1000),"from its end (2.44.0's rule)");
        assertEquals(30,BEATDOWN_COOLDOWN/20.0,1e-9,"thirty seconds, as the other cutscene, Kageno's H");
    }
}
