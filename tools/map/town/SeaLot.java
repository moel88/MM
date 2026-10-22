package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The battle ground (map v2.0): the old lot by the sea, reclaimed land reaching out from the promenade, with nothing in
 * its middle. Old, pale, coarse asphalt with cracks, patches and weeds, a faded court painted across it with a small
 * goal at each end; the sea wall round its three sea sides, a low block wall with a pipe rail over it and tetrapods
 * heaped outside; a rusty shed and an old container in its far corners, tall floodlights at the four corners for
 * fighting after dark, short white posts along the promenade with room to walk between them.
 */
final class SeaLot {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int X0=Plan.LOT_X0,X1=Plan.LOT_X1,Z0=Plan.SEAWARD_Z,Z1=Plan.LOT_Z1,Y=Plan.COAST;
    /** The court painted across the middle (x, z inclusive). */
    static final int CX0=X0+20,CX1=X1-20,CZ0=Z0+10,CZ1=Z1-10;
    SeaLot(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        surface();
        court();
        walls();
        shed(X1-14,Z1-10);
        container(X0+4,Z1-7);
        for(int[] p:new int[][]{{X0+2,Z0+2},{X1-2,Z0+2},{X0+2,Z1-2},{X1-2,Z1-2}})floodlight(p[0],p[1]);
        goal(CX0-1,(CZ0+CZ1)/2,Direction.EAST);goal(CX1+1,(CZ0+CZ1)/2,Direction.WEST);
        for(int x=X0+2;x<=X1-2;x+=5)if(c.air(x,Y+1,Plan.PARAPET_Z))c.set(x,Y+1,Plan.PARAPET_Z,TB.PIPE);
        c.wallSign(X0+6,Y+1,Plan.PARAPET_Z-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLACK,false,"ぼたんど町","海浜広場","火気厳禁");
        t.places.put("battle",new int[]{(X0+X1)/2,Y+1,(Z0+Z1)/2});
        Town.log("battle ground: "+c.placed);
    }

