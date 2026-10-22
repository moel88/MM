package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The town's middle (map v2.0): the square before the town hall, the town's widest open ground and where players
 * appear. The town hall (west) and the community hall with the library (east) stand along its north side with the
 * shopping street running down between them onto the square; the square itself is laid in pale pavers with a compass
 * rose in Botand's sky blue at its heart, a row of zelkovas along each side with benches between them, a few lamps,
 * the police box, and the two merchants' stalls near its south edge facing the middle.
 */
final class Plaza {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int Y=Plan.COAST,X0=Plan.COL_C[0],X1=Plan.COL_C[1],Z0=Plan.ROW_M[0],Z1=Plan.ROW_M[1];
    /** The compass rose's middle (the spawn), its outer ring's radius. */
    static final int CX=Plan.SPAWN_X,CZ=Plan.SPAWN_Z,R=13;
    Plaza(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        paving();
        compass();
        townHall(new Frame(c,-54,Z0+2,-16,Plan.HALL_Z1-1,Direction.SOUTH,Y));
        communityHall(new Frame(c,16,Z0+2,54,Plan.HALL_Z1-1,Direction.SOUTH,Y));
        trees();
        flowers();
        stall(-21,true);stall(18,false);
        policeBox();
        lamps();
        t.places.put("square",new int[]{CX,Y+1,CZ});
        Town.log("square: "+c.placed);
    }

    /** Pale pavers over the whole block, a darker band every nine blocks, granite kerbs round the edge. */
    private void paving(){
        for(int x=X0;x<=X1;x++)for(int z=Z0;z<=Z1;z++){
            boolean edge=x==X0||x==X1||z==Z0||z==Z1;
            boolean band=Math.floorMod(x-CX,9)==4||Math.floorMod(z-CZ,9)==4;
            c.set(x,Y,z,edge?s(Blocks.POLISHED_GRANITE):band?s(Blocks.POLISHED_ANDESITE):TB.PAVING);
            c.clear(x,Y+1,z,x,Y+10,z);c.ground(x,z,Y);
        }
    }

    /**
     * The compass rose let into the paving: a sky-blue outer ring, a white inner one, long sky-blue points north,
     * east, south and west and short white ones between, a light at the middle that glows at night.
     */
    private void compass(){
        for(int x=CX-R-1;x<=CX+R+1;x++)for(int z=CZ-R-1;z<=CZ+R+1;z++){
            double dx=x-CX,dz=z-CZ,d=Math.hypot(dx,dz);
            if(d>=R+0.5)continue;
            BlockState b=s(Blocks.POLISHED_DIORITE);
            if(d>=R-1)b=s(Blocks.LIGHT_BLUE_CONCRETE);
            else if(d>=8.0&&d<9.0)b=s(Blocks.WHITE_CONCRETE);
            // the points: the main four reach the outer ring, the diagonals the inner one
            double along=Math.max(Math.abs(dx),Math.abs(dz)),across=Math.min(Math.abs(dx),Math.abs(dz));
            if(d<R-1&&across<=(R-1.5-along)*0.30+0.5)b=s(Blocks.LIGHT_BLUE_CONCRETE);
            double diag=(Math.abs(dx)+Math.abs(dz))/Math.sqrt(2),off=Math.abs(Math.abs(dx)-Math.abs(dz))/Math.sqrt(2);
            if(d<8&&off<=(7.5-diag)*0.28+0.3)b=s(Blocks.WHITE_CONCRETE);
            if(d<1.5)b=s(Blocks.SEA_LANTERN);
            c.set(x,Y,z,b);
        }
    }

