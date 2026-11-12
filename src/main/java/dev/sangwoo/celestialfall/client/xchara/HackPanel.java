package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.xchara.XChara;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import static dev.sangwoo.celestialfall.client.xchara.HackLook.*;

/**
 * The hacking panel in X Chara's other hand while their hack is on (2.115.0, user request: "다른 손에는 해킹 패널이 생겨",
 * and the user's video: a dark flat slab within a glowing purple rim at their other hand, purple writing on it), drawn as
 * the item in that hand (XChara.PANEL through HackMode.panel: so a hand in the world and one's own eyes both have it).
 * <p>
 * A thin slab, dark on its faces and purple on its sides, a rim of light round its face and a soft glow past it; on its
 * face (what the video is too small to show, so the hacker's console): a title bar, a line filling as each line is
 * typed, rows of writing typed a character at a time and moved up as each is done (HackLook: every moment a function
 * of the clock), a cursor, two rows of keys lit as each character is struck, a faint line of light sweeping down it.
 * In a hand in the world it is a screen floating by the hand of the arm that hangs out from the body (XCharaIdle), as
 * the user's video has it (2.116.1, user: "패널이 영상 보면 방향이 저렇게 안되어 있어"; read again from the video's two
 * cameras, in front and above the front right, the two agreeing on one slab): its long side, the way its writing runs,
 * lies fore and aft; its short side rises out to the holder's side, the outer edge (the top of the writing) some 42°
 * above the inner; its face is turned in and up, to its holder's eyes as they look down to that side, so the writing
 * reads from behind them to ahead. It floats below and outside the hand, a gap between them, the hand over the lower
 * half of it (its keys), a little ahead of the arm; bobbing a little. It goes where the hand goes and stands so as the
 * body stands, whatever the arm does (a guard's raised forearm, a hit's swing: the arm's own turn is undone,
 * {@link #arm}); turned with the arm it stood on its edge in a guard. (2.116.0 had it level across, its long side out to
 * the side, its far edge raised: not the video's. 2.115.0 held it over the palm of a raised forearm.) From one's own eyes
 * it lies low on the off hand's side, leaning back to face them. It opens from a line of
 * light and folds back to one (HackLook.wide, deep, content, line).
 */
public final class HackPanel {
    private HackPanel(){}
    /** The hack it is drawn for: set by whoever draws it in a hand, for that one draw. */
    public static HackLook.Frame frame;
    /**
     * The turn of the arm whose hand it is drawn by, in the model (its shoulder's, then its elbow's): set by whoever draws
     * it in a hand in the world, for that one draw (null: as the arm stands with the panel out, XCharaIdle.PANEL_OUT).
     */
    public static Quaternionf arm;
    /**
     * Its width (the way its writing runs), its depth and its thickness (blocks of the model). 2.116.1: measured again on
     * the user's video with the slab seen as it stands there (its long side fore and aft, from above the front right; its
     * short side across the view from in front): some 2.2 by 1.3 of the body's studs, a little longer than 2.115.0 read
     * it lying level (0.74).
     */
    static final float W=.82f,D=.48f,T=.028f;
    static final int SLAB=0x140a22,UNDER=0x0b0514,RIM=0xa635ff,HOT=0xe6c8ff,GLOW=0x8a14e8,TEXT=0xd4a6ff,KEY=0x7426cc,WHITE=0xffffff;
    /**
     * Where its middle floats from the middle of the end of the arm that holds it, the arm hanging out from the body
     * (blocks of the model, as the body stands: out to the holder's side, down, ahead), and how far its outer edge is
     * raised above its inner one (degrees). From the user's video, the game filmed from the video's two cameras (level in
     * front at the neck's height; above the front right, 36° round and 29° up) and the rim of light measured the same
     * way in both: the short side rises 41° to 46° outward in the view from in front (the game's at 40° measured 40° to
     * 44°), the middle some 0.24 out and 0.14 below the hand's end there, and ahead of it by what the view from above
     * has (0.17 to 0.30: the hand's own place is the least sure of these).
     */
    static final float OUT=.22f,DOWN=.13f,AHEAD=.24f,TILT=42;
    /** How far ahead of the middle of the arm's end the held item's frame is (HeldItemFeatureRenderer: the front edge of the arm's end, 2 px). */
    static final float FRONT=.125f;
    /**
     * A placing to try before it goes in (the capture tool's "panelfit=TILT/OUT/DOWN/AHEAD/WIDE/DEEP": the last two
     * stretch it, 1 as it is), null otherwise.
     */
    static volatile float[] tuned;

    public static void initialize(){BuiltinItemRendererRegistry.INSTANCE.register(XChara.PANEL,HackPanel::render);}
    /** Whether anything of it is drawn for this hack (it is folded away a little before the rest of the hack is gone). */
    static boolean shown(HackLook.Frame f){return f.on()||f.t()<.25f;}

