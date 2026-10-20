package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The houses along the photo street (photo 1, tools/map/town/PHOTO_SCENES.md). In front of the photo's camera they
 * are the photo's own. On the left (east): a black sliding gate between rough stone pillars, a white letterbox in a
 * pillar, ivy over a low wall, and behind them a two-storey plaster house with dark timber, big curtained windows, a
 * tiled eave and an air conditioner; then the Yamada house with its name board, black railings over a low wall,
 * potted flowers by its gate and a cherry tree over the road. On the right (west): a long block wall under a dark
 * slat fence with a cherry tree leaning over it and a balconied house behind, a tall stone pillar, a sign post and a
 * red vending machine against a block wall. Further down both sides the same kinds of houses, walls and gates, mixed.
 */
final class PhotoStreet {
    private final Townscape t;private final Canvas c;private final Random rnd;
    int houses;
    PhotoStreet(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    /** A plot's street side: its front column, which way is in from the street, its level. */
    private static final class Side {
        final Lot l;final boolean east;final int fx,in,y;
        Side(Lot l){this.l=l;east=l.front==Direction.WEST;fx=east?l.x0:l.x1;in=east?1:-1;y=l.y;}
        /** The column k blocks in from the street. */
        int x(int k){return fx+in*k;}
        /** A frame for a building k0..k1 blocks in from the street, z0..z1 along it. */
        Frame frame(Canvas c,int k0,int k1,int z0,int z1){
            int a=x(k0),b=x(k1);
            return new Frame(c,Math.min(a,b),z0,Math.max(a,b),z1,l.front,y);
        }
    }

    void build(){
        for(Lot l:t.layout.lots){
            if(!l.zone.equals("photo"))continue;
            boolean east=l.front==Direction.WEST;
            yard(l);
            if(east&&l.z0==Layout.PHOTO_EAST[1][0])gateHouse(l);
            else if(east&&l.z0==Layout.PHOTO_EAST[2][0])yamada(l);
            else if(!east&&l.z0==Layout.PHOTO_WEST[0][0])fenceHouse(l);
            else if(!east&&l.z0==Layout.PHOTO_WEST[1][0])vendingCorner(l);
            else house(l);
            l.use="photo";houses++;
        }
        Town.log("photo street: "+houses+" houses");
    }

    /** Levels the plot: grass, a strip of gravel inside the front wall, everything above cleared, a block wall on its north side. */
    private void yard(Lot l){
        var s=new Side(l);
        for(int x=l.x0;x<=l.x1;x++)for(int z=l.z0;z<=l.z1;z++){
            c.clear(x,l.y+1,z,x,l.y+14,z);
            int k=(x-s.fx)*s.in;
            c.set(x,l.y,z,k>=1&&k<=2?s(Blocks.GRAVEL):s(Blocks.GRASS_BLOCK));
        }
        // the front wall stands straight up from the sidewalk: blocks from the sidewalk's level to the plot's
        for(int z=l.z0;z<=l.z1;z++){
            int sg=c.ground(s.x(-1),z);
            for(int y=Math.min(sg,l.y);y<=l.y;y++)c.set(s.x(0),y,z,TB.BLOCK_WALL);
        }
        for(int k=1;k<=l.x1-l.x0;k++){c.set(s.x(k),l.y+1,l.z0,TB.BLOCK_WALL);c.set(s.x(k),l.y+2,l.z0,TB.wallCap());}
    }

    // ---------------------------------------------------------------- the photo's own houses

    /** Left, round the camera: the gate, the pillars and letterbox, the ivy wall, the plaster house with its eave. */
    private void gateHouse(Lot l){
        var s=new Side(l);int y=s.y,fx=s.x(0);
        pillar(fx,y,l.z0,2);
        for(int z=l.z0+1;z<=-68;z++){c.set(fx,y+1,z,TB.GATE);c.set(fx,y+2,z,TB.GATE);}
        pillar(fx,y,-67,2);
        c.set(fx,y+2,-67,TB.facing("letterbox",l.front));
        for(int z=-66;z<=-64;z++){
            c.set(fx,y+1,z,TB.BLOCK_WALL);
            for(int h=1;h<=2;h++)c.set(s.x(1),y+h,z,Canvas.leaves(Blocks.AZALEA_LEAVES));
            if(z!=-65&&c.air(fx-s.in,y+1,z))c.vine(fx-s.in,y+1,z,s.east?Direction.EAST:Direction.WEST);
            c.set(fx,y+2,z,Canvas.leaves(Blocks.OAK_LEAVES));
        }
        railWall(fx,y,-63,l.z1);
        // a paved path from the gate to the door
        for(int k=1;k<=2;k++)for(int z=-72;z<=-70;z++)c.set(s.x(k),y,z,Blocks.STONE_BRICKS.getDefaultState());
        Frame f=s.frame(c,3,12,-73,-61);
        body(f,true,false,true,2);
        // the tiled porch reaching out over the path to the gate, on a dark post
        int door=f.W/2;
        for(int bx=door-2;bx<=door+2;bx++)for(int bz=-2;bz<=-1;bz++)if(f.air(bx,3,bz))f.set(bx,3,bz,Kit.along(Canvas.slab(Kit.KAWARA.slab(),true),Direction.Axis.Z));
        f.set(door+2,1,-2,Canvas.axis(Blocks.STRIPPED_DARK_OAK_LOG,Direction.Axis.Y));f.set(door+2,2,-2,Canvas.axis(Blocks.STRIPPED_DARK_OAK_LOG,Direction.Axis.Y));
        f.set(door+1,2,-1,Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING,true));
        f.wallSign(door-1,2,-1,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,false,"","汐見");
        Kit.airCon(f,f.W-3,6,-1,Direction.NORTH);
        Kit.airCon(f,-1,1,f.D-2,Direction.WEST);
        for(int k=3;k<=12;k++)c.set(s.x(k),y+1,-74,TB.BLOCK_WALL);
    }