    /** Old pale asphalt over the whole lot, repaired in darker squares, cracked, weeds in the cracks and at the edges. */
    private void surface(){
        for(int x=X0;x<=X1;x++)for(int z=Plan.PARAPET_Z;z<=Z1;z++){
            c.set(x,Y,z,TB.WORN_ASPHALT);c.clear(x,Y+1,z,x,Y+8,z);c.ground(x,z,Y);
        }
        for(int k=0;k<24;k++){
            int px=X0+3+rnd.nextInt(X1-X0-8),pz=Z0+3+rnd.nextInt(Z1-Z0-8),pw=2+rnd.nextInt(4),pd=1+rnd.nextInt(3);
            for(int x=px;x<px+pw;x++)for(int z=pz;z<pz+pd;z++)c.set(x,Y,z,TB.PATCHED);
        }
        for(int k=0;k<140;k++){
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
        for(int x=X0+1;x<X1;x++){if(rnd.nextInt(3)==0)weed(x,Z1-1);}
        for(int z=Z0+2;z<Z1;z++){if(rnd.nextInt(3)==0)weed(X1-1,z);if(rnd.nextInt(3)==0)weed(X0+1,z);}
    }
    private void weed(int x,int z){
        if(!c.air(x,Y+1,z))return;
        if(rnd.nextInt(8)==0&&c.air(x,Y+2,z))c.tallPlant(x,Y+1,z,Blocks.TALL_GRASS);else c.set(x,Y+1,z,s(rnd.nextInt(6)==0?Blocks.FERN:Blocks.GRASS));
    }

    /** A faded court: its outline, the half-way line and the centre circle, worn through in places. */
    private void court(){
        for(int x=CX0;x<=CX1;x++){paint(x,CZ0,Direction.Axis.X);paint(x,CZ1,Direction.Axis.X);}
        for(int z=CZ0;z<=CZ1;z++){paint(CX0,z,Direction.Axis.Z);paint(CX1,z,Direction.Axis.Z);paint((CX0+CX1)/2,z,Direction.Axis.Z);}
        int mx=(CX0+CX1)/2,mz=(CZ0+CZ1)/2;
        for(int a=0;a<360;a+=3){
            double r=Math.toRadians(a);
            int x=(int)Math.round(mx+Math.cos(r)*8),z=(int)Math.round(mz+Math.sin(r)*8);
            paint(x,z,Math.abs(Math.cos(r))>0.7?Direction.Axis.Z:Direction.Axis.X);
        }
    }
    /** One block of faded paint, worn through in places (a third gone, in runs). */
    private void paint(int x,int z,Direction.Axis along){
        double wear=Pal.smooth(along==Direction.Axis.X?x:z,along==Direction.Axis.X?z:x,4,41);
        if(wear<0.2||Pal.hash(x,43,z)<0.08)return;
        c.set(x,Y,z,TB.painted("worn_asphalt_line",along));
    }

    /**
     * The sea wall round the lot's three sea sides: concrete from the sea floor up, a knee-high wall of blocks on it
     * with a pipe rail over it (a gap with a rail across it in the middle of the south side), tetrapods heaped outside.
     */
    private void walls(){
        for(int x=X0;x<=X1;x++)for(int z=Z0;z<=Z1;z++){
            boolean edge=x==X0||x==X1||z==Z1;
            if(!edge)continue;
            for(int y=Canvas.Y0;y<Y;y++)c.set(x,y,z,Pal.seawall(x,y,z));
            c.set(x,Y,z,s(Blocks.SMOOTH_STONE));
            boolean gap=z==Z1&&Math.abs(x-(X0+X1)/2)<=2;
            if(gap){c.set(x,Y+1,z,TB.GUARDRAIL);continue;}
            c.set(x,Y+1,z,TB.BLOCK_WALL);
            boolean post=(x==X0||x==X1)?Math.floorMod(z,3)==0:Math.floorMod(x,3)==0;
            c.set(x,Y+2,z,post?TB.PIPE:TB.GUARDRAIL);
            int ox=x==X0?-1:x==X1?1:0,oz=z==Z1&&ox==0?1:0;
            if(c.water(x+ox,Plan.SEA,z+oz)&&rnd.nextInt(3)==0){
                int fx=x+ox,fz=z+oz;
                t.coast.tetrapod(fx,c.ground(fx,fz)+1+rnd.nextInt(Math.max(1,Plan.SEA-c.ground(fx,fz))),fz);
            }
        }
    }

    /** A low shed of white and grey corrugated iron streaked with rust, its rusty doors, odds and ends inside. */
    private void shed(int x0,int z0){
        var f=new Frame(c,x0,z0,x0+9,z0+5,Direction.NORTH,Y);
        int W=f.W,D=f.D;
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){
            boolean edge=x==0||x==W-1||z==0||z==D-1;
            f.set(x,0,z,s(Blocks.SMOOTH_STONE));
            boolean rust=Pal.hash(f.wx(x,z),31,f.wz(x,z))<0.3;
            for(int y=1;y<=2;y++)f.set(x,y,z,edge?(rust?TB.RUSTY:TB.CORRUGATED):s(Blocks.AIR));
        }
        for(int x=4;x<=5;x++)for(int y=1;y<=2;y++)f.set(x,y,0,TB.RUSTY);
        Kit.roof(f,0,0,W-1,D-1,3,Kit.GABLE,true,Kit.SHEET,TB.CORRUGATED,s(Blocks.AIR));
        f.set(W-2,1,D-2,s(Blocks.BARREL));f.set(W-3,1,D-2,s(Blocks.BARREL));
        f.wallSign(W/2+1,2,-1,Blocks.SPRUCE_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,false,"","第二倉庫","");
    }
    /** An old shipping container, rusted, its doors shut. */
    private void container(int x0,int z0){
        for(int x=x0;x<=x0+6;x++)for(int z=z0;z<=z0+2;z++)for(int y=Y+1;y<=Y+3;y++)
            c.set(x,y,z,x==x0+6?Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.EAST,true,false):TB.RUSTY);
        c.set(x0+8,Y+1,z0,s(Blocks.BARREL));c.set(x0+8,Y+1,z0+1,s(Blocks.BLUE_CONCRETE));
    }
    /** A tall floodlight: a grey mast, a crossbar with three lamps facing the lot. */
    private void floodlight(int x,int z){
        for(int y=Y+1;y<=Y+12;y++)c.set(x,y,z,s(Blocks.ANDESITE_WALL));
        for(int k=-1;k<=1;k++){
            int dx=z<(Z0+Z1)/2?k:k,dz=0;
            c.set(x+dx,Y+13,z+dz,s(Blocks.POLISHED_ANDESITE));
            c.set(x+dx,Y+12,z+dz+(z<(Z0+Z1)/2?1:-1),s(Blocks.SEA_LANTERN));
        }
    }
    /** A small white goal, its mouth toward {@code mouth}: pipe posts, a bar, the net behind. */
    private void goal(int x,int zc,Direction mouth){
        int back=-mouth.getOffsetX();
        for(int z=zc-2;z<=zc+2;z++){
            boolean post=z==zc-2||z==zc+2;
            c.set(x,Y+1,z,post?TB.PIPE:s(Blocks.AIR));
            c.set(x,Y+2,z,post?TB.PIPE:TB.GUARDRAIL);
            c.set(x+back,Y+1,z,TB.NET);
        }
    }
}
