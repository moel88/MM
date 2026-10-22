package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The fishing harbour in the south-west (map v2.0): a concrete quay along the coastal road with bollards and rubber
 * fenders, the fish market's open shed with its white boxes and ice, boats moored along the quay (some of them squid
 * boats strung with lamps), a slipway, breakwaters heaped with tetrapods closing the basin with a red light and a white
 * one at its mouth; across the road the fishing co-op, the ice plant and the fishermen's gear sheds.
 */
final class Harbor {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int Y=Plan.COAST;
    /** The breakwaters: east (along x=EX south from the quay, then west along the arm at ARM_Z) and west (from the shore at WEST_Z). */
    static final int EX=-68,ARM_Z=162,ARM_X=-125,WEST_Z=150,WEST_X=-152;
    Harbor(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        quay();
        breakwaters();
        market(-150,-122);
        int[] spots={-176,-160,-114,-100,-86};
        for(int i=0;i<spots.length;i++)boat(spots[i],Plan.QUAY_Z1+4,i%2==0?1:-1,10+rnd.nextInt(5),i%3==1);
        boat(-110,140,1,13,true);boat(-160,132,-1,12,false);
        slipway();
        lighthouse(ARM_X+2,ARM_Z,true);lighthouse(WEST_X-2,WEST_Z,false);
        t.places.put("harbor",new int[]{-130,Y+1,Plan.QUAY_Z1-3});
        Town.log("harbour: "+c.placed);
    }

    /** The quay: concrete paving, the edge face, bollards and fenders, lamps. */
    private void quay(){
        for(int x=Plan.HARBOR_X0;x<=Plan.HARBOR_X1;x++)for(int z=Plan.PROM_Z0;z<=Plan.QUAY_Z1;z++){
            c.set(x,Y,z,Pal.concrete(x,z));c.clear(x,Y+1,z,x,Y+8,z);c.ground(x,z,Y);
            if(z==Plan.QUAY_Z1){
                for(int y=Canvas.Y0;y<Y;y++)c.set(x,y,z,Pal.seawall(x,y,z));
                if(Math.floorMod(x,7)==0){c.set(x,Y+1,z,s(Blocks.POLISHED_BLACKSTONE_WALL));c.set(x,Plan.SEA,z+1,s(Blocks.BLACK_WOOL));c.set(x,Plan.SEA-1,z+1,s(Blocks.BLACK_WOOL));}
            }
        }
        // the promenade's line along the back of the quay, and lamps
        for(int x=Plan.HARBOR_X0+8;x<=Plan.HARBOR_X1;x+=22)t.street.sodium(x,Plan.PROM_Z0+3);
        // gear on the quay: nets, floats, crates
        for(int i=0;i<14;i++){
            int x=Plan.HARBOR_X0+8+rnd.nextInt(Plan.HARBOR_X1-Plan.HARBOR_X0-16),z=Plan.QUAY_Z1-1-rnd.nextInt(3);
            if(!c.air(x,Y+1,z))continue;
            switch(rnd.nextInt(4)){
                case 0->{c.set(x,Y+1,z,s(Blocks.BLACK_WOOL));if(c.air(x+1,Y+1,z))c.set(x+1,Y+1,z,s(Blocks.GREEN_CARPET));}
                case 1->{c.set(x,Y+1,z,s(Blocks.ORANGE_TERRACOTTA));if(c.air(x,Y+2,z))c.set(x,Y+2,z,s(Blocks.YELLOW_TERRACOTTA));}
                case 2->{c.set(x,Y+1,z,s(Blocks.BARREL));}
                default->{c.set(x,Y+1,z,s(Blocks.WHITE_CONCRETE));if(c.air(x,Y+2,z))c.set(x,Y+2,z,s(Blocks.WHITE_CONCRETE));}
            }
        }
    }