    /** Left, after it: the Yamada house, its name board out from the corner, railings, flowers, a cherry tree. */
    private void yamada(Lot l){
        var s=new Side(l);int y=s.y,fx=s.x(0);
        railWall(fx,y,l.z0,-54);
        pillar(fx,y,-53,2);pillar(fx,y,-50,2);
        railWall(fx,y,-49,l.z1);
        c.set(s.x(1),y+1,-54,Blocks.POTTED_PINK_TULIP.getDefaultState());c.set(s.x(1),y+1,-49,Blocks.POTTED_FLOWERING_AZALEA_BUSH.getDefaultState());
        c.set(s.x(1),y+1,-52,Blocks.POTTED_PINK_TULIP.getDefaultState());
        Frame f=s.frame(c,2,10,-57,-48);
        body(f,true,false,false,2);
        // the name board (表札) standing out from the front's north corner on the upper floor, read down the street
        int bx=s.x(1);
        c.set(bx,y+5,-57,Blocks.DARK_OAK_PLANKS.getDefaultState());c.set(bx,y+6,-57,Blocks.DARK_OAK_PLANKS.getDefaultState());
        c.wallSign(bx,y+6,-58,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,false,"や","ま","だ");
        c.wallSign(bx,y+5,-58,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,false,"","山田","","");
        c.wallSign(bx,y+6,-56,Blocks.DARK_OAK_WALL_SIGN,Direction.SOUTH,DyeColor.WHITE,false,"や","ま","だ");
        t.nature.cherry(s.x(1),y+1,-50,6);
    }

    /** Right, round the camera: the long wall and slat fence, a cherry tree over it, the balconied house behind. */
    private void fenceHouse(Lot l){
        var s=new Side(l);int y=s.y,fx=s.x(0);
        for(int z=l.z0;z<=l.z1;z++)slatWall(fx,y,z);
        Frame f=s.frame(c,4,13,l.z0+1,l.z1-1);
        body(f,false,true,true,2);
        t.nature.cherry(s.x(1),y+1,-66,6);
        for(int k=1;k<=3;k++)if(rnd.nextBoolean())c.set(s.x(k),y+1,l.z1-1,Canvas.leaves(Blocks.AZALEA_LEAVES));
    }

