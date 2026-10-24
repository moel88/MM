package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.gacha.GachaFont.Weight;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;
import java.util.List;

/**
 * What a pull brought (user request, 2.40.0: the results drastically reworked). An SSR opens on a full-screen splash: the
 * Kageno banner bursting in gold, "SSR" and five stars striking in, his title cut in by a stroke of light, "스탠드 획득"
 * and NEW. Other grades come as tall cards that fly in face down and turn over in a flash of their colour: the Rokakaka
 * an R gives (2.41.0) or the crest of the hourglass that lit, the grade and its stars. A ten-pull (2.41.0, user request)
 * shows its cards one at a time, a click going on to the next (a click during a card's turn finishes it first; an SSR's
 * card opens its splash), then all ten in a row with the tally; 건너뛰기 goes straight to the row. The 확인 button shows when
 * all is out. Times are ticks since the results (or the card on show) began, smooth between ticks.
 */
final class GachaResults {
    private GachaResults(){}
    /** The SSR splash's beats. */
    static final float SPLASH_DONE=46;
    static final float CARD_FLY=6,CARD_FLIP=5;
    /** A ten-pull's card on its own: when it has turned and settled (a click then goes on), and when an SSR's card turns into its splash. */
    static final float CARD_SHOWN=24,SSR_TURN=CARD_FLY+CARD_FLIP/2;
    /** All ten together: one card after another this far apart, everything out (확인) by {@link #ALL_DONE}. */
    static final float ALL_STEP=1.2f,ALL_DONE=30;
    /** What each grade gives (2.41.0, user request: every R is a Rokakaka for now; SR has no reward yet). */
    static final String[] ITEM={"로카카카","보랏빛 모래시계","카게노"},GIVES={"스탠드를 초기화하는 열매","보상 준비 중",""};
    private static float last=-1;
    /** A ten-pull's place: the card on show (0 to 9; the count of cards: all of them) and the results' age when it came. */
    private static int shown;private static float shownAt,lastSince=-1;private static int lastShown=-1;

    static void begin(List<Grade> grades){last=-1;shown=0;shownAt=0;lastSince=-1;lastShown=-1;}
    static boolean splash(List<Grade> grades){return grades.contains(Grade.SSR);}
    /** Whether a ten-pull is still going through its cards one at a time. */
    static boolean oneByOne(List<Grade> grades){return grades.size()>1&&shown<grades.size();}
    static boolean done(List<Grade> grades,float age){
        if(grades.size()==1)return age>=(grades.get(0)==Grade.SSR?SPLASH_DONE:30);
        return shown>=grades.size()&&age-shownAt>=ALL_DONE;
    }
    /** When a ten-pull's card on show has turned and settled. */
    private static float shownBy(Grade g){return g==Grade.SSR?SSR_TURN+SPLASH_DONE:CARD_SHOWN;}
    /**
     * A click: a single pull's reveal hurries to its end (how far, returned); a ten-pull's card on show finishes turning,
     * or the next card comes, or after the tenth all ten.
     */
    static float click(List<Grade> grades,float age){
        if(grades.size()==1)return Math.max(0,(grades.get(0)==Grade.SSR?SPLASH_DONE:30)-age);
        if(shown>=grades.size())return 0;
        var g=grades.get(shown);float since=age-shownAt,by=shownBy(g);
        if(since<by){
            // finish this card's turn, with its sound if it had not turned yet
            if(since<(g==Grade.SSR?SSR_TURN:CARD_FLY+CARD_FLIP/2))turned(g,shown);
            shownAt=age-by;lastSince=by;lastShown=shown;return 0;
        }
        shown++;shownAt=age;if(shown>=grades.size())GachaSound.play(GachaSound.Cue.RESULT);
        return 0;
    }
    /** 건너뛰기 during the cards: straight to all ten. */
    static void all(List<Grade> grades,float age){shown=grades.size();shownAt=age;GachaSound.play(GachaSound.Cue.RESULT);}
    static float[] okButton(GachaScreen s){return GachaArt.slab(s.width/2f-s.L(160),s.B(812),s.L(320),s.L(70),s.L(18),s.L(8));}

