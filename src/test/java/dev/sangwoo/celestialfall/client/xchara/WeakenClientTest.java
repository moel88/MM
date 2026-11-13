package dev.sangwoo.celestialfall.client.xchara;

import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.WeakenRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's weakening as drawn (2.126.0, the user: "머리 위에 보라색 글씨로 공격력 방어력이 25% 감소한다. 라고 뜨게
 * 해주고 몸에도 보라색 … 아우라가 방출되게"; 2.126.1: "그냥 안쪽에서 화살표 말고 바깥쪽으로 보라색 이펙트기 퍼져나가게",
 * no arrows at all and "흩어지는 보라 입자"; "글자 좀만 낮춰 그냥 저 가만히 있는 더미 이름표랑 겹치게 해 그리고 글자
 * 폰트를 도트 처럼 만들어주고 더 보라색으로"): the words are the user's, where a name is and in the game's pixel
 * typeface, a strong purple; the motes come out of the body and only ever go outward, many always on their way, none
 * stepping from one frame to the next; it shows all its time and goes as its time runs out; its user presses their
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
    @Test void theMotesComeOutOfTheBodyAndOnlyGoOutward(){
        int seed=4711;
        for(int k=0;k<WeakenClient.MOTES;k++){
            float[] was=null;
            for(float t=0;t<8;t+=1/240f){
                float[] a=WeakenClient.mote(k,t,seed);
                assertTrue(a[1]<1,"it starts within the body");
                assertTrue(a[2]>=0&&a[2]<=WeakenClient.REACH_MOST+1e-4,"out no farther than its reach: "+a[2]);
                assertTrue(a[3]>0&&a[3]<1,"between the feet and the top of the head: "+a[3]);
                assertTrue(a[4]>=0&&a[4]<=1);assertTrue(a[5]>.4f&&a[5]<1.35f);assertTrue(a[6]>=0&&a[6]<=1);
                if(was!=null){
                    if(was[0]==a[0]){
                        // on its way out: never back in (the user: "바깥쪽으로 … 퍼져나가게"), and no jump
                        assertTrue(a[2]>=was[2]-1e-5,"mote "+k+" came back in at "+t);
                        assertTrue(a[2]-was[2]<.03,"mote "+k+" jumped at "+t);
                        assertTrue(Math.abs(a[3]-was[3])<.01,"mote "+k+" jumped up or down at "+t);
                        assertTrue(Math.abs(a[4]-was[4])<.08,"mote "+k+" blinked at "+t);
                    }else{
                        // gone, and come again somewhere else: unseen as it does
                        assertTrue(was[4]<.02&&a[4]<.02,"mote "+k+" moved while seen at "+t+": "+was[4]+" "+a[4]);
                    }
                }
                was=a;
            }
        }
    }
    @Test void theMotesAreSmallManyAllRoundAndGoWellOut(){
        // small squares, many of them: a scattering, not a few big things
        assertTrue(WeakenClient.MOTE<=.1);assertTrue(WeakenClient.MOTES/WeakenClient.LIFE>=60,"sixty and more a second come out");
        assertTrue(WeakenClient.REACH_LEAST>=.6&&WeakenClient.REACH_MOST>=1.3,"well out from the body");
        int seed=99;
        for(float t=0;t<12;t+=.03f){
            int seen=0;for(int k=0;k<WeakenClient.MOTES;k++)if(WeakenClient.mote(k,t,seed)[4]>.5f)seen++;
            assertTrue(seen>=20,"only "+seen+" motes at "+t);
        }
        // they go out all round, at every height of the body, and fade well out from it
        boolean[] side=new boolean[8],level=new boolean[4];
        for(float t=0;t<12;t+=.1f)for(int k=0;k<WeakenClient.MOTES;k++){float[] a=WeakenClient.mote(k,t,seed);side[(int)(a[0]/(Math.PI*2)*8)%8]=true;level[Math.min(3,(int)(a[3]*4))]=true;}
        for(boolean s:side)assertTrue(s);for(boolean l:level)assertTrue(l);
        float far=0;for(float u=0;u<WeakenClient.LIFE;u+=.01f)for(int k=0;k<WeakenClient.MOTES;k++){float[] a=WeakenClient.mote(k,u,seed);if(a[4]>.3f)far=Math.max(far,a[2]);}
        assertTrue(far>1,"seen a block and more out: "+far);
        // one's motes are not another's
        assertNotEquals(WeakenClient.mote(0,1,1)[0],WeakenClient.mote(0,1,2)[0]);
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