    /** Right, after it: the tall stone pillar, the drive, the sign post, the vending machine against its wall. */
    private void vendingCorner(Lot l){
        var s=new Side(l);int y=s.y,fx=s.x(0);
        pillar(fx,y,l.z0,2);
        // the sign post on the sidewalk by the drive: a thin pipe with a red sign on top
        int px=fx-s.in,pz=-61,pg=c.ground(px,pz);
        c.set(px,pg+1,pz,TB.PIPE);c.set(px,pg+2,pz,TB.PIPE);
        c.standingSign(px,pg+3,pz,Blocks.MANGROVE_SIGN,8,DyeColor.WHITE,true,"","止まれ","STOP","");
        // the vending machine in the wall line, its back to a taller block wall
        for(int z=-60;z<=-58;z++){c.set(s.x(1),y+1,z,TB.BLOCK_WALL);c.set(s.x(1),y+2,z,TB.BLOCK_WALL);c.set(s.x(1),y+3,z,TB.wallCap());}
        c.set(fx,y+1,-59,TB.vending(l.front,false));c.set(fx,y+2,-59,TB.vending(l.front,true));
        t.street.machines++;
        for(int z=-58;z<=l.z1;z++)slatWall(fx,y,z);
        // a small house set back past the drive (open from the pillar to the machine)
        Frame f=s.frame(c,4,12,l.z0,l.z1);
        body(f,false,false,false,2);
        for(int k=1;k<=3;k++)for(int z=-62;z<=-60;z++)c.set(s.x(k),y,z,Blocks.SMOOTH_STONE.getDefaultState());
    }

    // ---------------------------------------------------------------- the rest of the street

    /**
     * A house further down the street: a front of block wall under a slat fence or a black railing, or a low wall
     * and hedge, a gate between stone pillars with the letterbox in one; the house set back behind it; a cherry tree
     * now and then, a vending machine at the odd corner.
     */
    private void house(Lot l){
        var s=new Side(l);int y=s.y,fx=s.x(0);
        int W=l.z1-l.z0+1;
        int gate=l.z0+1+rnd.nextInt(Math.max(1,W-4)),kind=rnd.nextInt(4);
        for(int z=l.z0;z<=l.z1;z++){
            if(z>=gate&&z<=gate+1)continue;
            switch(kind){
                case 0->slatWall(fx,y,z);
                case 1->{c.set(fx,y+1,z,TB.BLOCK_WALL);c.set(fx,y+2,z,TB.BALCONY);}
                case 2->{c.set(fx,y+1,z,TB.BLOCK_WALL);c.set(fx,y+2,z,TB.wallCap());c.set(s.x(1),y+1,z,Canvas.leaves(Blocks.AZALEA_LEAVES));}
                default->{c.set(fx,y+1,z,TB.BLOCK_WALL);c.set(fx,y+2,z,TB.wallCap());}
            }
        }
        if(gate-1>=l.z0){pillar(fx,y,gate-1,2);c.set(fx,y+2,gate-1,TB.facing("letterbox",l.front));}
        if(gate+2<=l.z1)pillar(fx,y,gate+2,2);
        if(kind==1)for(int z=gate;z<=gate+1;z++){c.set(fx,y+1,z,TB.GATE);c.set(fx,y+2,z,TB.GATE);}
        int depth=Math.min(10,Math.abs(l.x1-l.x0)-3);
        // toward the coast the houses are lower and further back, so the end of the street opens on the sea
        boolean low=l.z0>=0;
        Frame f=s.frame(c,low?5:3,3+depth,l.z0+(W>=10?1:0),l.z1-(W>=10?1:0));
        body(f,rnd.nextInt(3)==0,!low&&rnd.nextInt(3)==0,rnd.nextBoolean(),low?1:2);
        if(!low&&rnd.nextInt(7)==0)t.nature.cherry(s.x(3),y+1,gate>l.z0+3?l.z0+1:l.z1-1,5+rnd.nextInt(2));
        if(rnd.nextInt(8)==0&&gate+3<=l.z1){
            int vz=l.z1;c.set(fx,y+1,vz,TB.vending(l.front,false));c.set(fx,y+2,vz,TB.vending(l.front,true));t.street.machines++;
        }
    }

    // ---------------------------------------------------------------- parts

