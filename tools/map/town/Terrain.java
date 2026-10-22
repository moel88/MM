package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import java.util.ArrayDeque;
import java.util.Random;

/**
 * The hourglass island's ground (map v3.0, 2000 x 2000). The rift at the waist is level white ground (y 70) falling to
 * the sea in white cliffs. The upper glass rises gently away from it (the town and the capital from y 72 to 80, rolling
 * a little), the shrine's hill standing up in the town's far corner (its precinct at y 104) and the castle's plateau in
 * the capital's (y 98, on sheer cliffs). The lower glass is lower: the forest rolls over hills and comes down to sand
 * all along its shore; the cyber city is level (y 70) on low cliffs. Where two zones meet on the axis the ground blends
 * from one to the other; round the rift it eases down to the rift's level. The town's canal runs in a stone channel two
 * blocks below the ground; the forest's lake lies in a hollow.
 */
final class Terrain {
    static final byte SEA=1,LAND=2,BEACH=4;
    private final Canvas c;private final Random rnd;
    final byte[] kind=new byte[Plan.W*Plan.D];
    /** The water inside the land: the canal's and the lake's columns, with their surface's level. */
    final int[] water=new int[Plan.W*Plan.D];
    /** The columns whose ground is the rift's white. */
    final boolean[] pale=new boolean[Plan.W*Plan.D];
    private final double[] ph=new double[12];
    Terrain(Canvas c,long seed){this.c=c;rnd=new Random(seed);for(int i=0;i<ph.length;i++)ph[i]=rnd.nextDouble()*Math.PI*2;}

    double noise(double x,double z){
        return (Math.sin(x/37+ph[0])*Math.cos(z/41+ph[1])+0.6*Math.sin(x/17.3+z/23.1+ph[2])+0.35*Math.cos(x/9.7-z/11.3+ph[3])+0.2*Math.sin(x/5.1+ph[4])*Math.sin(z/4.7+ph[5]))/2.15;
    }
    double noise2(double x,double z){return (Math.sin(x/29+ph[6])*Math.cos(z/31+ph[7])+0.5*Math.sin(x/13.1-z/15.7+ph[8])+0.25*Math.cos(x/6.3+z/7.1+ph[9]))/1.75;}

