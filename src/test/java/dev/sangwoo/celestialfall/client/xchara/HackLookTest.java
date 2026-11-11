package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.xchara.XCharaRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.xchara.HackLook.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's hack over time (2.115.0, their Q): steady it is all there and gone it is all gone; its coming and its going
 * pick up where the other left off and nothing of them jumps between frames (its flicker a frame or two at a time, as
 * the rule for flashes has it); both are done before the server hears the next Q; the panel opens its width, then its
 * depth, then what is on it, and closes the other way; what is on it is typed a character at a time, moved up a row as
 * each line is done without a jump, its keys lit only as characters are struck.
 */
class HackLookTest {
    static final float LONG=99;

    @Test void steadyItIsAllThereAndGoneItIsAllGone(){
        assertEquals(1,fill(true,LONG));assertEquals(1,rim(true,LONG),1e-6);assertEquals(0,cells(true,LONG),1e-6);
        assertEquals(1,arm(true,LONG));assertEquals(1,wide(true,LONG),1e-6);assertEquals(1,deep(true,LONG),1e-6);
        assertEquals(1,content(true,LONG));assertEquals(0,line(true,LONG),1e-6);
        var on=new Frame(true,LONG,3,7);
        assertTrue(on.shows());assertEquals(1,on.steady(),1e-6);
        for(float t:new float[]{OFF,OFF+.01f,1,LONG}){
            assertEquals(0,fill(false,t),1e-6);assertEquals(0,rim(false,t),1e-6);assertEquals(0,cells(false,t),1e-6);
            assertEquals(0,arm(false,t),1e-6);assertEquals(0,wide(false,t),1e-6);assertEquals(0,content(false,t),1e-6);assertEquals(0,line(false,t),1e-6);
            assertFalse(new Frame(false,t,3,7).shows());
        }
        assertTrue(new Frame(false,OFF-.01f,3,7).shows());
    }
    @Test void itsComingAndItsGoingPickUpWhereTheOtherLeftOff(){
        // going off starts from the steady look
        assertEquals(1,fill(false,0));assertEquals(1,rim(false,0),1e-6);assertEquals(0,cells(false,0),1e-6);assertEquals(1,arm(false,0),1e-6);
        assertEquals(1,wide(false,0),1e-6);assertEquals(1,deep(false,0),1e-6);assertEquals(1,content(false,0),1e-6);assertEquals(0,line(false,0),1e-6);
        // coming on starts from nothing
        assertEquals(0,fill(true,0));assertEquals(0,rim(true,0),1e-6);assertEquals(0,cells(true,0),1e-6);assertEquals(0,arm(true,0),1e-6);
        assertEquals(0,wide(true,0),1e-6);assertEquals(0,content(true,0),1e-6);
        assertEquals(LINE,deep(true,0),1e-6,"a line until it is wide");
    }
    @Test void nothingJumpsBetweenFrames(){
        for(boolean on:new boolean[]{true,false}){
            float[] was=null;
            for(int i=0;i<=480;i++){
                float t=i/960f;
                float[] now={fill(on,t),rim(on,t),cells(on,t),arm(on,t),wide(on,t),deep(on,t),content(on,t),line(on,t)};
                if(was!=null)for(int k=0;k<now.length;k++)assertTrue(Math.abs(now[k]-was[k])<.045f,(on?"on":"off")+" part "+k+" at "+t+": "+was[k]+" -> "+now[k]);
                was=now;
                for(float v:now)assertTrue(v>=-1e-6f&&v<=1.85f,"in range at "+t+": "+v);
            }
        }
        // the knife's white flickers a frame or two at a time (60 a second), never all at once
        for(boolean on:new boolean[]{true,false})for(int i=0;i<30;i++)
            assertTrue(Math.abs(fill(on,(i+1)/60f)-fill(on,i/60f))<=.56f,"white at frame "+i);
    }
    @Test void bothAreDoneBeforeTheNextQIsHeard(){
        float heard=XCharaRules.HACK_RATE/20f;
        assertTrue(ON<=heard&&OFF<=heard,"its coming and going fit between two Qs");
        // by ON everything of its coming has settled (the rim's flare, the blocks of the glitch, the panel, the arm)
        assertEquals(1,fill(true,ON));assertEquals(1,rim(true,ON),.02f);assertEquals(0,cells(true,ON),.01f);assertEquals(1,arm(true,ON),1e-6);
        assertEquals(1,wide(true,ON),1e-6);assertEquals(1,deep(true,ON),1e-6);assertEquals(1,content(true,ON),1e-6);assertEquals(0,line(true,ON),1e-6);
    }
    @Test void theRimFlaresAsItComesOnAndThePurpleBlocksBreakOverTheKnife(){
        float peak=0,blocks=0;
        for(int i=0;i<=340;i++){peak=Math.max(peak,rim(true,i/1000f));blocks=Math.max(blocks,cells(true,i/1000f));}
        assertTrue(peak>1.5f&&peak<1.85f,"the rim flares: "+peak);
        assertTrue(blocks>.45f&&blocks<=.55f,"about half the knife under blocks at its most: "+blocks);
        float gone=0;for(int i=0;i<=300;i++)gone=Math.max(gone,cells(false,i/1000f));
        assertTrue(gone>.45f,"and as it goes: "+gone);
    }
    @Test void thePanelOpensItsWidthThenItsDepthThenWhatIsOnItAndClosesTheOtherWay(){
        assertTrue(wide(true,.07f)>.5f&&deep(true,.07f)<LINE+.01f,"wide while still a line");
        for(int i=0;i<=340;i++){
            float t=i/1000f;
            if(content(true,t)>.001f)assertTrue(wide(true,t)>.99f&&deep(true,t)>.6f,"what is on it shows on an open panel at "+t);
            if(deep(true,t)>LINE+.02f)assertTrue(wide(true,t)>.9f,"its depth opens once it is wide at "+t);
        }
        float past=0;for(int i=0;i<=340;i++)past=Math.max(past,Math.max(wide(true,i/1000f),deep(true,i/1000f)));
        assertTrue(past>1.01f&&past<1.06f,"a touch past and back: "+past);
        // closing: what is on it first, then its depth, then its width
        assertTrue(content(false,.05f)<.01f&&deep(false,.05f)>.5f);
        assertTrue(deep(false,.13f)<LINE+.01f&&wide(false,.12f)>.99f,"a line before it closes");
        assertEquals(0,wide(false,.24f),1e-6);
        assertTrue(line(false,.16f)>.9f,"the line of light as it closes");
        assertTrue(line(true,.03f)>.9f,"and as it opens");
    }
    @Test void theOtherArmComesUpAndGoesDownSmoothly(){
        float was=0;
        for(int i=0;i<=340;i++){float a=arm(true,i/1000f);assertTrue(a>=was-1e-6f,"up only");was=a;}
        assertEquals(1,was,1e-6);
        for(int i=0;i<=300;i++){float a=arm(false,i/1000f);assertTrue(a<=was+1e-6f,"down only");was=a;}
        assertEquals(0,was,1e-6);
    }