    /**
     * A two-storey house of the photos: white plaster (dark timber posts and bands on some), big curtained windows in
     * groups across the front, small frosted ones at the sides, the door in the middle, a tiled eave over the ground
     * floor, an upper balcony with a dark rail on some, an air conditioner, a kawara roof with deep eaves.
     */
    private void body(Frame f,boolean timber,boolean balcony,boolean eave,int stories){
        int W=f.W,D=f.D;
        if(W<5||D<5)return;
        BlockState wall=TB.PLASTER,dark=s(Blocks.STRIPPED_DARK_OAK_WOOD),post=Canvas.axis(Blocks.STRIPPED_DARK_OAK_LOG,Direction.Axis.Y),floor=s(Blocks.SPRUCE_PLANKS);
        Kit.shell(f,0,0,W-1,D-1,stories,wall,timber?post:wall,timber?dark:wall,floor,s(Blocks.STONE_BRICKS));
        if(timber)for(int x=4;x<W-2;x+=4)for(int y=1;y<4*stories;y++)if(y%4!=0)f.set(x,y,0,post);
        int door=W/2;
        // the front: big windows in runs of three on the ground floor, pairs above
        for(int x=1;x<W-1;x++){
            if(timber&&x%4==0)continue;
            boolean nearDoor=Math.abs(x-door)<=1;
            if(!nearDoor&&(x%4!=3||!timber)){f.set(x,1,0,TB.WINDOW);f.set(x,2,0,TB.WINDOW);}
            if(stories>1&&x%3!=0){f.set(x,5,0,TB.WINDOW);f.set(x,6,0,TB.WINDOW);}
        }
        for(int z=2;z<D-2;z+=3)for(int side:new int[]{0,W-1})for(int st=0;st<stories;st++)f.set(side,4*st+2,z,s(Blocks.WHITE_STAINED_GLASS_PANE));
        for(int x=2;x<W-2;x+=3){f.set(x,2,D-1,s(Blocks.WHITE_STAINED_GLASS_PANE));if(stories>1)f.set(x,6,D-1,TB.WINDOW);}
        f.door(door,1,0,Pal.pick(rnd,Blocks.DARK_OAK_DOOR,Blocks.SPRUCE_DOOR),Direction.NORTH,false);
        if(eave)for(int x=0;x<W;x++)if(f.air(x,3,-1))f.set(x,3,-1,Kit.along(Canvas.slab(Kit.KAWARA.slab(),true),Direction.Axis.Z));
        if(balcony&&stories>1){
            for(int x=0;x<W;x++){f.set(x,4,-1,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));f.set(x,4,-2,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));f.set(x,5,-2,TB.BALCONY);}
            for(int z=-1;z>=-2;z--){f.set(0,5,z,TB.BALCONY);f.set(W-1,5,z,TB.BALCONY);}
            for(int x=1;x<W-1;x++)if(x%3!=0){f.set(x,5,0,TB.WINDOW);f.set(x,6,0,TB.WINDOW);}
            Kit.airCon(f,W-2,5,-1,Direction.NORTH);
        }
        Kit.roof(f,0,0,W-1,D-1,4*stories,W>=D+3||rnd.nextBoolean()?Kit.HIP:Kit.GABLE,true,Kit.KAWARA,wall,floor);
        for(int st=0;st<stories;st++)if(rnd.nextInt(4)>0)Kit.roomLight(f,W/2,4*st,D/2,true);
        f.set(1,1,D-2,Canvas.slab(Blocks.SPRUCE_SLAB,true));
        if(rnd.nextBoolean())Kit.airCon(f,W,1,D-2,Direction.EAST);
    }
    /** A rough stone gatepost. */
    private void pillar(int x,int y,int z,int h){for(int k=1;k<=h;k++)c.set(x,y+k,z,TB.STONE_PILLAR);}
    /** The photo's wall: concrete blocks a metre high, a dark slat fence on top. */
    private void slatWall(int x,int y,int z){c.set(x,y+1,z,TB.BLOCK_WALL);c.set(x,y+2,z,TB.SLAT_FENCE);}
    /** A low block wall with a black railing on it, z0..z1. */
    private void railWall(int x,int y,int z0,int z1){for(int z=z0;z<=z1;z++){c.set(x,y+1,z,TB.BLOCK_WALL);c.set(x,y+2,z,TB.BALCONY);}}
}
