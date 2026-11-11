package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.xchara.GlitchFx.*;

/**
 * The light about X Chara's hack in the world (2.115.0; drawn with the effects, KagenoRenderer, on the knife and the panel
 * where they were drawn this frame: HackMode): a soft purple glow along the knife (the effect layer's bloom makes its
 * rim shine), small pixels of purple rising off its blade and blinking out, a burst of pixels off the knife and off the
 * panel as it comes on and as it goes (the video's blocks breaking about them), a soft light about the panel, and a
 * little purple light on the world round both. Everything a function of the hack's own clock.
 */
public final class HackFx {
    private HackFx(){}
    /** One player's hack this frame: its look, and where its knife (its heel's middle, its point, edge to spine) and its panel (its middle, half its width and depth, its face) are; either may be null. */
    record Shown(HackLook.Frame f,Vec3d heel,Vec3d tip,Vec3d spine,Vec3d panel,Vec3d across,Vec3d along,Vec3d face){}
    private static List<Shown> shown=List.of();
    /** Pixels rising off the blade at once, and off the knife and the panel as the hack comes on or goes. */
    static final int MOTES=9,BURST_KNIFE=16,BURST_PANEL=11;

    /** Every frame before the effects: whose hack shows in the world. True if any does. */
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        shown=HackMode.shown(mc,delta,camera);
        return !shown.isEmpty();
    }
    /** Mote {@code k} of a knife at {@code clock}: where it is along the blade (0 heel, 1 point) and across it (-.5 edge, .5 spine), how far it has risen (blocks), its half size, and how much of it shows. */
    static double[] mote(int k,int seed,float clock){
        double every=.7+.5*h(k,0,seed,31),turn=clock/every+h(k,0,seed,32);
        int cycle=(int)Math.floor(turn);float phase=(float)(turn-cycle);
        double along=.06+.9*h(k,cycle,seed,33),across=h(k,cycle,seed,34)-.5;
        double rise=phase*every*(.42+.3*h(k,cycle,seed,35)),half=.02+.018*h(k,cycle,seed,36);
        // there, whole, then a flicker over its last quarter
        float alpha=smooth(phase/.12f)*(phase<.75f?1:((int)Math.floor(clock*30+k)%2==0?1-smooth((phase-.75f)/.25f):0));
        return new double[]{along,across,rise,half,alpha};
    }
    private static void motes(EffectMesh m,Shown s,boolean light){
        float w=s.f.steady();if(w<=.01f)return;
        Vec3d run=s.tip.subtract(s.heel);
        for(int k=0;k<MOTES;k++){
            var d=mote(k,s.f.seed(),s.f.clock());float a=(float)d[4]*w;if(a<=.01f)continue;
            Vec3d p=s.heel.add(run.multiply(d[0])).add(s.spine.multiply(d[1]*(1-d[0]*.8))).add(0,d[2],0);
            float near=near(p,m.camera());if(near<=0)continue;
            var f=facing(m.camera(),p);int rgb=k%3==0?LILAC:k%3==1?PURPLE:GLOW;
            if(light)square(m,p,f,d[3]*2,GLOW,.3f*a*near);
            else{square(m,p,f,d[3]+.008,INK,.8f*a*near);square(m,p,f,d[3],rgb,a*near);}
        }
    }
    /**
     * The pixels thrown off as the hack comes on or goes: off the knife all along its blade and off the panel all over
     * its face (each from its own place: thrown from one point they pile up into a blob of light), up and out, as a blow
     * of the knife throws them (GlitchFx.pixel).
     */
    private static void burst(EffectMesh m,Shown s,boolean light){
        float t=s.f.t();if(t>=PIXEL_LIFE+.05f)return;
        int seed=s.f.seed()*31+(s.f.on()?7:13);
        Vec3d up=new Vec3d(0,1,0);
        if(s.heel!=null){
            Vec3d run=s.tip.subtract(s.heel);
            for(int j=0;j<BURST_KNIFE;j++){
                double along=.04+.94*h(j,1,seed,41),across=(h(j,1,seed,42)-.5)*(1-along*.8);
                dot(m,GlitchFx.pixel(s.heel.add(run.multiply(along)).add(s.spine.multiply(across)),up,.95,s.f.on()?3.0:2.2,.04,j,seed,t),light);
            }
        }
        if(s.panel!=null)for(int j=0;j<BURST_PANEL;j++){
            Vec3d from=s.panel.add(s.across.multiply(2*h(j,2,seed,43)-1)).add(s.along.multiply(2*h(j,2,seed,44)-1));
            dot(m,GlitchFx.pixel(from,s.face.add(0,.4,0).normalize(),.85,s.f.on()?2.6:1.9,.038,j,seed+5,t),light);
        }
    }
    private static void dot(EffectMesh m,Pixel p,boolean light){
        if(p==null||p.alpha()<=0)return;
        float near=near(p.at(),m.camera());if(near<=0)return;
        var f=facing(m.camera(),p.at());
        if(light){if(p.rgb()!=INK)square(m,p.at(),f,p.half()*1.9,p.rgb()==WHITE?GLOW:p.rgb(),.3f*near);}
        else{square(m,p.at(),f,p.half()+.012,INK,.9f*near);square(m,p.at(),f,p.half(),p.rgb(),near);}
    }
    /** The blended pass: the pixels' bodies. */
    public static void alpha(EffectMesh m){
        for(var s:shown){if(s.heel!=null)motes(m,s,false);burst(m,s,false);}
    }
    /** The light, added. */
    public static void additive(EffectMesh m){
        for(var s:shown){
            float w=s.f.steady(),breath=.86f+.14f*(float)Math.sin(s.f.clock()*3.8f);
            if(s.heel!=null){
                float near=near(s.heel,m.camera());
                // the glow along the knife, flaring as it comes on
                float flare=s.f.on()?1-smooth(s.f.t()/.3f):0;
                m.softLine(s.heel,s.tip,.62,GLOW,(.2f*w*breath+.3f*flare)*near);
                motes(m,s,true);
            }
            if(s.panel!=null){
                float near=near(s.panel,m.camera());
                m.softDisc(s.panel.add(s.face.multiply(.04)),.2+s.across.length()*.9,GLOW,.14f*w*breath*near);
            }
            burst(m,s,true);
        }
    }
    /** A little purple light on the world round the knife and the panel. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var s:shown){
            float w=s.f.steady();if(w<=.01f)continue;
            if(s.heel!=null)f.light(s.heel.add(s.tip.subtract(s.heel).multiply(.5)),GLOW,2.6,.3f*w);
            if(s.panel!=null)f.light(s.panel,GLOW,1.8,.2f*w);
        }
    }
}