    static void draw(DrawContext d,GachaScreen s,List<Grade> grades,boolean granted,float age){
        boolean one=grades.size()==1;
        if(one){
            var g=grades.get(0);
            if(g==Grade.SSR)splash(d,s,granted,age,true);
            else{backdrop(d,s,g.ordinal(),age,1);single(d,s,g,age);}
            if(done(grades,age))ok(d,s,GachaArt.ease((age-(g==Grade.SSR?SPLASH_DONE:30))/6f));
            cues(grades,age);
            return;
        }
        if(shown<grades.size())step(d,s,grades,granted,age-shownAt);
        else{
            float since=age-shownAt;int best=grades.stream().mapToInt(Grade::ordinal).max().orElse(0);
            backdrop(d,s,best,since,GachaArt.ramp(0,5,since));all(d,s,grades,since);
            if(since>=ALL_DONE)ok(d,s,GachaArt.ease((since-ALL_DONE)/6f));
        }
        // the scene's white fading off the first card
        if(age<WHITE)d.fill(0,0,s.width,s.height,GachaArt.alpha(0xfff6f3ff,255*(1-GachaArt.ease(age/WHITE))));
    }
    /** How long the scene's closing white takes to clear off a ten-pull's first card. */
    static final float WHITE=6;

    // ---- a ten-pull, one card at a time ----
    /** The card on show, {@code since} ticks after it came: its own ground, the card (an SSR's opening on its splash), where we are. */
    private static void step(DrawContext d,GachaScreen s,List<Grade> grades,boolean granted,float since){
        int k=shown;var g=grades.get(k);
        stepCues(grades,k,since);
        if(g==Grade.SSR&&since>=SSR_TURN)splash(d,s,granted,since-SSR_TURN,true);
        else{
            backdrop(d,s,g==Grade.SSR?0:g.ordinal(),since,GachaArt.ramp(0,4,since));
            if(g==Grade.SSR){
                // face down, a gold light gathering behind it, turning: the splash opens as it turns
                float cw=s.L(300),ch=s.L(440),cx=s.width/2f,cy=s.height*.45f,fly=GachaArt.ease(since/CARD_FLY);
                var m=d.getMatrices().peek().getPositionMatrix();var halo=GachaArt.sprites(d,GachaArt.SPARK,true);
                GachaArt.sprite(halo,m,cx,cy,cw*3,cw*3,0,GachaArt.fade(0xffffae1a,.35f*GachaArt.ramp(2,SSR_TURN,since)));GachaArt.endSprites(halo);
                card(d,s,cx,cy+(1-fly)*s.L(120),cw*(.85f+.15f*fly),ch*(.85f+.15f*fly),2,GachaArt.clamp01((since-CARD_FLY)/CARD_FLIP),-1,fly,true);
            }else single(d,s,g,since);
        }
        pips(d,s,grades,k,since);
        if(since>=shownBy(g)){
            float a=.55f+.45f*(float)Math.sin((since-shownBy(g))*.2f);
            GachaFont.text(k+1<grades.size()?"눌러서 다음":"눌러서 모두 보기",s.width/2f,s.B(870),s.L(20),Weight.MEDIUM).align(.5f).color(0xffe9e2ff).alpha(a).shadow(0xcc000008,s.L(4),0,s.L(1)).draw(d);
        }
    }
    /** Where the ten-pull is: ten marks at the top, the ones seen in their grade's colour, the one on show larger. */
    private static void pips(DrawContext d,GachaScreen s,List<Grade> grades,int k,float since){
        var m=d.getMatrices().peek().getPositionMatrix();float step=s.L(30),y=s.T(70),x0=s.width/2f-step*(grades.size()-1)/2f;
        var b=GachaArt.begin(d,false);
        for(int i=0;i<grades.size();i++){
            boolean seen=i<k||i==k&&since>=CARD_FLY+CARD_FLIP/2;float r=s.L(i==k?8:5.5f);
            int c=seen?GachaScreen.COLOR[grades.get(i).ordinal()]:0x80a7a3d6;
            GachaArt.fill(b,m,new float[]{x0+i*step,y-r,x0+i*step+r,y,x0+i*step,y+r,x0+i*step-r,y},c);
        }
        GachaArt.end(b);
        GachaFont.text((k+1)+" / "+grades.size(),s.width/2f,y+s.L(30),s.L(19),Weight.BOLD).align(.5f).color(0xffcfd3f5).shadow(0xcc000008,s.L(4),0,s.L(1)).draw(d);
    }
    /** The card on show's sounds: its turn, its stars (an SSR's splash its own). */
    private static void stepCues(List<Grade> grades,int k,float since){
        float from=lastShown==k?lastSince:-1;lastShown=k;lastSince=since;
        if(from<0||since-from>4)return;
        var g=grades.get(k);
        if(crossed(from,since,g==Grade.SSR?SSR_TURN:CARD_FLY+CARD_FLIP/2))turned(g,k);
        if(g!=Grade.SSR)for(int j=0;j<g.ordinal()+3;j++)if(crossed(from,since,CARD_FLY+CARD_FLIP+3+j*8f/(g.ordinal()+3)))GachaSound.play(GachaSound.Cue.STAR,1+j*.06f,.6f);
        if(g==Grade.SSR&&crossed(from,since,SSR_TURN+30))GachaSound.play(GachaSound.Cue.STAR,1.1f,.9f);
    }
    /** A card's turning over: its sound (an SSR's splash its fanfare). */
    private static void turned(Grade g,int k){
        if(g==Grade.SSR)GachaSound.play(GachaSound.Cue.SSR_FANFARE);
        else GachaSound.play(g==Grade.R?GachaSound.Cue.CARD:GachaSound.Cue.CARD_SR,1+k*.02f,1);
    }