    /** A zone's own ground height at a point (before the blends round the rift and across the axis). */
    double zoneHeight(int zone,double x,double z){
        double u=Math.abs(z)/Plan.END,K=Plan.K;
        switch(zone){
            case Plan.TOWN->{
                double h=72+8*Plan.ss(.35,.75,u)+1.6*noise(x/K,z/K)*Plan.ss(.3,.5,u);
                return Math.max(h,mountain(x,z,h));
            }
            case Plan.CAPITAL->{
                double h=72+8*Plan.ss(.35,.75,u)+1.2*noise2(x/K,z/K)*Plan.ss(.3,.5,u);
                // the castle's plateau: level ground on cliffs
                return Math.hypot(x-Plan.CASTLE_X,z-Plan.CASTLE_Z)<Plan.PLATEAU_R+3*noise2(x,z)?Plan.CASTLE_Y:h;
            }
            case Plan.FOREST->{
                return 70+6*noise(x/2.5,z/2.5)+3*noise2(x/1.3,z/1.3)+1.2*noise(x,z)+4*Plan.ss(.45,.85,u)*(1-Plan.ss(.9,1,u));
            }
            case Plan.CYBER->{return Plan.BASE;}
            default->{return Plan.BASE;}
        }
    }
    /**
     * The great mountain (the town's far corner, the island's landmark): a level summit for the shrine, then slopes
     * steep near the top and easing out to the foot, its face cut into ridges and gullies that wander round it, rough
     * underfoot; {@code below} is the ground it rises from.
     */
    double mountain(double x,double z,double below){
        double dx=x-Plan.MOUNT_X,dz=z-Plan.MOUNT_Z,d=Math.hypot(dx,dz);
        if(d>Plan.MOUNT_R*1.3)return below;
        double a=Math.atan2(dz,dx);
        double rr=d*(1+0.10*Math.sin(7*a+2.5*noise(x/40.0,z/40.0))+0.05*Math.sin(13*a+1.7)+0.03*Math.sin(23*a+0.3));
        double sr=Plan.MOUNT_SUMMIT,R=Plan.MOUNT_R;
        if(d<sr*0.8)return Plan.MOUNT_TOP;
        double s=Math.max(0,Math.min(1,(rr-sr)/(R-sr)));
        double h=below+(Plan.MOUNT_TOP-below)*Math.pow(1-s,1.75);
        // rough slopes, rougher high up, smooth toward the foot and the summit's edge
        h+=(2.0+5.0*(1-s))*noise(x/6.0,z/6.0)*Plan.ss(0,0.08,s)*(1-Plan.ss(0.8,1,s));
        return h;
    }
    /** The ground's height at a point, every blend applied (the shore aside). */
    double height(double x,double z){
        double R=Math.hypot(x,z);
        if(R<Plan.RIFT-30)return Plan.BASE;
        boolean upper=z<0;
        int left=upper?Plan.TOWN:Plan.FOREST,right=upper?Plan.CAPITAL:Plan.CYBER;
        double w=Plan.ss(-90,90,x);
        double h=zoneHeight(left,x,z)*(1-w)+zoneHeight(right,x,z)*w;
        // the shrine's hill and the castle's plateau keep their own heights wherever they are
        if(upper&&x<-100)h=zoneHeight(Plan.TOWN,x,z);
        if(upper&&x>100)h=zoneHeight(Plan.CAPITAL,x,z);
        return Plan.BASE+(h-Plan.BASE)*Plan.ss(Plan.RIFT-30,Plan.RIFT+120,R);
    }
    /** The canal's pools: level stretches of sixteen blocks, each two below the lowest bank along it, held by weirs (OldTown). */
    static final int POOL=16;
    private int canalLevel(int[] F,int z){
        int seg=Math.floorDiv(z-Plan.CANAL_Z0,POOL),lo=Integer.MAX_VALUE;
        for(int zz=Plan.CANAL_Z0+seg*POOL;zz<Plan.CANAL_Z0+(seg+1)*POOL&&zz<=Plan.CANAL_Z1;zz++){
            for(int side=-1;side<=1;side+=2){
                int x=(int)Math.floor(Plan.canalX(zz+0.5)+side*(Plan.canalHalf(zz+0.5)+1));
                lo=Math.min(lo,F[Canvas.idx(x,zz)]);
            }
        }
        return lo-2;
    }
    /** Whether a shore comes down to sand (the forest's) or ends in a cliff (everywhere else). */
    static boolean sandy(double x,double z){return z>0&&x<-60;}

