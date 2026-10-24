package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Light rising off a Star Platinum that is out (2.52.0, user request: "몸에서 이펙트가 떠올라도 괜찮을듯"): motes and thin
 * streaks of its aura's pale violet blue leave the body (the chest, shoulders, arms, head, the hair streaming behind it and
 * the legs), drift up and a little outward, sway, twinkle and fade. They are light, not fire or smoke, so each is a soft
 * glowing point that faces the camera (a streak turns only about its own way up): a violet heart that shows against a
 * bright sky, under a halo and a white core of light that adds to what is behind it. A spark starts where the body was in
 * the world when it left it, so a stand on the move leaves a trail of them; its path and brightness are smooth functions
 * of its own age, so it moves at the frame rate. Bursting into its pose throws off many more.
 */
final class StandAura {
    /** Sparks a second off a stand at rest, and more for a gust of 1 (bursting into its pose). */
    static final float RATE=30,BURST=90;
    /** Never more than this many at once. */
    static final int MOST=160;
    /** Of every spark, how many are streaks (the rest are motes). */
    static final float STREAKS=.25f;
    /**
     * The parts of the body sparks leave from, as boxes round them in model units (x across, y up from the soles, z
     * backwards), how often, and whether on both sides. A spark leaves from SKIN outside its box, so it shows as it
     * leaves rather than inside the body.
     */
    private static final float[][] BODY={
        {-6,6,19,35,-3.6f,3.2f,4,0},          // chest and belly
        {3,12,32.5f,38,-4.5f,4.3f,3,1},       // shoulders and their pads
        {5.2f,9.8f,17,34,-2.4f,2.6f,3,1},     // arms
        {-3.2f,3.2f,37,44.5f,-3.6f,3.7f,1.5f,0}, // head
        {-5,5,40,53,1.5f,12,3,0},             // the hair streaming behind it
        {.3f,5.4f,2,20,-2.8f,2.8f,2,1}};      // legs
    static final float SKIN=1;
    private static RenderLayer layer,hearts;

    /** A spark: when it left the body (client clock, ticks) and where, its way up and out (blocks a second), how long it lasts (s), its size (blocks). */
    record Spark(double born,float life,double x,double y,double z,float vx,float vy,float vz,float size,boolean streak,float seed){
        /** Where it is {@code age} seconds after it left: rising a little slower as it goes, swaying. */
        Vec3d at(float age){
            float rise=age*(1-.3f*Math.min(1,age/life));
            return new Vec3d(x+vx*age+.045f*Math.sin(2.1f*age+seed*6.3f),y+vy*rise,z+vz*age+.045f*Math.cos(1.7f*age+seed*4.1f));
        }
    }
    final List<Spark> sparks=new ArrayList<>();
    private final Random random;
    private float owed;
    StandAura(){this(Random.create());}
    StandAura(Random random){this.random=random;}

    boolean isEmpty(){return sparks.isEmpty();}
    int size(){return sparks.size();}

    /** How bright a spark is {@code age} seconds into a life of {@code life}: in quickly, twinkling, out slowly over its second half. */
    static float brightness(float age,float life,float seed){
        if(age<0||age>life)return 0;
        float in=StandTimeline.smooth(age/.18f),out=1-StandTimeline.smooth((age-.45f*life)/(.55f*life));
        return in*out*(.78f+.22f*(float)Math.sin(11*age+20*seed));
    }