    /** Breakwaters: a concrete wall five wide, its parapet to seaward and tetrapods heaped outside. */
    private void breakwaters(){
        // the east breakwater: south from the quay's east end, then west along the arm
        for(int z=Plan.SEAWARD_Z;z<=ARM_Z+2;z++)for(int x=EX-2;x<=EX+2;x++)wall(x,z,x==EX+2);
        for(int x=ARM_X;x<EX-2;x++)for(int z=ARM_Z-2;z<=ARM_Z+2;z++)wall(x,z,z==ARM_Z+2);
        // the west breakwater: from the headland's shore out east
        for(int x=Plan.X0+4;x<=WEST_X;x++)for(int z=WEST_Z-2;z<=WEST_Z+2;z++)if(t.terrain.kind[Canvas.idx(x,z)]==Terrain.SEA)wall(x,z,z==WEST_Z+2);
        var coast=t.coast;
        for(int z=Plan.QUAY_Z1+8;z<=ARM_Z+5;z++)for(int x=EX+3;x<=EX+5;x++)if(c.water(x,Plan.SEA,z)&&rnd.nextInt(3)==0)coast.tetrapod(x,c.ground(x,z)+1+rnd.nextInt(Math.max(1,Plan.SEA-c.ground(x,z))),z);
        for(int x=ARM_X;x<=EX+5;x++)for(int z=ARM_Z+3;z<=ARM_Z+5;z++)if(c.water(x,Plan.SEA,z)&&rnd.nextInt(3)==0)coast.tetrapod(x,c.ground(x,z)+1+rnd.nextInt(Math.max(1,Plan.SEA-c.ground(x,z))),z);
        for(int x=Plan.X0+4;x<=WEST_X;x++)for(int z=WEST_Z+3;z<=WEST_Z+5;z++)if(c.water(x,Plan.SEA,z)&&rnd.nextInt(3)==0)coast.tetrapod(x,c.ground(x,z)+1+rnd.nextInt(Math.max(1,Plan.SEA-c.ground(x,z))),z);
    }
    private void wall(int x,int z,boolean parapet){
        for(int y=Canvas.Y0;y<=Y;y++)c.set(x,y,z,Pal.seawall(x,y,z));
        c.set(x,Y,z,Pal.concrete(x,z));c.ground(x,z,Y);
        if(parapet){c.set(x,Y+1,z,s(Blocks.LIGHT_GRAY_CONCRETE));c.set(x,Y+2,z,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));}
    }

    /** 魚市場: a long open shed on posts under a low metal roof, boxes and ice on its floor, its name on the end. */
    private void market(int x0,int x1){
        int z0=Plan.SEAWARD_Z,z1=Plan.QUAY_Z1-3;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            c.set(x,Y,z,s(Blocks.SMOOTH_STONE));
            boolean post=(x-x0)%6==0&&(z==z0||z==z1);
            if(post)for(int y=Y+1;y<=Y+4;y++)c.set(x,y,z,s(Blocks.ANDESITE_WALL));
        }
        var f=new Frame(c,x0,z0,x1,z1,Direction.NORTH,Y);
        Kit.roof(f,0,0,f.W-1,f.D-1,5,Kit.GABLE,true,Kit.TEAL,s(Blocks.AIR),s(Blocks.AIR));
        for(int x=1;x<f.W-1;x+=6)f.set(x,4,f.D/2,Canvas.rod(Blocks.END_ROD,Direction.DOWN));
        // rows of white boxes and ice
        for(int x=x0+2;x<=x1-2;x++)for(int z=z0+2;z<=z1-2;z+=3){
            if(rnd.nextInt(3)==0)continue;
            c.set(x,Y+1,z,rnd.nextInt(4)==0?s(Blocks.PACKED_ICE):s(Blocks.WHITE_CONCRETE));
            if(rnd.nextInt(3)==0)c.set(x,Y+2,z,s(Blocks.WHITE_CONCRETE));
        }
        c.wallSign(x1+1,Y+4,(z0+z1)/2,Blocks.SPRUCE_WALL_SIGN,Direction.EAST,DyeColor.WHITE,false,"ぼたんど漁港","魚市場","FISH MARKET");
    }
    /** The harbour's buildings on their plots across the coastal road. */
    void building(Lot l){
        switch(l.zone){
            case "coop"->office(Frame.of(c,l));
            case "ice"->store(Frame.of(c,l));
            default->gearShed(Frame.of(c,l));
        }
        l.use=l.zone;
    }
    /** 漁協: the fishing co-op's two-storey office. */
    private void office(Frame f){
        int W=f.W,D=f.D,bx0=1,bx1=W-2,bz0=3,bz1=Math.min(D-1,bz0+8);
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){f.set(x,0,z,Pal.concrete(f.wx(x,z),f.wz(x,z)));f.clear(x,1,z,x,10,z);}
        Kit.shell(f,bx0,bz0,bx1,bz1,2,TB.STUCCO_WHITE,TB.CONCRETE,TB.TRIM_WHITE,s(Blocks.SMOOTH_STONE),TB.CONCRETE);
        int m=(bx0+bx1)/2;
        for(int s=0;s<2;s++)Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'f',s,TB.WIN,TB.WIN_FROSTED,m,true);
        f.door(m,1,bz0,Blocks.IRON_DOOR,Direction.NORTH,false);
        f.wallSign(m,3,bz0-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLUE,false,"ぼたんど漁協","Botand","Fishery Co-op");
        Kit.roof(f,bx0,bz0,bx1,bz1,8,Kit.FLAT,true,Kit.SLATE,TB.STUCCO_WHITE,s(Blocks.SMOOTH_STONE));
        Kit.roomLight(f,m,0,(bz0+bz1)/2,false);Kit.roomLight(f,m,4,(bz0+bz1)/2,false);
        t.street.vending(f.wx(bx1+1,bz0),f.wz(bx1+1,bz0),f.dir(Direction.SOUTH),2);
    }
    /** 製氷・冷蔵: a plain warehouse with a wide shutter. */
    private void store(Frame f){
        int W=f.W,D=f.D,bx0=1,bx1=W-2,bz0=3,bz1=Math.min(D-1,bz0+9);
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){f.set(x,0,z,Pal.concrete(f.wx(x,z),f.wz(x,z)));f.clear(x,1,z,x,12,z);}
        Kit.shell(f,bx0,bz0,bx1,bz1,2,s(Blocks.LIGHT_GRAY_CONCRETE),s(Blocks.LIGHT_GRAY_CONCRETE),s(Blocks.LIGHT_GRAY_CONCRETE),s(Blocks.SMOOTH_STONE),s(Blocks.SMOOTH_STONE));
        int m=(bx0+bx1)/2;
        for(int x=m-2;x<=m+2;x++)for(int y=1;y<=3;y++)f.set(x,y,bz0,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,true,false));
        f.wallSign(m,5,bz0-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLACK,false,"製氷・冷蔵","ICE");
        Kit.roof(f,bx0,bz0,bx1,bz1,8,Kit.GABLE,true,Kit.SLATE,s(Blocks.LIGHT_GRAY_CONCRETE),s(Blocks.SMOOTH_STONE));
    }
    /** 作業小屋: a fisherman's gear shed of corrugated iron, its front open, nets and floats piled in front. */
    private void gearShed(Frame f){
        int W=f.W,D=f.D,bx0=1,bx1=W-2,bz0=4,bz1=Math.min(D-1,bz0+6);
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){f.set(x,0,z,Pal.concrete(f.wx(x,z),f.wz(x,z)));f.clear(x,1,z,x,8,z);}
        Houses.perimeter(f,bx0,bz0,bx1,bz1,(x,z,corner)->{
            boolean front=z==bz0&&!corner&&x>bx0+1&&x<bx1-1;
            for(int y=1;y<=3;y++)f.set(x,y,z,front?s(Blocks.AIR):Pal.hash(f.wx(x,z),y,f.wz(x,z))<0.3?TB.RUSTY:TB.CORRUGATED);
        });
        Kit.roof(f,bx0,bz0,bx1,bz1,4,Kit.SHED,false,Kit.SHEET,TB.CORRUGATED,s(Blocks.AIR));
        for(int x=bx0+1;x<bx1;x++){
            if(rnd.nextInt(3)==0)continue;
            f.set(x,1,bz0-1,switch(rnd.nextInt(4)){case 0->s(Blocks.BLACK_WOOL);case 1->s(Blocks.ORANGE_TERRACOTTA);case 2->s(Blocks.BARREL);default->s(Blocks.WHITE_CONCRETE);});
        }
        f.hangingLantern((bx0+bx1)/2,3,(bz0+bz1)/2,false);
    }

    /**
     * A fishing boat moored with its bow toward {@code dir} (±x): white hull with a blue band and red bottom, a
     * wooden deck, the wheelhouse aft with a mast, its catch flags and lamp; a squid boat carries a line of big
     * lamps strung bow to stern.
     */
    void boat(int x0,int zc,int dir,int len,boolean squid){
        for(int i=0;i<len;i++){
            int x=x0+dir*i;
            int hw=i>=len-1?0:i>=len-3?1:2;
            for(int dz=-hw;dz<=hw;dz++){
                int z=zc+dz;boolean side=Math.abs(dz)==hw;
                if(Math.abs(dz)<=Math.max(0,hw-1))c.set(x,Plan.SEA-2,z,s(Blocks.RED_CONCRETE));
                c.set(x,Plan.SEA-1,z,side?s(Blocks.RED_CONCRETE):s(Blocks.SPRUCE_PLANKS));
                c.set(x,Plan.SEA,z,side?s(Blocks.WHITE_CONCRETE):s(Blocks.SPRUCE_PLANKS));
                c.set(x,Plan.SEA+1,z,side?s(Blocks.BLUE_CONCRETE):s(Blocks.SPRUCE_PLANKS));
                if(side||i==0)c.set(x,Plan.SEA+2,z,s(Blocks.WHITE_CONCRETE));
            }
        }
        // the wheelhouse
        int a=x0+dir*2,b=x0+dir*5;
        for(int x=Math.min(a,b);x<=Math.max(a,b);x++)for(int dz=-1;dz<=1;dz++){
            int z=zc+dz;boolean wall=dz!=0||x==a||x==b;
            for(int y=Plan.SEA+2;y<=Plan.SEA+4;y++)c.set(x,y,z,wall?(y==Plan.SEA+3&&(dz!=0||x==b)?s(Blocks.GLASS_PANE):s(Blocks.WHITE_CONCRETE)):s(Blocks.AIR));
            c.set(x,Plan.SEA+5,z,Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,false));
        }
        int mx=x0+dir;
        for(int y=Plan.SEA+2;y<=Plan.SEA+8;y++)c.set(mx,y,zc,s(Blocks.SPRUCE_FENCE));
        c.set(mx,Plan.SEA+9,zc,Canvas.rod(Blocks.END_ROD,Direction.UP));
        BlockState[] flags={s(Blocks.RED_WALL_BANNER),s(Blocks.YELLOW_WALL_BANNER),s(Blocks.LIGHT_BLUE_WALL_BANNER),s(Blocks.WHITE_WALL_BANNER)};
        c.set(mx-dir,Plan.SEA+7,zc,flags[rnd.nextInt(flags.length)].with(Properties.HORIZONTAL_FACING,dir>0?Direction.WEST:Direction.EAST));
        if(squid){
            int bow=x0+dir*(len-2);
            c.set(bow,Plan.SEA+2,zc,s(Blocks.SPRUCE_FENCE));for(int y=Plan.SEA+3;y<=Plan.SEA+6;y++)c.set(bow,y,zc,s(Blocks.SPRUCE_FENCE));
            for(int x=Math.min(mx,bow);x<=Math.max(mx,bow);x++){
                c.set(x,Plan.SEA+7,zc,Canvas.chain(Direction.Axis.X));
                if(Math.floorMod(x-mx,2)==1&&x!=mx&&x!=bow)c.set(x,Plan.SEA+6,zc,s(Blocks.GLOWSTONE));
            }
        }
    }

    private void slipway(){
        for(int x=Plan.HARBOR_X0;x<=Plan.HARBOR_X0+5;x++)for(int k=0;k<10;k++){
            int z=Plan.QUAY_Z1+1+k,y=Y-k/2;
            for(int yy=c.ground(x,z)+1;yy<y;yy++)c.set(x,yy,z,s(Blocks.STONE));
            c.set(x,y,z,(k&1)==0?s(Blocks.SMOOTH_STONE):Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
        }
    }

    /** A harbour light: a slender round tower, a gallery with a rail, the lantern under its cap. */
    private void lighthouse(int x,int z,boolean red){
        BlockState body=red?s(Blocks.RED_CONCRETE):s(Blocks.WHITE_CONCRETE);
        int top=Y+12;
        for(int y=Y+1;y<=top;y++){
            c.set(x,y,z,body);
            for(Direction d:Direction.Type.HORIZONTAL)c.set(x+d.getOffsetX(),y,z+d.getOffsetZ(),y==Y+4||y==Y+8?s(Blocks.WHITE_CONCRETE):body);
        }
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            if(Math.abs(dx)==2&&Math.abs(dz)==2)continue;
            c.set(x+dx,top+1,z+dz,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
            if(Math.abs(dx)==2||Math.abs(dz)==2)c.set(x+dx,top+2,z+dz,s(Blocks.IRON_BARS));
        }
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dz==0){c.set(x,top+2,z,red?s(Blocks.SHROOMLIGHT):s(Blocks.SEA_LANTERN));continue;}
            c.set(x+dx,top+2,z+dz,red?s(Blocks.RED_STAINED_GLASS_PANE):s(Blocks.GLASS_PANE));
            c.set(x+dx,top+3,z+dz,Canvas.slab(red?Blocks.RED_NETHER_BRICK_SLAB:Blocks.SMOOTH_QUARTZ_SLAB,false));
        }
        c.set(x,top+3,z,red?s(Blocks.RED_NETHER_BRICKS):s(Blocks.SMOOTH_QUARTZ));
        c.set(x,top+4,z,Canvas.rod(Blocks.LIGHTNING_ROD,Direction.UP));
    }
}
