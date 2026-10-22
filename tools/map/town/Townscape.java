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
    final Roads roads;final Nature nature;final Houses houses;final Street street;final Coast coast;
    final Harbor harbor;final Bridge bridge;final Shrine shrine;final Shops shops;
    final Plaza plaza;final Park park;final School school;final SeaLot lot;final Waterfront waterfront;
    /** Places the mod uses: merchants (seller, buyer) and the field supply spots. */
    final List<int[]> merchants=new ArrayList<>();final List<int[]> loot=new ArrayList<>();
    final Map<String,int[]> places=new LinkedHashMap<>();
    /** The power and telephone lines, drawn by the mod as thin sagging lines: x0 y0 z0 x1 y1 z1 sag, in blocks. */
    final List<float[]> wires=new ArrayList<>();
    Townscape(Canvas c,Terrain terrain,Layout layout,Random rnd){
        this.c=c;this.terrain=terrain;this.layout=layout;this.rnd=rnd;
        roads=new Roads(this);nature=new Nature(this);houses=new Houses(this);street=new Street(this);coast=new Coast(this);
        harbor=new Harbor(this);bridge=new Bridge(this);shrine=new Shrine(this);shops=new Shops(this);
        plaza=new Plaza(this);park=new Park(this);school=new School(this);lot=new SeaLot(this);waterfront=new Waterfront(this);
    }
    /** A wire from one point to another, hanging {@code sag} blocks below the straight line at its middle. */
    void wire(double x0,double y0,double z0,double x1,double y1,double z1,double sag){
        wires.add(new float[]{(float)x0,(float)y0,(float)z0,(float)x1,(float)y1,(float)z1,(float)sag});
    }

    void build(){
        roads.plan();
        roads.lay();Town.log("streets laid: "+c.placed);
        // the sea front first, then the blocks of the town, the plots, the street furniture and the woods last
        coast.build();harbor.build();lot.build();bridge.build();shrine.build();
        plaza.build();park.build();school.build();waterfront.build();
        shops.build();
        assignUses();
        Map<String,Integer> counts=new TreeMap<>();
        for(Lot l:layout.lots){
            switch(l.zone){
                case "house"->houses.build(l);
                case "commons"->{l.use="commons";houses.commons(Frame.of(c,l));}
                case "coop","ice","gear"->harbor.building(l);
                default->{}
            }
            if(!l.use.isEmpty())counts.merge(l.use.contains(":")?"shop":l.use,1,Integer::sum);
        }
        Town.log("plots built: "+counts+", "+c.placed);
        street.build();
        details();
        nature.woods();Town.log("woods: "+c.placed);
        street.wireRuns();
    }

    /** Everyday things along the streets: post boxes, bus stops. */
    void details(){
        street.postBox(Plan.WEST.a1()+2,Plan.MIDDLE.a1()+3);street.postBox(Plan.EAST.a0()-2,Plan.SOUTH.a0()-3);
        street.busStop(14,Plan.NORTH.a1(),Direction.NORTH,"神社前");
        street.busStop(Plan.WEST.a1(),Plan.SOUTH.a1()+6,Direction.WEST,"ぼたんど漁港");
        street.busStop(Plan.EAST.a0(),Plan.MIDDLE.a0()-6,Direction.EAST,"ぼたんど小学校前");
    }

    /** What each house plot becomes: old and new houses, now and then a block of flats, a garden or an empty plot. */
    void assignUses(){
        var r=new Random(rnd.nextLong());
        String[] pool={"old","old","old","modern","modern","modern","modern","modern3","apartment","garden","old","modern"};
        for(Lot l:layout.lots){
            if(!l.zone.equals("house"))continue;
            String use=pool[r.nextInt(pool.length)];
            if(use.equals("apartment")&&l.frontage()<13)use="modern";
            l.use=use;
        }
    }

    /** Where players appear: the middle of the square, looking south down the main street to the bridge. */
    int[] spawn(){return new int[]{Plan.SPAWN_X,Plan.COAST+1,Plan.SPAWN_Z,0};}

    /**
     * The map's definition for the mod (data/botand/map/definition.json in the world's data pack: the spawn, the two
     * merchants' places, the field supply spots, the named places, the wires, the open sea's level) and a teleport
     * function per place for operators (/function botand:goto/bridge).
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
        j.append("\n  },\n  \"wires\": [");
        for(int i=0;i<wires.size();i++){
            float[] w=wires.get(i);j.append(i>0?",":"").append(i%4==0?"\n    ":" ").append("[");
            for(int q=0;q<7;q++)j.append(q>0?", ":"").append(String.format(Locale.ROOT,"%.2f",w[q]));
            j.append("]");
        }
        j.append("\n  ],\n  \"backdrop\": [");
        for(int i=0;i<Plan.BACKDROP.length;i++){
            float[] m=Plan.BACKDROP[i];j.append(i>0?",":"").append("\n    [");
            for(int q=0;q<5;q++)j.append(q>0?", ":"").append(String.format(Locale.ROOT,"%.1f",m[q]));
            j.append(", ").append((int)m[5]).append("]");
        }
        j.append(Plan.BACKDROP.length>0?"\n  ],\n":"],\n").append("  \"sea\": ").append(String.format(Locale.ROOT,"%.1f",Plan.FAR_SEA)).append("\n}\n");
        Files.createDirectories(pack.resolve("data/botand/map"));
        Files.writeString(pack.resolve("data/botand/map/definition.json"),j.toString());
        var fn=pack.resolve("data/botand/functions/goto");Files.createDirectories(fn);
        for(var e:places.entrySet()){int[] p=e.getValue();Files.writeString(fn.resolve(e.getKey()+".mcfunction"),"tp @s "+p[0]+".5 "+p[1]+" "+p[2]+".5\n");}
        Files.writeString(fn.resolve("spawn.mcfunction"),"tp @s "+sp[0]+".5 "+sp[1]+" "+sp[2]+".5 "+sp[3]+" 0\n");
        Town.log("definition: "+loot.size()+" supply spots, "+places.size()+" places, "+wires.size()+" wires");
    }
    /** The field supply spots: open ground in public places all over the town, checked to be clear. */
    List<int[]> lootSpots(){
        int[][] want={{-30,-20},{30,-20},{-30,20},{30,20},{0,-46},                     // the square
            {-100,-30},{-100,15},{-160,0},{-125,-45},                                  // the park
            {95,-15},{150,-15},{95,20},{150,20},                                       // the sports ground
            {60,120},{120,120},{90,140},{45,140},                                      // the battle ground by the sea
            {-120,104},{-160,100},{-36,110},{-30,70},{30,78},                          // the harbour, the beach, the pines, the sea station
            {40,-90},{40,-75},{-120,-100},{120,-100},                                  // the supermarket's car park, the fields
            {0,-184},{0,166},{-64,-100},{64,10},{-179,-40},{179,40}};                  // the shrine, the island, the avenues, the ring roads
        List<int[]> out=new ArrayList<>();
        for(int[] w:want){
            // the open ground at the spot, or the nearest clear ground round it
            int[] found=null;
            search:for(int r=0;r<=5;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
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
