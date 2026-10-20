package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The battle ground: an old reclaimed lot on the waterfront beside the bridge, the town's open space for fights. A
 * wide sweep of weathered concrete and asphalt with faded lines, cracks with weeds in them; rusty corrugated sheds
 * and a container along its east side, a football goal at the west end, floodlight towers at the corners for night
 * fights, a low fence and tetrapods along the sea. The middle is left open.
 */
final class Ground {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int X0=Plan.LOT_X0,X1=Plan.LOT_X1,Z0=Plan.SEAWARD_Z,Z1=Plan.LOT_Z1,Y=Plan.COAST;
    Ground(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        surface();
        edges();
        shed(84,X1-1,Z0+4,Z0+19);
        shed(88,X1-1,Z0+27,Z0+40);
        container(X1-8,Z0+46,true);container(X1-4,Z0+46,false);
        goal(X0+5,(Z0+Z1)/2);
        for(int[] p:new int[][]{{X0+1,Z0+1},{X1-1,Z0+1},{X0+1,Z1-1},{X1-1,Z1-1}})floodlight(p[0],p[1]);
        props();
        t.places.put("battle",new int[]{(X0+X1)/2-6,Y+1,(Z0+Z1)/2});
        Town.log("battle ground: "+c.placed);
    }

    private void surface(){
        for(int x=X0;x<=X1;x++)for(int z=Z0;z<=Z1;z++){
            // old asphalt over most of it, worn through to the concrete under it in soft-edged patches
            double n=Pal.smooth(x,z,13,11)*0.75+Pal.smooth(x,z,5,12)*0.25,h=Pal.hash(x,11,z);
            BlockState top;
            if(n<0.6)top=h<0.12?s(Blocks.GRAY_CONCRETE):h<0.16?s(Blocks.COBBLED_DEEPSLATE):s(Blocks.GRAY_CONCRETE_POWDER);
            else if(n<0.72)top=h<0.5?s(Blocks.ANDESITE):s(Blocks.GRAY_CONCRETE_POWDER);
            else top=h<0.25?s(Blocks.ANDESITE):h<0.35?s(Blocks.STONE):s(Blocks.LIGHT_GRAY_CONCRETE);
            // cracks: dark lines wandering across, weeds growing out of them
            boolean crack=Math.abs(Math.sin(x*0.21+z*0.13)+Math.sin(z*0.17-x*0.07))<0.05;
            if(crack)top=s(Blocks.COBBLESTONE);
            c.set(x,Y,z,top);c.clear(x,Y+1,z,x,Y+8,z);c.ground(x,z,Y);
            if(crack&&rnd.nextInt(5)==0)c.set(x,Y+1,z,s(Blocks.GRASS));
        }
        // faded lines of an old car park and a court, the paint worn through in places
        BlockState paint=s(Blocks.WHITE_CONCRETE_POWDER);
        for(int x=X0+8;x<=X0+60;x+=4)for(int z=Z0+3;z<=Z0+8;z++)if(Pal.hash(x,12,z)<0.7)c.set(x,Y,z,paint);
        int cx0=X0+18,cx1=X0+62,cz0=Z0+20,cz1=Z1-12;
        for(int x=cx0;x<=cx1;x++)for(int z:new int[]{cz0,cz1})if(Pal.hash(x,13,z)<0.65)c.set(x,Y,z,paint);
        for(int z=cz0;z<=cz1;z++)for(int x:new int[]{cx0,cx1,(cx0+cx1)/2})if(Pal.hash(x,14,z)<0.65)c.set(x,Y,z,paint);
        // weeds along the edges where nobody walks
        for(int x=X0+1;x<X1;x++)for(int z:new int[]{Z1-1,Z1-2})if(rnd.nextInt(3)==0&&c.air(x,Y+1,z))c.set(x,Y+1,z,s(Blocks.GRASS));
    }

    /** The sea sides: a concrete edge, a low rusty fence on posts, tetrapods below. */
    private void edges(){
        for(int x=X0;x<=X1;x++)for(int z=Z0;z<=Z1;z++){
            boolean west=x==X0,east=x==X1,south=z==Z1;
            if(!west&&!east&&!south)continue;
            for(int y=Canvas.Y0;y<Y;y++)c.set(x,y,z,Pal.seawall(x,y,z));
            c.set(x,Y,z,s(Blocks.SMOOTH_STONE));
            c.set(x,Y+1,z,(x+z)%5==0?s(Blocks.ANDESITE_WALL):s(Blocks.IRON_BARS));
            int ox=west?x-1:east?x+1:x,oz=south?z+1:z;
            if(c.water(ox,Plan.SEA,oz)&&rnd.nextInt(3)==0)t.coast.tetrapod(ox,c.ground(ox,oz)+1+rnd.nextInt(Math.max(1,Plan.SEA-c.ground(ox,oz))),oz);
        }
        // a gap in the fence at the south end with a ladder down to the water
        for(int x=(X0+X1)/2-1;x<=(X0+X1)/2+1;x++)c.set(x,Y+1,Z1,s(Blocks.AIR));
    }

