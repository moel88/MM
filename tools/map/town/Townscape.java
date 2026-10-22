package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/**
 * Puts the hourglass island together (map v3.0, 1000 x 1000), in the order the pieces depend on each other: the roads,
 * the rift and its world tree, the old town with its canal and the shrine on the great mountain, the capital in its
 * walls with the great castle and the cathedral, the cyber city, the forest with its lake and ruins, the meadows, the
 * wires; then writes the map's definition.
 */
final class Townscape {
    final Canvas c;final Terrain terrain;final Random rnd;
    final Roads roads;final Lots lots;final Nature nature;final Houses houses;final Street street;
    final Rift rift;final Shrine shrine;final OldTown town;final Capital capital;final Cyber cyber;final Forest forest;
    /** Places the mod uses: merchants (seller, buyer) and the field supply spots. */
    final List<int[]> merchants=new ArrayList<>();
    final Map<String,int[]> places=new LinkedHashMap<>();
    /** The power and telephone lines, drawn by the mod as thin sagging lines: x0 y0 z0 x1 y1 z1 sag, in blocks. */
    final List<float[]> wires=new ArrayList<>();
    Townscape(Canvas c,Terrain terrain,Random rnd){
        this.c=c;this.terrain=terrain;this.rnd=rnd;
        roads=new Roads(this);lots=new Lots(this);nature=new Nature(this);houses=new Houses(this);street=new Street(this);
        rift=new Rift(this);shrine=new Shrine(this);town=new OldTown(this);capital=new Capital(this);cyber=new Cyber(this);forest=new Forest(this);
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
        cyber.build();
        forest.build();
        nature.meadows();
        street.wireRuns();
        Town.log("plots turned down: "+lots.refused);
    }

    /** Where players appear: on the rift's white south of the world tree, looking at it. */
    int[] spawn(){return new int[]{Plan.SPAWN_X,Plan.BASE+1,Plan.SPAWN_Z,180};}

    /**
     * The map's definition for the mod (data/botand/map/definition.json in the world's data pack: the spawn, the two
     * merchants' places, the field supply spots, the named places, the wires, the open sea's level) and a teleport
     * function per place for operators (/function botand:goto/tree).
     */
    /** The map's definition and its teleport functions, by their path in the data pack (made while the model is in memory, written after). */
    Map<String,String> definition(){
        var files=new LinkedHashMap<String,String>();
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
        files.put("data/botand/map/definition.json",j.toString());
        for(var e:places.entrySet()){int[] p=e.getValue();files.put("data/botand/functions/goto/"+e.getKey()+".mcfunction","tp @s "+p[0]+".5 "+p[1]+" "+p[2]+".5\n");}
        files.put("data/botand/functions/goto/spawn.mcfunction","tp @s "+sp[0]+".5 "+sp[1]+" "+sp[2]+".5 "+sp[3]+" 0\n");
        Town.log("definition: "+loot.size()+" supply spots, "+places.size()+" places, "+wires.size()+" wires");
        return files;
    }
    /**
     * The field supply spots: open ground in every zone of the island, checked to be clear: six round the world tree,
     * then one about every hundred and ten blocks over the land (none high on the mountain, none in the sea); never
     * more than the mod takes (MapDefinition.MAX_LOOT, 64).
     */
    static final int MAX_LOOT=64;
    List<int[]> lootSpots(){
        List<int[]> wantList=new ArrayList<>();
        for(int k=0;k<6;k++){double a=k*Math.PI/3+0.3;wantList.add(new int[]{(int)(Math.cos(a)*95),(int)(Math.sin(a)*95)});}
        for(int x=-440;x<=440;x+=110)for(int z=-440;z<=440;z+=110){
            if(Plan.shore(x,z)>-12||Math.hypot(x,z)<Plan.RIFT)continue;
            if(Math.hypot(x-Plan.MOUNT_X,z-Plan.MOUNT_Z)<Plan.MOUNT_R*0.6)continue;
            wantList.add(new int[]{x+((x/110+z/110)&1)*27,z});
        }
        int[][] want=wantList.toArray(new int[0][]);
        List<int[]> out=new ArrayList<>();
        for(int[] w:want){
            int[] found=null;
            search:for(int r=0;r<=24;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
                int x=w[0]+dx,z=w[1]+dz;
                if(!Canvas.inPlan(x,z)||terrain.kind[Canvas.idx(x,z)]==Terrain.SEA)continue;
                int y=c.ground(x,z)+1;
                if(!c.air(x,y,z)||!c.air(x,y+1,z)||!c.air(x,y+2,z)||c.water(x,y-1,z)||c.open(x,y-1,z))continue;
                found=new int[]{x,y,z};break search;
            }
            if(found==null){Town.log("supply spot skipped at "+w[0]+","+w[1]);continue;}
            if(out.size()>=MAX_LOOT){Town.log("supply spot dropped at "+w[0]+","+w[1]+": the mod takes "+MAX_LOOT);continue;}
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
        // the plots tried, outlined by why they were turned down (green: built), for tuning the layout
        var g=img.createGraphics();
        Map<String,java.awt.Color> col=Map.of("",java.awt.Color.GREEN,"road",java.awt.Color.RED,"taken",java.awt.Color.ORANGE,"shore",java.awt.Color.CYAN,
            "not land",java.awt.Color.BLUE,"rift",java.awt.Color.MAGENTA,"steep",java.awt.Color.YELLOW,"water",java.awt.Color.PINK,"outside",java.awt.Color.GRAY);
        for(Object[] r:lots.tried){
            g.setColor(col.getOrDefault((String)r[4],java.awt.Color.WHITE));
            g.drawRect((int)r[0]-Plan.X0,(int)r[1]-Plan.Z0,(int)r[2]-(int)r[0],(int)r[3]-(int)r[1]);
        }
        g.dispose();
        ImageIO.write(img,"png",out.resolveSibling("town_lots.png").toFile());
        var own=new BufferedImage(Plan.W,Plan.D,BufferedImage.TYPE_INT_RGB);
        int[] pal={0x202020,0xff4040,0x40ff40,0x4040ff,0xffff40,0xff40ff,0x40ffff,0xffffff,0x808080,0xff8000};
        for(int x=Plan.X0;x<Plan.X0+Plan.W;x++)for(int z=Plan.Z0;z<Plan.Z0+Plan.D;z++){int o=c.owner(x,z);own.setRGB(x-Plan.X0,z-Plan.Z0,o<0?0:pal[o%pal.length]);}
        ImageIO.write(own,"png",out.resolveSibling("town_owner.png").toFile());
    }
}
