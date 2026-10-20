package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The battle ground: the old lot by the sea of photo 2 (tools/map/town/PHOTO_SCENES.md), the town's open space for
 * fights. Old, pale, coarse asphalt with cracks, patches and weeds, faded court lines. Seen from the photo's spot
 * (eighteen blocks short of the sea wall, near the east side): ahead on the left a low shed of white and grey
 * corrugated iron streaked with rust, its rusty doors right of centre, a chain-link fence on posts left of it with a
 * small box at its foot, a cherry tree at the far left, a barrel and a crate close by; ahead a long low wall of
 * concrete blocks along the sea with a pipe rail over it, a gap with a rail in the middle and a chain-link fence over
 * the wall right of it; on the right a small white goal with its net. Behind the wall, a step down: two run-down
 * houses on the left, a run-down block of flats with balconies on the right, the sea between, and poles with two
 * transformers each carrying wires across the whole view. The hillside of houses rises on the left.
 */
final class Ground {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int X0=Plan.LOT_X0,X1=Plan.LOT_X1,Z0=Plan.LOT_Z0,Z1=Plan.LOT_Z1,Y=Plan.COAST;
    /** The shed (x, z inclusive), the goal's posts' line and middle, the gap in the sea wall. */
    static final int SHED_X0=90,SHED_X1=97,SHED_Z0=113,SHED_Z1=117,GOAL_X=74,GOAL_Z=109,GAP_X0=75,GAP_X1=77;
    Ground(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        surface();
        lines();
        edges();
        seaWall();
        shed();
        fence();
        goal(GOAL_X,GOAL_Z);
        props();
        back();
        t.places.put("battle",new int[]{(X0+X1)/2,Y+1,(Z0+Z1)/2});
        Town.log("battle ground: "+c.placed);
    }

    /** Old pale asphalt over the whole lot, repaired in darker squares, cracked, weeds in the cracks and at the edges. */
    private void surface(){
        for(int x=X0;x<=X1;x++)for(int z=Z0;z<=Z1;z++){
            c.set(x,Y,z,TB.WORN_ASPHALT);c.clear(x,Y+1,z,x,Y+8,z);c.ground(x,z,Y);
        }
        for(int k=0;k<10;k++){
            int px=X0+3+rnd.nextInt(X1-X0-8),pz=Z0+3+rnd.nextInt(Z1-Z0-8),pw=2+rnd.nextInt(3),pd=1+rnd.nextInt(3);
            for(int x=px;x<px+pw;x++)for(int z=pz;z<pz+pd;z++)c.set(x,Y,z,TB.PATCHED);
        }
        // cracks wandering across, weeds growing out of a third of them
        for(int k=0;k<60;k++){
            double x=X0+2+rnd.nextInt(X1-X0-4),z=Z0+2+rnd.nextInt(Z1-Z0-4),a=rnd.nextDouble()*Math.PI*2;
            int len=5+rnd.nextInt(16);
            for(int i=0;i<len;i++){
                int bx=(int)Math.round(x),bz=(int)Math.round(z);
                if(bx<=X0||bx>=X1||bz<=Z0||bz>=Z1)break;
                c.set(bx,Y,bz,TB.WORN_CRACKED);
                if(rnd.nextInt(14)==0&&c.air(bx,Y+1,bz))c.set(bx,Y+1,bz,s(Blocks.GRASS));
                a+=(rnd.nextDouble()-0.5)*0.9;x+=Math.cos(a);z+=Math.sin(a);
            }
        }
        // weeds where nobody walks: along the sea wall, the hill's foot and the west edge
        for(int x=X0+1;x<X1;x++)if(rnd.nextInt(3)==0)weed(x,Z1-1);
        for(int z=Z0+2;z<Z1;z++){if(rnd.nextInt(3)==0)weed(X1,z);if(rnd.nextInt(4)==0)weed(X0+1,z);}
    }
    private void weed(int x,int z){
        if(!c.air(x,Y+1,z))return;
        if(rnd.nextInt(8)==0&&c.air(x,Y+2,z))c.tallPlant(x,Y+1,z,Blocks.TALL_GRASS);else c.set(x,Y+1,z,s(rnd.nextInt(6)==0?Blocks.FERN:Blocks.GRASS));
    }

