package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.ProgressClient;
import dev.sangwoo.celestialfall.client.menu.MenuClient;
import dev.sangwoo.celestialfall.client.gacha.GachaFont.Weight;
import dev.sangwoo.celestialfall.gacha.GachaRules;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/**
 * The stand gacha (user request; 2.40.0 laid out after the user's second mockup): the Kageno banner covering the screen,
 * the BOTAND logo, the count to the next certain SSR where the mockup has its currency, a details button and the 1- and
 * 10-pull buttons. A pull plays its scene ({@link GachaStage}: Bomi and her hourglasses) and then shows what came
 * ({@link GachaResults}). Controls are placed in the mockup's own pixels ({@link #u} GUI units each), held to the
 * screen's edges as there; the banner's art covers the screen behind them.
 */
public final class GachaScreen extends Screen {
    enum Stage {MAIN,SCENE,RESULT}
    /** The grades' colours (user request): R sky blue, SR purple, SSR yellow. */
    static final int[] COLOR={0xff74d8ff,0xffb978ff,0xffffd447},GLOW={0xff2fb4ff,0xff9447ff,0xffffae1a};
    static final String[] NAME={"R","SR","SSR"};
    static final int DESIGN_W=GachaArt.BANNER_W,DESIGN_H=GachaArt.BANNER_H;
    private Stage stage=Stage.MAIN;
    private boolean details;
    private List<Grade> grades=List.of();
    private boolean granted;
    private int ticks;
    private float sceneAt,resultAt,openedAt=-1,detailsAt=-100,pressedAt=-100;
    private int pressed=-1;
    float u;
    private double mx=-1,my=-1;
    private String hovered="";
    public GachaScreen(){super(Text.literal("스탠드 뽑기"));}
    @Override public boolean shouldPause(){return false;}
    @Override protected void init(){u=Math.min(width/(float)DESIGN_W,height/(float)DESIGN_H);}
    /**
     * Ticks since the screen opened, running smoothly between ticks (the scene is timed by it, like every cast). The
     * screen counts them itself: the world's time is set again from the server every second and can step a tick there,
     * which made the scene skip 50 ms.
     */
    float clock(){return ticks+client.getTickDelta();}
    Stage stage(){return stage;}

    @Override public void tick(){ticks++;if(!MenuClient.available())client.setScreen(null);}
    @Override public void close(){
        if(details){details=false;GachaSound.play(GachaSound.Cue.BACK);return;}
        if(stage==Stage.SCENE){reveal(true);return;}
        if(stage==Stage.RESULT){toMain();return;}
        back();
    }
    @Override public void removed(){GachaSound.stopAll();}
    /** Back to the menu's dock, where the gacha was opened from. */
    private void back(){GachaSound.play(GachaSound.Cue.BACK);client.setScreen(null);MenuClient.open();}
    private void toMain(){stage=Stage.MAIN;openedAt=clock()-14;GachaSound.play(GachaSound.Cue.BACK);}
    /** The server's answer: what was rolled (nothing when it only sent the count or refused, its notice saying why). */
    void answered(List<Grade> rolled,boolean equipped){
        if(rolled.isEmpty())return;
        grades=rolled;granted=equipped;details=false;stage=Stage.SCENE;sceneAt=clock();
        GachaStage.begin(this,rolled);
    }
    /**
     * The scene ends (or is skipped, its sounds cut short with it): the results. Its GPU resources stay for the next pull
     * until the screen closes.
     */
    private void reveal(boolean skipped){stage=Stage.RESULT;resultAt=clock();if(skipped)GachaStage.stop();GachaResults.begin(grades);}
    boolean canPull(){return GachaClient.ready&&!GachaClient.pending&&GachaRules.canPull(ProgressClient.profile.stand());}
    double mouseX(){return mx;}
    double mouseY(){return my;}

