package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * The old Japanese town in the upper glass's west half (map v3.0: '일본 마을·신사'): old wooden houses with tiled roofs
 * and a few newer ones along the loop road and the axis road, set back with open ground between them; a canal inside
 * the loop in level pools held by little weirs, stone banks with a paved walk on either side, houses facing it, red
 * bridges across it and a wider pool halfway with the town's red arched bridge; cherry trees along it (only there and
 * at the shrine); power poles and their wires along the roads; the shrine on its hill at the far corner.
 */
final class OldTown {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int CODE=1;
    final List<Lot> lots=new ArrayList<>();
    OldTown(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}
    private static BlockState s(net.minecraft.block.Block b){return b.getDefaultState();}

    void build(){
        canal();
        t.shrine.build();
        double[][] loop=t.roads.lines.get(0),axis=t.roads.lines.get(2),canal=canalLine();
        int back=Plan.ROAD_HALF+3;
        // houses facing the canal first (it is the town's heart), then along the loop's inner and outer sides and the axis road
        lots.addAll(t.lots.along(canal,1,Plan.canalHalf(Plan.POOL_Z)+5,14,15,5,2,6,Plan.TOWN,"house",CODE));
        lots.addAll(t.lots.along(canal,-1,Plan.canalHalf(Plan.POOL_Z)+5,14,15,5,2,6,Plan.TOWN,"house",CODE));
        lots.addAll(t.lots.along(loop,-1,back,15,17,6,2,7,Plan.TOWN,"house",CODE));
        lots.addAll(t.lots.along(loop,1,back,15,17,6,2,7,Plan.TOWN,"house",CODE));
        lots.addAll(t.lots.along(axis,1,back,15,17,6,2,7,Plan.TOWN,"house",CODE));
        // then the open ground between: houses wherever they fit, each turned to its nearest road or the canal
        lots.addAll(t.lots.fill(Plan.TOWN,12,14,21,2,5,2,"house",CODE,40,rnd.nextLong()));
        String[] pool={"old","old","old","old","old","old","modern","modern","modern","modern3","apartment","garden","field"};
        Map<String,Integer> counts=new TreeMap<>();
        for(Lot l:lots){
            String use=pool[rnd.nextInt(pool.length)];
            if(use.equals("apartment")&&l.frontage()<13)use="modern";
            l.use=use;
            t.houses.build(l);
            counts.merge(use,1,Integer::sum);
        }
        Town.log("old town: "+lots.size()+" plots "+counts+", "+c.placed);
        cherries();
        poles(loop,-1);poles(axis,1);
        int[] p=t.lots.freeNear(axis,1,Plan.ROAD_HALF+2,-150,Plan.TOWN);
        if(p!=null)t.street.vending(p[0],p[1],Direction.NORTH,2);
        p=t.lots.freeNear(loop,-1,Plan.ROAD_HALF+2,-60,Plan.TOWN);
        if(p!=null)t.street.busStop(p[0],p[1],Direction.EAST,"運河通り");
        p=t.lots.freeNear(axis,1,Plan.ROAD_HALF+2,-100,Plan.TOWN);
        if(p!=null)t.street.postBox(p[0],p[1]);
        t.places.put("canal",new int[]{(int)Math.round(Plan.canalX(Plan.POOL_Z))+9,c.ground((int)Math.round(Plan.canalX(Plan.POOL_Z))+9,Plan.POOL_Z)+1,Plan.POOL_Z});
    }

    /** The canal's centre line, every half block, as the roads keep theirs. */
    static double[][] canalLine(){
        int n=(Plan.CANAL_Z1-Plan.CANAL_Z0)*2;double[][] p=new double[n+1][];
        for(int k=0;k<=n;k++){
            double z=Plan.CANAL_Z0+k*0.5+0.5,x=Plan.canalX(z),dx=Plan.canalX(z+0.5)-Plan.canalX(z-0.5),l=Math.hypot(dx,1);
            p[k]=new double[]{x,z,dx/l,1/l};
        }
        return p;
    }

