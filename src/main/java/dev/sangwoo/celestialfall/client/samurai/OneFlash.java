package dev.sangwoo.celestialfall.client.samurai;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.samurai.SamuraiRules.SLASH_SEC;

/**
 * The samurai's G, the one flash, drawn (2.143.0, through the effect layer, from its cast's clock: SamuraiClient; the
 * user's video for every look, its seconds). For everyone near, in the world: the quick strokes round its user's feet as
 * they stand over the sheathed sword; the burst (a white ring, white and red light, the character for death in gold
 * before them, red bolts thrown off, black and red stars, smoke along the ground, then a red cross on them and a thin red
 * beam up); the streak of the dash and the cut made in passing; the flourish's white crescents where the blade goes, its
 * stars (the big ones split into colours), the black ink spiked up behind it, the wind turning about the two; the light
 * off the blade as it is laid at the scabbard's mouth and the flash as it goes home; the four cuts through the one marked
 * where they stand; and the pillar of light as the world comes back. For its two, in its cutscene's own frames
 * (SamuraiClient.cinematic): the wind cut past the one marked in the grey world and the ring closing on them; the black
 * ground's grid and horizon, the four blades waiting round them, and each cut's band and burst.
 * <p>
 * White is laid over the world (the blended pass) so it stays white on a bright sky; its glow is added on it; black is
 * drawn after all the light ({@link #shaders}), as X Chara's sword dance's is.
 */
public final class OneFlash implements Fx.Skill {
    static final int WHITE=0xffffff,INK=0x05060a,RED=0xff1c2c,DEEP_RED=0x8c0612,PINK=0xffb0b8,GOLD=0xffc820,PALE=0xeef4ff;
    static final Vec3d UP=new Vec3d(0,1,0);

    @Override public boolean layered(){return true;}
    @Override public float lifetime(){return SamuraiKata.LENGTH+1.2f;}
    @Override public Anim clip(int combo){return SamuraiKata.CLIP;}
    /** No afterimages of the body (the video has none). */
    @Override public float afterimages(float sec){return 0;}

    /** One frame's view of a G: its clock, where its two are drawn, and the frames of its user's body and of the side shots. */
    private static final class View {
        final SamuraiClient.Flash d;final float sec;final boolean cine;final SamuraiScene.Part part;
        /** Its user's feet as drawn and the way their body faces; the one marked's feet. */
        final Vec3d feet,face,hand,hold;final float facing;
        /** The side shots' frame: to its right, and away from the eye. */
        final Vec3d sr,sl;
        View(Fx c,SamuraiClient.Flash d){
            this.d=d;sec=c.sec;cine=SamuraiClient.cinematic()==d;part=cine?SamuraiScene.part(sec):null;
            float t=c.age;facing=d.facing(t);face=Vec3d.fromPolar(0,facing);hand=new Vec3d(-face.z,0,face.x);
            feet=!d.inScene()||sec<SamuraiScene.DASH?d.from:cine?SamuraiScene.userFeet(d.spot,d.hold,d.f,d.side,sec):d.spot;
            hold=d.hold;sr=SamuraiScene.sideRight(d.f,d.side);sl=SamuraiScene.sideLook(d.f,d.side);
        }
        /** A point by its user: ahead of them (the way they face), up, and to their right. */
        Vec3d by(double fw,double up,double rt){return feet.add(face.multiply(fw)).add(0,up,0).add(hand.multiply(rt));}
        boolean flat(){return part!=null&&SamuraiScene.flat(part);}
        /** Whether what is drawn in the world shows in this frame (not on a flat frame of the cutscene). */
        boolean world(){return !flat();}
        boolean live(float at){return d.runs(at*20);}
    }
    private static View view(Fx c){var d=SamuraiClient.ofCast(c.cast);return d==null?null:new View(c,d);}

    // ───────────────────────────── the passes ─────────────────────────────

