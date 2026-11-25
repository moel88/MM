package dev.sangwoo.celestialfall.client.xchara;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** (2.137.6, user: "폰트를 패널에 맞게 예쁘게") The lettering of the word on the hacking panel. */
class PanelFontTest{
    private static float len(float ax,float ay,float bx,float by){return (float)Math.hypot(bx-ax,by-ay);}

    @Test void theWordFillsItsLineLetterByLetter(){
        var w=PanelFont.WORD;
        assertEquals(HackLook.WORD.length(),w.length);
        assertEquals(0,w[0].x(),1e-6,"from its left edge");
        assertEquals(PanelFont.WIDTH,w[w.length-1].x()+w[w.length-1].width(),1e-4,"to its right");
        for(int i=0;i<w.length;i++){
            assertEquals(HackLook.WORD.charAt(i),w[i].sign());
            if(i>0)assertEquals(PanelFont.GAP,w[i].x()-w[i-1].x()-w[i-1].width(),1e-5,"one gap between two letters");
        }
        // the I is one stroke, the W the widest, the rest much alike; taller than wide, all of them
        for(var l:w){
            if(l.sign()=='I')assertEquals(PanelFont.STROKE,l.width(),1e-6);
            else assertTrue(l.width()>PanelFont.STROKE*4&&l.width()<PanelFont.HEIGHT,"a letter taller than wide: "+l.sign()+" "+l.width());
        }
        assertTrue(w[4].width()>w[0].width()*1.2f,"the W wider than the O");
        // the same letter is the same strokes, moved along (the two Es, the two Rs)
        for(int a=0;a<w.length;a++)for(int b=a+1;b<w.length;b++)if(w[a].sign()==w[b].sign()){
            assertEquals(w[a].paths().length,w[b].paths().length);
            for(int k=0;k<w[a].paths().length;k++)for(int i=0;i<w[a].paths()[k].x().length;i++){
                assertEquals(w[a].paths()[k].x()[i]-w[a].x(),w[b].paths()[k].x()[i]-w[b].x(),1e-5);
                assertEquals(w[a].paths()[k].y()[i],w[b].paths()[k].y()[i],1e-6);
            }
        }
    }

    @Test void everyStrokeLiesWithinItsLetterAndReachesItsEdges(){
        var body=PanelFont.edges(0);
        for(int l=0;l<PanelFont.WORD.length;l++){
            var letter=PanelFont.WORD[l];
            float x0=9,x1=-9,y0=9,y1=-9;
            for(var s:body[l])for(int i=0;i<s.lx().length;i++){
                x0=Math.min(x0,Math.min(s.lx()[i],s.rx()[i]));x1=Math.max(x1,Math.max(s.lx()[i],s.rx()[i]));
                y0=Math.min(y0,Math.min(s.ly()[i],s.ry()[i]));y1=Math.max(y1,Math.max(s.ly()[i],s.ry()[i]));
            }
            String at="letter "+letter.sign()+" ("+l+")";
            assertEquals(letter.x(),x0,.0012,at+" from its left");
            assertEquals(letter.x()+letter.width(),x1,.0012,at+" to its right");
            assertEquals(0,y0,.0012,at+" from the top of the line");
            assertEquals(PanelFont.HEIGHT,y1,.0012,at+" to its foot");
        }
    }

    @Test void aStrokeIsOneWidthAndItsEdgesNeverCross(){
        for(float reach:new float[]{0,HackPanel.WORD_GLOW_NEAR,HackPanel.WORD_GLOW_FAR}){
            var e=PanelFont.edges(reach);
            for(int l=0;l<PanelFont.WORD.length;l++)for(int k=0;k<e[l].length;k++){
                var p=PanelFont.WORD[l].paths()[k];var s=e[l][k];
                String at=PanelFont.WORD[l].sign()+" stroke "+k+" reach "+reach;
                for(int i=0;i<p.spans();i++){
                    int a=i,b=(i+1)%p.x().length;
                    float dx=p.x()[b]-p.x()[a],dy=p.y()[b]-p.y()[a],d=len(0,0,dx,dy);dx/=d;dy/=d;
                    // both edges run the way the stroke does (no bow-tie), each on its own side of the middle line
                    assertTrue((s.lx()[i+1]-s.lx()[i])*dx+(s.ly()[i+1]-s.ly()[i])*dy>-1e-5,at+" run "+i+": left edge turned back");
                    assertTrue((s.rx()[i+1]-s.rx()[i])*dx+(s.ry()[i+1]-s.ry()[i])*dy>-1e-5,at+" run "+i+": right edge turned back");
                    for(int j=i;j<=i+1;j++){
                        float left=-(s.lx()[j]-p.x()[a])*dy+(s.ly()[j]-p.y()[a])*dx,right=-(s.rx()[j]-p.x()[a])*dy+(s.ry()[j]-p.y()[a])*dx;
                        assertTrue(left>0&&right<0,at+" run "+i+": edges on their sides");
                        // the stroke itself: half its width either side of every run
                        if(reach==0){assertEquals(PanelFont.STROKE*.5f,left,1e-4,at);assertEquals(-PanelFont.STROKE*.5f,right,1e-4,at);}
                        else{
                            // (an end cut level, not square, carries its light a little further to one side past the cut)
                            boolean level=!p.closed()&&(j==0&&p.flatFrom()||j==p.x().length-1&&p.flatTo());
                            float most=PanelFont.STROKE*.5f+reach*(level?1.8f:1)+1e-4f;
                            assertTrue(left<=most&&-right<=most,at+": the light no further than its reach");
                        }
                    }
                }
            }
        }
    }

