package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Star Platinum's basic attack (2.54.0): four punches, the fourth throwing. 2.56.0: quicker, and only the hook and the
 * finisher shout, each on the "RA" of its own from the user's recording. 2.57.0: its E, the rush. 2.59.0: its R, the heavy
 * punch.
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
    @Test void eachPunchHoldsItsTargetForTheNext(){
        for(int k=1;k<4;k++)assertTrue(hold(k)>=STEP[k]-CONTACT[k]+CONTACT[k+1],"punch "+k+" holds them until the next lands");
        assertEquals(0,hold(4),"the fourth throws them");
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
        assertEquals(0,hold(HEAVY),"it throws whom it strikes, it does not hold them");
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
    @Test void thePunchGoesWhereItsUserFaces(){
        assertTrue(lane(1,2.5,0,1,.3));assertTrue(lane(1,1,.9,0,.3));
        assertFalse(lane(1,-1,0,1,.3),"not behind");assertFalse(lane(1,4.2,0,1,.3),"not out of reach");
        assertTrue(lane(4,4.2,0,1,.3),"the fourth lunges further");assertFalse(lane(1,2,2,1,.3),"not far to the side");
        assertFalse(lane(1,2,0,3.5,.3),"not far above");
    }
}