    @Override public void alpha(Fx c,EffectMesh m){
        var v=view(c);if(v==null)return;
        if(v.world()){
            before(v,m,false);burst(c,v,m,false);dash(v,m,false);flourish(c,v,m,false);
            if(v.cine&&v.part==SamuraiScene.Part.GREY_WORLD)grey(c,v,m,false);
            if(!v.cine)cuts(c,v,m,false);
            pillar(v,m,false);
        }else{
            if(v.part==SamuraiScene.Part.RING_WHITE)blades(v,m,0xd6d6d6);
            if(v.part==SamuraiScene.Part.VOID_GROUND){ground(c,v,m);blades(v,m,WHITE);cuts(c,v,m,false);}
        }
    }
    @Override public void additive(Fx c,EffectMesh m){
        var v=view(c);if(v==null)return;
        if(v.world()){
            before(v,m,true);burst(c,v,m,true);dash(v,m,true);flourish(c,v,m,true);
            if(v.cine&&v.part==SamuraiScene.Part.GREY_WORLD)grey(c,v,m,true);
            if(!v.cine)cuts(c,v,m,true);
            pillar(v,m,true);
        }else if(v.part==SamuraiScene.Part.VOID_GROUND)cuts(c,v,m,true);
    }
    /**
     * Its flat white, drawn straight onto the frame after the effect layer (the pass over the full-screen flash): the
     * blade's crescents and the white spikes beside the ink. On the layer a broad white over a bright sky comes out cream
     * (what it adds to a blue sky is warm, and the layer's bloom adds that again before its light is brought back under
     * white); here it is the one flat white of the video's.
     */
    @Override public void overFlash(Fx c,EffectMesh m,boolean additive){
        if(additive)return;
        var v=view(c);if(v==null||!v.world())return;
        float s=v.sec;if(!v.d.inScene()||s<SamuraiScene.DASH||s>=SamuraiScene.FLASH||!v.live(SamuraiScene.DASH))return;
        // the blade's white crescents: what it swept in the last moments, as strong as it is fast
        if(s<SamuraiKata.NOTO-.06f)sweep(v,m,s,false);
        // the white spikes beside the ink (its light side)
        for(int k=0;k<INKS.length;k++){
            float[] g=INKS[k];float a=s-g[0];if(a<0||a>=g[1])continue;
            Vec3d foot=v.by(g[2],g[3],g[4]);
            Vec3d across=c.camera.subtract(foot).crossProduct(UP);if(across.lengthSquared()<1e-5)continue;across=across.normalize();
            double off=(k%2==0?1:-1)*(g[5]*.75+.22);
            for(int j=0;j<6;j++){
                double x=off+(j-2.5)*.1+.05*SamuraiScene.hash(j,k*7+3),tall=g[6]*(.3+.6*SamuraiScene.hash(j,k*7+4));
                Vec3d base=foot.add(across.multiply(x)).add(0,g[6]*.5+g[6]*.2*(SamuraiScene.hash(j,k*7+6)-.5),0);
                m.needle(base.subtract(0,tall*.5,0),base.add(0,tall*.5,0),across,.1+.07*SamuraiScene.hash(j,k*7+5),.3+.4*SamuraiScene.hash(j,k*7+7),WHITE,.97f);
            }
        }
    }
    /** Its black, over all its light: a batch of its own after the light, on the effect layer or not (SwordDance.shaders' way). */
    @Override public void shaders(Fx c){
        var v=view(c);if(v==null||!v.world()||v.sec>3.3f)return;
        var buf=net.minecraft.client.render.Tessellator.getInstance().getBuffer();
        buf.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS,net.minecraft.client.render.VertexFormats.POSITION_COLOR);
        black(c,v,new EffectMesh(buf,c.matrix,c.camera));
        var built=buf.end();
        if(dev.sangwoo.celestialfall.client.KagenoRenderer.layering())dev.sangwoo.celestialfall.client.vfx.FxLayer.draw(built,false,1,.2f);
        else{
            com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(built);
        }
    }
    @Override public void frame(Fx c,FxFrame f){
        var v=view(c);if(v==null)return;float s=v.sec;
        // a flat frame: nothing of the frame before left glowing on it
        if(v.flat()){f.afterglow(.01f);return;}
        if(v.cine&&(v.part==SamuraiScene.Part.GREY_WORLD||s<SamuraiScene.CLOSE+.1f))f.afterglow(.01f);
        if(s>=SamuraiKata.BURST&&s<SamuraiScene.DASH&&v.live(SamuraiKata.BURST)){
            float u=(s-SamuraiKata.BURST)/.4f;
            f.light(v.d.from.add(0,1,0),0xff4050,11,2.2f*(1-u*.6f));
        }
        if(v.d.inScene()&&s>=SamuraiScene.CLOSE&&s<SamuraiScene.FLASH){
            // its shapes are crisp, as the video's: no afterglow (over a bright sky what white adds is warm, and lingers cream); the big stars split the light
            f.afterglow(.01f);
            float chroma=0;for(float[] g:STARS)if(g[6]>0){float a=s-g[0];if(a>=0&&a<g[1])chroma=Math.max(chroma,.006f*(1-a/g[1]));}
            if(chroma>0)f.chroma(chroma);
            float lit=noto(s);
            if(lit>0){var hb=SamuraiKata.blade(v.feet,v.facing,s);f.light(hb[0].add(hb[1].multiply(SamuraiKata.TIP)),0xf4f8ff,7,1.2f*lit);}
        }
        if(v.d.inScene()&&!v.d.stopped()&&s>=SamuraiKata.SNAP&&s<SamuraiKata.SNAP+.7f){
            float u=(s-SamuraiKata.SNAP)/.7f;
            f.light(v.hold.add(0,1.2,0),0xffffff,16,2.6f*(1-u));
            f.shaft(v.hold.add(0,3,0),0xffffff,.8f*(1-u));
        }
    }
    @Override public float shake(StandClient.Cast cast,float sec){
        var d=SamuraiClient.ofCast(cast);if(d==null||SamuraiClient.cinematic()==d)return 0;
        float v=0,a=sec-SamuraiKata.BURST-.03f;
        if(a>=0&&a<.3f&&d.runs(SamuraiKata.BURST*20))v=Math.max(v,1.2f*(1-a/.3f));
        if(!d.inScene()||d.stopped())return v;
        for(float at:SLASH_SEC){a=sec-at;if(a>=0&&a<.15f)v=Math.max(v,.9f*(1-a/.15f));}
        for(float[] cut:SamuraiKata.CUTS){a=sec-cut[0];if(a>=0&&a<.14f)v=Math.max(v,.45f*cut[1]*(1-a/.14f));}
        a=sec-SamuraiKata.SNAP;if(a>=0&&a<.45f)v=Math.max(v,2.2f*(1-a/.45f));
        return v;
    }
    /** The cutscene's camera's jolts at {@code sec}: {yaw, pitch, roll} degrees (each comes in over a frame or two and dies away). */
    static float[] jolt(float sec){
        float yaw=0,pitch=0,roll=0;
        float[][] hits={{SamuraiScene.CLOSE,1.5f},{SamuraiKata.SPIN,.5f},{SamuraiKata.WHIRL,1.4f},{SamuraiKata.CLICK,1.1f},{SLASH_SEC[0],1.6f},{SLASH_SEC[1],1.8f},{SLASH_SEC[2],1.8f},{SLASH_SEC[3],2.2f}};
        for(int k=0;k<hits.length;k++){
            float a=sec-hits[k][0];if(a<0||a>.3f)continue;
            float e=FxLib.kick(sec,hits[k][0],.05f)*hits[k][1];
            yaw+=e*(float)Math.sin(k*2.4+a*70)*.8f;pitch+=e*(float)Math.cos(k*1.7+a*83)*.6f;roll+=e*(float)Math.sin(k*3.1+a*61)*.9f;
        }
        return new float[]{yaw,pitch,roll};
    }

    // ───────────────────────────── before the dash ─────────────────────────────

    /** The ring of ink brushed on the ground round its user as they sink over the sword: its radius, how far round it goes, and when its brush goes round (seconds). */
    static final double ENSO=1.55,ENSO_ROUND=Math.toRadians(332);
    static final float ENSO_FROM=.06f,ENSO_TO=.5f;
    /** How far round the ring's brush has gone at {@code sec} (0 to 1), and how much of the ring is there at all (it is scattered by the dash). */
    static float ensoDrawn(float sec){return SamuraiScene.smooth((sec-ENSO_FROM)/(ENSO_TO-ENSO_FROM));}
    static float ensoThere(float sec){return 1-SamuraiScene.smooth((sec-(SamuraiScene.DASH-.04f))/.1f);}
    /** The brush's width {@code u} of the way round (blocks): set down full, drawn thin, lifted to nothing. */
    static double ensoWidth(double u){return .3*(.18+.82*Math.pow(Math.sin(Math.PI*Math.pow(u,.62)),.8));}
    /** A point of the ring {@code u} of the way round, {@code off} blocks out from its line. */
    private static Vec3d enso(View v,double u,double off){
        double a=Math.toRadians(-150)+ENSO_ROUND*u,r=ENSO+off+.05*Math.sin(u*9.2);
        return v.d.from.add(v.face.multiply(Math.cos(a)*r)).add(v.hand.multiply(Math.sin(a)*r)).add(0,.035,0);
    }
    /** The ring's band from {@code u0} to {@code u1} of the way round, {@code part} of its brush's width, one colour. */
    private static void ensoBand(View v,EffectMesh m,double u0,double u1,double part,int rgb,float alpha){
        if(alpha<=0||u1<=u0)return;int n=Math.max(2,(int)(54*(u1-u0)));
        for(int i=0;i<n;i++){
            double a=u0+(u1-u0)*i/n,b=u0+(u1-u0)*(i+1)/n,wa=ensoWidth(a)*part*.5,wb=ensoWidth(b)*part*.5;
            m.quad(enso(v,a,-wa),enso(v,a,wa),enso(v,b,wb),enso(v,b,-wb),rgb,alpha);
        }
    }
    /** Before the dash: the ring's brush head and its pale line (the ink itself is over the light: black), flecks lifting off it, red once the sword is broken out. */
    private static void before(View v,EffectMesh m,boolean light){
        float s=v.sec;if(s>=SamuraiScene.DASH+.08f||!v.live(0))return;
        float drawn=ensoDrawn(s),there=ensoThere(s);if(drawn<=0||there<=0)return;
        float red=SamuraiScene.smooth((s-SamuraiKata.BURST)/.05f);
        if(light){
            // the brush's head, wet; and the ring lit red from within once the sword is out
            if(drawn<1)m.softDisc(enso(v,drawn,0).add(0,.05,0),.5,PALE,.5f);
            if(red>0){FxLib.ring(m,v.d.from.add(0,.05,0),ENSO,.55,RED,.5f*red*there);m.softDisc(v.d.from.add(0,.3,0),2.2,RED,.2f*red*there);}
        }else{
            // a pale line down the middle of the wet ink, gone as it dries; the head
            float wet=1-SamuraiScene.smooth((s-ENSO_TO)/.25f);
            if(wet>0)ensoBand(v,m,Math.max(0,drawn-.35),drawn,.16,WHITE,.8f*wet);
            if(red>0)ensoBand(v,m,0,1,.22,0xff4a56,.9f*red*there);
            // flecks lifted off the ring, turning red and quicker with the sword out
            for(int k=0;k<18;k++){
                float born=.14f+.045f*k,life=.55f;float a=((s-born)%life+life)%life;if(s<born)continue;
                double u=SamuraiScene.hash(k,301),rise=(1.1+1.2*SamuraiScene.hash(k,302))*(a/life)*(1+1.4*red),size=.035+.035*SamuraiScene.hash(k,303);
                Vec3d p=enso(v,u,0).add(0,rise,0).add(v.hand.multiply(.12*Math.sin(a*9+k)));
                Vec3d x=m.camera().subtract(p).crossProduct(UP);if(x.lengthSquared()<1e-6)continue;x=x.normalize().multiply(size);
                if(red>.5f)m.quad(p.add(x),p.add(0,size*1.7,0),p.subtract(x),p.subtract(0,size*1.7,0),0xe01426,.9f*(1-a/life)*there);
            }
        }
    }
    /** A level way out from its user: {@code degrees} to the right of the way they face. */
    private static Vec3d dir(View v,double degrees){
        double a=Math.toRadians(degrees);return v.face.multiply(Math.cos(a)).add(v.hand.multiply(Math.sin(a)));
    }

    // ───────────────────────────── the burst ─────────────────────────────

    private static void burst(Fx c,View v,EffectMesh m,boolean light){
        float s=v.sec,a=s-SamuraiKata.BURST;if(a<0||s>=SamuraiScene.DASH+.05f||!v.live(SamuraiKata.BURST))return;
        Vec3d mid=v.d.from.add(0,1.0,0);int seed=(int)(v.d.cast.start()*31);
        Vec3d n=c.camera.subtract(mid);n=n.lengthSquared()<1e-6?UP:n.normalize();
        Vec3d right=n.crossProduct(UP);if(right.lengthSquared()<1e-4)right=new Vec3d(1,0,0);right=right.normalize();Vec3d up=right.crossProduct(n).normalize();
        // the white ring that opens it
        if(a<.08f){
            float u=a/.08f;
            if(!light)FxLib.ringPlane(m,mid,right,up,.45+1.0*u,.16,WHITE,1-u*u);
        }
        float b=a-.03f;
        if(b>=0){
            // white light out of them, and red within it
            float white=Math.max(0,1-b/.13f),red=Math.max(0,1-b/.36f);
            if(light){
                m.softDisc(mid,2.6+1.2*b,WHITE,.85f*white);
                m.softDisc(mid,1.9+.6*b,RED,.75f*red);m.softDisc(mid,3.4,RED,.22f*red);
            }else{
                // white blades of light thrown out all round, short-lived
                for(int k=0;k<12;k++){
                    double ang=k*Math.PI*2/12+SamuraiScene.hash(k,seed)*.5;
                    double len=(1.5+2.2*SamuraiScene.hash(k,seed+1))*(.5+.5*Math.min(1,b/.05));
                    Vec3d way=right.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang)));
                    m.needle(mid.add(way.multiply(.35)),mid.add(way.multiply(len)),n.crossProduct(way),.5+.4*SamuraiScene.hash(k,seed+2),.3,WHITE,.95f*white);
                }
            }
            // red bolts thrown off, turning as they go
            float bolt=b/.34f;
            if(bolt<1)for(int k=0;k<10;k++){
                double ang=k*Math.PI*2/10+SamuraiScene.hash(k,seed+5)*.7,el=(SamuraiScene.hash(k,seed+6)-.35)*1.1;
                Vec3d way=new Vec3d(Math.cos(ang)*Math.cos(el),Math.sin(el),Math.sin(ang)*Math.cos(el));
                double r0=.8+(3.2+1.6*SamuraiScene.hash(k,seed+8))*bolt,r1=r0+.55+.5*SamuraiScene.hash(k,seed+7);
                bolt(m,mid.add(way.multiply(r0)),mid.add(way.multiply(r1)),seed+k*17+(int)(s*30),.5f*(1-bolt*bolt)+.4f*(1-bolt),light);
            }
            // black and red stars about it
            float st=b-.05f;
            if(st>=0&&st<.14f){
                float u=st/.14f,size=(float)Math.sin(Math.PI*Math.min(1,u*1.15));
                for(int k=0;k<4;k++){
                    double ang=1.1+k*1.7+SamuraiScene.hash(k,seed+9),r=2.3+1.5*SamuraiScene.hash(k,seed+10);
                    Vec3d p=mid.add(right.multiply(Math.cos(ang)*r)).add(up.multiply(Math.sin(ang)*r*.6));
                    if(light)glint(m,p,.75*size,RED,.9f,0);
                }
            }
            // smoke along the ground, spreading
            float sm=b-.06f;
            if(!light&&sm>=0){
                float u=Math.min(1,sm/.36f),on=1-u*u;
                for(int k=0;k<16;k++){
                    double ang=k*Math.PI*2/16+SamuraiScene.hash(k,seed+12),r=.9+1.9*u+.6*SamuraiScene.hash(k,seed+13);
                    m.softDisc(v.d.from.add(Math.cos(ang)*r,.25+.25*u,Math.sin(ang)*r),.7+.5*u,0xeef0f2,.75f*on);
                }
            }
        }
        // the sword broken an inch out of its scabbard: a glint at its guard, and a red line across the eye through it
        if(a<.16f&&light){
            float u=a/.16f,on=(1-u)*Math.min(1,a/.02f);
            var hb=SamuraiKata.blade(v.feet,v.facing,s);Vec3d guard=hb[0].add(hb[1].multiply(SamuraiKata.GUARD));
            glint(m,guard,1.1*(1-u*.5),WHITE,on,20);
            m.softLine(guard.subtract(right.multiply(5)),guard.add(right.multiply(5)),.09,RED,.8f*on);
            m.softLine(guard.subtract(right.multiply(2.2)),guard.add(right.multiply(2.2)),.05,WHITE,.8f*on);
        }
        // the sign before them: the character for death, gold, a red glow behind it
        float sg=a-.03f;
        if(sg>=0&&sg<.17f){
            float on=Math.min(1,sg/.02f)*(1-SamuraiScene.smooth((sg-.11f)/.06f));
            Vec3d at=mid.add(n.multiply(.55));double size=.6+.15*Math.min(1,sg/.04f);
            // (the sign itself is drawn over all the light: sign)
            if(light)m.softDisc(at,size*.9,RED,.8f*on);
        }
        // then a red cross on them, pale streaks out of it, and a thin red beam up
        float x=a-.18f;
        if(x>=0){
            float on=Math.min(1,x/.03f)*(1-SamuraiScene.smooth((s-(SamuraiScene.DASH-.06f))/.08f));
            for(int k=0;k<2;k++){
                Vec3d way=right.multiply(k==0?.72:-.72).add(up.multiply(.72));
                if(light)m.glowLine(mid.subtract(way.multiply(.95)),mid.add(way.multiply(.95)),.2,RED,.9f*on);
            }
            if(!light)for(int k=0;k<7;k++){
                double ang=.4+k*Math.PI*2/7+SamuraiScene.hash(k,seed+20)*.5;
                Vec3d way=right.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang)*.75));
                m.needle(mid.add(way.multiply(.3)),mid.add(way.multiply(1.9+1.1*SamuraiScene.hash(k,seed+21))),n.crossProduct(way),.34,.25,PINK,.5f*on);
            }
            if(light&&x>.03f){
                m.softTaper(mid.add(0,.4,0),mid.add(0,14,0),.5,.2,RED,.75f*on);
                m.softTaper(mid.add(0,.4,0),mid.add(0,14,0),.16,.06,0xffd0d0,.8f*on);
            }
        }
    }
    /** A jagged bolt of red: a dark heart within a bright rim (the blended pass), its glow (the light). */
    private static void bolt(EffectMesh m,Vec3d from,Vec3d to,int seed,float alpha,boolean light){
        if(alpha<=0)return;
        Vec3d prev=from,way=to.subtract(from);double len=way.length();if(len<1e-4)return;
        Vec3d side=way.crossProduct(UP);if(side.lengthSquared()<1e-6)side=new Vec3d(1,0,0);side=side.normalize();Vec3d over=side.crossProduct(way).normalize();
        int n=5;
        for(int i=1;i<=n;i++){
            Vec3d p=from.add(way.multiply(i/(double)n));
            if(i<n)p=p.add(side.multiply((SamuraiScene.hash(i,seed)-.5)*len*.45)).add(over.multiply((SamuraiScene.hash(i,seed+3)-.5)*len*.35));
            if(light)m.line(prev,p,.3,RED,.2f*alpha);
            else{m.line(prev,p,.11,RED,alpha);m.line(prev,p,.045,DEEP_RED,alpha);}
            prev=p;
        }
    }

    // ───────────────────────────── the dash ─────────────────────────────

    private static void dash(View v,EffectMesh m,boolean light){
        float s=v.sec,a=s-SamuraiScene.DASH;if(a<0||a>=.34f||!v.d.inScene()||!v.live(SamuraiScene.DASH))return;
        // its streak, from where its user stood to where they stand (not for its two: the dash is their flat frames)
        if(!v.cine&&a<.2f){
            float on=1-a/.2f;Vec3d from=v.d.from.add(0,1,0),to=v.d.spot.add(0,1,0);
            if(light)m.glowLine(from,to,.3,PALE,.6f*on);else m.needle(from,to,.5*on,.8,WHITE,.9f*on);
        }
        // the cut made in passing: a line through the one marked, low at one side to high at the other, and red off it
        float cut=s-(SamuraiScene.CLOSE-.03f);
        if(cut>=0&&cut<.16f){
            float on=1-SamuraiScene.smooth(cut/.16f);
            Vec3d across=v.hand.multiply(-1),mid=v.hold.add(0,1.05,0);
            Vec3d way=across.multiply(.62).add(0,.78,0);
            Vec3d lo=mid.subtract(way.multiply(2.6)),hi=mid.add(way.multiply(2.9));
            if(light){m.softLine(lo,hi,.7,PALE,.5f*on);}
            else{
                m.needle(lo,hi,.12+.22*on,.5,WHITE,on);
                for(int k=0;k<8;k++){
                    double ang=SamuraiScene.hash(k,71)*6.28,r=.3+1.4*cut/.16*(.5+SamuraiScene.hash(k,72));
                    Vec3d p=mid.add(across.multiply(Math.cos(ang)*r)).add(0,Math.sin(ang)*r*.8,0);
                    m.softDisc(p,.1+.14*SamuraiScene.hash(k,73),0xb00818,.85f*on);
                }
            }
        }
    }

    // ───────────────────────────── the flourish ─────────────────────────────

    /** The blade's point in its user's frame at {@code sec} of the clip: {ahead, up, to their right}. */
    static float[] tipBy(float sec){
        Vec3d t=SamuraiKata.tip(Vec3d.ZERO,0,sec);return new float[]{(float)t.z,(float)t.y,(float)-t.x};
    }
    /**
     * The cuts' stars, off the blade's point as each cut goes through its middle (SamuraiKata.CUTS): {when, how long,
     * ahead of its user, up, to their right, how big, whether it splits into colours}: one at the cut's middle (the big
     * cuts' split the light), a small one a moment before it and one after along the blade's path.
     */
    static final float[][] STARS=stars();
    private static float[][] stars(){
        var out=new java.util.ArrayList<float[]>();
        for(float[] c:SamuraiKata.CUTS){
            float[] at=tipBy(c[0]),was=tipBy(c[0]-.03f),will=tipBy(c[0]+.03f);
            out.add(new float[]{c[0]-.01f,.09f,at[0],at[1],at[2],.35f+.6f*c[1],c[1]>=1.1f?1:0});
            out.add(new float[]{c[0]-.03f,.06f,was[0],was[1],was[2],.3f*c[1],0});
            out.add(new float[]{c[0]+.035f,.07f,will[0],will[1],will[2],.38f*c[1],0});
        }
        return out.toArray(new float[0][]);
    }
    /**
     * The black ink spiked up behind the blade at each cut: {when, how long, ahead of its user, up (its foot), to their
     * right, how wide, how tall}: to the side the blade's point went through, behind them (never before their body).
     */
    static final float[][] INKS=inks();
    private static float[][] inks(){
        float[][] out=new float[SamuraiKata.CUTS.length][];
        for(int k=0;k<out.length;k++){
            float[] c=SamuraiKata.CUTS[k],at=tipBy(c[0]);
            out[k]=new float[]{c[0]+.015f,.075f,-.4f,Math.max(.12f,at[1]*.3f),at[2]*.75f,.42f*c[1],2f+.6f*c[1]};
        }
        return out;
    }
    /** A cut's line: through where the blade's point is at its middle, along the way the point goes there (the world; its user's feet and facing as drawn): {a point of it, its way}. */
    private static Vec3d[] cutLine(View v,float at){
        Vec3d p=SamuraiKata.tip(v.feet,v.facing,at),way=SamuraiKata.tip(v.feet,v.facing,at+.012f).subtract(SamuraiKata.tip(v.feet,v.facing,at-.012f));
        return new Vec3d[]{p,way.lengthSquared()<1e-8?v.hand:way.normalize()};
    }
    /** How long a cut's white line, its ink and its red stay (seconds). */
    static final float LINE_WHITE=.13f,LINE_INK=.2f,LINE_RED=.34f;

    private static void flourish(Fx c,View v,EffectMesh m,boolean light){
        float s=v.sec;if(!v.d.inScene()||s<SamuraiScene.DASH||s>=SamuraiScene.FLASH+.3f||!v.live(SamuraiScene.DASH))return;
        if(v.cine&&s>=SamuraiScene.FLASH)return;
        float out=1-SamuraiScene.smooth((s-SamuraiScene.FLASH)/.25f);
        // the red edge beyond the blade's crescent, a moment longer than its white
        if(!light&&s<SamuraiKata.NOTO)band(v,m,s,s-.13f,SWEEP_TO-.02,SWEEP_TO+RED_EDGE,0xe01426,.9f*out);
        Vec3d mid=v.d.spot.lerp(v.hold,.4).add(0,1,0);int seed=(int)(v.d.cast.start()*31);
        // the wind turning about the two: thin white arcs on a dome, thick with the dash's arrival and thinning
        if(!light){
            float thick=.12f+.4f*(1-SamuraiScene.smooth((s-SamuraiScene.CLOSE)/.5f));
            for(int k=0;k<16;k++){
                double tilt=(SamuraiScene.hash(k,seed+30)-.5)*1.5,turn=SamuraiScene.hash(k,seed+31)*6.28;
                Vec3d axis=new Vec3d(Math.sin(tilt)*Math.cos(turn),Math.cos(tilt),Math.sin(tilt)*Math.sin(turn));
                Vec3d u=axis.crossProduct(UP);if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();Vec3d w=axis.crossProduct(u);
                double r=1.9+1.1*SamuraiScene.hash(k,seed+32);
                arc(m,mid.add(0,(SamuraiScene.hash(k,seed+35)-.5)*1.2,0),u,w,r,s*(1.2+2*SamuraiScene.hash(k,seed+33))+k,.9+1.1*SamuraiScene.hash(k,seed+34),.022,WHITE,thick*out,16);
            }
            // mist low about their feet as they arrive
            float mist=1-SamuraiScene.smooth((s-SamuraiScene.CLOSE)/.45f);
            if(mist>0)for(int k=0;k<9;k++){
                double ang=k*.7+SamuraiScene.hash(k,seed+36),r=.8+1.8*SamuraiScene.hash(k,seed+37);
                m.softDisc(mid.add(Math.cos(ang)*r,-.7,Math.sin(ang)*r),.8,0xf2f4f6,.28f*mist);
            }
        }
        // its stars
        for(int k=0;k<STARS.length;k++){
            float[] g=STARS[k];float a=s-g[0];if(a<0||a>=g[1])continue;
            float u=a/g[1],size=g[5]*(float)Math.sin(Math.PI*Math.min(1,.25+u*.85));
            Vec3d p=v.by(g[2],g[3],g[4]);
            float on=1-u*u*u;
            if(g[6]>0){if(light)split(m,p,size,on);else{star(m,p,size,WHITE,on,0);m.softDisc(p,size*.3,WHITE,on);}}
            else if(light)m.softDisc(p,size*.45,WHITE,.4f*on);
            else star(m,p,size,WHITE,on,k%2==0?0:14);
        }
        // each cut's line, left in the air where the blade's point went through: white and thin for a moment, red after it
        for(int k=0;k<SamuraiKata.CUTS.length;k++){
            float[] g=SamuraiKata.CUTS[k];float a=s-g[0];if(a<0||a>=LINE_RED)continue;
            Vec3d[] line=cutLine(v,g[0]);double half=2.6*g[1]+.6;
            Vec3d from=line[0].subtract(line[1].multiply(half)),to=line[0].add(line[1].multiply(half*.8));
            if(light){
                float red=(1-a/LINE_RED)*Math.min(1,a/.03f);
                m.softLine(from,to,.16,RED,.7f*red*out);
                if(a<LINE_WHITE)m.softLine(from,to,.5*g[1],PALE,.35f*(1-a/LINE_WHITE)*out);
            }else if(a<LINE_WHITE){
                float u=a/LINE_WHITE;
                m.needle(from,to,(.05+.13*g[1])*(1-u*u)+.012,.55,WHITE,(1-u*u*u)*out);
            }
        }
        // the second spin: a ring thrown out level from them as the blade goes round through the one marked
        float wh=s-(SamuraiKata.WHIRL-.04f);
        if(wh>=0&&wh<.34f&&v.live(SamuraiKata.WHIRL)){
            float u=wh/.34f;Vec3d at=v.feet.add(0,1.25,0);
            double r=1.1+3.2*(1-(1-u)*(1-u));
            if(light)FxLib.ring(m,at,r,.7,RED,.4f*(1-u)*out);
            else{FxLib.ring(m,at,r,.13*(1-u)+.015,WHITE,(1-u*u)*out);FxLib.ring(m,at.add(0,-.5,0),r*.72,.05*(1-u)+.01,WHITE,.7f*(1-u)*out);}
        }
        // the one marked cut by it: red off them along the blade's way
        spray(v,m,light,SamuraiKata.WHIRL,97,out);
        // the sword laid at the scabbard's mouth: light off the blade, glints running down it, and the flash as it goes home
        float lit=noto(s);
        if(lit>0){
            var hb=SamuraiKata.blade(v.feet,v.facing,s);
            Vec3d mouth=hb[0].add(hb[1].multiply(SamuraiKata.TIP-(SamuraiKata.TIP-SamuraiKata.GUARD)*SamuraiKata.home(s)));
            Vec3d n=c.camera.subtract(mouth).normalize(),right=n.crossProduct(UP).normalize(),up=right.crossProduct(n).normalize();
            float flicker=.75f+.25f*(float)Math.sin(s*61)*(float)Math.sin(s*23+1);
            if(light){
                m.softDisc(mouth,1.0,WHITE,.26f*lit*flicker);m.softDisc(mouth,.4,WHITE,.8f*lit);
                // the long streak across the lens, and the one along the blade's line, split into colours at its ends
                m.softLine(mouth.subtract(right.multiply(5.5)),mouth.add(right.multiply(5.5)),.13,PALE,.5f*lit*flicker);
                Vec3d along=hb[1].subtract(n.multiply(hb[1].dotProduct(n)));along=along.lengthSquared()<1e-5?up:along.normalize();
                m.softLine(mouth.subtract(along.multiply(6)),mouth.add(along.multiply(2.5)),.12,0xfff0d8,.55f*lit);
                m.softLine(mouth.subtract(along.multiply(6)).add(up.multiply(.07)),mouth.subtract(along.multiply(2.2)).add(up.multiply(.07)),.1,0xff7a50,.35f*lit);
                m.softLine(mouth.subtract(along.multiply(6)).subtract(up.multiply(.07)),mouth.subtract(along.multiply(2.2)).subtract(up.multiply(.07)),.1,0x58b8ff,.35f*lit);
                // the lens's rings, in parts
                arc(m,mouth.add(right.multiply(-.5)),right,up,1.45,.4,2.4,.07,0x8cf0a0,.3f*lit,20);
                arc(m,mouth.add(right.multiply(-.9)),right,up,2.0,2.8,2.0,.08,0xffa8e0,.24f*lit,20);
                arc(m,mouth.add(right.multiply(-.2)),right,up,1.05,4.6,1.6,.05,0xfff2a0,.26f*lit,16);
                // rays
                for(int k=0;k<10;k++){
                    double ang=k*Math.PI*2/10+.3;Vec3d way=right.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang)));
                    m.softTaper(mouth,mouth.add(way.multiply(1.6+1.6*SamuraiScene.hash(k,91))),.1,.01,WHITE,.4f*lit*flicker);
                }
                glint(m,mouth,.75*(.8+.2*flicker),WHITE,lit,0);
                // glints running down the blade to its point
                for(int k=0;k<3;k++){
                    float u=((s-SamuraiKata.NOTO)*2.6f+k/3f)%1f;if(u<0)u+=1;
                    Vec3d p=hb[0].add(hb[1].multiply(SamuraiKata.GUARD+(SamuraiKata.TIP-SamuraiKata.GUARD)*u));
                    glint(m,p,.42*Math.sin(Math.PI*u),WHITE,.9f*lit,k*30);
                }
            }
        }
        // home: a ring at the mouth, and a level streak of white through them both
        float home=s-(SamuraiKata.CLICK-.1f);
        if(home>=0&&home<.2f){
            var hb=SamuraiKata.blade(v.feet,v.facing,s);
            Vec3d mouth=hb[0].add(hb[1].multiply(SamuraiKata.TIP-(SamuraiKata.TIP-SamuraiKata.GUARD)*SamuraiKata.home(s)));
            Vec3d n=c.camera.subtract(mouth).normalize(),right=n.crossProduct(UP).normalize(),up=right.crossProduct(n).normalize();
            if(home<.12f&&!light)FxLib.ringPlane(m,mouth,right,up,.22+.5*home/.12,.07,WHITE,1-home/.12f);
            float streak=home-.09f;
            if(streak>=0&&streak<.08f){
                float on=1-streak/.08f;Vec3d level=v.hand;
                Vec3d a=mouth.subtract(level.multiply(1.5)),b=mouth.add(level.multiply(6.5));
                if(light)m.softLine(a,b,.9,PALE,.5f*on);else m.needle(a,b,.34*on+.05,.2,WHITE,.95f*on);
            }
        }
        // sheathed: the wind swept out along the ground from them
        float sw=s-(SamuraiKata.CLICK+.01f);
        if(sw>=0&&sw<.34f&&!light){
            float u=sw/.34f,on=(1-u)*Math.min(1,sw/.04f);
            Vec3d g=v.feet.add(0,.12,0);
            for(int k=0;k<7;k++)arc(m,g.add(0,.05*k,0),new Vec3d(1,0,0),new Vec3d(0,0,1),1.0+3.6*u+.35*k,k*.9+u*3,2.0+.6*(k%3),.07+.05*(k%2),WHITE,.85f*on,22);
            for(int k=0;k<10;k++){
                double ang=k*.63+1,r=.8+3.0*u;
                m.softDisc(g.add(Math.cos(ang)*r,.15,Math.sin(ang)*r),.7,0xf4f6f8,.3f*on);
            }
        }
    }
    /** How strongly the light off the blade shows at {@code sec} as it is laid at the scabbard's mouth and slid home (0 outside it). */
    static float noto(float sec){
        float from=SamuraiKata.NOTO-.07f;
        if(sec<from||sec>=SamuraiKata.CLICK+.02f)return 0;
        return SamuraiScene.smooth((sec-from)/.07f)*(1-SamuraiScene.smooth((sec-(SamuraiKata.CLICK-.05f))/.07f))*(sec<SamuraiKata.NOTO+.03f?1.3f:1);
    }
    /**
     * Red off the one marked as a cut goes through them at {@code at}: drops thrown along the way the blade's point went,
     * falling; a red light on them.
     */
    private static void spray(View v,EffectMesh m,boolean light,float at,int seed,float out){
        float a=v.sec-at;if(a<0||a>=.42f||!v.live(at))return;
        Vec3d[] line=cutLine(v,at);Vec3d mid=v.hold.add(0,1.1,0);float u=a/.42f;
        if(light){m.softDisc(mid,1.3,RED,.55f*(1-u)*(1-u)*out);return;}
        for(int k=0;k<14;k++){
            double fast=2.5+6*SamuraiScene.hash(k,seed),t=a;
            Vec3d vel=line[1].multiply(fast).add(0,1.5+2.5*SamuraiScene.hash(k,seed+1),0).add(v.face.multiply(1.5*(SamuraiScene.hash(k,seed+2)-.5)));
            Vec3d p=mid.add(vel.multiply(t)).add(0,-7*t*t,0);
            m.softDisc(p,.07+.1*SamuraiScene.hash(k,seed+3),0xb00818,.9f*(1-u*u)*out);
        }
    }
    /** Where along the blade's line its crescents are (blocks from the hand: their inner edge at the blade, and their outer), and how far back in time they trail (seconds). */
    static final double SWEEP_FROM=.8,SWEEP_TO=1.75;
    static final float SWEEP_TRAIL=.085f;
    /** The ink that follows a crescent: how long after the blade it begins and ends (seconds), and the red edge beyond the crescent's outer edge (blocks). */
    static final float INK_AFTER=.06f,INK_UNTIL=.2f;
    static final double RED_EDGE=.11;
    /**
     * The blade's crescent now: the band its line swept in the last moments, one flat white, as deep as the blade at its
     * front and drawn out to a point behind; only as strong as the blade's point is fast (a held blade leaves none).
     */
    private static void sweep(View v,EffectMesh m,float s,boolean light){
        if(!light)band(v,m,s,s-SWEEP_TRAIL,SWEEP_FROM,SWEEP_TO,WHITE,1);
    }
    /**
     * The band the blade's line swept between {@code s} (its front) and {@code back} (its tail, drawn out to a point at
     * its outer edge), from {@code from} to {@code to} blocks along the blade; as strong as the blade's point was fast
     * over it.
     */
    private static void band(View v,EffectMesh m,float s,float back,double from,double to,int rgb,float alpha){
        int n=18;Vec3d[] in=new Vec3d[n+1],outer=new Vec3d[n+1];
        float start=Math.max(SamuraiScene.DASH+.005f,back);if(start>=s)return;
        for(int i=0;i<=n;i++){
            float ts=s-(s-start)*i/n;
            var hb=SamuraiKata.blade(v.feet,v.facing,ts);
            double u=i/(double)n,f=from+(to-from)*(1-Math.pow(1-u,1.6));
            in[i]=hb[0].add(hb[1].multiply(f));outer[i]=hb[0].add(hb[1].multiply(to));
        }
        double speed=outer[0].distanceTo(outer[n])/Math.max(1e-3,s-start);
        float strong=SamuraiScene.smooth((float)((speed-9)/16))*alpha;if(strong<=0)return;
        for(int i=0;i<n;i++){m.vertex(in[i],rgb,strong);m.vertex(outer[i],rgb,strong);m.vertex(outer[i+1],rgb,strong);m.vertex(in[i+1],rgb,strong);}
    }
    /** What is drawn over all its light: the burst's sign, and its black (the ink spiked up behind the blade through the flourish, the strokes' and the stars' dark hearts before it). */
    private static void black(Fx c,View v,EffectMesh m){
        float s=v.sec;
        // before the dash: the ring of ink, as far round as its brush has gone, and the flecks lifting off it
        if(s<SamuraiScene.DASH+.08f&&v.live(0)){
            float drawn=ensoDrawn(s),there=ensoThere(s);
            if(drawn>0&&there>0){
                ensoBand(v,m,0,drawn,1,INK,.96f*there);
                // dry-brush streaks at its tail
                if(drawn>=1)for(int k=0;k<5;k++)ensoBand(v,m,.9+.012*k,1.02+.03*SamuraiScene.hash(k,311),.12,INK,.8f*there);
                if(s<SamuraiKata.BURST+.03f)for(int k=0;k<18;k++){
                    float born=.14f+.045f*k,life=.55f;float a=((s-born)%life+life)%life;if(s<born)continue;
                    double u=SamuraiScene.hash(k,301),rise=(1.1+1.2*SamuraiScene.hash(k,302))*(a/life),size=.035+.035*SamuraiScene.hash(k,303);
                    Vec3d p=enso(v,u,0).add(0,rise,0).add(v.hand.multiply(.12*Math.sin(a*9+k)));
                    Vec3d x=c.camera.subtract(p).crossProduct(UP);if(x.lengthSquared()<1e-6)continue;x=x.normalize().multiply(size);
                    m.quad(p.add(x),p.add(0,size*1.7,0),p.subtract(x),p.subtract(0,size*1.7,0),INK,.9f*(1-a/life));
                }
            }
        }
        // the burst's sign over its light: the character for death, gold on a deep red
        float sg=s-SamuraiKata.BURST-.03f;
        if(sg>=0&&sg<.17f&&v.live(SamuraiKata.BURST)){
            Vec3d mid=v.d.from.add(0,1,0);
            Vec3d n=c.camera.subtract(mid).normalize(),right=n.crossProduct(UP).normalize(),up=right.crossProduct(n).normalize();
            float on=Math.min(1,sg/.02f)*(1-SamuraiScene.smooth((sg-.11f)/.06f));
            Vec3d at=mid.add(n.multiply(.55));double size=.6+.15*Math.min(1,sg/.04f);
            Shi.draw(m,at,right,up,size*1.12,DEEP_RED,.92f*on);Shi.draw(m,at.add(n.multiply(.01)),right,up,size,GOLD,on);
        }
        float b=s-SamuraiKata.BURST-.08f;
        if(b>=0&&b<.14f&&v.live(SamuraiKata.BURST)){
            Vec3d mid=v.d.from.add(0,1,0);int seed=(int)(v.d.cast.start()*31);
            Vec3d n=c.camera.subtract(mid).normalize(),right=n.crossProduct(UP).normalize(),up=right.crossProduct(n).normalize();
            float u=b/.14f,size=(float)Math.sin(Math.PI*Math.min(1,u*1.15));
            for(int k=0;k<4;k++){
                double ang=.2+k*1.6+SamuraiScene.hash(k,seed+15),r=2.0+1.7*SamuraiScene.hash(k,seed+16);
                star(m,mid.add(right.multiply(Math.cos(ang)*r)).add(up.multiply(Math.sin(ang)*r*.6)),.7*size,INK,.95f,0);
            }
        }
        if(!v.d.inScene()||!v.live(SamuraiScene.CLOSE))return;
        if(v.cine&&s>=SamuraiScene.FLASH)return;
        // the cut made in passing: a black line beside the white
        float cut=s-(SamuraiScene.CLOSE-.03f);
        if(cut>=0&&cut<.09f){
            Vec3d across=v.hand.multiply(-1),mid=v.hold.add(0,1.05,0).add(across.multiply(-.16)),way=across.multiply(.62).add(0,.78,0);
            m.needle(mid.subtract(way.multiply(3.2)),mid.add(way.multiply(3.2)),.13,.45,INK,.95f*(1-cut/.09f));
        }
        // the ink that follows the blade's crescent: a brush's stroke along its outer edge where the blade was a moment ago; and a line of it beside each cut's line
        if(s<SamuraiKata.NOTO)band(v,m,s-INK_AFTER,s-INK_UNTIL,SWEEP_TO-.24,SWEEP_TO+.1,INK,.92f);
        for(int k=0;k<SamuraiKata.CUTS.length;k++){
            float[] g=SamuraiKata.CUTS[k];float a=s-g[0]-.02f;if(a<0||a>=LINE_INK)continue;
            Vec3d[] line=cutLine(v,g[0]);double half=2.4*g[1]+.5;float u=a/LINE_INK;
            Vec3d off=line[1].crossProduct(c.camera.subtract(line[0])).normalize().multiply(.07+.05*g[1]);
            m.needle(line[0].subtract(line[1].multiply(half)).add(off),line[0].add(line[1].multiply(half*.9)).add(off),(.04+.07*g[1])*(1-u),.5,INK,.95f*(1-u*u));
        }
        for(int k=0;k<INKS.length;k++){
            float[] g=INKS[k];float a=s-g[0];if(a<0||a>=g[1])continue;
            Vec3d foot=v.by(g[2],g[3],g[4]);
            Vec3d across=c.camera.subtract(foot).crossProduct(UP);if(across.lengthSquared()<1e-5)continue;across=across.normalize();
            // a mass of strokes up and down, thick in its middle where they run together, ragged at its top and its foot
            int count=(int)(22*g[5]/.6f)+12;
            for(int j=0;j<count;j++){
                double spread=(SamuraiScene.hash(j,k*13+1)+SamuraiScene.hash(j,k*13+6)+SamuraiScene.hash(j,k*13+7))/1.5-1;
                double x=spread*g[5]*.9,tall=g[6]*(.45+.55*SamuraiScene.hash(j,k*13+2))*(1-.4*Math.abs(spread));
                double y=g[6]*.5+g[6]*.3*(SamuraiScene.hash(j,k*13+3)-.5);
                Vec3d base=foot.add(across.multiply(x)).add(0,y,0);
                m.needle(base.subtract(0,tall*.5,0),base.add(0,tall*.5,0),across,.035+.085*SamuraiScene.hash(j,k*13+4),.2+.6*SamuraiScene.hash(j,k*13+5),INK,.95f);
            }
        }
        // the split stars' dark hearts
        for(int k=0;k<STARS.length;k++){
            float[] g=STARS[k];if(g[6]<=0)continue;float a=s-g[0];if(a<0||a>=g[1])continue;
            float u=a/g[1],size=g[5]*(float)Math.sin(Math.PI*Math.min(1,.25+u*.85));
            if(u>.2f&&u<.85f)star(m,v.by(g[2],g[3],g[4]),size*.42,INK,.9f,0);
        }
    }

    // ───────────────────────────── the grey world ─────────────────────────────

    private static void grey(Fx c,View v,EffectMesh m,boolean light){
        float s=v.sec;Vec3d mid=v.hold.add(0,.95,0);
        // the grey world darker (the video's): a shade before the eye, under everything drawn here
        if(!light){
            Vec3d look=mid.subtract(c.camera).normalize(),across=look.crossProduct(UP).normalize(),over=across.crossProduct(look);
            Vec3d at=c.camera.add(look.multiply(.7));
            m.quad(at.subtract(across.multiply(2)).subtract(over.multiply(2)),at.add(across.multiply(2)).subtract(over.multiply(2)),at.add(across.multiply(2)).add(over.multiply(2)),at.subtract(across.multiply(2)).add(over.multiply(2)),0,.4f);
        }
        // the wind cut past the one marked, from where its user went: long thin strokes sweeping through
        float w=s-SamuraiScene.GREY;
        if(w<.24f&&!light){
            float on=1-SamuraiScene.smooth(w/.24f);
            for(int k=0;k<5;k++){
                // each from the side its user went, rising or falling across them, bowed
                double y0=-.6+1.5*SamuraiScene.hash(k,51),y1=-.2+1.0*SamuraiScene.hash(k,53),bow=(k%2==0?1:-1)*(.2+.35*SamuraiScene.hash(k,52));
                double far=4.8+1.8*SamuraiScene.hash(k,54),near=-1.2-2.2*SamuraiScene.hash(k,55);
                Vec3d prev=null;
                for(int i=0;i<=12;i++){
                    double u=i/12.0,x=far+(near-far)*u-1.6*w/.24;
                    Vec3d p=mid.add(v.sr.multiply(x)).add(0,y0+(y1-y0)*u+bow*Math.sin(u*Math.PI),0).subtract(v.sl.multiply(.5));
                    if(prev!=null)m.line(prev,p,.03+.06*Math.sin(u*Math.PI),WHITE,.85f*on*(float)Math.sin(u*Math.PI));
                    prev=p;
                }
            }
        }
        // the ring closing on them: white, its light fading inward; all outside it darker
        double r=SamuraiScene.ring(s);
        if(r>0){
            Vec3d u=v.sr,up=UP;float on=Math.min(1,(s-3.58f)/.05f);
            if(light){FxLib.ringPlane(m,mid,u,up,r,.3,WHITE,.3f*on);}
            else{
                FxLib.ringPlane(m,mid,u,up,r,.09,WHITE,on);
                int n=48;
                for(int i=0;i<n;i++){
                    double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;
                    Vec3d d0=u.multiply(Math.cos(a0)).add(up.multiply(Math.sin(a0))),d1=u.multiply(Math.cos(a1)).add(up.multiply(Math.sin(a1)));
                    m.vertex(mid.add(d0.multiply(r+.03)),0,.5f*on);m.vertex(mid.add(d1.multiply(r+.03)),0,.5f*on);
                    m.vertex(mid.add(d1.multiply(40)),0,.5f*on);m.vertex(mid.add(d0.multiply(40)),0,.5f*on);
                }
            }
        }
    }

    // ───────────────────────────── the black ground ─────────────────────────────

    /** The black ground: its grid running to the horizon, the light along the horizon, and the ground paler toward the eye. */
    private static void ground(Fx c,View v,EffectMesh m){
        Vec3d o=v.hold.add(0,.01,0),across=v.sr,away=v.sl;
        // paler toward the eye
        Vec3d n0=o.subtract(away.multiply(14)),n1=o.subtract(away.multiply(7));
        m.vertex(n0.subtract(across.multiply(30)),0xc8c8c8,.24f);m.vertex(n0.add(across.multiply(30)),0xc8c8c8,.24f);
        m.vertex(n1.add(across.multiply(30)),0xc8c8c8,0);m.vertex(n1.subtract(across.multiply(30)),0xc8c8c8,0);
        // the light along the horizon
        m.sheet(o.add(away.multiply(260)).add(0,.15,0),across.multiply(420),new Vec3d(0,.4,0),WHITE,.55f);
        m.sheet(o.add(away.multiply(260)).add(0,.15,0),across.multiply(420),new Vec3d(0,5,0),WHITE,.08f);
        // a red moon low over the horizon behind the one marked; after the last cut a line of the dark through it, its halves slid apart
        {
            Vec3d moon=o.add(away.multiply(150)).add(0,27,0).add(across.multiply(-14));double r=24;
            float split=SamuraiScene.smooth((v.sec-SLASH_SEC[3])/.09f);
            Vec3d slip=across.multiply(.5).add(0,.866,0).multiply(4.2*split);
            int n=40;
            for(int half=0;half<2;half++){
                Vec3d centre=moon.add(slip.multiply(half==0?1:-1));
                for(int i=0;i<n/2;i++){
                    // (each half a fan about the moon's middle, on its own side of the cut's line)
                    double a0=Math.atan2(.866,.5)+Math.PI*half+Math.PI*i/(n/2.0),a1=a0+Math.PI/(n/2.0);
                    Vec3d p0=centre.add(across.multiply(Math.cos(a0)*r)).add(0,Math.sin(a0)*r,0),p1=centre.add(across.multiply(Math.cos(a1)*r)).add(0,Math.sin(a1)*r,0);
                    m.quad(centre,p0,p1,centre,0xb20c1a,1);
                    Vec3d q0=centre.add(across.multiply(Math.cos(a0)*(r+.7))).add(0,Math.sin(a0)*(r+.7),0),q1=centre.add(across.multiply(Math.cos(a1)*(r+.7))).add(0,Math.sin(a1)*(r+.7),0);
                    m.quad(p0,q0,q1,p1,0xff5060,1);
                }
            }
            if(split>0)m.line(moon.subtract(across.multiply(.5*40).add(0,.866*40,0)),moon.add(across.multiply(.5*40).add(0,.866*40,0)),.5*split,WHITE,split*(1-SamuraiScene.smooth((v.sec-SLASH_SEC[3]-.1f)/.12f)));
        }
        // lines across, further and further off; and lines away from the eye
        double[] depths={-9,-6.2,-3.4,-1.2,1.5,5,10,18,30,48,75};
        for(double z:depths){
            double far=Math.max(0,z);float a=(float)Math.max(.2,.9-far/160);
            Vec3d p=o.add(away.multiply(z));
            // (as thin on the frame however far: as wide as it is far from the eye)
            m.line(p.subtract(across.multiply(160)),p.add(across.multiply(160)),.0034*(z+SamuraiScene.SIDE_VOID),0xe6e6e6,a);
        }
        for(double x:new double[]{-52,-30,-15,-5.5,5.5,15,30,52}){
            Vec3d p=o.add(across.multiply(x));
            m.line(p.subtract(away.multiply(12)),p.add(away.multiply(240)),.04,0xe6e6e6,.7f);
        }
        // two lines askew across it
        m.line(o.add(across.multiply(-40)).subtract(away.multiply(8)),o.add(across.multiply(60)).add(away.multiply(70)),.05,0xdcdcdc,.6f);
        m.line(o.add(across.multiply(40)).subtract(away.multiply(8)),o.add(across.multiply(-60)).add(away.multiply(70)),.05,0xdcdcdc,.6f);
    }
    /** The four blades waiting round the one marked, each until its cut, drifting in a little as they wait. */
    private static void blades(View v,EffectMesh m,int rgb){
        float s=v.sec;
        for(int k=0;k<4;k++){
            if(!SamuraiScene.waits(k,s))continue;
            double[] b=SamuraiScene.BLADES[k];
            double creep=.22*SamuraiScene.unit((s-SamuraiScene.VOID)/(SLASH_SEC[k]-SamuraiScene.VOID));
            double tx=b[0]+b[2]*creep,ty=b[1]+b[3]*creep;
            Vec3d tip=SamuraiScene.plane(v.hold,v.d.f,v.d.side,tx,ty);
            Vec3d way=v.sr.multiply(b[2]).add(0,b[3],0),side=v.sr.multiply(-b[3]).add(0,b[2],0);
            // a long wedge, a slight bend a third of the way back
            double L=SamuraiScene.BLADE_LENGTH,w=SamuraiScene.BLADE_WIDTH;
            Vec3d knee=tip.subtract(way.multiply(L*.3)).add(side.multiply(.05)),root=tip.subtract(way.multiply(L)).subtract(side.multiply(.12));
            m.quad(tip,knee.add(side.multiply(w*.32)),knee.subtract(side.multiply(w*.32)),tip,rgb,1);
            m.quad(knee.add(side.multiply(w*.32)),root.add(side.multiply(w*.5)),root.subtract(side.multiply(w*.5)),knee.subtract(side.multiply(w*.32)),rgb,1);
        }
    }
    /**
     * The four cuts through the one marked: each a broad white band along its blade's line as it goes through, the thin
     * line it leaves, and what bursts out of them where it went (for its two on the black ground; for everyone else in
     * the world, where the one marked stands, gone a moment after the last).
     */
    private static void cuts(Fx c,View v,EffectMesh m,boolean light){
        float s=v.sec;if(!v.d.inScene()||v.d.stopped()&&v.d.ended<SLASH_SEC[0]*20)return;
        if(s<SLASH_SEC[0]||s>=SamuraiScene.WORLD+.25f)return;
        float out=v.cine?1:1-SamuraiScene.smooth((s-SamuraiScene.OUT)/.3f);if(out<=0)return;
        Vec3d mid=v.hold.add(0,1,0);
        for(int k=0;k<4;k++){
            float a=s-SLASH_SEC[k];if(a<0||!v.live(SLASH_SEC[k]))continue;
            double[] b=SamuraiScene.BLADES[k];
            Vec3d tip=SamuraiScene.plane(v.hold,v.d.f,v.d.side,b[0],b[1]);
            Vec3d way=v.sr.multiply(b[2]).add(0,b[3],0),side=v.sr.multiply(-b[3]).add(0,b[2],0);
            double width=SamuraiScene.cut(k,s),back=v.cine?40:7,on=v.cine?40:9;
            Vec3d from=tip.subtract(way.multiply(back)),to=tip.add(way.multiply(on));
            if(light){if(a<.12f)m.softLine(from,to,width*3+.4,PALE,.5f*(1-a/.12f)*out);continue;}
            Vec3d h=side.multiply(width*.5);
            if(v.cine)m.quad(from.subtract(h),from.add(h),to.add(h),to.subtract(h),WHITE,out);
            else m.needle(from,to,side,width,.45,WHITE,out);
            // out of them where it went: white spikes, and bits thrown off
            if(a<.1f){
                float u=a/.1f;
                // where the line goes through them
                double along=mid.subtract(tip).dotProduct(way);Vec3d hit=tip.add(way.multiply(along));
                for(int j=0;j<11;j++){
                    double ang=SamuraiScene.hash(j,k*19+1)*6.28,len=(1.0+2.2*SamuraiScene.hash(j,k*19+2))*(.4+.6*u);
                    Vec3d dir=v.sr.multiply(Math.cos(ang)).add(0,Math.sin(ang),0);
                    m.needle(hit.add(dir.multiply(.2)),hit.add(dir.multiply(len)),v.sl.crossProduct(dir),.3*(1-u),.3,WHITE,(1-u*u)*out);
                }
                m.softDisc(hit,1.1*(1-u),WHITE,(1-u)*out);
            }
            if(a<.3f){
                float u=a/.3f;
                double along=mid.subtract(tip).dotProduct(way);Vec3d hit=tip.add(way.multiply(along));
                for(int j=0;j<9;j++){
                    double ang=SamuraiScene.hash(j,k*23+5)*6.28,r=(.6+3.4*SamuraiScene.hash(j,k*23+6))*u;
                    Vec3d p=hit.add(v.sr.multiply(Math.cos(ang)*r)).add(0,Math.sin(ang)*r-1.2*u*u,0);
                    double sz=.05+.07*SamuraiScene.hash(j,k*23+7);
                    m.quad(p.add(v.sr.multiply(sz)),p.add(0,sz*1.6,0),p.subtract(v.sr.multiply(sz)),p.subtract(0,sz*1.6,0),0xe0e0e0,(1-u)*out);
                }
            }
        }
    }

    // ───────────────────────────── the world back ─────────────────────────────

    /**
     * The last inch of the sword pushed home (SamuraiKata.SNAP): a ring and a glint at the scabbard's mouth; and at that
     * the pillar of light where the one marked stands, rings and wind along the ground from it, red in it for a moment,
     * and red petals thrown up out of it that drift down after.
     */
    private static void pillar(View v,EffectMesh m,boolean light){
        float s=v.sec,a=s-SamuraiKata.SNAP;if(a<0||a>=1.5f||!v.d.inScene()||v.d.stopped())return;
        // at the mouth
        if(a<.2f){
            float u=a/.2f;
            var hb=SamuraiKata.blade(v.feet,v.facing,s);Vec3d mouth=hb[0].add(hb[1].multiply(SamuraiKata.GUARD));
            Vec3d n=m.camera().subtract(mouth).normalize(),right=n.crossProduct(UP).normalize(),up=right.crossProduct(n).normalize();
            if(light){glint(m,mouth,.9*(1-u),WHITE,1-u,0);m.softLine(mouth.subtract(right.multiply(3.5)),mouth.add(right.multiply(3.5)),.08,PALE,.6f*(1-u));}
            else FxLib.ringPlane(m,mouth,right,up,.15+.6*u,.06*(1-u)+.01,WHITE,1-u*u);
        }
        // the petals: thrown up and out of where the one marked stands, turning, drifting down
        if(!light){
            float on=(1-SamuraiScene.smooth((a-1.05f)/.45f))*Math.min(1,a/.04f);
            for(int k=0;k<34;k++){
                double ang=SamuraiScene.hash(k,401)*6.283,out=1.2+3.6*SamuraiScene.hash(k,402),up=4.5+5.5*SamuraiScene.hash(k,403);
                // (thrown: slowing; then falling slowly, swaying)
                double t=a,slow=1-Math.exp(-t*3.2),x=out*slow,y=up*slow*.55-1.1*Math.max(0,t-.35)*Math.max(0,t-.35)*2.2-.5*Math.max(0,t-.35);
                Vec3d p=v.hold.add(Math.cos(ang)*x+.25*Math.sin(t*5+k),1+y,Math.sin(ang)*x+.25*Math.cos(t*4.3+k*1.7));
                if(p.y<v.hold.y+.05)continue;
                double size=.07+.06*SamuraiScene.hash(k,404),turn=t*(5+6*SamuraiScene.hash(k,405))+k;
                Vec3d e1=new Vec3d(Math.cos(turn),Math.sin(turn)*.6,Math.sin(turn)*.5).normalize().multiply(size),e2=new Vec3d(-Math.sin(turn)*.5,.5,Math.cos(turn)).normalize().multiply(size*1.7);
                m.quad(p.add(e1),p.add(e2),p.subtract(e1),p.subtract(e2),k%4==0?0xff5a6a:0xd8101e,.95f*on);
            }
        }
        if(a>=.85f)return;
        Vec3d g=v.hold.add(0,.08,0);
        float u=a/.85f,on=(1-u)*(1-u),first=Math.max(0,1-a/.2f);
        double width=.25+1.5*first;
        if(light){
            m.softTaper(g,g.add(0,34,0),width*3.2,width*1.4,PALE,.5f*on);
            m.softDisc(g.add(0,1.2,0),3.2+2*first,WHITE,.85f*first);
            for(int k=0;k<4;k++)FxLib.ring(m,g.add(0,.02*k,0),.8+(5.5+1.4*k)*SamuraiScene.smooth(a/(.35f+.1f*k)),.5,PALE,.5f*on);
            glint(m,g.add(0,5.5,0).add(v.sr.multiply(-.6)),2.6*first,WHITE,first,0);
            glint(m,g.add(0,8.5,0).add(v.sr.multiply(1.4)),1.9*first,WHITE,first,20);
            // red in it for a moment
            float red=SamuraiScene.smooth((a-.2f)/.05f)*(1-SamuraiScene.smooth((a-.38f)/.08f));
            if(red>0)for(int k=0;k<5;k++){
                double x=(k-2)*.16;
                m.softTaper(g.add(v.sr.multiply(x)).add(0,.5,0),g.add(v.sr.multiply(x)).add(0,26,0),.2,.12,RED,.7f*red*(.6f+.4f*(float)Math.sin(s*90+k)));
            }
        }else{
            m.needle(g.subtract(0,.3,0),g.add(0,34,0),width,.06,WHITE,Math.min(1,1.6f*(1-u)));
            // wind along the ground, turning out from it
            for(int k=0;k<9;k++)arc(m,g.add(0,.04*k,0),new Vec3d(1,0,0),new Vec3d(0,0,1),1.2+4.2*SamuraiScene.smooth(a/.5f)+.3*k,k*.7+a*4,1.8+.5*(k%3),.08,WHITE,.8f*on,22);
            // the air round it, thrown up
            for(int k=0;k<10;k++){
                double ang=k*.63,r=1.6+2.6*u;
                Vec3d p=g.add(Math.cos(ang)*r,.2,Math.sin(ang)*r);
                m.softTaper(p,p.add(Math.cos(ang)*.8,3.5+2*SamuraiScene.hash(k,61),Math.sin(ang)*.8),.5,.05,WHITE,.3f*on);
            }
        }
    }

    // ───────────────────────────── shapes ─────────────────────────────

    /** Part of a circle of radius {@code r} about {@code c} in the plane of {@code u} and {@code v}: from angle {@code from}, {@code span} round, thin at both ends. */
    static void arc(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,double r,double from,double span,double width,int rgb,float alpha,int n){
        if(alpha<=0)return;Vec3d prev=null;
        for(int i=0;i<=n;i++){
            double t=i/(double)n,a=from+span*t;
            Vec3d p=c.add(u.multiply(Math.cos(a)*r)).add(v.multiply(Math.sin(a)*r));
            if(prev!=null)m.line(prev,p,width*Math.sin(Math.PI*(t-.5/n))+.004,rgb,alpha*(float)Math.sin(Math.PI*(t-.5/n)));
            prev=p;
        }
    }
    /** A four-point star facing the camera: long thin rays (turned {@code turn} degrees), a bright middle, a soft glow. */
    static void glint(EffectMesh m,Vec3d p,double r,int rgb,float alpha,double turn){
        if(alpha<=0||r<=0)return;
        m.softDisc(p,r*.5,rgb,alpha*.45f);
        star(m,p,r,rgb,alpha,turn);
        m.softDisc(p,r*.16,WHITE,alpha);
    }
    /** The star's four rays alone, one colour. */
    static void star(EffectMesh m,Vec3d p,double r,int rgb,float alpha,double turn){
        if(alpha<=0||r<=0)return;
        Vec3d n=m.camera().subtract(p);if(n.lengthSquared()<1e-8)return;n=n.normalize();
        Vec3d u=n.crossProduct(UP);if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();Vec3d w=u.crossProduct(n).normalize();
        double a=Math.toRadians(turn);Vec3d x=u.multiply(Math.cos(a)).add(w.multiply(Math.sin(a))),y=w.multiply(Math.cos(a)).subtract(u.multiply(Math.sin(a)));
        // two crossed kites: wide by the middle, sharp at the ends
        double k=r*.14;
        m.quad(p.add(x.multiply(r)),p.add(y.multiply(k)),p.subtract(x.multiply(r)),p.subtract(y.multiply(k)),rgb,alpha);
        m.quad(p.add(y.multiply(r*1.15)),p.add(x.multiply(k)),p.subtract(y.multiply(r*1.15)),p.subtract(x.multiply(k)),rgb,alpha);
    }
    /** The light of a big star split into colours: red thrown to one side of its white (drawn over the world, the blended pass) and blue to the other. */
    static void split(EffectMesh m,Vec3d p,double r,float alpha){
        if(alpha<=0||r<=0)return;
        Vec3d n=m.camera().subtract(p);if(n.lengthSquared()<1e-8)return;n=n.normalize();
        Vec3d u=n.crossProduct(UP);if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();
        m.softDisc(p,r*.35,WHITE,.25f*alpha);
        star(m,p.add(u.multiply(r*.04)),r*1.03,0xff2a10,.5f*alpha,0);
        star(m,p.subtract(u.multiply(r*.04)),r*1.03,0x1088ff,.5f*alpha,0);
    }

    /** The character for death in strokes (tools/samurai/make_ink.py's SHI: the same seven), for the burst's sign. */
    static final class Shi {
        private Shi(){}
        /** Each stroke: its points {x to the right, y down} in the unit square, its width where it lands and where it leaves, and its belly (of its height). */
        static final double[][][] STROKES={
            {{.07,.215},{.30,.175},{.62,.155},{.93,.150}},
            {{.36,.23},{.30,.34},{.20,.47},{.10,.56}},
            {{.27,.385},{.40,.355},{.535,.335}},
            {{.535,.335},{.49,.50},{.37,.70},{.21,.86},{.06,.94}},
            {{.265,.525},{.33,.575},{.40,.65}},
            {{.90,.355},{.79,.43},{.655,.50}},
            {{.635,.225},{.628,.45},{.625,.66},{.66,.80},{.80,.845},{.93,.82},{.955,.70}}};
        static final double[][] WIDTHS={{.105,.085,.012},{.085,.028,0},{.060,.078,0},{.085,.020,.014},{.040,.085,0},{.075,.034,0},{.090,.050,.020}};
        /** Draws it facing along the normal of {@code right} and {@code up}, its middle at {@code c}, {@code size} blocks tall. */
        static void draw(EffectMesh m,Vec3d c,Vec3d right,Vec3d up,double size,int rgb,float alpha){
            if(alpha<=0)return;
            for(int k=0;k<STROKES.length;k++){
                double[][] pts=STROKES[k];double[] w=WIDTHS[k];
                int n=(pts.length-1)*6;Vec3d pl=null,pr=null;
                for(int i=0;i<=n;i++){
                    double t=i/(double)n;double[] p=at(pts,t),q=at(pts,Math.min(1,t+.02)),o=at(pts,Math.max(0,t-.02));
                    double dx=q[0]-o[0],dy=q[1]-o[1],len=Math.hypot(dx,dy);if(len<1e-9)continue;
                    double half=(w[0]+(w[1]-w[0])*t+w[2]*Math.sin(Math.PI*t))*(.55+.45*Math.min(1,t*14))*.5;
                    double nx=-dy/len*half,ny=dx/len*half;
                    Vec3d l=point(c,right,up,size,p[0]+nx,p[1]+ny),r=point(c,right,up,size,p[0]-nx,p[1]-ny);
                    if(pl!=null)m.quad(pl,l,r,pr,rgb,alpha);
                    pl=l;pr=r;
                }
            }
        }
        private static Vec3d point(Vec3d c,Vec3d right,Vec3d up,double size,double x,double y){return c.add(right.multiply((x-.5)*size)).add(up.multiply((.5-y)*size));}
        /** A point {@code t} along a line bent smoothly through {@code pts} (Catmull-Rom). */
        static double[] at(double[][] pts,double t){
            int spans=pts.length-1;double x=Math.min(spans-1e-9,Math.max(0,t*spans));int i=(int)x;double u=x-i;
            double[] p0=pts[Math.max(0,i-1)],p1=pts[i],p2=pts[i+1],p3=pts[Math.min(spans,i+2)];
            double[] out=new double[2];
            for(int c=0;c<2;c++)out[c]=.5*(2*p1[c]+(-p0[c]+p2[c])*u+(2*p0[c]-5*p1[c]+4*p2[c]-p3[c])*u*u+(-p0[c]+3*p1[c]-3*p2[c]+p3[c])*u*u*u);
            return out;
        }
    }
}