    /** The results' own ground: a deep blue dark, slow rays in the best grade's colour, motes rising ({@code in}: how far its light is in). */
    private static void backdrop(DrawContext d,GachaScreen s,int best,float age,float in){
        int w=s.width,h=s.height;
        // the dark is always whole (nothing of the world behind ever shows between cards); its light comes in with {@code in}
        d.draw();d.fill(0,0,w,h,0xff05050f);
        var m=d.getMatrices().peek().getPositionMatrix();float cx=w/2f,cy=h*.44f,big=Math.max(w,h);
        var halo=GachaArt.sprites(d,GachaArt.SPARK,true);
        GachaArt.sprite(halo,m,cx,cy,big*1.2f,big*.9f,0,GachaArt.fade(GachaScreen.GLOW[best],in*.3f));
        GachaArt.endSprites(halo);
        var g=GachaArt.begin(d,true);
        glory(g,m,cx,cy,big*.8f,18,age*.004f,GachaArt.fade(GachaScreen.GLOW[best],in*.12f),big*.035f);
        GachaArt.end(g);
        GachaBanner.motes(d,w,h,age,in*.75f,40,GachaScreen.GLOW[best],GachaArt.mix(GachaScreen.COLOR[best],0xffffffff,.6f),17);
    }
    /** Rays from {@code (x, y)} that start a little way out and are brightest along their middle (a glory behind a figure). */
    static void glory(net.minecraft.client.render.BufferBuilder g,Matrix4f m,float x,float y,float reach,int n,float spin,int color,float width){
        for(int k=0;k<n;k++){
            double a=k*Math.PI*2/n+spin+(k%2)*.13f;float len=reach*(k%3==0?1:.72f),half=width*(k%2==0?1:.6f),r0=reach*.12f,mid=reach*.42f;
            float c=(float)Math.cos(a),sn=(float)Math.sin(a);
            float ax=x+c*r0,ay=y+sn*r0,mx=x+c*mid,my=y+sn*mid,ex=x+c*len,ey=y+sn*len;
            float hw0=half*.25f,hw1=half*.6f;
            int clear=GachaArt.alpha(color,0);
            GachaArt.vertex(g,m,ax-sn*hw0,ay+c*hw0,clear);GachaArt.vertex(g,m,ax+sn*hw0,ay-c*hw0,clear);GachaArt.vertex(g,m,mx+sn*hw1,my-c*hw1,color);
            GachaArt.vertex(g,m,ax-sn*hw0,ay+c*hw0,clear);GachaArt.vertex(g,m,mx+sn*hw1,my-c*hw1,color);GachaArt.vertex(g,m,mx-sn*hw1,my+c*hw1,color);
            GachaArt.vertex(g,m,mx-sn*hw1,my+c*hw1,color);GachaArt.vertex(g,m,mx+sn*hw1,my-c*hw1,color);GachaArt.vertex(g,m,ex+sn*half,ey-c*half,clear);
            GachaArt.vertex(g,m,mx-sn*hw1,my+c*hw1,color);GachaArt.vertex(g,m,ex+sn*half,ey-c*half,clear);GachaArt.vertex(g,m,ex-sn*half,ey+c*half,clear);
        }
    }
    static void rays(net.minecraft.client.render.BufferBuilder g,Matrix4f m,float x,float y,float reach,int n,float spin,int color,float width){
        for(int k=0;k<n;k++){
            double a=k*Math.PI*2/n+spin+(k%2)*.13f;float len=reach*(k%3==0?1:.7f),half=width*(k%2==0?1:.6f);
            float c=(float)Math.cos(a),sn=(float)Math.sin(a);
            GachaArt.vertex(g,m,x-sn*half*.15f,y+c*half*.15f,color);GachaArt.vertex(g,m,x+c*len-sn*half,y+sn*len+c*half,GachaArt.alpha(color,0));
            GachaArt.vertex(g,m,x+c*len+sn*half,y+sn*len-c*half,GachaArt.alpha(color,0));
        }
    }

