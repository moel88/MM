package dev.sangwoo.celestialfall.client.meteor;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.meteor.MeteorEntity;
import dev.sangwoo.celestialfall.meteor.MeteorRegistry;
import dev.sangwoo.celestialfall.meteor.MeteorRules;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.util.*;

/**
 * Everything the meteors show beyond their rocks lying on the ground (2.49.0, user request: "운석 떨어지는 효과 잘
 * 만들어야해").
 *
 * <p>The fall: the rock comes in white-hot and tumbling, wrapped in a sheath of flame along its way, a glowing trail
 * behind it, flames and then dark smoke left hanging along its whole path, sparks shedding off it, a turning mark where
 * it will land and the ground there lit up as it nears. The landing: a flash, a fireball, a shock ring of dust racing
 * out along the ground, earth and stone thrown out bouncing, a spray of sparks, a dark column of smoke, the ground
 * glowing, the camera shaken for those near and a boom heard as late as sound takes to get there. On the ground: smoke
 * and embers rising off the rock as it cools. Mining: sparks, chips and dust off the crystal being struck; a fragment
 * coming off bursts in gold; the last one leaves the rock falling apart, and one left five minutes crumbles into the
 * ground.
 *
 * <p>Fire, smoke and dust are drawn on fixed planes (never turned to face the camera, as the project's rule has it),
 * light (glows, sparks, glints) faces the camera. Every particle is a function of its seed and the clock, never stepped
 * frame by frame, so all of it moves smoothly at any frame rate.
 */
public final class MeteorFx {
    private static ShaderProgram blend,glow;
    private static final List<Fall> FALLS=new ArrayList<>();
    private static final List<Burst> BURSTS=new ArrayList<>();
    private static final List<Ending> ENDINGS=new ArrayList<>();
    /** Each rock on the ground (entity id): the clock at its landing, and at its last hit. */
    private static final Map<Integer,Double> LANDED=new HashMap<>(),JOLTS=new HashMap<>();
    private static final Vec3d UP=new Vec3d(0,1,0),WIND=new Vec3d(.016,0,.007);
    private static final double GRAVITY=.06;
    private MeteorFx(){}

    static void initialize(){
        CoreShaderRegistrationCallback.EVENT.register(c->{
            c.register(CelestialFall.id("meteor_fx"),VertexFormats.POSITION_TEXTURE_COLOR,s->blend=s);
            c.register(CelestialFall.id("meteor_fx_add"),VertexFormats.POSITION_TEXTURE_COLOR,s->glow=s);
        });
    }
    static void clear(){FALLS.clear();BURSTS.clear();ENDINGS.clear();LANDED.clear();JOLTS.clear();}

    // ---------------------------------------------------------------- events

    /** A fall the server announced: its clock starts so that it reads {@code age} ticks now. */
    static void fall(int slot,long cycle,int seed,Vec3d from,Vec3d to,int ticks,double age){
        FALLS.removeIf(f->f.slot==slot&&f.cycle!=cycle&&f.t(0)<f.ticks);
        if(FALLS.stream().anyMatch(f->f.slot==slot&&f.cycle==cycle))return;
        var f=new Fall(slot,cycle,seed,from,to,ticks,MeteorClient.clock(0)-age);
        FALLS.add(f);
        var mc=MinecraftClient.getInstance();
        if(age<ticks-6)mc.getSoundManager().play(new FallSound(f));
    }
    /** Whether the rock of this meteor is still in the air on this client (its rock on the ground waits for it). */
    static boolean falling(int slot,long cycle,float delta){
        for(var f:FALLS)if(f.slot==slot&&f.cycle==cycle)return f.t(delta)<f.ticks;
        return false;
    }
    /** A status the server sent for a rock: a hit, a fragment off, mined out or crumbled. */
    static void event(MeteorEntity rock,byte status){
        double now=MeteorClient.clock(0);
        var mesh=MeteorMesh.of(rock.seed());
        int before=Math.max(1,Math.min(MeteorRules.FRAGMENTS,rock.fragments()));
        var cluster=mesh.clusters[before-1];
        Vec3d at=rock.getPos();
        if(status==MeteorEntity.HIT){JOLTS.put(rock.getId(),now);BURSTS.add(new Burst(at,cluster,rock.seed()*31+rock.hits()*7+rock.fragments(),now,false));}
        else if(status==MeteorEntity.BREAK){JOLTS.put(rock.getId(),now);BURSTS.add(new Burst(at,cluster,rock.seed()*31+rock.fragments()*13,now,true));}
        else{
            if(status==MeteorEntity.MINED)BURSTS.add(new Burst(at,cluster,rock.seed()*29+5,now,true));
            ENDINGS.add(new Ending(at,rock.seed(),landedAt(rock,0),now,status==MeteorEntity.MINED,status==MeteorEntity.MINED?0:rock.fragments()));
        }
    }
    static void tick(MinecraftClient mc){
        double now=MeteorClient.clock(0);
        FALLS.removeIf(f->now-f.zero>f.ticks+340);
        BURSTS.removeIf(b->now-b.zero>70);
        ENDINGS.removeIf(e->now-e.zero>150);
        if(mc.world==null){LANDED.clear();JOLTS.clear();return;}
        LANDED.keySet().removeIf(id->!(mc.world.getEntityById(id) instanceof MeteorEntity));
        JOLTS.keySet().removeIf(id->!(mc.world.getEntityById(id) instanceof MeteorEntity)||now-JOLTS.get(id)>20);
    }

    /** The clock when this rock landed (from its fall if this client saw it, else from the world's time). */
    static double landedAt(MeteorEntity rock,float delta){
        return LANDED.computeIfAbsent(rock.getId(),id->{
            for(var f:FALLS)if(f.slot==rock.slot()&&f.cycle==rock.cycle())return f.zero+f.ticks;
            return MeteorClient.clock(0)-(rock.getWorld().getTime()-rock.landing());
        });
    }
    /** How long ago (ticks, this frame) this rock was last struck; large if never. */
    static double sinceHit(MeteorEntity rock,float delta){Double at=JOLTS.get(rock.getId());return at==null?1e9:MeteorClient.clock(delta)-at;}

    // ---------------------------------------------------------------- the camera

    /** The camera's shake for the local viewer from landings near it: {yaw, pitch, roll} degrees, or null. */
    public static float[] shake(Vec3d camera,float delta){
        if(FALLS.isEmpty())return null;
        double now=MeteorClient.clock(delta);float amount=0;
        for(var f:FALLS){
            double ti=now-f.zero-f.ticks;if(ti<0||ti>40)continue;
            double near=Math.max(0,1-f.to.distanceTo(camera)/72);if(near<=0)continue;
            // in over a frame or two, then dying away
            amount=(float)Math.max(amount,2.6*near*near*smooth(0,.9,ti)*Math.exp(-ti/8));
        }
        if(amount<=.001f)return null;
        amount*=(float)CelestialFall.CONFIG.screenShakeStrength;
        double time=now;
        return new float[]{amount*(float)(Math.sin(time*3.1)*.7+Math.sin(time*5.7)*.3),amount*(float)(Math.cos(time*3.9)*.7+Math.sin(time*6.3)*.3),amount*.4f*(float)Math.sin(time*4.6)};
    }