    void build(){
        int n=Plan.W*Plan.D;int[] F=new int[n];
        for(int i=0;i<n;i++){
            int x=Plan.X0+i/Plan.D,z=Plan.Z0+i%Plan.D;
            double s=Plan.shore(x,z);
            if(s>=0){kind[i]=SEA;continue;}
            double h=height(x,z);
            if(sandy(x,z)){
                // the forest's shore: the ground eases down to a strip of sand at the water
                h=63+(h-63)*Plan.ss(0,30,-s);
                kind[i]=h<64.6?BEACH:LAND;
            }else kind[i]=LAND;
            F[i]=(int)Math.round(h);
        }
        // the town's canal in its channel, the forest's lake in its hollow
        for(int i=0;i<n;i++){
            if(kind[i]==SEA)continue;
            int x=Plan.X0+i/Plan.D,z=Plan.Z0+i%Plan.D;
            if(z>=Plan.CANAL_Z0&&z<=Plan.CANAL_Z1&&Math.abs(x+0.5-Plan.canalX(z+0.5))<Plan.canalHalf(z+0.5)){water[i]=canalLevel(F,z);F[i]=water[i]-2;}
            double dl=Math.hypot(x-Plan.LAKE_X,z-Plan.LAKE_Z)+7*noise(x/1.5,z/1.5);int ly=Plan.LAKE_Y;
            if(dl<Plan.LAKE_R){water[i]=ly;F[i]=Math.min(F[i],(int)Math.round(ly-1-6*Plan.ss(Plan.LAKE_R,Plan.LAKE_R*0.4,dl)));}
            else if(dl<Plan.LAKE_R+24)F[i]=(int)Math.round(Math.min(F[i],ly+(F[i]-ly)*Plan.ss(Plan.LAKE_R,Plan.LAKE_R+24,dl)));
        }
        // the sea floor: shelving off the sandy shores, falling away quickly under the cliffs
        int[] dist=shoreDistance();
        for(int i=0;i<n;i++){
            if(kind[i]!=SEA)continue;
            int x=Plan.X0+i/Plan.D,z=Plan.Z0+i%Plan.D;
            double d=dist[i],floor=sandy(x,z)?61.2-d*0.3:58-d*0.32;
            floor+=0.8*noise(x*2,z*2);
            F[i]=(int)Math.round(Math.max(Plan.FLOOR+2,Math.min(61,floor)));
        }
        for(int i=0;i<n;i++){
            int x=Plan.X0+i/Plan.D,z=Plan.Z0+i%Plan.D,top=F[i];
            c.ground(x,z,top);
            if(kind[i]==SEA){
                for(int y=Canvas.Y0;y<=top;y++)c.set(x,y,z,y==top&&rnd.nextInt(5)==0?Blocks.GRAVEL.getDefaultState():Blocks.SAND.getDefaultState());
                continue;
            }
            int zone=Plan.zone(x,z);
            // the white, and round its edge white patches bleeding into the land
            double over=Math.hypot(x,z)-Plan.riftEdge(x,z);
            boolean rift=over<0||over<20&&Pal.smooth(x,z,7,77)>0.42+over*0.025;
            pale[i]=rift;
            for(int y=Canvas.Y0;y<=top;y++){
                BlockState st;
                if(y==top)st=surface(x,z,top,i,F,zone,rift);
                else if(rift)st=white(x,y,z,y>=top-3);
                else if(y>=top-3&&!steep(F,i,top)&&top<=140)st=kind[i]==BEACH||top<=Plan.SEA+2?Blocks.SAND.getDefaultState():Blocks.DIRT.getDefaultState();
                else if(top>140&&y>=top-24)st=rock(x,y,z);
                else st=y>=top-12&&cliffSide(F,i,top)?rock(x,y,z):Blocks.STONE.getDefaultState();
                c.set(x,y,z,st);
            }
            if(water[i]!=0)for(int y=top+1;y<=water[i];y++)c.set(x,y,z,Blocks.WATER.getDefaultState());
        }
    }

    static final BlockState RIFT_GROUND=TB.of("rift_ground");
    /** The rift's ground (Botand's white rift ground, 2.49.0) and what lies under it: calcite, quartz, white concrete, the odd vein of diorite. */
    BlockState white(int x,int y,int z,boolean near){
        double h=Pal.hash(x,y,z),s=Pal.smooth(x+y,z-y,7,41);
        if(!near&&h<0.18)return Blocks.DIORITE.getDefaultState();
        if(s<0.35)return Blocks.CALCITE.getDefaultState();
        if(s<0.70)return Blocks.SMOOTH_QUARTZ.getDefaultState();
        return Blocks.WHITE_CONCRETE.getDefaultState();
    }
    private BlockState surface(int x,int z,int top,int i,int[] F,int zone,boolean rift){
        if(rift)return Pal.smooth(x,z,5,43)>0.86?Blocks.CALCITE.getDefaultState():RIFT_GROUND;
        if(kind[i]==BEACH||top<=Plan.SEA+2&&sandy(x,z))return Pal.hash(x,6,z)<0.9?Blocks.SAND.getDefaultState():Blocks.GRAVEL.getDefaultState();
        if(water[i]!=0)return Pal.hash(x,8,z)<0.5?Blocks.GRAVEL.getDefaultState():Blocks.DIRT.getDefaultState();
        if(zone==Plan.TOWN&&top>132){
            // the mountain's heights: snow over its top, scree and bare rock below it, the last grass giving out
            double sn=Plan.SNOW+7*noise(x/9.0,z/9.0);
            if(top>=sn||top>=sn-16&&Pal.hash(x,4,z)<(top-(sn-16))/16.0)return Blocks.SNOW_BLOCK.getDefaultState();
            double g=Pal.hash(x,5,z),grass=Plan.ss(170,135,top);
            if(!steep(F,i,top)&&g<grass*0.8)return Blocks.GRASS_BLOCK.getDefaultState();
            if(g>0.9)return Blocks.GRAVEL.getDefaultState();
            return rock(x,top,z);
        }
        if(steep(F,i,top)){
            double g=Pal.hash(x,5,z);
            if(g<0.30)return Blocks.GRASS_BLOCK.getDefaultState();
            if(g<0.36)return Blocks.MOSS_BLOCK.getDefaultState();
            return rock(x,top,z);
        }
        if(zone==Plan.FOREST){
            double g=Pal.smooth(x,z,9,23);
            if(g<0.16)return Blocks.PODZOL.getDefaultState();
            if(g<0.22)return Blocks.COARSE_DIRT.getDefaultState();
            if(g>0.86)return Blocks.MOSS_BLOCK.getDefaultState();
        }
        return Pal.hash(x,7,z)<0.02?Blocks.COARSE_DIRT.getDefaultState():Blocks.GRASS_BLOCK.getDefaultState();
    }
    /** Whether a column stands at the sea or over a big drop, so its sides show rock rather than earth. */
    private boolean cliffSide(int[] F,int i,int top){return slope(F,i,top)>=3;}

