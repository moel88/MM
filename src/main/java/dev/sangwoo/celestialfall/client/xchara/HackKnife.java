package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.xchara.XCharaKnifeItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * X Chara's knife while their hack is on (2.115.0, user request: "Q를 누르면 검이 빛나고 빛나는 보라색 테두리가 생기고", and
 * the user's video: the whole knife solid white, a glowing purple rim round it), drawn over the knife's own model
 * wherever that is drawn (XCharaKnifeRenderer: a hand in the world, one's own eyes).
 * <p>
 * Its white is the model drawn once more, unshaded and added (so it is the knife's own shape from anywhere). Its rim is
 * drawn round the knife's outline ({@link #OUTLINE}, the model's measures: tools/xchara/make_knife.py): a crisp band of
 * lilac and vivid purple in the blade's plane and a soft glow past it, and the same standing off both its flats (so
 * that the knife seen edge on is still a white sliver between purple), blended rather than added (purple added to a
 * bright sky washes out), under the white (which takes everything within the knife's shape). As it comes on and goes
 * off blocks of purple break over it (the video's glitch: {@link #CELLS}, a grid over the knife and a little about it,
 * a share of them showing, reshuffled thirty times a second); steady, one blinks now and then. A light runs along its
 * rim from its rod to its point every couple of seconds, and its glow breathes.
 */
public final class HackKnife {
    private HackKnife(){}
    // the model's measures (make_knife.py), in sixteenths of a block: the knife along +y, the middle of its rod at the
    // origin, its spine towards +x, its flats towards +-z
    static final double K=24.0/(1345+438),BLADE=1345*K,DEPTH=325*K,ROD_LEN=438*K,ROD_R=.18,SPINE_X=33*K,
        LEAN=Math.tan(Math.toRadians(7)),Y_HEEL=ROD_LEN/2,Y_TIP=Y_HEEL+BLADE,THICK=.32;
    /** The rim: its crisp band's width (lilac by the knife, then vivid purple) and its glow's reach past it (model units). */
    static final double RIM_LILAC=.10,RIM=.34,GLOW_REACH=1.05;
    /** Its glow's reach off the flats (the knife seen edge on, as from behind its holder: a line of purple light rather than a sliver). */
    static final double GLOW_OFF=1.9;
    static final int LILAC=0xdcb2ff,VIVID=0xa233ff,GLOW=0x8a14e8,WHITE=0xffffff,DEEP=0x6a0cb8,PURPLE=0x9416e0;
    /** A plain white texture (the game's own): the layers below take their colour from each vertex. */
    static final Identifier BLANK=new Identifier("textures/misc/white.png");
    /** Unshaded, blended, no depth written (the rim, its glow, the blocks of the glitch, the panel's light). */
    static RenderLayer glowing(){return RenderLayer.getBeaconBeam(BLANK,true);}
    /** Unshaded and solid (the panel's slab). */
    static RenderLayer solid(){return RenderLayer.getBeaconBeam(BLANK,false);}
    /** Unshaded and added (the knife's white). */
    static RenderLayer lit(){return RenderLayer.getEyes(BLANK);}

    /** Model units to the renderer's (blocks); GeckoLib mirrors x as it bakes a model. */
    static float u(double units){return (float)(units/16);}
    static float rx(double x){return (float)(-x/16);}
    /** The blade's depth (spine to edge) at {@code y}. */
    static double depthAt(double y){return DEPTH*SwordDance.depth((y-Y_HEEL)/BLADE);}
    /** The heel's line at {@code x} (it leans: its lower corner a little ahead). */
    static double heelY(double x){return Y_HEEL+(SPINE_X-x)*LEAN;}
    static boolean inBlade(double x,double y){return y<=Y_TIP&&x<=SPINE_X&&x>=SPINE_X-depthAt(y)&&y>=heelY(x);}
    /** Whether a point of the blade's plane is on the knife (its blade or its rod). */
    static boolean inKnife(double x,double y){return inBlade(x,y)||Math.abs(x)<=ROD_R&&y>=-Y_HEEL&&y<=heelY(x);}

    /** The knife's outline in its blade's plane, going round counterclockwise (x towards the spine, y towards the point). */
    static final double[][] OUTLINE=outline();
    /** At each of its points, the way out of the knife there, as long as a band one unit wide needs it at that corner. */
    static final double[][] MITER=miters(OUTLINE);
    static double[][] outline(){return outline(26);}
    /** The same outline with {@code n} points along the edge (fewer for a knife seen small: the rain's, 2.132.2). */
    static double[][] outline(int n){
        var p=new ArrayList<double[]>();
        p.add(new double[]{ROD_R,-Y_HEEL});p.add(new double[]{ROD_R,heelY(ROD_R)});
        p.add(new double[]{SPINE_X,Y_HEEL});p.add(new double[]{SPINE_X,Y_TIP});
        // back along the edge from the point to the heel's lower corner (closer together by the point, where it curves fast)
        double corner=DEPTH*LEAN/BLADE;
        for(int k=1;k<=n;k++){
            double t=1-(1-corner)*Math.pow(k/(double)n,1.5),y=Y_HEEL+BLADE*t;
            p.add(new double[]{SPINE_X-depthAt(y),y});
        }
        p.add(new double[]{-ROD_R,heelY(-ROD_R)});p.add(new double[]{-ROD_R,-Y_HEEL});
        return p.toArray(new double[0][]);
    }
    /** Twice the area an outline goes round (positive: counterclockwise). */
    static double area2(double[][] p){
        double a=0;for(int i=0;i<p.length;i++){var q=p[(i+1)%p.length];a+=p[i][0]*q[1]-q[0]*p[i][1];}
        return a;
    }
    static double[][] miters(double[][] p){
        int n=p.length;double sign=area2(p)>=0?1:-1;var out=new double[n][];
        for(int i=0;i<n;i++){
            double[] a=p[(i+n-1)%n],b=p[i],c=p[(i+1)%n];
            double x0=b[0]-a[0],y0=b[1]-a[1],l0=Math.hypot(x0,y0),x1=c[0]-b[0],y1=c[1]-b[1],l1=Math.hypot(x1,y1);
            // each side's way out (to the right of the way round, going counterclockwise)
            double n0x=sign*y0/l0,n0y=-sign*x0/l0,n1x=sign*y1/l1,n1y=-sign*x1/l1;
            double mx=n0x+n1x,my=n0y+n1y,ml=Math.hypot(mx,my);
            if(ml<1e-9){mx=n0x;my=n0y;ml=1;}
            mx/=ml;my/=ml;
            double scale=1/Math.max(.38,mx*n0x+my*n0y);
            out[i]=new double[]{mx*scale,my*scale};
        }
        return out;
    }

    /** One cell of the glitch's grid: its middle, whether it is on the knife (or just off it), its number. */
    record Cell(double x,double y,boolean on,int id){}
    static final double CELL=.8;
    static final List<Cell> CELLS=cells();
    static List<Cell> cells(){
        var out=new ArrayList<Cell>();int id=0;
        for(int iy=(int)Math.floor((-Y_HEEL-1.2)/CELL);iy<=(int)Math.ceil((Y_TIP+1.2)/CELL);iy++)
            for(int ix=(int)Math.floor((SPINE_X-DEPTH-1.8)/CELL);ix<=(int)Math.ceil((SPINE_X+1.8)/CELL);ix++){
                double x=(ix+.5)*CELL,y=(iy+.5)*CELL;id++;
                if(inKnife(x,y)){out.add(new Cell(x,y,true,id));continue;}
                boolean near=false;
                for(int k=0;k<8&&!near;k++){double turn=k*Math.PI/4;near=inKnife(x+Math.cos(turn)*1.25,y+Math.sin(turn)*1.25);}
                if(near)out.add(new Cell(x,y,false,id));
            }
        return out;
    }
    /** Whether cell {@code c} shows a block at {@code slot} (the glitch reshuffles every slot) when {@code share} of the knife is under them: those off it less often. */
    static boolean showing(Cell c,int slot,int seed,float share){return HackLook.hash(c.id(),slot,seed)<share*(c.on()?1:.42f);}
    /** How far the light running along the rim is at {@code y} along the knife (0 none, 1 its middle), {@code clock} seconds on: from the rod's end to the point every RUN seconds. */
    static final float RUN=2.2f;
    static float running(double y,float clock){
        double along=(y+Y_HEEL)/(Y_TIP+Y_HEEL)-clock/RUN;
        double c=Math.cos(2*Math.PI*along);
        return c<=0?0:(float)Math.pow(c,14);
    }

    // ───────────────────────────── drawing ─────────────────────────────

    /** Draws the hack's look over the knife's model, just drawn with {@code matrices} (its renderer's, at the model's origin). */
    static void draw(GeoItemRenderer<XCharaKnifeItem> renderer,BakedGeoModel model,MatrixStack matrices,VertexConsumerProvider buffers,XCharaKnifeItem item,float delta,int overlay,HackLook.Frame h){
        float rim=h.rim(),fill=h.fill();
        var e=matrices.peek();Matrix4f pm=e.getPositionMatrix();Matrix3f nm=e.getNormalMatrix();
        if(rim>.004f)rim(buffers.getBuffer(glowing()),pm,nm,rim,h.clock());
        if(fill>.004f){
            RenderLayer lit=lit();
            // (half lit it is violet, not grey: its green comes last)
            float r=(float)Math.pow(fill,.8),g=(float)Math.pow(fill,2.2),b=(float)Math.pow(fill,.6);
            renderer.reRender(model,matrices,buffers,item,lit,buffers.getBuffer(lit),delta,0xF000F0,overlay,r,g,b,1);
            // and the same light as a sheet just over each flat (the model is slices: seen close, hair-wide gaps between
            // them showed as dark specks on the white)
            flats(buffers.getBuffer(solid()),pm,nm,Math.round(r*255)<<16|Math.round(g*255)<<8|Math.round(b*255));
        }
        blocks(buffers,pm,nm,h);
    }
    private static void rim(VertexConsumer v,Matrix4f pm,Matrix3f nm,float rim,float clock){
        float crisp=Math.min(1,rim),flare=Math.max(1,rim),breath=.86f+.14f*(float)Math.sin(clock*3.8f);
        double reach=GLOW_REACH*(.55+.45*flare);float glow=Math.min(1,.5f*rim)*breath;
        int n=OUTLINE.length;
        double half=THICK*.5;
        for(int i=0;i<n;i++){
            int j=(i+1)%n;double[] a=OUTLINE[i],b=OUTLINE[j],ma=MITER[i],mb=MITER[j];
            // (the rod's three sides: its two lengths and its end; every other side is the blade's)
            ofBlade=i>=1&&i<=n-3;
            float la=running(a[1],clock),lb=running(b[1],clock);
            int ca=mix(VIVID,WHITE,.8f*la),cb=mix(VIVID,WHITE,.8f*lb);
            // in the blade's plane: lilac by the knife, vivid purple, then the glow dying away
            strip(v,pm,nm,a,b,ma,mb,-.05,RIM_LILAC,0,0,LILAC,crisp,LILAC,crisp,LILAC,crisp,LILAC,crisp);
            strip(v,pm,nm,a,b,ma,mb,RIM_LILAC,RIM,0,0,ca,crisp,cb,crisp,ca,crisp,cb,crisp);
            strip(v,pm,nm,a,b,ma,mb,RIM,RIM+reach,0,0,GLOW,glow,GLOW,glow,GLOW,0,GLOW,0);
            // standing off both flats (the knife seen edge on)
            for(int s=-1;s<=1;s+=2){
                strip(v,pm,nm,a,b,ma,mb,.02,.02,s*(half-.02),s*(half+RIM*.8),ca,crisp,cb,crisp,ca,crisp,cb,crisp);
                strip(v,pm,nm,a,b,ma,mb,.02,.02,s*(half+RIM*.8),s*(half+RIM*.8+GLOW_OFF*(.55+.45*flare)),GLOW,glow,GLOW,glow,GLOW,0,GLOW,0);
            }
        }
    }
    /** The blade's two flats, a hair off them: from the spine to the edge all along it (the outline's own points), and the heel's lean. */
    private static void flats(VertexConsumer v,Matrix4f pm,Matrix3f nm,int rgb){
        ofBlade=true;
        int first=3,last=OUTLINE.length-3;
        for(int s=-1;s<=1;s+=2){
            double z=s*(THICK*.5+.012);
            for(int k=first;k<last;k++){
                double[] a=OUTLINE[k],b=OUTLINE[k+1];
                sheet(v,pm,nm,SPINE_X,a[1],a[0],a[1],b[0],b[1],SPINE_X,b[1],z,rgb);
            }
            double[] corner=OUTLINE[last];
            sheet(v,pm,nm,SPINE_X,corner[1],corner[0],corner[1],SPINE_X,Y_HEEL,SPINE_X,Y_HEEL,z,rgb);
        }
    }
    private static void sheet(VertexConsumer v,Matrix4f pm,Matrix3f nm,double x0,double y0,double x1,double y1,double x2,double y2,double x3,double y3,double z,int rgb){
        vertex(v,pm,nm,x0,y0,z,rgb,1);vertex(v,pm,nm,x1,y1,z,rgb,1);vertex(v,pm,nm,x2,y2,z,rgb,1);vertex(v,pm,nm,x3,y3,z,rgb,1);
        vertex(v,pm,nm,x3,y3,z,rgb,1);vertex(v,pm,nm,x2,y2,z,rgb,1);vertex(v,pm,nm,x1,y1,z,rgb,1);vertex(v,pm,nm,x0,y0,z,rgb,1);
    }
    /** One side of the outline's band, from {@code d0} to {@code d1} out of the knife and {@code z0} to {@code z1} off its plane (both faces). */
    private static void strip(VertexConsumer v,Matrix4f pm,Matrix3f nm,double[] a,double[] b,double[] ma,double[] mb,double d0,double d1,double z0,double z1,
        int a0,float aa0,int b0,float ab0,int a1,float aa1,int b1,float ab1){
        double ax0=a[0]+ma[0]*d0,ay0=a[1]+ma[1]*d0,bx0=b[0]+mb[0]*d0,by0=b[1]+mb[1]*d0,ax1=a[0]+ma[0]*d1,ay1=a[1]+ma[1]*d1,bx1=b[0]+mb[0]*d1,by1=b[1]+mb[1]*d1;
        vertex(v,pm,nm,ax0,ay0,z0,a0,aa0);vertex(v,pm,nm,bx0,by0,z0,b0,ab0);vertex(v,pm,nm,bx1,by1,z1,b1,ab1);vertex(v,pm,nm,ax1,ay1,z1,a1,aa1);
        vertex(v,pm,nm,ax1,ay1,z1,a1,aa1);vertex(v,pm,nm,bx1,by1,z1,b1,ab1);vertex(v,pm,nm,bx0,by0,z0,b0,ab0);vertex(v,pm,nm,ax0,ay0,z0,a0,aa0);
    }
    /** The glitch's blocks: over the knife's flats and in its plane about it as it comes on and goes off; one or two blinking now and then while it is on. */
    private static void blocks(VertexConsumerProvider buffers,Matrix4f pm,Matrix3f nm,HackLook.Frame h){
        float share=h.cells();
        boolean changing=share>.004f,steady=h.on()&&h.t()>=HackLook.ON;
        if(!changing&&!steady)return;
        VertexConsumer v=null;
        int slot=(int)Math.floor(h.t()*30),blink=(int)Math.floor(h.clock()*9);
        int first=-1,second=-1;
        if(steady){
            if(HackLook.hash(blink,1,h.seed())<.3f)first=(int)(HackLook.hash(blink,2,h.seed())*CELLS.size());
            if(HackLook.hash(blink,3,h.seed())<.12f)second=(int)(HackLook.hash(blink,4,h.seed())*CELLS.size());
        }
        for(int i=0;i<CELLS.size();i++){
            var c=CELLS.get(i);ofBlade=c.y()>=Y_HEEL;
            boolean show=changing&&showing(c,slot,h.seed(),share)||i==first||i==second;
            if(!show)continue;
            if(v==null)v=buffers.getBuffer(glowing());
            float pick=HackLook.hash(c.id(),7,h.seed());
            int rgb=c.on()?(pick<.4f?PURPLE:pick<.65f?DEEP:pick<.88f?VIVID:GLOW):(pick<.6f?VIVID:pick<.85f?PURPLE:LILAC);
            double r=CELL*.46;
            if(c.on()){
                double z=THICK*.5+.02;
                for(int s=-1;s<=1;s+=2)square(v,pm,nm,c.x(),c.y(),s*z,r,rgb,.95f);
            }else square(v,pm,nm,c.x(),c.y(),0,r,rgb,.9f);
        }
    }
    private static void square(VertexConsumer v,Matrix4f pm,Matrix3f nm,double x,double y,double z,double r,int rgb,float a){
        vertex(v,pm,nm,x-r,y-r,z,rgb,a);vertex(v,pm,nm,x+r,y-r,z,rgb,a);vertex(v,pm,nm,x+r,y+r,z,rgb,a);vertex(v,pm,nm,x-r,y+r,z,rgb,a);
        vertex(v,pm,nm,x-r,y+r,z,rgb,a);vertex(v,pm,nm,x+r,y+r,z,rgb,a);vertex(v,pm,nm,x+r,y-r,z,rgb,a);vertex(v,pm,nm,x-r,y-r,z,rgb,a);
    }
    /**
     * (2.122.0) Whether what is being drawn is the blade's (not the rod's): the blade grown on its own for their cleave
     * (XCharaKnifeRenderer.blade) takes its white and its rim with it.
     */
    private static boolean ofBlade;
    /** A point of the model as the grown blade has it: about its heel ({@code k} times; the rod's points stay). */
    static double[] grown(double x,double y,double z,float k){return new double[]{x*k,Y_HEEL+(y-Y_HEEL)*k,z*k};}
    /** A vertex at a point of the model (its units), of one colour, unshaded. */
    private static void vertex(VertexConsumer v,Matrix4f pm,Matrix3f nm,double x,double y,double z,int rgb,float a){
        float k=XCharaKnifeRenderer.blade;
        if(ofBlade&&k!=1){double[] g=grown(x,y,z,k);x=g[0];y=g[1];z=g[2];}
        raw(v,pm,nm,rx(x),u(y),u(z),rgb,a);
    }
    /** A vertex at a point of the renderer's own space. */
    static void raw(VertexConsumer v,Matrix4f pm,Matrix3f nm,float x,float y,float z,int rgb,float a){
        v.vertex(pm,x,y,z).color((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,Math.max(0,Math.min(1,a))).texture(.5f,.5f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(nm,0,1,0).next();
    }
    static int mix(int a,int b,float t){
        t=Math.max(0,Math.min(1,t));
        int r=Math.round((a>>16&255)+((b>>16&255)-(a>>16&255))*t),g=Math.round((a>>8&255)+((b>>8&255)-(a>>8&255))*t),bl=Math.round((a&255)+((b&255)-(a&255))*t);
        return r<<16|g<<8|bl;
    }
}
