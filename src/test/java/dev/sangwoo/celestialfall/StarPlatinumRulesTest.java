package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Star Platinum's basic attack (2.54.0): four punches on the beat of the user's recording, the fourth throwing. */
class StarPlatinumRulesTest {
    /** Where each shout's "RA" begins in the user's recording (seconds; tools/stand/cut_ora.py). */
    static final double[] RA={.250,1.030,1.930,3.145};
    /** Each clip has its RA this far in (cut_ora.py RA_AT) and starts StandAttack.LEAD (1) tick into its punch. */
    static final double[] RA_AT={.25,.25,.40,.45};

    @Test void eachShoutsRaFallsOnItsPunch(){
        for(int k=1;k<=4;k++)assertEquals(RA_AT[k-1],(CONTACT[k]-1)/20.0,1e-9,"punch "+k);
        for(int k=1;k<=4;k++)assertNotNull(getClass().getResourceAsStream("/assets/celestialfall/sounds/star_platinum_ora_"+k+".ogg"),"shout "+k);
    }
    @Test void mashedThePunchesKeepTheRecordingsBeat(){
        long start=0;double first=CONTACT[1]/20.0;
        for(int k=1;k<=4;k++){
            double contact=(start+CONTACT[k])/20.0;
            assertEquals(RA[k-1]-RA[0],contact-first,.025,"punch "+k+" lands with its RA");
            if(k<4)start=nextAt(k,start);
        }
        // no shout runs into the next: each clip starts a tick into its punch and is this long (cut_ora.py prints it)
        double[] length={.72,.72,1.15};
        for(int k=1;k<4;k++)assertTrue(STEP[k]/20.0>=length[k-1]-1e-9,"shout "+k+" is over before the next starts");
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
