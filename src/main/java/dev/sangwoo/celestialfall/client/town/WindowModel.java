package dev.sangwoo.celestialfall.client.town;

import dev.sangwoo.celestialfall.town.TownBlocks;
import dev.sangwoo.celestialfall.town.TownBlocks.Window;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import java.util.function.Function;

/**
 * A window as the photos' houses have them (2.48.0): an aluminium sash frame flush with the wall, its members a
 * sixteenth and a half wide, the glass set a little back in it with a sheen of sky, curtains behind it where the
 * block has them, a sill under it and a drip over it, and the same frame on the room's side. Sashes beside, above
 * and below one another join into one window: the members between them narrow to half, the sill and the drip run
 * on, and a sash on its own gets the meeting stile of a sliding window down its middle. The wall's own face shows
 * as the reveal between the two frames.
 */
final class WindowModel {
    /** The outer frame's depth into the wall, how far behind the face the glass and the curtain sit. */
    static final double FRAME=2.5,GLASS=1.6,CURTAIN=2.1,MEMBER=1.5,HALF=0.75,STILE=0.75;

    static void register(){
        ModelLoadingPlugin.register(plugin->{
            for(var e:TownBlocks.WINDOWS.entrySet()){var look=e.getValue();CodeModel.resolve(plugin,e.getKey(),s->sp->build(sp,look,s));}
        });
    }

    static BakedModel build(Function<String,Sprite> sp,String[] look,BlockState s){
        Sprite frame=sp.apply(look[0]),glass=sp.apply(look[1]),curtain=look[2]==null?null:sp.apply(look[2]);
        boolean up=s.get(Window.UP),down=s.get(Window.DOWN),left=s.get(Window.LEFT),right=s.get(Window.RIGHT);
        Mesh m=new Mesh(s.get(Window.FACING));
        double l=left?HALF:MEMBER,r=right?16-HALF:16-MEMBER,b=down?HALF:MEMBER,t=up?16-HALF:16-MEMBER;
        for(double[] z:new double[][]{{0,FRAME},{16-FRAME,16}}){
            m.box(new Mesh.Box(0,0,z[0],l,16,z[1]).all(frame));
            m.box(new Mesh.Box(r,0,z[0],16,16,z[1]).all(frame));
            m.box(new Mesh.Box(l,0,z[0],r,b,z[1]).all(frame));
            m.box(new Mesh.Box(l,t,z[0],r,16,z[1]).all(frame));
            if(!left&&!right){
                // a sash on its own: the meeting stile where its two sliding halves overlap
                double z0=z[0]==0?0.4:16-FRAME+0.3,z1=z[0]==0?FRAME-0.3:15.6;
                m.box(new Mesh.Box(8-STILE,b,z0,8+STILE,t,z1).all(frame));
            }
        }
        float[] uv={(float)l,(float)(16-t),(float)r,(float)(16-b)};
        pane(m,glass,l,b,r,t,GLASS,uv);
        if(curtain!=null)pane(m,curtain,l,b,r,t,CURTAIN,uv);
        pane(m,glass,l,b,r,t,16-GLASS,uv);
        // the sill and the drip, running on along joined sashes
        if(!down)m.box(new Mesh.Box(0,0,-1.5,16,1.25,FRAME+0.5).all(frame));
        if(!up)m.box(new Mesh.Box(0,15,-1,16,16,FRAME).all(frame));
        return m.bake(frame,false);
    }
    /** A sheet across the opening at a depth, seen from both sides, the sprite laid as over the block's face. */
    private static void pane(Mesh m,Sprite s,double l,double b,double r,double t,double z,float[] uv){
        m.card(s,new double[][]{{l,t,z},{l,b,z},{r,b,z},{r,t,z}},new float[][]{{uv[0],uv[1]},{uv[0],uv[3]},{uv[2],uv[3]},{uv[2],uv[1]}});
    }
}
