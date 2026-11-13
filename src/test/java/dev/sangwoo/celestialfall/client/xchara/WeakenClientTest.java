package dev.sangwoo.celestialfall.client.xchara;

import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.WeakenRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's weakening as drawn (2.126.0, the user: "머리 위에 보라색 글씨로 공격력 방어력이 25% 감소한다. 라고 뜨게
 * 해주고 몸에도 보라색 아래 방향 화살표 아우라가 방출되게"): the words are the user's; the arrows come out round the body
 * and only ever sink, some always on their way, none stepping from one frame to the next; it shows all its time and
 * goes as its time runs out; its user presses their panel as it is called.
 */
class WeakenClientTest {
    @Test void theWordsAreTheUsers(){
        assertEquals("공격력 방어력이 25% 감소한다.",WeakenClient.WORDS);
        // (the user: "글자를 머리 바로 위에 나오게 바꾸고 글씨도 조금만 더 작게") just over the head: clear of the head under them
        // and of a name over them (the game's label is from 0.275 to 0.525 blocks over the head)
        double tall=WeakenClient.EM*.75;
        assertTrue(WeakenClient.OVER-tall/2>=.12,"a little clear of the head (the user: \"머리랑 글씨랑 겹쳐있잖아 조금은 띄워\")");assertTrue(WeakenClient.OVER+tall/2<.275,"under the name");
        assertTrue(WeakenClient.EM>=.15&&WeakenClient.EM<=.2,"small (the user: \"글씨를 더 작게해\"), still to be read");
        // the "!!!" stands well over them
        assertTrue(WeakenClient.BANGS-.4>WeakenClient.OVER+tall/2);
        assertEquals(1,WeakenClient.grown(3),1e-6);assertEquals(1,WeakenClient.grown(10),1e-6);assertTrue(WeakenClient.grown(14)>1.3);assertEquals(1.5f,WeakenClient.grown(60),1e-6);
        for(double far=1;far<60;far+=.25)assertTrue(WeakenClient.grown(far+.25)>=WeakenClient.grown(far));
    }
    @Test void theArrowsComeOutRoundTheBodyAndOnlySink(){
        int seed=4711;
        for(int k=0;k<WeakenClient.ARROWS;k++){
            float[] was=null;
            for(float t=0;t<8;t+=1/240f){
                float[] a=WeakenClient.arrow(k,t,seed);
                assertTrue(a[1]>=1.2f&&a[1]<=1.85f,"out from the body, not in it: "+a[1]);
                assertTrue(a[2]<=WeakenClient.FROM+1e-4&&a[2]>=WeakenClient.TO-1e-4,"between over the head and the shins: "+a[2]);
                assertTrue(a[3]>=0&&a[3]<=1);assertTrue(a[4]>.7f&&a[4]<1.3f);
                if(was!=null){
                    boolean same=was[0]==a[0];
                    if(same){
                        // on its way down: never up, and no jump
                        assertTrue(a[2]<=was[2]+1e-5,"arrow "+k+" rose at "+t);
                        assertTrue(was[2]-a[2]<.02,"arrow "+k+" jumped at "+t);
                        assertTrue(Math.abs(a[3]-was[3])<.06,"arrow "+k+" blinked at "+t);
                    }else{
                        // gone, and come again somewhere else: unseen as it does
                        assertTrue(was[3]<.02&&a[3]<.02,"arrow "+k+" moved while seen at "+t+": "+was[3]+" "+a[3]);
                    }
                }
                was=a;
            }
        }
    }
    @Test void theArrowsAreSmallAndVeryMany(){
        // (the user: "화살표도 작게 만들도 나오는 빈도를 엄청 많게")
        assertTrue(WeakenClient.ARROW<=.22,"small");
        assertTrue(WeakenClient.ARROWS/WeakenClient.SINK>=40,"some forty a second come out");
    }
    @Test void someArrowsAreAlwaysOnTheirWayAndTheyAreSpreadRoundTheBody(){
        int seed=99;
        for(float t=0;t<12;t+=.03f){
            int seen=0;for(int k=0;k<WeakenClient.ARROWS;k++)if(WeakenClient.arrow(k,t,seed)[3]>.5f)seen++;
            assertTrue(seen>=20,"only "+seen+" arrows at "+t);
        }
        // over time they come out all round, not on one side
        boolean[] side=new boolean[8];
        for(float t=0;t<12;t+=.1f)for(int k=0;k<WeakenClient.ARROWS;k++)side[(int)(WeakenClient.arrow(k,t,seed)[0]/(Math.PI*2)*8)%8]=true;
        for(boolean s:side)assertTrue(s);
        // one's arrows are not another's
        assertNotEquals(WeakenClient.arrow(0,1,1)[0],WeakenClient.arrow(0,1,2)[0]);
    }
    @Test void itShowsAllItsTimeAndGoesAsItRunsOut(){
        float all=LASTS/20f;
        assertEquals(0,WeakenClient.shows(0,all),1e-6);
        assertEquals(1,WeakenClient.shows(WeakenClient.IN,all-WeakenClient.IN),1e-6,"all there within a fifth of a second");
        // the user: the words stay all its 20 seconds
        for(float on=WeakenClient.IN;on<all-WeakenClient.OUT;on+=.1f)assertEquals(1,WeakenClient.shows(on,all-on),1e-6,"there at "+on);
        assertTrue(WeakenClient.shows(all-.2f,.2f)<1);assertEquals(0,WeakenClient.shows(all,0),1e-6);assertEquals(0,WeakenClient.shows(all+1,-1),1e-6);
        for(float left=WeakenClient.OUT;left>0;left-=.01f)assertTrue(WeakenClient.shows(5,left)>=WeakenClient.shows(5,left-.01f)-1e-6);
        // the words pop in from bigger as it takes; one this client only came near has them at their size
        assertTrue(WeakenClient.pop(0,true)>1.3f);assertEquals(1,WeakenClient.pop(.2f,true),1e-6);assertEquals(1,WeakenClient.pop(0,false),1e-6);
        for(float on=0;on<.3f;on+=.005f)assertTrue(WeakenClient.pop(on+.005f,true)<=WeakenClient.pop(on,true)+1e-6);
    }
    @Test void itsUserPressesThePanelAsItIsCalledAndItLightsAgainAsItTakes(){
        float none=Float.NaN,takes=APPLY/20f;
        float[] start=WeakenClient.pressAt(0,none);
        assertEquals(0,start[0],1e-6);assertEquals(0,start[1],1e-6);assertEquals(0,start[2],1e-6);
        assertEquals(1,WeakenClient.pressAt(.1f,none)[0],1e-4,"the arm up by a tenth of a second");
        assertEquals(1,WeakenClient.pressAt(.11f,none)[1],.05,"and the hand down on it");
        assertTrue(WeakenClient.pressAt(.12f,none)[2]>.8f,"the panel lit as it is pressed");
        assertEquals(0,WeakenClient.pressAt(.6f,none)[0],1e-4,"the arm back at their side before it takes");
        assertTrue(WeakenClient.pressAt(takes+.03f,none)[2]>.7f,"lit again as it takes");
        assertEquals(0,WeakenClient.pressAt(takes+.4f,none)[2],1e-4);
        // the arm and the hand step nowhere from one frame to the next (the press itself is quick: 0.06 s, as the double's)
        float[] was=start;
        for(float t=1/60f;t<takes+.5f;t+=1/60f){
            float[] p=WeakenClient.pressAt(t,none);
            assertTrue(Math.abs(p[0]-was[0])<.3,"the arm stepped at "+t);assertTrue(Math.abs(p[1]-was[1])<.45,"the hand stepped at "+t);
            was=p;
        }
        // blocked or called off: the arm drops, the panel is not lit
        assertEquals(0,WeakenClient.pressAt(.2f+.15f,.2f)[0],1e-4);assertEquals(0,WeakenClient.pressAt(.25f,.2f)[2],1e-6);
    }
}