    /**
     * ぼたんど町役場: three storeys of white walls with long bands of windows, a solid middle bay carrying the town's
     * name and its sky-blue mark, a flat roof; the glass entrance under a flat canopy, three flagpoles in front.
     */
    private void townHall(Frame f){
        int W=f.W,D=f.D,m=W/2;
        for(int x=-1;x<=W;x++)for(int z=-4;z<D;z++){f.set(x,0,z,z<0?TB.PAVING:s(Blocks.SMOOTH_STONE));f.clear(x,1,z,x,14,z);}
        Kit.shell(f,0,0,W-1,D-1,3,TB.STUCCO_WHITE,TB.CONCRETE,TB.TRIM_WHITE,s(Blocks.SMOOTH_STONE),TB.CONCRETE);
        bands(f,m,3,TB.WIN,TB.WIN_WHITE_CURTAIN);
        // the middle bay: solid, its name and the town's mark, taller than the roof
        for(int x=m-3;x<=m+3;x++)for(int y=5;y<=14;y++)f.set(x,y,0,y>=13?TB.TRIM_WHITE:TB.CONCRETE);
        for(int x=m-1;x<=m+1;x++)for(int y=10;y<=12;y++)f.set(x,y,-1,x==m&&y==11?s(Blocks.WHITE_CONCRETE):s(Blocks.LIGHT_BLUE_CONCRETE));
        f.wallSign(m,7,-1,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"","ぼたんど町役場","BOTAND TOWN HALL");
        entrance(f,m);
        Kit.roof(f,0,0,W-1,D-1,12,Kit.FLAT,true,Kit.SLATE,TB.STUCCO_WHITE,s(Blocks.SMOOTH_STONE));
        for(int s=0;s<3;s++)for(int x=3;x<W-3;x+=6)Kit.roomLight(f,x,4*s,D/2,false);
        // inside the entrance: the counter
        for(int x=m-5;x<=m+5;x++)f.set(x,1,5,s(Blocks.SMOOTH_QUARTZ));
        for(int i=-1;i<=1;i++)flagpole(f,m+i*5,-6,i==0?Blocks.LIGHT_BLUE_WALL_BANNER:Blocks.WHITE_WALL_BANNER);
        Kit.airCon(f,W,1,D-3,Direction.EAST);
        t.places.put("town_hall",new int[]{f.wx(m,-3),Y+1,f.wz(m,-3)});
    }

    /**
     * ぼたんど公民館・図書館: two storeys of warm timber and glass, the library's tall windows, a tiled hip roof that
     * answers the old houses, its name over the door.
     */
    private void communityHall(Frame f){
        int W=f.W,D=f.D,m=W/2;
        for(int x=-1;x<=W;x++)for(int z=-4;z<D;z++){f.set(x,0,z,z<0?TB.PAVING:s(Blocks.SMOOTH_STONE));f.clear(x,1,z,x,16,z);}
        Kit.shell(f,0,0,W-1,D-1,2,TB.BOARDS,TB.TIMBER,TB.TIMBER,s(Blocks.OAK_PLANKS),TB.CONCRETE);
        // tall windows of the reading room on the ground floor, a band above
        for(int x=1;x<W-1;x++){
            if(x%4==0)continue;
            for(int y=1;y<=3;y++)f.set(x,y,0,TB.WIN_WHITE);
            f.set(x,6,0,TB.WIN_WHITE);f.set(x,7,0,TB.WIN_WHITE);
            f.set(x,2,D-1,TB.out(TB.WIN_WHITE,Direction.SOUTH));f.set(x,6,D-1,TB.out(TB.WIN_WHITE,Direction.SOUTH));
        }
        for(int z=2;z<D-2;z+=3){f.set(0,2,z,TB.out(TB.WIN_WHITE,Direction.WEST));f.set(W-1,2,z,TB.out(TB.WIN_WHITE,Direction.EAST));}
        entrance(f,m);
        f.wallSign(m,5,-1,Blocks.SPRUCE_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"","ぼたんど公民館","図書館 LIBRARY");
        // shelves and reading tables inside
        for(int z=3;z<D-2;z+=3)for(int x=2;x<m-3;x++)f.set(x,1,z,s(Blocks.BOOKSHELF));
        for(int x=m+4;x<W-2;x+=3)f.set(x,1,D/2,Canvas.slab(Blocks.SPRUCE_SLAB,true));
        for(int s=0;s<2;s++)for(int x=3;x<W-3;x+=6)Kit.roomLight(f,x,4*s,D/2,true);
        Kit.roof(f,0,0,W-1,D-1,8,Kit.HIP,true,Kit.KAWARA,TB.PLASTER,s(Blocks.SPRUCE_PLANKS));
        t.places.put("library",new int[]{f.wx(m,-3),Y+1,f.wz(m,-3)});
    }