    @Test void theLettersAreAConsolesCutCorneredShapes(){
        var w=PanelFont.WORD;
        // the O: one closed stroke of eight sides, its four corners cut at 45 degrees
        var o=w[0].paths();
        assertEquals(1,o.length);assertTrue(o[0].closed());assertEquals(8,o[0].x().length);
        int cut=0;
        for(int i=0;i<8;i++){
            float dx=Math.abs(o[0].x()[(i+1)%8]-o[0].x()[i]),dy=Math.abs(o[0].y()[(i+1)%8]-o[0].y()[i]);
            if(dx>1e-5&&dy>1e-5){assertEquals(dx,dy,1e-5,"a cut at 45");assertEquals(PanelFont.CUT,dx,1e-5);cut++;}
        }
        assertEquals(4,cut);
        // the V and the W: one open stroke each, their tops cut level, standing on flats no narrower than the stroke
        for(int l:new int[]{1,4}){
            var p=w[l].paths();assertEquals(1,p.length);
            assertTrue(!p[0].closed()&&p[0].flatFrom()&&p[0].flatTo(),"level tops");
            int n=p[0].x().length;assertEquals(l==1?4:8,n);
            assertEquals(0,p[0].y()[0],1e-6);assertEquals(0,p[0].y()[n-1],1e-6);
            for(int i=1;i<n-1;i+=2){
                assertEquals(p[0].y()[i],p[0].y()[i+1],1e-6,"a flat");
                assertTrue(p[0].x()[i+1]-p[0].x()[i]>=PanelFont.STROKE-1e-5,"no narrower than the stroke");
            }
            for(int i=0;i<n;i++)assertEquals(p[0].x()[i]-w[l].x(),w[l].width()-(p[0].x()[n-1-i]-w[l].x()),1e-5,"the same either side");
        }
        // the W's middle peak stops short of its tops
        assertTrue(w[4].paths()[0].y()[3]>PanelFont.HEIGHT*.3f&&w[4].paths()[0].y()[3]<PanelFont.HEIGHT*.5f);
        // the E: a stem and its two arms as one stroke, its upper left corner cut; a shorter middle arm from the stem
        var e=w[2].paths();assertEquals(2,e.length);
        assertTrue(e[1].joinedFrom()&&!e[1].joinedTo());
        assertEquals(PanelFont.HEIGHT*.5f,e[1].y()[0],1e-6);
        assertTrue(e[1].x()[1]<e[0].x()[0]-.005f,"the middle arm the shorter");
        // the R: a stem and a bowl closing on it, and a leg from under the bowl to the foot of the line
        var r=w[3].paths();assertEquals(2,r.length);
        assertTrue(r[0].joinedTo()&&r[1].joinedFrom()&&r[1].flatTo());
        assertEquals(PanelFont.HEIGHT,r[1].y()[1],1e-6);
        // the I one stroke, the T a bar and a stem under its middle
        assertEquals(1,w[6].paths().length);
        var t=w[7].paths();assertEquals(2,t.length);
        assertEquals(w[7].x()+w[7].width()*.5f,t[1].x()[0],1e-5);
    }

    @Test void anOpenEndsLightGoesPastItAndAJoinedEndsDoesNot(){
        float r=HackPanel.WORD_GLOW_FAR;
        // the I: its light a stroke and twice the reach wide, the line and twice the reach tall
        var i=PanelFont.sides(PanelFont.WORD[6].paths()[0],r);
        assertEquals(PanelFont.STROKE+2*r,Math.abs(i.rx()[0]-i.lx()[0]),1e-5);
        assertEquals(-r,i.ly()[0],1e-5);assertEquals(PanelFont.HEIGHT+r,i.ly()[1],1e-5);
        // the T's stem under its bar: nothing above where it joins
        var stem=PanelFont.WORD[7].paths()[1];var s=PanelFont.sides(stem,r);
        assertEquals(stem.y()[0],s.ly()[0],1e-6);assertEquals(stem.y()[0],s.ry()[0],1e-6);
    }
}
