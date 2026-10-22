package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * The forest in the lower glass's west half (map v3.0: '숲·유적'): rolling ground under groves of oak, birch, dark oak
 * and spruce with clearings between them; a lake inside the loop road with a wooden pier, reeds and lily pads; the
 * ruins of an old stronghold near the far corner, broken walls and columns, a fallen tower, a great tree grown through
 * its court; earth paths from the roads to the lake and the ruins; a sandy shore along the coast with driftwood and
 * rocks. The trees keep a few blocks off the roads and paths so the forest can be fought through.
 */
final class Forest {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int CODE=4;
    final boolean[] path=new boolean[Plan.W*Plan.D];
    int trees;
    Forest(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}
    private static BlockState s(net.minecraft.block.Block b){return b.getDefaultState();}

    void build(){
        lake();
        ruins();
        paths();
        trees();
        shore();
        t.places.put("forest",new int[]{-40,c.ground(-40,140)+1,140});
        Town.log("forest: "+trees+" trees, "+c.placed);
    }

    // ---------------------------------------------------------------- the lake

    /** The lake: its shore claimed, a pier out from its south shore, reeds at its edge, lily pads, boulders on its banks. */
    private void lake(){
        int lx=Plan.LAKE_X,lz=Plan.LAKE_Z,R=Plan.LAKE_R+9;
        for(int x=lx-R;x<=lx+R;x++)for(int z=lz-R;z<=lz+R;z++){
            if(!Canvas.inPlan(x,z)||Math.hypot(x-lx,z-lz)>R)continue;
            int i=Canvas.idx(x,z);
            if(t.roads.on(x,z))continue;
            c.own(x,z,CODE);
            int w=t.terrain.water[i];
            if(w!=0){
                double h=Pal.hash(x,21,z);
                if(h<0.05&&c.air(x,w+1,z))c.set(x,w+1,z,s(Blocks.LILY_PAD));
                else if(h<0.12&&c.water(x,w-1,z))c.set(x,w-1,z,s(Blocks.SEAGRASS));
                continue;
            }
            // reeds where the bank meets the water
            int g=c.ground(x,z);
            boolean edge=false;
            for(var d:Direction.Type.HORIZONTAL){int nx=x+d.getOffsetX(),nz=z+d.getOffsetZ();if(Canvas.inPlan(nx,nz)&&t.terrain.water[Canvas.idx(nx,nz)]!=0)edge=true;}
            if(edge&&Pal.hash(x,22,z)<0.3&&c.air(x,g+1,z)&&(c.get(x,g,z).isOf(Blocks.GRASS_BLOCK)||c.get(x,g,z).isOf(Blocks.DIRT)||c.get(x,g,z).isOf(Blocks.SAND))){
                int h=1+(int)(Pal.hash(x,23,z)*3);
                for(int k=1;k<=h;k++)c.set(x,g+k,z,s(Blocks.SUGAR_CANE));
            }
        }
        // the pier: from the south shore toward the middle, three wide, on log posts
        int py=68+1;
        int z1=lz+Plan.LAKE_R+2,z0=lz+5;
        for(int z=z0;z<=z1;z++)for(int x=lx-1;x<=lx+1;x++){
            c.set(x,py,z,Canvas.slab(Blocks.SPRUCE_SLAB,false));
            c.clear(x,py+1,z,x,py+3,z);
            if(Math.abs(x-lx)==1&&(z-z0)%3==0)for(int y=60;y<py;y++)if(c.open(x,y,z))c.set(x,y,z,Canvas.axis(Blocks.SPRUCE_LOG,Direction.Axis.Y));
        }
        for(int x:new int[]{lx-2,lx+2})for(int z=z0;z<=z1;z+=4){c.set(x,py,z,s(Blocks.SPRUCE_FENCE));c.set(x,py+1,z,s(Blocks.SPRUCE_FENCE));c.set(x,py+2,z,s(Blocks.LANTERN));}
        c.set(lx,py+1,z0,s(Blocks.BARREL));
        // a rowing boat's worth of crates at the pier's end and boulders round the banks
        for(int k=0;k<7;k++){
            double a=rnd.nextDouble()*2*Math.PI,d=Plan.LAKE_R+3+rnd.nextDouble()*4;
            int bx=lx+(int)Math.round(Math.cos(a)*d),bz=lz+(int)Math.round(Math.sin(a)*d);
            if(Math.abs(bx-lx)<4&&bz>lz)continue;
            boulder(bx,bz,1.2+rnd.nextDouble()*1.3);
        }
        t.places.put("lake",new int[]{lx,py+1,z1-1});
    }
    /** A boulder of mossy stone half sunk in the ground. */
    private void boulder(int x,int z,double r){
        int g=c.ground(x,z);
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-1;dy<=3;dy++){
            double d=Math.sqrt(dx*dx+dz*dz+dy*dy*1.6);
            if(d>r+0.3*Pal.hash(x+dx,dy,z+dz))continue;
            int y=c.ground(x+dx,z+dz)+dy;
            if(dy>=0&&!c.air(x+dx,y+1,z+dz))continue;
            double h=Pal.hash(x+dx,y,z+dz);
            c.set(x+dx,y+(dy>=0?1:0),z+dz,h<0.35?s(Blocks.MOSSY_COBBLESTONE):h<0.7?s(Blocks.ANDESITE):s(Blocks.COBBLESTONE));
        }
    }

    // ---------------------------------------------------------------- the ruins

    /**
     * The ruins: a square court twenty-five blocks across levelled to the ground at its middle, its walls broken to
     * different heights with gaps, of old masonry, moss and cracks; two rows of columns, some fallen; a round tower
     * fallen in at its north-west corner; an altar of stone at its head; a great oak grown up through the court; vines
     * and moss over everything.
     */
    private void ruins(){
        int cx=Plan.RUINS_X,cz=Plan.RUINS_Z,H=12;
        int y=c.ground(cx,cz);
        for(int x=cx-H-4;x<=cx+H+4;x++)for(int z=cz-H-4;z<=cz+H+4;z++)if(Canvas.inPlan(x,z))c.own(x,z,CODE);
        // the court
        for(int x=cx-H;x<=cx+H;x++)for(int z=cz-H;z<=cz+H;z++){
            if(!Canvas.inPlan(x,z)||t.terrain.kind[Canvas.idx(x,z)]==Terrain.SEA)continue;
            t.lots.setGround(x,z,y);
            double h=Pal.hash(x,31,z);
            c.set(x,y,z,h<0.45?s(Blocks.COBBLESTONE):h<0.6?s(Blocks.MOSSY_COBBLESTONE):h<0.75?s(Blocks.GRAVEL):h<0.9?s(Blocks.MOSS_BLOCK):s(Blocks.GRASS_BLOCK));
        }
        // the walls, broken
        for(int x=cx-H;x<=cx+H;x++)for(int z=cz-H;z<=cz+H;z++){
            boolean edge=x==cx-H||x==cx+H||z==cz-H||z==cz+H;
            if(!edge)continue;
            double broken=Pal.smooth(x+z,x-z,5,61);
            int h=(int)Math.round(1+6*broken*broken);
            if(Math.abs(x-cx)<=2&&z==cz+H)h=0;                     // the gate
            if(broken<0.25)h=0;                                     // the gaps
            for(int k=1;k<=h;k++)c.set(x,y+k,z,t.terrain.masonry(x,y+k,z));
            if(h>0&&Pal.hash(x,32,z)<0.4)c.set(x,y+h+1,z,Canvas.slab(Blocks.MOSSY_STONE_BRICK_SLAB,false));
            // ivy on the walls' outer faces
            if(h>2)for(var d:Direction.Type.HORIZONTAL){
                int vx=x+d.getOffsetX(),vz=z+d.getOffsetZ();
                if(Math.abs(vx-cx)>H&&Pal.hash(vx,33,vz)<0.35||Math.abs(vz-cz)>H&&Pal.hash(vx,33,vz)<0.35)
                    for(int k=2;k<=h;k++)if(c.air(vx,y+k,vz))c.vine(vx,y+k,vz,d.getOpposite());
            }
        }
        // the columns: two rows, some whole, some broken, some fallen
        for(int z=cz-H+4;z<=cz+H-4;z+=4)for(int x:new int[]{cx-5,cx+5}){
            double h=Pal.hash(x,34,z);
            // a fallen column: its drums lying in a row
            if(h<0.2){for(int k=1;k<=4;k++)if(c.air(x+(x<cx?-k:k),y+1,z))c.set(x+(x<cx?-k:k),y+1,z,Pal.hash(x+k,35,z)<0.5?s(Blocks.MOSSY_STONE_BRICKS):s(Blocks.CRACKED_STONE_BRICKS));continue;}
            int top=h<0.5?2+(int)(h*6):7;
            c.set(x,y+1,z,s(Blocks.CHISELED_STONE_BRICKS));
            for(int k=2;k<=top;k++)c.set(x,y+k,z,Pal.hash(x,y+k,z)<0.45?s(Blocks.MOSSY_STONE_BRICK_WALL):s(Blocks.STONE_BRICK_WALL));
            if(top==7)c.set(x,y+8,z,s(Blocks.CHISELED_STONE_BRICKS));
        }
        // the altar
        for(int x=cx-2;x<=cx+2;x++)c.set(x,y+1,cz-H+3,Canvas.stairs(Blocks.STONE_BRICK_STAIRS,Direction.NORTH,false));
        c.set(cx,y+1,cz-H+2,s(Blocks.CHISELED_STONE_BRICKS));c.set(cx,y+2,cz-H+2,s(Blocks.LODESTONE));
        c.set(cx-1,y+2,cz-H+2,Blocks.CANDLE.getDefaultState().with(Properties.CANDLES,2).with(Properties.LIT,true));
        // the fallen tower at the north-west corner
        double tx=cx-H+0.5,tz=cz-H+0.5;
        for(int x=(int)tx-5;x<=tx+5;x++)for(int z=(int)tz-5;z<=tz+5;z++){
            double d=Math.hypot(x+0.5-tx,z+0.5-tz);
            if(d>4.5||d<3.4)continue;
            int h=(int)Math.round(3+9*Pal.smooth(x*3,z*3,4,62));
            for(int k=0;k<=h;k++)c.set(x,c.ground(x,z)+k+(k==0?0:0),z,t.terrain.masonry(x,y+k,z));
            c.ground(x,z,Math.max(c.ground(x,z),y+h));
        }
        // rubble round it
        for(int k=0;k<26;k++){
            int x=(int)tx+rnd.nextInt(14)-2,z=(int)tz+rnd.nextInt(14)-2,g=c.ground(x,z);
            if(c.air(x,g+1,z))c.set(x,g+1,z,rnd.nextBoolean()?s(Blocks.COBBLESTONE):Canvas.slab(Blocks.MOSSY_COBBLESTONE_SLAB,false));
        }
        // the great oak in the court
        int ox=cx+6,oz=cz+4;
        for(int k=1;k<=10;k++)for(int dx=0;dx<=1;dx++)for(int dz=0;dz<=1;dz++)c.set(ox+dx,y+k,oz+dz,Canvas.axis(Blocks.OAK_LOG,Direction.Axis.Y));
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2+0.6;
            for(int k=1;k<=4;k++)c.set(ox+(int)Math.round(Math.cos(a)*k),y+9+k/2,oz+(int)Math.round(Math.sin(a)*k),Canvas.axis(Blocks.OAK_LOG,Math.abs(Math.cos(a))>0.7?Direction.Axis.X:Direction.Axis.Z));
        }
        t.nature.blob(ox+1,y+13,oz+1,7,4,7,Blocks.OAK_LEAVES,Blocks.DARK_OAK_LEAVES);
        t.places.put("ruins",new int[]{cx,y+1,cz+H+2});
        Town.log("ruins at "+cx+","+cz+" y "+y);
    }

    // ---------------------------------------------------------------- paths

    /** Earth paths: from the loop road to the ruins' gate, from the axis road to the lake, round the lake's south. */
    private void paths(){
        path(-130,124,Plan.RUINS_X,Plan.RUINS_Z+15,8);
        path(-7,110,Plan.LAKE_X+Plan.LAKE_R+4,Plan.LAKE_Z-4,-6);
        path(Plan.LAKE_X,Plan.LAKE_Z+Plan.LAKE_R+3,Plan.LAKE_X-4,Plan.LAKE_Z+44,4);
        for(int x=Plan.X0;x<Plan.X0+Plan.W;x++)for(int z=Plan.Z0;z<Plan.Z0+Plan.D;z++){
            int i=Canvas.idx(x,z);
            if(!path[i]||t.roads.on(x,z)||t.terrain.water[i]!=0)continue;
            int g=c.ground(x,z);
            var top=c.get(x,g,z);
            if(!(top.isOf(Blocks.GRASS_BLOCK)||top.isOf(Blocks.DIRT)||top.isOf(Blocks.PODZOL)||top.isOf(Blocks.COARSE_DIRT)||top.isOf(Blocks.MOSS_BLOCK)))continue;
            c.set(x,g,z,Pal.hash(x,41,z)<0.15?s(Blocks.COARSE_DIRT):s(Blocks.DIRT_PATH));
            c.clear(x,g+1,z,x,g+3,z);
        }
    }
    /** A path two or three wide from one point to another, bowing sideways by {@code bow} blocks at its middle. */
    private void path(double x0,double z0,double x1,double z1,double bow){
        double len=Math.hypot(x1-x0,z1-z0),nx=-(z1-z0)/len,nz=(x1-x0)/len;
        int n=(int)(len*3);
        for(int k=0;k<=n;k++){
            double s=k/(double)n,b=bow*4*s*(1-s);
            double x=x0+(x1-x0)*s+nx*b+Math.sin(s*9)*1.2,z=z0+(z1-z0)*s+nz*b+Math.cos(s*7)*1.2;
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                int px=(int)Math.floor(x+dx),pz=(int)Math.floor(z+dz);
                if(Canvas.inPlan(px,pz)&&Math.hypot(px+0.5-x,pz+0.5-z)<1.4)path[Canvas.idx(px,pz)]=true;
            }
        }
    }

    // ---------------------------------------------------------------- the trees

    /**
     * The forest: a tree tried every six blocks or so, in groves (thick where the grove noise is high), clearings where
     * it is low; oaks, birches, dark oaks, spruces; none on the paths, the roads, the shore, the lake or the ruins, nor
     * within a few blocks of them. Grass, ferns and flowers under them.
     */
    private void trees(){
        for(int x=Plan.X0+3;x<0;x+=6)for(int z=0;z<Plan.Z0+Plan.D-3;z+=6){
            int px=x+rnd.nextInt(5)-2,pz=z+rnd.nextInt(5)-2;
            if(!Canvas.inPlan(px,pz)||Plan.zone(px,pz)!=Plan.FOREST)continue;
            int i=Canvas.idx(px,pz);
            if(t.terrain.kind[i]!=Terrain.LAND||t.terrain.pale[i]||c.owner[i]!=0)continue;
            if(t.roads.near(px,pz,3)||nearPath(px,pz,2)||Plan.shore(px,pz)>-6)continue;
            int g=c.ground(px,pz);
            var top=c.get(px,g,pz);
            if(!(top.isOf(Blocks.GRASS_BLOCK)||top.isOf(Blocks.PODZOL)||top.isOf(Blocks.MOSS_BLOCK)||top.isOf(Blocks.COARSE_DIRT))||!c.air(px,g+1,pz))continue;
            double grove=Pal.smooth(px,pz,26,71);
            double p=grove<0.36?0:grove>0.62?0.62:0.32;
            if(rnd.nextDouble()>p)continue;
            int r=rnd.nextInt(100);
            if(r<42)t.nature.broadleaf(px,g+1,pz,5+rnd.nextInt(3));
            else if(r<66)t.nature.birch(px,g+1,pz,6+rnd.nextInt(3));
            else if(r<82)t.nature.cedar(px,g+1,pz,10+rnd.nextInt(6));
            else t.nature.darkOak(px,g+1,pz,6+rnd.nextInt(3));
            trees++;
        }
        int plants=0;
        for(int x=Plan.X0+1;x<0;x++)for(int z=0;z<Plan.Z0+Plan.D-1;z++){
            if(Plan.zone(x,z)!=Plan.FOREST)continue;
            int i=Canvas.idx(x,z);
            if(t.terrain.kind[i]!=Terrain.LAND||t.terrain.pale[i]||path[i]||t.roads.on(x,z))continue;
            int g=c.ground(x,z);
            var top=c.get(x,g,z);
            if(!(top.isOf(Blocks.GRASS_BLOCK)||top.isOf(Blocks.PODZOL))||!c.air(x,g+1,z))continue;
            double r=rnd.nextDouble();
            if(r<0.3&&t.nature.undergrowth(x,g+1,z,false))plants++;
            else if(r<0.31)c.set(x,g+1,z,rnd.nextBoolean()?s(Blocks.BROWN_MUSHROOM):s(Blocks.RED_MUSHROOM));
            else if(r<0.32&&top.isOf(Blocks.GRASS_BLOCK)){c.set(x,g+1,z,Blocks.PINK_PETALS.getDefaultState().with(Properties.FLOWER_AMOUNT,1+rnd.nextInt(4)));plants++;}
        }
        Town.log("forest: "+plants+" plants");
    }
    private boolean nearPath(int x,int z,int m){
        for(int dx=-m;dx<=m;dx++)for(int dz=-m;dz<=m;dz++)if(Canvas.inPlan(x+dx,z+dz)&&path[Canvas.idx(x+dx,z+dz)])return true;
        return false;
    }

    /** The sandy shore: driftwood lying on the sand, rocks, dead bushes, a little beach grass where the sand meets the grass. */
    private void shore(){
        int wood=0;
        for(int x=Plan.X0;x<0;x++)for(int z=0;z<Plan.Z0+Plan.D;z++){
            int i=Canvas.idx(x,z);
            if(t.terrain.kind[i]!=Terrain.BEACH)continue;
            int g=c.ground(x,z);
            if(!c.air(x,g+1,z))continue;
            double h=Pal.hash(x,51,z);
            if(h<0.004&&wood<26){
                Direction.Axis a=Pal.hash(x,52,z)<0.5?Direction.Axis.X:Direction.Axis.Z;
                int len=2+(int)(Pal.hash(x,53,z)*3);
                for(int k=0;k<len;k++){int wx=x+(a==Direction.Axis.X?k:0),wz=z+(a==Direction.Axis.Z?k:0);if(c.air(wx,c.ground(wx,wz)+1,wz))c.set(wx,c.ground(wx,wz)+1,wz,Canvas.axis(Blocks.STRIPPED_OAK_LOG,a));}
                wood++;
            }else if(h<0.012)c.set(x,g+1,z,s(Blocks.DEAD_BUSH));
            else if(h<0.016)boulder(x,z,0.9);
        }
        Town.log("shore: "+wood+" driftwood logs");
    }
}