    /** Faded white lines: a long side line on the photo's right, a second shorter one, a line across, old parking bays. */
    private void lines(){
        for(int z=Z0+7;z<=Z1-2;z++)paint(76,z,Direction.Axis.Z);
        for(int z=96;z<=Z1-2;z++)paint(72,z,Direction.Axis.Z);
        for(int x=40;x<=X1-3;x++)paint(x,105,Direction.Axis.X);
        for(int x=X0+4;x<=X0+40;x+=3)for(int z=Z0+1;z<=Z0+5;z++)paint(x,z,Direction.Axis.Z);
        for(int x=X0+4;x<=X0+40;x++)paint(x,Z0+6,Direction.Axis.X);
    }
    /** One block of faded paint, worn through in places (a third gone, in runs). */
    private void paint(int x,int z,Direction.Axis along){
        double wear=Pal.smooth(along==Direction.Axis.X?x:z,along==Direction.Axis.X?z:x,4,41);
        if(wear<0.2||Pal.hash(x,43,z)<0.08)return;
        c.set(x,Y,z,TB.painted("worn_asphalt_line",along));
    }

    /** The lot's west edge over the sea: a concrete kerb with a pipe rail, the sea wall under it, tetrapods below. */
    private void edges(){
        for(int z=Plan.SEAWARD_Z;z<=Z1;z++){
            int x=X0;
            for(int y=Canvas.Y0;y<Y;y++)c.set(x,y,z,Pal.seawall(x,y,z));
            c.set(x,Y,z,s(Blocks.SMOOTH_STONE));
            c.set(x,Y+1,z,z%3==0?TB.PIPE:TB.GUARDRAIL);
            if(c.water(x-1,Plan.SEA,z)&&rnd.nextInt(3)==0)t.coast.tetrapod(x-1,c.ground(x-1,z)+1+rnd.nextInt(Math.max(1,Plan.SEA-c.ground(x-1,z))),z);
        }
        // the open side to the coastal road: short white posts with room to walk between
        for(int x=X0+2;x<=X1-2;x+=4)if(c.air(x,Y+1,Z0))c.set(x,Y+1,Z0,TB.PIPE);
    }

    /**
     * The long low wall along the sea (the lot's south side): concrete blocks a metre high with a pipe rail over it,
     * a gap with a rail across it in the middle of the photo, a chain-link fence over the wall right of the gap.
     */
    private void seaWall(){
        int z=Z1;
        for(int x=X0;x<=X1;x++){
            boolean sea=c.water(x,Plan.SEA,z+1);
            if(sea)for(int y=Canvas.Y0;y<Y;y++)c.set(x,y,z,Pal.seawall(x,y,z));
            c.set(x,Y,z,s(Blocks.SMOOTH_STONE));
            if(x>=GAP_X0&&x<=GAP_X1){c.set(x,Y+1,z,TB.GUARDRAIL);continue;}
            c.set(x,Y+1,z,TB.BLOCK_WALL);
            boolean chain=x>=GAP_X0-6&&x<GAP_X0;
            c.set(x,Y+2,z,chain?TB.CHAIN_LINK:Math.floorMod(x,3)==0||x==GAP_X0-1||x==GAP_X1+1?TB.PIPE:TB.GUARDRAIL);
            if(chain)c.set(x,Y+3,z,TB.CHAIN_LINK);
            if(sea&&rnd.nextInt(3)==0)t.coast.heap(x,z+1,2);
        }
        for(int x:new int[]{GAP_X0-6,GAP_X0-1})c.set(x,Y+3,z,TB.PIPE);
    }

