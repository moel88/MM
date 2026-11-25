package dev.sangwoo.celestialfall.client.xchara;

import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.BlinkRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** X Chara's Z, the teleport, as a client draws it (2.138.0): the flicker, the bars, what is left where they stood. */
class BlinkClientTest {
    @Test void theBodyFlickersPurpleFromThePressByTheVideosFrames(){
        // before the move: purple and themselves by turns, most of the frames purple
        int purple=0;
        for(int k=0;k<BlinkClient.BEFORE.length;k++){
            assertEquals(BlinkClient.BEFORE[k]==1,BlinkClient.solid((k+.5f)/60,-1),"frame "+k);
            purple+=BlinkClient.BEFORE[k];
        }
        assertTrue(BlinkClient.solid(.001f,-1),"purple at once");
        assertTrue(purple>=6&&purple<BlinkClient.BEFORE.length,"it flickers: "+purple);
        // (the move late: the flicker goes round again, never frozen on one or the other)
        boolean on=false,off=false;for(float s=GO/20f;s<GO/20f+.2f;s+=1/120f){if(BlinkClient.solid(s,-1))on=true;else off=true;}
        assertTrue(on&&off);
        assertFalse(BlinkClient.solid(-.1f,-1));
    }
    @Test void afterTheMoveItsPurpleIsGoneInAFifthOfASecond(){
        for(int k=0;k<BlinkClient.ARRIVED.length;k++)assertEquals(BlinkClient.ARRIVED[k]==1,BlinkClient.solid(1,(k+.5f)/60),"frame "+k);
        assertTrue(BlinkClient.solid(.2f,0),"purple as they come");
        assertTrue(BlinkClient.ARRIVED.length/60f<=.2f);
        for(float s=.2f;s<3;s+=.01f)assertFalse(BlinkClient.solid(1,s),"no purple past a fifth of a second: "+s);
    }
    @Test void theBarsAcrossThemThinOutToNothing(){
        assertEquals(1,BlinkClient.bars(0,-1),1e-6);assertEquals(1,BlinkClient.bars(.15f,-1),1e-6);assertEquals(0,BlinkClient.bars(-.1f,-1),1e-6);
        assertEquals(1,BlinkClient.bars(.3f,.1f),1e-6);assertEquals(1,BlinkClient.bars(.4f,.2f),1e-6);
        for(float s=.2f;s<.5f;s+=.01f)assertTrue(BlinkClient.bars(1,s+.01f)<=BlinkClient.bars(1,s)+1e-6);
        assertEquals(0,BlinkClient.bars(1,AFTER/20f),1e-6);assertEquals(0,BlinkClient.bars(2,1),1e-6);
    }
    @Test void theBarsAreShortDashesOnTheBody(){
        double width=.6;int seed=77,shown=0;
        for(float s=.01f;s<.2f;s+=.011f){
            int here=0;
            for(int j=0;j<BlinkClient.DASHES;j++){
                double[] b=BlinkClient.dash(j,s,1,seed,width);if(b==null)continue;here++;
                assertTrue(b[0]>=.05&&b[0]<=.95,"on the body: "+b[0]);
                assertTrue(Math.abs(b[1])<=width*.65+1e-9);
                assertTrue(b[2]>0&&b[2]<=width*.36&&b[3]>0&&b[3]<.04,"short and thin");
                int c=(int)b[4];assertTrue(c==BlinkClient.BAR||c==GlitchFx.PURPLE||c==GlitchFx.LILAC,"purple");
            }
            assertTrue(here>=5&&here<BlinkClient.DASHES,"some, not all: "+here);shown+=here;
        }
        assertTrue(shown>0);
        for(int j=0;j<BlinkClient.DASHES;j++)assertNull(BlinkClient.dash(j,.1f,0,seed,width));
        // the same within a thirtieth of a second, other from one cast to the next
        assertArrayEquals(BlinkClient.dash(3,.1001f,1,seed,width),BlinkClient.dash(3,.13f,1,seed,width));
        boolean other=false;for(int j=0;j<BlinkClient.DASHES&&!other;j++)if(!java.util.Arrays.equals(BlinkClient.dash(j,.1f,1,1,width),BlinkClient.dash(j,.1f,1,2,width)))other=true;
        assertTrue(other);
    }
    @Test void aKnotOfBarsIsLeftWhereTheyStoodHalfASecond(){
        assertEquals(1,BlinkClient.left(0),1e-6);assertEquals(0,BlinkClient.left(-.01f),1e-6);assertEquals(0,BlinkClient.left(LEFT/20f),1e-6);
        for(float s=0;s<.6f;s+=.01f)assertTrue(BlinkClient.left(s+.01f)<=BlinkClient.left(s)+1e-6);
        double width=.6;int early=0,late=0;
        for(int j=0;j<BlinkClient.LEFT_DASHES;j++){
            double[] b=BlinkClient.leftDash(j,.02f,9,width);
            if(b!=null){
                early++;
                assertTrue(b[0]>=.5&&b[0]<=.92,"between the waist and the top of the head: "+b[0]);
                assertTrue(Math.abs(b[1])<=width*.55+1e-9,"no wider than they were");
            }
            if(BlinkClient.leftDash(j,.42f,9,width)!=null)late++;
            assertNull(BlinkClient.leftDash(j,.5f,9,width));assertNull(BlinkClient.leftDash(j,-.1f,9,width));
        }
        assertTrue(early>=10,"a knot of them: "+early);assertTrue(late<early,"thinning out: "+late+" of "+early);
    }
    @Test void thePurpleAboutThemComesAtOnceAndGoesWithTheFlicker(){
        assertEquals(0,BlinkClient.aura(-.1f,-1,-1),1e-6);
        assertTrue(BlinkClient.aura(.02f,-1,-1)>.3f,"on within two frames");assertEquals(1,BlinkClient.aura(.1f,-1,-1),1e-6);
        assertEquals(1,BlinkClient.aura(.3f,.1f,-1),1e-6);assertEquals(0,BlinkClient.aura(.7f,AFTER/20f,-1),1e-6);
        // called off: gone in a tenth of a second
        assertEquals(0,BlinkClient.aura(.3f,-1,.1f),1e-6);assertTrue(BlinkClient.aura(.3f,-1,.03f)>0);
        for(float s=0;s<.7f;s+=.005f)assertTrue(Math.abs(BlinkClient.aura(s+.005f,s-.2f+.005f,-1)-BlinkClient.aura(s,s-.2f,-1))<.3f,"no jump at "+s);
    }
    @Test void itsLookIsThePullsAndItsSoundComesWithTheMove(){
        assertEquals(PullClient.FLAT,BlinkClient.FLAT);assertEquals(PullClient.SWELL,BlinkClient.SWELL,1e-9);assertEquals(PullClient.BAR,BlinkClient.BAR);
        assertEquals(1,BlinkClient.flash(0),1e-6);assertEquals(0,BlinkClient.flash(-.1f),1e-6);assertEquals(0,BlinkClient.flash(.2f),1e-6);
        assertTrue(BlinkClient.SOUND_LATE>=2&&BlinkClient.SOUND_LATE<=6);
        assertTrue(BlinkClient.SETTLE>=4&&BlinkClient.SETTLE<=AFTER);assertTrue(BlinkClient.THERE<=.5);
    }
}
