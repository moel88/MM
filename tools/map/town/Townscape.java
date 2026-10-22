package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/**
 * Puts the hourglass island together (map v3.0), in the order the pieces depend on each other: the roads, the rift and
 * its world tree, the old town with its canal and shrine, the capital with its castle and cathedral, the old city of
 * the first map, the forest with its lake and ruins, the meadows, the wires; then writes the map's definition.
 */
final class Townscape {
    final Canvas c;final Terrain terrain;final Random rnd;
    final Roads roads;final Lots lots;final Nature nature;final Houses houses;final Street street;
    final Rift rift;final Shrine shrine;final OldTown town;final Capital capital;final OldCity city;final Forest forest;
    /** Places the mod uses: merchants (seller, buyer) and the field supply spots. */
    final List<int[]> merchants=new ArrayList<>();
    final Map<String,int[]> places=new LinkedHashMap<>();
    /** The power and telephone lines, drawn by the mod as thin sagging lines: x0 y0 z0 x1 y1 z1 sag, in blocks. */
    final List<float[]> wires=new ArrayList<>();
    Townscape(Canvas c,Terrain terrain,Random rnd,Path pieces)throws Exception{
        this.c=c;this.terrain=terrain;this.rnd=rnd;
        roads=new Roads(this);lots=new Lots(this);nature=new Nature(this);houses=new Houses(this);street=new Street(this);
        rift=new Rift(this);shrine=new Shrine(this);town=new OldTown(this);capital=new Capital(this);city=new OldCity(this,pieces);forest=new Forest(this);
    }
    /** A wire from one point to another, hanging {@code sag} blocks below the straight line at its middle. */
    void wire(double x0,double y0,double z0,double x1,double y1,double z1,double sag){
        wires.add(new float[]{(float)x0,(float)y0,(float)z0,(float)x1,(float)y1,(float)z1,(float)sag});
    }

    void build(){
        roads.plan();
        roads.lay();Town.log("roads laid: "+c.placed);
        rift.build();
        town.build();
        capital.build();
        city.build();
        forest.build();
        nature.meadows();
        street.wireRuns();
    }

    /** Where players appear: on the rift's white south of the world tree, looking at it. */
    int[] spawn(){return new int[]{Plan.SPAWN_X,Plan.BASE+1,Plan.SPAWN_Z,180};}

    /**
     * The map's definition for the mod (data/botand/map/definition.json in the world's data pack: the spawn, the two
     * merchants' places, the field supply spots, the named places, the wires, the open sea's level) and a teleport
     * function per place for operators (/function botand:goto/tree).
     */
    void definition(Path pack)throws Exception{
        var loot=lootSpots();
        var j=new StringBuilder();
        int[] sp=spawn();
        j.append("{\n  \"format\": 1,\n  \"id\": \"botand:hourglass\",\n  \"name\": \"보탠드 모래시계 섬\",\n");
        j.append("  \"spawn\": [").append(sp[0]).append(", ").append(sp[1]).append(", ").append(sp[2]).append(", ").append(sp[3]).append("],\n");
        int[] a=merchants.get(0),b=merchants.get(1);
        j.append("  \"merchants\": {\n    \"seller\": {\"pos\": [").append(a[0]+0.5).append(", ").append(a[1]).append(", ").append(a[2]+0.5).append("], \"yaw\": ").append(a[3]).append("},\n");
        j.append("    \"buyer\": {\"pos\": [").append(b[0]+0.5).append(", ").append(b[1]).append(", ").append(b[2]+0.5).append("], \"yaw\": ").append(b[3]).append("}\n  },\n");
        j.append("  \"loot\": [");
        for(int i=0;i<loot.size();i++){int[] l=loot.get(i);j.append(i>0?", ":"").append("[").append(l[0]).append(", ").append(l[1]).append(", ").append(l[2]).append("]");}
        j.append("],\n  \"places\": {");
        int k=0;
        for(var e:places.entrySet()){int[] p=e.getValue();j.append(k++>0?",":"").append("\n    \"").append(e.getKey()).append("\": [").append(p[0]).append(", ").append(p[1]).append(", ").append(p[2]).append("]");}
        j.append("\n  },\n  \"wires\": [");
        for(int i=0;i<wires.size();i++){
            float[] w=wires.get(i);j.append(i>0?",":"").append(i%4==0?"\n    ":" ").append("[");
            for(int q=0;q<7;q++)j.append(q>0?", ":"").append(String.format(Locale.ROOT,"%.2f",w[q]));
            j.append("]");
        }
        j.append("\n  ],\n  \"backdrop\": [],\n");
        j.append("  \"sea\": ").append(String.format(Locale.ROOT,"%.1f",Plan.FAR_SEA)).append("\n}\n");
        Files.createDirectories(pack.resolve("data/botand/map"));
        Files.writeString(pack.resolve("data/botand/map/definition.json"),j.toString());
        var fn=pack.resolve("data/botand/functions/goto");Files.createDirectories(fn);
        for(var e:places.entrySet()){int[] p=e.getValue();Files.writeString(fn.resolve(e.getKey()+".mcfunction"),"tp @s "+p[0]+".5 "+p[1]+" "+p[2]+".5\n");}
        Files.writeString(fn.resolve("spawn.mcfunction"),"tp @s "+sp[0]+".5 "+sp[1]+" "+sp[2]+".5 "+sp[3]+" 0\n");
        Town.log("definition: "+loot.size()+" supply spots, "+places.size()+" places, "+wires.size()+" wires");
    }
    /** The field supply spots: open ground in every zone of the island, checked to be clear. */
    List<int[]> lootSpots(){
        int[][] want={{-30,22},{30,22},{-28,-24},{28,-24},{0,-42},                      // the rift, round the tree
            {-40,-90},{-95,-105},{-60,-140},{-120,-150},{-30,-160},{-100,-75},           // the old town, the canal, the shrine's foot
            {40,-80},{95,-105},{100,-140},{30,-150},{130,-120},{150,-128},                // the capital, the cathedral's square, the castle
            {-40,90},{-100,100},{-110,140},{-40,150},{-150,130},{-170,100},               // the forest, the lake, the ruins, the shore
            {20,80},{110,90},{150,170},{100,170},{20,165},{120,130}};                     // the old city
        List<int[]> out=new ArrayList<>();
        for(int[] w:want){
            int[] found=null;
            search:for(int r=0;r<=8;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
                int x=w[0]+dx,z=w[1]+dz;
                if(!Canvas.inPlan(x,z)||terrain.kind[Canvas.idx(x,z)]==Terrain.SEA)continue;
                int y=c.ground(x,z)+1;
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