    /**
     * Once a tick: the sparks that leave the body over the tick the frames to come will show (from {@code start} on the
     * client clock, the feet going from {@code from} to {@code to}, facing {@code yaw}, at {@code scale}), {@code rate} a
     * second; and those that have faded out by its start gone.
     */
    void tick(double start,Vec3d from,Vec3d to,float yaw,float scale,float rate){
        sparks.removeIf(s->(start-s.born)/20>=s.life);
        owed+=rate/20f;int n=(int)owed;owed-=n;
        double r=Math.toRadians(yaw),fx=-Math.sin(r),fz=Math.cos(r);
        float unit=scale*StarPlatinumRenderer.SCALE/16;
        for(int i=0;i<n&&sparks.size()<MOST;i++){
            float f=(i+random.nextFloat())/n;var feet=from.lerp(to,f);
            float[] b=part();double round=2*Math.PI*random.nextFloat();
            float x=(b[0]+b[1])/2+((b[1]-b[0])/2+SKIN)*(float)Math.cos(round),z=(b[4]+b[5])/2+((b[5]-b[4])/2+SKIN)*(float)Math.sin(round);
            float y=b[2]+(b[3]-b[2])*random.nextFloat();
            if(b[7]>0&&random.nextBoolean())x=-x;
            // the idle stance floats 2.5 units up; across is the right hand (Minecraft: 0 faces +z, the right is -x)
            y+=2.5f;
            double ox=(-fz*x-fx*z)*unit,oz=(fx*x-fz*z)*unit,ol=Math.max(.05,Math.hypot(ox,oz));
            boolean streak=random.nextFloat()<STREAKS;
            float outward=.04f+.08f*random.nextFloat();
            float vx=(float)(ox/ol*outward)+(random.nextFloat()-.5f)*.08f,vz=(float)(oz/ol*outward)+(random.nextFloat()-.5f)*.08f;
            float vy=streak?.9f+.5f*random.nextFloat():.35f+.4f*random.nextFloat();
            float life=streak?.45f+.35f*random.nextFloat():1f+.9f*random.nextFloat();
            float size=(streak?.03f+.015f*random.nextFloat():.045f+.04f*random.nextFloat())*scale;
            sparks.add(new Spark(start+f,life,feet.x+ox,feet.y+y*unit,feet.z+oz,vx,vy,vz,size,streak,random.nextFloat()));
        }
    }
    private float[] part(){
        float total=0;for(float[] b:BODY)total+=b[6];
        float pick=random.nextFloat()*total;
        for(float[] b:BODY){pick-=b[6];if(pick<=0)return b;}
        return BODY[0];
    }