    // ---- the SSR splash ----
    private static void splash(DrawContext d,GachaScreen s,boolean granted,float age,boolean one){
        int w=s.width,h=s.height;
        // the art bursting in: a gold-white flash, a jolt, the zoom settling
        float shake=(float)Math.exp(-age/3.5f)*(float)Math.sin(age*5.1f)*s.L(9);
        float zoom=1+.12f*(1-GachaArt.ease(age/40f))+.008f*(float)Math.sin(age*.03f);
        var p=GachaBanner.place(w,h,zoom,w*.66f,h*.4f);p=new GachaBanner.Place(p.scale(),p.x()+shake,p.y()+shake*.4f);
        d.fill(0,0,w,h,0xff050308);
        GachaBanner.art(d,p,GachaArt.ramp(0,3,age));
        var m=d.getMatrices().peek().getPositionMatrix();
        // gold light pouring from behind him (from above his hood, so it never covers his face), and the ring of the
        // strike rushing out from there
        float fx=p.sx(GachaArt.GLORY_X),fy=p.sy(GachaArt.GLORY_Y),big=Math.max(w,h);
        float glowIn=GachaArt.ramp(2,14,age);
        var halo=GachaArt.sprites(d,GachaArt.SPARK,true);
        GachaArt.sprite(halo,m,fx,fy,big*1.1f,big*1.1f,0,GachaArt.fade(0xffffa928,.32f*glowIn*(.85f+.15f*(float)Math.sin(age*.15f))));
        GachaArt.endSprites(halo);
        var g=GachaArt.begin(d,true);
        glory(g,m,fx,fy,big*1.05f,22,age*.005f,GachaArt.fade(0xffffc44d,.16f*glowIn),big*.028f);
        if(age<18){float k=age/18f,r=big*(.05f+.9f*GachaArt.ease(k)),wide=big*.035f*(1-k*.6f);
            GachaArt.band(g,m,GachaArt.circle(fx,fy,r,64),wide,GachaArt.fade(0xfffff1c2,(1-k)*.5f),GachaArt.fade(0x00ffd27a,0));
            GachaArt.band(g,m,GachaArt.circle(fx,fy,r,64),-wide,GachaArt.fade(0xfffff1c2,(1-k)*.5f),GachaArt.fade(0x00ffd27a,0));}
        GachaArt.end(g);
        float pulse=(float)Math.sin(age*.07f);
        GachaBanner.eyes(d,p,.3f+.25f*pulse*pulse+.6f*GachaBanner.flicker(age,5));
        // the logo in the art's top band, as on the banner
        float logo=GachaArt.ramp(10,22,age);
        if(logo>0)GachaArt.texture(d,GachaArt.LOGO,s.L(GachaArt.LOGO_AT[0]),s.T(GachaArt.LOGO_AT[1]),s.L(GachaArt.LOGO_AT[2]),s.L(GachaArt.LOGO_AT[3]),logo,true);
        // "SSR" striking in, its stars after it
        float sx=p.sx(92),sy=p.sy(250),size=104*p.scale();
        float strike=GachaArt.back((age-6)/8f),sa=GachaArt.ramp(6,9,age);
        if(sa>0){
            var ms=d.getMatrices();ms.push();float k=1+(1-strike)*.6f;ms.translate(sx,sy,0);ms.scale(k,k,1);ms.translate(-sx,-sy,0);
            GachaFont.text("SSR",sx,sy,size,Weight.HEAVY).gradient(0xfffffbe6,0xffffb52e).glow(0xffff9a12,size*.12f,.75f+.6f*(float)Math.exp(-(age-8)/6f)).shadow(0xc0200800,size*.06f,0,size*.035f).alpha(sa).draw(d);
            ms.pop();
            float sw=GachaFont.width("SSR",size,Weight.HEAVY);
            GachaScreen.stars(d,sx+sw+size*1.32f,sy+size*.02f,size*.22f,5,0xffffd447,1,GachaArt.clamp01((age-12)/14f));
        }
        // the title cut in, its sub line closing up
        float cut=GachaArt.ease((age-14)/13f),land=age>27?.9f*(float)Math.exp(-(age-27)/7f):0;
        GachaBanner.title(d,p,cut,land+.08f+.4f*GachaBanner.flicker(age,23),age>60?((age-60)%120)/24f:-1,1);
        GachaBanner.sub(d,p,GachaArt.ramp(26,34,age),1+.3f*(1-GachaArt.ease((age-26)/10f)));
        GachaBanner.motes(d,w,h,age,GachaArt.ramp(4,16,age),56,0xffffb53a,0xfffff0c4,29);
        // "스탠드 획득" on a ribbon, NEW beside it, and what happened to the stand
        float rib=GachaArt.ease((age-30)/9f);
        if(rib>0){
            float rx=p.sx(150)-(1-rib)*s.L(120),ry=p.sy(640),rw=s.L(340),rh=s.L(54);
            float[] r=GachaArt.slab(rx,ry,rw,rh,s.L(16),s.L(6));
            var b=GachaArt.begin(d,false);
            GachaArt.smooth(b,m,r,GachaArt.fade(0xf01f1236,rib),GachaArt.fade(0xf00f0820,rib));
            GachaArt.stroke(b,m,r,s.L(1.6f),GachaArt.fade(0xffffcf5c,rib));
            GachaArt.end(b);
            g=GachaArt.begin(d,true);GachaArt.glow(g,m,r,s.L(22),GachaArt.fade(0xffffa628,rib*.55f),1);GachaArt.end(g);
            GachaFont.text("스탠드 획득",rx+rw/2,ry+rh/2,s.L(27),Weight.BOLD).align(.5f).gradient(0xfffff6dc,0xffffd27a).glow(0xffffa21a,s.L(6),.6f).alpha(rib).draw(d);
            float nw=GachaArt.back((age-36)/7f);
            if(nw>0){
                float nx=rx+rw+s.L(18),ny=ry+rh/2,k=nw;
                float[] badge=GachaArt.slab(nx,ny-s.L(18)*k,s.L(74)*k,s.L(36)*k,s.L(8)*k,s.L(4)*k);
                b=GachaArt.begin(d,false);GachaArt.smooth(b,m,badge,0xffff5b8a,0xffd61e5c);GachaArt.stroke(b,m,badge,s.L(1.2f),0xffffd0dc);GachaArt.end(b);
                g=GachaArt.begin(d,true);GachaArt.glow(g,m,badge,s.L(16),0x99ff3d7a,1);GachaArt.end(g);
                GachaFont.text("NEW",nx+s.L(37)*k,ny,s.L(22)*k,Weight.HEAVY).align(.5f).color(0xffffffff).draw(d);
            }
        }
        if(granted&&one){
            float ga=GachaArt.ramp(38,46,age);
            GachaFont.text("카게노가 바로 장착됐어요",p.sx(150)+s.L(4),p.sy(640)+s.L(86),s.L(24),Weight.BOLD).color(0xffffe7ad).shadow(0xdd000008,s.L(5),0,s.L(1.5f)).alpha(ga).draw(d);
        }
        // the flash it all opened with
        float flash=age<1.5f?GachaArt.ramp(0,1.5f,age):(float)Math.exp(-(age-1.5f)/3.2f);
        d.draw();if(flash>.004f)d.fill(0,0,w,h,GachaArt.alpha(0xfff6dd,235*flash));
    }

