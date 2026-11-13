package dev.sangwoo.celestialfall.client.xchara;

import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.PullRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's pull as drawn (2.127.0, the user's video, read frame by frame and confirmed: "맞음, 이대로"): the one
 * pulled flickers between a flat purple silhouette and purple bars for half a second, in the video's own order; the
 * bars thin out to nothing; its sound's burst is on the pull; its user presses their panel as it is called.
 */
class PullClientTest {
    @Test void theOnePulledFlickersPurpleAndBarsAsTheVideoHasIt(){
        // the video's frames of a sixtieth after the pull: themselves 0-1, purple 2-3, bars 4, purple 5, bars 6, purple 7-10, bars from 11
        boolean[] purple={false,false,true,true,false,true,false,true,true,true,true,false,false,false};
        for(int k=0;k<purple.length;k++)assertEquals(purple[k],PullClient.solid((k+.5f)/60),"frame "+k);
        for(float s=.2f;s<3;s+=.01f)assertFalse(PullClient.solid(s),"no purple after the first fifth of a second: "+s);
        assertFalse(PullClient.solid(0));
        // the bars: none while they are themselves, all of them then, thinning out to nothing by the half second
        assertEquals(0,PullClient.bars(0),1e-6);assertEquals(0,PullClient.bars(.03f),1e-6);
        assertEquals(1,PullClient.bars(.07f),1e-6);assertEquals(1,PullClient.bars(.2f),1e-6);
        for(float s=.2f;s<.5f;s+=.01f)assertTrue(PullClient.bars(s+.01f)<=PullClient.bars(s)+1e-6);
        assertEquals(0,PullClient.bars(GLITCH/20f),1e-6);assertEquals(0,PullClient.bars(1),1e-6);
        // and it changes every other frame of sixty, as the video's
        assertEquals(30,PullClient.RATE,1e-6);
    }
    @Test void theBarsAreShortDashesOnTheBodyManyAtFirstAndFewerAsItGoes(){
        int seed=4711;double height=1.8,width=.6;
        int early=0,late=0,steps=0,low=0,all=0;
        for(float s=1/30f+.001f;s<GLITCH/20f;s+=1/60f){
            int here=0;
            for(int j=0;j<PullClient.DASHES;j++){
                double[] b=PullClient.dash(j,s,seed,height,width);if(b==null)continue;here++;
                assertTrue(b[0]>0&&b[0]<1,"on the body, between the feet and the top of the head");
                assertTrue(Math.abs(b[1])<=width*.66,"across the body, some a little past its side: "+b[1]);
                // short and thin: dashes, not bars from side to side
                assertTrue(b[2]<=width*.35&&b[2]>=width*.08&&b[3]<=.035,"a short dash");
                if(b[0]<.68)low++;all++;
                int c=(int)b[4];assertTrue(c==PullClient.BAR||c==GlitchFx.PURPLE||c==GlitchFx.LILAC,"purple");
            }
            if(s<.2f)early+=here;else if(s>.35f)late+=here;
            steps++;
        }
        assertTrue(early>late*2,"many at first, fewer as it goes: "+early+" "+late);
        assertTrue(early/10.0>12,"a body covered in them at first (the video: a dozen and more): "+early/10.0);
        assertTrue(low>all*.7,"most of them on the trunk and the legs, as the video's: "+low+" of "+all);
        for(int j=0;j<PullClient.DASHES;j++){assertNull(PullClient.dash(j,0,seed,height,width));assertNull(PullClient.dash(j,.6f,seed,height,width));}
        // the same within a thirtieth of a second, other the next; one's are not another's
        assertArrayEquals(PullClient.dash(3,.1001f,seed,height,width),PullClient.dash(3,.13f,seed,height,width));
        boolean other=false;for(int j=0;j<PullClient.DASHES&&!other;j++)if(!java.util.Arrays.equals(PullClient.dash(j,.1f,1,height,width),PullClient.dash(j,.1f,2,height,width)))other=true;
        assertTrue(other);
    }
    @Test void theSilhouetteIsAVividPurpleALittleBiggerThanTheBody(){
        int r=PullClient.FLAT>>16&255,g=PullClient.FLAT>>8&255,b=PullClient.FLAT&255;
        assertTrue(b>=230&&g<=80&&r>=90&&r<=160,"purple: "+Integer.toHexString(PullClient.FLAT));
        assertTrue(PullClient.SWELL>1&&PullClient.SWELL<1.08f);
        // the flash of the pull: at its fullest as they are put there, gone in a fifth of a second, never stepping in
        assertEquals(1,PullClient.flash(0),1e-6);assertEquals(0,PullClient.flash(-.1f),1e-6);assertEquals(0,PullClient.flash(.2f),1e-6);
        for(float s=0;s<.3f;s+=.005f)assertTrue(PullClient.flash(s+.005f)<=PullClient.flash(s)+1e-6);
    }
    @Test void itsSoundIsOnThePullAndTheOnePulledIsDrawnThereAtOnce(){
        // the burst's file starts on its onset (extract_pull.py) and is begun as this client hears of the pull; not if that is long after
        assertTrue(PullClient.SOUND_LATE>=2&&PullClient.SOUND_LATE<=6);
        // drawn where the server put them for as long as this client's tracked place may take to catch up, and no longer
        assertTrue(PullClient.SETTLE>=4&&PullClient.SETTLE<=GLITCH);assertTrue(PullClient.THERE<=.5);
    }
    @Test void itsUserPressesThePanelAsItIsCalledAndItLightsAgainAsItPulls(){
        float none=Float.NaN,pull=PULL/20f;
        float[] start=PullClient.pressAt(0,none);
        assertEquals(0,start[0],1e-6);assertEquals(0,start[1],1e-6);assertEquals(0,start[2],1e-6);
        assertEquals(1,PullClient.pressAt(.1f,none)[0],1e-4,"the arm up by a tenth of a second");
        assertEquals(1,PullClient.pressAt(.11f,none)[1],.05,"and the hand down on it");
        assertTrue(PullClient.pressAt(.12f,none)[2]>.8f,"the panel lit as it is pressed");
        assertTrue(PullClient.pressAt(pull+.03f,none)[2]>.7f,"lit again as it pulls");
        assertEquals(0,PullClient.pressAt(.55f,none)[0],1e-4,"the arm back at their side after");
        assertEquals(0,PullClient.pressAt(pull+.4f,none)[2],1e-4);
        float[] was=start;
        for(float t=1/60f;t<pull+.5f;t+=1/60f){
            float[] p=PullClient.pressAt(t,none);
            assertTrue(Math.abs(p[0]-was[0])<.3,"the arm stepped at "+t);assertTrue(Math.abs(p[1]-was[1])<.45,"the hand stepped at "+t);
            was=p;
        }
        // blocked or called off: the arm drops, the panel is not lit
        assertEquals(0,PullClient.pressAt(.2f+.15f,.2f)[0],1e-4);assertEquals(0,PullClient.pressAt(.25f,.2f)[2],1e-6);
    }
}
