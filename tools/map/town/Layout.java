package dev.sangwoo.botandtown;

import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * Cuts the town's blocks into plots (map v2.0): the houses round the edges of the two residential blocks north of the
 * park and the school, each plot well apart from the next with the common fields inside; the shops along both sides
 * of the shopping street with a passage every few shops, the public bath, the post office and the clinic behind them,
 * the supermarket and its car park; the convenience store, the gas station and two rows of houses by the sea in the
 * south-east; the harbour's buildings and a row of old houses in the south-west. The town is flat, so every plot
 * stands at the town's level.
 */
final class Layout {
    final List<Lot> lots=new ArrayList<>();
    /** Ground levels set by plots, in half blocks, or {@link Terrain#NONE} (none since v2.0: the town is one level). */
    final int[] level=new int[Plan.W*Plan.D];
    private final Random rnd;
    static final int Y=Plan.COAST;
    /** How deep the house plots are, and how deep the shops along the main street. */
    static final int HOUSE_D=14,SHOP_D=14;
    Layout(Random rnd){this.rnd=rnd;Arrays.fill(level,Terrain.NONE);}

    void plan(){
        residential(Plan.COL_W,Plan.ROW_N);
        residential(Plan.COL_E,Plan.ROW_N);
        shoppingStreet();
        southEast();
        harbourRow();
        Town.log("plots: "+lots.size());
    }

    /**
     * A residential block: houses along the streets on all four sides, their plots apart from each other by a strip of
     * lawn (open to walk through), and the block's middle left as fields.
     */
    private void residential(int[] col,int[] row){
        int x0=col[0],x1=col[1],z0=row[0],z1=row[1];
        houses(x0+2,x1-2,z0,z0+HOUSE_D-1,Direction.NORTH);
        houses(x0+2,x1-2,z1-HOUSE_D+1,z1,Direction.SOUTH);
        // the east and west sides between the corners
        int a=z0+HOUSE_D+6,b=z1-HOUSE_D-6;
        housesAlongZ(x0,x0+HOUSE_D-1,a,b,Direction.WEST);
        housesAlongZ(x1-HOUSE_D+1,x1,a,b,Direction.EAST);
        lots.add(new Lot(x0+HOUSE_D+6,a,x1-HOUSE_D-6,b,Y,Direction.NORTH,"commons"));
    }
    /** House plots facing north or south along x0..x1, 12..16 wide, with 6..8 blocks of open lawn between them. */
    private void houses(int x0,int x1,int z0,int z1,Direction front){
        int x=x0;
        while(x<=x1){
            int w=12+rnd.nextInt(5);
            if(x1-(x+w-1)<12)w=x1-x+1;
            if(w>18){int half=(w-6)/2;lots.add(new Lot(x,z0,x+half-1,z1,Y,front,"house"));x+=half+6;w=x1-x+1;}
            lots.add(new Lot(x,z0,x+w-1,z1,Y,front,"house"));
            x+=w+6+rnd.nextInt(3);
        }
    }
    private void housesAlongZ(int x0,int x1,int z0,int z1,Direction front){
        int z=z0;
        while(z<=z1){
            int w=12+rnd.nextInt(4);
            if(z1-(z+w-1)<12)w=z1-z+1;
            if(w>=12)lots.add(new Lot(x0,z,x1,z+w-1,Y,front,"house"));
            z+=w+7;
        }
    }

    /**
     * The shopping street: shops shoulder to shoulder along both sides of the main street in groups of two or three,
     * a passage five blocks wide between the groups; behind the west side the public bath (on West Avenue), the post
     * office (on North Street) and the clinic (on Middle Street); behind the east side the supermarket with its car
     * park in front of it.
     */
    private void shoppingStreet(){
        int z0=Plan.ROW_N[0]+2,z1=Plan.ROW_N[1]-2;
        shops(-6-SHOP_D,-7,z0,z1,Direction.EAST);
        shops(7,6+SHOP_D,z0,z1,Direction.WEST);
        lots.add(new Lot(-58,-116,-40,-96,Y,Direction.WEST,"sento"));
        lots.add(new Lot(-40,Plan.ROW_N[0],-25,Plan.ROW_N[0]+12,Y,Direction.NORTH,"post"));
        lots.add(new Lot(-40,Plan.ROW_N[1]-12,-25,Plan.ROW_N[1],Y,Direction.SOUTH,"clinic"));
        lots.add(new Lot(23,Plan.ROW_N[0],Plan.COL_C[1],Plan.ROW_N[1],Y,Direction.SOUTH,"supermarket"));
    }
    private void shops(int x0,int x1,int z0,int z1,Direction front){
        int z=z0,inGroup=0,group=2+rnd.nextInt(2);
        while(z<=z1){
            int w=9+rnd.nextInt(4);
            if(z1-(z+w-1)<9)w=z1-z+1;
            if(w<9)break;
            lots.add(new Lot(x0,z,x1,z+w-1,Y,front,"shop"));
            z+=w;
            if(++inGroup==group){z+=5;inGroup=0;group=2+rnd.nextInt(2);}
        }
    }

    /**
     * The south-east: the convenience store on South Street at East Avenue, the gas station on the coastal road below
     * it, houses facing South Street and the coastal road east of them, a strip of lawn between.
     */
    private void southEast(){
        lots.add(new Lot(Plan.COL_E[0],Plan.ROW_S[0],Plan.COL_E[0]+21,Plan.ROW_S[0]+17,Y,Direction.NORTH,"konbini"));
        lots.add(new Lot(Plan.COL_E[0],Plan.ROW_S[1]-17,Plan.COL_E[0]+27,Plan.ROW_S[1],Y,Direction.SOUTH,"gas"));
        houses(Plan.COL_E[0]+30,Plan.COL_E[1]-2,Plan.ROW_S[0],Plan.ROW_S[0]+12,Direction.NORTH);
        houses(Plan.COL_E[0]+34,Plan.COL_E[1]-2,Plan.ROW_S[1]-12,Plan.ROW_S[1],Direction.SOUTH);
    }

    /**
     * The harbour's side of the town, south-west: the fishing co-op, the ice plant and two gear sheds facing the
     * coastal road and the quay across it, a row of old houses facing South Street.
     */
    private void harbourRow(){
        int z1=Plan.ROW_S[1];
        lots.add(new Lot(-170,z1-12,-154,z1,Y,Direction.SOUTH,"gear"));
        lots.add(new Lot(-144,z1-13,-122,z1,Y,Direction.SOUTH,"ice"));
        lots.add(new Lot(-112,z1-13,-92,z1,Y,Direction.SOUTH,"coop"));
        lots.add(new Lot(-86,z1-11,-73,z1,Y,Direction.SOUTH,"gear"));
        houses(Plan.COL_W[0]+2,Plan.COL_W[1]-2,Plan.ROW_S[0],Plan.ROW_S[0]+12,Direction.NORTH);
    }

    Lot at(int x,int z){for(Lot l:lots)if(l.contains(x,z))return l;return null;}
}