    /** Long bands of windows along the front and back of every storey, broken by a pier every sixth block and the middle bay. */
    private void bands(Frame f,int m,int stories,BlockState pane,BlockState curtained){
        int W=f.W,D=f.D;
        for(int s=0;s<stories;s++){
            int fy=4*s;
            for(int x=1;x<W-1;x++){
                if(x%6==0||s>0&&Math.abs(x-m)<=3||s==0&&Math.abs(x-m)<=2)continue;
                BlockState p=s>0&&Pal.hash(f.wx(x,0),fy,f.wz(x,0))<0.3?curtained:pane;
                f.set(x,fy+2,0,p);f.set(x,fy+3,0,p);
                f.set(x,fy+2,D-1,TB.out(pane,Direction.SOUTH));
            }
            for(int z=2;z<D-2;z+=3){f.set(0,fy+2,z,TB.out(pane,Direction.WEST));f.set(W-1,fy+2,z,TB.out(pane,Direction.EAST));}
        }
    }
    /** A glass entrance five wide, its doors open, under a flat canopy on two slim columns. */
    private void entrance(Frame f,int m){
        for(int x=m-2;x<=m+2;x++){f.set(x,1,0,s(Blocks.AIR));f.set(x,2,0,s(Blocks.AIR));f.set(x,3,0,TB.WIN);}
        for(int x=m-4;x<=m+4;x++)for(int z=-4;z<=-1;z++)f.set(x,4,z,Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,true));
        for(int x:new int[]{m-4,m+4})for(int y=1;y<=3;y++)f.set(x,y,-4,s(Blocks.QUARTZ_PILLAR));
        f.set(m,3,-2,Canvas.rod(Blocks.END_ROD,Direction.DOWN));
    }
    /** A flagpole: a thin mast with a flag at its head and a gilt ball on top. */
    private void flagpole(Frame f,int x,int z,Block flag){
        for(int y=1;y<=9;y++)f.set(x,y,z,s(Blocks.IRON_BARS));
        f.set(x,10,z,s(Blocks.GOLD_BLOCK));
        f.set(x,8,z-1,s(flag).with(Properties.HORIZONTAL_FACING,Direction.NORTH));
    }

    /** Zelkovas in round beds down both long sides of the square, benches facing in between them. */
    private void trees(){
        for(int side=-1;side<=1;side+=2){
            int x=CX+side*50;
            for(int z=Plan.SQUARE_Z0+6;z<=Z1-6;z+=12){
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)c.set(x+dx,Y,z+dz,dx==0&&dz==0?s(Blocks.GRASS_BLOCK):s(Blocks.MOSS_BLOCK));
                t.nature.townTree(x,Y+1,z,6+rnd.nextInt(2));
                int bz=z+6;
                if(bz<Z1-3)for(int k=-1;k<=0;k++)c.set(x+side*-2,Y+1,bz+k,Canvas.stairs(Blocks.SPRUCE_STAIRS,side<0?Direction.WEST:Direction.EAST,false));
            }
        }
        // a pair of cherry trees either side of the shopping street's mouth onto the square
        for(int side=-1;side<=1;side+=2){
            int x=side*12,z=Plan.HALL_Z1+3;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)c.set(x+dx,Y,z+dz,s(Blocks.GRASS_BLOCK));
            t.nature.cherry(x,Y+1,z,6,1.15);
        }
    }

    /** Flower beds in neat rows either side of the way in from the shopping street, between the two halls. */
    private void flowers(){
        Block[] rows={Blocks.RED_TULIP,Blocks.WHITE_TULIP,Blocks.PINK_TULIP,Blocks.AZURE_BLUET,Blocks.ORANGE_TULIP,Blocks.CORNFLOWER,Blocks.OXEYE_DAISY};
        for(int side=-1;side<=1;side+=2)for(int k=0;k<6;k++){
            int x=side*(9+k);
            for(int z=Z0+3;z<=Plan.HALL_Z1-2;z++){
                boolean edge=k==5||z==Z0+3||z==Plan.HALL_Z1-2;
                c.set(x,Y,z,edge?s(Blocks.POLISHED_GRANITE):s(Blocks.GRASS_BLOCK));
                if(!edge)c.set(x,Y+1,z,s(rows[Math.floorMod(z,rows.length)]));
            }
        }
    }

    /**
     * A market stall on the square for one of the mod's two merchants: a counter of barrels, four posts, a striped
     * awning with what the merchant does, a lantern. The merchant stands behind the counter facing the square (north).
     */
    private void stall(int x0,boolean seller){
        int z0=Z1-8,y=Y;
        for(int x=x0;x<=x0+3;x++){
            c.set(x,y+1,z0,x==x0+1?s(Blocks.AIR):s(Blocks.BARREL));
            for(int z=z0;z<=z0+2;z++)c.set(x,y+4,z,s((x+z&1)==0?(seller?Blocks.RED_WOOL:Blocks.BLUE_WOOL):Blocks.WHITE_WOOL));
        }
        c.set(x0+1,y+1,z0,Canvas.slab(Blocks.SPRUCE_SLAB,true));
        for(int x:new int[]{x0,x0+3})for(int z:new int[]{z0,z0+2})for(int yy=y+1;yy<=y+3;yy++)if(!(z==z0&&yy==y+1))c.set(x,yy,z,s(Blocks.SPRUCE_FENCE));
        c.hangingLantern(x0+2,y+3,z0+1,false);
        c.wallSign(x0+1,y+4,z0-1,Blocks.SPRUCE_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"",seller?"아이템 판매":"아이템 매입",seller?"로카":"카카");
        t.merchants.add(new int[]{x0+1,y+1,z0+1,180});
    }

    /** 交番: a small white police box with the red lamp over its door, at the square's south-east corner. */
    private void policeBox(){
        var f=new Frame(c,X1-11,Z1-7,X1-5,Z1-2,Direction.WEST,Y);
        Kit.shell(f,0,0,f.W-1,f.D-1,1,s(Blocks.WHITE_CONCRETE),s(Blocks.WHITE_CONCRETE),s(Blocks.LIGHT_GRAY_CONCRETE),s(Blocks.SMOOTH_STONE),s(Blocks.SMOOTH_STONE));
        int m=f.W/2;
        f.set(m,1,0,s(Blocks.AIR));f.set(m,2,0,s(Blocks.AIR));
        f.set(1,2,0,TB.WIN_FROSTED);f.set(f.W-2,2,0,TB.WIN_FROSTED);
        f.set(m,4,-1,s(Blocks.SHROOMLIGHT));
        f.wallSign(m-1,3,-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLACK,false,"交番","KOBAN","파출소");
        Kit.roof(f,0,0,f.W-1,f.D-1,4,Kit.FLAT,true,Kit.SLATE,s(Blocks.WHITE_CONCRETE),s(Blocks.SMOOTH_STONE));
        f.set(m,3,2,Canvas.rod(Blocks.END_ROD,Direction.DOWN));
    }

    /** Four tall lamps round the compass rose, and the post box and a bus stop at the square's edges. */
    private void lamps(){
        for(int dx=-1;dx<=1;dx+=2)for(int dz=-1;dz<=1;dz+=2){
            int x=CX+dx*22,z=CZ+dz*20;
            for(int y=Y+1;y<=Y+6;y++)c.set(x,y,z,s(Blocks.POLISHED_BLACKSTONE_WALL));
            c.set(x,Y+7,z,s(Blocks.SEA_LANTERN));c.set(x,Y+8,z,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));
        }
        t.street.postBox(X0+3,Plan.HALL_Z1+2);
        t.street.vending(X0+2,Z1-1,Direction.EAST,2);
        t.street.busStop(-26,Plan.SOUTH.a0(),Direction.SOUTH,"町役場前");
    }
}
