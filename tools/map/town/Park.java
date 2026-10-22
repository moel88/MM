package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * ぼたんど中央公園, west of the square (map v2.0): a wide lawn edged by a knee-high hedge with its gateways, paths of
 * beaten earth crossing at a little round plaza, a pond with stone edges and water lilies under a ring of cherry trees
 * (the park is one of the few places the town has them), a pavilion by the water, a playground (swings, a slide, a
 * climbing frame, a sandpit), big shade trees, benches and park lamps, and the public toilet.
 */
final class Park {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int Y=Plan.COAST,X0=Plan.COL_W[0],X1=Plan.COL_W[1],Z0=Plan.ROW_M[0],Z1=Plan.ROW_M[1];
    /** The pond's middle and radii; the paths' crossing. */
    static final int PX=-148,PZ=-30,PRX=15,PRZ=10,MX=-118,MZ=-10;
    Park(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        for(int x=X0;x<=X1;x++)for(int z=Z0;z<=Z1;z++){c.set(x,Y,z,s(Blocks.GRASS_BLOCK));c.clear(x,Y+1,z,x,Y+10,z);c.ground(x,z,Y);}
        hedge();
        paths();
        pond();
        pavilion(PX+PRX+5,PZ-3);
        cherries();
        playground(X0+6,Z1-22);
        toilet(X1-10,Z0+3);
        shadeTrees();
        furniture();
        t.places.put("park",new int[]{-100,Y+1,-10});
        Town.log("park: "+c.placed);
    }

    /** A knee-high hedge round the park a block in from the sidewalks, open at the corners and at a gateway on each side. */
    private void hedge(){
        for(int x=X0+5;x<=X1-5;x++){
            if(Math.abs(x-MX)>3){hedgeAt(x,Z0+1);hedgeAt(x,Z1-1);}
        }
        for(int z=Z0+5;z<=Z1-5;z++){
            if(Math.abs(z-MZ)>3){hedgeAt(X0+1,z);hedgeAt(X1-1,z);}
        }
    }
    private void hedgeAt(int x,int z){if(c.air(x,Y+1,z))c.set(x,Y+1,z,TB.HEDGE);}

