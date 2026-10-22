package dev.sangwoo.botandtown;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * The cyber city's ring and what hangs under and over it (2.49.0, from the user's photos). The ring (the third photo):
 * a deck forty-two blocks up circling the plaza, a road on it either side of a monorail's beam with a train standing on
 * it, walks at its edges behind rails of glass, a line of light round both its faces, ribs and lamps under it; huge
 * pillars of dark brick under it with lines of neon up them. Under it the undercity market (the second photo): narrow
 * shophouses of whatever came to hand along both sides of the market street with their shops lit, blade signs and air
 * conditioners, stalls between the pillars, lanterns hanging on long chains from the deck, purple light growing at the
 * walls' feet. Over the plaza a hologram of an hourglass, and over the ring's south side a koi of light arching (the
 * fifth photo's holograms; the island's own figures, not the photo's).
 */
final class CyberRing {
    private final Townscape t;private final Canvas c;private final Random rnd;private final CyberTowers towers;
    static final int MX=Cyber.MID_X,MZ=Cyber.MID_Z,R=Cyber.RING,HALF=Cyber.RING_HALF,DECK=Cyber.DECK,Y=Cyber.Y;
    static final BlockState HOLO_PINK=TB.of("hologram_pink"),HOLO_CYAN=TB.of("hologram_cyan"),WET=TB.of("wet_asphalt"),PAVING=TB.of("cyber_paving");
    int pillars,shophouses,stalls,lanterns;
    CyberRing(Townscape t,Random rnd,CyberTowers towers){this.t=t;c=t.c;this.rnd=rnd;this.towers=towers;}
    private static BlockState s(Block b){return b.getDefaultState();}
    static double r(double x,double z){return Math.hypot(x+0.5-MX,z+0.5-MZ);}
    static double ang(double x,double z){return Math.atan2(z+0.5-MZ,x+0.5-MX);}

    // ---------------------------------------------------------------- the deck

    void deck(){
        int R1=R+HALF;
        for(int x=MX-R1-1;x<=MX+R1+1;x++)for(int z=MZ-R1-1;z<=MZ+R1+1;z++){
            double d=r(x,z)-R;
            if(Math.abs(d)>HALF+0.5)continue;
            double a=ang(x,z);
            int along=(int)Math.floor(a*R);
            boolean edge=Math.abs(d)>HALF-0.6;
            // the top: a road either side of the monorail's beam, walks at the edges behind glass
            c.set(x,DECK,z,Math.abs(d)<1.2?s(Blocks.SMOOTH_STONE):Math.abs(d)<HALF-2.2?WET:PAVING);
            if(Math.abs(d)<1.2)c.set(x,DECK+1,z,s(Blocks.LIGHT_GRAY_CONCRETE));
            if(edge){c.set(x,DECK+1,z,s(Blocks.LIGHT_BLUE_STAINED_GLASS));c.set(x,DECK+2,z,Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,false));}
            // its body: dark, a line of light round both faces
            c.set(x,DECK-1,z,edge?CyberTowers.CYAN:s(Blocks.POLISHED_DEEPSLATE));
            // ribs under it: along its edges and across it every six blocks, a lamp in the middle of every other bay
            boolean rib=edge||Math.floorMod(along,6)==0;
            if(rib)c.set(x,DECK-2,z,s(Blocks.POLISHED_BLACKSTONE_BRICKS));
            else if(Math.abs(d)<0.6&&Math.floorMod(along,12)==3)c.set(x,DECK-2,z,s(Blocks.SEA_LANTERN));
            c.own(x,z,Cyber.BAND_CODE);
        }
    }

    /** The pillars under the deck: one every twenty-four blocks or so round it, wherever no road runs. */
    void pillars(){
        int n=(int)Math.round(2*Math.PI*R/24);
        for(int k=0;k<n;k++){
            double a=k*2*Math.PI/n+0.07;
            int px=(int)Math.floor(MX+Math.cos(a)*R),pz=(int)Math.floor(MZ+Math.sin(a)*R);
            if(t.roads.near(px,pz,5))continue;
            pillar(px,pz);pillars++;
        }
    }
    private void pillar(int cx,int cz){
        BlockState brick=s(Blocks.DEEPSLATE_BRICKS),corner=s(Blocks.POLISHED_DEEPSLATE),cap=s(Blocks.POLISHED_BLACKSTONE_BRICKS);
        BlockState neon=rnd.nextBoolean()?CyberTowers.CYAN:CyberTowers.PURPLE;
        for(int x=cx-3;x<=cx+3;x++)for(int z=cz-3;z<=cz+3;z++){
            int dx=Math.abs(x-cx),dz=Math.abs(z-cz),g=c.ground(x,z);
            for(int y=g-2;y<=DECK-2;y++){
                boolean foot=y<=Y+1,head=y>=DECK-5;
                if(dx==3||dz==3){if(foot||head)c.set(x,y,z,cap);continue;}
                boolean cor=dx==2&&dz==2;
                boolean strip=(dx==0&&dz==2||dz==0&&dx==2)&&y>Y+2&&y<DECK-8;
                c.set(x,y,z,strip?neon:cor?corner:brick);
            }
        }
        // purple light at its foot
        for(int k=0;k<4;k++){
            int x=cx+(k%2==0?-4:4),z=cz+(k<2?-1:1)+rnd.nextInt(3)-1;
            if(c.air(x,Y+1,z)&&!t.roads.on(x,z))c.set(x,Y+1,z,Blocks.AMETHYST_CLUSTER.getDefaultState().with(Properties.FACING,Direction.UP));
        }
    }

    /** The monorail's train standing on its beam: three cars of white with a band of windows and a line of neon. */
    void train(double a0){
        double carLen=12,gap=1.5;
        for(int car=0;car<3;car++){
            double s0=a0*R+car*(carLen+gap),s1=s0+carLen;
            int R1=R+3;
            for(int x=MX-R1;x<=MX+R1;x++)for(int z=MZ-R1;z<=MZ+R1;z++){
                double d=r(x,z)-R;
                if(Math.abs(d)>2.2)continue;
                double s=ang(x,z)*R;
                // the arc's length measured the same way round as a0
                if(s<s0-Math.PI*R)s+=2*Math.PI*R;
                if(s<s0||s>s1)continue;
                boolean end=s<s0+1||s>s1-1,nose=car==0&&s<s0+1.5||car==2&&s>s1-1.5;
                c.set(x,DECK+2,z,s(Blocks.WHITE_CONCRETE));
                c.set(x,DECK+3,z,end?s(Blocks.WHITE_CONCRETE):Math.abs(d)>1.4?s(Blocks.LIGHT_BLUE_STAINED_GLASS):s(Blocks.LIGHT_GRAY_CONCRETE));
                c.set(x,DECK+4,z,nose?Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,false):Math.abs(d)<0.8?CyberTowers.CYAN:s(Blocks.WHITE_CONCRETE));
            }
        }
    }

    /** Lanterns hanging under the deck on long chains, over the market street. */
    void lanterns(){
        int n=(int)Math.round(2*Math.PI*R/7);
        for(int k=0;k<n;k++){
            double a=k*2*Math.PI/n+rnd.nextDouble()*0.02,rr=R+(rnd.nextDouble()-0.5)*(2*HALF-4);
            int x=(int)Math.floor(MX+Math.cos(a)*rr),z=(int)Math.floor(MZ+Math.sin(a)*rr);
            int len=6+rnd.nextInt(10),y=DECK-3;
            boolean clear=true;
            for(int k2=0;k2<=len+1;k2++)if(!c.air(x,y-k2,z)){clear=false;break;}
            if(!clear)continue;
            for(int k2=0;k2<len;k2++)c.set(x,y-k2,z,Canvas.chain(Direction.Axis.Y));
            c.hangingLantern(x,y-len,z,rnd.nextInt(4)==0);
            lanterns++;
        }
    }

    // ---------------------------------------------------------------- the market under the deck

    /**
     * The market: shophouses along both sides of the market street under the deck (an inner row and an outer row, their
     * fronts to the street), stalls along the street between the pillars, the street paved in dark worn stone.
     */
    void market(){
        int R0=R-HALF-6,R1=R+HALF+6;
        // the market street's paving
        for(int x=MX-R1;x<=MX+R1;x++)for(int z=MZ-R1;z<=MZ+R1;z++){
            double d=r(x,z);
            if(d<R0||d>R1||Plan.zone(x,z)!=Plan.CYBER||t.roads.on(x,z))continue;
            int i=Canvas.idx(x,z);
            if(t.terrain.kind[i]!=Terrain.LAND)continue;
            double h=Pal.hash(x,61,z);
            c.set(x,c.ground(x,z),z,h<0.45?s(Blocks.POLISHED_BLACKSTONE_BRICKS):h<0.7?s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS):h<0.85?s(Blocks.DEEPSLATE_TILES):h<0.93?s(Blocks.TUFF):s(Blocks.MOSS_BLOCK));
        }
        // the shophouses: an inner row (fronts outward) and an outer row (fronts inward)
        for(int row=0;row<2;row++){
            double rr=row==0?R-HALF-2:R+HALF+2;
            double step=9.0/rr,a=rnd.nextDouble()*step;
            while(a<2*Math.PI){
                int w=6+rnd.nextInt(4),dep=6+rnd.nextInt(3);
                double ca=Math.cos(a),sa=Math.sin(a);
                // the front faces the market street: outward for the inner row, inward for the outer
                double fx=row==0?ca:-ca,fz=row==0?sa:-sa;
                Direction front=Math.abs(fx)>=Math.abs(fz)?(fx>0?Direction.EAST:Direction.WEST):(fz>0?Direction.SOUTH:Direction.NORTH);
                double bx=MX+ca*(rr-(row==0?dep/2.0:-dep/2.0)),bz=MZ+sa*(rr-(row==0?dep/2.0:-dep/2.0));
                boolean ns=front.getAxis()==Direction.Axis.Z;
                int hw=ns?w:dep,hd=ns?dep:w;
                int x0=(int)Math.round(bx-hw/2.0),z0=(int)Math.round(bz-hd/2.0),x1=x0+hw-1,z1=z0+hd-1;
                if(free(x0,z0,x1,z1)){shophouse(new Frame(c,x0,z0,x1,z1,front,Y));c.claim(x0,z0,x1,z1,Cyber.BAND_CODE);shophouses++;}
                a+=(w+1.5+rnd.nextInt(3))/rr;
            }
        }
        // stalls along the street
        int n=(int)Math.round(2*Math.PI*R/11);
        for(int k=0;k<n;k++){
            double a=(k+0.5)*2*Math.PI/n,rr=R+(k%2==0?-3:3);
            int x=(int)Math.floor(MX+Math.cos(a)*rr),z=(int)Math.floor(MZ+Math.sin(a)*rr);
            if(stall(x,z,a))stalls++;
        }
    }
    /** Moss carpeting the market street's moss where nothing stands on it (once everything is built). */
    void moss(){
        int R1=R+HALF+6;
        for(int x=MX-R1;x<=MX+R1;x++)for(int z=MZ-R1;z<=MZ+R1;z++){
            int g=c.ground(x,z);
            if(c.get(x,g,z).isOf(Blocks.MOSS_BLOCK)&&c.air(x,g+1,z)&&Pal.hash(x,63,z)<0.7)c.set(x,g+1,z,s(Blocks.MOSS_CARPET));
        }
    }
    /** Whether a rectangle under the deck is the city's open ground, off the roads and every pillar and building. */
    private boolean free(int x0,int z0,int x1,int z1){
        for(int x=x0-1;x<=x1+1;x++)for(int z=z0-1;z<=z1+1;z++){
            if(!Canvas.inPlan(x,z)||Plan.zone(x,z)!=Plan.CYBER||t.roads.near(x,z,1))return false;
            int i=Canvas.idx(x,z);
            if(t.terrain.kind[i]!=Terrain.LAND||Plan.shore(x,z)>-8)return false;
            if(c.owner[i]!=0&&c.owner[i]!=Cyber.BAND_CODE)return false;
            if(c.owner[i]==Cyber.BAND_CODE&&(r(x,z)<R-HALF-0.5||r(x,z)>R+HALF+0.5))return false;
            for(int y=Y+1;y<=Y+12;y++)if(!c.air(x,y,z))return false;
        }
        return true;
    }

    /**
     * A shophouse of the undercity (the second photo): two or three storeys of whatever came to hand (brick, mud brick,
     * rusty iron, tired concrete, dark panel), its shop open at the front and lit inside under an awning, a sign over
     * it or a blade sign at its corner, lit windows, an air conditioner, a pipe up its side, clutter on its roof.
     */
    private void shophouse(Frame f){
        int W=f.W,D=f.D,storeys=2+rnd.nextInt(2),h=storeys*4;
        BlockState wall=Pal.pick(rnd,new BlockState[]{s(Blocks.BRICKS),s(Blocks.MUD_BRICKS),TB.RUSTY,TB.WEATHERED,CyberTowers.PANEL,s(Blocks.DEEPSLATE_BRICKS),s(Blocks.CYAN_TERRACOTTA),TB.CORRUGATED});
        BlockState inside=Pal.pick(rnd,new BlockState[]{s(Blocks.ORANGE_TERRACOTTA),s(Blocks.WHITE_CONCRETE),s(Blocks.YELLOW_TERRACOTTA),s(Blocks.LIGHT_BLUE_TERRACOTTA)});
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){
            boolean edge=x==0||x==W-1||z==0||z==D-1;
            f.set(x,0,z,s(Blocks.POLISHED_BLACKSTONE));
            for(int y=1;y<=h;y++){
                if(!edge){f.set(x,y,z,y%4==0?s(Blocks.SPRUCE_PLANKS):s(Blocks.AIR));continue;}
                int st=(y-1)/4,iy=(y-1)%4;
                boolean front=z==0;
                if(st==0&&front&&x>0&&x<W-1){f.set(x,y,z,iy==3?s(Blocks.POLISHED_BLACKSTONE):s(Blocks.AIR));continue;}
                boolean win=st>0&&(iy==1||iy==2)&&x>0&&x<W-1&&(front||z==D-1)&&x%3!=0;
                f.set(x,y,z,win?(Pal.hash(f.wx(x,z),y,f.wz(x,z))<0.6?CyberTowers.BANDS[Pal.hash(f.wx(x,z),7,f.wz(x,z))<0.5?0:4]:CyberTowers.GLASS):wall);
            }
        }
        // the shop: lit inside, a counter, its back wall coloured
        for(int x=1;x<W-1;x++){
            f.set(x,3,1,s(Blocks.SHROOMLIGHT));
            f.set(x,1,D-2,Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,true));
            for(int y=1;y<=3;y++)f.set(x,y,D-1,y==2?inside:wall);
        }
        for(int z=1;z<D-1;z++)for(int y=1;y<=3;y++){f.set(1,y,z,inside);f.set(W-2,y,z,inside);}
        // an awning over the front, a sign over it or a blade sign
        BlockState awning=Pal.pick(rnd,new BlockState[]{s(Blocks.RED_WOOL),s(Blocks.CYAN_WOOL),s(Blocks.PURPLE_WOOL),s(Blocks.ORANGE_WOOL),s(Blocks.BLACK_WOOL)});
        for(int x=0;x<W;x++){int[] p={f.wx(x,-1),f.wz(x,-1)};if(c.air(p[0],f.y0+4,p[1]))c.set(p[0],f.y0+4,p[1],Canvas.slab(Blocks.DARK_OAK_SLAB,true));
            int[] q={f.wx(x,-1),f.wz(x,-1)};if(c.air(q[0],f.y0+5,q[1])&&x%2==0)c.set(q[0],f.y0+5,q[1],awning.getBlock()==Blocks.BLACK_WOOL?Blocks.BLACK_CARPET.getDefaultState():carpetOf(awning));}
        int w=towers.pickWord();
        if(rnd.nextBoolean()&&W>=towers.word(CyberTowers.WORDS[w]).length+2)towers.wallSign(f.wx(1,0),f.y0+5,f.wz(1,0),f.front,w);
        else towers.bladeSign(f.wx(rnd.nextBoolean()?0:W-1,-2),f.y0+h-1,f.wz(rnd.nextBoolean()?0:W-1,-2),f.dir(Direction.SOUTH),w);
        // an air conditioner, a pipe up a side, the roof's clutter
        if(storeys>1){int x=1+rnd.nextInt(W-2);int[] p={f.wx(x,-1),f.wz(x,-1)};if(c.air(p[0],f.y0+6,p[1]))c.set(p[0],f.y0+6,p[1],TB.facing("air_conditioner",f.front));}
        int px=rnd.nextBoolean()?-1:W;
        for(int y=1;y<=h+1;y++){int[] p={f.wx(px,1),f.wz(px,1)};if(c.air(p[0],f.y0+y,p[1]))c.set(p[0],f.y0+y,p[1],s(Blocks.IRON_BARS));}
        for(int x=0;x<W;x++)for(int z=0;z<D;z++)if(x==0||x==W-1||z==0||z==D-1)f.set(x,h+1,z,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));
        if(rnd.nextBoolean())towers.tank(f.wx(W/2,D/2),f.y0+h+1,f.wz(W/2,D/2),1.4,3);
        // purple light at the wall's foot
        for(int x=0;x<W;x+=3){int[] p={f.wx(x,D),f.wz(x,D)};if(c.air(p[0],f.y0+1,p[1])&&Pal.hash(p[0],62,p[1])<0.4)c.set(p[0],f.y0+1,p[1],Blocks.SMALL_AMETHYST_BUD.getDefaultState().with(Properties.FACING,Direction.UP));}
    }
    private static BlockState carpetOf(BlockState wool){
        Block b=wool.getBlock();
        return (b==Blocks.RED_WOOL?Blocks.RED_CARPET:b==Blocks.CYAN_WOOL?Blocks.CYAN_CARPET:b==Blocks.PURPLE_WOOL?Blocks.PURPLE_CARPET:Blocks.ORANGE_CARPET).getDefaultState();
    }

    /** A stall in the market street: a counter, posts, an awning of wool, a lantern, crates beside it. */
    private boolean stall(int x,int z,double a){
        Direction front=Math.abs(Math.cos(a))>=Math.abs(Math.sin(a))?(Math.cos(a)>0?Direction.EAST:Direction.WEST):(Math.sin(a)>0?Direction.SOUTH:Direction.NORTH);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            if(t.roads.near(x+dx,z+dz,1)||!c.air(x+dx,Y+1,z+dz)||!c.air(x+dx,Y+4,z+dz))return false;
            int i=Canvas.idx(x+dx,z+dz);
            if(c.owner[i]!=0&&c.owner[i]!=Cyber.BAND_CODE)return false;
        }
        var f=new Frame(c,x-1,z-1,x+1,z+1,front,Y);
        BlockState awning=Pal.pick(rnd,new BlockState[]{s(Blocks.RED_WOOL),s(Blocks.CYAN_WOOL),s(Blocks.PURPLE_WOOL),s(Blocks.ORANGE_WOOL),s(Blocks.WHITE_WOOL)});
        for(int bx=0;bx<3;bx++){
            f.set(bx,1,0,Canvas.slab(Blocks.SPRUCE_SLAB,true));
            f.set(bx,4,0,awning);f.set(bx,4,1,awning);f.set(bx,4,2,awning);
        }
        for(int bx:new int[]{0,2})for(int y=1;y<=3;y++)f.set(bx,y,2,s(Blocks.SPRUCE_FENCE));
        for(int y=2;y<=3;y++){f.set(0,y,0,s(Blocks.SPRUCE_FENCE));f.set(2,y,0,s(Blocks.SPRUCE_FENCE));}
        f.hangingLantern(1,3,1,false);
        f.set(1,1,2,s(Blocks.BARREL));
        if(rnd.nextBoolean())f.set(3,1,1,s(Blocks.BARREL));
        c.claim(x-2,z-2,x+2,z+2,Cyber.BAND_CODE);
        return true;
    }

    // ---------------------------------------------------------------- holograms

    /**
     * The hourglass over the plaza (the island's own figure, as the fifth photo's holograms stand over its city): two
     * bulbs of cyan light meeting at a narrow neck, the upper one half full of pink sand, a thread of it falling through
     * the neck onto a heap in the lower one, a frame of white light round it.
     */
    void hourglass(int cx,int y0,int cz){
        int H=36,mid=y0+H/2;
        for(int y=y0;y<=y0+H;y++){
            double u=Math.abs(y-mid)/(H/2.0);
            double rr=1.6+10.5*Math.pow(u,0.75)*(1-0.25*u*u*u);
            int Rr=(int)Math.ceil(rr)+1;
            for(int x=cx-Rr;x<=cx+Rr;x++)for(int z=cz-Rr;z<=cz+Rr;z++){
                double d=Math.hypot(x-cx,z-cz);
                if(d>rr+0.5)continue;
                boolean shell=d>rr-0.8||y==y0||y==y0+H;
                boolean sand=y>mid&&y<mid+H/2*0.62&&!shell||y<mid&&y<y0+1+(int)Math.round(5*(1-d/10.0))&&!shell||y<=mid+H/2&&d<0.8&&y<mid+H/4;
                if(shell)c.set(x,y,z,HOLO_CYAN);
                else if(sand)c.set(x,y,z,HOLO_PINK);
            }
        }
        // the frame: four posts of white light and caps
        for(int k=0;k<4;k++){
            int x=cx+(k%2==0?-12:12),z=cz+(k<2?-12:12);
            for(int y=y0-1;y<=y0+H+1;y++)c.set(x,y,z,CyberTowers.WHITE);
        }
        for(int x=cx-12;x<=cx+12;x++)for(int z=cz-12;z<=cz+12;z++){
            boolean ring=Math.abs(x-cx)==12||Math.abs(z-cz)==12;
            if(ring){c.set(x,y0-1,z,CyberTowers.WHITE);c.set(x,y0+H+1,z,CyberTowers.WHITE);}
        }
    }

    /** A koi of light arching over the ring (the fifth photo's holograms): its body along a curve, fins, a forked tail. */
    void koi(double x0,double y0,double z0,double x1,double y1,double z1,double rise){
        int n=160;
        double px=0,pz=0;
        for(int k=0;k<=n;k++){
            double s=k/(double)n,u=1-s;
            double mx=(x0+x1)/2,my=Math.max(y0,y1)+rise,mz=(z0+z1)/2;
            double x=u*u*x0+2*u*s*mx+s*s*x1,y=u*u*y0+2*u*s*my+s*s*y1,z=u*u*z0+2*u*s*mz+s*s*z1;
            // thick behind the head, thinning to the tail
            double r=s<0.12?2.2+s*22:4.8*Math.pow(1-(s-0.12)/0.88,0.8)+0.6;
            ball(x,y,z,r,s<0.06?HOLO_CYAN:HOLO_PINK);
            if(k==Math.round(n*0.35)||k==Math.round(n*0.5)){
                // a dorsal fin and two pectoral fins
                for(int j=1;j<=5;j++)ball(x,y+r+j*0.8,z,1.2-j*0.15,HOLO_CYAN);
            }
            px=x;pz=z;
            if(k==n){
                for(int j=1;j<=8;j++){ball(x+j*0.6,y+j*0.7,z,1.6-j*0.12,HOLO_CYAN);ball(x+j*0.6,y-j*0.7,z,1.6-j*0.12,HOLO_CYAN);}
            }
        }
    }
    private void ball(double x,double y,double z,double r,BlockState s){
        int R=(int)Math.ceil(r);
        for(int dx=-R;dx<=R;dx++)for(int dy=-R;dy<=R;dy++)for(int dz=-R;dz<=R;dz++){
            int bx=(int)Math.floor(x)+dx,by=(int)Math.floor(y)+dy,bz=(int)Math.floor(z)+dz;
            double ex=bx+0.5-x,ey=by+0.5-y,ez=bz+0.5-z;
            if(ex*ex+ey*ey+ez*ez>r*r)continue;
            if(c.air(bx,by,bz)||c.get(bx,by,bz).isOf(HOLO_PINK.getBlock()))c.set(bx,by,bz,s);
        }
    }
}