    // ---- one card ----
    /** One R or SR card on its own: flying in face down, turning over, then what it gives under it. */
    private static void single(DrawContext d,GachaScreen s,Grade grade,float age){
        int g=grade.ordinal();float cw=s.L(300),ch=s.L(440),cx=s.width/2f,cy=s.height*.45f;
        float fly=GachaArt.ease(age/CARD_FLY);
        float flip=GachaArt.clamp01((age-CARD_FLY)/CARD_FLIP);
        card(d,s,cx,cy+(1-fly)*s.L(120),cw*(.85f+.15f*fly),ch*(.85f+.15f*fly),g,flip,age-CARD_FLY-CARD_FLIP/2,fly,true);
        // the name and what it gives
        float k=GachaArt.ease((age-14)/8f);
        GachaFont.text(ITEM[g],cx,cy+ch/2+s.L(44),s.L(28),Weight.BOLD).align(.5f).color(0xfff1f0ff).glow(GachaScreen.GLOW[g],s.L(8),.5f).alpha(k).draw(d);
        GachaFont.text(GIVES[g],cx,cy+ch/2+s.L(82),s.L(19),Weight.MEDIUM).align(.5f).color(g==0?0xffffc4e1:0xffaab3dc).alpha(GachaArt.ease((age-20)/8f)).draw(d);
    }