    private static void render(ItemStack stack,ModelTransformationMode mode,MatrixStack m,VertexConsumerProvider buffers,int light,int overlay){
        boolean held=mode==ModelTransformationMode.THIRD_PERSON_LEFT_HAND||mode==ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
        boolean eyes=mode==ModelTransformationMode.FIRST_PERSON_LEFT_HAND||mode==ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
        var f=frame;
        if(f==null){
            // not in a hacking hand (nobody has it: a creative tab, a command): as it is, steady
            if(held||eyes)return;
            f=new HackLook.Frame(true,99,StandClient.ticks()/20f,0);
        }
        if(!shown(f))return;
        boolean left=mode==ModelTransformationMode.THIRD_PERSON_LEFT_HAND||mode==ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
        m.push();m.translate(.5f,.5f,.5f);
        if(held)inHand(m,left,f.clock(),arm);else if(eyes)inView(m,left);
        else{m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));m.scale(1.2f,1.2f,1.2f);}
        // (its coming on: it jumps a little sideways as it breaks in)
        if(f.on()&&f.t()<.25f)m.translate((hash((int)Math.floor(f.t()*40),f.seed(),3)-.5f)*.05f*(1-smooth(f.t(),0,.25f)),0,0);
        draw(m,buffers,f);
        if(held)HackMode.recordPanel(m,W*.5f,D*.5f);
        m.pop();
    }
    /** The held item's frame from the arm's own (HeldItemFeatureRenderer: a quarter turn down, half round). */
    private static final Quaternionf HAND=new Quaternionf().rotationX(-(float)Math.PI/2).rotateY((float)Math.PI);
    /**
     * Into its place by a hand in the world, from the held item's frame there (its x across the arm, towards its holder's
     * right; its y off the front of the arm, ahead of an arm that hangs; its z back up the arm), the arm turned
     * {@code arm} in the model (null: as it stands with the panel out): first as the body stands again, as if the arm
     * hung unturned (its turn undone about the hand, then up from the frame's z: x to the holder's right, y up, z
     * behind); then to its middle, out to the side and down from the middle of the arm's end and ahead of it; then
     * turned so that its width lies fore and aft and its far edge (the top of the writing) is the outer one, raised:
     * its face in and up, to its holder (a quarter turn about the upright, the writing running ahead as its holder
     * reads it from that side, then leaned about the fore-and-aft line).
     */
    static void inHand(MatrixStack m,boolean left,float clock,Quaternionf arm){
        float side=left?-1:1;
        float[] fit=tuned;
        float tilt=fit!=null?fit[0]:TILT,out=fit!=null?fit[1]:OUT,down=fit!=null?fit[2]:DOWN,ahead=fit!=null?fit[3]:AHEAD;
        Quaternionf turn=arm!=null?arm:new Quaternionf().rotationZ(side*XCharaIdle.PANEL_OUT*(float)Math.PI/180);
        m.multiply(new Quaternionf(HAND).conjugate().mul(new Quaternionf(turn).conjugate()).mul(HAND));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        m.translate(side*out,-down+.012f*(float)Math.sin(clock*3.1f),FRONT-ahead);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side*(tilt+1.5f*(float)Math.sin(clock*2.3f))));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side*90));
        if(fit!=null&&fit.length>5)m.scale(fit[4],1,fit[5]);
    }
    /**
     * Into its place before one's own eyes, from the off hand's item's frame there (the view's: x right, y up, z towards
     * the eyes): leaning back to face them, turned a little to the middle (as large in the view as it was before 2.116.1
     * made it longer: 0.56 of its size).
     */
    static void inView(MatrixStack m,boolean left){
        float side=left?-1:1;
        m.translate(side*-.04f,.2f,-.06f);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side*-14));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(54));
        m.scale(.56f,.56f,.56f);
    }

    /** The panel about its middle: its width along x, its depth along z (its far edge at -z, the top of what is on it), its face up y. */
    static void draw(MatrixStack m,VertexConsumerProvider buffers,HackLook.Frame f){
        float wide=f.wide(),deep=f.deep(),hw=W*.5f*wide,hd=D*.5f*deep;
        if(hw<=.003f)return;
        var e=m.peek();Matrix4f pm=e.getPositionMatrix();Matrix3f nm=e.getNormalMatrix();
        float clock=f.clock(),breath=.86f+.14f*(float)Math.sin(clock*3.8f),steady=f.on()?1:1-smooth(f.t(),.02f,.2f);
        // the slab: dark faces, purple sides
        VertexConsumer s=buffers.getBuffer(HackKnife.solid());
        flat(s,pm,nm,-hw,-hd,hw,hd,0,SLAB,1);flat(s,pm,nm,-hw,-hd,hw,hd,-T,UNDER,1);
        side(s,pm,nm,-hw,-hd,hw,-hd,RIM);side(s,pm,nm,hw,-hd,hw,hd,RIM);side(s,pm,nm,hw,hd,-hw,hd,RIM);side(s,pm,nm,-hw,hd,-hw,-hd,RIM);
        VertexConsumer v=buffers.getBuffer(HackKnife.glowing());
        float y=.003f,b=.014f;
        // the rim of light round its face (and, dimmer, under it), and the glow past its sides
        for(int k=0;k<2;k++){
            float at=k==0?y:-T-y,a=k==0?1:.7f;
            flat(v,pm,nm,-hw,-hd,hw,-hd+b,at,RIM,a);flat(v,pm,nm,-hw,hd-b,hw,hd,at,RIM,a);
            flat(v,pm,nm,-hw,-hd+b,-hw+b,hd-b,at,RIM,a);flat(v,pm,nm,hw-b,-hd+b,hw,hd-b,at,RIM,a);
        }
        // (from a hair off its sides: touching them, it showed as a dotted line along each)
        float g=.1f,ga=.5f*breath*steady,mid=-T*.5f,o=.004f,ow=hw+o,od=hd+o;
        fade(v,pm,nm,-ow,-od,ow,-od,ow+g,-od-g,-ow-g,-od-g,mid,ga);fade(v,pm,nm,ow,od,-ow,od,-ow-g,od+g,ow+g,od+g,mid,ga);
        fade(v,pm,nm,ow,-od,ow,od,ow+g,od+g,ow+g,-od-g,mid,ga);fade(v,pm,nm,-ow,od,-ow,-od,-ow-g,-od-g,-ow-g,od+g,mid,ga);
        // the line of light it opens from and closes to
        float line=f.line();
        if(line>.004f){
            flat(v,pm,nm,-hw*1.06f,-.04f,hw*1.06f,.04f,y*2,GLOW,.3f*line);flat(v,pm,nm,-hw,-.01f,hw,.01f,y*3,HOT,.85f*line);
        }
        float c=f.content();
        if(c>.004f)content(v,pm,nm,f,c,wide,deep,y*2);
    }
    /** What is on its face: every place given as on the open panel, moved in with it as it opens ({@code wide}, {@code deep}). */
    private static void content(VertexConsumer v,Matrix4f pm,Matrix3f nm,HackLook.Frame f,float c,float wide,float deep,float y){
        float clock=f.clock();int seed=f.seed();
        int n=lineAt(clock);float into=clock-n*LINE_EVERY,scroll=scrolled(clock),typed=typed(clock,seed);
        float hw=W*.5f,hd=D*.5f;
        // the title bar: a name's block, three small squares (the last one blinking)
        rect(v,pm,nm,wide,deep,-hw+.03f,-hd+.028f,-hw+.25f,-hd+.066f,y,TEXT,.9f*c);
        for(int k=0;k<3;k++){
            float x=hw-.15f+k*.045f,a=k==2?(.35f+.65f*smooth((float)Math.sin(clock*5.5f),-.2f,.2f)):.8f;
            rect(v,pm,nm,wide,deep,x,-hd+.034f,x+.028f,-hd+.062f,y,k==2?HOT:RIM,a*c);
        }
        // the line filling as the line of writing is typed
        float done=Math.min(1,into/TYPE);
        rect(v,pm,nm,wide,deep,-hw+.03f,-hd+.078f,hw-.03f,-hd+.084f,y,KEY,.6f*c);
        rect(v,pm,nm,wide,deep,-hw+.03f,-hd+.078f,-hw+.03f+(W-.06f)*done,-hd+.084f,y,HOT,.9f*c);
        // the rows of writing: the last being typed, all moved up a row as it is done
        float top=-hd+.098f,pitch=.048f,cw=.026f,high=.029f,x0=-hw+.035f;
        for(int i=0;i<ROWS;i++){
            int line=n-(ROWS-1-i);float z=top+(i-scroll)*pitch,a=(i==ROWS-1?.95f:.5f+.13f*i)*c;
            if(i==0)a*=1-scroll;
            int len=length(line,seed),upto=i==ROWS-1?(int)Math.floor(typed):len;
            int from=-1;
            for(int k=0;k<=upto;k++){
                boolean ink=k<upto&&glyph(line,k,seed);
                if(ink&&from<0)from=k;
                else if(!ink&&from>=0){rect(v,pm,nm,wide,deep,x0+from*cw,z,x0+k*cw-.007f,z+high,y,TEXT,a);from=-1;}
            }
            if(i==ROWS-1){
                // the cursor: there while it types, blinking once the line is done
                float blink=into<TYPE?1:.2f+.8f*smooth((float)Math.sin(clock*20),-.3f,.3f),x=x0+Math.min(upto,len)*cw;
                rect(v,pm,nm,wide,deep,x,z,x+cw*.8f,z+high,y,WHITE,.9f*blink*c*(1-scroll));
            }
        }
        // its keys, lit as each character is struck
        float kw=.058f,kh=.035f,gap=.0105f,kx=-(KEYS_ACROSS*kw+(KEYS_ACROSS-1)*gap)*.5f,kz=hd-.03f-KEYS_DOWN*kh-(KEYS_DOWN-1)*gap;
        for(int r=0;r<KEYS_DOWN;r++)for(int k=0;k<KEYS_ACROSS;k++){
            float lit=keyLit(r*KEYS_ACROSS+k,clock,seed),x=kx+k*(kw+gap),z=kz+r*(kh+gap);
            rect(v,pm,nm,wide,deep,x,z,x+kw,z+kh,y,HackKnife.mix(KEY,WHITE,lit),(.55f+.45f*lit)*c);
        }
        // a faint line of light sweeping down it
        float sweep=(clock/2.2f-(float)Math.floor(clock/2.2f))*2-1;
        rect(v,pm,nm,wide,deep,-hw+.014f,sweep*hd-.012f,hw-.014f,sweep*hd+.012f,y,WHITE,.07f*c);
    }
    /** A rectangle on its face (given as on the open panel), kept within its rim. */
    private static void rect(VertexConsumer v,Matrix4f pm,Matrix3f nm,float wide,float deep,float x0,float z0,float x1,float z1,float y,int rgb,float a){
        float hd=D*.5f-.014f;
        z0=Math.max(-hd,Math.min(hd,z0));z1=Math.max(-hd,Math.min(hd,z1));
        if(a<=.003f||x1<=x0||z1<=z0)return;
        flat(v,pm,nm,x0*wide,z0*deep,x1*wide,z1*deep,y,rgb,a);
    }
    /** A level rectangle at height {@code y} (both faces). */
    private static void flat(VertexConsumer v,Matrix4f pm,Matrix3f nm,float x0,float z0,float x1,float z1,float y,int rgb,float a){
        HackKnife.raw(v,pm,nm,x0,y,z0,rgb,a);HackKnife.raw(v,pm,nm,x0,y,z1,rgb,a);HackKnife.raw(v,pm,nm,x1,y,z1,rgb,a);HackKnife.raw(v,pm,nm,x1,y,z0,rgb,a);
        HackKnife.raw(v,pm,nm,x1,y,z0,rgb,a);HackKnife.raw(v,pm,nm,x1,y,z1,rgb,a);HackKnife.raw(v,pm,nm,x0,y,z1,rgb,a);HackKnife.raw(v,pm,nm,x0,y,z0,rgb,a);
    }
    /** One of the slab's sides, from its face down to its underside (both faces). */
    private static void side(VertexConsumer v,Matrix4f pm,Matrix3f nm,float x0,float z0,float x1,float z1,int rgb){
        HackKnife.raw(v,pm,nm,x0,0,z0,rgb,1);HackKnife.raw(v,pm,nm,x1,0,z1,rgb,1);HackKnife.raw(v,pm,nm,x1,-T,z1,rgb,1);HackKnife.raw(v,pm,nm,x0,-T,z0,rgb,1);
        HackKnife.raw(v,pm,nm,x0,-T,z0,rgb,1);HackKnife.raw(v,pm,nm,x1,-T,z1,rgb,1);HackKnife.raw(v,pm,nm,x1,0,z1,rgb,1);HackKnife.raw(v,pm,nm,x0,0,z0,rgb,1);
    }
    /** The glow past one of its sides: from the side (two points) out to two points further, dying away (both faces). */
    private static void fade(VertexConsumer v,Matrix4f pm,Matrix3f nm,float ax,float az,float bx,float bz,float cx,float cz,float dx,float dz,float y,float a){
        if(a<=.003f)return;
        HackKnife.raw(v,pm,nm,ax,y,az,GLOW,a);HackKnife.raw(v,pm,nm,bx,y,bz,GLOW,a);HackKnife.raw(v,pm,nm,cx,y,cz,GLOW,0);HackKnife.raw(v,pm,nm,dx,y,dz,GLOW,0);
        HackKnife.raw(v,pm,nm,dx,y,dz,GLOW,0);HackKnife.raw(v,pm,nm,cx,y,cz,GLOW,0);HackKnife.raw(v,pm,nm,bx,y,bz,GLOW,a);HackKnife.raw(v,pm,nm,ax,y,az,GLOW,a);
    }
}
