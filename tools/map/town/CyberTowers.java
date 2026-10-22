package dev.sangwoo.botandtown;

import dev.sangwoo.celestialfall.town.TownBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * The cyber city's buildings (2.49.0, from the user's five photos), each its own way of building a tower on a plot:
 * monoliths of dark stone inlaid with salmon geometry over a stepped podium, white balconies and red bands (the first
 * photo); towers wearing their storeys in bands of coloured light, and round ones with a wrap of LED (the fifth);
 * arcade blocks over shops behind thick pillars, signs hanging under them and billboards above (the fourth); with their
 * roofs' tanks, helipads, masts and beams of light, and the neon signs in Hangul hung on them.
 */
final class CyberTowers {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final BlockState GLASS=TB.of("glass_curtain"),LIT=TB.of("glass_curtain_lit"),PANEL=TB.of("cyber_panel"),PAVING=TB.of("cyber_paving");
    static final BlockState[] LED={TB.of("led_screen_pink"),TB.of("led_screen_cyan"),TB.of("led_screen_mix")};
    static final BlockState[] BANDS={TB.of("light_band_yellow"),TB.of("light_band_blue"),TB.of("light_band_green"),TB.of("light_band_pink"),TB.of("light_band_white")};
    static final BlockState PINK=TB.of("neon_pink"),PURPLE=TB.of("neon_purple"),CYAN=TB.of("neon_cyan"),YELLOW=TB.of("neon_yellow"),GREEN=TB.of("neon_green"),
        ORANGE=TB.of("neon_orange"),WHITE=TB.of("neon_white"),RED=TB.of("neon_red");
    static final BlockState[] NEON={PINK,PURPLE,CYAN,YELLOW,GREEN,ORANGE,WHITE,RED};
    /** The signs' words and the neon that frames each (TownBlocks.SIGN_WORDS has the words). */
    static final String[] WORDS={"ramen","karaoke","hotel","botand","store","pc","rokakaka","arcade","allday","sauna"};
    static final BlockState[] WORD_NEON={ORANGE,PINK,PURPLE,CYAN,GREEN,CYAN,YELLOW,RED,WHITE,GREEN};
    int beacons,signs;
    CyberTowers(Townscape t,Random rnd){this.t=t;c=t.c;this.rnd=rnd;}
    private static BlockState s(Block b){return b.getDefaultState();}

    /** A word's letter blocks in order (a letter is sign_KEY_N, N its first place in the word). */
    static BlockState[] word(String key){
        for(String[] w:TownBlocks.SIGN_WORDS)if(w[0].equals(key)){
            BlockState[] out=new BlockState[w[1].length()];
            for(int i=0;i<out.length;i++)out[i]=TB.of("sign_"+key+"_"+w[1].indexOf(w[1].charAt(i)));
            return out;
        }
        throw new IllegalArgumentException("no sign word "+key);
    }
    int pickWord(){return rnd.nextInt(WORDS.length);}
    /** A two-letter word's place, for signs hung where there is room for two letters. */
    int pickShortWord(){int w;do w=rnd.nextInt(WORDS.length);while(word(WORDS[w]).length>2);return w;}
    /** A point of a frame in the world: x and z. */
    private static int[] at(Frame f,int bx,int bz){return new int[]{f.wx(bx,bz),f.wz(bx,bz)};}

    // ---------------------------------------------------------------- signs

