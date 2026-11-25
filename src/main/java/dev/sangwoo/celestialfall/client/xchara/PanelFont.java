package dev.sangwoo.celestialfall.client.xchara;

import java.util.ArrayList;
import java.util.List;

/**
 * (2.137.6, user request: "폰트를 패널에 맞게 예쁘게") The lettering of the word on the hacking panel: no longer dots
 * of a five by seven grid but letters drawn as strokes of one width, in the cut-cornered shapes of a console's display
 * (an O of eight sides, the E and the R with their upper left corner cut, a V and a W standing on flat feet), each as
 * tall as the word's line and together as wide as the panel's face within its rim.
 *
 * <p>Everything is in blocks of the open panel, from the word's upper left corner: x across (the way it reads), y down.
 * A letter is a few paths (the middle lines of its strokes); {@link #sides} gives a path's two edges at a distance from
 * its middle line, mitred at its corners, which is both the stroke itself (at half its width) and how far its light
 * reaches (further out). Pure geometry: the panel draws it ({@link HackPanel}).
 */
public final class PanelFont{
    private PanelFont(){}
    /** A letter's height, a stroke's width, the length of a cut corner's legs, and the gap between two letters. */
    public static final float HEIGHT=.1155f,STROKE=.014f,CUT=.017f,GAP=.017f;
    /** The whole word's width (the panel's face within its rim: HackPanel.W less 0.07). */
    public static final float WIDTH=.75f;
    /** Half a stroke. */
    static final float H=STROKE*.5f;
    /** How wide the flat a V or a W stands on (and the W's middle peak) is: no narrower than the stroke, or its corners cross. */
    static final float FOOT=STROKE;
    /** How far one stroke runs into another it joins (no hair of the face showing between them). */
    static final float INTO=.001f;

    /**
     * The middle line of a stroke. An open one's ends are cut square to it, or level (flat: a V's tops), and an end
     * that joins another stroke (joined) has no light of its own beyond it.
     */
    public record Path(float[] x,float[] y,boolean closed,boolean flatFrom,boolean flatTo,boolean joinedFrom,boolean joinedTo){
        /** How many straight runs it has. */
        public int spans(){return closed?x.length:x.length-1;}
    }
    /** A path's two edges: point i of each (a closed path's first again at its end, so run i is between i and i+1). */
    public record Sides(float[] lx,float[] ly,float[] rx,float[] ry){}
    /** A letter of the word: which, where its left edge is, how wide, and its strokes (in the word's blocks). */
    public record Letter(char sign,float x,float width,Path[] paths){}

    /** The word (HackLook.WORD), laid out across WIDTH. */
    public static final Letter[] WORD=word(HackLook.WORD);

    /** How wide a letter is, of the common width (the I is one stroke: nothing of it). */
    static float share(char c){return switch(c){case 'W'->1.3f;case 'E'->.9f;case 'I'->0;default->1;};}
    static Letter[] word(String word){
        float shares=0,fixed=GAP*(word.length()-1);
        for(char c:word.toCharArray()){shares+=share(c);if(share(c)==0)fixed+=STROKE;}
        float each=(WIDTH-fixed)/shares,x=0;
        Letter[] out=new Letter[word.length()];
        for(int i=0;i<out.length;i++){
            char c=word.charAt(i);float w=share(c)==0?STROKE:share(c)*each;
            out[i]=new Letter(c,x,w,glyph(c,w,x));x+=w+GAP;
        }
        return out;
    }
    /** The strokes of a letter {@code w} wide whose left edge is at {@code at}. */
    static Path[] glyph(char c,float w,float at){
        float t=HEIGHT,h=H,k=CUT,f=FOOT*.5f,cap=h*1.03f;
        List<Path> p=new ArrayList<>();
        switch(c){
            case 'O'->p.add(path(at,true,false,false,false,false,h+k,h,w-h-k,h,w-h,h+k,w-h,t-h-k,w-h-k,t-h,h+k,t-h,h,t-h-k,h,h+k));
            case 'V'->p.add(path(at,false,true,true,false,false,cap,0,w*.5f-f,t-h,w*.5f+f,t-h,w-cap,0));
            case 'W'->p.add(path(at,false,true,true,false,false,cap,0,w*.24f-f,t-h,w*.24f+f,t-h,w*.5f-f,t*.4f,w*.5f+f,t*.4f,w*.76f-f,t-h,w*.76f+f,t-h,w-cap,0));
            case 'E'->{
                p.add(path(at,false,false,false,false,false,w,h,h+k,h,h,h+k,h,t-h,w,t-h));
                p.add(path(at,false,false,false,true,false,2*h-INTO,t*.5f,w*.8f,t*.5f));
            }
            case 'R'->{
                float m=t*.52f;
                p.add(path(at,false,false,false,false,true,h,t,h,h+k,h+k,h,w-h-k,h,w-h,h+k,w-h,m-k,w-h-k,m,2*h-INTO,m));
                p.add(path(at,false,false,true,true,false,w*.45f,m+h*.4f,w-h*1.18f,t));
            }
            case 'I'->p.add(path(at,false,false,false,false,false,h,0,h,t));
            case 'T'->{
                p.add(path(at,false,false,false,false,false,0,h,w,h));
                p.add(path(at,false,false,false,true,false,w*.5f,2*h-INTO,w*.5f,t));
            }
            default->throw new IllegalArgumentException("no letter "+c);
        }
        return p.toArray(Path[]::new);
    }
    private static Path path(float at,boolean closed,boolean flatFrom,boolean flatTo,boolean joinedFrom,boolean joinedTo,float... xy){
        float[] x=new float[xy.length/2],y=new float[xy.length/2];
        for(int i=0;i<x.length;i++){x[i]=at+xy[2*i];y[i]=xy[2*i+1];}
        return new Path(x,y,closed,flatFrom,flatTo,joinedFrom,joinedTo);
    }