    /** Draws the sparks at {@code clock} (client clock, ticks) for {@code camera}. */
    void render(double clock,Camera camera,Matrix4f m,VertexConsumerProvider buffers){
        if(sparks.isEmpty())return;
        layer();
        var cam=camera.getPos();var rot=camera.getRotation();
        var right=new Vector3f(1,0,0).rotate(rot);var up=new Vector3f(0,1,0).rotate(rot);
        // the violet hearts first (blended over what is behind them), then the light that adds on top of them
        for(int pass=0;pass<2;pass++){
            VertexConsumer vc=buffers.getBuffer(pass==0?hearts:layer);
            for(var s:sparks){
                float age=(float)((clock-s.born)/20),a=brightness(age,s.life,s.seed);
                if(a<=.003f)continue;
                var p=s.at(age).subtract(cam);
                a*=near(p.length());
                if(a<=.003f)continue;
                if(s.streak){
                    // a thin line of light along its way up, turned about it to face the camera
                    var axis=new Vector3f(s.vx,s.vy,s.vz).normalize();
                    var side=new Vector3f(axis).cross((float)-p.x,(float)-p.y,(float)-p.z);
                    if(side.lengthSquared()<1e-8f)side.set(right);
                    side.normalize();
                    float len=s.size*4.5f,w=s.size*.55f;
                    if(pass==0)quad(vc,m,p,axis,len*.9f,side,w*.8f,HEART_R,HEART_G,HEART_B,.6f*a);
                    else{quad(vc,m,p,axis,len,side,w,AURA_R*.65f*a,AURA_G*.65f*a,AURA_B*.65f*a,1);quad(vc,m,p,axis,len*.8f,side,w*.4f,.9f*a,.93f*a,a,1);}
                }else{
                    // a violet heart, a soft halo of the aura's colour round it and a white core
                    if(pass==0)quad(vc,m,p,up,s.size*1.3f,right,s.size*1.3f,HEART_R,HEART_G,HEART_B,.75f*a);
                    else{quad(vc,m,p,up,s.size*2.6f,right,s.size*2.6f,AURA_R*.65f*a,AURA_G*.65f*a,AURA_B*.65f*a,1);quad(vc,m,p,up,s.size*.8f,right,s.size*.8f,a,a,a,1);}
                }
            }
        }
    }
    /**
     * How much of a spark shows {@code distance} blocks from the camera: none right by it, all from 1.25 blocks on. A
     * camera following its user walks into the trail a stand leaves, and a spark it passed through would fill the screen.
     */
    static float near(double distance){return StandTimeline.smooth((float)((distance-.35)/.9));}
    /** The hearts' violet (a little deeper than the aura's light, so it shows on a bright sky). */
    static final float HEART_R=.58f,HEART_G=.46f,HEART_B=1f;
    static final float AURA_R=StarPlatinumRenderer.AURA_R,AURA_G=StarPlatinumRenderer.AURA_G,AURA_B=StarPlatinumRenderer.AURA_B;
    /** A quad centred on {@code p} (camera-relative), {@code h} along {@code v} and {@code w} along {@code u} either way. */
    private static void quad(VertexConsumer vc,Matrix4f m,Vec3d p,Vector3f v,float h,Vector3f u,float w,float r,float g,float b,float alpha){
        float x=(float)p.x,y=(float)p.y,z=(float)p.z;
        vertex(vc,m,x-u.x*w-v.x*h,y-u.y*w-v.y*h,z-u.z*w-v.z*h,0,1,r,g,b,alpha);
        vertex(vc,m,x+u.x*w-v.x*h,y+u.y*w-v.y*h,z+u.z*w-v.z*h,1,1,r,g,b,alpha);
        vertex(vc,m,x+u.x*w+v.x*h,y+u.y*w+v.y*h,z+u.z*w+v.z*h,1,0,r,g,b,alpha);
        vertex(vc,m,x-u.x*w+v.x*h,y-u.y*w+v.y*h,z-u.z*w+v.z*h,0,0,r,g,b,alpha);
    }
    private static void vertex(VertexConsumer vc,Matrix4f m,float x,float y,float z,float u,float v,float r,float g,float b,float alpha){
        vc.vertex(m,x,y,z).color(r,g,b,alpha).texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,1,0).next();
    }

    /**
     * Sparks are drawn like the glowing eyes of mobs (unshaded, behind nothing they do not reach) from a soft round spot
     * made here once (brighter in the middle), smoothly filtered, from either side: their light adds up over what is
     * behind it, and their violet hearts are the same spot in see-through white, blended over it (the game's own
     * see-through glowing layer would cut the spot's faint edge off and shade it by the world's light).
     */
    private static RenderLayer layer(){
        if(layer!=null)return layer;
        Identifier mote=CelestialFall.id("stand/aura_mote"),heart=CelestialFall.id("stand/aura_heart");
        var light=new NativeImage(NativeImage.Format.RGBA,64,64,false);
        var white=new NativeImage(NativeImage.Format.RGBA,64,64,false);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){
            double dx=(x+.5-32)/32,dy=(y+.5-32)/32,d2=dx*dx+dy*dy,d=Math.sqrt(d2);
            double edge=d>=1?0:d<=.8?1:1-StandTimeline.smooth((float)((d-.8)/.2));
            int c=(int)Math.round(255*Math.min(1,(Math.exp(-5*d2)+.55*Math.exp(-45*d2))*edge));
            light.setColor(x,y,c<<24|c<<16|c<<8|c);
            white.setColor(x,y,c<<24|0xFFFFFF);
        }
        var textures=MinecraftClient.getInstance().getTextureManager();
        textures.registerTexture(mote,new NativeImageBackedTexture(light));
        textures.registerTexture(heart,new NativeImageBackedTexture(white));
        hearts=Layers.sparks("celestialfall_stand_aura_hearts",heart,false);
        return layer=Layers.sparks("celestialfall_stand_aura",mote,true);
    }
    /** Only to reach the game's protected render-layer parts; never made. */
    private static final class Layers extends RenderLayer {
        private Layers(){super("",VertexFormats.POSITION,VertexFormat.DrawMode.QUADS,0,false,false,()->{},()->{});}
        static RenderLayer sparks(String name,Identifier texture,boolean adds){
            return of(name,VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,VertexFormat.DrawMode.QUADS,1024,false,true,
                MultiPhaseParameters.builder().program(EYES_PROGRAM).texture(new Texture(texture,true,false))
                    .transparency(adds?ADDITIVE_TRANSPARENCY:TRANSLUCENT_TRANSPARENCY).writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).build(false));
        }
    }
}