    /** A rusty corrugated shed: streaked iron walls, a low rusted roof, one end open on clutter inside. */
    private void shed(int x0,int x1,int z0,int z1){
        var f=new Frame(c,x0,z0,x1,z1,Direction.WEST,Y);
        int W=f.W,D=f.D;
        BlockState[] rust={s(Blocks.BROWN_TERRACOTTA),s(Blocks.ORANGE_TERRACOTTA),s(Blocks.TERRACOTTA),s(Blocks.BROWN_TERRACOTTA),s(Blocks.GRANITE),s(Blocks.LIGHT_GRAY_TERRACOTTA)};
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){
            boolean edge=x==0||x==W-1||z==0||z==D-1;
            f.set(x,0,z,s(Blocks.SMOOTH_STONE));
            for(int y=1;y<=5;y++){
                BlockState wall=rust[(int)(Pal.hash(f.wx(x,z),y/2,f.wz(x,z))*rust.length)];
                f.set(x,y,z,edge?wall:s(Blocks.AIR));
            }
        }
        // the open front (toward the lot), a dark interior with a boat on trestles and crates
        for(int x=2;x<W-2;x++)for(int y=1;y<=4;y++)f.set(x,y,0,s(Blocks.AIR));
        for(int x=2;x<W-2;x+=2)if(rnd.nextBoolean())f.set(x,1,D-2,s(Blocks.BARREL));
        for(int z=3;z<Math.min(D-2,11);z++){f.set(W/2,1,z,s(Blocks.SPRUCE_FENCE));f.set(W/2,2,z,s(Blocks.SPRUCE_PLANKS));}
        f.set(W/2-1,2,4,s(Blocks.SPRUCE_PLANKS));f.set(W/2+1,2,4,s(Blocks.SPRUCE_PLANKS));
        Kit.roof(f,0,0,W-1,D-1,6,Kit.GABLE,true,Kit.RUST,rust[0],s(Blocks.AIR));
        f.wallSign(W/2,5,-1,Blocks.SPRUCE_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,false,"","第二倉庫","");
    }
    private void container(int x,int z,boolean blue){
        BlockState body=blue?s(Blocks.CYAN_TERRACOTTA):s(Blocks.RED_TERRACOTTA);
        for(int dx=0;dx<3;dx++)for(int dz=0;dz<8;dz++)for(int y=Y+1;y<=Y+3;y++)c.set(x+dx,y,z+dz,body);
        for(int dx=0;dx<3;dx++)for(int y=Y+1;y<=Y+3;y++)c.set(x+dx,y,z,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,true,false));
    }

    /** A football goal: white posts and bar, a net behind. */
    private void goal(int x,int zc){
        for(int z=zc-3;z<=zc+3;z++){
            c.set(x,Y+3,z,s(Blocks.DIORITE_WALL));
            c.set(x-2,Y+1,z,s(Blocks.WHITE_STAINED_GLASS_PANE));c.set(x-2,Y+2,z,s(Blocks.WHITE_STAINED_GLASS_PANE));c.set(x-1,Y+3,z,s(Blocks.WHITE_STAINED_GLASS_PANE));
        }
        for(int z:new int[]{zc-3,zc+3})for(int y=Y+1;y<=Y+2;y++){c.set(x,y,z,s(Blocks.DIORITE_WALL));c.set(x-1,y,z,s(Blocks.WHITE_STAINED_GLASS_PANE));}
    }
    /** A floodlight tower: a tall grey mast with a bank of lamps turned toward the lot. */
    private void floodlight(int x,int z){
        int top=Y+15;
        for(int y=Y+1;y<=top;y++)c.set(x,y,z,s(Blocks.ANDESITE_WALL));
        int dx=x<(X0+X1)/2?1:-1,dz=z<(Z0+Z1)/2?1:-1;
        for(int i=0;i<=1;i++)for(int j=0;j<=1;j++){c.set(x+dx*i,top+1,z+dz*j,s(Blocks.SEA_LANTERN));c.set(x+dx*i,top+2,z+dz*j,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));}
    }
    /** Odds and ends round the edges: drums, pallets, an old upturned boat, a vending machine at the gate. */
    private void props(){
        for(int i=0;i<10;i++){
            int x=rnd.nextBoolean()?X0+2+rnd.nextInt(4):X1-14+rnd.nextInt(4),z=Z0+3+rnd.nextInt(Z1-Z0-6);
            if(!c.air(x,Y+1,z))continue;
            switch(rnd.nextInt(3)){
                case 0->c.set(x,Y+1,z,rnd.nextBoolean()?s(Blocks.BLUE_TERRACOTTA):s(Blocks.CAULDRON));
                case 1->{c.set(x,Y+1,z,Canvas.slab(Blocks.OAK_SLAB,false));}
                default->c.set(x,Y+1,z,s(Blocks.BARREL));
            }
        }
        t.street.vending(X0+2,Z0+1,Direction.EAST,2);
    }
}
