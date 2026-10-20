package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Puts the town together, in the order the pieces depend on each other, and writes the map's definition. */
final class Townscape {
    final Canvas c;final Terrain terrain;final Layout layout;final Random rnd;
    final Roads roads;final Hill hill;final Nature nature;final Houses houses;final Street street;final Coast coast;
    final Rail rail;final Harbor harbor;final Bridge bridge;final Ground ground;final Shrine shrine;final River river;final Shops shops;
    /** Places the mod uses: merchants (seller, buyer) and the field supply spots. */
    final List<int[]> merchants=new ArrayList<>();final List<int[]> loot=new ArrayList<>();
    final Map<String,int[]> places=new LinkedHashMap<>();
    Townscape(Canvas c,Terrain terrain,Layout layout,Random rnd){
        this.c=c;this.terrain=terrain;this.layout=layout;this.rnd=rnd;
        roads=new Roads(this);hill=new Hill(this);nature=new Nature(this);houses=new Houses(this);street=new Street(this);coast=new Coast(this);
        rail=new Rail(this);harbor=new Harbor(this);bridge=new Bridge(this);ground=new Ground(this);shrine=new Shrine(this);river=new River(this);shops=new Shops(this);
    }

    void build(){
        roads.plan();hill.plan(roads);
        roads.lay();Town.log("streets laid: "+c.placed);
        hill.build();Town.log("stairway and ramps: "+c.placed);
        // the sea front and the line first (the shops and houses keep clear of them), then the plots
        coast.build();harbor.build();ground.build();rail.build();river.build();bridge.build();shrine.build();
        assignUses();
        shops.build();
        Map<String,Integer> counts=new TreeMap<>();
        for(Lot l:layout.lots){
            if(l.use.isEmpty()||l.use.contains(":")||Set.of("konbini","post","clinic","sento","ryokan").contains(l.use))continue;
            houses.build(l);counts.merge(l.use,1,Integer::sum);
        }
        Town.log("plots built: "+counts+", "+c.placed);
        street.build();
        details();
        nature.woods();Town.log("woods: "+c.placed);
    }

    /** Everyday things along the streets: rubbish points, post boxes, mirrors at blind corners, a few bus stops. */
    void details(){
        var r=new Random(rnd.nextLong());
        for(int k=0;k<4;k++)for(int[] s:Plan.SIDE_STREETS){
            int z=Plan.ST_N[k]-1;
            if(r.nextInt(3)==0)street.rubbish(s[1]+1,z,Direction.SOUTH);
            if(r.nextInt(3)==0)street.mirror(s[0]-1,Plan.ST_S[k]+1);
        }
        for(int i=0;i<4;i++){int x=-150+i*95;street.postBox(x,Plan.ST_S[1]+1);}
        street.busStop(-60,Plan.PROM_Z0,Direction.NORTH,"ぼたんど海岸");
        street.busStop(70,Plan.PROM_Z0,Direction.NORTH,"大橋入口");
        street.busStop(-150,Plan.PROM_Z0,Direction.NORTH,"ぼたんど漁港");
    }

    /** What each plot becomes, by where it lies. */
    void assignUses(){
        var r=new Random(rnd.nextLong());
        for(Lot l:layout.lots){
            String[] pool=switch(l.zone){
                case "row","station-street"->new String[]{"old","old","old","modern","modern","modern","modern","modern3","apartment","apartment","parking","field","garden","vacant"};
                case "terrace"->new String[]{"old","old","old","old","modern","modern","modern","modern3","apartment","field","field","garden","vacant","parking"};
                case "terrace-back"->new String[]{"old","old","modern","modern","field","field","field","garden","vacant"};
                case "strip"->new String[]{"garden","field"};
                default->null;
            };
            if(pool==null)continue;
            String use=pool[r.nextInt(pool.length)];
            if(use.equals("apartment")&&l.frontage()<13)use="modern";
            l.use=use;
        }
    }

    /** Where players appear: the station square, looking down the main street to the bridge. */
    int[] spawn(){return new int[]{0,Plan.COAST+1,Plan.ST_S[0]+6,0};}