    /**
     * A blade sign: a word's letters stacked down from y (top) at (x, z), standing out from a wall, capped above and
     * below in its neon, an iron bracket back to the wall at both ends. Reads from either way along the street.
     */
    boolean bladeSign(int x,int y,int z,Direction toWall,int w){
        BlockState[] letters=word(WORDS[w]);
        int n=letters.length;
        for(int k=-1;k<=n;k++)if(!c.air(x,y-k,z))return false;
        c.set(x,y+1,z,WORD_NEON[w]);
        for(int k=0;k<n;k++)c.set(x,y-k,z,letters[k]);
        c.set(x,y-n,z,WORD_NEON[w]);
        for(int yy:new int[]{y+1,y-n}){int bx=x+toWall.getOffsetX(),bz=z+toWall.getOffsetZ();if(c.air(bx,yy,bz))c.set(bx,yy,bz,s(Blocks.IRON_BARS));}
        signs++;
        return true;
    }
    /** A sign along a wall facing out the given way: the word read left to right by someone facing the wall, neon at its ends. */
    void wallSign(int x,int y,int z,Direction front,int w){
        BlockState[] letters=word(WORDS[w]);
        Direction right=front.rotateYCounterclockwise();
        int n=letters.length;
        for(int k=-1;k<=n;k++){
            int px=x+right.getOffsetX()*k,pz=z+right.getOffsetZ()*k;
            c.set(px,y,pz,k<0||k==n?WORD_NEON[w]:letters[k]);
        }
        signs++;
    }
    /** A sign hanging from a ceiling on a chain: a word's letters stacked, lit. */
    boolean hangingSign(int x,int ceiling,int z,int w){
        BlockState[] letters=word(WORDS[w]);
        int n=letters.length,top=ceiling-2;
        for(int k=0;k<n+1;k++)if(!c.air(x,top-k+1,z))return false;
        c.set(x,ceiling-1,z,Canvas.chain(Direction.Axis.Y));
        for(int k=0;k<n;k++)c.set(x,top-k,z,letters[k]);
        signs++;
        return true;
    }

    // ---------------------------------------------------------------- the first photo: monoliths

