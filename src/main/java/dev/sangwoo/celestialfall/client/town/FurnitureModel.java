package dev.sangwoo.celestialfall.client.town;

import dev.sangwoo.celestialfall.town.TownBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import java.util.function.Function;

/**
 * The street's furniture built in code (2.48.0), as the photos show it: drinks machines (red, white) with their lit
 * display of cans set back behind a sheet of glass, the row of buttons and the coin panel under it, the flap of the
 * tray and a plinth; an air conditioner's outdoor unit with its round fan guard standing proud of the face, its
 * louvres and the pipes into the wall; and the traffic mirror at a blind corner, a round convex glass in an orange
 * rim on its post, tipped a little down to the road.
 */
final class FurnitureModel {
    static void register(){
        ModelLoadingPlugin.register(plugin->{
            CodeModel.resolve(plugin,TownBlocks.VENDING_MACHINE,s->sp->vending(sp,s,"red"));
            CodeModel.resolve(plugin,TownBlocks.VENDING_MACHINE_WHITE,s->sp->vending(sp,s,"white"));
            CodeModel.resolve(plugin,TownBlocks.AIR_CONDITIONER,s->sp->airCon(sp,s));
            CodeModel.resolve(plugin,TownBlocks.TRAFFIC_MIRROR,s->sp->mirror(sp,s));
        });
    }

    /** The display's opening in the front: its sides, and its top and bottom in the machine's two blocks. */
    static final double DX0=1.5,DX1=14.5,DY_LOW=11,DY_UP=11,BACK=3.5,SHEET=2.3;

    static BakedModel vending(Function<String,Sprite> sp,BlockState s,String colour){
        Sprite side=sp.apply("vending_side_"+colour),lower=sp.apply("vending_lower_"+colour),upper=sp.apply("vending_upper_"+colour),top=sp.apply("vending_top"),dark=sp.apply("vending_dark"),
            display=sp.apply("vending_display"),glass=sp.apply("vending_glass");
        boolean high=s.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER;
        Mesh m=new Mesh(s.get(Properties.HORIZONTAL_FACING));
        if(!high){
            m.box(new Mesh.Box(1,0,3,15,1.5,15).all(dark));
            // the body without its front, which is pieced round the display's opening
            m.box(new Mesh.Box(0,1.5,2,16,16,16).all(side).face(Direction.DOWN,top).skip(Direction.NORTH,Direction.UP));
            m.face(lower,Direction.NORTH,new Mesh.Box(0,1.5,2,DX0,16,16));
            m.face(lower,Direction.NORTH,new Mesh.Box(DX1,1.5,2,16,16,16));
            m.face(lower,Direction.NORTH,new Mesh.Box(DX0,1.5,2,DX1,DY_LOW,16));
            // the opening: its floor and sides, the cans at its back (the display's lower rows), the glass over it
            m.face(dark,Direction.UP,new Mesh.Box(DX0,DY_LOW,2,DX1,DY_LOW,BACK));
            m.face(dark,Direction.EAST,new Mesh.Box(DX0,DY_LOW,2,DX0,16,BACK));
            m.face(dark,Direction.WEST,new Mesh.Box(DX1,DY_LOW,2,DX1,16,BACK));
            m.face(display,Direction.NORTH,new Mesh.Box(DX0,DY_LOW,BACK,DX1,16,16).face(Direction.NORTH,display,DX0,DY_UP,DX1,16));
            m.card(glass,new double[][]{{DX0,16,SHEET},{DX0,DY_LOW,SHEET},{DX1,DY_LOW,SHEET},{DX1,16,SHEET}},new float[][]{{(float)DX0,0},{(float)DX0,(float)(16-DY_LOW)},{(float)DX1,(float)(16-DY_LOW)},{(float)DX1,0}});
            // the tray's flap and the coin panel standing proud of the front
            m.box(new Mesh.Box(3,2.5,1,13,6.5,2).all(dark));
            m.box(new Mesh.Box(4,3.5,0.6,12,5.5,1).all(dark));
            m.box(new Mesh.Box(10.5,7,1.4,14.5,10.5,2).all(top));
        }else{
            m.box(new Mesh.Box(0,0,2,16,13,16).all(side).face(Direction.UP,top).skip(Direction.NORTH,Direction.DOWN));
            m.face(upper,Direction.NORTH,new Mesh.Box(0,0,2,DX0,13,16));
            m.face(upper,Direction.NORTH,new Mesh.Box(DX1,0,2,16,13,16));
            m.face(upper,Direction.NORTH,new Mesh.Box(DX0,DY_UP,2,DX1,13,16));
            m.face(dark,Direction.DOWN,new Mesh.Box(DX0,DY_UP,2,DX1,DY_UP,BACK));
            m.face(dark,Direction.EAST,new Mesh.Box(DX0,0,2,DX0,DY_UP,BACK));
            m.face(dark,Direction.WEST,new Mesh.Box(DX1,0,2,DX1,DY_UP,BACK));
            m.face(display,Direction.NORTH,new Mesh.Box(DX0,0,BACK,DX1,DY_UP,16).face(Direction.NORTH,display,DX0,0,DX1,DY_UP));
            m.card(glass,new double[][]{{DX0,DY_UP,SHEET},{DX0,0,SHEET},{DX1,0,SHEET},{DX1,DY_UP,SHEET}},new float[][]{{(float)DX0,0},{(float)DX0,(float)DY_UP},{(float)DX1,(float)DY_UP},{(float)DX1,0}});
        }
        return m.bake(side,true);
    }