    /**
     * The canal's works: banks of old masonry from its floor to the ground, a paved walk three blocks wide along each
     * bank, walls across its ends, a weir of stone between each pool and the next, small bridges and the red arched
     * bridge over the pool.
     */
    private void canal(){
        for(int z=Plan.CANAL_Z0-1;z<=Plan.CANAL_Z1+1;z++){
            double cx=Plan.canalX(z+0.5),half=Plan.canalHalf(z+0.5);
            for(int x=(int)Math.floor(cx-half-5);x<=cx+half+5;x++){
                int i=Canvas.idx(x,z);
                double d=Math.abs(x+0.5-cx);
                boolean end=z<Plan.CANAL_Z0||z>Plan.CANAL_Z1;
                if(t.terrain.water[i]!=0&&!end)continue;
                int g=c.ground(x,z);
                if(d<half+1||end&&d<half+1){
                    int floor=nearestWater(x,z)-2;
                    for(int y=floor;y<g;y++)c.set(x,y,z,t.terrain.masonry(x,y,z));
                    c.set(x,g,z,s(Blocks.STONE_BRICKS));
                    c.own(x,z,CODE);
                }else if(d<half+4&&!t.roads.on(x,z)){
                    c.set(x,g,z,Pal.paverBlock(x,z));c.clear(x,g+1,z,x,g+3,z);
                    c.own(x,z,CODE);
                }
            }
        }
        // the weirs between the pools
        for(int zb=Plan.CANAL_Z0+Terrain.POOL;zb<=Plan.CANAL_Z1;zb+=Terrain.POOL){
            double cx=Plan.canalX(zb+0.5),half=Plan.canalHalf(zb+0.5);
            int top=t.terrain.water[Canvas.idx((int)Math.floor(Plan.canalX(zb-0.5)),zb-1)];
            for(int x=(int)Math.floor(cx-half);x<=cx+half;x++){
                int i=Canvas.idx(x,zb);if(t.terrain.water[i]==0)continue;
                for(int y=t.terrain.water[i]-2;y<=top;y++)c.set(x,y,zb,t.terrain.masonry(x,y,zb));
            }
        }
        for(int z:new int[]{-141,-104,-93})bridge(z,false);
        bridge(Plan.POOL_Z-3,true);
    }
    private int nearestWater(int x,int z){
        for(int r=0;r<6;r++)for(int dx=-r;dx<=r;dx++){
            int zz=Math.max(Plan.CANAL_Z0,Math.min(Plan.CANAL_Z1,z));
            int w=Canvas.inPlan(x+dx,zz)?t.terrain.water[Canvas.idx(x+dx,zz)]:0;
            if(w!=0)return w;
        }
        return c.ground(x,z)-2;
    }
    /**
     * A bridge across the canal at z (three blocks wide along it): a deck of dark planks from bank to bank at the walks'
     * level, its middle a half block up; red rails along both edges, red posts with lanterns at its ends. The pool's
     * bridge is the town's red arched bridge, longer and rising a block and a half.
     */
    private void bridge(int z,boolean arched){
        double cx=Plan.canalX(z+1.5),half=Math.max(Plan.canalHalf(z+0.5),Math.max(Plan.canalHalf(z+1.5),Plan.canalHalf(z+2.5)));
        int x0=(int)Math.floor(cx-half-1),x1=(int)Math.ceil(cx+half);
        int y=Math.max(c.ground(x0-1,z+1),c.ground(x1+1,z+1));
        int span=x1-x0;
        for(int x=x0;x<=x1;x++){
            double u=Math.abs((x-x0)/(double)span-0.5)*2;
            int rise=arched?(u<0.3?3:u<0.65?2:u<0.9?1:0):(u<0.5?1:0);
            int dy=rise>>1;boolean slab=(rise&1)==1;
            for(int k=0;k<3;k++){
                int zz=z+k;
                c.clear(x,y+1,zz,x,y+4,zz);
                c.set(x,y+dy,zz,slab?Canvas.slab(Blocks.SPRUCE_SLAB,true):s(Blocks.SPRUCE_PLANKS));
                if(slab)c.set(x,y+dy+1,zz,Canvas.slab(Blocks.SPRUCE_SLAB,false));
            }
            int top=y+dy+(slab?2:1);
            c.set(x,top,z-1,s(Blocks.CRIMSON_FENCE));c.set(x,top,z+3,s(Blocks.CRIMSON_FENCE));
            c.set(x,top-1,z-1,s(Blocks.SPRUCE_PLANKS));c.set(x,top-1,z+3,s(Blocks.SPRUCE_PLANKS));
        }
        for(int x:new int[]{x0-1,x1+1})for(int zz:new int[]{z-1,z+3}){
            int g=c.ground(x,zz);
            c.set(x,g+1,zz,s(Blocks.RED_CONCRETE));c.set(x,g+2,zz,s(Blocks.RED_CONCRETE));c.set(x,g+3,zz,s(Blocks.LANTERN));
        }
    }

    /** Cherry trees along the canal's walks, on the walk's outer edge, alternating sides; fallen petals under them. */
    private void cherries(){
        int n=0;
        for(int z=Plan.CANAL_Z0+5;z<=Plan.CANAL_Z1-3;z+=11){
            int side=(n&1)==0?1:-1;
            double cx=Plan.canalX(z+0.5),half=Plan.canalHalf(z+0.5);
            int x=(int)Math.floor(cx+side*(half+5));
            int g=c.ground(x,z);
            if(!c.air(x,g+1,z)||t.roads.near(x,z,3))continue;
            if(!c.get(x,g,z).isOf(Blocks.GRASS_BLOCK)&&!c.get(x,g,z).isOf(Blocks.DIRT))c.set(x,g,z,s(Blocks.GRASS_BLOCK));
            t.nature.cherry(x,g+1,z,6+rnd.nextInt(2),1.15);n++;
        }
        Town.log("old town: "+n+" cherry trees by the canal");
    }

    /**
     * Power poles along a road on the given side, about every twenty-two blocks on the sidewalk's outer edge, their
     * lamps reaching over the road; a run of wires from pole to pole (none across the rift or other zones).
     */
    private void poles(double[][] line,int side){
        List<int[]> run=new ArrayList<>();
        for(int k=0;k<line.length;k+=44){
            double[] p=line[k];
            double nx=-p[3]*side,nz=p[2]*side;
            int x=(int)Math.floor(p[0]+nx*(Plan.ROAD_HALF-0.6)),z=(int)Math.floor(p[1]+nz*(Plan.ROAD_HALF-0.6));
            if(Plan.zone(x,z)!=Plan.TOWN||!t.roads.on(x,z)||t.roads.map[Canvas.idx(x,z)]!=Roads.WALK){if(!run.isEmpty()){t.street.addRun(run);run=new ArrayList<>();}continue;}
            Direction road=Math.abs(nx)>=Math.abs(nz)?(nx>0?Direction.WEST:Direction.EAST):(nz>0?Direction.NORTH:Direction.SOUTH);
            int[] top=t.street.pole(x,z,road,run.size()%3==0?1:0,run.size()%2==0);
            if(top!=null)run.add(top);
        }
        if(!run.isEmpty())t.street.addRun(run);
    }
}