    /** How far each sea column lies from the nearest land, in blocks (4-way steps), up to 60. */
    private int[] shoreDistance(){
        int n=Plan.W*Plan.D;int[] d=new int[n];
        ArrayDeque<Integer> q=new ArrayDeque<>();
        for(int i=0;i<n;i++){if(kind[i]!=SEA){d[i]=0;q.add(i);}else d[i]=60;}
        while(!q.isEmpty()){
            int i=q.poll(),x=i/Plan.D,z=i%Plan.D;
            if(d[i]>=60)continue;
            int[] nb={x>0?i-Plan.D:-1,x<Plan.W-1?i+Plan.D:-1,z>0?i-1:-1,z<Plan.D-1?i+1:-1};
            for(int j:nb)if(j>=0&&d[j]>d[i]+1){d[j]=d[i]+1;q.add(j);}
        }
        return d;
    }
    private int lvl(int[] F,int j){return kind[j]==SEA?Plan.SEA-1:F[j];}
    /** A retaining wall's stones: weathered masonry, some of it mossy or cracked. */
    BlockState masonry(int x,int y,int z){
        double r=Pal.hash(x,y,z)*100;
        if(r<46)return Blocks.STONE_BRICKS.getDefaultState();
        if(r<62)return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        if(r<72)return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        if(r<86)return Blocks.ANDESITE.getDefaultState();
        if(r<94)return Blocks.COBBLESTONE.getDefaultState();
        return Blocks.MOSSY_COBBLESTONE.getDefaultState();
    }
    /** The greatest drop from a column to its neighbours. */
    private int slope(int[] F,int i,int top){
        int x=i/Plan.D,z=i%Plan.D,d=0;
        for(int[] o:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            int xx=x+o[0],zz=z+o[1];if(xx<0||zz<0||xx>=Plan.W||zz>=Plan.D)continue;
            d=Math.max(d,top-lvl(F,xx*Plan.D+zz));
        }
        return d;
    }
    /**
     * Bare rock of the cliffs and the shore: beds of stone, andesite and tuff a few blocks thick that wander up and
     * down across the face, broken and grown over in places.
     */
    BlockState rock(int x,int y,int z){
        double h=Pal.hash(x,y,z);
        if(h<0.05)return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        if(h<0.10)return Blocks.COBBLESTONE.getDefaultState();
        int bed=Math.floorMod((int)Math.floor((y+Pal.smooth(x,z,29,31)*7)/2.5),6);
        return switch(bed){
            case 0,3->h<0.78?Blocks.STONE.getDefaultState():Blocks.ANDESITE.getDefaultState();
            case 1,4->h<0.72?Blocks.ANDESITE.getDefaultState():Blocks.STONE.getDefaultState();
            default->h<0.70?Blocks.TUFF.getDefaultState():Blocks.STONE.getDefaultState();
        };
    }
    private boolean steep(int[] F,int i,int top){
        int x=i/Plan.D,z=i%Plan.D;
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            int xx=x+d[0],zz=z+d[1];if(xx<0||zz<0||xx>=Plan.W||zz>=Plan.D)continue;
            if(Math.abs(lvl(F,xx*Plan.D+zz)-top)>2)return true;
        }
        return false;
    }
}
