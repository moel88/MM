package dev.sangwoo.celestialfall.client.xchara;

import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.WeakenRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's weakening as drawn (2.126.0, the user: "머리 위에 보라색 글씨로 공격력 방어력이 25% 감소한다. 라고 뜨게
 * 해주고 몸에도 보라색 … 아우라가 방출되게"; 2.126.1: "아니다 그냥 글리치 효과가 생기게", "몸과 글씨 둘 다";
 * "글자 좀만 낮춰 그냥 저 가만히 있는 더미 이름표랑 겹치게 해 그리고 글자
 * 폰트를 도트 처럼 만들어주고 더 보라색으로"): the words are the user's, where a name is and in the game's pixel
 * typeface, a strong purple; the body glitches all its time (bars torn across it, pixels flickering on it), more in
 * bursts and most as it takes; the words glitch in bursts and stand to be read between; it shows all its time and goes as its time runs out; its user presses their
 * panel as it is called.
 */
class WeakenClientTest {
    @Test void theWordsAreTheUsersWhereANameIsAndPurple(){
        assertEquals("공격력 방어력이 25% 감소한다.",WeakenClient.WORDS);
        // (the user: "그냥 저 가만히 있는 더미 이름표랑 겹치게 해") where the game draws a name: from 0.275 to 0.525 over the head
        assertTrue(WeakenClient.OVER>=.3&&WeakenClient.OVER<=.5,"where a name is: "+WeakenClient.OVER);
        // clear of the head under them (the user before: "머리랑 글씨랑 겹쳐있잖아"), the "!!!" well over them
        double tall=WeakenClient.DOT*8;
        assertTrue(WeakenClient.OVER-tall/2>=.25,"clear of the head");assertTrue(WeakenClient.BANGS-.4>WeakenClient.OVER+tall/2);
        // (the user: "글자 폰트를 도트 처럼") the game's pixel typeface, its pixels smaller than a name's (the user: "글씨를 더 작게해") but to be read
        assertTrue(WeakenClient.DOT<.025&&WeakenClient.DOT>=.012);
        // (the user: "더 보라색으로") a strong purple: far more blue than green, red between; a dark line round it
        int r=WeakenClient.WORD>>16&255,g=WeakenClient.WORD>>8&255,b=WeakenClient.WORD&255;
        assertTrue(b>=230&&g<=70&&r>=120&&r<=190,"purple: "+Integer.toHexString(WeakenClient.WORD));
        int line=WeakenClient.WORD_LINE;assertTrue((line>>16&255)+(line>>8&255)+(line&255)<200&&(line&255)>(line>>16&255)&&(line>>16&255)>(line>>8&255),"a deep purple line");
        assertEquals(1,WeakenClient.grown(3),1e-6);assertEquals(1,WeakenClient.grown(10),1e-6);assertTrue(WeakenClient.grown(14)>1.3);assertEquals(1.5f,WeakenClient.grown(60),1e-6);
        for(double far=1;far<60;far+=.25)assertTrue(WeakenClient.grown(far+.25)>=WeakenClient.grown(far));
    }
    @Test void theBodyGlitchesAllItsTimeMoreInBurstsAndMostAsItTakes(){
        int seed=4711;double height=1.8,width=.6;
        // all its time something of it shows nearly always, and never so much that the body is covered
        int steps=0,bare=0,bars=0,pixels=0,most=0;
        for(float t=.6f;t<20;t+=1/WeakenClient.STEP_RATE/3){
            float often=WeakenClient.often(t,seed,true);int here=0,dots=0;
            for(int j=0;j<WeakenClient.BARS;j++){
                double[] b=WeakenClient.tear(j,t,seed,height,width,often);if(b==null)continue;here++;
                assertTrue(b[0]>0&&b[0]<1,"on the body, between the feet and the top of the head");
                assertTrue(Math.abs(b[1])+b[2]<=width*1.35,"across the body, not far off it: "+b[1]+" "+b[2]);
                assertTrue(b[2]>=width*.3&&b[3]>=.02&&b[3]<=.061,"a thin bar");
                int c=(int)b[4];assertTrue(c==GlitchFx.PURPLE||c==GlitchFx.DEEP||c==GlitchFx.LILAC||c==GlitchFx.INK,"purple (or a dead black)");
            }
            for(int j=0;j<WeakenClient.PIXELS;j++){
                double[] b=WeakenClient.pixel(j,t,seed,height,width,often);if(b==null)continue;dots++;
                assertTrue(b[0]>0&&b[0]<1);assertTrue(Math.abs(b[1])<=width*.5+.3,"on the body: "+b[1]);
                assertTrue(b[2]<=WeakenClient.PIXEL+1e-9,"small");
                // on a grid, as the cells of a screen
                assertEquals(0,Math.abs(b[1]/WeakenClient.PIXEL-Math.round(b[1]/WeakenClient.PIXEL)),1e-6);
            }
            steps++;if(here+dots==0)bare++;bars+=here;pixels+=dots;most=Math.max(most,here);
        }
        assertTrue(bare<steps*.04,"the glitch is there all its time: bare "+bare+" of "+steps);
        double b=bars/(double)steps,p=pixels/(double)steps;
        assertTrue(b>1.5&&b<3.5,"a few bars at a time: "+b);assertTrue(p>3&&p<9,"a few pixels at a time: "+p);
        // it changes many times a second (a glitch), and holds between: the same within a step
        assertTrue(WeakenClient.STEP_RATE>=10);
        float at=5.01f/WeakenClient.STEP_RATE*WeakenClient.STEP_RATE;int n=WeakenClient.step(at);
        assertEquals(n,WeakenClient.step(at+.2f/WeakenClient.STEP_RATE));assertEquals(n+1,WeakenClient.step(at+1/WeakenClient.STEP_RATE));
        int changed=0;for(int k=0;k<200;k++){
            String one="",next="";
            for(int j=0;j<WeakenClient.BARS;j++){one+=java.util.Arrays.toString(WeakenClient.tear(j,k/WeakenClient.STEP_RATE+.01f,seed,height,width,1));next+=java.util.Arrays.toString(WeakenClient.tear(j,(k+1)/WeakenClient.STEP_RATE+.01f,seed,height,width,1));}
            if(!one.equals(next))changed++;
        }
        assertTrue(changed>170,"other every step: "+changed);
        // in bursts about half the time, more of it then; most of all as it takes; one this client only came near has no such start
        int in=0,all=0;for(float t=0;t<200;t+=.05f){all++;if(WeakenClient.burst(t,seed))in++;}
        assertTrue(in>all*.3&&in<all*.7,"bursts: "+in+" of "+all);
        float burst=0,calm=0;for(float t=1;t<60;t+=.01f){if(WeakenClient.burst(t,seed))burst=WeakenClient.often(t,seed,true);else calm=WeakenClient.often(t,seed,true);}
        assertTrue(burst>calm*1.5&&calm>.5f);assertTrue(WeakenClient.often(.1f,seed,true)>burst);assertTrue(WeakenClient.often(.1f,seed,false)<=burst);
        // one's glitch is not another's
        boolean other=false;for(float t=1;t<3&&!other;t+=.07f)for(int j=0;j<WeakenClient.BARS;j++)if(!java.util.Arrays.equals(WeakenClient.tear(j,t,1,height,width,1),WeakenClient.tear(j,t,2,height,width,1)))other=true;
        assertTrue(other);
    }
    @Test void theWordsGlitchInBurstsAndStandToBeReadBetween(){
        int seed=99;
        // (the user: "몸과 글씨 둘 다") thrown a few pixels for a step at a time, in bursts; most of the time they stand as they are
        int thrown=0,all=0,split=0,struck=0,apart=0;
        for(float t=.5f;t<120;t+=1/WeakenClient.STEP_RATE/2){
            all++;float[] j=WeakenClient.jolt(t,seed,true);
            if(j==null){assertFalse(!WeakenClient.burst(t,seed)&&j!=null);continue;}
            assertTrue(WeakenClient.burst(t,seed),"only in a burst");thrown++;
            assertTrue(Math.abs(j[0])<=4&&Math.abs(j[1])<=2,"a few pixels: "+j[0]+" "+j[1]);
            assertTrue(j[2]>=1&&j[2]<=3,"its colours split a pixel to three");if(j[2]>=2)split++;
            for(int k=0;k<4;k++){float[] own=WeakenClient.part(k,t,seed);assertTrue(Math.abs(own[0])<=5&&Math.abs(own[1])<=2);if(own[0]!=0||own[1]!=0)apart++;}
            for(int k=0;k<WeakenClient.STRIKES;k++){
                float[] b=WeakenClient.strike(k,t,seed);if(b==null)continue;struck++;
                assertTrue(Math.abs(b[0])<=.45f&&Math.abs(b[1])<=4&&b[2]>=6&&b[2]<=28&&b[3]<=1,"a thin bar across them");
            }
        }
        double share=thrown/(double)all;
        assertTrue(share>.15&&share<.45,"thrown "+share+" of the time: to be read the rest");
        assertTrue(split>thrown/3);assertTrue(struck>thrown);assertTrue(apart>thrown&&apart<thrown*3,"some words of their own, most with the rest");
        // as it takes they are thrown every step, for a moment; one this client only came near is not
        for(float t=0;t<.39f;t+=.01f)assertNotNull(WeakenClient.jolt(t,seed,true));
        boolean still=false;for(float t=0;t<.39f;t+=.01f)if(WeakenClient.jolt(t,7,false)==null)still=true;
        assertTrue(still);
        // the colours they split to are of purple's two sides, a pink and a blue violet
        assertTrue((WeakenClient.SPLIT_RIGHT>>16&255)>200&&(WeakenClient.SPLIT_RIGHT&255)>180&&(WeakenClient.SPLIT_RIGHT>>8&255)<120);
        assertTrue((WeakenClient.SPLIT_LEFT&255)>230&&(WeakenClient.SPLIT_LEFT>>16&255)<130&&(WeakenClient.SPLIT_LEFT>>8&255)<120);
        assertEquals(4,WeakenClient.WORDS.split(" ").length);
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