    // ---------------------------------------------------------------- drawing

    private static SimpleFramebuffer depthCopy;
    /**
     * Copies the depth of the scene drawn so far and binds it as Sampler0, so the puffs and light melt where they meet
     * the ground or the rock instead of being cut off along it. Returns 1 when it did, 0 when the frame is drawn
     * somewhere other than the game's own framebuffer (a shader pack's): then nothing melts and the effects are drawn as
     * they would be without it.
     */
    private static float captureDepth(){
        var main=MinecraftClient.getInstance().getFramebuffer();
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.fbo)return 0;
        int w=main.textureWidth,h=main.textureHeight;
        if(depthCopy==null)depthCopy=new SimpleFramebuffer(w,h,true,MinecraftClient.IS_SYSTEM_MAC);
        else if(depthCopy.textureWidth!=w||depthCopy.textureHeight!=h)depthCopy.resize(w,h,MinecraftClient.IS_SYSTEM_MAC);
        depthCopy.copyDepthFrom(main);
        main.beginWrite(true);
        RenderSystem.setShaderTexture(0,depthCopy.getDepthAttachment());
        return 1;
    }

    /** Draws it all after the world (the rocks in the air and the pieces through the game's entity layers first). */
    static void render(WorldRenderContext c){
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;
        List<MeteorEntity> rocks=new ArrayList<>();
        for(var e:mc.world.getEntities())if(e instanceof MeteorEntity m&&m.isAlive())rocks.add(m);
        if(FALLS.isEmpty()&&BURSTS.isEmpty()&&ENDINGS.isEmpty()&&rocks.isEmpty())return;
        if(blend==null||glow==null)return;
        float delta=c.tickDelta();double now=MeteorClient.clock(delta);
        Vec3d cam=c.camera().getPos();
        var ms=c.matrixStack();ms.push();ms.translate(-cam.x,-cam.y,-cam.z);
        var f=new Frame(cam,(float)now,mc.gameRenderer.getViewDistance());
        // the daylight on the smoke and dust (the sky's brightness runs from .2 at night to 1 at noon)
        f.ambient=(float)(.3+.7*Math.max(0,Math.min(1,(mc.world.getSkyBrightness(delta)-.2)/.8)));
        var consumers=mc.getBufferBuilders().getEntityVertexConsumers();
        try{
            for(var fall:FALLS)fall.collect(f,now,ms,consumers,mc);
            for(var b:BURSTS)b.collect(f,now,ms,consumers,mc);
            for(var e:ENDINGS)e.collect(f,now,ms,consumers,mc);
            for(var rock:rocks)landed(rock,f,now,delta);
            consumers.draw();
            f.draw(ms,c.camera());
        }finally{
            ms.pop();
            RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
        }
    }

    /** Smoke and embers rising off a rock on the ground while it cools, and the ground's glow round it. */
    private static void landed(MeteorEntity rock,Frame f,double now,float delta){
        if(falling(rock.slot(),rock.cycle(),delta))return;
        double age=now-landedAt(rock,delta);if(age<0)return;
        var mesh=MeteorMesh.of(rock.seed());Vec3d at=rock.getLerpedPos(delta);int seed=rock.seed();
        double heat=MeteorRenderer.heat(age);
        f.pool(at.add(0,.04,0),4.2,heat,.55*heat,seed,true);
        // a wisp every four ticks (fewer as it cools, never none, so it can be found by its smoke)
        int last=(int)Math.floor(age/4);
        for(int j=Math.max(0,last-30);j<=last;j++){
            double born=j*4,a=age-born;double life=70+rnd(seed,j,1)*45;if(a<0||a>life)continue;
            if(rnd(seed,j,2)>.35+.65*MeteorRenderer.heat(born))continue;
            double ang=rnd(seed,j,3)*Math.PI*2,rad=Math.sqrt(rnd(seed,j,4))*mesh.radius*.7;
            Vec3d o=at.add(Math.cos(ang)*rad,mesh.centreY+.35+rnd(seed,j,5)*.4,Math.sin(ang)*rad);
            Vec3d p=o.add(0,(.05+rnd(seed,j,6)*.03)*a,0).add(WIND.multiply(a*(.6+.6*rnd(seed,j,7))));
            double size=.45+1.7*(1-Math.exp(-a/40));
            float alpha=(float)(.55*smooth(0,6,a)*(1-smooth(life*.45,life,a)));
            f.smoke(p,size,.5+.25*rnd(seed,j,8),(float)Math.max(0,.6*MeteorRenderer.heat(born)*Math.exp(-a/12)),alpha,seed+j);
        }
        int lastEmber=(int)Math.floor(age/3);
        for(int j=Math.max(0,lastEmber-16);j<=lastEmber;j++){
            double born=j*3,a=age-born,life=25+rnd(seed,j,11)*22;if(a<0||a>life)continue;
            if(rnd(seed,j,12)>.85*MeteorRenderer.heat(born)-.05)continue;
            double ang=rnd(seed,j,13)*Math.PI*2,rad=rnd(seed,j,14)*mesh.radius*.8;
            Vec3d p=at.add(Math.cos(ang)*rad+Math.sin(a*.3+j)*.15,mesh.centreY+.4+(.045+rnd(seed,j,15)*.05)*a,Math.sin(ang)*rad+Math.cos(a*.27+j)*.15);
            f.glow(p,.09+rnd(seed,j,16)*.07,.8f,.3f,(float)(smooth(0,3,a)*(1-smooth(life*.5,life,a))));
        }
    }

    // ---------------------------------------------------------------- one fall

    private static final class Fall {
        final int slot,seed,ticks;final long cycle;final Vec3d from,to,dir;final double length,zero;
        final BlockState ground;
        boolean boomed;
        Fall(int slot,long cycle,int seed,Vec3d from,Vec3d to,int ticks,double zero){
            this.slot=slot;this.cycle=cycle;this.seed=seed;this.from=from;this.to=to;this.ticks=ticks;this.zero=zero;
            length=from.distanceTo(to);dir=to.subtract(from).multiply(1/length);
            var mc=MinecraftClient.getInstance();
            BlockState under=mc.world==null?Blocks.DIRT.getDefaultState():mc.world.getBlockState(BlockPos.ofFloored(to.x,to.y-.5,to.z));
            ground=under.isAir()||under.getRenderType()!=net.minecraft.block.BlockRenderType.MODEL?Blocks.DIRT.getDefaultState():under;
        }
        double t(float delta){return MeteorClient.clock(delta)-zero;}
        /** Share of the way down at fall clock t (a little faster towards the end). */
        double travelled(double t){double u=Math.max(0,Math.min(1,t/ticks));return length*u*(.82+.18*u);}
        /** When the head passed a point {@code d} blocks down its way. */
        double passed(double d){double k=Math.max(0,Math.min(1,d/length));return ticks*(-.82+Math.sqrt(.6724+.72*k))/.36;}
        Vec3d at(double d){return from.add(dir.multiply(d));}
        double speed(double t){double u=Math.max(0,Math.min(1,t/ticks));return length/ticks*(.82+.36*u);}

        void collect(Frame f,double now,MatrixStack ms,VertexConsumerProvider consumers,MinecraftClient mc){
            double t=now-zero,ti=t-ticks;
            double ignite=smooth(0,5,t);
            if(t<ticks){
                double d=travelled(t);Vec3d head=at(d);
                var mesh=MeteorMesh.of(seed);
                Vec3d centre=head.add(0,mesh.centreY,0);
                // the rock, tumbling to come to rest as it lands
                Vec3d axis=dir.crossProduct(UP);if(axis.lengthSquared()<1e-4)axis=new Vec3d(1,0,0);axis=axis.normalize();
                float angle=(float)(-.3*(ticks-t));
                ms.push();ms.translate(head.x,head.y,head.z);ms.translate(0,mesh.centreY,0);
                ms.multiply(new Quaternionf().rotateAxis(angle,(float)axis.x,(float)axis.y,(float)axis.z));ms.translate(0,-mesh.centreY,0);
                MeteorRenderer.drawRock(ms,consumers,mesh,MeteorRules.FRAGMENTS,-1,0,LightmapTextureManager.MAX_LIGHT_COORDINATE,1);
                ms.pop();
                double dist=centre.distanceTo(f.cam);
                // the light of it: a wide halo (kept a few pixels wide however far it is) and a white-hot core
                f.glow(centre,4.2+dist*.014,.55f,.55f,(float)(.85*ignite));
                f.glow(centre,1.7+dist*.006,1f,.9f,(float)ignite);
                // the sheath of flame, open towards the back
                f.axial(3,centre.add(dir.multiply(1.5)),centre.subtract(dir.multiply(7.5)),3.0,3.0,dir,seed,1,(float)ignite);
                // the glowing trail, up to 46 blocks back
                double back=Math.min(d,46);
                f.trail(centre.subtract(dir.multiply(.6)),dir,back,3.2,.6,seed,1f,(float)(.9*ignite));
                // where it will land
                double warn=smooth(.12*ticks,.55*ticks,t);
                if(to.distanceTo(f.cam)<180)f.mark(to.add(0,.05,0),3.3,(float)warn);
                f.pool(to.add(0,.06,0),11,.6,smooth(.62*ticks,ticks,t)*.85,seed,false);
            }else if(ti<40){
                // after landing the trail's light dies away in a couple of seconds
                double back=Math.min(46,length);
                f.trail(to.add(0,MeteorMesh.of(seed).centreY,0).subtract(dir.multiply(.6)),dir,back,3.2*(1+ti/20),.6,seed,(float)Math.exp(-ti/5),(float)(.9*Math.exp(-ti/6)));
            }
            trailPuffs(f,t);
            sheddings(f,t);
            if(ti>=0)impact(f,ti,ms,consumers,mc);
            // until its rock is on the ground here, the landed rock is drawn from the fall
            if(ti>=0&&ti<60&&!rockArrived(mc)){
                ms.push();ms.translate(to.x,to.y,to.z);
                MeteorRenderer.drawRock(ms,consumers,MeteorMesh.of(seed),MeteorRules.FRAGMENTS,ti,1e9,WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(to.x,to.y+.5,to.z)),1);
                MeteorRenderer.drawScorch(ms,consumers,seed,ti,WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(to.x,to.y+.5,to.z)),(float)smooth(0,2,ti));
                ms.pop();
            }
        }
        private boolean rockArrived(MinecraftClient mc){
            for(var e:mc.world.getEntities())if(e instanceof MeteorEntity m&&m.slot()==slot&&m.cycle()==cycle)return true;
            return false;
        }

        /** Flames and then smoke left along the whole way, each where and when the head passed. */
        private void trailPuffs(Frame f,double t){
            double head=travelled(Math.min(t,ticks));
            Vec3d side1=dir.crossProduct(UP);if(side1.lengthSquared()<1e-4)side1=new Vec3d(1,0,0);side1=side1.normalize();Vec3d side2=dir.crossProduct(side1).normalize();
            // smoke: a puff every 1.4 blocks, 6-12 s each, growing and drifting up with the wind
            int count=(int)Math.floor(head/1.4);
            for(int i=0;i<=count;i++){
                double d=i*1.4,born=passed(d),a=t-born,life=120+rnd(seed,i,21)*120;if(a<0||a>life)continue;
                double r1=(rnd(seed,i,22)-.5)*1.1,r2=(rnd(seed,i,23)-.5)*1.1;
                Vec3d spread=side1.multiply(r1).add(side2.multiply(r2));
                Vec3d p=at(d).add(0,MeteorMesh.of(seed).centreY,0).add(spread.multiply(1+(1-Math.exp(-a/30))*1.2)).add(0,.012*a,0).add(WIND.multiply(a));
                double size=1.2+(3.4+rnd(seed,i,24)*1.8)*(1-Math.exp(-a/45));
                float alpha=(float)(.78*smooth(0,3,a)*(1-smooth(life*.4,life,a)));
                f.smoke(p,size,.62+.25*rnd(seed,i,25),(float)(.9*Math.exp(-a/7)),alpha,seed+i*3);
            }
            // flames: a puff every 0.9 blocks, half a second or so, cooling into smoke
            int flames=(int)Math.floor(head/.9);
            for(int i=Math.max(0,flames-60);i<=flames;i++){
                double d=i*.9,born=passed(d),a=t-born,life=7+rnd(seed,i,31)*5;if(a<0||a>life)continue;
                double r1=(rnd(seed,i,32)-.5)*.9,r2=(rnd(seed,i,33)-.5)*.9;
                Vec3d p=at(d).add(0,MeteorMesh.of(seed).centreY,0).add(side1.multiply(r1)).add(side2.multiply(r2)).add(0,.03*a,0);
                double size=1.25+1.2*(a/life);
                float alpha=(float)(smooth(0,1.5,a)*(1-smooth(life*.6,life,a)));
                f.fire(p,size,(float)(1.0*Math.exp(-a/(life*.45))),(float)(.3+.5*rnd(seed,i,34)),alpha,seed+i*5);
            }
        }
        /** Sparks shed off the burning rock, two a tick, falling away behind it. */
        private void sheddings(Frame f,double t){
            int last=(int)Math.floor(Math.min(t,ticks)*2);
            for(int i=Math.max(0,last-60);i<=last;i++){
                double born=i*.5;if(born>ticks)break;double a=t-born,life=12+rnd(seed,i,41)*14;if(a<0||a>life)continue;
                Vec3d o=at(travelled(born)).add(0,MeteorMesh.of(seed).centreY,0).add((rnd(seed,i,42)-.5)*1.2,(rnd(seed,i,43)-.5)*1.2,(rnd(seed,i,44)-.5)*1.2);
                Vec3d v=dir.multiply(speed(born)*(.15+.2*rnd(seed,i,45))).add((rnd(seed,i,46)-.5)*.5,(rnd(seed,i,47)-.3)*.4,(rnd(seed,i,48)-.5)*.5);
                Vec3d p=ballistic(o,v,a,.035,.02),q=ballistic(o,v,Math.max(0,a-1.6),.035,.02);
                f.spark(p,q,.09,(float)(1-a/life*.7),(float)(1-smooth(life*.6,life,a)));
            }
        }

        /** The landing, {@code ti} ticks after it. */
        private void impact(Frame f,double ti,MatrixStack ms,VertexConsumerProvider consumers,MinecraftClient mc){
            if(ti>330)return;
            Vec3d c=to.add(0,.9,0);
            if(ti<26){
                // the flash: big seen from afar, held back close by (a few blocks off it must not white the whole view out)
                double off=to.distanceTo(f.cam);
                f.glow(c,(5+6*smooth(0,2.5,ti))*Math.min(1,.45+off/40),1f,.65f,(float)((ti<1?1:Math.exp(-(ti-1)/3.5))*Math.min(1,.55+off/60)));
                f.glow(c,6,.2f,.35f,(float)(.55*Math.exp(-ti/20)));
            }
            f.pool(to.add(0,.05,0),10,.75,Math.max(0,.7*Math.exp(-ti/20)),seed,true);
            if(ti<30){
                double r=1.5+21*(1-Math.exp(-ti/7));
                f.ring(to.add(0,.12,0),r*.55,r,seed,(float)(ti/30),(float)(.95*(1-smooth(10,30,ti))));
            }
            // the flash on the screen of those close
            double near=Math.max(0,1-to.distanceTo(f.cam)/50);
            if(near>0&&ti<14)f.flash=(float)Math.max(f.flash,.5*near*smooth(0,.8,ti)*Math.exp(-ti/3));
            // the fireball
            for(int i=0;i<18;i++){
                double life=12+rnd(seed,i,51)*8,a=ti;if(a>life)continue;
                double ang=rnd(seed,i,52)*Math.PI*2,out=.3+.3*rnd(seed,i,53);
                Vec3d v=new Vec3d(Math.cos(ang)*out,.25+.4*rnd(seed,i,54),Math.sin(ang)*out);
                Vec3d p=drag(to.add(0,.8,0),v,a,.14).add(0,.06*a,0);
                f.fire(p,1.3+1.8*(1-Math.exp(-a/6)),(float)(1.05*Math.exp(-a/6)),(float)(.25+.5*rnd(seed,i,55)),(float)(smooth(0,1,a)*(1-smooth(life*.25,life*.75,a))),seed+i*11);
            }
            // the dark column rising out of the hole
            for(int i=0;i<22;i++){
                double born=1+i*2.6,a=ti-born,life=100+rnd(seed,i,61)*70;if(a<0||a>life)continue;
                Vec3d o=to.add((rnd(seed,i,62)-.5)*1.8,.7,(rnd(seed,i,63)-.5)*1.8);
                // quick at first out of the hole, then a steady rise that slows a little as it spreads
                double rise=.5*(1-Math.exp(-a/4))*4+(.11+.07*rnd(seed,i,64))*a*(1-a/(life*3));
                Vec3d p=o.add(0,rise,0).add(WIND.multiply(a*(.8+.6*rnd(seed,i,65))));
                double size=.8+(1.2+.9*rnd(seed,i,66))*(1-Math.exp(-a/35))+rise*.08;
                f.smoke(p,size,.58+.22*rnd(seed,i,67),(float)(.9*Math.exp(-a/9)),(float)(.5*smooth(0,3,a)*(1-smooth(life*.35,life,a))),seed+i*17);
            }
            // the dust racing out along the ground
            for(int i=0;i<26;i++){
                double born=rnd(seed,i,71)*4,a=ti-born,life=34+rnd(seed,i,72)*26;if(a<0||a>life)continue;
                double ang=(i+rnd(seed,i,73)*.8)/26*Math.PI*2,r0=1.3+rnd(seed,i,74)*1.4;
                Vec3d o=to.add(Math.cos(ang)*r0,.35,Math.sin(ang)*r0);
                Vec3d v=new Vec3d(Math.cos(ang),0,Math.sin(ang)).multiply(.45+.3*rnd(seed,i,75));
                double size=.8+1.9*(1-Math.exp(-a/14));
                // it rolls out along the ground, its middle never higher than its own size
                Vec3d p=drag(o,v,a,.1).add(0,Math.min(size*.55,.006*a),0).add(WIND.multiply(a));
                f.smoke(p,size,.2+.2*rnd(seed,i,76),0,(float)(.72*smooth(0,2,a)*(1-smooth(life*.35,life,a)))*f.groundFade(p),seed+i*23+64);
            }
            // sparks spraying out, bouncing off nothing: they go out as they reach the ground
            for(int i=0;i<64;i++){
                double life=14+rnd(seed,i,81)*26,a=ti;if(a>life)continue;
                double ang=rnd(seed,i,82)*Math.PI*2,up=.25+.75*rnd(seed,i,83),sp=.45+.65*rnd(seed,i,84);
                Vec3d v=new Vec3d(Math.cos(ang)*sp*(1.1-up*.6),up*sp,Math.sin(ang)*sp*(1.1-up*.6));
                Vec3d o=to.add(0,.7,0);
                Vec3d p=ballistic(o,v,a,.03,.045),q=ballistic(o,v,Math.max(0,a-1.4),.03,.045);
                if(p.y<to.y+.05)continue;
                f.spark(p,q,.08,(float)(1-a/life*.8),(float)(1-smooth(life*.55,life,a)));
            }
            // earth and stone thrown out, bouncing to rest, then sinking out of sight
            for(int i=0;i<20;i++){
                double a=ti,end=150+rnd(seed,i,91)*40;if(a>end)continue;
                double ang=rnd(seed,i,92)*Math.PI*2,sp=.16+.26*rnd(seed,i,93),size=.2+.3*rnd(seed,i,94);
                Vec3d v=new Vec3d(Math.cos(ang)*sp,.38+.38*rnd(seed,i,95),Math.sin(ang)*sp);
                double[] s=bounce(to.add(Math.cos(ang)*.8,.6,Math.sin(ang)*.8),v,a,to.y+size*.5);
                float shrink=(float)(1-smooth(end-25,end,a));
                f.block(i%3==0?Blocks.BLACKSTONE.getDefaultState():ground,new Vec3d(s[0],s[1],s[2]),size*shrink,s[3]*(1+rnd(seed,i,96)),i,f.groundFade(new Vec3d(s[0],s[1],s[2]))>0);
            }
            // embers drifting up out of the hole for a few seconds
            for(int i=0;i<24;i++){
                double born=rnd(seed,i,101)*30,a=ti-born,life=30+rnd(seed,i,102)*30;if(a<0||a>life)continue;
                Vec3d p=to.add((rnd(seed,i,103)-.5)*3+Math.sin(a*.2+i)*.3,.6+(.06+.05*rnd(seed,i,104))*a,(rnd(seed,i,105)-.5)*3+Math.cos(a*.17+i)*.3);
                f.glow(p,.12+rnd(seed,i,106)*.08,.8f,.35f,(float)(smooth(0,3,a)*(1-smooth(life*.5,life,a))));
            }
        }
    }

    /** The boom of a landing, heard as late as sound takes to come (one tick per 17 blocks). */
    static void boom(Vec3d at){
        var mc=MinecraftClient.getInstance();if(mc.player==null)return;
        int delay=(int)Math.round(mc.gameRenderer.getCamera().getPos().distanceTo(at)/17.15);
        mc.getSoundManager().play(new PositionedSoundInstance(MeteorRegistry.IMPACT_SOUND,SoundCategory.BLOCKS,1f,.95f+(float)Math.random()*.1f,SoundInstance.createRandom(),at.x,at.y+1,at.z),delay);
    }
    /** Called every client tick: the booms of the landings that have just happened. */
    static void sounds(){
        double now=MeteorClient.clock(0);
        for(var f:FALLS){double ti=now-f.zero-f.ticks;if(ti>=0&&!f.boomed){f.boomed=true;if(ti<10)boom(f.to);}}
    }

    // ---------------------------------------------------------------- mining

    /** What flies off the crystal a pickaxe strikes: sparks, chips and dust; a fragment coming off bursts in gold. */
    private record Burst(Vec3d rock,MeteorMesh.Cluster cluster,int seed,double zero,boolean big){
        void collect(Frame f,double now,MatrixStack ms,VertexConsumerProvider consumers,MinecraftClient mc){
            double a=now-zero;
            Vec3d at=rock.add(cluster.x(),cluster.y(),cluster.z()),n=new Vec3d(cluster.nx(),cluster.ny(),cluster.nz());
            int sparks=big?30:14;
            for(int i=0;i<sparks;i++){
                double life=8+rnd(seed,i,1)*(big?16:9);if(a>life)continue;
                Vec3d v=n.multiply(.18+.3*rnd(seed,i,2)).add((rnd(seed,i,3)-.5)*.5,.12+.2*rnd(seed,i,4),(rnd(seed,i,5)-.5)*.5);
                Vec3d p=ballistic(at,v,a,.05,.04),q=ballistic(at,v,Math.max(0,a-1.2),.05,.04);
                if(p.y<rock.y)continue;
                f.spark(p,q,.05,(float)(.9-a/life*.5),(float)(1-smooth(life*.5,life,a)));
            }
            for(int i=0;i<(big?9:3);i++){
                double life=big?14:7;if(a>life)continue;
                Vec3d p=at.add(n.multiply(.3)).add((rnd(seed,i,11)-.5)*.6,(rnd(seed,i,12)-.2)*.5,(rnd(seed,i,13)-.5)*.6).add(n.multiply(a*.02));
                f.glint(p,(.25+.25*rnd(seed,i,14))*(big?1.4:1),(float)rnd(seed,i,15),(float)(Math.sin(Math.PI*Math.min(1,a/life))));
            }
            if(big&&a<10)f.glow(at.add(n.multiply(.3)),2.6,.9f,.85f,(float)(Math.exp(-a/3)));
            for(int i=0;i<(big?6:2);i++){
                double life=16+rnd(seed,i,21)*10;if(a>life)continue;
                Vec3d p=drag(at,n.multiply(.08).add((rnd(seed,i,22)-.5)*.12,.03,(rnd(seed,i,23)-.5)*.12),a,.1).add(0,.015*a,0);
                f.smoke(p,.45+.9*(1-Math.exp(-a/6)),.22,0,(float)(.7*smooth(0,1.5,a)*(1-smooth(life*.4,life,a))),seed+i*7+90);
            }
            int chips=big?11:3;
            for(int i=0;i<chips;i++){
                double end=big?60:36;if(a>end)continue;
                boolean gold=big&&i<6;double size=gold?.12+.07*rnd(seed,i,31):.1+.08*rnd(seed,i,31);
                Vec3d v=n.multiply(.12+.15*rnd(seed,i,32)).add((rnd(seed,i,33)-.5)*.25,.18+.16*rnd(seed,i,34),(rnd(seed,i,35)-.5)*.25);
                double[] s=bounce(at,v,a,rock.y+size*.5);
                f.block(gold?Blocks.RAW_GOLD_BLOCK.getDefaultState():Blocks.BLACKSTONE.getDefaultState(),new Vec3d(s[0],s[1],s[2]),size*(1-smooth(end-10,end,a)),s[3]*1.7,i,true);
            }
        }
    }

    /** A rock mined out (its pieces burst apart) or crumbling with age (they sink into the ground in dust). */
    private record Ending(Vec3d at,int seed,double landedAt,double zero,boolean mined,int fragments){
        void collect(Frame f,double now,MatrixStack ms,VertexConsumerProvider consumers,MinecraftClient mc){
            double a=now-zero;
            var mesh=MeteorMesh.of(seed);
            int light=WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(at.x,at.y+.5,at.z));
            double age=now-landedAt;
            for(int k=0;k<mesh.pieces.length;k++){
                var piece=mesh.pieces[k];
                double out=mined?.07:.018,spin=(rnd(seed,k,1)-.5)*(mined?.16:.05);
                double life=mined?55:110,sink=mined?Math.max(0,a-22)*.03:Math.max(0,a-8)*.022;
                double scale=1-smooth(life*.55,life,a);if(scale<=0)continue;
                Vec3d d=new Vec3d(piece.dx(),0,piece.dz());
                double up=mined?(.12*a-.5*.03*a*a):0;
                Vec3d off=d.multiply(out*a/(1+a*.03)).add(0,Math.max(-.6,up)-sink,0);
                ms.push();ms.translate(at.x+off.x,at.y+off.y,at.z+off.z);
                ms.translate(piece.cx(),piece.cy(),piece.cz());
                ms.multiply(new Quaternionf().rotateAxis((float)(spin*a),(float)-piece.dz(),0,(float)piece.dx()));
                ms.scale((float)scale,(float)scale,(float)scale);
                ms.translate(-piece.cx(),-piece.cy(),-piece.cz());
                MeteorRenderer.drawPiece(ms,consumers,piece.tris(),age+a,light);
                ms.pop();
            }
            if(fragments>0&&a<12){
                // the crystals still in it crack off with it
                ms.push();ms.translate(at.x,at.y-a*.03,at.z);
                MeteorRenderer.drawClusters(ms,consumers,mesh,fragments,age,light,(float)(1-a/12));
                ms.pop();
            }
            ms.push();ms.translate(at.x,at.y,at.z);
            MeteorRenderer.drawScorch(ms,consumers,seed,age+a,light,(float)(1-smooth(40,140,a)));
            ms.pop();
            if(a<30)f.ring(at.add(0,.1,0),1,1.5+(mined?7:5)*(1-Math.exp(-a/6)),seed,(float)(a/30),(float)(.8*(1-smooth(8,30,a))));
            for(int i=0;i<(mined?14:18);i++){
                double born=rnd(seed,i,41)*(mined?4:14),b=a-born,life=40+rnd(seed,i,42)*40;if(b<0||b>life)continue;
                double ang=rnd(seed,i,43)*Math.PI*2;
                Vec3d o=at.add(Math.cos(ang)*mesh.radius*.6,.3+rnd(seed,i,44)*.6,Math.sin(ang)*mesh.radius*.6);
                Vec3d p=drag(o,new Vec3d(Math.cos(ang),0,Math.sin(ang)).multiply(mined?.16:.06),b,.08).add(0,.02*b,0).add(WIND.multiply(b));
                f.smoke(p,.8+2.2*(1-Math.exp(-b/16)),.32+.2*rnd(seed,i,45),0,(float)(.8*smooth(0,2,b)*(1-smooth(life*.45,life,b))),seed+i*13+33);
            }
            for(int i=0;i<(mined?12:16);i++){
                double end=mined?70:90;if(a>end)continue;double born=mined?0:rnd(seed,i,51)*16,b=a-born;if(b<0)continue;
                double ang=rnd(seed,i,52)*Math.PI*2,size=.13+.12*rnd(seed,i,53);
                Vec3d v=new Vec3d(Math.cos(ang)*(mined?.22:.07),mined?.3+.2*rnd(seed,i,54):.05,Math.sin(ang)*(mined?.22:.07));
                double[] s=bounce(at.add(Math.cos(ang)*.6,mesh.centreY+.3,Math.sin(ang)*.6),v,b,at.y+size*.5);
                f.block(Blocks.BLACKSTONE.getDefaultState(),new Vec3d(s[0],s[1],s[2]),size*(1-smooth(end-12,end,a)),s[3]*1.5,i,true);
            }
        }
    }

    // ---------------------------------------------------------------- motion

    /** Thrown with velocity v (blocks a tick), slowed by drag k and pulled down by g; at age a. */
    private static Vec3d ballistic(Vec3d o,Vec3d v,double a,double k,double g){
        double s=(1-Math.exp(-k*a))/k;
        return o.add(v.multiply(s)).add(0,-g*(a-s)/k,0);
    }
    private static Vec3d drag(Vec3d o,Vec3d v,double a,double k){return o.add(v.multiply((1-Math.exp(-k*a))/k));}
    /**
     * A thrown piece bouncing on flat ground at {@code floor}: {x, y, z, spin angle} at age a. It loses most of its
     * speed at each bounce and lies still after the third.
     */
    private static double[] bounce(Vec3d o,Vec3d v,double a,double floor){
        double x=o.x,y=o.y,z=o.z,vx=v.x,vy=v.y,vz=v.z,left=a,turned=0;
        for(int hop=0;hop<3;hop++){
            // y + vy t - g t^2 / 2 = floor
            double disc=vy*vy+2*GRAVITY*(y-floor);double hit=disc<0?0:(vy+Math.sqrt(disc))/GRAVITY;
            if(left<=hit)return new double[]{x+vx*left,Math.max(floor,y+vy*left-GRAVITY*left*left/2),z+vz*left,turned+left*.35};
            x+=vx*hit;z+=vz*hit;y=floor;left-=hit;turned+=hit*.35;
            double down=vy-GRAVITY*hit;
            vy=-down*.32;vx*=.55;vz*=.55;
            if(vy<.03)break;
        }
        // then it slides to a stop over six ticks
        double slide=Math.min(left,6),k=slide-slide*slide/12;
        return new double[]{x+vx*k,floor,z+vz*k,turned};
    }

    static double smooth(double e0,double e1,double x){double t=Math.max(0,Math.min(1,(x-e0)/(e1-e0)));return t*t*(3-2*t);}
    /** A draw in 0..1 fixed by the seed, the particle and what it is for. */
    static double rnd(int seed,int i,int k){
        long h=seed*0x9E3779B97F4A7C15L+i*0xC2B2AE3D27D4EB4FL+k*0x165667B19E3779F9L;
        h^=h>>>33;h*=0xff51afd7ed558ccdL;h^=h>>>33;h*=0xc4ceb9fe1a85ec53L;h^=h>>>33;
        return (h>>>11)*0x1.0p-53;
    }

    // ---------------------------------------------------------------- one frame's drawing

    /** What a frame draws, gathered first and then drawn in order: decals, puffs back to front, light, the flash. */
    private static final class Frame {
        final Vec3d cam;final float age;final double horizon;
        final List<float[]> cells=new ArrayList<>(),rings=new ArrayList<>(),glows=new ArrayList<>(),pools=new ArrayList<>(),marks=new ArrayList<>(),sparks=new ArrayList<>(),glints=new ArrayList<>(),axials=new ArrayList<>(),trails=new ArrayList<>();
        final List<Object[]> blocks=new ArrayList<>();
        float flash,soft,ambient=1;
        Frame(Vec3d cam,float age,double horizon){this.cam=cam;this.age=age;this.horizon=Math.max(64,horizon);}
        /** Things on the ground far beyond the land that is drawn fade out (they would hang over nothing). */
        float groundFade(Vec3d p){return (float)(1-smooth(horizon*.85,horizon*1.05,Math.hypot(p.x-cam.x,p.z-cam.z)));}
        void fire(Vec3d p,double size,float heat,float wisp,float alpha,int seed){if(alpha>.004)cells.add(new float[]{(float)p.x,(float)p.y,(float)p.z,(float)size,seed&127,heat,wisp,alpha});}
        void smoke(Vec3d p,double size,double dark,float ember,float alpha,int seed){if(alpha>.004)cells.add(new float[]{(float)p.x,(float)p.y,(float)p.z,(float)size,128+(seed&127),(float)dark,ember,alpha});}
        void glow(Vec3d p,double size,float white,float hue,float alpha){if(alpha>.004)glows.add(new float[]{(float)p.x,(float)p.y,(float)p.z,(float)size,white,hue,alpha});}
        void glint(Vec3d p,double size,float white,float alpha){if(alpha>.004)glints.add(new float[]{(float)p.x,(float)p.y,(float)p.z,(float)size,white,alpha});}
        void spark(Vec3d head,Vec3d tail,double width,float heat,float alpha){if(alpha>.004)sparks.add(new float[]{(float)head.x,(float)head.y,(float)head.z,(float)tail.x,(float)tail.y,(float)tail.z,(float)width,heat,alpha});}
        void pool(Vec3d p,double radius,double heat,double alpha,int seed,boolean ground){float a=(float)alpha*(ground?groundFade(p):1);if(a>.004)pools.add(new float[]{(float)p.x,(float)p.y,(float)p.z,(float)radius,(float)heat,a,seed&127});}
        void mark(Vec3d p,double radius,float strength){float a=strength*groundFade(p);if(a>.004)marks.add(new float[]{(float)p.x,(float)p.y,(float)p.z,(float)radius,a});}
        void ring(Vec3d p,double r0,double r1,int seed,float progress,float alpha){float a=alpha*groundFade(p);if(a>.004)rings.add(new float[]{(float)p.x,(float)p.y,(float)p.z,(float)r0,(float)r1,seed&127,progress,a});}
        void axial(int mode,Vec3d front,Vec3d back,double w0,double w1,Vec3d dir,int seed,float g,float alpha){if(alpha>.004)axials.add(new float[]{mode,(float)front.x,(float)front.y,(float)front.z,(float)back.x,(float)back.y,(float)back.z,(float)w0,(float)w1,seed&127,g,alpha});}
        void trail(Vec3d head,Vec3d dir,double back,double w0,double w1,int seed,float heat,float alpha){if(alpha>.004&&back>.5)trails.add(new float[]{(float)head.x,(float)head.y,(float)head.z,(float)dir.x,(float)dir.y,(float)dir.z,(float)back,(float)w0,(float)w1,seed&127,heat,alpha});}
        void block(BlockState state,Vec3d p,double size,double angle,int i,boolean shown){if(shown&&size>.01)blocks.add(new Object[]{state,p,size,angle,i});}

        void draw(MatrixStack ms,Camera camera){
            var mc=MinecraftClient.getInstance();Matrix4f m=ms.peek().getPositionMatrix();
            if(!blocks.isEmpty()){
                var consumers=mc.getBufferBuilders().getEntityVertexConsumers();
                for(Object[] b:blocks){
                    Vec3d p=(Vec3d)b[1];double size=(double)b[2],angle=(double)b[3];int i=(int)b[4];
                    ms.push();ms.translate(p.x,p.y,p.z);
                    ms.multiply(new Quaternionf().rotateY((float)(angle+i)).rotateX((float)(angle*.71)));
                    ms.scale((float)size,(float)size,(float)size);ms.translate(-.5,-.5,-.5);
                    mc.getBlockRenderManager().renderBlockAsEntity((BlockState)b[0],ms,consumers,WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(p)),OverlayTexture.DEFAULT_UV);
                    ms.pop();
                }
                consumers.draw();
            }
            soft=captureDepth();
            RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();
            Vec3d look=new Vec3d(camera.getHorizontalPlane().x(),camera.getHorizontalPlane().y(),camera.getHorizontalPlane().z());
            Vec3d upv=new Vec3d(camera.getVerticalPlane().x(),camera.getVerticalPlane().y(),camera.getVerticalPlane().z());
            Vec3d side=look.crossProduct(upv).normalize();
            // decals on the ground, then the puffs back to front, then light
            var b=begin(blend,5);for(float[] r:rings)annulus(b,m,r[0],r[1],r[2],r[3],r[4],(int)r[5],r[6],0,r[7]);end(b);
            b=begin(blend,1);
            cells.sort(Comparator.comparingDouble((float[] c)->-sq(c[0]-cam.x,c[1]-cam.y,c[2]-cam.z)));
            for(float[] c:cells)cell(b,m,c);
            end(b);
            b=begin(glow,9);for(float[] p:pools)flat(b,m,p[0],p[1],p[2],p[3],(int)p[6],p[4],0,p[5]);end(b);
            b=begin(glow,6);for(float[] k:marks)annulus(b,m,k[0],k[1],k[2],0,k[3],0,1,0,k[4]);end(b);
            b=begin(glow,4);for(float[] t:trails)trail(b,m,t);end(b);
            b=begin(glow,3);for(float[] a:axials)axial(b,m,a);end(b);
            b=begin(glow,7);for(float[] s:sparks)streak(b,m,s);end(b);
            b=begin(glow,2);for(float[] g:glows)facing(b,m,g[0],g[1],g[2],g[3],side,upv,0,g[4],g[5],g[6]);end(b);
            b=begin(glow,8);for(float[] g:glints)facing(b,m,g[0],g[1],g[2],g[3],side,upv,0,g[4],0,g[5]);end(b);
            if(flash>.004){
                RenderSystem.disableDepthTest();
                b=begin(glow,2);
                Vec3d p=cam.add(look.multiply(.3));
                facing(b,m,p.x,p.y,p.z,4,side,upv,0,1,.8f,Math.min(1,flash));
                end(b);
                RenderSystem.enableDepthTest();
            }
        }
        private BufferBuilder begin(ShaderProgram shader,int mode){
            RenderSystem.setShader(()->shader);shader.getUniform("Age").set(age%100000f);shader.getUniform("Mode").set(mode);
            var melt=shader.getUniform("Soft");if(melt!=null)melt.set(soft);
            var day=shader.getUniform("Ambient");if(day!=null)day.set(ambient);
            // the program sets its blending only when it differs from the last one bound: set it here as well
            RenderSystem.enableBlend();
            if(shader==glow)RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
            else RenderSystem.defaultBlendFunc();
            var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);return b;
        }
        private static void end(BufferBuilder b){var built=b.end();BufferRenderer.drawWithGlobalProgram(built);}
        private static double sq(double x,double y,double z){return x*x+y*y+z*z;}
        private static void v(BufferBuilder b,Matrix4f m,double x,double y,double z,float u,float w,int code,float g,float bl,float a){
            b.vertex(m,(float)x,(float)y,(float)z).texture(u,w).color(code&255,Math.round(Math.max(0,Math.min(1,g))*255),Math.round(Math.max(0,Math.min(1,bl))*255),Math.round(Math.max(0,Math.min(1,a))*255)).next();
        }
        /** A puff on three upright planes 60° apart, turned by its seed only (like the game's fire block). */
        private void cell(BufferBuilder b,Matrix4f m,float[] c){
            int code=(int)c[4];double s=c[3];
            double turn=((code*2654435761L)&0xffff)/65536.0*Math.PI/3;
            for(int k=0;k<3;k++){
                double ang=turn+k*Math.PI/3,ux=Math.cos(ang)*s,uz=Math.sin(ang)*s;
                v(b,m,c[0]-ux,c[1]-s,c[2]-uz,0,0,code,c[5],c[6],c[7]);v(b,m,c[0]+ux,c[1]-s,c[2]+uz,1,0,code,c[5],c[6],c[7]);
                v(b,m,c[0]+ux,c[1]+s,c[2]+uz,1,1,code,c[5],c[6],c[7]);v(b,m,c[0]-ux,c[1]+s,c[2]-uz,0,1,code,c[5],c[6],c[7]);
            }
        }
        /** A flat ring on the ground (uv: round it, and from its inner edge out). */
        private static void annulus(BufferBuilder b,Matrix4f m,double x,double y,double z,double r0,double r1,int code,float g,float bl,float a){
            int n=48;
            for(int i=0;i<n;i++){
                double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;float u0=i/(float)n,u1=(i+1)/(float)n;
                v(b,m,x+Math.cos(a0)*r0,y,z+Math.sin(a0)*r0,u0,0,code,g,bl,a);v(b,m,x+Math.cos(a0)*r1,y,z+Math.sin(a0)*r1,u0,1,code,g,bl,a);
                v(b,m,x+Math.cos(a1)*r1,y,z+Math.sin(a1)*r1,u1,1,code,g,bl,a);v(b,m,x+Math.cos(a1)*r0,y,z+Math.sin(a1)*r0,u1,0,code,g,bl,a);
            }
        }
        private static void flat(BufferBuilder b,Matrix4f m,double x,double y,double z,double r,int code,float g,float bl,float a){
            v(b,m,x-r,y,z-r,0,0,code,g,bl,a);v(b,m,x+r,y,z-r,1,0,code,g,bl,a);v(b,m,x+r,y,z+r,1,1,code,g,bl,a);v(b,m,x-r,y,z+r,0,1,code,g,bl,a);
        }
        private static void facing(BufferBuilder b,Matrix4f m,double x,double y,double z,double s,Vec3d side,Vec3d up,int code,float g,float bl,float a){
            Vec3d u=side.multiply(s),w=up.multiply(s);
            v(b,m,x-u.x-w.x,y-u.y-w.y,z-u.z-w.z,0,0,code,g,bl,a);v(b,m,x+u.x-w.x,y+u.y-w.y,z+u.z-w.z,1,0,code,g,bl,a);
            v(b,m,x+u.x+w.x,y+u.y+w.y,z+u.z+w.z,1,1,code,g,bl,a);v(b,m,x-u.x+w.x,y-u.y+w.y,z-u.z+w.z,0,1,code,g,bl,a);
        }
        /** A spark's streak from its tail to its head, turned about its own line to face the camera. */
        private void streak(BufferBuilder b,Matrix4f m,float[] s){
            Vec3d head=new Vec3d(s[0],s[1],s[2]),tail=new Vec3d(s[3],s[4],s[5]);
            Vec3d along=head.subtract(tail);if(along.lengthSquared()<1e-6)along=new Vec3d(0,.02,0);
            Vec3d across=along.crossProduct(head.subtract(cam)).normalize().multiply(s[6]);
            if(!Double.isFinite(across.x))return;
            v(b,m,tail.x-across.x,tail.y-across.y,tail.z-across.z,0,0,0,s[7],0,s[8]);v(b,m,tail.x+across.x,tail.y+across.y,tail.z+across.z,0,1,0,s[7],0,s[8]);
            v(b,m,head.x+across.x,head.y+across.y,head.z+across.z,1,1,0,s[7],0,s[8]);v(b,m,head.x-across.x,head.y-across.y,head.z-across.z,1,0,0,s[7],0,s[8]);
        }
        /** Three planes 60° apart round a line (the way the rock goes), never turned to the camera: from any side one shows. */
        private static Vec3d[] planes(Vec3d dir,int seed){
            Vec3d s1=dir.crossProduct(UP);if(s1.lengthSquared()<1e-4)s1=new Vec3d(1,0,0);s1=s1.normalize();Vec3d s2=dir.crossProduct(s1).normalize();
            double turn=seed*.37;Vec3d[] out=new Vec3d[3];
            for(int k=0;k<3;k++){double a=turn+k*Math.PI/3;out[k]=s1.multiply(Math.cos(a)).add(s2.multiply(Math.sin(a)));}
            return out;
        }
        private static void axial(BufferBuilder b,Matrix4f m,float[] a){
            Vec3d front=new Vec3d(a[1],a[2],a[3]),back=new Vec3d(a[4],a[5],a[6]);Vec3d dir=front.subtract(back).normalize();
            int code=(int)a[9];
            for(Vec3d s:planes(dir,code)){
                Vec3d s0=s.multiply(a[7]),s1=s.multiply(a[8]);
                v(b,m,front.x-s0.x,front.y-s0.y,front.z-s0.z,0,0,code,a[10],0,a[11]);v(b,m,front.x+s0.x,front.y+s0.y,front.z+s0.z,0,1,code,a[10],0,a[11]);
                v(b,m,back.x+s1.x,back.y+s1.y,back.z+s1.z,1,1,code,a[10],0,a[11]);v(b,m,back.x-s1.x,back.y-s1.y,back.z-s1.z,1,0,code,a[10],0,a[11]);
            }
        }
        /** The trail behind the head along its way, narrowing to the tail, in eighteen pieces on the same three planes. */
        private static void trail(BufferBuilder b,Matrix4f m,float[] t){
            Vec3d head=new Vec3d(t[0],t[1],t[2]),dir=new Vec3d(t[3],t[4],t[5]);double back=t[6];int code=(int)t[9];
            int n=18;
            for(Vec3d s:planes(dir,code+5)){
                for(int i=0;i<n;i++){
                    double k0=i/(double)n,k1=(i+1)/(double)n;
                    Vec3d p0=head.subtract(dir.multiply(back*k0)),p1=head.subtract(dir.multiply(back*k1));
                    double w0=t[7]+(t[8]-t[7])*Math.sqrt(k0),w1=t[7]+(t[8]-t[7])*Math.sqrt(k1);
                    Vec3d a0=s.multiply(w0),a1=s.multiply(w1);
                    v(b,m,p0.x-a0.x,p0.y-a0.y,p0.z-a0.z,(float)k0,0,code,t[10],0,t[11]);v(b,m,p0.x+a0.x,p0.y+a0.y,p0.z+a0.z,(float)k0,1,code,t[10],0,t[11]);
                    v(b,m,p1.x+a1.x,p1.y+a1.y,p1.z+a1.z,(float)k1,1,code,t[10],0,t[11]);v(b,m,p1.x-a1.x,p1.y-a1.y,p1.z-a1.z,(float)k1,0,code,t[10],0,t[11]);
                }
            }
        }
    }

    /** The roar of a fall, following the rock down (stopped as it lands; the landing has its own sound). */
    private static final class FallSound extends MovingSoundInstance {
        private final Fall fall;
        FallSound(Fall fall){
            super(MeteorRegistry.FALL_SOUND,SoundCategory.BLOCKS,SoundInstance.createRandom());
            this.fall=fall;volume=1f;pitch=.94f+(float)rnd(fall.seed,0,99)*.12f;repeat=false;attenuationType=AttenuationType.LINEAR;
            place();
        }
        private void place(){double t=fall.t(0);Vec3d p=fall.at(fall.travelled(Math.min(t,fall.ticks)));x=p.x;y=p.y;z=p.z;}
        @Override public void tick(){
            if(!FALLS.contains(fall)||fall.t(0)>=fall.ticks+1){setDone();return;}
            place();
        }
    }
}
