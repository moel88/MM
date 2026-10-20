package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * ぼたんど大橋, Botand's landmark: a cable-stayed bridge carrying the main street's line on from the coast across the
 * sea to the island. Its deck arches gently over the water; a single white A-shaped pylon straddles it, the two legs
 * rising to meet in a crown high above the town, fans of cables running down from them to the deck's edges. Botand's
 * sky blue runs along both edges of the deck, up the legs and in the crown; at night those lines glow blue, blue
 * lanterns hang along the cables and white lamps light the road. On the island the bridge lands on a little square,
 * from which a path climbs under a red torii to a small shrine and on to a lighthouse.
 */
final class Bridge {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int Z0=Plan.BRIDGE_Z0,Z1=Plan.BRIDGE_Z1,PZ=Plan.PYLON_Z;
    static final int APEX=124,BASE=56,SPREAD=12;
    int cables,lights;
    Bridge(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    /**
     * The deck's surface at z, in half blocks: level with the coast, rising ten blocks to its crest over the pylon and
     * down to the island's square. Like a real deck's vertical curve it leaves each end gently and is steepest midway
     * up, so from the street the approach does not rise like a flight of steps.
     */
    static int deckHalf(int z){
        double u=Math.max(0,Math.min(1,(z-Z0)/(double)(Z1-Z0)));
        double y=64+2*u+10.5*(1-Math.cos(2*Math.PI*u))/2;
        return (int)Math.round(2*y);
    }
    static int deckY(int z){return deckHalf(z)>>1;}
    /** The centre of a pylon leg at height y (its |x|). */
    static double legX(int y){return SPREAD*(1-(y-BASE)/(double)(APEX-BASE));}

    void build(){
        deck();
        piers();
        pylon();
        fans();
        entrance();
        island();
        t.places.put("bridge",new int[]{0,(deckHalf(PZ)+3)>>1,PZ}); // standing on the deck, half a block up where a slab tops it
        Town.log("bridge: "+cables+" cable blocks, "+lights+" lights");
    }

    private void deck(){
        for(int z=Z0;z<=Z1;z++){
            int lv=deckHalf(z),y=lv>>1;boolean half=(lv&1)==1;
            // sidewalks stand half a block above the road
            int lvS=lv+1,yS=lvS>>1;boolean halfS=(lvS&1)==1;
            for(int x=-7;x<=7;x++){
                int ax=Math.abs(x);
                c.clear(x,y+1,z,x,y+9,z);
                int girder=ax<=4?3:2;
                for(int k=1;k<=girder;k++)if(c.open(x,y-k,z)||z<Plan.SEAWARD_Z)c.set(x,y-k,z,s(Blocks.LIGHT_GRAY_CONCRETE));
                if(ax==7){
                    // the fascia: white, Botand's blue line along it lit from within, a railing on top
                    for(int yy=y-2;yy<=yS;yy++)c.set(x,yy,z,yy==y-1?s(Blocks.LIGHT_BLUE_STAINED_GLASS):s(Blocks.WHITE_CONCRETE));
                    c.set(x,yS+1,z,s(Blocks.IRON_BARS));if(halfS)c.set(x,yS+2,z,s(Blocks.IRON_BARS));
                    // a string of blue lanterns along the railing: the bridge's line of light at night
                    if(Math.floorMod(z-Z0,4)==2&&Math.floorMod(z-Z0,16)!=8){c.set(x,yS+(halfS?3:2),z,s(Blocks.SOUL_LANTERN));lights++;}
                    c.ground(x,z,yS);
                    continue;
                }
                if(ax>=4){
                    // the blue line's light, behind two panes of glass so that its blue is deep enough to read at night
                    if(ax==6)c.set(x,y-1,z,s(Blocks.LIGHT_BLUE_STAINED_GLASS));
                    if(ax==5){c.set(x,y-1,z,s(Blocks.SEA_LANTERN));lights++;}
                    if(halfS){c.set(x,yS,z,s(Blocks.STONE));c.set(x,yS+1,z,Pal.pavers(x,z));}else c.set(x,yS,z,Pal.paverBlock(x,z));
                    if(yS>y)c.set(x,y,z,s(Blocks.STONE));
                    c.ground(x,z,halfS?yS+1:yS);
                    continue;
                }
                BlockState full,slab;
                if(x==0&&Math.floorMod(z,8)<4){full=s(Blocks.WHITE_CONCRETE);slab=Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,false);}
                // the deck's newer, darker asphalt is one material in whole and half blocks, so the arch reads as one smooth rise
                else{full=Pal.ramp(false);slab=Pal.ramp(true);}
                if(half){c.set(x,y,z,Pal.asphaltBase());c.set(x,y+1,z,slab);}else c.set(x,y,z,full);
                c.ground(x,z,half?y+1:y);
            }
            // white lamps on both edges every sixteen blocks
            if(Math.floorMod(z-Z0,16)==8&&z>Z0+4&&Math.abs(z-PZ)>4)for(int side=-1;side<=1;side+=2)lamp(side*7,yS+(halfS?3:2),z,-side);
        }
    }
    private void lamp(int x,int base,int z,int inward){
        for(int y=base;y<=base+5;y++)c.set(x,y,z,s(Blocks.DIORITE_WALL));
        c.set(x+inward,base+5,z,Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,true));c.set(x+inward,base+4,z,s(Blocks.SEA_LANTERN));lights++;
    }

    /** Concrete piers under the deck, and the great footing of the pylon. */
    private void piers(){
        for(int z:new int[]{68,84,100,162,178,194}){
            int y=deckY(z)-3;
            for(int x:new int[]{-4,-3,3,4})for(int zz=z;zz<=z+1;zz++)for(int yy=c.ground(x,zz)+1;yy<=y;yy++)c.set(x,yy,zz,s(Blocks.LIGHT_GRAY_CONCRETE));
            for(int x=-5;x<=5;x++)for(int zz=z;zz<=z+1;zz++)c.set(x,y,zz,s(Blocks.LIGHT_GRAY_CONCRETE));
        }
        for(int x=-16;x<=16;x++)for(int z=PZ-5;z<=PZ+5;z++){
            if(Math.abs(x)==16&&Math.abs(z-PZ)==5)continue;
            for(int y=c.ground(x,z)+1;y<=Plan.SEA+1;y++)c.set(x,y,z,y>=Plan.SEA?s(Blocks.SMOOTH_STONE):s(Blocks.LIGHT_GRAY_CONCRETE));
        }
    }

    /** The pylon: two legs leaning in from the footing to meet high above the deck, a crown of light on top. */
    private void pylon(){
        int deck=deckY(PZ);
        for(int y=Plan.SEA+2;y<=APEX;y++){
            double lx=legX(y);
            int depth=y<deck?2:1;
            for(int side=-1;side<=1;side+=2){
                int xc=(int)Math.round(side*lx);
                for(int x=xc-1;x<=xc+1;x++)for(int z=PZ-depth;z<=PZ+depth;z++){
                    boolean outer=x==xc+side||(side>0?x==xc+1:x==xc-1);
                    boolean face=z==PZ-depth||z==PZ+depth;
                    BlockState b=s(Blocks.WHITE_CONCRETE);
                    // Botand's blue lines up the outer face and up the faces towards the town and the island, lit from inside
                    if(outer&&z==PZ&&y>deck||x==xc&&face&&depth==1&&y>deck)b=s(Blocks.LIGHT_BLUE_STAINED_GLASS);
                    else if(x==xc&&z==PZ&&y>deck)b=s(Blocks.SEA_LANTERN);
                    else if(face&&y%9==0)b=s(Blocks.LIGHT_BLUE_CONCRETE);
                    if(Math.abs(x)<=6&&y>=deck-3&&y<=deck+6)continue;
                    c.set(x,y,z,b);
                }
            }
        }
        // the cross beam under the deck that carries it at the pylon
        for(int x=-10;x<=10;x++)for(int z=PZ-2;z<=PZ+2;z++)for(int y=deck-5;y<=deck-3;y++)c.set(x,y,z,s(Blocks.LIGHT_GRAY_CONCRETE));
        // the crown: a tapering spire from the apex with a lantern of blue glass
        for(int y=APEX;y<=APEX+8;y++){
            int r=y<APEX+3?1:0;
            for(int x=-r;x<=r;x++)for(int z=PZ-r;z<=PZ+r;z++)c.set(x,y,z,s(Blocks.WHITE_CONCRETE));
        }
        for(int y=APEX+3;y<=APEX+6;y++)for(Direction d:Direction.Type.HORIZONTAL)c.set(d.getOffsetX(),y,PZ+d.getOffsetZ(),s(Blocks.LIGHT_BLUE_STAINED_GLASS));
        for(int y=APEX+3;y<=APEX+6;y++){c.set(0,y,PZ,s(Blocks.SEA_LANTERN));lights++;}
        c.set(0,APEX+9,PZ,s(Blocks.WHITE_CONCRETE));c.set(0,APEX+10,PZ,Canvas.rod(Blocks.LIGHTNING_ROD,Direction.UP));
        c.set(0,APEX+11,PZ,s(Blocks.RED_STAINED_GLASS));
    }

    /** The cables: on each side a fan from the upper legs down to the deck's edge, both ways along the bridge. */
    private void fans(){
        int deck=deckY(PZ);
        int n=9;
        for(int side=-1;side<=1;side+=2)for(int way=-1;way<=1;way+=2)for(int i=0;i<n;i++){
            int ay=APEX-4-i*2;
            double ax=side*legX(ay);
            int dz=10+i*7;
            int z=PZ+way*dz;
            int dy=deckY(z)+2;
            cable(ax,ay,PZ+way*1.5,side*6.0,dy,z);
        }
    }
    private void cable(double x0,double y0,double z0,double x1,double y1,double z1){
        double len=Math.sqrt((x1-x0)*(x1-x0)+(y1-y0)*(y1-y0)+(z1-z0)*(z1-z0));
        int steps=(int)Math.ceil(len*2.2);
        int lx=Integer.MIN_VALUE,ly=0,lz=0,count=0;
        Direction.Axis axis=Math.abs(y1-y0)>Math.abs(z1-z0)?Direction.Axis.Y:Direction.Axis.Z;
        for(int i=0;i<=steps;i++){
            double s=i/(double)steps;
            int bx=(int)Math.round(x0+(x1-x0)*s),by=(int)Math.round(y0+(y1-y0)*s),bz=(int)Math.round(z0+(z1-z0)*s);
            if(bx==lx&&by==ly&&bz==lz)continue;lx=bx;ly=by;lz=bz;
            if(!c.air(bx,by,bz))continue;
            c.set(bx,by,bz,Canvas.chain(axis));cables++;
            // a blue lantern hangs under the cable every so often
            if(++count%7==3&&c.air(bx,by-1,bz)&&by-1>deckY(bz)+2){c.set(bx,by-1,bz,s(Blocks.SOUL_LANTERN).with(Properties.HANGING,true));lights++;}
        }
    }

    /** The north end: stone posts with lanterns and the bridge's name in three languages. */
    private void entrance(){
        for(int side=-1;side<=1;side+=2){
            int x=side*8,z=Z0;
            int g=c.ground(x,z);
            for(int y=g+1;y<=g+3;y++)c.set(x,y,z,s(Blocks.POLISHED_ANDESITE));
            c.set(x,g+4,z,s(Blocks.LANTERN));
            Direction out=Direction.NORTH;
            if(side<0)c.wallSign(x,g+2,z-1,Blocks.DARK_OAK_WALL_SIGN,out,DyeColor.WHITE,false,"","ぼたんど大橋","");
            else c.wallSign(x,g+2,z-1,Blocks.DARK_OAK_WALL_SIGN,out,DyeColor.WHITE,false,"BOTAND","OHASHI","보탠드 대교");
        }
    }

    /** The island: its square, a red torii and a path up to a small shrine, the lighthouse beyond, benches. */
    private void island(){
        int y=66;
        for(int x=-14;x<=14;x++)for(int z=Z1;z<=Z1+12;z++){
            c.set(x,y,z,Math.abs(x)<=2||z==Z1+6?s(Blocks.STONE_BRICKS):Pal.paverBlock(x,z));
            c.clear(x,y+1,z,x,y+8,z);c.ground(x,z,y);
        }
        for(int x=-12;x<=12;x+=6)if(x!=0){c.set(x,y+1,Z1+2,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.SOUTH,false));}
        c.wallSign(0,y+1,Z1+11,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLUE,false,"ぼたんど島","BOTAND ISLAND","보탠드섬");
        c.set(0,y+1,Z1+12,s(Blocks.STONE_BRICKS));c.set(0,y+2,Z1+12,s(Blocks.LANTERN));
        t.places.put("island",new int[]{0,y+1,Z1+6});
        // the torii at the square's far side, then stone steps climbing to the top
        torii(0,y,Z1+14);
        int topZ=Plan.ISLAND_Z,peak=c.ground(0,topZ);
        for(int z=Z1+13;z<=topZ;z++){
            int g=Math.max(y,Math.min(peak,y+(z-Z1-13)/2));
            for(int x=-1;x<=1;x++){
                for(int yy=c.ground(x,z)+1;yy<g;yy++)c.set(x,yy,z,s(Blocks.STONE));
                c.set(x,g,z,s(Blocks.STONE_BRICKS));c.clear(x,g+1,z,x,g+6,z);c.ground(x,z,g);
            }
        }
        shrine(0,c.ground(0,topZ),topZ+2);
        lighthouse(6,Plan.ISLAND_Z+Plan.ISLAND_RZ-8);
    }
    /** A red torii facing north. */
    void torii(int x,int g,int z){
        for(int side=-1;side<=1;side+=2)for(int y=g+1;y<=g+5;y++)c.set(x+side*2,y,z,s(Blocks.RED_CONCRETE));
        for(int dx=-2;dx<=2;dx++)c.set(x+dx,g+4,z,s(Blocks.RED_CONCRETE));
        for(int dx=-3;dx<=3;dx++)c.set(x+dx,g+6,z,s(Blocks.BLACK_CONCRETE));
        c.set(x-3,g+6,z,Canvas.stairs(Blocks.POLISHED_BLACKSTONE_STAIRS,Direction.EAST,true));c.set(x+3,g+6,z,Canvas.stairs(Blocks.POLISHED_BLACKSTONE_STAIRS,Direction.WEST,true));
        c.set(x,g+5,z,s(Blocks.RED_CONCRETE));
    }
    private void shrine(int x,int g,int z){
        var f=new Frame(c,x-2,z,x+2,z+3,Direction.NORTH,g);
        f.fill(0,0,0,4,0,3,s(Blocks.STONE_BRICKS));
        Kit.shell(f,0,0,4,3,1,s(Blocks.SPRUCE_PLANKS),Canvas.axis(Blocks.STRIPPED_SPRUCE_LOG,Direction.Axis.Y),s(Blocks.STRIPPED_DARK_OAK_WOOD),s(Blocks.SPRUCE_PLANKS),s(Blocks.STONE_BRICKS));
        f.set(2,1,0,s(Blocks.AIR));f.set(2,2,0,s(Blocks.AIR));
        f.set(1,1,-1,s(Blocks.STONE_BRICK_WALL));f.set(1,2,-1,s(Blocks.LANTERN));f.set(3,1,-1,s(Blocks.STONE_BRICK_WALL));f.set(3,2,-1,s(Blocks.LANTERN));
        Kit.roof(f,0,0,4,3,4,Kit.GABLE,false,Kit.COPPER,s(Blocks.SPRUCE_PLANKS),s(Blocks.SPRUCE_PLANKS));
    }
    private void lighthouse(int x,int z){
        int g=c.ground(x,z),top=g+16;
        for(int y=g+1;y<=top;y++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(Math.abs(dx)==1&&Math.abs(dz)==1&&y>g+2)continue;
            c.set(x+dx,y,z+dz,s(Blocks.WHITE_CONCRETE));
        }
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            if(Math.abs(dx)==2&&Math.abs(dz)==2)continue;
            c.set(x+dx,top+1,z+dz,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
            if(Math.abs(dx)==2||Math.abs(dz)==2)c.set(x+dx,top+2,z+dz,s(Blocks.IRON_BARS));
        }
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dz==0){c.set(x,top+2,z,s(Blocks.SEA_LANTERN));c.set(x,top+3,z,s(Blocks.SEA_LANTERN));continue;}
            c.set(x+dx,top+2,z+dz,s(Blocks.GLASS_PANE));c.set(x+dx,top+3,z+dz,s(Blocks.GLASS_PANE));
            c.set(x+dx,top+4,z+dz,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));
        }
        c.set(x,top+4,z,s(Blocks.POLISHED_BLACKSTONE));c.set(x,top+5,z,Canvas.rod(Blocks.LIGHTNING_ROD,Direction.UP));
        lights++;
    }
}