    /**
     * A path's two edges {@code reach} beyond its stroke (0: the stroke's own edges). At a corner they meet in a mitre;
     * inside a tight corner the reach is drawn in so two runs' edges never cross. At an open end they are cut as the
     * end is (square or level), and with a reach also carried that far past the end (a mitred corner of light), unless
     * the end joins another stroke.
     */
    public static Sides sides(Path p,float reach){
        int n=p.x.length,out=p.closed?n+1:n;
        float[] lx=new float[out],ly=new float[out],rx=new float[out],ry=new float[out];
        for(int i=0;i<n;i++){
            boolean first=i==0&&!p.closed,last=i==n-1&&!p.closed;
            float px=p.x[i],py=p.y[i];
            if(first||last){
                int a=first?0:n-2;
                float dx=p.x[a+1]-p.x[a],dy=p.y[a+1]-p.y[a],len=(float)Math.hypot(dx,dy);dx/=len;dy/=len;
                boolean flat=first?p.flatFrom:p.flatTo,joined=first?p.joinedFrom:p.joinedTo;
                // along the cut: square to the run, or level
                float cx=flat?1:-dy,cy=flat?0:dx;
                float u=-1/(cx*dy-cy*dx),d=(H+reach)*u,ox=0,oy=0;
                if(reach>0&&!joined){
                    float ex=first?-dx:dx,ey=first?-dy:dy,qx=-cy,qy=cx;
                    if(qx*ex+qy*ey<0){qx=-qx;qy=-qy;}
                    ox=qx*reach;oy=qy*reach;
                }
                lx[i]=px+cx*d+ox;ly[i]=py+cy*d+oy;rx[i]=px-cx*d+ox;ry[i]=py-cy*d+oy;
            }else{
                int a=(i-1+n)%n,b=(i+1)%n;
                float ax=px-p.x[a],ay=py-p.y[a],la=(float)Math.hypot(ax,ay),bx=p.x[b]-px,by=p.y[b]-py,lb=(float)Math.hypot(bx,by);
                ax/=la;ay/=la;bx/=lb;by/=lb;
                float dot=ax*bx+ay*by,mx=(-ay-by)/(1+dot),my=(ax+bx)/(1+dot);
                float tan=(float)Math.sqrt(Math.max(0,mx*mx+my*my-1)),inside=mx*bx+my*by;
                float dl=H+reach,dr=dl;
                if(reach>0&&tan>1e-4f){
                    float most=Math.max(H,.5f*Math.min(la,lb)/tan);
                    if(inside>0)dl=Math.min(dl,most);else if(inside<0)dr=Math.min(dr,most);
                }
                lx[i]=px+mx*dl;ly[i]=py+my*dl;rx[i]=px-mx*dr;ry[i]=py-my*dr;
            }
        }
        if(p.closed){lx[n]=lx[0];ly[n]=ly[0];rx[n]=rx[0];ry[n]=ry[0];}
        return new Sides(lx,ly,rx,ry);
    }
    /** Every path of the word's edges at a reach: [letter][path]. */
    public static Sides[][] edges(float reach){
        Sides[][] out=new Sides[WORD.length][];
        for(int l=0;l<WORD.length;l++){
            out[l]=new Sides[WORD[l].paths().length];
            for(int k=0;k<out[l].length;k++)out[l][k]=sides(WORD[l].paths()[k],reach);
        }
        return out;
    }
}