    // ---- layout, in the mockup's pixels from the screen's edges ----
    float L(float x){return x*u;}
    /** Mockup x measured against its right edge. */
    float R(float x){return width-(DESIGN_W-x)*u;}
    float T(float y){return y*u;}
    /** Mockup y measured against its bottom edge. */
    float B(float y){return height-(DESIGN_H-y)*u;}
    private float[] pullOne(){return GachaArt.slab(R(925),B(766),L(347),L(118),L(24),L(10));}
    private float[] pullTen(){return GachaArt.slab(R(1281),B(766),L(347),L(118),L(24),L(10));}
    private float[] detailButton(){return GachaArt.slab(L(56),B(772),L(180),L(60),L(24),L(8));}
    private float[] closeBox(){float c=L(26);return GachaArt.rect(R(1608)-c,T(47)-c,2*c,2*c);}
    private float[] countBox(){return GachaArt.slab(R(1246),T(22),L(268),L(51),L(0),L(9));}
    // details panel and result buttons
    private float[] panel(){float w=Math.min(width-L(80),L(1040)),h=Math.min(height-L(50),L(760));return new float[]{(width-w)/2,(height-h)/2,w,h};}
    private float[] panelClose(){var p=panel();float c=L(24),x=p[0]+p[2]-L(52),y=p[1]+L(50);return GachaArt.rect(x-c,y-c,2*c,2*c);}
    private float[] okButton(){return GachaResults.okButton(this);}
    private float[] skipButton(){return GachaArt.slab(R(1452),T(26),L(176),L(50),L(14),L(7));}

    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return super.mouseClicked(x,y,button);
        float t=clock();
        if(stage==Stage.SCENE){if(GachaArt.inside(skipButton(),x,y)){GachaSound.play(GachaSound.Cue.CLICK);reveal(true);}return true;}
        if(stage==Stage.RESULT){
            float age=t-resultAt;
            if(GachaResults.done(grades,age)){if(GachaArt.inside(okButton(),x,y)){GachaSound.play(GachaSound.Cue.CLICK);toMain();}}
            else if(GachaResults.oneByOne(grades)&&GachaArt.inside(skipButton(),x,y)){GachaSound.play(GachaSound.Cue.CLICK);GachaResults.all(grades,age);}
            else resultAt-=GachaResults.click(grades,age);
            return true;
        }
        if(details){var p=panel();if(GachaArt.inside(panelClose(),x,y)||x<p[0]||y<p[1]||x>p[0]+p[2]||y>p[1]+p[3]){details=false;GachaSound.play(GachaSound.Cue.BACK);}return true;}
        if(GachaArt.inside(closeBox(),x,y)){back();return true;}
        if(GachaArt.inside(detailButton(),x,y)){details=true;detailsAt=t;GachaSound.play(GachaSound.Cue.PANEL);return true;}
        for(int n:new int[]{GachaRules.SINGLE,GachaRules.TEN})if(GachaArt.inside(n==1?pullOne():pullTen(),x,y)){
            if(canPull()){pressed=n;pressedAt=t;GachaSound.play(GachaSound.Cue.PULL);GachaClient.request(n);}
            else GachaSound.play(GachaSound.Cue.DENIED);
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
            if(age>=GachaStage.length(grades))reveal(false);
            else drawSkip(d);
            return;
        }
        if(stage==Stage.RESULT){
            float age=t-resultAt;
            GachaResults.draw(d,this,grades,granted,age);
            // a ten-pull's cards one at a time can be skipped to all ten
            if(GachaResults.oneByOne(grades))drawSkip(d);
            else hover(GachaResults.done(grades,age)&&GachaArt.inside(okButton(),mx,my)?"ok":"");
            return;
        }
        if(openedAt<0){openedAt=t;GachaSound.play(GachaSound.Cue.OPEN);}
        float age=t-openedAt;
        drawBanner(d,age);
        drawMain(d,t,age);
        // once the screen has settled, ready the scenes' renderer a piece a frame
        if(age>16){d.draw();GachaStage.prepare();}
        if(details)drawDetails(d,t);
        hover(details?(GachaArt.inside(panelClose(),mx,my)?"panelClose":""):mainHover());
    }
    private String mainHover(){
        if(GachaArt.inside(closeBox(),mx,my))return "close";
        if(GachaArt.inside(detailButton(),mx,my))return "detail";
        if(GachaArt.inside(pullOne(),mx,my))return "one";
        if(GachaArt.inside(pullTen(),mx,my))return "ten";
        return "";
    }
    /** A soft tick when the pointer comes onto a control. */
    void hover(String now){if(!now.equals(hovered)){if(!now.isEmpty())GachaSound.play(GachaSound.Cue.HOVER);hovered=now;}}

    // ---- the banner ----
    private void drawBanner(DrawContext d,float age){
        float in=GachaArt.ramp(0,7,age),zoom=1+.06f*(1-GachaArt.ease(age/34f))+.006f*(float)Math.sin(age*.021f);
        var p=GachaBanner.place(width,height,zoom,width*.62f,height*.42f);
        d.fill(0,0,width,height,0xff03030c);
        GachaBanner.art(d,p,in);
        float cut=GachaArt.ease((age-6)/14f),land=age>20?.85f*(float)Math.exp(-(age-20)/7f):0;
        float glow=land+.05f+.05f*(float)Math.sin(age*.09f)+.45f*GachaBanner.flicker(age,11);
        float shine=age<40?-1:((age-40)%110)/24f;
        GachaBanner.title(d,p,cut,glow*in,shine,in);
        GachaBanner.sub(d,p,GachaArt.ramp(17,28,age),1+.3f*(1-GachaArt.ease((age-17)/12f)));
        float pulse=(float)Math.sin(age*.07f);
        GachaBanner.eyes(d,p,in*(.12f+.2f*pulse*pulse+.75f*GachaBanner.flicker(age+3,47)));
        GachaBanner.motes(d,width,height,age,in*.9f,46,0xffa45cff,0xffe3c9ff,5);
        // the top band's shade, so the controls read over the brightest art
        var m=d.getMatrices().peek().getPositionMatrix();var b=GachaArt.begin(d,false);
        GachaArt.fill(b,m,GachaArt.rect(0,0,width,T(112)),GachaArt.alpha(0x02020c,120*in),GachaArt.alpha(0x02020c,0));
        GachaArt.fill(b,m,GachaArt.rect(0,B(700),width,height-B(700)),GachaArt.alpha(0x030312,0),GachaArt.alpha(0x030312,150*in));
        GachaArt.end(b);
    }

    // ---- main view ----
    private void drawMain(DrawContext d,float t,float age){
        var ms=d.getMatrices();
        boolean can=canPull();
        // the logo: its light settling down into the band, a shine passing through it now and then
        float top=GachaArt.ease((age-4)/10f);
        ms.push();ms.translate(0,-(1-top)*L(16),0);
        GachaArt.texture(d,GachaArt.LOGO,L(GachaArt.LOGO_AT[0]),T(GachaArt.LOGO_AT[1]),L(GachaArt.LOGO_AT[2]),L(GachaArt.LOGO_AT[3]),top,true);
        float lshine=((age+30)%160)/18f;
        if(lshine<1)GachaArt.texture(d,GachaArt.LOGO,L(GachaArt.LOGO_AT[0]),T(GachaArt.LOGO_AT[1]),L(GachaArt.LOGO_AT[2]),L(GachaArt.LOGO_AT[3]),top*.55f*(float)Math.sin(Math.PI*lshine),true);
        drawCount(d,top,t);
        drawClose(d,top);
        ms.pop();
        // the details button, from the left
        float left=GachaArt.ease((age-10)/12f);
        ms.push();ms.translate(-(1-left)*L(70),0,0);
        drawDetailButton(d,left,!details&&hovered.equals("detail"));
        ms.pop();
        // the pull buttons, rising in one after the other
        for(int k=0;k<2;k++){
            float rise=GachaArt.ease((age-12-k*3)/12f);
            ms.push();ms.translate(0,(1-rise)*L(60),0);
            drawPull(d,k==0?pullOne():pullTen(),k,can,!details&&hovered.equals(k==0?"one":"ten"),rise,age,t);
            ms.pop();
        }
        // why the buttons are dark, or what the server said
        String note=!GachaRules.canPull(ProgressClient.profile.stand())?"장착한 스탠드가 없어야 뽑을 수 있어요.":GachaClient.pending?"서버에서 확인하는 중...":GachaClient.notice;
        if(!note.isEmpty())GachaFont.text(note,R(1627),B(732),L(19),Weight.BOLD).align(1).color(!can&&!GachaClient.pending?0xffffb9cf:0xffd9dcff)
            .shadow(0xe0000008,L(5),0,L(1.5f)).alpha(GachaArt.ramp(14,22,age)).draw(d);
    }
    /** The count to the next certain SSR, in the mockup's currency box: an hourglass, the count, and how far along it is. */
    private void drawCount(DrawContext d,float in,float t){
        if(in<=0)return;
        var m=d.getMatrices().peek().getPositionMatrix();float[] box=countBox();float x=box[10],y=box[1],w=L(268),h=L(51);
        var b=GachaArt.begin(d,false);
        GachaArt.smooth(b,m,box,GachaArt.fade(0xe00a0e30,in),GachaArt.fade(0xe0060820,in));
        GachaArt.stroke(b,m,box,L(1.3f),GachaArt.fade(0xff3442a8,in));
        GachaArt.end(b);
        var count=GachaClient.count;boolean ready=GachaClient.ready;
        float fill=ready?1-count.toSsr()/(float)GachaRules.SSR_PITY:0;
        var g=GachaArt.begin(d,true);
        GachaArt.glow(g,m,box,L(14),0x333a66ff,in);
        hourglass(g,m,x+L(31),y+h/2,L(14),GachaArt.fade(0xff7fe9ff,in),GachaArt.fade(0xcc56d9ff,in),.35f+.5f*fill);
        // how far toward the certain SSR: a bar along the box's foot
        float bx0=x+L(14),bx1=x+w-L(14),by=y+h-L(7);
        GachaArt.fill(g,m,GachaArt.rect(bx0,by,bx1-bx0,L(2.4f)),GachaArt.fade(0x26ffffff,in));
        if(fill>0)GachaArt.fillAcross(g,m,GachaArt.rect(bx0,by,(bx1-bx0)*fill,L(2.4f)),GachaArt.fade(0xff38e1ff,in),GachaArt.fade(0xffb77cff,in));
        GachaArt.end(g);
        String value=ready?count.toSsr()+"회":"…";
        GachaFont.text("SSR 확정까지",x+L(56),y+h/2-L(1),L(16),Weight.MEDIUM).color(0xffb7bff2).alpha(in).draw(d);
        GachaFont.text(value,x+w-L(20),y+h/2-L(2),L(27),Weight.BOLD).align(1).color(0xff5ae6f2).glow(0xff2fc8e8,L(6),.55f).alpha(in).draw(d);
    }
    private void drawClose(DrawContext d,float in){
        if(in<=0)return;
        boolean hover=hovered.equals("close");
        var m=d.getMatrices().peek().getPositionMatrix();float cx=R(1608),cy=T(47),r=L(16)*(hover?1.1f:1);
        var g=GachaArt.begin(d,true);
        cross(g,m,cx,cy,r,L(6),GachaArt.fade(hover?0x66b9a6ff:0x337f72ff,in));
        GachaArt.end(g);
        var b=GachaArt.begin(d,false);
        cross(b,m,cx,cy,r,L(2.3f),GachaArt.fade(hover?0xffffffff:0xffe2defa,in));
        GachaArt.end(b);
    }
    private void drawDetailButton(DrawContext d,float in,boolean hover){
        if(in<=0)return;
        var m=d.getMatrices().peek().getPositionMatrix();float[] s=detailButton();float x=s[10],y=s[1],h=s[7]-s[1];
        var b=GachaArt.begin(d,false);
        GachaArt.smooth(b,m,s,GachaArt.fade(hover?0xf0221f66:0xe8120f42,in),GachaArt.fade(hover?0xf0181450:0xe80b0a2c,in));
        GachaArt.stroke(b,m,s,L(1.8f),GachaArt.fade(hover?0xffe07bff:0xffc23bff,in));
        GachaArt.end(b);
        var g=GachaArt.begin(d,true);
        GachaArt.glow(g,m,s,L(hover?26:16),GachaArt.fade(0xffb22cff,in*(hover?.62f:.34f)),1);
        GachaArt.band(g,m,s,-L(5),GachaArt.fade(0x55c94dff,in),GachaArt.fade(0x00c94dff,in));
        GachaArt.end(g);
        magnifier(d,x+L(52),y+h/2,L(12),GachaArt.fade(0xffe6e2ff,in));
        GachaFont.text("상세",x+L(76),y+h/2,L(27),Weight.BOLD).color(GachaArt.fade(0xfff4f2ff,in)).draw(d);
    }
    /** A pull button: glassy sky blue (1) or violet (10), its neon rim and glow, its label and ticket pill, a passing shine. */
    private void drawPull(DrawContext d,float[] s,int kind,boolean can,boolean hover,float in,float age,float t){
        if(in<=0)return;
        var ms=d.getMatrices();
        float x0=s[10],x1=s[4],y0=s[1],y1=s[7],h=y1-y0,cx=(x0+x1)/2,cy=(y0+y1)/2;
        boolean down=pressed==(kind==0?1:10)&&t-pressedAt<5;
        float press=down?1-.035f*(float)Math.sin(Math.PI*(t-pressedAt)/5f):hover?1.02f:1;
        ms.push();ms.translate(cx,cy,0);ms.scale(press,press,1);ms.translate(-cx,-cy,0);
        Matrix4f m=ms.peek().getPositionMatrix();
        // the mockup's own colours: sky blue (#98d2fd into #72b0fd) and violet (#d979fc into #b45bfc), rims pale cyan and pink
        int top=kind==0?0xffa4d8fe:0xffde86fc,bottom=kind==0?0xff70acfd:0xffb158fb,rim=kind==0?0xffd2f8ff:0xffffb4ff,halo=kind==0?0xff3474ff:0xffa13cff;
        if(hover){top=GachaArt.mix(top,0xffffffff,.2f);bottom=GachaArt.mix(bottom,0xffffffff,.1f);}
        if(!can){top=0xff606a8a;bottom=0xff434a66;rim=0xff8b93b0;}
        float pulse=.82f+.18f*(float)Math.sin(t*.16f+kind*1.7f);
        if(can){
            var g=GachaArt.begin(d,true);
            GachaArt.glow(g,m,s,L(hover?46:36),GachaArt.fade(halo,in),(hover?1.25f:.95f)*pulse);
            GachaArt.end(g);
        }
        var b=GachaArt.begin(d,false);
        GachaArt.smooth(b,m,s,GachaArt.fade(top,in),GachaArt.fade(bottom,in));
        // the glass: a sheen over the top half and a soft darkening into the foot
        float[] sheen=GachaArt.clip(GachaArt.rect(x0-10,y0,x1-x0+20,h*.46f),s),foot=GachaArt.clip(GachaArt.rect(x0-10,y0+h*.62f,x1-x0+20,h*.4f),s);
        if(sheen.length>=6)GachaArt.fill(b,m,sheen,GachaArt.fade(0x55ffffff,in*(can?1:.3f)),GachaArt.fade(0x00ffffff,in));
        if(foot.length>=6)GachaArt.fill(b,m,foot,GachaArt.fade(0x00000020,in),GachaArt.fade(0x2a000020,in));
        GachaArt.end(b);
        var g=GachaArt.begin(d,true);
        GachaArt.band(g,m,s,-L(7),GachaArt.fade(0x77ffffff,in*(can?1:.3f)),GachaArt.fade(0x00ffffff,in));
        GachaArt.end(g);
        b=GachaArt.begin(d,false);
        GachaArt.stroke(b,m,s,L(2f),GachaArt.fade(rim,in));
        // the ticket pill
        float pw=L(156),ph=L(36),px=cx-pw/2,py=y0+h*.555f;float[] pill=GachaArt.slab(px,py,pw,ph,L(11),L(4));
        GachaArt.smooth(b,m,pill,GachaArt.fade(can?(kind==0?0xff1e3ec4:0xff3a16c6):0xff3a4160,in),GachaArt.fade(can?(kind==0?0xff15309e:0xff2a0ca2):0xff2c3249,in));
        GachaArt.stroke(b,m,pill,L(1f),GachaArt.fade(can?(kind==0?0x995c7cff:0x99a070ff):0x667d86a3,in));
        GachaArt.end(b);
        ticket(d,px+L(52),py+ph/2,L(12),in,can);
        // a shine sweeping across the glass every few seconds
        float period=150,sweep=((age-26-kind*6)%period+period)%period/16f;
        if(can&&age>26&&sweep<1){
            float sx=x0-h+sweep*(x1-x0+2*h);
            float[] cut=GachaArt.clip(new float[]{sx,y0,sx+L(34),y0,sx+L(34)-h*.5f,y1,sx-h*.5f,y1},s);
            if(cut.length>=6){var sg=GachaArt.begin(d,true);GachaArt.fillAcross(sg,m,cut,GachaArt.fade(0x00ffffff,in),GachaArt.fade(0x88ffffff,in));GachaArt.end(sg);}
        }
        String label=kind==0?"1회 뽑기":"10회 뽑기";int ink=can?(kind==0?0xff15279a:0xff2a0db4):0xffa3abc6;
        GachaFont.text(label,cx,y0+h*.3f,L(33),Weight.BOLD).color(GachaArt.fade(ink,in)).shadow(GachaArt.fade(can?0x66ffffff:0,in),L(2),0,L(1.2f)).align(.5f).draw(d);
        GachaFont.text("무료",px+L(98),py+ph/2,L(20),Weight.BOLD).align(.5f).color(GachaArt.fade(can?0xffffffff:0xffb2b9cf,in)).draw(d);
        ms.pop();
    }
    /** The pill's ticket: a tilted pass, pale blue to white, with its own inner frame. */
    private void ticket(DrawContext d,float x,float y,float r,float in,boolean can){
        var ms=d.getMatrices();ms.push();ms.translate(x,y,0);ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-22));
        Matrix4f m=ms.peek().getPositionMatrix();float w=r*1.5f,h=r;
        var b=GachaArt.begin(d,false);
        GachaArt.smooth(b,m,GachaArt.slab(-w,-h,2*w,2*h,0,r*.22f),GachaArt.fade(can?0xfff4fbff:0xffaeb6cc,in),GachaArt.fade(can?0xff9ec9ff:0xff7f879e,in));
        GachaArt.stroke(b,m,GachaArt.rect(-w*.62f,-h*.5f,w*1.24f,h),r*.16f,GachaArt.fade(can?0xff5a6cf0:0xff737a92,in));
        GachaArt.fill(b,m,GachaArt.rect(-w*.42f,-h*.12f,w*.84f,h*.24f),GachaArt.fade(can?0xff8a63ff:0xff737a92,in));
        GachaArt.end(b);
        ms.pop();
    }

    static void cross(BufferBuilder b,Matrix4f m,float x,float y,float r,float w,int color){
        GachaArt.line(b,m,x-r,y-r,x+r,y+r,w,color);GachaArt.line(b,m,x-r,y+r,x+r,y-r,w,color);
    }
    /** An hourglass mark: two glass bulbs meeting at a waist, sand {@code fill} of the way down. */
    static void hourglass(BufferBuilder b,Matrix4f m,float x,float y,float r,int frame,int sand,float fill){
        float w=r*.62f,neck=r*.09f;
        GachaArt.fill(b,m,new float[]{x-w,y-r,x+w,y-r,x+neck,y,x-neck,y},GachaArt.fade(frame,.25f));
        GachaArt.fill(b,m,new float[]{x-neck,y,x+neck,y,x+w,y+r,x-w,y+r},GachaArt.fade(frame,.25f));
        float s=r*Math.max(0,Math.min(1,fill));GachaArt.fill(b,m,new float[]{x-w*s/r,y+r-s,x+w*s/r,y+r-s,x+w,y+r,x-w,y+r},sand);
        GachaArt.fill(b,m,new float[]{x-w*1.18f,y-r-r*.16f,x+w*1.18f,y-r-r*.16f,x+w*1.18f,y-r,x-w*1.18f,y-r},frame);
        GachaArt.fill(b,m,new float[]{x-w*1.18f,y+r,x+w*1.18f,y+r,x+w*1.18f,y+r+r*.16f,x-w*1.18f,y+r+r*.16f},frame);
    }
    /** A magnifying glass with a plus in it. */
    private void magnifier(DrawContext d,float x,float y,float r,int color){
        var m=d.getMatrices().peek().getPositionMatrix();var b=GachaArt.begin(d,false);
        float cx=x-r*.12f,cy=y-r*.12f,rr=r*.7f;
        GachaArt.stroke(b,m,GachaArt.circle(cx,cy,rr,28),r*.2f,color);
        GachaArt.line(b,m,cx+rr*.72f,cy+rr*.72f,x+r*.95f,y+r*.95f,r*.26f,color);
        GachaArt.line(b,m,cx-rr*.45f,cy,cx+rr*.45f,cy,r*.16f,color);GachaArt.line(b,m,cx,cy-rr*.45f,cx,cy+rr*.45f,r*.16f,color);
        GachaArt.end(b);
    }

    // ---- details ----
    private void drawDetails(DrawContext d,float t){
        float in=GachaArt.ease((t-detailsAt)/6f);
        d.draw();d.fill(0,0,width,height,GachaArt.alpha(0x020310,205*in));d.draw();
        var ms=d.getMatrices();var p=panel();float x=p[0],y=p[1],w=p[2],h=p[3];
        float k=.965f+.035f*in;ms.push();ms.translate(width/2f,height/2f,0);ms.scale(k,k,1);ms.translate(-width/2f,-height/2f,0);
        Matrix4f m=ms.peek().getPositionMatrix();
        float[] box=GachaArt.slab(x,y,w,h,L(20),L(14));
        var g=GachaArt.begin(d,true);GachaArt.glow(g,m,box,L(34),GachaArt.fade(0xff5b2cff,in*.3f),1);GachaArt.end(g);
        var b=GachaArt.begin(d,false);
        GachaArt.smooth(b,m,box,GachaArt.fade(0xf70d0f34,in),GachaArt.fade(0xf7060719,in));
        GachaArt.stroke(b,m,box,L(1.6f),GachaArt.fade(0xff7656ff,in));
        // the header's rule, magenta into cyan
        GachaArt.fillAcross(b,m,GachaArt.rect(x+L(44),y+L(104),w-L(88),L(2)),GachaArt.fade(0xffd04bff,in),GachaArt.fade(0x0040e0ff,in));
        boolean hc=GachaArt.inside(panelClose(),mx,my);float[] pc=panelClose();
        cross(b,m,(pc[0]+pc[2])/2,(pc[1]+pc[5])/2,L(14),L(2.4f),GachaArt.fade(hc?0xffffffff:0xff9ea2dc,in));
        GachaArt.end(b);
        float tx=x+L(56);
        GachaFont.text("뽑기 상세",tx,y+L(56),L(36),Weight.BOLD).color(GachaArt.fade(0xfff3f1ff,in)).draw(d);
        GachaFont.text("카게노 픽업 · 스탠드 뽑기",tx+GachaFont.width("뽑기 상세",L(36),Weight.BOLD)+L(22),y+L(60),L(19),Weight.MEDIUM).color(GachaArt.fade(0xffa69fe0,in)).draw(d);
        // the grades: a crest chip with its stars, the odds, the reward
        String[] odds={"1%","5%","94%"},reward={"스탠드 카게노 · 바로 장착","보상 준비 중","로카카카 · 스탠드를 초기화하는 열매"};
        float rowY=y+L(136),rowH=L(70),gap=L(12);
        for(int i=0;i<3;i++){
            int gr=2-i;float ry=rowY+i*(rowH+gap),slide=GachaArt.ease((t-detailsAt-3-i*2)/7f);
            float[] row=GachaArt.slab(x+L(44),ry,w-L(88),rowH,L(10),L(5));
            b=GachaArt.begin(d,false);
            GachaArt.smooth(b,m,row,GachaArt.fade(0x881a1d50,in*slide),GachaArt.fade(0x88101336,in*slide));
            GachaArt.stroke(b,m,row,L(1.2f),GachaArt.fade(GachaArt.alpha(COLOR[gr],150),in*slide));
            float[] chip=GachaArt.slab(x+L(62),ry+L(13),L(104),rowH-L(26),L(9),L(3));
            GachaArt.smooth(b,m,chip,GachaArt.fade(GachaArt.mix(COLOR[gr],0xffffffff,.35f),in*slide),GachaArt.fade(GLOW[gr],in*slide));
            GachaArt.end(b);
            stars(d,x+L(250),ry+rowH/2,L(8.5f),gr+3,COLOR[gr],in*slide,1);
            GachaFont.text(NAME[gr],x+L(114),ry+rowH/2,L(26),Weight.HEAVY).align(.5f).color(GachaArt.fade(0xff140a2c,in*slide)).draw(d);
            GachaFont.text(odds[i],x+L(440),ry+rowH/2,L(33),Weight.HEAVY).align(1).color(GachaArt.fade(COLOR[gr],in*slide)).glow(GLOW[gr],L(7),.45f).draw(d);
            GachaFont.text(reward[i],x+L(470),ry+rowH/2,L(21),Weight.MEDIUM).color(GachaArt.fade(gr==2?0xffffe9a8:0xffb5bce0,in*slide)).draw(d);
        }
        float gy=rowY+3*(rowH+gap)+L(22);
        GachaFont.text("확정 규칙",tx,gy,L(22),Weight.BOLD).color(GachaArt.fade(0xffa9b6ff,in)).draw(d);
        GachaFont.text("· 10회 안에 SR 이상이 반드시 1번 나와요. 10회 뽑기에는 SR 이상이 꼭 들어 있어요.",tx,gy+L(36),L(19),Weight.MEDIUM).color(GachaArt.fade(0xffe3e6ff,in)).draw(d);
        GachaFont.text("· 100회 안에 SSR이 반드시 1번 나와요.",tx,gy+L(66),L(19),Weight.MEDIUM).color(GachaArt.fade(0xffe3e6ff,in)).draw(d);
        // my record, as bars toward each certainty
        float ry=gy+L(116);
        GachaFont.text("내 기록",tx,ry,L(22),Weight.BOLD).color(GachaArt.fade(0xffa9b6ff,in)).draw(d);
        var c=GachaClient.count;boolean ready=GachaClient.ready;
        meter(d,tx,ry+L(40),w-L(112),"SSR 확정까지",ready?c.toSsr()+"회":"…",ready?1-c.toSsr()/(float)GachaRules.SSR_PITY:0,0xffffd447,0xffffa21a,in,t);
        meter(d,tx,ry+L(80),w-L(112),"SR 이상 확정까지",ready?c.toSr()+"회":"…",ready?1-c.toSr()/(float)GachaRules.SR_PITY:0,0xffd2a0ff,0xff9447ff,in,t);
        String total=ready?"지금까지 "+c.pulls()+"회 뽑았어요.":"내 기록을 불러오는 중...";
        String avg=String.format(Locale.ROOT,"확정까지 포함한 장기 평균 · SSR %.2f%% · SR %.2f%% · R %.2f%%",GachaRules.LONG_RUN[2]*100,GachaRules.LONG_RUN[1]*100,GachaRules.LONG_RUN[0]*100);
        GachaFont.text(total,tx,ry+L(118),L(18),Weight.MEDIUM).color(GachaArt.fade(0xff7fe3f4,in)).draw(d);
        GachaFont.text(avg,tx,ry+L(146),L(16),Weight.MEDIUM).color(GachaArt.fade(0xff8d95c2,in)).draw(d);
        GachaFont.text("장착한 스탠드가 없어야 뽑을 수 있어요. 지금은 재화 없이 무료로 뽑아요.",tx,y+h-L(40),L(17),Weight.MEDIUM).color(GachaArt.fade(0xffa9b0d6,in)).draw(d);
        ms.pop();
    }
    /** A labelled bar filling toward a certainty. */
    private void meter(DrawContext d,float x,float y,float w,String label,String value,float fill,int c0,int c1,float in,float t){
        float lw=L(210),bx=x+lw,bw=w-lw-L(90),bh=L(10);
        GachaFont.text(label,x,y,L(18),Weight.MEDIUM).color(GachaArt.fade(0xffdfe2ff,in)).draw(d);
        var m=d.getMatrices().peek().getPositionMatrix();
        float[] track=GachaArt.slab(bx,y-bh/2,bw,bh,L(4),0),bar=fill>0?GachaArt.clip(GachaArt.rect(bx-L(4),y-bh,bw*fill+L(4),bh*2),track):new float[0];
        var b=GachaArt.begin(d,false);
        GachaArt.smooth(b,m,track,GachaArt.fade(0x40ffffff,in),GachaArt.fade(0x26ffffff,in));
        if(bar.length>=6)GachaArt.fillAcross(b,m,bar,GachaArt.fade(c0,in),GachaArt.fade(c1,in));
        GachaArt.end(b);
        if(bar.length>=6){var g=GachaArt.begin(d,true);GachaArt.glow(g,m,bar,L(10),GachaArt.fade(c1,in*.6f),.8f+.2f*(float)Math.sin(t*.2f));GachaArt.end(g);}
        GachaFont.text(value,x+w,y,L(21),Weight.BOLD).align(1).color(GachaArt.fade(c0,in)).draw(d);
    }
    /** {@code n} stars in a row centred on {@code (x, y)}, {@code show} (0..1) of the way through popping in. */
    static void stars(DrawContext d,float x,float y,float r,int n,int color,float alpha,float show){
        var m=d.getMatrices().peek().getPositionMatrix();float step=r*2.35f,x0=x-(n-1)*step/2;
        var g=GachaArt.begin(d,true);
        for(int i=0;i<n;i++){float k=GachaArt.back(show*n-i);if(k<=0)continue;float rr=r*k;
            GachaArt.glow(g,m,GachaArt.circle(x0+i*step,y,rr*.6f,14),rr*1.6f,GachaArt.fade(color,alpha*.6f),1);}
        GachaArt.end(g);
        var b=GachaArt.begin(d,false);
        for(int i=0;i<n;i++){float k=GachaArt.back(show*n-i);if(k<=0)continue;float rr=r*k;
            GachaArt.fillStar(b,m,x0+i*step,y,rr,rr*.45f,5,GachaArt.fade(0xffffffff,alpha),GachaArt.fade(color,alpha));}
        GachaArt.end(b);
    }

    private void drawSkip(DrawContext d){
        var m=d.getMatrices().peek().getPositionMatrix();float[] s=skipButton();boolean hover=GachaArt.inside(s,mx,my);
        hover(hover?"skip":"");
        var b=GachaArt.begin(d,false);
        GachaArt.smooth(b,m,s,hover?0xb0262c74:0x800c0f2e,hover?0xb01a1d52:0x80070920);
        GachaArt.stroke(b,m,s,L(1.3f),hover?0xffa9b2ff:0x996670c8);
        GachaArt.end(b);
        GachaFont.text("건너뛰기  ▶▶",(s[10]+s[4])/2,(s[1]+s[7])/2,L(21),Weight.BOLD).align(.5f).color(hover?0xffffffff:0xffd2d7f6).shadow(0x99000008,L(3),0,L(1)).draw(d);
    }
}