    static BakedModel airCon(Function<String,Sprite> sp,BlockState s){
        Sprite front=sp.apply("ac_front"),side=sp.apply("ac_side"),guard=sp.apply("ac_guard"),steel=sp.apply("steel");
        Mesh m=new Mesh(s.get(Properties.HORIZONTAL_FACING));
        m.box(new Mesh.Box(1,0,11,15,10,16).all(side).face(Direction.NORTH,front));
        // the fan's guard, a hand's width proud of the face over the fan's well; two feet; the pipes into the wall
        m.disc(guard,Direction.Axis.Z,5.5,5,4.3,10.6,16,false);
        m.box(new Mesh.Box(2,0,10.4,3,10,11).all(side));
        m.box(new Mesh.Box(13,0,10.4,14,10,11).all(side));
        m.box(new Mesh.Box(14,1,13,16,2,14).all(steel));
        m.box(new Mesh.Box(14,2.5,13,16,3.5,14).all(steel));
        return m.bake(side,true);
    }

    static BakedModel mirror(Function<String,Sprite> sp,BlockState s){
        Sprite orange=sp.apply("mirror_orange"),face=sp.apply("mirror_face"),steel=sp.apply("steel");
        Mesh m=new Mesh(s.get(Properties.HORIZONTAL_FACING));
        m.cylinder(orange,orange,Direction.Axis.Y,8,8,0.9,0,8.5,16,false,true);
        m.box(new Mesh.Box(7,6.5,7.6,9,10.5,8.6).all(steel));
        // the glass in its rim, tipped ten degrees down to the road
        double cy=9.5,cz=7;
        double c=Math.cos(Math.toRadians(10)),sn=Math.sin(Math.toRadians(10));
        m.warp(p->new double[]{p[0],cy+(p[1]-cy)*c+(p[2]-cz)*sn,cz-(p[1]-cy)*sn+(p[2]-cz)*c});
        m.cylinder(orange,orange,Direction.Axis.Z,8,cy,7,cz-1.1,cz+0.5,16,false,true);
        m.disc(face,Direction.Axis.Z,8,cy,6.4,cz-1.0,16,false);
        m.warp(null);
        return m.bake(orange,true);
    }
}
