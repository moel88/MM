package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.ProgressClient;
import dev.sangwoo.celestialfall.client.menu.MenuClient;
import dev.sangwoo.celestialfall.gacha.GachaRules;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/**
 * The stand gacha (user request, 2.39.0), laid out after the user's draft: the backdrop with its compass emblem, the logo,
 * a details button and the 1- and 10-pull buttons. A pull plays its scene ({@link GachaStage}: Bomi and her hourglasses)
 * and then shows what came. Everything is placed in the draft's own pixels ({@link #u} GUI units each), the backdrop
 * covering the screen from the top, the controls held to the screen's edges as in the draft.
 */
public final class GachaScreen extends Screen {
    enum Stage {MAIN,SCENE,RESULT}
    /** The grades' colours (user request): R sky blue, SR purple, SSR yellow. */
    static final int[] COLOR={0xff74d8ff,0xffb978ff,0xffffd447},GLOW={0xff2fb4ff,0xff9447ff,0xffffae1a};
    static final String[] NAME={"R","SR","SSR"};
    private Stage stage=Stage.MAIN;
    private boolean details;
    private List<Grade> grades=List.of();
    private boolean granted;
    private long base=Long.MIN_VALUE;
    private float sceneAt,resultAt,openedAt=-1;
    private float u,bgX;
    private double mx=-1,my=-1;
    public GachaScreen(){super(Text.literal("스탠드 뽑기"));}
    @Override public boolean shouldPause(){return false;}
    @Override protected void init(){u=Math.max(width/(float)GachaArt.BG_W,height/(float)GachaArt.BG_H);bgX=(width-GachaArt.BG_W*u)/2;}
    /** Ticks since the screen opened, running smoothly between ticks (the scene is timed by it, like every cast). */
    float clock(){var w=client.world;if(w==null)return 0;if(base==Long.MIN_VALUE)base=w.getTime();return (w.getTime()-base)+client.getTickDelta();}
    Stage stage(){return stage;}

    @Override public void tick(){if(!MenuClient.available())client.setScreen(null);}
    @Override public void close(){
        if(details){details=false;return;}
        if(stage==Stage.SCENE){reveal();return;}
        if(stage==Stage.RESULT){stage=Stage.MAIN;openedAt=-1;return;}
        back();
    }
    /** Back to the menu's dock, where the gacha was opened from. */
    private void back(){client.setScreen(null);dev.sangwoo.celestialfall.client.menu.MenuClient.open();}
    /** The server's answer: what was rolled (nothing when it only sent the count or refused, its notice saying why). */
    void answered(List<Grade> rolled,boolean equipped){
        if(rolled.isEmpty())return;
        grades=rolled;granted=equipped;details=false;stage=Stage.SCENE;sceneAt=clock();
        GachaStage.begin(this,rolled);
    }
    /** The scene ends (or is skipped): the results. Its GPU resources stay for the next pull until the screen closes. */
    private void reveal(){stage=Stage.RESULT;resultAt=clock();GachaSound.result(grades);}
    boolean canPull(){return GachaClient.ready&&!GachaClient.pending&&GachaRules.canPull(ProgressClient.profile.stand());}