    /** Paths of beaten earth from the four gateways to the round plaza where they cross, and a loop round the pond. */
    private void paths(){
        for(int x=X0+1;x<=X1-1;x++)for(int dz=-1;dz<=1;dz++)path(x,MZ+dz);
        for(int z=Z0+1;z<=Z1-1;z++)for(int dx=-1;dx<=1;dx++)path(MX+dx,z);
        for(int a=0;a<360;a+=2){
            double r=Math.toRadians(a);
            int x=(int)Math.round(PX+Math.cos(r)*(PRX+3)),z=(int)Math.round(PZ+Math.sin(r)*(PRZ+3));
            for(int dx=0;dx<=1;dx++)for(int dz=0;dz<=1;dz++)path(x+dx,z+dz);
        }
        // the plaza at the crossing: pavers in a circle, a clock post at its middle
        for(int dx=-6;dx<=6;dx++)for(int dz=-6;dz<=6;dz++){
            double d=Math.hypot(dx,dz);if(d>6.3)continue;
            c.set(MX+dx,Y,MZ+dz,d>5.4?s(Blocks.POLISHED_ANDESITE):TB.PAVING);
        }
        for(int y=Y+1;y<=Y+4;y++)c.set(MX,y,MZ,s(Blocks.POLISHED_BLACKSTONE_WALL));
        c.set(MX,Y+5,MZ,s(Blocks.WHITE_CONCRETE));
        for(Direction d:Direction.Type.HORIZONTAL)c.wallSign(MX+d.getOffsetX(),Y+5,MZ+d.getOffsetZ(),Blocks.BIRCH_WALL_SIGN,d,DyeColor.BLACK,false,"","12:00","");
        c.set(MX,Y+6,MZ,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));
    }
    private void path(int x,int z){
        if(x<=X0||x>=X1||z<=Z0||z>=Z1)return;
        if(!c.get(x,Y,z).isOf(Blocks.GRASS_BLOCK))return;
        c.set(x,Y,z,Pal.hash(x,71,z)<0.15?s(Blocks.COARSE_DIRT):s(Blocks.DIRT_PATH));
    }

    /** Whether a point lies in the pond (its shore wanders a little). */
    static double pondEdge(int x,int z){
        double dx=(x-PX)/(double)PRX,dz=(z-PZ)/(double)PRZ,a=Math.atan2(dz,dx);
        return Math.sqrt(dx*dx+dz*dz)-(1+0.07*Math.sin(a*3+0.5)+0.05*Math.sin(a*5+2));
    }
    /** The pond: water two deep within a rim of stones, water lilies, reeds at the edge. */
    private void pond(){
        for(int x=PX-PRX-3;x<=PX+PRX+3;x++)for(int z=PZ-PRZ-3;z<=PZ+PRZ+3;z++){
            double e=pondEdge(x,z);
            if(e<0){
                c.set(x,Y-3,z,s(Blocks.CLAY));c.set(x,Y-2,z,s(Blocks.WATER));c.set(x,Y-1,z,s(Blocks.WATER));c.set(x,Y,z,s(Blocks.AIR));
                c.ground(x,z,Y-3);
                double h=Pal.hash(x,73,z);
                if(e<-0.15&&h<0.07)c.set(x,Y,z,s(Blocks.LILY_PAD));
                else if(e>-0.12&&h<0.25)c.set(x,Y-2,z,s(Blocks.SEAGRASS));
            }else if(e<0.09){
                // the rim: rounded stones, mossy here and there
                double h=Pal.hash(x,74,z);
                c.set(x,Y,z,h<0.6?s(Blocks.STONE_BRICKS):h<0.85?s(Blocks.MOSSY_STONE_BRICKS):s(Blocks.ANDESITE));
                if(Pal.hash(x,75,z)<0.08&&c.air(x,Y+1,z))c.tallPlant(x,Y+1,z,Blocks.TALL_GRASS);
            }
        }
    }

    /** 東屋: a little open pavilion by the pond, four posts under a tiled hip roof, benches inside looking at the water. */
    private void pavilion(int x,int z){
        var f=new Frame(c,x-2,z-2,x+2,z+2,Direction.WEST,Y);
        f.fill(0,0,0,4,0,4,s(Blocks.SPRUCE_PLANKS));
        for(int bx:new int[]{0,4})for(int bz:new int[]{0,4})for(int y=1;y<=3;y++)f.set(bx,y,bz,TB.TIMBER);
        Kit.roof(f,0,0,4,4,4,Kit.HIP,true,Kit.KAWARA,s(Blocks.AIR),s(Blocks.AIR));
        for(int bx=1;bx<=3;bx++)f.set(bx,1,3,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.SOUTH,false));
        f.set(2,1,1,Canvas.slab(Blocks.SPRUCE_SLAB,true));
        t.places.put("pond",new int[]{x,Y+1,z+4});
    }

    /** The cherry trees round the pond, wide and old. */
    private void cherries(){
        int[] angles={20,80,140,200,250,320};
        for(int a:angles){
            double r=Math.toRadians(a);
            int x=(int)Math.round(PX+Math.cos(r)*(PRX+6)),z=(int)Math.round(PZ+Math.sin(r)*(PRZ+6));
            if(!c.get(x,Y,z).isOf(Blocks.GRASS_BLOCK)&&!c.get(x,Y,z).isOf(Blocks.DIRT_PATH))continue;
            if(Math.abs(x-(PX+PRX+5))<4&&Math.abs(z-(PZ-3))<4)continue;
            c.set(x,Y,z,s(Blocks.GRASS_BLOCK));
            t.nature.cherry(x,Y+1,z,6,1.2+rnd.nextDouble()*0.2);
        }
    }

    /** The playground: swings, a slide, a climbing frame and a sandpit on a patch of sand, a bench for the parents. */
    private void playground(int x0,int z0){
        for(int x=x0;x<=x0+26;x++)for(int z=z0;z<=z0+17;z++)if(c.get(x,Y,z).isOf(Blocks.GRASS_BLOCK)||c.get(x,Y,z).isOf(Blocks.DIRT_PATH))
            c.set(x,Y,z,Pal.hash(x,76,z)<0.85?s(Blocks.SAND):s(Blocks.COARSE_DIRT));
        // swings: two posts and a bar, two seats on chains
        int sx=x0+2,sz=z0+3;
        for(int y=Y+1;y<=Y+4;y++){c.set(sx,y,sz,s(Blocks.IRON_BARS));c.set(sx+5,y,sz,s(Blocks.IRON_BARS));}
        for(int x=sx;x<=sx+5;x++)c.set(x,Y+5,sz,s(Blocks.IRON_BARS));
        for(int x:new int[]{sx+2,sx+3}){c.set(x,Y+4,sz,Canvas.chain(Direction.Axis.Y));c.set(x,Y+3,sz,Canvas.chain(Direction.Axis.Y));c.set(x,Y+2,sz,Canvas.slab(Blocks.OAK_SLAB,false));}
        // the slide: a ladder up the back of its little tower, a shining chute down the front
        int lx=x0+12,lz=z0+3;
        for(int x:new int[]{lx,lx+1}){
            c.set(x,Y+1,lz,s(Blocks.SMOOTH_QUARTZ));c.set(x,Y+2,lz,s(Blocks.SMOOTH_QUARTZ));c.set(x,Y+3,lz,Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,false));
            c.set(x,Y+1,lz-1,Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH));
            c.set(x,Y+2,lz-1,Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH));
            int[][] chute={{2,1},{2,0},{1,1},{1,0}};
            for(int k=0;k<4;k++)c.set(x,Y+chute[k][0],lz+1+k,Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,chute[k][1]==1));
        }
        // the climbing frame: a lattice cube of bars
        int jx=x0+20,jz=z0+3;
        for(int dx=0;dx<=3;dx++)for(int dz=0;dz<=3;dz++)for(int dy=1;dy<=3;dy++){
            int edges=(dx==0||dx==3?1:0)+(dz==0||dz==3?1:0)+(dy==1||dy==3?1:0);
            if(edges>=2)c.set(jx+dx,Y+dy,jz+dz,s(Blocks.IRON_BARS));
        }
        // the sandpit, its rim of planks flush with the ground
        int px=x0+4,pz=z0+10;
        for(int x=px;x<=px+7;x++)for(int z=pz;z<=pz+5;z++){
            boolean rim=x==px||x==px+7||z==pz||z==pz+5;
            c.set(x,Y,z,rim?s(Blocks.SPRUCE_PLANKS):s(Blocks.SAND));
        }
        c.set(px+3,Y+1,pz+2,s(Blocks.RED_CONCRETE));
        // spring riders and a bench
        c.set(x0+15,Y+1,z0+12,s(Blocks.YELLOW_CONCRETE));c.set(x0+18,Y+1,z0+13,s(Blocks.LIME_CONCRETE));
        for(int x=x0+21;x<=x0+22;x++)c.set(x,Y+1,z0+15,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.SOUTH,false));
        t.places.put("playground",new int[]{x0+14,Y+1,z0+9});
    }

    /** The public toilet: a small building in grey tiles under a flat roof, a sign for men and women. */
    private void toilet(int x0,int z0){
        var f=new Frame(c,x0,z0,x0+6,z0+4,Direction.SOUTH,Y);
        Kit.shell(f,0,0,f.W-1,f.D-1,1,TB.TILE,TB.CONCRETE,TB.CONCRETE,s(Blocks.SMOOTH_STONE),TB.CONCRETE);
        f.set(1,1,0,s(Blocks.AIR));f.set(1,2,0,s(Blocks.AIR));f.set(f.W-2,1,0,s(Blocks.AIR));f.set(f.W-2,2,0,s(Blocks.AIR));
        f.set(3,2,0,TB.WIN_FROSTED);
        f.wallSign(3,3,-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLUE,false,"","公衆トイレ","男 | 女");
        Kit.roof(f,0,0,f.W-1,f.D-1,4,Kit.FLAT,true,Kit.SLATE,TB.CONCRETE,s(Blocks.SMOOTH_STONE));
        f.set(3,3,2,Canvas.rod(Blocks.END_ROD,Direction.DOWN));
    }

    /** Big shade trees round the lawn's edges, leaving its middle open. */
    private void shadeTrees(){
        int[][] at={{X1-8,Z0+14},{X1-8,Z1-8},{-100,Z1-7},{-98,Z0+7},{X0+8,Z0+8},{-134,Z1-6}};
        for(int[] p:at){
            if(!c.air(p[0],Y+1,p[1]))continue;
            t.nature.townTree(p[0],Y+1,p[1],6+rnd.nextInt(3));
        }
    }

    /** Benches along the paths, park lamps, the park's name on a stone at the main gateway, a drinking fountain. */
    private void furniture(){
        for(int x=X1-14;x>MX+8;x-=12)for(int side=-1;side<=1;side+=2){
            int z=MZ+side*3;
            if(c.air(x,Y+1,z)&&c.air(x+1,Y+1,z))for(int k=0;k<=1;k++)c.set(x+k,Y+1,z,Canvas.stairs(Blocks.SPRUCE_STAIRS,side<0?Direction.NORTH:Direction.SOUTH,false));
        }
        for(int x=X1-8;x>X0+6;x-=16)lamp(x,MZ-3);
        for(int z=Z0+8;z<Z1-4;z+=16)lamp(MX+3,z);
        // the name stone by the east gateway
        int nx=X1-3,nz=MZ+5;
        c.set(nx,Y+1,nz,s(Blocks.POLISHED_ANDESITE));
        c.wallSign(nx+1,Y+1,nz,Blocks.DARK_OAK_WALL_SIGN,Direction.EAST,DyeColor.WHITE,false,"ぼたんど","中央公園","BOTAND PARK");
        // a drinking fountain on the plaza's edge
        c.set(MX+5,Y+1,MZ-5,s(Blocks.STONE_BRICK_WALL));c.set(MX+5,Y+2,MZ-5,Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3,3));
        t.street.vending(X1-2,MZ-6,Direction.NORTH,2);
    }
    /** A park lamp: a slim dark post and a lantern under a little cap. */
    private void lamp(int x,int z){
        if(!c.air(x,Y+1,z)||!c.air(x,Y+4,z))return;
        for(int y=Y+1;y<=Y+3;y++)c.set(x,y,z,s(Blocks.DARK_OAK_FENCE));
        c.set(x,Y+4,z,s(Blocks.LANTERN));c.set(x,Y+5,z,Canvas.slab(Blocks.DARK_OAK_SLAB,false));
    }
}
