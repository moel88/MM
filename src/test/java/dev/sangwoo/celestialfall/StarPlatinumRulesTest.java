package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Star Platinum's basic attack (2.54.0): four punches, the fourth throwing. 2.56.0: quicker, and only the hook and the
 * finisher shout, each on the "RA" of its own from the user's recording.
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
    @Test void thePunchGoesWhereItsUserFaces(){
        assertTrue(lane(1,2.5,0,1,.3));assertTrue(lane(1,1,.9,0,.3));
        assertFalse(lane(1,-1,0,1,.3),"not behind");assertFalse(lane(1,4.2,0,1,.3),"not out of reach");
        assertTrue(lane(4,4.2,0,1,.3),"the fourth lunges further");assertFalse(lane(1,2,2,1,.3),"not far to the side");
        assertFalse(lane(1,2,0,3.5,.3),"not far above");
    }
}