    /**
     * The shed: two blocks of corrugated iron walls, white and grey with rust running down in streaks, a pair of
     * rusty doors right of the middle of its front, a low roof of dark sheet, a concrete floor, odds and ends inside.
     */
    private void shed(){
        var f=new Frame(c,SHED_X0,SHED_Z0,SHED_X1,SHED_Z1,Direction.NORTH,Y);
        int W=f.W,D=f.D;
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){
            boolean edge=x==0||x==W-1||z==0||z==D-1;
            f.set(x,0,z,s(Blocks.SMOOTH_STONE));
            boolean rust=Pal.hash(f.wx(x,z),31,f.wz(x,z))<0.3;
            for(int y=1;y<=2;y++)f.set(x,y,z,edge?(rust?TB.RUSTY:TB.CORRUGATED):s(Blocks.AIR));
        }
        for(int x=1;x<=2;x++)for(int y=1;y<=2;y++)f.set(x,y,0,TB.RUSTY);
        Kit.roof(f,0,0,W-1,D-1,3,Kit.GABLE,true,Kit.SHEET,TB.CORRUGATED,s(Blocks.AIR));
        f.set(W-2,1,D-2,s(Blocks.BARREL));f.set(W-3,1,D-2,s(Blocks.BARREL));
        f.set(3,1,D-2,Canvas.slab(Blocks.SPRUCE_SLAB,false));f.set(4,1,D-2,Canvas.slab(Blocks.SPRUCE_SLAB,false));
        f.wallSign(W/2+1,2,-1,Blocks.SPRUCE_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,false,"","第二倉庫","");
    }

    /**
     * The chain-link fence on white posts across the shed's left end and on to the lot's east edge (photo 2: from the
     * left edge of the picture to the shed, 11.5 blocks ahead), a small box at its foot, and the big cherry tree behind
     * it at the foot of the hill, its crown filling the photo's top left corner.
     */
    private void fence(){
        int z=SHED_Z0-2,from=SHED_X1-3;
        for(int x=from;x<=X1;x++){
            boolean post=x==from||x==X1||(x-from)%3==0;
            c.set(x,Y+1,z,post?TB.PIPE:TB.CHAIN_LINK);c.set(x,Y+2,z,post?TB.PIPE:TB.CHAIN_LINK);
        }
        for(int zz=z+1;zz<SHED_Z0;zz++){c.set(from,Y+1,zz,TB.CHAIN_LINK);c.set(from,Y+2,zz,TB.CHAIN_LINK);}
        c.set(from+2,Y+1,z-1,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
        int tx=X1+1,tz=SHED_Z0+1;
        t.nature.cherry(tx,c.ground(tx,tz)+1,tz,5,1.45);
    }

    /** A small white goal (photo 2: about a metre high, two wide), its mouth to the east: pipe posts, a bar, the net behind. */
    private void goal(int x,int zc){
        for(int z=zc-1;z<=zc+1;z++){
            boolean post=z==zc-1||z==zc+1;
            c.set(x,Y+1,z,post?TB.PIPE:TB.GUARDRAIL);
            c.set(x-1,Y+1,z,TB.NET);
        }
    }

    /** Near the photo's spot: a barrel, a crate, a white concrete block; drums, pallets and a rusty container further off. */
    private void props(){
        double cx=Plan.PHOTO2_X,cz=Plan.PHOTO2_Z;
        c.set((int)Math.floor(cx+4.7),Y+1,(int)Math.floor(cz+3.6),s(Blocks.BARREL));
        c.set((int)Math.floor(cx+3.7),Y+1,(int)Math.floor(cz+4.6),s(Blocks.COMPOSTER));
        c.set((int)Math.floor(cx+2.5),Y+1,(int)Math.floor(cz+2.2),s(Blocks.WHITE_CONCRETE));
        for(int i=0;i<10;i++){
            int x=rnd.nextBoolean()?X0+2+rnd.nextInt(4):X1-6+rnd.nextInt(4),z=Z0+4+rnd.nextInt(40);
            if(!c.air(x,Y+1,z))continue;
            switch(rnd.nextInt(3)){
                case 0->c.set(x,Y+1,z,rnd.nextBoolean()?s(Blocks.BLUE_CONCRETE):s(Blocks.CAULDRON));
                case 1->c.set(x,Y+1,z,Canvas.slab(Blocks.OAK_SLAB,false));
                default->c.set(x,Y+1,z,s(Blocks.BARREL));
            }
        }
        // an old container by the hill, its doors to the lot
        for(int x=X1-8;x<=X1-2;x++)for(int z=Z0+3;z<=Z0+5;z++)for(int y=Y+1;y<=Y+3;y++)c.set(x,y,z,x==X1-8?Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.WEST,true,false):TB.RUSTY);
        // two vending machines at the way in from the road
        c.set(X0+3,Y+1,Z0+1,TB.vending(Direction.NORTH,false));c.set(X0+3,Y+2,Z0+1,TB.vending(Direction.NORTH,true));
        c.set(X0+4,Y+1,Z0+1,TB.vending(Direction.NORTH,false));c.set(X0+4,Y+2,Z0+1,TB.vending(Direction.NORTH,true));
        t.street.machines+=2;
    }

    /**
     * Behind the sea wall, a step down: on the left two run-down houses (a flat-roofed concrete one, a tiled one
     * behind it) among bushes, on the right a run-down two-storey block of flats with balconies, the sea between
     * them; poles with two transformers each along the strips, wired to each other and up the hill.
     */
    private void back(){
        int y=Plan.BACK_Y;
        for(int[] r:new int[][]{Plan.BACK_E,Plan.BACK_W})for(int x=r[0];x<=r[2];x++)for(int z=r[1];z<=r[3];z++){
            c.set(x,y,z,Pal.hash(x,51,z)<0.7?s(Blocks.GRASS_BLOCK):s(Blocks.COARSE_DIRT));c.clear(x,y+1,z,x,y+14,z);
            boolean seaSide=c.water(x+1,Plan.SEA,z)||c.water(x-1,Plan.SEA,z)||c.water(x,Plan.SEA,z+1);
            if(seaSide)for(int yy=Canvas.Y0;yy<y;yy++)c.set(x,yy,z,Pal.seawall(x,yy,z));
            else if(rnd.nextInt(4)==0)c.set(x,y+1,z,s(Blocks.GRASS));
        }
        runDownHouse(new Frame(c,80,125,88,132,Direction.NORTH,y),true);
        runDownHouse(new Frame(c,90,128,97,135,Direction.NORTH,y),false);
        flats(new Frame(c,42,122,62,131,Direction.NORTH,y));
        for(int[] b:new int[][]{{86,121},{98,123},{79,134}})t.nature.broadleaf(b[0],y+1,b[1],4+rnd.nextInt(2));
        // trees and bushes along the hill's foot behind the shed, hiding the hillside's lowest wall (photo 2: green
        // under the houses on the hill)
        for(int[] b:new int[][]{{99,129},{100,136},{97,139}})t.nature.broadleaf(b[0],y+1,b[1],5+rnd.nextInt(2));
        for(int z=Plan.BACK_E[1]+6;z<=Plan.BACK_E[3];z++)if(rnd.nextInt(3)>0&&c.air(Plan.BACK_E[2],y+1,z))t.nature.blob(Plan.BACK_E[2]+0.5,y+1.5,z+0.5,1.2,1.4,1.2,rnd.nextBoolean()?Blocks.AZALEA_LEAVES:Blocks.OAK_LEAVES,null);
        List<int[]> run=new ArrayList<>();
        for(int[] p:new int[][]{{40,120,0},{63,120,0},{66,132,1},{92,135,1},{104,133,1}}){
            int[] top=t.street.pole(p[0],p[1],p[2]==0?Direction.EAST:Direction.NORTH,2,false);
            if(top!=null)run.add(top);
        }
        t.street.addRun(run);
    }
    /** A run-down two-storey house: stained concrete or plaster, dark and broken windows, a rusty rail, an old roof. */
    private void runDownHouse(Frame f,boolean flat){
        int W=f.W,D=f.D;
        BlockState wall=flat?TB.WEATHERED:TB.PLASTER,floor=s(Blocks.SPRUCE_PLANKS);
        Kit.shell(f,0,0,W-1,D-1,2,wall,wall,TB.WEATHERED,floor,TB.WEATHERED);
        for(int st=0;st<2;st++)for(int x=1;x<W-1;x++){
            if(x%3==0)continue;
            BlockState w=Pal.hash(f.wx(x,0),st,f.wz(x,0))<0.25?s(Blocks.AIR):s(Blocks.GRAY_STAINED_GLASS_PANE);
            f.set(x,4*st+2,0,w);f.set(x,4*st+3,0,w);
        }
        f.door(W/2,1,0,Blocks.IRON_DOOR,Direction.NORTH,false);
        for(int x=1;x<W-1;x++){f.set(x,4,-1,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));if(rnd.nextInt(5)>0)f.set(x,5,-1,TB.BALCONY);}
        Kit.airCon(f,W-2,5,-2,Direction.NORTH);
        if(flat)Kit.roof(f,0,0,W-1,D-1,8,Kit.FLAT,true,Kit.SLATE,TB.WEATHERED,floor);
        else Kit.roof(f,0,0,W-1,D-1,8,Kit.HIP,true,Kit.KAWARA,wall,floor);
        for(int i=0;i<4;i++){int x=rnd.nextInt(W),z=rnd.nextInt(D);if(f.air(x,1,z-1))f.set(x,1,z-1,s(Blocks.GRASS));}
    }
    /** The run-down block of flats: weathered concrete, a row of windows and doors, balconies with dark rails above. */
    private void flats(Frame f){
        int W=f.W,D=f.D;
        BlockState wall=TB.WEATHERED,floor=s(Blocks.BIRCH_PLANKS);
        Kit.shell(f,0,0,W-1,D-1,2,wall,wall,wall,floor,wall);
        for(int st=0;st<2;st++){
            int fy=4*st;
            for(int x=1;x<W-1;x++){
                int u=x%5;
                if(u==0)continue;
                BlockState w=Pal.hash(f.wx(x,0),fy,f.wz(x,0))<0.2?s(Blocks.AIR):u==1&&st==0?s(Blocks.GRAY_STAINED_GLASS_PANE):s(Blocks.GLASS_PANE);
                f.set(x,fy+2,0,w);f.set(x,fy+3,0,w);if(u>=2&&u<=3)f.set(x,fy+1,0,st==1?w:wall);
            }
            if(st==1)for(int x=0;x<W;x++){f.set(x,fy,-1,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));f.set(x,fy,-2,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));f.set(x,fy+1,-2,x%5==0?wall:TB.BALCONY);}
        }
        for(int x=2;x<W-1;x+=5)f.door(x,1,0,Blocks.IRON_DOOR,Direction.NORTH,false);
        for(int x=4;x<W-1;x+=5)Kit.airCon(f,x,5,-1,Direction.NORTH);
        Kit.roof(f,0,0,W-1,D-1,8,Kit.FLAT,true,Kit.SLATE,wall,floor);
        f.wallSign(W/2,7,-1,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,false,"","コーポ浜風","");
    }
}