    @Test void itsWritingIsWordsOfAFewCharactersWithASpaceBetween(){
        for(int seed:new int[]{0,7,-12345}){
            for(int n=-3;n<60;n++){
                int len=length(n,seed);
                assertTrue(len>=12&&len<=CHARS,"length "+len);
                int run=0,spaces=0;
                assertTrue(glyph(n,0,seed),"a line starts with a word");
                for(int c=0;c<len;c++){
                    if(glyph(n,c,seed)){run++;spaces=0;assertTrue(run<=7,"a word of seven at most");}
                    else{assertTrue(run>=2,"a word of two at least before a space");run=0;spaces++;assertEquals(1,spaces,"one space at a time");}
                }
                assertFalse(glyph(n,len,seed));assertFalse(glyph(n,-1,seed));
            }
        }
    }
    @Test void aLineIsTypedThenHeldThenMovedUpWithoutAJump(){
        int seed=42;
        for(int n=0;n<8;n++){
            float start=n*LINE_EVERY;
            assertEquals(n,lineAt(start+.001f));
            assertEquals(0,typed(start+.0001f,seed),.05f,"a new line starts empty");
            assertEquals(length(n,seed),typed(start+TYPE,seed),.01f,"typed in full by TYPE");
            float was=0;
            for(int i=0;i<=200;i++){
                float t=start+i*(LINE_EVERY-.0005f)/200f,now=typed(t,seed);
                assertTrue(now>=was-1e-4f,"typed forward");was=now;
                assertTrue(scrolled(t)>=0&&scrolled(t)<=1);
                if(t-start<LINE_EVERY-SCROLL)assertEquals(0,scrolled(t),1e-6,"still while it types");
            }
            assertEquals(1,scrolled(start+LINE_EVERY-.0002f),.001f,"moved up a whole row as the next line begins");
            // a row moved up by one with the old line is where the next line's row above the last is: nothing jumps
            assertEquals(0,scrolled(start+LINE_EVERY+.0002f),.001f);
        }
    }
    @Test void aKeyIsLitOnlyAsItsCharacterIsStruck(){
        int seed=5;int keys=KEYS_ACROSS*KEYS_DOWN,struck=0;
        for(int n=0;n<6;n++){
            int len=length(n,seed);float per=TYPE/len;
            for(int c=0;c<len;c++){
                int key=keyOf(n,c,seed);assertTrue(key>=0&&key<keys);
                float at=n*LINE_EVERY+c*per;
                if(glyph(n,c,seed)){assertTrue(keyLit(key,at+.03f,seed)>.5f,"lit just after it is struck");struck++;}
            }
            // long after the last character: all dark
            for(int k=0;k<keys;k++){
                assertEquals(0,keyLit(k,n*LINE_EVERY+TYPE+KEY_LIT+.02f,seed),1e-6,"dark once the line is done");
                for(int i=0;i<140;i++){float v=keyLit(k,n*LINE_EVERY+i*.01f,seed);assertTrue(v>=0&&v<=1);}
            }
        }
        assertTrue(struck>40);
    }
}
