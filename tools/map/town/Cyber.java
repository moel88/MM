package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * The cyber city in the lower glass's east half (map v3.0, the user's request and photo: '기존 보탠드 맵 구역을 사이버펑크
 * 구역으로'): a grid of avenues twenty blocks across, wet asphalt and dark paving, and between them city blocks of towers
 * — glass curtain walls dark and lit floor by floor, dark metal bands, a neon tube up every corner and round the
 * setbacks and crowns, LED walls playing on their faces, bright glass shop fronts at their feet — tallest toward the
 * city's middle; here and there a block left open as a plaza with palms and an LED kiosk. Black lamp posts with lights
 * over the avenues, cars along the kerbs, bus stops with lit posters, and tangles of cables strung across the narrow
 * gaps between towers. No arena (the user's choice); the day and night as everywhere else.
 */
final class Cyber {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int CODE=3;
    static final BlockState[] NEON={TB.of("neon_pink"),TB.of("neon_purple"),TB.of("neon_cyan")};
    static final BlockState[] LED={TB.of("led_screen_pink"),TB.of("led_screen_cyan"),TB.of("led_screen_mix")};
    static final BlockState GLASS=TB.of("glass_curtain"),LIT=TB.of("glass_curtain_lit"),PANEL=TB.of("cyber_panel"),PAVING=TB.of("cyber_paving");
    /** Where the avenues run (as Roads lays them): north-south at x X0+k*STEP, east-west at z Z0+k*STEP. */
    static final int STEP=104,AX0=56,AZ0=Plan.RIFT+60;
    /** The city's middle, where the towers stand tallest. */
    static final int MID_X=470,MID_Z=600;
    int towers,plazas,cars,lamps;
    Cyber(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}
    private static BlockState s(net.minecraft.block.Block b){return b.getDefaultState();}

    void build(){
        int y=Plan.BASE;
        List<int[]> blocks=new ArrayList<>();
        int half=Plan.AVENUE_HALF;
        // the blocks between the avenues (and the strip between the axis road and the first avenue)
        List<Integer> xs=new ArrayList<>(List.of(Plan.ROAD_HALF-Plan.AVENUE_HALF));
        for(int k=0;AX0+k*STEP<=Plan.WIDE;k++)xs.add(AX0+k*STEP);
        List<Integer> zs=new ArrayList<>();
        for(int k=-1;AZ0+k*STEP<=Plan.END;k++)zs.add(AZ0+k*STEP);
        for(int i=0;i+1<xs.size();i++)for(int j=0;j+1<zs.size();j++){
            int x0=xs.get(i)+half+2,x1=xs.get(i+1)-half-2,z0=zs.get(j)+half+2,z1=zs.get(j+1)-half-2;
            blocks.add(new int[]{x0,z0,x1,z1});
        }
        for(int[] b:blocks)cityBlock(b[0],b[1],b[2],b[3],y);
        for(var s:t.roads.streets(Plan.CYBER))furnish(s);
        t.places.put("cyber",new int[]{MID_X,c.ground(MID_X,MID_Z)+1,MID_Z});
        Town.log("cyber: "+towers+" towers, "+plazas+" plazas, "+cars+" cars, "+lamps+" lamps, "+c.placed);
    }

    /** Whether a rectangle is the city's own open land (its zone, dry, off the shore, the rift and the roads). */
    private boolean land(int x0,int z0,int x1,int z1,int margin){
        for(int x=x0;x<=x1;x+=2)for(int z=z0;z<=z1;z+=2){
            if(!Canvas.inPlan(x,z)||Plan.zone(x,z)!=Plan.CYBER||Plan.shore(x,z)>-margin)return false;
            if(Math.hypot(x,z)<Plan.riftEdge(x,z)+12)return false;
            int i=Canvas.idx(x,z);
            if(t.terrain.kind[i]!=Terrain.LAND||t.terrain.pale[i]||t.roads.on(x,z)||c.owner[i]!=0)return false;
        }
        return true;
    }

    /**
     * A city block: paved in dark tiles to its edges, then built up in one of a few ways (one great tower, four towers
     * with gaps between, two long slabs, or an open plaza), each tower only where its ground is the city's.
     */
    private void cityBlock(int x0,int z0,int x1,int z1,int y){
        // pave what of it is the city's
        int paved=0;
        for(int x=x0-1;x<=x1+1;x++)for(int z=z0-1;z<=z1+1;z++){
            if(!Canvas.inPlan(x,z)||Plan.zone(x,z)!=Plan.CYBER||t.roads.on(x,z))continue;
            int i=Canvas.idx(x,z);
            if(t.terrain.kind[i]!=Terrain.LAND||t.terrain.pale[i]||Plan.shore(x,z)>-3)continue;
            t.lots.setGround(x,z,y);c.set(x,y,z,PAVING);c.clear(x,y+1,z,x,y+3,z);paved++;
        }
        if(paved<200)return;
        int w=x1-x0+1,d=z1-z0+1;
        double dm=Math.hypot((x0+x1)/2.0-MID_X,(z0+z1)/2.0-MID_Z);
        double tall=Math.exp(-(dm/300)*(dm/300));
        int pick=rnd.nextInt(10);
        if(w<50||d<50){
            // a narrow strip: slim towers along it
            int n=Math.max(1,(Math.max(w,d)-6)/28);
            for(int k=0;k<n;k++){
                int a0,a1,b0,b1;
                if(w<d){a0=x0+3;a1=x1-3;b0=z0+3+k*(d-6)/n;b1=z0+3+(k+1)*(d-6)/n-6;}
                else{b0=z0+3;b1=z1-3;a0=x0+3+k*(w-6)/n;a1=x0+3+(k+1)*(w-6)/n-6;}
                tryTower(a0,b0,a1,b1,y,height(tall));
            }
            return;
        }
        if(pick==0&&dm>120){plaza(x0,z0,x1,z1,y);return;}
        if(pick<=3){
            // one great tower in the middle of its plaza
            tryTower(x0+12,z0+12,x1-12,z1-12,y,(int)(height(tall)*1.25));
        }else if(pick<=7){
            // four towers, alleys between them
            int mx=(x0+x1)/2,mz=(z0+z1)/2;
            int[][] q={{x0+4,z0+4,mx-6,mz-6},{mx+6,z0+4,x1-4,mz-6},{x0+4,mz+6,mx-6,z1-4},{mx+6,mz+6,x1-4,z1-4}};
            List<int[]> built=new ArrayList<>();
            for(int[] r:q)if(tryTower(r[0],r[1],r[2],r[3],y,height(tall)))built.add(r);
            // cables strung across the alleys between them
            for(int i=0;i<built.size();i++)for(int j=i+1;j<built.size();j++)cables(built.get(i),built.get(j),y);
        }else{
            // two long slabs
            int mz=(z0+z1)/2;
            tryTower(x0+4,z0+4,x1-4,mz-5,y,height(tall));
            tryTower(x0+4,mz+5,x1-4,z1-4,y,height(tall));
        }
    }
    private int height(double tall){return (int)Math.round(34+150*tall*(0.55+0.45*rnd.nextDouble())+rnd.nextInt(16));}

    private boolean tryTower(int x0,int z0,int x1,int z1,int y,int h){
        if(x1-x0<12||z1-z0<12||!land(x0,z0,x1,z1,6))return false;
        // the tower must stay below the world's ceiling
        h=Math.min(h,Canvas.YMAX-y-12);
        tower(x0,z0,x1,z1,y,h);
        c.claim(x0-1,z0-1,x1+1,z1+1,CODE);
        towers++;
        return true;
    }

    /**
     * A tower from x0..x1, z0..z1 standing on y, h tall: a shop floor of bright glass with a neon band over it, then
     * storeys of curtain wall four blocks high between thin metal bands (each storey lit or dark, some panes lit on their
     * own), metal fins every few blocks, a neon tube up every corner; on tall towers a setback with a neon ring, and a
     * crown with a ring of neon and a mast; an LED wall on its face toward the widest view.
     */
    private void tower(int x0,int z0,int x1,int z1,int y,int h){
        BlockState neon=NEON[rnd.nextInt(NEON.length)];
        double litRate=0.15+0.45*rnd.nextDouble();
        int fin=4+rnd.nextInt(4);
        int setback=h>70?(int)(h*(0.55+0.15*rnd.nextDouble())):h+1,inset=h>70?3+rnd.nextInt(3):0;
        int top=y+h;
        boolean[] storeyLit=new boolean[h/4+2];
        for(int k=0;k<storeyLit.length;k++)storeyLit[k]=rnd.nextDouble()<litRate*0.5;
        for(int yy=y+1;yy<=top;yy++){
            int in=yy-y>setback?inset:0;
            int ax=x0+in,bx=x1-in,az=z0+in,bz=z1-in;
            int up=yy-y;
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++){
                boolean edgeX=x==ax||x==bx,edgeZ=z==az||z==bz;
                if(!edgeX&&!edgeZ){
                    // inside: a shop's floor and ceiling at the foot, a floor at the setback, else hollow
                    if(up==6||up==setback+1&&in>0)c.set(x,yy,z,PANEL);
                    else c.set(x,yy,z,s(Blocks.AIR));
                    continue;
                }
                boolean corner=edgeX&&edgeZ;
                BlockState b;
                if(corner)b=neon;
                else if(up<=5)b=up==5?PANEL:up==1?PANEL:shopFront(x,yy,z);
                else if(up==6)b=neon;
                else if(in>0&&up==setback+1||yy==top)b=neon;
                else{
                    int along=edgeX?z-az:x-ax;
                    int storey=(up-7)/4;
                    if((up-7)%4==3)b=PANEL;
                    else if(along%fin==0)b=PANEL;
                    else b=storeyLit[Math.min(storey,storeyLit.length-1)]||Pal.hash(x,yy,z)<litRate*0.35?LIT:GLASS;
                }
                c.set(x,yy,z,b);
            }
            // the setback's ledge
            if(in>0&&up==setback+1)for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(x<x0+inset||x>x1-inset||z<z0+inset||z>z1-inset)c.set(x,yy-1,z,PANEL);
        }
        // the roof and its crown: a deck of metal, a low parapet of neon, a mast on the tallest
        int in=h>setback?inset:0;
        for(int x=x0+in;x<=x1-in;x++)for(int z=z0+in;z<=z1-in;z++)c.set(x,top,z,x==x0+in||x==x1-in||z==z0+in||z==z1-in?neon:PANEL);
        int mx=(x0+x1)/2,mz=(z0+z1)/2;
        if(h>90){
            int mh=10+rnd.nextInt(16);
            for(int k=1;k<=mh;k++)c.set(mx,top+k,mz,s(Blocks.IRON_BARS));
            c.set(mx,top+mh+1,mz,NEON[0]);
            for(int k=1;k<=3;k++)c.set(mx,top+k,mz,PANEL);
        }else if(rnd.nextBoolean()){
            // rooftop plant: boxes of metal
            for(int k=0;k<3;k++){int bx=x0+in+2+rnd.nextInt(Math.max(1,x1-x0-2*in-6)),bz=z0+in+2+rnd.nextInt(Math.max(1,z1-z0-2*in-6));
                for(int dx=0;dx<3;dx++)for(int dz=0;dz<2;dz++)c.set(bx+dx,top+1,bz+dz,PANEL);}
        }
        // an LED wall on one face, toward the avenue on its widest side
        if(h>30&&rnd.nextDouble()<0.75)screen(x0,z0,x1,z1,y,Math.min(h,setback),rnd.nextInt(4));
    }
    /** A shop front at a tower's foot: bright panes with the shop's light behind, a door every so often. */
    private BlockState shopFront(int x,int y,int z){
        double h=Pal.hash(x,y/3,z);
        return h<0.12?PANEL:h<0.75?s(Blocks.LIGHT_GRAY_STAINED_GLASS):LIT;
    }
    /** An LED wall on a tower's face (0 north, 1 east, 2 south, 3 west), a block out from the wall, high up its shaft. */
    private void screen(int x0,int z0,int x1,int z1,int y,int shaft,int face){
        BlockState led=LED[rnd.nextInt(LED.length)];
        int len=(face%2==0?x1-x0:z1-z0)-6;
        if(len<8)return;
        int sw=Math.min(len,8+rnd.nextInt(Math.max(1,len-7))),sh=Math.min(shaft-14,10+rnd.nextInt(16));
        if(sh<6)return;
        int base=y+8+rnd.nextInt(Math.max(1,shaft-sh-10));
        int a=(face%2==0?x0:z0)+3+rnd.nextInt(Math.max(1,len-sw+1));
        for(int k=0;k<sw;k++)for(int j=0;j<sh;j++){
            int yy=base+j;
            switch(face){
                case 0->c.set(a+k,yy,z0-1,led);
                case 2->c.set(a+k,yy,z1+1,led);
                case 1->c.set(x1+1,yy,a+k,led);
                default->c.set(x0-1,yy,a+k,led);
            }
        }
    }

    /** An open plaza: dark paving, palms in a ring, an LED kiosk in its middle, benches. */
    private void plaza(int x0,int z0,int x1,int z1,int y){
        if(!land(x0+4,z0+4,x1-4,z1-4,6))return;
        int cx=(x0+x1)/2,cz=(z0+z1)/2;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            if(Math.abs(Math.hypot(x-cx,z-cz)-14)<0.6)c.set(x,y,z,NEON[2]);
        }
        // the kiosk: a column of LED walls under a neon cap
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int k=1;k<=9;k++){
            boolean edge=Math.abs(dx)==2||Math.abs(dz)==2;
            c.set(cx+dx,y+k,cz+dz,edge?LED[(k/3)%LED.length]:PANEL);
        }
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)c.set(cx+dx,y+10,cz+dz,Math.abs(dx)==3||Math.abs(dz)==3?NEON[0]:PANEL);
        for(int k=0;k<10;k++){
            double a=k*2*Math.PI/10;
            int px=cx+(int)Math.round(Math.cos(a)*24),pz=cz+(int)Math.round(Math.sin(a)*24);
            if(px>x0+2&&px<x1-2&&pz>z0+2&&pz<z1-2)palm(px,y,pz);
        }
        c.claim(x0,z0,x1,z1,CODE);
        plazas++;
    }
    /** A palm: a leaning trunk of jungle wood, a crown of fronds drooping all round, a pink light at its foot. */
    void palm(int x,int y,int z){
        int h=8+rnd.nextInt(5);double lean=rnd.nextDouble()*2*Math.PI;
        double tx=x+0.5,tz=z+0.5;
        for(int k=1;k<=h;k++){
            double f=k/(double)h;
            int bx=(int)Math.floor(tx+Math.cos(lean)*f*f*2),bz=(int)Math.floor(tz+Math.sin(lean)*f*f*2);
            c.set(bx,y+k,bz,Canvas.axis(Blocks.JUNGLE_LOG,Direction.Axis.Y));
            if(k==h){
                for(int a=0;a<8;a++){
                    double ang=a*Math.PI/4+rnd.nextDouble()*0.3;
                    for(int r=1;r<=5;r++){
                        int fx=(int)Math.floor(bx+0.5+Math.cos(ang)*r),fz=(int)Math.floor(bz+0.5+Math.sin(ang)*r);
                        int fy=y+k+1-(r*r)/9;
                        if(c.air(fx,fy,fz))c.set(fx,fy,fz,Canvas.leaves(Blocks.JUNGLE_LEAVES));
                    }
                }
                c.set(bx,y+k+1,bz,Canvas.leaves(Blocks.JUNGLE_LEAVES));
            }
        }
        if(c.get(x,y,z).isOf(PAVING.getBlock()))c.set(x,y,z,NEON[0]);
    }

    /** Cables strung across the gap between two towers, sagging, at a few heights over the alley. */
    private void cables(int[] a,int[] b,int y){
        boolean alongX=a[2]<b[0]||b[2]<a[0];
        if(!alongX&&!(a[3]<b[1]||b[3]<a[1]))return;
        int n=4+rnd.nextInt(6);
        for(int k=0;k<n;k++){
            double h=y+6+rnd.nextDouble()*14;
            if(alongX){
                int[] l=a[2]<b[0]?a:b,r=l==a?b:a;
                double z=Math.max(l[1],r[1])+rnd.nextDouble()*Math.max(1,Math.min(l[3],r[3])-Math.max(l[1],r[1]));
                t.wire(l[2]+1.05,h,z,r[0]-0.05,h+rnd.nextDouble()*3-1.5,z+rnd.nextDouble()*4-2,0.4+rnd.nextDouble()*1.6);
            }else{
                int[] n0=a[3]<b[1]?a:b,s0=n0==a?b:a;
                double x=Math.max(n0[0],s0[0])+rnd.nextDouble()*Math.max(1,Math.min(n0[2],s0[2])-Math.max(n0[0],s0[0]));
                t.wire(x,h,n0[3]+1.05,x+rnd.nextDouble()*4-2,h+rnd.nextDouble()*3-1.5,s0[1]-0.05,0.4+rnd.nextDouble()*1.6);
            }
        }
    }

    /**
     * An avenue's furniture: black lamp posts on both kerbs every thirty blocks with their lights over the road, cars
     * parked along the kerb, a bus stop now and then with a lit poster.
     */
    private void furnish(double[][] line){
        int half=t.roads.half(line);
        for(int side=-1;side<=1;side+=2){
            for(int k=0;k<line.length;k+=60){
                double[] p=line[k];double nx=-p[3]*side,nz=p[2]*side;
                int x=(int)Math.floor(p[0]+nx*(half-0.6)),z=(int)Math.floor(p[1]+nz*(half-0.6));
                if(Plan.zone(x,z)!=Plan.CYBER||!t.roads.on(x,z)||t.roads.map[Canvas.idx(x,z)]!=Roads.WALK)continue;
                Direction road=Direction.getFacing(-nx,0,-nz);
                if(lamp(x,z,road))lamps++;
                // a car at the kerb beside some of them
                if(rnd.nextDouble()<0.45){
                    int cx=(int)Math.floor(p[0]+nx*(half-4.5)),cz=(int)Math.floor(p[1]+nz*(half-4.5));
                    if(car(cx,cz,Math.abs(p[2])>Math.abs(p[3])?Direction.Axis.X:Direction.Axis.Z))cars++;
                }
                if((k/60)%7==3)busStop(x+road.getOffsetX()*-2,z+road.getOffsetZ()*-2,road);
            }
        }
    }
    private boolean lamp(int x,int z,Direction road){
        int g=c.ground(x,z);
        for(int yy=g+1;yy<=g+9;yy++)if(!c.air(x,yy,z))return false;
        for(int yy=g+1;yy<=g+8;yy++)c.set(x,yy,z,s(Blocks.POLISHED_BLACKSTONE_WALL));
        c.set(x,g+9,z,PANEL);
        for(int k=1;k<=2;k++){int ax=x+road.getOffsetX()*k,az=z+road.getOffsetZ()*k;if(c.air(ax,g+9,az))c.set(ax,g+9,az,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,true));}
        int lx=x+road.getOffsetX()*2,lz=z+road.getOffsetZ()*2;
        if(c.air(lx,g+8,lz))c.set(lx,g+8,lz,Canvas.rod(Blocks.END_ROD,Direction.DOWN));
        c.set(x,g+4,z,NEON[rnd.nextInt(NEON.length)]);
        return true;
    }
    /** A car at the kerb along the given axis: a body of painted concrete, dark glass over it, lights at its ends. */
    private boolean car(int x,int z,Direction.Axis along){
        int lx=along==Direction.Axis.X?2:1,lz=along==Direction.Axis.X?1:2;
        int g=c.ground(x,z);
        for(int dx=-lx;dx<=lx;dx++)for(int dz=-lz;dz<=lz;dz++){
            if(!t.roads.on(x+dx,z+dz)||t.roads.map[Canvas.idx(x+dx,z+dz)]==Roads.WALK||!c.air(x+dx,g+1,z+dz)||c.ground(x+dx,z+dz)!=g)return false;
        }
        BlockState body=switch(rnd.nextInt(5)){case 0->s(Blocks.BLACK_CONCRETE);case 1->s(Blocks.WHITE_CONCRETE);case 2->s(Blocks.RED_CONCRETE);case 3->s(Blocks.GRAY_CONCRETE);default->s(Blocks.LIGHT_BLUE_CONCRETE);};
        for(int dx=-lx;dx<=lx;dx++)for(int dz=-lz;dz<=lz;dz++){
            boolean end=along==Direction.Axis.X?Math.abs(dx)==lx:Math.abs(dz)==lz;
            boolean front=along==Direction.Axis.X?dx==lx:dz==lz;
            c.set(x+dx,g+1,z+dz,end?(front?s(Blocks.SEA_LANTERN):s(Blocks.REDSTONE_BLOCK)):body);
            c.set(x+dx,g+2,z+dz,end?Canvas.slab(body.getBlock()==Blocks.BLACK_CONCRETE?Blocks.POLISHED_BLACKSTONE_SLAB:Blocks.SMOOTH_STONE_SLAB,false):s(Blocks.BLACK_STAINED_GLASS));
        }
        return true;
    }
    /** A bus stop on the sidewalk: a canopy of metal on posts, a bench, a lit poster at its end. */
    private void busStop(int x,int z,Direction road){
        int g=c.ground(x,z);
        Direction along=road.rotateYClockwise();
        for(int k=-2;k<=2;k++){
            int bx=x+along.getOffsetX()*k,bz=z+along.getOffsetZ()*k;
            if(!c.air(bx,g+1,bz)||!c.air(bx,g+4,bz))return;
        }
        for(int k=-2;k<=2;k++){
            int bx=x+along.getOffsetX()*k,bz=z+along.getOffsetZ()*k;
            c.set(bx,g+4,bz,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
            if(Math.abs(k)==2)for(int yy=g+1;yy<=g+3;yy++)c.set(bx,yy,bz,s(Blocks.IRON_BARS));
            else c.set(bx,g+1,bz,Canvas.stairs(Blocks.POLISHED_BLACKSTONE_STAIRS,road.getOpposite(),false));
        }
        int px=x+along.getOffsetX()*3,pz=z+along.getOffsetZ()*3;
        for(int yy=g+1;yy<=g+3;yy++)if(c.air(px,yy,pz))c.set(px,yy,pz,LED[0]);
        c.wallSign(px+road.getOffsetX(),g+2,pz+road.getOffsetZ(),Blocks.DARK_OAK_WALL_SIGN,road,DyeColor.CYAN,true,"NEON LINE","정류장");
    }
}