    // ---- ten cards ----
    /** The row's card size and gap, and its left edge. */
    static float[] rowLayout(GachaScreen s,int n){
        float cw=s.L(128),ch=s.L(300),gap=s.L(14),total=n*cw+(n-1)*gap,k=Math.min(1,(s.width-s.L(70))/total);
        cw*=k;ch*=k;gap*=k;total*=k;return new float[]{cw,ch,gap,(s.width-total)/2};
    }
    /** All ten together once each has been seen (2.41.0): face up, rising in one after another, then the tally. */
    private static void all(DrawContext d,GachaScreen s,List<Grade> grades,float age){
        float[] r=rowLayout(s,grades.size());float cw=r[0],ch=r[1],gap=r[2],x0=r[3],cy=s.height*.44f;
        for(int i=0;i<grades.size();i++){
            float at=i*ALL_STEP,fly=GachaArt.ease((age-at)/CARD_FLY);if(fly<=0)continue;
            float x=x0+i*(cw+gap)+cw/2;
            // seen already: they come face up, no flash, a shine crossing them as they land
            card(d,s,x,cy+(1-fly)*s.L(90),cw,ch,grades.get(i).ordinal(),1,8+age-at-CARD_FLY,fly,false);
        }
        // the tally, once every card is in, and the Rokakaka they gave
        float tally=GachaArt.ease((age-(grades.size()-1)*ALL_STEP-CARD_FLY-2)/8f);
        if(tally>0){
            int[] n=new int[3];for(var g:grades)n[g.ordinal()]++;
            float y=cy+ch/2+s.L(56),chip=s.L(150),step=s.L(170),x=s.width/2f-step;
            for(int k=2;k>=0;k--){
                float cx=x+(2-k)*step;float[] c=GachaArt.slab(cx-chip/2,y-s.L(22),chip,s.L(44),s.L(10),s.L(4));
                var m=d.getMatrices().peek().getPositionMatrix();
                var b=GachaArt.begin(d,false);GachaArt.smooth(b,m,c,GachaArt.fade(0xd0121436,tally),GachaArt.fade(0xd00a0b22,tally));GachaArt.stroke(b,m,c,s.L(1.2f),GachaArt.fade(GachaScreen.COLOR[k],tally*.8f));GachaArt.end(b);
                GachaFont.text(GachaScreen.NAME[k]+"  "+n[k],cx,y,s.L(25),Weight.BOLD).align(.5f).color(GachaArt.fade(GachaScreen.COLOR[k],tally)).draw(d);
            }
            if(n[0]>0)GachaFont.text("로카카카 "+n[0]+"개를 받았어요",s.width/2f,y+s.L(52),s.L(20),Weight.MEDIUM).align(.5f).color(GachaArt.fade(0xffffc4e1,tally)).shadow(0xcc000008,s.L(4),0,s.L(1)).draw(d);
        }
    }

