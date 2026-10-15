package dev.sangwoo.celestialfall.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.SpellTimeline;
import dev.sangwoo.celestialfall.MountainScale;
import net.minecraft.world.Heightmap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Bounded client-side effects: never spawns entities, removes blocks, or changes world lighting. */
public final class ImpactEffects {
    private static final Map<UUID, Blast> BLASTS=new LinkedHashMap<>();
    private static final List<Debris> DEBRIS=new ArrayList<>();
    private static final VertexConsumerProvider.Immediate BLOCK_BUFFER=VertexConsumerProvider.immediate(new BufferBuilder(32768));
    private static final Identifier SMOKE=CelestialFall.id("textures/effect/smoke.png");
    private static final Vec3d UP=new Vec3d(0,1,0);
    private static final int MAX_DEBRIS=240, MAX_SMOKE=360;
    private static final class Ground {
        final int x,z;
        Vec3d position;
        BlockState state=Blocks.STONE.getDefaultState();
        boolean valid;
        Ground(int x,int z) { this.x=x;this.z=z; }
    }
    private record Puff(Vec3d origin,Vec3d velocity,float birth,float life,float radius,float growth,boolean dust,float phase) {}
    private record VisiblePuff(Vec3d position,double size,float alpha,float red,float green,float blue,float rotation) {}
    private static final class Blast {
        final ClientSpell spell;
        final List<Ground> ground=new ArrayList<>();
        final Map<Long,Ground> surface=new HashMap<>();
        final List<Puff> smoke=new ArrayList<>();
        boolean exploded;
        int sampleCursor,spacing,radius;
        double scale=1;
        Blast(ClientSpell spell) { this.spell=spell; }
    }
    private static final class Debris {
        final UUID spell;
        final BlockState state;
        final double size, spin, phase;
        Vec3d previous,position,velocity;
        int ticks, bounces;
        double restAngle;
        boolean resting;
        Debris(UUID spell,BlockState state,Vec3d position,Vec3d velocity,double size,double spin,double phase) {
            this.spell=spell;this.state=state;this.position=this.previous=position;
            this.velocity=velocity;this.size=size;this.spin=spin;this.phase=phase;
        }
    }
    private ImpactEffects() {}
    private static int quality(int low,int mid,int high) {
        return CelestialFall.CONFIG.visualQuality==0?low:CelestialFall.CONFIG.visualQuality==1?mid:high;
    }
    public static void clear() { BLASTS.clear();DEBRIS.clear(); }
    public static void prepare(MinecraftClient mc,ClientSpell spell) {
        if(BLASTS.containsKey(spell.id))return;
        Blast blast=new Blast(spell);
        // A sparse surface grid spans an entire mountain. Heightmap samples are spread across ticks.
        var atomic=AtomicClient.CASTS.get(spell.id);
        int radius=atomic==null?MountainScale.GLOW_RADIUS:(int)Math.ceil(atomic.radius);
        blast.radius=radius;blast.scale=atomic==null?1:Math.min(1,atomic.radius/96.);
        blast.spacing=Math.max(2,(int)Math.round(quality(12,9,6)*blast.scale));
        BlockPos centre=BlockPos.ofFloored(spell.target);
        for(int x=-radius;x<=radius;x+=blast.spacing)for(int z=-radius;z<=radius;z+=blast.spacing) {
            if(x*x+z*z<=radius*radius) {
                var g=new Ground(centre.getX()+x,centre.getZ()+z);blast.ground.add(g);
                blast.surface.put(BlockPos.asLong(g.x,0,g.z),g);
            }
        }
        // Populate the important centre before the outer slopes.
        blast.ground.sort(Comparator.comparingDouble(g->Math.pow(g.x-spell.target.x,2)+Math.pow(g.z-spell.target.z,2)));
        BLASTS.put(spell.id,blast);
    }
    /** Uses the already sampled surface; no heightmap or chunk lookups in the rendering loop. */
    public static Vec3d surfacePoint(ClientSpell spell,Vec3d point) {
        Blast b=BLASTS.get(spell.id);if(b==null)return point;
        int x0=MathHelper.floor(spell.target.x)-b.radius,z0=MathHelper.floor(spell.target.z)-b.radius;
        int x=x0+(int)Math.round((point.x-x0)/b.spacing)*b.spacing,z=z0+(int)Math.round((point.z-z0)/b.spacing)*b.spacing;
        Ground g=b.surface.get(BlockPos.asLong(x,0,z));
        return g!=null && g.valid?new Vec3d(point.x,g.position.y+.25,point.z):point;
    }
    private static void sampleGround(MinecraftClient mc,Ground g) {
        g.valid=false;
        if(!mc.world.isChunkLoaded(BlockPos.ofFloored(g.x,0,g.z)))return;
        int top=mc.world.getTopY(Heightmap.Type.WORLD_SURFACE,g.x,g.z)-1;
        for(int y=top;y>=Math.max(mc.world.getBottomY(),top-8);y--) {
            BlockPos pos=new BlockPos(g.x,y,g.z);
            BlockState state=mc.world.getBlockState(pos);
            if(state.getRenderType()!=BlockRenderType.MODEL || !state.isFullCube(mc.world,pos))continue;
            g.position=new Vec3d(g.x+.5,y+1.03,g.z+.5);g.state=state;g.valid=true;return;
        }
    }
    public static void explode(MinecraftClient mc,ClientSpell spell) {
        prepare(mc,spell);
        Blast blast=BLASTS.get(spell.id);
        if(blast.exploded)return;
        blast.exploded=true;
        Random rng=new Random(spell.id.getLeastSignificantBits());
        if(CelestialFall.CONFIG.flyingBlocks && spell.age(mc.world.getTime(),0)<SpellTimeline.IMPACT+2) {
            int count=Math.min(HeavenClient.CASTS.containsKey(spell.id)?quality(32,48,64):quality(48,92,144),MAX_DEBRIS-DEBRIS.size());
            List<Ground> source=blast.ground.stream().filter(g->g.valid && g.position.subtract(spell.target).horizontalLengthSquared()
                <=Math.min(spell.visualRadius,blast.radius)*Math.min(spell.visualRadius,blast.radius)).toList();
            emitDebris(spell,blast,rng,count,source);
        }
        if(CelestialFall.CONFIG.flyingBlocks && CinematicDirector.active()==spell
            && spell.age(mc.world.getTime(),0)<SpellTimeline.IMPACT+2) {
            Vec3d dir=mc.gameRenderer.getCamera().getPos().subtract(spell.target).multiply(1,0,1).normalize();
            Vec3d side=new Vec3d(-dir.z,0,dir.x);
            for(int i=0;i<6 && DEBRIS.size()<MAX_DEBRIS;i++) {
                Vec3d from=spell.target.add(dir.multiply(25)).add(side.multiply((i-2.5)*9)).add(0,3+i,0);
                Vec3d velocity=dir.multiply(10+i*.7).add(0,3+i*.5,0);
                DEBRIS.add(new Debris(spell.id,Blocks.STONE.getDefaultState(),from,velocity,6+i*1.4,.045,i));
            }
        }
        if(CelestialFall.CONFIG.explosionSmoke && !AtomicClient.CASTS.containsKey(spell.id)) {
            int existing=BLASTS.values().stream().mapToInt(b->b.smoke.size()).sum();
            int count=Math.min(quality(64,112,184),MAX_SMOKE-existing);
            for(int i=0;i<count;i++) {
                boolean gold=HeavenClient.CASTS.containsKey(spell.id),dust=i%3==0;
                double a=rng.nextDouble()*Math.PI*2;
                double r=dust?18+rng.nextDouble()*42:rng.nextDouble()*28;
                Vec3d origin=spell.target.add(Math.cos(a)*r,dust?1.5:rng.nextDouble()*(gold?8:60),Math.sin(a)*r);
                if(gold)origin=surfacePoint(spell,origin).add(0,dust?1.25:rng.nextDouble()*6,0);
                Vec3d velocity=new Vec3d(Math.cos(a)*(dust?1.8:.24),dust?.11:.65+rng.nextDouble()*.65,Math.sin(a)*(dust?1.8:.24));
                blast.smoke.add(new Puff(origin,velocity,(float)(rng.nextDouble()*(gold?(i%2==0?12:80):(dust?15:50))),
                    (float)(gold?110+rng.nextDouble()*65:230+rng.nextDouble()*105),gold?10f:dust?14f:16f,(float)(.13+rng.nextDouble()*.20),dust,rng.nextFloat()*6.28f));
            }
        }
    }
    private static void emitDebris(ClientSpell spell,Blast blast,Random rng,int count,List<Ground> source) {
            for(int i=0;i<count;i++) {
                Ground g=source.isEmpty()?null:source.get(rng.nextInt(source.size()));
                Vec3d start=g==null?spell.target:g.position;
                BlockState material=g==null?Blocks.STONE.getDefaultState():g.state;
                double angle=Math.atan2(start.z-spell.target.z,start.x-spell.target.x)+(rng.nextDouble()-.5)*.35;
                double speed=(1.3+rng.nextDouble()*2.3)*blast.scale;
                double size=(i%12==0?10+rng.nextDouble()*6:2+rng.nextDouble()*6)*blast.scale;
                Vec3d velocity=new Vec3d(Math.cos(angle)*speed,(2.3+rng.nextDouble()*2.5)*blast.scale,Math.sin(angle)*speed);
                DEBRIS.add(new Debris(spell.id,material,start.add(0,size*.72+.05,0),velocity,
                    size,.015+rng.nextDouble()*.045,rng.nextDouble()*Math.PI*2));
            }
    }
    public static void heavenPressureBurst(MinecraftClient mc,ClientSpell spell,int stage) {
        Blast blast=BLASTS.get(spell.id);var cast=HeavenClient.CASTS.get(spell.id);
        if(blast==null || cast==null || !blast.exploded || !CelestialFall.CONFIG.flyingBlocks)return;
        if(cast.age(0)>HeavenImpact.burstAge(stage)+2)return;
        int count=Math.min(quality(10,18,28),MAX_DEBRIS-DEBRIS.size());
        List<Ground> source=blast.ground.stream().filter(g->g.valid && g.position.subtract(spell.target).horizontalLengthSquared()<85*85).toList();
        emitDebris(spell,blast,new Random(spell.id.getLeastSignificantBits()+stage*701L),count,source);
    }
    public static void tick(MinecraftClient mc) {
        BLASTS.entrySet().removeIf(e->!CelestialFallClient.SPELLS.containsKey(e.getKey()) && !AtomicClient.CASTS.containsKey(e.getKey()) && !HeavenClient.CASTS.containsKey(e.getKey()));
        int sampleBudget=128;
        for(Blast blast:BLASTS.values()) {
            int samples=Math.min(sampleBudget,64);
            for(int i=0;i<samples && !blast.ground.isEmpty();i++) {
                sampleGround(mc,blast.ground.get(blast.sampleCursor++%blast.ground.size()));
            }
            sampleBudget-=samples;
        }
        Iterator<Debris> iterator=DEBRIS.iterator();
        while(iterator.hasNext()) {
            Debris d=iterator.next();
            if(++d.ticks>=300 || (!CelestialFallClient.SPELLS.containsKey(d.spell) && !AtomicClient.CASTS.containsKey(d.spell) && !HeavenClient.CASTS.containsKey(d.spell))) { iterator.remove();continue; }
            d.previous=d.position;
            if(d.resting)continue;
            Vec3d next=d.position.add(d.velocity);
            double foot=d.size*.7;
            BlockHitResult hit=mc.world.raycast(new RaycastContext(d.position.add(0,-foot,0),next.add(0,-foot,0),
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
            if(hit.getType()!=HitResult.Type.MISS) {
                Vec3d normal=Vec3d.of(hit.getSide().getVector());
                d.position=hit.getPos().add(0,foot,0).add(normal.multiply(.025));
                d.velocity=d.velocity.subtract(normal.multiply(1.38*d.velocity.dotProduct(normal))).multiply(.63);
                if(++d.bounces>=3 || d.velocity.lengthSquared()<.015) { d.resting=true;d.restAngle=d.phase+d.ticks*d.spin;d.velocity=Vec3d.ZERO; }
            } else {
                d.position=next;
                d.velocity=d.velocity.multiply(.994).add(0,-.07,0);
            }
        }
    }
    public static void renderBlocks(WorldRenderContext context) {
        if(DEBRIS.isEmpty() && BLASTS.isEmpty())return;
        MinecraftClient mc=MinecraftClient.getInstance();
        MatrixStack matrices=context.matrixStack();
        Vec3d camera=context.camera().getPos();
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
        matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);
        try {
            if(CelestialFall.CONFIG.flyingBlocks)for(Blast blast:BLASTS.values()) {
                if(HeavenClient.CASTS.containsKey(blast.spell.id))continue;
                float age=blast.spell.age(mc.world.getTime(),context.tickDelta());
                if(age<32 || age>=SpellTimeline.CONTACT)continue;
                float lift=SpellTimeline.smooth(SpellTimeline.progress(age,32,120));
                int shown=0;
                for(Ground ground:blast.ground) {
                    if(!ground.valid)continue;
                    int index=shown++;
                    if(index>=quality(12,24,40))break;
                    Vec3d p=ground.position.add(0,1+lift*(2+index%7)+Math.sin(age*.06+index)*.3,0);
                    matrices.push();matrices.translate(p.x,p.y,p.z);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotation(age*.015f+index));
                    float size=.35f+(index%5)*.17f;matrices.scale(size,size,size);matrices.translate(-.5,-.5,-.5);
                    mc.getBlockRenderManager().renderBlockAsEntity(ground.state,matrices,BLOCK_BUFFER,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);
                    matrices.pop();
                }
            }
            for(Debris d:DEBRIS) {
                float t=d.ticks+context.tickDelta();
                float shrink=1-SpellTimeline.smooth(SpellTimeline.progress(t,255,300));
                if(shrink<=0)continue;
                Vec3d p=d.previous.lerp(d.position,context.tickDelta());
                matrices.push();matrices.translate(p.x,p.y,p.z);
                double angle=d.phase+t*d.spin;
                // Rested fragments settle without endlessly rotating on the floor.
                if(d.resting)angle=d.restAngle;
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)angle));
                matrices.multiply(RotationAxis.POSITIVE_X.rotation((float)(angle*.71)));
                float size=(float)d.size*shrink;matrices.scale(size,size,size);matrices.translate(-.5,-.5,-.5);
                mc.getBlockRenderManager().renderBlockAsEntity(d.state,matrices,BLOCK_BUFFER,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);
                matrices.pop();
            }
            BLOCK_BUFFER.draw();
        } finally { matrices.pop(); }
    }
    static void glow(EffectMesh mesh,ClientSpell spell,float age) {
        if(!CelestialFall.CONFIG.purpleGlow)return;
        Blast blast=BLASTS.get(spell.id);
        if(blast==null)return;
        float time=age-SpellTimeline.IMPACT;
        float pulse=time<0?SpellTimeline.visibility(age)*.12f+SpellTimeline.clashPulse(age)*.18f
            :(float)(.32+.68*Math.exp(-time/65))*SpellTimeline.visibility(age);
        float strength=pulse*(float)CelestialFall.CONFIG.purpleGlowStrength;
        if(strength<=0)return;
        for(Ground g:blast.ground) {
            if(!g.valid || MinecraftClient.getInstance().world.getBlockState(BlockPos.ofFloored(g.position.add(0,-.1,0))).isAir())continue;
            Vec3d p=g.position;
            double half=blast.spacing*.51;
            for(Vec3d offset:new Vec3d[]{new Vec3d(-half,0,-half),new Vec3d(half,0,-half),new Vec3d(half,0,half),new Vec3d(-half,0,half)}) {
                Vec3d v=p.add(offset);
                float fade=(float)Math.max(0,1-v.subtract(spell.target).horizontalLengthSquared()/(MountainScale.GLOW_RADIUS*MountainScale.GLOW_RADIUS));
                mesh.vertex(v,0xB646FF,strength*fade*fade*.9f);
            }
        }
        Vec3d base=spell.target.add(0,.3,0);
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4+age*.003;
            Vec3d lo=base.add(Math.cos(a)*18,0,Math.sin(a)*18);
            Vec3d hi=base.add(Math.cos(a+.1)*45,240+70*Math.sin(a),Math.sin(a+.1)*45);
            mesh.glowLine(lo,hi,12,0x9F3FFF,strength*.18f);
        }
    }
    public static void renderSmoke(WorldRenderContext context,MatrixStack matrices) {
        MinecraftClient mc=MinecraftClient.getInstance();
        Vec3d camera=context.camera().getPos();
        List<VisiblePuff> visible=new ArrayList<>();
        for(Blast b:BLASTS.values()) {
            float age=b.spell.age(mc.world.getTime(),context.tickDelta())-SpellTimeline.IMPACT;
            for(Puff p:b.smoke) {
                float t=age-p.birth;
                if(t<0 || t>=p.life)continue;
                float opacity=SpellTimeline.smooth(SpellTimeline.progress(t,0,9))
                    *(1-SpellTimeline.smooth(SpellTimeline.progress(t,p.life*.45f,p.life)));
                float hot=(float)Math.exp(-age/70);
                double spread=190*(1-Math.exp(-t/190));
                Vec3d pos=p.origin.add(p.velocity.x*spread,p.velocity.y*t,p.velocity.z*spread)
                    .add(Math.sin(t*.014+p.phase)*9,0,Math.cos(t*.012+p.phase)*9);
                boolean gold=HeavenClient.CASTS.containsKey(b.spell.id);
                if(gold)opacity*=dev.sangwoo.celestialfall.HeavenTimeline.visibility(HeavenClient.CASTS.get(b.spell.id).age(context.tickDelta()));
                visible.add(new VisiblePuff(pos,p.radius+t*p.growth,opacity*(p.dust?.46f:.45f),
                    gold?.26f+.60f*hot:.22f+.46f*hot,gold?.23f+.31f*hot:.17f+.17f*hot,
                    gold?.21f+.06f*hot:.29f+.53f*hot,p.phase+t*.003f));
            }
        }
        if(visible.isEmpty())return;
        visible.sort(Comparator.comparingDouble((VisiblePuff p)->-p.position.squaredDistanceTo(camera)));
        Vector3f right=new Vector3f(1,0,0).rotate(context.camera().getRotation());
        Vector3f up=new Vector3f(0,1,0).rotate(context.camera().getRotation());
        Vec3d r=new Vec3d(right.x,right.y,right.z),u=new Vec3d(up.x,up.y,up.z);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0,SMOKE);RenderSystem.setShaderColor(1,1,1,1);
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        Matrix4f matrix=matrices.peek().getPositionMatrix();
        for(VisiblePuff p:visible) {
            Vec3d a=r.multiply(Math.cos(p.rotation)).add(u.multiply(Math.sin(p.rotation))).multiply(p.size);
            Vec3d b=u.multiply(Math.cos(p.rotation)).subtract(r.multiply(Math.sin(p.rotation))).multiply(p.size);
            smokeVertex(buffer,matrix,p.position.subtract(a).subtract(b),0,1,p);
            smokeVertex(buffer,matrix,p.position.add(a).subtract(b),1,1,p);
            smokeVertex(buffer,matrix,p.position.add(a).add(b),1,0,p);
            smokeVertex(buffer,matrix,p.position.subtract(a).add(b),0,0,p);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
    private static void smokeVertex(BufferBuilder buffer,Matrix4f matrix,Vec3d v,float u,float w,VisiblePuff p) {
        buffer.vertex(matrix,(float)v.x,(float)v.y,(float)v.z).texture(u,w).color(p.red,p.green,p.blue,p.alpha).next();
    }
}
