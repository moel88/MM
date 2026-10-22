package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;
import java.nio.file.Path;
import java.util.*;

/**
 * Botand's old city in the lower glass's east half (map v3.0, the user's request: '원조 보탠드 맵처럼 평범한 건물들
 * 몇 개, 주차장, 투기장'): the first map's arena and a stretch of its car park as they were (lifted block for block
 * from Azure Garden v1.3 by tools/map/town/lift_azure.py), its plain white buildings each turned to face a road, on the
 * flat ground of the city above low cliffs; white lamps along the roads, azalea trees and benches between.
 */
final class OldCity {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int CODE=3;
    final Pieces pieces;
    OldCity(Townscape t,Path file)throws Exception{this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());pieces=new Pieces(file);}
    private static BlockState s(net.minecraft.block.Block b){return b.getDefaultState();}

    void build(){
        int y=Plan.BASE;
        var arena=pieces.get("arena");
        int ax=Plan.ARENA_X-arena.W()/2,az=Plan.ARENA_Z-arena.D()/2;
        clearSite(ax,az,ax+arena.W()-1,az+arena.D()-1,y);
        Pieces.paste(c,arena,ax,az,y);
        c.claim(ax-1,az-1,ax+arena.W(),az+arena.D(),CODE);
        t.places.put("arena",new int[]{Plan.ARENA_X,y+1,Plan.ARENA_Z});
        var parking=pieces.get("parking");
        clearSite(Plan.PARKING_X,Plan.PARKING_Z,Plan.PARKING_X+parking.W()-1,Plan.PARKING_Z+parking.D()-1,y);
        Pieces.paste(c,parking,Plan.PARKING_X,Plan.PARKING_Z,y);
        c.claim(Plan.PARKING_X-1,Plan.PARKING_Z-1,Plan.PARKING_X+parking.W(),Plan.PARKING_Z+parking.D(),CODE);
        t.places.put("parking",new int[]{Plan.PARKING_X+parking.W()/2,y+1,Plan.PARKING_Z+parking.D()/2});
        // the white buildings: each in the first place along a road where it fits, its door to the road
        double[][] loop=t.roads.lines.get(1),axis=t.roads.lines.get(3);
        int placed=0;
        for(String name:new String[]{"hall_b","house_a","house_b","kiosk","house_c","house_d","hall_a","hall_c"}){
            var p=pieces.get(name);
            List<Lot> got=new ArrayList<>();
            for(int[] way:new int[][]{{0,-1},{0,1},{1,-1}}){
                double[][] line=way[0]==0?loop:axis;
                got=t.lots.along(line,way[1],Plan.ROAD_HALF+2,p.frontage(),p.depth(),4,1,5,Plan.CITY,name,CODE,1);
                if(!got.isEmpty())break;
            }
            if(got.isEmpty()){Town.log("old city: no room for "+name);continue;}
            Lot l=got.get(0);
            // the plot is the city's flat ground: the building stands on it at the city's level
            Frame f=Frame.of(c,l);
            Pieces.paste(f,p);
            l.use=name;placed++;
            Town.log("old city: "+name+" at "+l);
        }
        lamps(loop,-1);lamps(loop,1);lamps(axis,-1);
        trees();
        Town.log("old city: arena, car park, "+placed+" buildings, "+c.placed);
    }
    /** The site of a piece made level at the city's ground (the pasted blocks bring their own ground). */
    private void clearSite(int x0,int z0,int x1,int z1,int y){
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            if(!Canvas.inPlan(x,z))continue;
            t.lots.setGround(x,z,y);
            c.clear(x,y+1,z,x,y+30,z);
        }
    }

    /** White lamps along the roads as on the first map: a quartz post, a sea lantern under a quartz cap. */
    private void lamps(double[][] line,int side){
        for(int k=0;k<line.length;k+=40){
            double[] p=line[k];double nx=-p[3]*side,nz=p[2]*side;
            int x=(int)Math.floor(p[0]+nx*(Plan.ROAD_HALF-0.6)),z=(int)Math.floor(p[1]+nz*(Plan.ROAD_HALF-0.6));
            if(Plan.zone(x,z)!=Plan.CITY||!t.roads.on(x,z)||t.roads.map[Canvas.idx(x,z)]!=Roads.WALK)continue;
            int g=c.ground(x,z);boolean room=true;
            for(int yy=g+1;yy<=g+6;yy++)if(!c.air(x,yy,z))room=false;
            if(!room)continue;
            for(int yy=g+1;yy<=g+4;yy++)c.set(x,yy,z,Canvas.axis(Blocks.QUARTZ_PILLAR,Direction.Axis.Y));
            c.set(x,g+5,z,s(Blocks.SEA_LANTERN));c.set(x,g+6,z,Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,false));
        }
    }
    /** Azalea trees on the open grass between the buildings, as the first map's, and a few benches. */
    private void trees(){
        int n=0;
        for(int x=4;x<Plan.X0+Plan.W-4;x+=8)for(int z=Plan.RIFT;z<Plan.Z0+Plan.D-4;z+=8){
            int px=x+rnd.nextInt(5)-2,pz=z+rnd.nextInt(5)-2;
            if(!Canvas.inPlan(px,pz)||Plan.zone(px,pz)!=Plan.CITY)continue;
            int i=Canvas.idx(px,pz);
            if(t.terrain.kind[i]!=Terrain.LAND||t.terrain.pale[i]||c.owner[i]!=0||t.roads.near(px,pz,3)||Plan.shore(px,pz)>-5)continue;
            if(nearOwned(px,pz,3))continue;
            int g=c.ground(px,pz);
            if(!c.get(px,g,pz).isOf(Blocks.GRASS_BLOCK)||!c.air(px,g+1,pz)||rnd.nextDouble()>0.35)continue;
            int h=4+rnd.nextInt(2);
            for(int k=1;k<=h;k++)c.set(px,g+k,pz,Canvas.axis(Blocks.OAK_LOG,Direction.Axis.Y));
            t.nature.blob(px+0.5,g+h+0.5,pz+0.5,2.6,1.9,2.6,Blocks.AZALEA_LEAVES,Blocks.FLOWERING_AZALEA_LEAVES);
            n++;
            if(rnd.nextInt(3)==0&&c.air(px+2,g+1,pz)&&c.get(px+2,g,pz).isOpaque())c.set(px+2,g+1,pz,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.EAST,false));
        }
        Town.log("old city: "+n+" azalea trees");
    }
    private boolean nearOwned(int x,int z,int m){
        for(int dx=-m;dx<=m;dx++)for(int dz=-m;dz<=m;dz++)if(c.owner(x+dx,z+dz)>0)return true;
        return false;
    }
}