    /**
     * A monolith on x0..x1, z0..z1 standing on y, h tall: a podium stepping in three times, each step's edge trimmed in
     * orange, salmon stripes across its faces; a shaft of dark stone tiles with great geometric panels of salmon and
     * white terracotta on its faces, storeys of dark or lit glass in slots, every third storey a white balcony with a
     * glass rail all round, red bands between; a crown set back with a line of red light, masts.
     */
    void monolith(int x0,int z0,int x1,int z1,int y,int h){
        h=Math.min(h,Cyber.ROOF_Y-y);
        int top=y+h;
        BlockState wall=s(Blocks.DEEPSLATE_TILES),dark=s(Blocks.POLISHED_DEEPSLATE),pink=s(Blocks.PINK_TERRACOTTA),white=s(Blocks.WHITE_TERRACOTTA),
            orange=s(Blocks.ORANGE_TERRACOTTA),red=s(Blocks.RED_NETHER_BRICKS),salmon=s(Blocks.TERRACOTTA);
        int phase=rnd.nextInt(16);
        // the podium: three steps in, four storeys each
        for(int k=0;k<3;k++){
            int in=k*2,ya=y+1+k*4,yb=y+4+k*4;
            for(int x=x0+in;x<=x1-in;x++)for(int z=z0+in;z<=z1-in;z++){
                boolean edge=x==x0+in||x==x1-in||z==z0+in||z==z1-in;
                for(int yy=ya;yy<=yb;yy++){
                    if(!edge){c.set(x,yy,z,yy==yb?dark:s(Blocks.AIR));continue;}
                    int u=x+z;
                    c.set(x,yy,z,yy==yb?orange:((u+yy+phase)%8<2?pink:wall));
                }
            }
        }
        int sx0=x0+6,sx1=x1-6,sz0=z0+6,sz1=z1-6,base=y+13;
        if(sx1-sx0<6||sz1-sz0<6){sx0=x0+4;sx1=x1-4;sz0=z0+4;sz1=z1-4;}
        int crown=top-6;
        boolean redBand=rnd.nextBoolean();
        for(int yy=base;yy<=top;yy++){
            int up=yy-base,storey=up/4,inStorey=up%4;
            boolean balcony=storey%3==2&&inStorey==0&&yy<crown-4;
            // the shaft's top (the crown's floor) and the crown's own top closed right across
            if(yy==crown+1)for(int x=sx0;x<=sx1;x++)for(int z=sz0;z<=sz1;z++)c.set(x,crown,z,dark);
            if(yy==top){for(int x=sx0+2;x<=sx1-2;x++)for(int z=sz0+2;z<=sz1-2;z++)c.set(x,top,z,dark);continue;}
            int in=yy>crown?2:0;
            int ax=sx0+in,bx=sx1-in,az=sz0+in,bz=sz1-in;
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++){
                boolean edge=x==ax||x==bx||z==az||z==bz;
                if(!edge){c.set(x,yy,z,inStorey==0?dark:s(Blocks.AIR));continue;}
                boolean corner=(x==ax||x==bx)&&(z==az||z==bz);
                // the faces: u along the face, v up it
                int u=(z==az||z==bz)?x-ax:z-az,len=(z==az||z==bz)?bx-ax:bz-az;
                BlockState b;
                if(corner)b=wall;
                else if(yy>crown)b=yy==top?dark:(yy==crown+1?RED:wall);
                else if(inStorey==0)b=redBand&&storey%3==1?red:dark;
                else{
                    // great panels: diamonds of salmon outlined in white, centred on each face, spanning a few storeys
                    int cu=len/2,cv=((storey/6)*6+3)*4+2,du=Math.abs(u-cu),dv=Math.abs(up-cv);
                    int dm=du+dv/2;
                    if(len>=12&&dm>=len/2-4&&dm<=len/2-3)b=white;
                    else if(len>=12&&dm<len/2-4&&dm>=len/2-7)b=pink;
                    else if(len>=12&&dm<len/2-7)b=salmon;
                    else b=u%3==1?wall:((storey+u)%5==0?LIT:GLASS);
                }
                c.set(x,yy,z,b);
            }
            if(balcony){
                // a white balcony slab a block out all round, a glass rail on it, lit windows behind
                for(int x=ax-1;x<=bx+1;x++)for(int z=az-1;z<=bz+1;z++){
                    boolean ring=x==ax-1||x==bx+1||z==az-1||z==bz+1;
                    if(!ring)continue;
                    c.set(x,yy,z,s(Blocks.SMOOTH_QUARTZ));
                    if(c.air(x,yy+1,z))c.set(x,yy+1,z,s(Blocks.WHITE_STAINED_GLASS_PANE));
                }
                for(int x=ax+1;x<bx;x++)for(int z:new int[]{az,bz}){c.set(x,yy+1,z,BANDS[4]);c.set(x,yy+2,z,BANDS[4]);}
                for(int z=az+1;z<bz;z++)for(int x:new int[]{ax,bx}){c.set(x,yy+1,z,BANDS[4]);c.set(x,yy+2,z,BANDS[4]);}
            }
        }
        roof(sx0+2,sz0+2,sx1-2,sz1-2,top,h,true);
    }

    // ---------------------------------------------------------------- the fifth photo: towers in bands of light

    /**
     * A tower of storeys in bands of coloured light (the fifth photo): its corners cut off, each storey a dark band
     * and two blocks of lit glass in its colour, the colours changing a few times up it, now and then a dark storey;
     * neon up its cut corners; an LED wall on a face; a set-back top on the tall ones; tanks, a helipad or a beam of
     * light on its roof.
     */
    void bandTower(int x0,int z0,int x1,int z1,int y,int h){
        h=Math.min(h,Cyber.ROOF_Y-y);
        int top=y+h,cut=Math.min(3,Math.min(x1-x0,z1-z0)/6);
        BlockState floor=rnd.nextBoolean()?s(Blocks.BLACK_CONCRETE):PANEL,neon=NEON[rnd.nextInt(NEON.length)];
        int[] colours=new int[1+rnd.nextInt(3)];
        for(int k=0;k<colours.length;k++)colours[k]=rnd.nextInt(BANDS.length);
        int setback=h>70?(int)(h*(0.62+0.12*rnd.nextDouble())):h+1;
        for(int yy=y+1;yy<=top;yy++){
            int up=yy-y,storey=(up-1)/3,in=up>setback?3:0;
            int ax=x0+in,bx=x1-in,az=z0+in,bz=z1-in;
            BlockState band=BANDS[colours[Math.min(colours.length-1,up*colours.length/Math.max(1,h))]];
            boolean darkStorey=Pal.hash(x0+storey,z0,storey*7)<0.12;
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++){
                int dx=Math.min(x-ax,bx-x),dz=Math.min(z-az,bz-z);
                if(dx+dz<cut)continue;
                boolean edge=dx==0||dz==0||dx+dz==cut;
                if(!edge){c.set(x,yy,z,(up-1)%3==0?floor:s(Blocks.AIR));continue;}
                boolean corner=dx+dz==cut;
                BlockState b;
                if(up<=4)b=(up-1)%4==0?floor:shopGlass(x,yy,z);
                else if(corner)b=neon;
                else if((up-1)%3==0)b=floor;
                else b=darkStorey?GLASS:band;
                c.set(x,yy,z,b);
            }
            if(in>0&&up==setback+1)for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)
                if(Math.min(x-x0,x1-x)+Math.min(z-z0,z1-z)>=cut)c.set(x,yy-1,z,floor);
        }
        int in=h>setback?3:0;
        for(int x=x0+in;x<=x1-in;x++)for(int z=z0+in;z<=z1-in;z++)
            if(Math.min(x-x0-in,x1-in-x)+Math.min(z-z0-in,z1-in-z)>=cut)c.set(x,top,z,floor);
        if(h>36&&rnd.nextDouble()<0.7)screen(x0,z0,x1,z1,y,Math.min(h,setback),rnd.nextInt(4));
        roof(x0+in,z0+in,x1-in,z1-in,top,h,false);
    }
    /** A shop's glass at a tower's foot: bright panes, a dark frame now and then. */
    private BlockState shopGlass(int x,int y,int z){
        double h=Pal.hash(x,y/4,z);
        return h<0.15?PANEL:h<0.7?s(Blocks.LIGHT_GRAY_STAINED_GLASS):BANDS[4];
    }

    /**
     * A round tower (the fifth photo): r blocks round (cx, cz), its storeys in rings of white light between rings of
     * panel, a band of storeys wrapped in an LED wall on one side, a stepped disc on top with a ring of neon and a mast.
     */
    void roundTower(double cx,double cz,double r,int y,int h){
        h=Math.min(h,Cyber.ROOF_Y-y);
        int top=y+h,R=(int)Math.ceil(r+1);
        BlockState band=BANDS[rnd.nextBoolean()?4:1],ring=s(Blocks.LIGHT_GRAY_CONCRETE),neon=NEON[rnd.nextInt(NEON.length)];
        double wrapA=rnd.nextDouble()*2*Math.PI;int wrap0=y+h/3,wrap1=y+h/3+Math.min(24,h/3);
        for(int x=(int)Math.floor(cx-R);x<=cx+R;x++)for(int z=(int)Math.floor(cz-R);z<=cz+R;z++){
            double d=Math.hypot(x+0.5-cx,z+0.5-cz);
            if(d>r+0.5)continue;
            boolean shell=d>r-0.9;
            double a=Math.atan2(z+0.5-cz,x+0.5-cx);
            boolean wrap=Math.abs(Math.atan2(Math.sin(a-wrapA),Math.cos(a-wrapA)))<0.9;
            for(int yy=y+1;yy<=top;yy++){
                int up=yy-y;
                if(!shell){c.set(x,yy,z,(up-1)%3==0?PANEL:s(Blocks.AIR));continue;}
                BlockState b;
                if(up<=4)b=(up-1)%4==0?ring:shopGlass(x,yy,z);
                else if(wrap&&yy>=wrap0&&yy<=wrap1)b=LED[1];
                else b=(up-1)%3==0?ring:band;
                c.set(x,yy,z,b);
            }
        }
        // the stepped top: a disc a little wider, a smaller one over it, neon round both
        for(int k=0;k<2;k++){
            double rk=r+1-k*2.5;int yy=top+1+k;
            for(int x=(int)Math.floor(cx-R-1);x<=cx+R+1;x++)for(int z=(int)Math.floor(cz-R-1);z<=cz+R+1;z++){
                double d=Math.hypot(x+0.5-cx,z+0.5-cz);
                if(d<=rk+0.5)c.set(x,yy,z,d>rk-0.5?neon:ring);
            }
        }
        mast((int)Math.floor(cx),top+3,(int)Math.floor(cz),8+rnd.nextInt(6));
    }

    // ---------------------------------------------------------------- the fourth photo: arcades

    /**
     * An arcade block on its plot facing the avenue: upper storeys of dark brick standing out over a walk behind thick
     * pillars, shops lit inside behind glass under them, each with its sign over the door; signs hanging under the
     * arcade's ceiling on chains, a blade sign at a corner, air conditioners and lit windows up the walls, a billboard
     * in a frame of neon on the front, clutter on the roof.
     */
    void arcade(Frame f,int h){
        int W=f.W,D=f.D;
        h=Math.min(h,Cyber.ROOF_Y-f.y0);
        BlockState brick=Pal.pick(rnd,new BlockState[]{s(Blocks.DEEPSLATE_BRICKS),s(Blocks.POLISHED_BLACKSTONE_BRICKS),s(Blocks.DEEPSLATE_TILES),s(Blocks.GRAY_CONCRETE),s(Blocks.MUD_BRICKS)});
        BlockState pillar=s(Blocks.DEEPSLATE_BRICKS),trim=Pal.pick(rnd,new BlockState[]{s(Blocks.POLISHED_DEEPSLATE),s(Blocks.SMOOTH_BASALT),PANEL});
        int arc=7,depth=4;
        // the shops' floor and the arcade's walk
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){f.clear(x,1,z,x,h+2,z);f.set(x,0,z,z<depth?s(Blocks.POLISHED_BLACKSTONE_BRICKS):s(Blocks.SMOOTH_STONE));}
        // the body: from the arcade's ceiling up, standing out over the walk
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){
            boolean edge=x==0||x==W-1||z==0||z==D-1;
            for(int y=arc+1;y<=h;y++){
                int up=y-arc-1;
                if(!edge){f.set(x,y,z,up%4==0?s(Blocks.SPRUCE_PLANKS):s(Blocks.AIR));continue;}
                boolean front=z==0,win=(up%4==2||up%4==3)&&(front||z==D-1?x%3==1:z%3==1)&&x>0&&x<W-1;
                f.set(x,y,z,up%4==0?trim:win?(Pal.hash(f.wx(x,z),y,f.wz(x,z))<0.55?LIT:GLASS):brick);
            }
            f.set(x,arc,z,trim);
        }
        // the pillars along the front, the shops behind them
        for(int x=0;x<W;x++){
            boolean p=x%6==0||x==W-1;
            if(p)for(int y=1;y<arc;y++){f.set(x,y,0,pillar);if(x+1<W)f.set(Math.min(W-1,x+1),y,0,pillar);}
        }
        for(int x=1;x<W-1;x++)for(int y=1;y<arc;y++){
            boolean door=x%6==3;
            f.set(x,y,depth,y==1&&door||y==2&&door?s(Blocks.AIR):(y<=3?s(Blocks.GLASS_PANE):trim));
            for(int z=depth+1;z<D-1;z++)f.set(x,y,z,y==arc-1?(x%3==0?s(Blocks.SHROOMLIGHT):s(Blocks.SMOOTH_STONE)):s(Blocks.AIR));
        }
        for(int z=depth;z<D;z++)for(int y=1;y<arc;y++){f.set(0,y,z,brick);f.set(W-1,y,z,brick);}
        for(int x=0;x<W;x++)for(int y=1;y<arc;y++)f.set(x,y,D-1,brick);
        // counters and the shops' colours inside
        BlockState inside=Pal.pick(rnd,new BlockState[]{s(Blocks.ORANGE_TERRACOTTA),s(Blocks.WHITE_CONCRETE),s(Blocks.CYAN_TERRACOTTA),s(Blocks.RED_TERRACOTTA)});
        for(int x=1;x<W-1;x++){f.set(x,1,D-2,Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,true));for(int y=2;y<arc-1;y++)f.set(x,y,D-2,inside);}
        // a sign over each shop's front, on the arcade's fascia
        for(int x=1;x+4<W-1;x+=6){
            int w=pickWord(),len=word(WORDS[w]).length;
            if(x+len+1<W-1){int[] p=at(f,x+1,-1);wallSign(p[0],f.y0+arc,p[1],f.front,w);}
        }
        // signs hanging under the arcade, lanterns between
        for(int x=3;x<W-2;x+=6){int[] p=at(f,x,1);if(rnd.nextBoolean())hangingSign(p[0],f.y0+arc,p[1],pickShortWord());else c.hangingLantern(p[0],f.y0+arc-1,p[1],false);}
        // a blade sign at a front corner, air conditioners, a billboard
        int[] corner=at(f,rnd.nextBoolean()?0:W-1,-2);
        bladeSign(corner[0],f.y0+arc+5+rnd.nextInt(Math.max(1,h-arc-12)),corner[1],f.dir(Direction.SOUTH),pickWord());
        for(int y=arc+3;y<h-2;y+=4)for(int x=2;x<W-2;x+=5)if(Pal.hash(f.wx(x,0),y,f.wz(x,0))<0.35){
            int[] p=at(f,x,-1);if(c.air(p[0],f.y0+y,p[1]))c.set(p[0],f.y0+y,p[1],TB.facing("air_conditioner",f.front));
        }
        if(h>arc+14&&W>=12)billboard(f,arc+4,h);
        // the roof, closed, and its clutter
        for(int x=0;x<W;x++)for(int z=0;z<D;z++)f.set(x,h,z,trim);
        for(int k=0;k<3;k++){int x=2+rnd.nextInt(Math.max(1,W-5)),z=2+rnd.nextInt(Math.max(1,D-5));f.set(x,h+1,z,PANEL);f.set(x+1,h+1,z,PANEL);if(rnd.nextBoolean())f.set(x,h+2,z,s(Blocks.LIGHTNING_ROD));}
        for(int x=0;x<W;x++)for(int z=0;z<D;z++)if(x==0||x==W-1||z==0||z==D-1)f.set(x,h+1,z,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));
    }
    /** A billboard on a front: an LED wall in a frame of neon, its storeys between from and to. */
    private void billboard(Frame f,int from,int to){
        int w=Math.min(f.W-4,6+rnd.nextInt(6)),hh=Math.min(to-from-4,5+rnd.nextInt(5)),x0=(f.W-w)/2,y0=from+1+rnd.nextInt(Math.max(1,to-from-hh-3));
        BlockState led=LED[rnd.nextInt(LED.length)],frame=NEON[rnd.nextInt(NEON.length)];
        for(int x=x0-1;x<=x0+w;x++)for(int y=y0-1;y<=y0+hh;y++){
            boolean edge=x==x0-1||x==x0+w||y==y0-1||y==y0+hh;
            int[] p=at(f,x,-1);
            c.set(p[0],f.y0+y,p[1],edge?frame:led);
        }
    }

    /** An LED wall on a tower's face (0 north, 1 east, 2 south, 3 west), a block out from the wall, up its shaft. */
    void screen(int x0,int z0,int x1,int z1,int y,int shaft,int face){
        BlockState led=LED[rnd.nextInt(LED.length)];
        int len=(face%2==0?x1-x0:z1-z0)-6;
        if(len<8)return;
        int sw=Math.min(len,8+rnd.nextInt(Math.max(1,len-7))),sh=Math.min(shaft-14,10+rnd.nextInt(16));
        if(sh<6)return;
        int base=y+8+rnd.nextInt(Math.max(1,shaft-sh-10));
        int a=(face%2==0?x0:z0)+3+rnd.nextInt(Math.max(1,len-sw+1));
        for(int k=0;k<sw;k++)for(int j=0;j<sh;j++){
            int yy=base+j;
            switch(face){
                case 0->c.set(a+k,yy,z0-1,led);
                case 2->c.set(a+k,yy,z1+1,led);
                case 1->c.set(x1+1,yy,a+k,led);
                default->c.set(x0-1,yy,a+k,led);
            }
        }
    }

    // ---------------------------------------------------------------- roofs

    /**
     * A roof's furniture on x0..x1, z0..z1 at the roof's level: a parapet; on a tall tower a beam of light (a beacon on
     * iron, its glass coloured) or a helipad; tanks, boxes, masts.
     */
    void roof(int x0,int z0,int x1,int z1,int top,int h,boolean monolith){
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if((x==x0||x==x1||z==z0||z==z1)&&c.air(x,top+1,z)&&!c.air(x,top,z))c.set(x,top+1,z,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));
        int cx=(x0+x1)/2,cz=(z0+z1)/2,w=x1-x0,d=z1-z0;
        double pick=rnd.nextDouble();
        if(h>80&&pick<0.45&&top+6<=Cyber.MAST_Y){beacon(cx,top,cz,monolith?RED_GLASS:pickGlass());return;}
        else if(!monolith&&h>50&&pick<0.7&&w>=11&&d>=11)helipad(cx,top,cz,Math.min(w,d)/2-1);
        else if(w>=8&&d>=8){
            int n=1+rnd.nextInt(3);
            for(int k=0;k<n;k++)tank(x0+3+rnd.nextInt(Math.max(1,w-6)),top+1,z0+3+rnd.nextInt(Math.max(1,d-6)),1.6+rnd.nextDouble()*1.4,3+rnd.nextInt(3));
        }
        if(rnd.nextDouble()<0.6)mast(x0+2+rnd.nextInt(Math.max(1,w-3)),top+1,z0+2+rnd.nextInt(Math.max(1,d-3)),4+rnd.nextInt(8));
    }
    static final BlockState RED_GLASS=s(Blocks.RED_STAINED_GLASS);
    private BlockState pickGlass(){return Pal.pick(rnd,new BlockState[]{s(Blocks.CYAN_STAINED_GLASS),s(Blocks.MAGENTA_STAINED_GLASS),s(Blocks.LIGHT_BLUE_STAINED_GLASS),s(Blocks.PINK_STAINED_GLASS),s(Blocks.PURPLE_STAINED_GLASS)});}
    /** A beam of light into the sky (the fourth photo): a beacon on a square of iron, coloured glass over it. */
    void beacon(int x,int top,int z,BlockState glass){
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)c.set(x+dx,top+1,z+dz,s(Blocks.IRON_BLOCK));
        c.set(x,top+2,z,s(Blocks.BEACON));
        c.set(x,top+3,z,glass);
        for(int y=top+4;y<=Canvas.YMAX;y++)if(!c.air(x,y,z))c.set(x,y,z,s(Blocks.AIR));
        beacons++;
    }
    /** A helipad: a white disc with an H in yellow on black, lights round its rim. */
    void helipad(int cx,int top,int cz,int r){
        for(int x=cx-r;x<=cx+r;x++)for(int z=cz-r;z<=cz+r;z++){
            double d=Math.hypot(x-cx,z-cz);
            if(d>r+0.4)continue;
            int dx=x-cx,dz=z-cz;
            boolean hMark=d<r-1.5&&(Math.abs(dx)==r/3&&Math.abs(dz)<=r/2||dz==0&&Math.abs(dx)<=r/3);
            c.set(x,top+1,z,d>r-0.6?((x+z)%3==0?s(Blocks.SEA_LANTERN):s(Blocks.WHITE_CONCRETE)):hMark?s(Blocks.YELLOW_CONCRETE):d>r-1.6?s(Blocks.WHITE_CONCRETE):s(Blocks.BLACK_CONCRETE));
        }
    }
    /** A round tank on a roof: rings of grey concrete, iron at its top and foot. */
    void tank(int cx,int y,int cz,double r,int h){
        h=Math.min(h,Cyber.MAST_Y+1-y);
        if(h<2)return;
        int R=(int)Math.ceil(r);
        for(int x=cx-R;x<=cx+R;x++)for(int z=cz-R;z<=cz+R;z++){
            if(Math.hypot(x-cx,z-cz)>r+0.3)continue;
            for(int k=0;k<h;k++)if(c.air(x,y+k,z))c.set(x,y+k,z,k==0||k==h-1?s(Blocks.IRON_BLOCK):s(Blocks.LIGHT_GRAY_CONCRETE));
        }
    }
    /** A mast: iron bars with a red light at its tip, kept under the clouds. */
    void mast(int x,int y,int z,int h){
        h=Math.min(h,Cyber.MAST_Y-1-y);
        if(h<3)return;
        for(int k=0;k<h;k++){if(!c.air(x,y+k,z)&&k>0)return;c.set(x,y+k,z,s(Blocks.IRON_BARS));}
        c.set(x,y+h,z,RED);
    }
}