    /**
     * The map's definition for the mod (data/botand/map/definition.json in the world's data pack: the spawn, the two
     * merchants' places, the field supply spots, the named places) and a teleport function per place for operators
     * (/function botand:goto/bridge).
     */
    void definition(Path pack)throws Exception{
        var loot=lootSpots();
        var j=new StringBuilder();
        int[] sp=spawn();
        j.append("{\n  \"format\": 1,\n  \"id\": \"botand:seaside\",\n  \"name\": \"보탠드 해안 마을\",\n");
        j.append("  \"spawn\": [").append(sp[0]).append(", ").append(sp[1]).append(", ").append(sp[2]).append(", ").append(sp[3]).append("],\n");
        int[] a=merchants.get(0),b=merchants.get(1);
        j.append("  \"merchants\": {\n    \"seller\": {\"pos\": [").append(a[0]+0.5).append(", ").append(a[1]).append(", ").append(a[2]+0.5).append("], \"yaw\": ").append(a[3]).append("},\n");
        j.append("    \"buyer\": {\"pos\": [").append(b[0]+0.5).append(", ").append(b[1]).append(", ").append(b[2]+0.5).append("], \"yaw\": ").append(b[3]).append("}\n  },\n");
        j.append("  \"loot\": [");
        for(int i=0;i<loot.size();i++){int[] l=loot.get(i);j.append(i>0?", ":"").append("[").append(l[0]).append(", ").append(l[1]).append(", ").append(l[2]).append("]");}
        j.append("],\n  \"places\": {");
        int k=0;
        for(var e:places.entrySet()){int[] p=e.getValue();j.append(k++>0?",":"").append("\n    \"").append(e.getKey()).append("\": [").append(p[0]).append(", ").append(p[1]).append(", ").append(p[2]).append("]");}
        j.append("\n  }\n}\n");
        Files.createDirectories(pack.resolve("data/botand/map"));
        Files.writeString(pack.resolve("data/botand/map/definition.json"),j.toString());
        var fn=pack.resolve("data/botand/functions/goto");Files.createDirectories(fn);
        for(var e:places.entrySet()){int[] p=e.getValue();Files.writeString(fn.resolve(e.getKey()+".mcfunction"),"tp @s "+p[0]+".5 "+p[1]+" "+p[2]+".5\n");}
        Files.writeString(fn.resolve("spawn.mcfunction"),"tp @s "+sp[0]+".5 "+sp[1]+" "+sp[2]+".5 "+sp[3]+" 0\n");
        Town.log("definition: "+loot.size()+" supply spots, "+places.size()+" places");
    }
    /** The field supply spots: open ground in public places all over the town, checked to be clear. */
    List<int[]> lootSpots(){
        int[][] want={{-160,66},{-120,60},{-50,62},{-75,64},{30,70},{60,100},{88,112},{8,216},{12,-222},{50,38},
            {River.pathX(-60)+1,-60},{60,Plan.terraceStreetZ0(1,60)-1},{-120,Plan.terraceStreetZ0(2,-120)-1},{-100,-44},{110,-3},{-170,21},{230,50},{-212,50}};
        List<int[]> out=new ArrayList<>();
        for(int[] w:want){
            // the open ground at the spot, or the nearest clear ground round it
            int[] found=null;
            search:for(int r=0;r<=4;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
                int x=w[0]+dx,z=w[1]+dz,y=c.ground(x,z)+1;
                if(!c.air(x,y,z)||!c.air(x,y+1,z)||!c.air(x,y+2,z)||c.water(x,y-1,z)||c.open(x,y-1,z))continue;
                found=new int[]{x,y,z};break search;
            }
            if(found==null){Town.log("supply spot skipped at "+w[0]+","+w[1]);continue;}
            out.add(found);
        }
        return out;
    }

    /** A top-down picture of the finished model in the colours of an in-game map (a check for the builder). */
    void preview(Path out)throws Exception{
        var img=new BufferedImage(Plan.W,Plan.D,BufferedImage.TYPE_INT_RGB);
        int[] last=new int[Plan.W];
        for(int z=Plan.Z0;z<Plan.Z0+Plan.D;z++)for(int x=Plan.X0;x<Plan.X0+Plan.W;x++){
            int top=c.top(x,z,Canvas.YMAX);
            BlockState s=c.get(x,top,z);
            int rgb=s.getMapColor(null,null).color;
            int prev=z>Plan.Z0?last[x-Plan.X0]:top;last[x-Plan.X0]=top;
            double k=top>prev?1.12:top<prev?0.82:1.0;
            if(!s.getFluidState().isEmpty()){int d=top-c.ground(x,z);k=Math.max(0.55,1.0-d*0.04);}
            int r=(int)Math.min(255,((rgb>>16)&255)*k),g=(int)Math.min(255,((rgb>>8)&255)*k),b=(int)Math.min(255,(rgb&255)*k);
            img.setRGB(x-Plan.X0,z-Plan.Z0,(r<<16)|(g<<8)|b);
        }
        ImageIO.write(img,"png",out.toFile());
        Town.log("preview written to "+out);
    }
}