    /**
     * A card centred on {@code (x, y)}: face down until {@code flip} passes half way, then its face: the grade's frame and
     * crest (Kageno's own face for an SSR), its letters and stars. {@code since} is the time since it turned over.
     */
    private static void card(DrawContext d,GachaScreen s,float x,float y,float w,float h,int g,float flip,float since,float alpha,boolean big){
        var ms=d.getMatrices();
        float turn=(float)Math.cos(Math.PI*flip),sx=Math.max(.02f,Math.abs(turn));boolean face=flip>=.5f;
        ms.push();ms.translate(x,y,0);ms.scale(sx,1+(1-sx)*.04f,1);
        Matrix4f m=ms.peek().getPositionMatrix();
        float hw=w/2,hh=h/2,cut=Math.min(w,h)*.07f;
        float[] box=GachaArt.slab(-hw,-hh,w,h,0,cut);
        int color=GachaScreen.COLOR[g],glow=GachaScreen.GLOW[g];
        if(!face){
            var b=GachaArt.begin(d,false);
            GachaArt.smooth(b,m,box,GachaArt.fade(0xff1b1846,alpha),GachaArt.fade(0xff0b0a24,alpha));
            GachaArt.stroke(b,m,box,Math.max(1,w*.012f),GachaArt.fade(0xffa58cff,alpha));
            GachaArt.stroke(b,m,GachaArt.offset(box,-w*.06f),Math.max(1,w*.006f),GachaArt.fade(0x668c74ff,alpha));
            GachaArt.end(b);
            compass(d,m,0,0,w*.26f,GachaArt.fade(0xffb9a6ff,alpha));
            ms.pop();return;
        }
        float lit=GachaArt.clamp01(since/6f);
        var gl=GachaArt.begin(d,true);
        GachaArt.glow(gl,m,box,Math.min(w,h)*(g==2?.3f:.2f),GachaArt.fade(glow,alpha*(g==2?1.1f:.75f)*(.8f+.2f*(float)Math.sin(since*.18f))),1);
        GachaArt.end(gl);
        var b=GachaArt.begin(d,false);
        GachaArt.smooth(b,m,box,GachaArt.fade(GachaArt.mix(0xff12123a,glow,.18f),alpha),GachaArt.fade(0xff06061a,alpha));
        GachaArt.end(b);
        if(g==2){
            // his face from the banner, the card a window onto it
            float aw=w*.94f,ah=h*.94f,artH=GachaArt.BANNER_H*.62f,artW=artH*aw/ah,u0=(GachaArt.FACE_X-artW*.52f)/GachaArt.BANNER_W,v0=.1f,u1=u0+artW/GachaArt.BANNER_W,v1=v0+artH/GachaArt.BANNER_H;
            GachaArt.texture(d,GachaArt.BANNER,-aw/2,-ah/2,aw,ah,u0,v0,u1,v1,GachaArt.fade(0xffffffff,alpha),false);
            b=GachaArt.begin(d,false);
            GachaArt.fill(b,m,GachaArt.rect(-aw/2,ah*.05f,aw,ah*.45f),GachaArt.fade(0x00100608,alpha),GachaArt.fade(0xf0100608,alpha));
            GachaArt.end(b);
        }else if(g==0){
            // the Rokakaka it gives, in 3D, turning on a soft pink glow
            gl=GachaArt.begin(d,true);
            GachaArt.glow(gl,m,GachaArt.circle(0,-h*.1f,w*.14f,28),w*.6f,GachaArt.fade(0xffff6fae,alpha*.75f),1);
            GachaArt.end(gl);
            fruit(d,0,-h*.12f,w*.66f,since,alpha);
        }else{
            gl=GachaArt.begin(d,true);
            GachaArt.glow(gl,m,GachaArt.circle(0,-h*.1f,w*.12f,28),w*.55f,GachaArt.fade(glow,alpha*.8f),1);
            GachaArt.end(gl);
            crest(d,m,0,-h*.1f,w*.3f,g,since,alpha);
        }
        b=GachaArt.begin(d,false);
        GachaArt.stroke(b,m,box,Math.max(1.2f,w*.014f),GachaArt.fade(GachaArt.mix(color,0xffffffff,.25f),alpha));
        GachaArt.stroke(b,m,GachaArt.offset(box,-w*.045f),Math.max(.8f,w*.005f),GachaArt.fade(GachaArt.alpha(color,120),alpha));
        GachaArt.end(b);
        // a shine crossing the face as it settles
        if(since>0&&since<14){
            float k=since/14f,sxp=-hw-h*.4f+k*(w+h*.8f);
            float[] band=GachaArt.clip(new float[]{sxp,-hh,sxp+w*.18f,-hh,sxp+w*.18f-h*.4f,hh,sxp-h*.4f,hh},box);
            if(band.length>=6){gl=GachaArt.begin(d,true);GachaArt.fillAcross(gl,m,band,GachaArt.fade(0x00ffffff,alpha),GachaArt.fade(0x99ffffff,alpha));GachaArt.end(gl);}
        }
        // the grade and its stars
        float size=Math.min(w*.36f,h*.16f)*(big?1.05f:1);float ly=hh-h*.2f;
        GachaFont.text(GachaScreen.NAME[g],0,ly,size,Weight.HEAVY).align(.5f).gradient(GachaArt.mix(color,0xffffffff,.55f),color).glow(glow,size*.2f,1).shadow(0xc0000008,size*.06f,0,size*.04f).alpha(alpha).draw(d);
        GachaScreen.stars(d,0,ly+size*.78f,Math.min(size*.17f,w*.075f),g+3,color,alpha,GachaArt.clamp01((since-2)/8f));
        if(g==2&&!big)GachaFont.text("카게노",0,ly-size*.9f,size*.42f,Weight.BOLD).align(.5f).color(0xfffff1d6).shadow(0xd0000008,size*.08f,0,size*.03f).alpha(alpha).draw(d);
        // the turn's flash
        if(since>-1&&since<8){
            float f=since<0?since+1:(float)Math.exp(-since/2.2f);
            gl=GachaArt.begin(d,true);
            GachaArt.fill(gl,m,box,GachaArt.fade(0xffffffff,alpha*.75f*f));
            GachaArt.glow(gl,m,box,Math.min(w,h)*.5f,GachaArt.fade(glow,alpha*f),1.4f);
            if(g>0)rays(gl,m,0,0,Math.max(w,h)*(g==2?1.4f:.9f)*(1.2f-f*.4f),14,since*.02f,GachaArt.fade(glow,alpha*.6f*f),w*.05f);
            GachaArt.end(gl);
        }
        ms.pop();
    }
    /** One Rokakaka for the cards: the item itself, so its 3D GeckoLib model draws (2.41.0). */
    private static final net.minecraft.item.ItemStack FRUIT_STACK=new net.minecraft.item.ItemStack(dev.sangwoo.celestialfall.commerce.CommerceRegistry.ROKAKAKA);
    /**
     * The Rokakaka in 3D, {@code width} across, centred on {@code (x, y)} under the current matrices (a card's turn squeezes
     * it with the card), turning slowly and tipped toward the eye; it grows in with {@code alpha} (a model cannot fade).
     */
    private static void fruit(DrawContext d,float x,float y,float width,float since,float alpha){
        if(alpha<=.05f)return;
        var mc=net.minecraft.client.MinecraftClient.getInstance();float block=width/(11/16f)*Math.min(1,alpha);
        var ms=d.getMatrices();ms.push();
        ms.translate(x,y,150);ms.scale(block,-block,block);
        ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(14));
        ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-30+since*2.2f));
        d.draw();com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();net.minecraft.client.render.DiffuseLighting.enableGuiDepthLighting();
        mc.getItemRenderer().renderItem(FRUIT_STACK,net.minecraft.client.render.model.json.ModelTransformationMode.NONE,net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE,
            net.minecraft.client.render.OverlayTexture.DEFAULT_UV,ms,d.getVertexConsumers(),mc.world,0);
        d.draw();com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        ms.pop();
    }
    /** The grade's crest: rings and ticks about the hourglass that lit, turning slowly. */
    private static void crest(DrawContext d,Matrix4f m,float x,float y,float r,int g,float since,float alpha){
        int color=GachaScreen.COLOR[g],glow=GachaScreen.GLOW[g];
        var b=GachaArt.begin(d,true);
        GachaArt.stroke(b,m,GachaArt.circle(x,y,r,48),Math.max(1,r*.035f),GachaArt.fade(GachaArt.alpha(color,200),alpha));
        GachaArt.stroke(b,m,GachaArt.circle(x,y,r*.78f,40),Math.max(.8f,r*.018f),GachaArt.fade(GachaArt.alpha(color,120),alpha));
        float spin=since*.012f;
        for(int k=0;k<24;k++){
            double a=k*Math.PI/12+spin;float c=(float)Math.cos(a),sn=(float)Math.sin(a),r0=r*(k%3==0?.84f:.9f);
            GachaArt.line(b,m,x+c*r0,y+sn*r0,x+c*r*.98f,y+sn*r*.98f,Math.max(.8f,r*.025f),GachaArt.fade(GachaArt.alpha(color,k%3==0?230:140),alpha));
        }
        GachaScreen.hourglass(b,m,x,y,r*.5f,GachaArt.fade(GachaArt.mix(color,0xffffffff,.35f),alpha),GachaArt.fade(glow,alpha),.62f);
        GachaArt.end(b);
    }
    /** The card back's emblem: the BOTAND compass star in a ring. */
    private static void compass(DrawContext d,Matrix4f m,float x,float y,float r,int color){
        var b=GachaArt.begin(d,false);
        GachaArt.stroke(b,m,GachaArt.circle(x,y,r*.62f,40),Math.max(1,r*.05f),color);
        GachaArt.fillStar(b,m,x,y,r,r*.2f,4,GachaArt.mix(color,0xffffffff,.4f),color);
        GachaArt.end(b);
        var g=GachaArt.begin(d,true);GachaArt.glow(g,m,GachaArt.circle(x,y,r*.2f,20),r*.9f,GachaArt.alpha(color,90),1);GachaArt.end(g);
    }

    private static void ok(DrawContext d,GachaScreen s,float in){
        var m=d.getMatrices().peek().getPositionMatrix();float[] ok=okButton(s);boolean hover=GachaArt.inside(ok,s.mouseX(),s.mouseY());
        var g=GachaArt.begin(d,true);GachaArt.glow(g,m,ok,s.L(hover?34:24),GachaArt.fade(0xff6f8dff,in*(hover?.9f:.6f)),1);GachaArt.end(g);
        var b=GachaArt.begin(d,false);
        GachaArt.smooth(b,m,ok,GachaArt.fade(hover?0xffe9f2ff:0xffd6e4ff,in),GachaArt.fade(hover?0xff9fb6ff:0xff8aa2f5,in));
        GachaArt.stroke(b,m,ok,s.L(1.8f),GachaArt.fade(0xfff6f9ff,in));
        GachaArt.end(b);
        GachaFont.text("확인",(ok[10]+ok[4])/2,(ok[1]+ok[7])/2,s.L(30),Weight.BOLD).align(.5f).color(GachaArt.fade(0xff17206e,in)).draw(d);
    }

    /** A single pull's sounds, between the last frame and this one; a click that hurries the reveal skips what it passes. */
    private static void cues(List<Grade> grades,float age){
        float from=last;last=age;
        var g=grades.get(0);
        if(from<0){if(g==Grade.SSR)GachaSound.play(GachaSound.Cue.SSR_FANFARE);return;}
        if(age-from>4)return;
        if(g!=Grade.SSR&&crossed(from,age,CARD_FLY+CARD_FLIP/2))GachaSound.play(g==Grade.SR?GachaSound.Cue.CARD_SR:GachaSound.Cue.CARD);
        if(g!=Grade.SSR)for(int k=0;k<g.ordinal()+3;k++)if(crossed(from,age,CARD_FLY+CARD_FLIP+3+k*8f/(g.ordinal()+3)))GachaSound.play(GachaSound.Cue.STAR,1+k*.06f,.6f);
        if(g==Grade.SSR&&crossed(from,age,30))GachaSound.play(GachaSound.Cue.STAR,1.1f,.9f);
    }
    private static boolean crossed(float from,float to,float at){return from<at&&to>=at;}
}