    // ---- layout, in the draft's pixels from the screen's edges ----
    private float L(float x){return x*u;}
    private float R(float fromRight){return width-fromRight*u;}
    private float B(float fromBottom){return height-fromBottom*u;}
    /** A slanted button (the draft's parallelogram with clipped corners): x, y its top-left, w, h its size. */
    private float[] slab(float x,float y,float w,float h,float slant,float clip){
        return new float[]{x+slant,y, x+w-clip,y, x+w,y+clip, x+w-slant,y+h, x+clip,y+h, x,y+h-clip};
    }
    private float[] pullOne(){return slab(R(714),B(211),L(338),L(114),L(26),L(10));}
    private float[] pullTen(){return slab(R(366),B(211),L(328),L(114),L(26),L(10));}
    private float[] detailButton(){return slab(L(40),B(334),L(162),L(47),L(12),L(5));}
    private float[] closeBox(){float c=L(30);return new float[]{R(49)-c,L(44)-c,R(49)+c,L(44)-c,R(49)+c,L(44)+c,R(49)-c,L(44)+c};}
    static boolean inside(float[] p,double x,double y){
        boolean in=false;for(int i=0,j=p.length/2-1;i<p.length/2;j=i++){
            double xi=p[i*2],yi=p[i*2+1],xj=p[j*2],yj=p[j*2+1];
            if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)in=!in;
        }return in;
    }
    // details panel and result buttons
    private float[] panel(){float w=Math.min(width-L(80),L(980)),h=Math.min(height-L(60),L(700));return new float[]{(width-w)/2,(height-h)/2,w,h};}
    private float[] panelClose(){var p=panel();float c=L(24),x=p[0]+p[2]-L(46),y=p[1]+L(46);return new float[]{x-c,y-c,x+c,y-c,x+c,y+c,x-c,y+c};}
    private float[] okButton(){float w=L(300),h=L(70);return slab((width-w)/2,B(112),w,h,L(18),L(8));}
    private float[] skipButton(){return slab(R(230),L(36),L(190),L(56),L(14),L(6));}

    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return super.mouseClicked(x,y,button);
        if(stage==Stage.SCENE){if(inside(skipButton(),x,y)){MenuClient.click();reveal();}return true;}
        if(stage==Stage.RESULT){if(clock()-resultAt>6&&inside(okButton(),x,y)){MenuClient.click();stage=Stage.MAIN;openedAt=-1;}return true;}
        if(details){var p=panel();if(inside(panelClose(),x,y)||x<p[0]||y<p[1]||x>p[0]+p[2]||y>p[1]+p[3]){MenuClient.click();details=false;}return true;}
        if(inside(closeBox(),x,y)){back();return true;}
        if(inside(detailButton(),x,y)){MenuClient.click();details=true;return true;}
        for(int n:new int[]{GachaRules.SINGLE,GachaRules.TEN})if(inside(n==1?pullOne():pullTen(),x,y)){
            if(canPull()){MenuClient.click();GachaClient.request(n);}
            return true;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_M){client.setScreen(null);return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void mouseMoved(double x,double y){mx=x;my=y;}


    @Override public void render(DrawContext d,int mouseX,int mouseY,float delta){
        mx=mouseX;my=mouseY;float t=clock();
        if(stage==Stage.SCENE){
            float age=t-sceneAt;
            GachaStage.render(d,this,grades,age);
            if(age>=GachaStage.length(grades)){reveal();}
            else drawSkip(d,t);
            return;
        }
        if(stage==Stage.RESULT){drawBackdrop(d,1);drawResult(d,t-resultAt);return;}
        // the screen opens over the world: the backdrop fades in, the logo settles, the pull buttons slide in
        if(openedAt<0)openedAt=t;float in=ease(Math.min(1,(t-openedAt)/8f));
        drawBackdrop(d,in);
        drawMain(d,t,in);
        // once the screen has settled, ready the scenes' renderer a piece a frame
        if(t-openedAt>10){d.draw();GachaStage.prepare();}
        if(details)drawDetails(d);
    }

    // ---- main view ----
    private void drawBackdrop(DrawContext d,float alpha){
        d.fill(0,0,width,height,GachaArt.alpha(0xff02030f,255*alpha));
        GachaArt.texture(d,GachaArt.BACKGROUND,bgX,0,GachaArt.BG_W*u,GachaArt.BG_H*u,0,0,GachaArt.BG_W,GachaArt.BG_H,GachaArt.BG_W,GachaArt.BG_H,alpha,false);
    }
    private void drawMain(DrawContext d,float t,float in){
        var m=d.getMatrices().peek().getPositionMatrix();
        // logo (light lifted from the draft) and its slow shimmer
        float ly=L(4)-(1-in)*L(18);
        GachaArt.texture(d,GachaArt.LOGO,L(18),ly,L(GachaArt.LOGO_W),L(GachaArt.LOGO_H),0,0,GachaArt.LOGO_W,GachaArt.LOGO_H,GachaArt.LOGO_W,GachaArt.LOGO_H,in,true);
        GachaArt.texture(d,GachaArt.LOGO,L(18),ly,L(GachaArt.LOGO_W),L(GachaArt.LOGO_H),0,0,GachaArt.LOGO_W,GachaArt.LOGO_H,GachaArt.LOGO_W,GachaArt.LOGO_H,in*(.18f+.12f*(float)Math.sin(t*.12f)),true);
        boolean hoverClose=inside(closeBox(),mx,my),hoverDetail=!details&&inside(detailButton(),mx,my);
        var b=GachaArt.begin(d,false);
        // the count pill where the draft had its currency (pulls are free for now), as wide as its words
        var count=GachaClient.count;String counted=GachaClient.ready?"SSR 확정까지 "+count.toSsr()+"회":"불러오는 중...";
        float pw=Math.max(L(220),GachaArt.width(counted,L(22),false)+L(92)),px=R(138)-pw,py=L(18),ph=L(52);float[] pill=slab(px,py,pw,ph,L(8),L(8));
        GachaArt.fill(b,m,pill,0xcc070d2c,0xcc040820);GachaArt.band(b,m,pill,L(1.6f),0xff2b3f8f,0xff2b3f8f);
        // divider and close mark
        GachaArt.fill(b,m,new float[]{R(105),L(22),R(103),L(22),R(103),L(66),R(105),L(66)},0x5531407a);
        int xc=hoverClose?0xffe4e8ff:0xff5d61a8;float c=L(17),w=L(2.4f);
        cross(b,m,R(49),L(44),c,w,xc);
        // details button
        float[] db=detailButton();
        GachaArt.fill(b,m,db,hoverDetail?0xcc272a78:0xb3141650,hoverDetail?0xcc1a1c5c:0xb30b0c38);GachaArt.band(b,m,db,L(1.6f),0xff5252cb,0xff5252cb);
        GachaArt.fill(b,m,new float[]{L(38),B(267),L(300),B(266.2f),L(300),B(265.4f),L(38),B(264.6f)},0x993a3fb8,0x993a3fb8);
        GachaArt.end(b);
        // pull buttons, sliding in from the right as the screen opens
        boolean can=canPull();
        var ms=d.getMatrices();ms.push();ms.translate((1-in)*L(220),0,0);var bm=ms.peek().getPositionMatrix();
        var pb=GachaArt.begin(d,false);
        drawPull(pb,bm,pullOne(),0,can,can&&!details&&inside(pullOne(),mx,my));
        drawPull(pb,bm,pullTen(),1,can,can&&!details&&inside(pullTen(),mx,my));
        GachaArt.end(pb);
        if(can){
            var pg=GachaArt.begin(d,true);float pulse=(.85f+.15f*(float)Math.sin(t*.2f))*in;
            GachaArt.glow(pg,bm,pullOne(),L(46),0xff2f62ff,(inside(pullOne(),mx,my)&&!details?1.25f:1f)*pulse);
            GachaArt.glow(pg,bm,pullTen(),L(46),0xffa13dff,(inside(pullTen(),mx,my)&&!details?1.25f:1f)*pulse);
            GachaArt.band(pg,bm,pullOne(),L(2.2f),0xccf4fcff,0x00f4fcff);GachaArt.band(pg,bm,pullTen(),L(2.2f),0xccfbeeff,0x00fbeeff);
            GachaArt.end(pg);
        }
        pullLabel(d,pullOne(),"1회 뽑기",0,can);pullLabel(d,pullTen(),"10회 뽑기",1,can);
        ms.pop();
        // the other glows, added as light
        var g=GachaArt.begin(d,true);
        if(hoverClose)GachaArt.glow(g,m,closeBox(),L(14),0x667a82ff,1);
        GachaArt.glow(g,m,detailButton(),L(18),0x553f48ff,hoverDetail?1.6f:1);
        hourglass(g,m,px+L(34),py+ph/2,L(15),0xff66bffc,0x8866bffc,1);
        GachaArt.end(g);
        // labels
        GachaArt.text(d,counted,px+L(62),py+ph/2-L(10),L(22),0xff66bffc,false,false,false);
        magnifier(d,L(88),B(334)+L(23.5f),L(10),0xffb9c3fa);
        GachaArt.text(d,"상세",L(110),B(334)+L(11),L(26),0xffdde6fd,false,true,false);
        // why the buttons are dark, or what the server said
        String note=!GachaRules.canPull(ProgressClient.profile.stand())?"장착한 스탠드가 없어야 뽑을 수 있어요.":GachaClient.pending?"서버에서 확인하는 중...":GachaClient.notice;
        if(!note.isEmpty())GachaArt.text(d,note,R(38)-GachaArt.width(note,L(24),false),B(250),L(24),!can&&!GachaClient.pending?0xffffb7c8:0xffc9d4ff,false,false,true);
    }
    private static void cross(net.minecraft.client.render.BufferBuilder b,Matrix4f m,float x,float y,float r,float w,int color){
        line(b,m,x-r,y-r,x+r,y+r,w,color);line(b,m,x-r,y+r,x+r,y-r,w,color);
    }
    static void line(net.minecraft.client.render.BufferBuilder b,Matrix4f m,float x1,float y1,float x2,float y2,float w,int color){
        float len=(float)Math.hypot(x2-x1,y2-y1);if(len==0)return;float dx=-(y2-y1)*w/len/2,dy=(x2-x1)*w/len/2;
        GachaArt.fill(b,m,new float[]{x1+dx,y1+dy,x2+dx,y2+dy,x2-dx,y2-dy,x1-dx,y1-dy},color);
    }
    /** An hourglass mark: two glass bulbs meeting at a waist, sand {@code fill} of the way down. */
    static void hourglass(net.minecraft.client.render.BufferBuilder b,Matrix4f m,float x,float y,float r,int frame,int sand,float fill){
        float w=r*.62f,neck=r*.09f;
        GachaArt.fill(b,m,new float[]{x-w,y-r,x+w,y-r,x+neck,y,x-neck,y},GachaArt.alpha(frame,60));
        GachaArt.fill(b,m,new float[]{x-neck,y,x+neck,y,x+w,y+r,x-w,y+r},GachaArt.alpha(frame,60));
        float s=r*Math.max(0,Math.min(1,fill));GachaArt.fill(b,m,new float[]{x-w*s/r,y+r-s,x+w*s/r,y+r-s,x+w,y+r,x-w,y+r},sand);
        GachaArt.fill(b,m,new float[]{x-w*1.18f,y-r-r*.16f,x+w*1.18f,y-r-r*.16f,x+w*1.18f,y-r,x-w*1.18f,y-r},frame);
        GachaArt.fill(b,m,new float[]{x-w*1.18f,y+r,x+w*1.18f,y+r,x+w*1.18f,y+r+r*.16f,x-w*1.18f,y+r+r*.16f},frame);
    }
    private void magnifier(DrawContext d,float x,float y,float r,int color){
        var m=d.getMatrices().peek().getPositionMatrix();var b=GachaArt.begin(d,false);
        float[] ring=new float[32];for(int i=0;i<16;i++){double a=i*Math.PI/8;ring[i*2]=x+(float)Math.cos(a)*r*.62f;ring[i*2+1]=y-r*.18f+(float)Math.sin(a)*r*.62f;}
        GachaArt.band(b,m,ring,r*.22f,color,color);
        line(b,m,x+r*.4f,y+r*.3f,x+r*.85f,y+r*.8f,r*.26f,color);
        GachaArt.end(b);
    }
    private void drawPull(net.minecraft.client.render.BufferBuilder b,Matrix4f m,float[] s,int kind,boolean can,boolean hover){
        int top=kind==0?0xff9dd2ff:0xffd598ff,bottom=kind==0?0xff6b95fe:0xffa451fd;
        if(hover){top=GachaArt.mix(top,0xffffffff,.18f);bottom=GachaArt.mix(bottom,0xffffffff,.08f);}
        if(!can){top=0xff5b6585;bottom=0xff434b68;}
        GachaArt.fill(b,m,s,top,bottom);
        // the draft's glassy sheen across the top
        float x0=s[10],y0=s[1],x1=s[4],y1=s[11];float h=y1-y0;
        GachaArt.fill(b,m,new float[]{s[0],y0,s[2],y0,s[2]-h*.1f,y0+h*.45f,s[0]-h*.1f,y0+h*.45f},GachaArt.alpha(0xffffff,can?64:20),GachaArt.alpha(0xffffff,0));
        GachaArt.band(b,m,s,-(x1-x0)*.004f-1.2f,can?(kind==0?0xffeef9ff:0xfff8eaff):0xff7d86a3,can?GachaArt.alpha(kind==0?0xffbfe3ff:0xffe0bfff,0):0x007d86a3);
        // the ticket pill (free for now)
        float cx=(x0+x1)/2,pw=(x1-x0)*.41f,ph=h*.33f,py=y0+h*.56f;
        float[] pill=slab(cx-pw/2,py,pw,ph,ph*.32f,ph*.12f);
        GachaArt.fill(b,m,pill,can?(kind==0?0xff143ca4:0xff3715af):0xff353c58);
    }
    private void pullLabel(DrawContext d,float[] s,String label,int kind,boolean can){
        float x0=s[10],x1=s[4],y0=s[1],h=s[11]-s[1],cx=(x0+x1)/2;
        GachaArt.text(d,label,cx,y0+h*.2f,h*.25f,can?(kind==0?0xff011b9b:0xff1d06a5):0xff9aa3c0,true,true,false);
        GachaArt.text(d,"무료",cx,y0+h*.56f+h*.33f/2-h*.19f/2*.9f,h*.19f,can?0xfff3f6ff:0xff9aa3c0,true,true,false);
    }

    // ---- details ----
    private void drawDetails(DrawContext d){
        // the main view's words are batched: put them down before the shade goes over them
        d.draw();d.fill(0,0,width,height,0xb3020414);d.draw();
        var m=d.getMatrices().peek().getPositionMatrix();var p=panel();float x=p[0],y=p[1],w=p[2],h=p[3];
        float[] box=slab(x,y,w,h,L(22),L(14));
        var b=GachaArt.begin(d,false);
        GachaArt.fill(b,m,box,0xf20b0f33,0xf2060822);GachaArt.band(b,m,box,L(1.8f),0xff5a4fe0,0xff5a4fe0);
        GachaArt.fill(b,m,new float[]{x+L(40),y+L(92),x+w-L(40),y+L(92),x+w-L(40),y+L(94),x+L(40),y+L(94)},0x996a5cff,0x226a5cff);
        boolean hc=inside(panelClose(),mx,my);float[] pc=panelClose();cross(b,m,(pc[0]+pc[2])/2,(pc[1]+pc[5])/2,L(14),L(2.4f),hc?0xffffffff:0xff7c80c8);
        // grade rows
        float rowY=y+L(160),rowH=L(62);
        for(int i=0;i<3;i++){int g=2-i;float ry=rowY+i*(rowH+L(10));
            float[] row=slab(x+L(50),ry,w-L(100),rowH,L(10),L(4));
            GachaArt.fill(b,m,row,0x661a1f52,0x66101438);GachaArt.band(b,m,row,L(1.2f),GachaArt.alpha(COLOR[g],140),GachaArt.alpha(COLOR[g],140));
            float[] chip=slab(x+L(66),ry+L(12),L(96),rowH-L(24),L(8),L(3));GachaArt.fill(b,m,chip,COLOR[g],GachaArt.mix(COLOR[g],0xff000000,.25f));
        }
        GachaArt.end(b);
        var g=GachaArt.begin(d,true);GachaArt.glow(g,m,box,L(30),0x664a3cff,1);GachaArt.end(g);
        float tx=x+L(56);
        GachaArt.text(d,"뽑기 상세",tx,y+L(40),L(36),0xffeef0ff,false,true,false);
        GachaArt.text(d,"등급별 확률",tx,y+L(118),L(24),0xff9fb0ff,false,true,false);
        String[] odds={"1%","5%","94%"},reward={"스탠드 카게노 · 바로 장착","보상 준비 중","보상 준비 중"};
        for(int i=0;i<3;i++){int gr=2-i;float ry=rowY+i*(rowH+L(10));
            GachaArt.text(d,NAME[gr],x+L(114),ry+rowH/2-L(12),L(26),0xff120a2a,true,true,false);
            GachaArt.text(d,odds[i],x+L(200),ry+rowH/2-L(14),L(30),COLOR[gr],false,true,true);
            GachaArt.text(d,reward[i],x+L(330),ry+rowH/2-L(11),L(24),gr==2?0xffffe9a8:0xffaab3d8,false,false,true);
        }
        float gy=rowY+3*(rowH+L(10))+L(18);
        GachaArt.text(d,"확정",tx,gy,L(24),0xff9fb0ff,false,true,false);
        GachaArt.text(d,"· 10회 안에 SR 이상이 반드시 1번 나와요. 10회 뽑기에는 SR 이상이 꼭 들어 있어요.",tx,gy+L(38),L(22),0xffdfe4ff,false,false,true);
        GachaArt.text(d,"· 100회 안에 SSR이 반드시 1번 나와요.",tx,gy+L(72),L(22),0xffdfe4ff,false,false,true);
        String mine=GachaClient.ready?"내 기록 · SSR 확정까지 "+GachaClient.count.toSsr()+"회 · SR 이상 확정까지 "+GachaClient.count.toSr()+"회 · 지금까지 "+GachaClient.count.pulls()+"회":"내 기록을 불러오는 중...";
        GachaArt.text(d,mine,tx,gy+L(118),L(22),0xff66bffc,false,true,true);
        String avg=String.format(Locale.ROOT,"확정까지 포함한 장기 평균 · SSR %.2f%% · SR %.2f%% · R %.2f%%",GachaRules.LONG_RUN[2]*100,GachaRules.LONG_RUN[1]*100,GachaRules.LONG_RUN[0]*100);
        GachaArt.text(d,avg,tx,gy+L(152),L(19),0xff8a93bd,false,false,true);
        GachaArt.text(d,"장착한 스탠드가 없어야 뽑을 수 있어요. 지금은 재화 없이 무료로 뽑아요.",tx,y+h-L(52),L(20),0xffaab3d8,false,false,true);
    }

    // ---- results ----
    private void drawResult(DrawContext d,float age){
        d.draw();d.fill(0,0,width,height,0x99020414);d.draw();
        var m=d.getMatrices().peek().getPositionMatrix();
        int best=grades.stream().mapToInt(Grade::ordinal).max().orElse(0);
        float in=Math.min(1,age/6f);
        var g=GachaArt.begin(d,true);
        // a wash of the best grade's light behind the cards
        float cx=width/2f,cy=height*.45f;float[] disc=new float[48];
        for(int i=0;i<24;i++){double a=i*Math.PI/12;disc[i*2]=cx+(float)Math.cos(a)*L(160);disc[i*2+1]=cy+(float)Math.sin(a)*L(110);}
        GachaArt.glow(g,m,disc,L(520),GLOW[best],.55f*in);
        GachaArt.end(g);
        if(grades.size()==1)drawSingle(d,grades.get(0),age);else drawTen(d,age);
        float okIn=Math.max(0,Math.min(1,(age-6)/5f));
        if(okIn>0){
            float[] ok=okButton();boolean hover=inside(ok,mx,my);
            var b=GachaArt.begin(d,false);GachaArt.fill(b,m,ok,GachaArt.alpha(hover?0xff7aa6ff:0xff5a7fe8,255*okIn),GachaArt.alpha(hover?0xff4f6fe0:0xff3a55c0,255*okIn));
            GachaArt.band(b,m,ok,-1.5f,GachaArt.alpha(0xffe8f0ff,220*okIn),GachaArt.alpha(0xffe8f0ff,0));GachaArt.end(b);
            float okY=ok[1],okH=ok[11]-ok[1];
            GachaArt.text(d,"확인",(ok[10]+ok[4])/2,okY+okH*.26f,okH*.42f,GachaArt.alpha(0xffffffff,255*okIn),true,true,false);
        }
        if(granted&&age>4){
            String won="카게노를 얻었어요! 스탠드가 바로 장착됐어요.";
            float[] gr=grid();float wy=grades.size()==1?B(178):gr[4]+2*gr[1]+gr[2]+L(64);
            GachaArt.text(d,won,width/2f,wy,L(28),GachaArt.alpha(0xffffe39a,255*Math.min(1,(age-4)/4f)),true,true,true);
        }
    }
    private void drawSingle(DrawContext d,Grade grade,float age){
        int g=grade.ordinal();float k=ease(Math.min(1,age/7f));
        if(grade==Grade.SSR){
            float w=Math.min(width-L(140),L(1060)),h=w*GachaArt.KAGENO_H/GachaArt.KAGENO_W,x=(width-w)/2,y=height*.43f-h/2;
            float sc=.92f+.08f*k;float cw=w*sc,ch=h*sc,cx0=width/2f-cw/2,cy0=y+h/2-ch/2;
            card(d,cx0,cy0,cw,ch,g,k,true);
            GachaArt.text(d,"카게노",cx0+L(36),cy0+ch-L(84),L(54),GachaArt.alpha(0xffffffff,255*k),false,true,true);
            GachaArt.text(d,"스탠드 · SSR",cx0+L(40),cy0+ch-L(28),L(24),GachaArt.alpha(0xffffe39a,255*k),false,true,true);
        }else{
            float w=L(380),h=L(500),sc=.9f+.1f*k,cw=w*sc,ch=h*sc,cx0=width/2f-cw/2,cy0=height*.43f-ch/2;
            card(d,cx0,cy0,cw,ch,g,k,false);
            GachaArt.text(d,"보상 준비 중",width/2f,cy0+ch-L(70),L(26),GachaArt.alpha(0xffd5dcff,255*k),true,false,true);
        }
    }
    /** The ten result cards' grid: card width and height, the gap, its left and top. */
    private float[] grid(){
        float cw=L(180),ch=L(246),gap=L(20);int cols=5;
        float total=cols*cw+(cols-1)*gap,scale=Math.min(1,(width-L(80))/total);cw*=scale;ch*=scale;gap*=scale;total*=scale;
        return new float[]{cw,ch,gap,(width-total)/2,height*.42f-ch-gap/2};
    }
    private void drawTen(DrawContext d,float age){
        float[] gr=grid();float cw=gr[0],ch=gr[1],gap=gr[2],x0=gr[3],y0=gr[4];int cols=5;
        for(int i=0;i<grades.size();i++){
            float k=ease(Math.max(0,Math.min(1,(age-i*1.6f)/5f)));if(k<=0)continue;
            float x=x0+(i%cols)*(cw+gap),y=y0+(i/cols)*(ch+gap)+(1-k)*L(30);
            card(d,x,y,cw,ch,grades.get(i).ordinal(),k,grades.get(i)==Grade.SSR);
        }
        int[] n=new int[3];for(var g:grades)n[g.ordinal()]++;
        String sum="SSR "+n[2]+"  ·  SR "+n[1]+"  ·  R "+n[0];
        GachaArt.text(d,sum,width/2f,y0+2*ch+gap+L(22),L(26),GachaArt.alpha(0xffdfe4ff,255*Math.min(1,age/8f)),true,true,true);
    }
    /** One result card: the Kageno illustration for an SSR, the grade's mark for the others. */
    private void card(DrawContext d,float x,float y,float w,float h,int g,float k,boolean art){
        var m=d.getMatrices().peek().getPositionMatrix();
        float[] box=slab(x,y,w,h,Math.min(w,h)*.05f,Math.min(w,h)*.03f);
        var b=GachaArt.begin(d,false);
        GachaArt.fill(b,m,box,GachaArt.alpha(0xff131740,240*k),GachaArt.alpha(0xff070920,240*k));
        GachaArt.end(b);
        if(art){
            // the face sits right of centre in the illustration: a narrow card shows that part
            float aspect=w/h,full=(float)GachaArt.KAGENO_W/GachaArt.KAGENO_H;
            float uw=aspect>=full?GachaArt.KAGENO_W:GachaArt.KAGENO_H*aspect,vh=aspect>=full?GachaArt.KAGENO_W/aspect:GachaArt.KAGENO_H;
            float u0=aspect>=full?0:Math.min(GachaArt.KAGENO_W-uw,Math.max(0,GachaArt.KAGENO_W*.66f-uw/2)),v0=(GachaArt.KAGENO_H-vh)/2;
            float inset=Math.min(w,h)*.035f;
            GachaArt.texture(d,GachaArt.KAGENO,x+inset,y+inset,w-2*inset,h-2*inset,u0,v0,uw,vh,GachaArt.KAGENO_W,GachaArt.KAGENO_H,k,false);
        }
        var l=GachaArt.begin(d,true);
        GachaArt.band(l,m,box,-Math.min(w,h)*.03f,GachaArt.alpha(COLOR[g],255*k),GachaArt.alpha(COLOR[g],0));
        GachaArt.glow(l,m,box,Math.min(w,h)*.16f,GLOW[g],(g==2?1.2f:.8f)*k);
        if(!art)hourglass(l,m,x+w/2,y+h*.42f,Math.min(w,h)*.2f,GachaArt.alpha(COLOR[g],255*k),GachaArt.alpha(COLOR[g],200*k),.7f);
        GachaArt.end(l);
        float size=Math.min(w,h)*(art?.16f:.2f);
        GachaArt.text(d,NAME[g],art?x+w-size*1.9f:x+w/2,y+Math.min(w,h)*.06f,size,GachaArt.alpha(COLOR[g],255*k),!art,true,true);
    }
    static float ease(float t){t=Math.max(0,Math.min(1,t));return 1-(1-t)*(1-t)*(1-t);}
    private void drawSkip(DrawContext d,float t){
        var m=d.getMatrices().peek().getPositionMatrix();float[] s=skipButton();boolean hover=inside(s,mx,my);
        var b=GachaArt.begin(d,false);GachaArt.fill(b,m,s,hover?0x99303a80:0x66101433);GachaArt.band(b,m,s,L(1.4f),0x887c86d8,0x887c86d8);GachaArt.end(b);
        GachaArt.text(d,"건너뛰기  ▶▶",(s[10]+s[4])/2,s[1]+(s[11]-s[1])*.3f,(s[11]-s[1])*.4f,hover?0xffffffff:0xffc5cbf2,true,true,false);
    }
}
