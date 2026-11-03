package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * Lee So-sang's G as drawn (2.83.0, user request: "이번에는 G스킬 궁극기 시전자만 컷씬이 나오고 범위는 30블록 효과음과 대사는
 * 내가 영상에 보냈고 저거 2개중에 랜덤으로 하나 나오게 하면 돼 컷씬은 저거랑 똑같이 나오게", the user's two videos of it read on
 * a sheet and their answers; the cutscene filmed in the world itself: "지금 맵에서 찍음 (배경은 실제 월드)").
 * <p>Everyone sees her raise her free hand, light gathering at its tip, sweep it up and draw a stroke of gold in the air,
 * bring it before her face (a cross of light flaring there), lean back and cry out as a whirl of cloud opens over her and a
 * giant golden tablet, carved all over, shows in its eye; the tablet falls point first and strikes the ground ahead of her
 * (IsosangRules.G_AHEAD) and the ground bursts (MeteorFx.blast: a meteor's landing, bigger); it darkens and crumbles, and
 * fire leaps up out of the ground round her as dust rolls over everything.
 * <p>She alone sees it as a cutscene, her own camera's shots of it as the videos have them ({@link #camera}): her hand
 * close in the dark (the world around it gone black, {@link #dark}), the camera whipping round it as the light sweeps up,
 * along her forearm to the fingertip, her face behind her raised hand, up at her from below with the whirl and the tablet
 * over her, the tablet coming down, its strike seen from the ground, and back to the game. Which face (the server's choice,
 * the variant): the first video's (her face straight on), or the second's (and then her face from the side, smiling).
 */
final class JadeTablet {
    private JadeTablet(){}
    /** How long it is drawn (seconds): until the dust has cleared; and, called off, how long after that its tablet takes to go. */
    static final float LIFE=IsosangRules.G_CLEAR+.4f,GONE=.6f;
    static final Vec3d UP=new Vec3d(0,1,0);
    static final int GOLD=0xffc46a,GOLD_WHITE=0xffe9b8,WHITE=0xffffff,SHARD=0x3d8dff,EYE=0xffdca0;

    // ───────────────────────────── the body ─────────────────────────────

    // (Her free hand does it all, her sword held low at her side: it rises in front of her, pointing across to her right;
    // sweeps up to point at the sky over her left; draws a stroke across; comes before her face, the hand at her nose; then
    // she leans back with her arms spread, looking up at the whirl; points down at where the tablet will strike; watches it
    // and lowers her hand. The body faces the way she faced, as every hit's.)
    static final R6.Pose G_RAISE=w(0,0,-6,2,0,d(-74,10),d(18,16),d(8,30),d(-90,3),d(-90,-3));
    static final R6.Pose G_SWEEP=w(0,0,-10,-2,0,d(-74,10),d(16,18),d(56,-24),d(-90,3),d(-90,-3)).head(-8,-14,0);
    static final R6.Pose G_POINT=w(0,0,-12,-4,0,d(-74,10),d(16,18),d(64,-30),d(-90,3),d(-90,-3)).head(-10,-20,0);
    static final R6.Pose G_POINT2=w(0,0,-12,-4,0,d(-74,10),d(16,18),d(66,-26),d(-90,3),d(-90,-3)).head(-10,-22,0);
    static final R6.Pose G_STROKE=w(0,0,-4,-2,0,d(-74,10),d(16,18),d(50,22),d(-90,3),d(-90,-3)).head(-2,-12,0);
    static final R6.Pose G_FACE=w(0,0,0,2,0,d(-76,12),d(14,20),d(30,42),d(-90,3),d(-90,-3)).head(0,4,0);
    static final R6.Pose G_FACE2=w(0,0,0,2,0,d(-76,12),d(14,20),d(32,40),d(-90,3),d(-90,-3)).head(2,6,2);
    static final R6.Pose G_SKY=w(0,0,0,-14,0,d(-40,70),d(-28,92),d(40,-74),d(-88,6),d(-88,-6)).head(0,-34,0);
    static final R6.Pose G_SKY2=w(0,0,0,-18,0,d(-34,76),d(-24,96),d(46,-82),d(-88,6),d(-88,-6)).head(0,-40,0);
    static final R6.Pose G_CALL=w(0,0,0,6,0,d(-60,30),d(-10,40),d(-6,2),d(-90,3),d(-90,-3)).head(0,10,0);
    static final R6.Pose G_CALL2=w(0,0,0,4,0,d(-62,28),d(-6,36),d(-10,4),d(-90,3),d(-90,-3)).head(0,8,0);
    static final R6.Pose G_WATCH=w(0,0,0,2,0,d(-68,16),d(20,10),d(-44,-12),d(-90,3),d(-90,-3)).head(0,4,0);
    static final Anim CLIP=Anim.joints(
        k(0,STANCE),
        k(.16,G_RAISE,Anim.Ease.OUT),
        k(.3,G_RAISE),
        k(.44,G_SWEEP,Anim.Ease.OUT),
        k(.62,G_POINT),
        k(.83,G_POINT2),
        k(.96,G_STROKE,Anim.Ease.OUT),
        k(1.06,G_FACE,Anim.Ease.OUT),
        k(1.5,G_FACE2),
        k(1.66,G_SKY,Anim.Ease.OUT),
        k(2.6,G_SKY2),
        k(2.8,G_CALL,Anim.Ease.OUT),
        k(3.3,G_CALL2),
        k(3.9,G_WATCH),
        k(IsosangRules.G_HOLD/20f,STANCE)).after(IsosangSlash::grounded);

    /** Her free hand and its fingertip at {@code t} as drawn, standing at {@code feet} facing {@code yaw}. */
    static Vec3d hand(Vec3d feet,float yaw,float t){return KagenoClips.freeHand(feet,yaw,R6.poseOf(CLIP.frame(t)));}
    static Vec3d tip(Vec3d feet,float yaw,float t){
        var p=R6.poseOf(CLIP.frame(t));Vec3d h=KagenoClips.freeHand(feet,yaw,p),s=KagenoClips.freeShoulder(feet,yaw,p);
        Vec3d d=h.subtract(s);return d.lengthSquared()<1e-6?h:h.add(d.normalize().multiply(.13));
    }
    /** Her head's middle at {@code t} as drawn. */
    static Vec3d head(Vec3d feet,float yaw,float t){return KagenoClips.head(feet,yaw,R6.poseOf(CLIP.frame(t)));}

    // ───────────────────────────── where it is ─────────────────────────────

    static Vec3d forward(StandClient.Cast c){return Vec3d.fromPolar(0,c.yaw());}
    /** Where its tablet strikes the ground (the server's word, else as far ahead of where she stood as it strikes). */
    static Vec3d strike(StandClient.Cast c){var u=IsosangClient.ultimate(c.id());return u!=null?u.strike():c.origin().add(forward(c).multiply(IsosangRules.G_AHEAD));}
    /** Which recording and face (0 the first video's, 1 the second's). */
    static int variant(StandClient.Cast c){var u=IsosangClient.ultimate(c.id());return u==null?0:u.variant();}
    /** The tablet's middle as it hangs in the sky: behind her and high over her. */
    static Vec3d skyCentre(StandClient.Cast c){return c.origin().subtract(forward(c).multiply(IsosangRules.G_SKY_BACK)).add(0,IsosangRules.G_SKY_UP,0);}
    /** The whirl's eye: just over the tablet's top as it hangs. */
    static Vec3d eye(StandClient.Cast c){return skyCentre(c).add(0,IsosangRules.TABLET_HEIGHT*.5+1.2,0);}
    /** When it was called off (seconds into it), or NaN. */
    static float off(StandClient.Cast c){return IsosangClient.ultimateEnd(c.id());}

    /** The times of the tablet (seconds): it shows in the whirl's eye, hangs there, falls (G_FALL to G_STRIKE), stands struck, darkens, crumbles. */
    static final float SHOW0=1.32f,SHOW1=1.62f,DARK0=4.35f,DARK1=4.85f,CRUMBLE0=4.85f,CRUMBLE1=5.4f;
    /** How far it leans as it hangs and as it stands struck (degrees: its top back toward her). */
    static final double HANG_LEAN=8,STRUCK_LEAN=16;
    /** Its thickness (blocks). */
    static final double THICK=.9;
    /**
     * The tablet at a moment: its point (the bottom tip), its long axis (from its point to its top), its face's normal, its
     * size (1 full), how solid it is, how hot it glows (0 to 1) and how dark it has gone (0 to 1).
     */
    record Tablet(Vec3d point,Vec3d axis,Vec3d normal,double scale,float alpha,float heat,float dark){
        Vec3d at(double x,double y,double z){Vec3d across=axis.crossProduct(normal);return point.add(across.multiply(x*scale)).add(axis.multiply(y*scale)).add(normal.multiply(z*scale));}
        Vec3d across(){return axis.crossProduct(normal);}
    }
    static Vec3d lean(Vec3d f,double deg){double a=Math.toRadians(deg);return UP.multiply(Math.cos(a)).subtract(f.multiply(Math.sin(a))).normalize();}
    static Vec3d leanNormal(Vec3d f,double deg){double a=Math.toRadians(deg);return f.multiply(Math.cos(a)).add(UP.multiply(Math.sin(a))).normalize();}
    /** The tablet at {@code t} (null while it is not there). */
    static Tablet tablet(StandClient.Cast c,float t){
        if(t<SHOW0||t>=CRUMBLE1)return null;
        float gone=off(c);
        float alpha=1;
        if(!Float.isNaN(gone)){if(t>=gone+GONE)return null;alpha=1-Ease.smooth(t,gone,gone+GONE);}
        Vec3d f=forward(c);double h=IsosangRules.TABLET_HEIGHT;
        Vec3d hangAxis=lean(f,HANG_LEAN),hangNormal=leanNormal(f,HANG_LEAN);
        Vec3d hangPoint=skyCentre(c).subtract(hangAxis.multiply(h*.5));
        Vec3d struckAxis=lean(f,STRUCK_LEAN),struckNormal=leanNormal(f,STRUCK_LEAN);
        Vec3d struckPoint=strike(c).subtract(struckAxis.multiply(IsosangRules.G_SINK));
        if(t<IsosangRules.G_FALL){
            // shows: out of the whirl's eye, growing and settling into place; then hangs, bobbing a little
            float e=Ease.smooth(t,SHOW0,SHOW1);
            double bob=.25*Math.sin((t-SHOW0)*2.6);
            Vec3d p=hangPoint.add(0,(1-e)*3.5+bob,0);
            return new Tablet(p,hangAxis,hangNormal,.72+.28*e,alpha*e,1,0);
        }
        if(t<IsosangRules.G_STRIKE){
            // falls point first, quicker and quicker, turning to its struck lean
            float u=(t-IsosangRules.G_FALL)/(IsosangRules.G_STRIKE-IsosangRules.G_FALL);double q=u*u;
            Vec3d from=hangPoint.add(0,.25*Math.sin((IsosangRules.G_FALL-SHOW0)*2.6),0);
            Vec3d axis=hangAxis.lerp(struckAxis,q).normalize(),normal=hangNormal.lerp(struckNormal,q).normalize();
            return new Tablet(from.lerp(struckPoint,q),axis,normal,1,alpha,1,0);
        }
        // struck: a jolt as it bites, then still; its glow dying down to embers, then dark; crumbling away
        double recoil=.18*Math.exp(-(t-IsosangRules.G_STRIKE)/.08)*Math.sin((t-IsosangRules.G_STRIKE)*55);
        float heat=1-.5f*Ease.smooth(t,IsosangRules.G_STRIKE+.1f,DARK0)-.5f*Ease.smooth(t,DARK0,DARK1);
        float dark=.8f*Ease.smooth(t,DARK0,DARK1);
        float crumble=Ease.smooth(t,CRUMBLE0,CRUMBLE1);
        Vec3d p=struckPoint.add(struckAxis.multiply(recoil)).add(0,-1.2*crumble,0);
        return new Tablet(p,struckAxis,struckNormal,1,alpha*(1-crumble),Math.max(0,heat),dark);
    }

    // ───────────────────────────── its shape ─────────────────────────────

    /** Its half width at {@code y} blocks up from its point (tools/isosang/make_tablet.py draws the same outline). */
    static double half(double y){
        double h=IsosangRules.TABLET_HEIGHT,r=IsosangRules.TABLET_WIDTH/2,yb=.4*h,ym=.7*h;
        if(y<0||y>h)return 0;
        if(y<yb){double u=y/yb;return r*(1-Math.pow(1-u,1.8));}
        if(y<ym)return r;
        double v=(y-ym)/(h-ym);return r*Math.sqrt(Math.max(0,1-v*v));
    }
    /** Its outline, going round (right side up from the point, left side down from the top): {x, y} in blocks. */
    static final double[][] OUTLINE=outline();
    private static double[][] outline(){
        double h=IsosangRules.TABLET_HEIGHT;int n=36;
        java.util.List<double[]> pts=new java.util.ArrayList<>();
        for(int i=0;i<=n;i++){double y=h*Math.pow(i/(double)n,1.0);pts.add(new double[]{half(y),y});}
        for(int i=n-1;i>0;i--){double y=h*Math.pow(i/(double)n,1.0);pts.add(new double[]{-half(y),y});}
        return pts.toArray(new double[0][]);
    }
    static final Identifier FACE=CelestialFall.id("textures/effect/isosang_tablet.png"),FACE_GLOW=CelestialFall.id("textures/effect/isosang_tablet_glow.png"),
        SIDE=CelestialFall.id("textures/effect/isosang_tablet_side.png"),VORTEX=CelestialFall.id("textures/effect/isosang_vortex.png");

    // ───────────────────────────── drawing it (with the entities: it hides what is behind it) ─────────────────────────────

    /** The tablet and the whirl of cloud for every G under way (AFTER_ENTITIES: solid, so the dust and the fire meet it). */
    static void models(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();if(mc.world==null||!MapClient.enabled())return;
        var buffers=ctx.consumers();if(buffers==null)return;
        Vec3d cam=ctx.camera().getPos();float delta=ctx.tickDelta();var ms=ctx.matrixStack();
        for(var c:IsosangClient.SWINGS.values()){
            if(c.combo()!=IsosangRules.ULTIMATE)continue;
            float t=c.age(delta)/20f;
            vortex(c,t,ms.peek().getPositionMatrix(),ms.peek().getNormalMatrix(),buffers,cam);
            var tab=tablet(c,t);
            if(tab!=null&&tab.alpha()>.004f)draw(tab,ms.peek().getPositionMatrix(),ms.peek().getNormalMatrix(),buffers,cam);
        }
    }
    private static void vertex(VertexConsumer vc,Matrix4f m,Matrix3f nm,Vec3d p,Vec3d cam,float u,float v,Vec3d n,float r,float g,float b,float a){
        vc.vertex(m,(float)(p.x-cam.x),(float)(p.y-cam.y),(float)(p.z-cam.z)).color(r,g,b,a).texture(u,v).overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(nm,(float)n.x,(float)n.y,(float)n.z).next();
    }
    /** The tablet: its two carved faces, its sides, and its glow over the faces. */
    static void draw(Tablet t,Matrix4f m,Matrix3f nm,net.minecraft.client.render.VertexConsumerProvider buffers,Vec3d cam){
        double w=IsosangRules.TABLET_WIDTH,h=IsosangRules.TABLET_HEIGHT,z=THICK*.5;
        // darkening to bronze as it dies down
        float k=1-.62f*t.dark();float r=k,g=k*(1-.08f*t.dark()),b=k*(1-.18f*t.dark());
        var face=buffers.getBuffer(RenderLayer.getEntityTranslucent(FACE));
        Vec3d mid=new Vec3d(0,h*.55,0);
        for(int s=-1;s<=1;s+=2){
            Vec3d n=t.normal().multiply(s);
            for(int i=0;i<OUTLINE.length;i++){
                double[] a=OUTLINE[i],bb=OUTLINE[(i+1)%OUTLINE.length];
                Vec3d pc=t.at(mid.x,mid.y,s*z),pa=t.at(a[0],a[1],s*z),pb=t.at(bb[0],bb[1],s*z);
                float uc=uv(mid.x,s),vc=1-(float)(mid.y/h),ua=uv(a[0],s),va=1-(float)(a[1]/h),ub=uv(bb[0],s),vb=1-(float)(bb[1]/h);
                vertex(face,m,nm,pc,cam,uc,vc,n,r,g,b,t.alpha());vertex(face,m,nm,pa,cam,ua,va,n,r,g,b,t.alpha());
                vertex(face,m,nm,pb,cam,ub,vb,n,r,g,b,t.alpha());vertex(face,m,nm,pb,cam,ub,vb,n,r,g,b,t.alpha());
            }
        }
        // its sides round the outline
        var side=buffers.getBuffer(RenderLayer.getEntityTranslucent(SIDE));
        double run=0;
        for(int i=0;i<OUTLINE.length;i++){
            double[] a=OUTLINE[i],bb=OUTLINE[(i+1)%OUTLINE.length];
            double len=Math.hypot(bb[0]-a[0],bb[1]-a[1]);
            Vec3d out=t.across().multiply((a[0]+bb[0])*.5).add(t.axis().multiply((a[1]+bb[1])*.5-h*.55));
            Vec3d edge=t.at(bb[0],bb[1],0).subtract(t.at(a[0],a[1],0));
            Vec3d n=edge.crossProduct(t.normal());if(n.dotProduct(out)<0)n=n.multiply(-1);n=n.normalize();
            float u0=(float)(run/2),u1=(float)((run+len)/2);run+=len;
            vertex(side,m,nm,t.at(a[0],a[1],-z),cam,u0,0,n,r*.9f,g*.9f,b*.9f,t.alpha());vertex(side,m,nm,t.at(bb[0],bb[1],-z),cam,u1,0,n,r*.9f,g*.9f,b*.9f,t.alpha());
            vertex(side,m,nm,t.at(bb[0],bb[1],z),cam,u1,1,n,r*.9f,g*.9f,b*.9f,t.alpha());vertex(side,m,nm,t.at(a[0],a[1],z),cam,u0,1,n,r*.9f,g*.9f,b*.9f,t.alpha());
        }
        // its glow: the carving's edges catching the light, strongest as it hangs in the sky and falls
        float glow=t.heat()*t.alpha()*(1-t.dark());
        if(glow>.01f){
            var eyes=buffers.getBuffer(RenderLayer.getEyes(FACE_GLOW));
            for(int s=-1;s<=1;s+=2){
                Vec3d n=t.normal().multiply(s);
                for(int i=0;i<OUTLINE.length;i++){
                    double[] a=OUTLINE[i],bb=OUTLINE[(i+1)%OUTLINE.length];
                    Vec3d pc=t.at(mid.x,mid.y,s*(z+.02)),pa=t.at(a[0],a[1],s*(z+.02)),pb=t.at(bb[0],bb[1],s*(z+.02));
                    float uc=uv(mid.x,s),vc=1-(float)(mid.y/h),ua=uv(a[0],s),va=1-(float)(a[1]/h),ub=uv(bb[0],s),vb=1-(float)(bb[1]/h);
                    vertex(eyes,m,nm,pc,cam,uc,vc,n,glow,glow,glow,1);vertex(eyes,m,nm,pa,cam,ua,va,n,glow,glow,glow,1);
                    vertex(eyes,m,nm,pb,cam,ub,vb,n,glow,glow,glow,1);vertex(eyes,m,nm,pb,cam,ub,vb,n,glow,glow,glow,1);
                }
            }
        }
    }
    /** Across the face's texture (its back mirrored, so it reads the same way from behind). */
    private static float uv(double x,int side){float u=.5f+(float)(x/IsosangRules.TABLET_WIDTH);return side>0?u:1-u;}

    /** The whirl's times (seconds): it opens, holds, and thins away once the tablet has fallen out of it. */
    static final float WHIRL0=1.15f,WHIRL1=1.6f,WHIRL_OUT0=2.95f,WHIRL_OUT1=3.8f;
    /** How wide it is (blocks), how deep its eye goes up into it, and how fast it turns (radians a second). */
    static final double WHIRL_R=15,WHIRL_DEPTH=4.5,WHIRL_SPIN=.55;
    /** The whirl of cloud over her: two layers of it turning, the upper one slower, its eye lifting up into it. */
    static void vortex(StandClient.Cast c,float t,Matrix4f m,Matrix3f nm,net.minecraft.client.render.VertexConsumerProvider buffers,Vec3d cam){
        if(t<WHIRL0||t>=WHIRL_OUT1)return;
        float open=Ease.smooth(t,WHIRL0,WHIRL1),thin=1-Ease.smooth(t,WHIRL_OUT0,WHIRL_OUT1);
        float gone=off(c);if(!Float.isNaN(gone))thin*=1-Ease.smooth(t,gone,gone+GONE);
        float a=open*thin;if(a<=.004f)return;
        var vc=buffers.getBuffer(RenderLayer.getEntityTranslucent(VORTEX));
        Vec3d o=eye(c);
        for(int layer=0;layer<2;layer++){
            double rad=WHIRL_R*(.55+.45*open)*(layer==0?1:.8),lift=layer==0?0:-.9,spin=(t-WHIRL0)*WHIRL_SPIN*(layer==0?1:1.7)+layer*1.3;
            float la=a*(layer==0?.95f:.7f);int rings=6,seg=40;
            for(int i=0;i<rings;i++){
                double r0=rad*i/rings,r1=rad*(i+1)/rings;
                for(int j=0;j<seg;j++){
                    double a0=2*Math.PI*j/seg,a1=2*Math.PI*(j+1)/seg;
                    Vec3d p00=whirl(o,rad,r0,a0,lift),p01=whirl(o,rad,r0,a1,lift),p11=whirl(o,rad,r1,a1,lift),p10=whirl(o,rad,r1,a0,lift);
                    float[] t00=whirlUv(rad,r0,a0,spin,layer),t01=whirlUv(rad,r0,a1,spin,layer),t11=whirlUv(rad,r1,a1,spin,layer),t10=whirlUv(rad,r1,a0,spin,layer);
                    Vec3d n=UP.multiply(-1);
                    vertex(vc,m,nm,p00,cam,t00[0],t00[1],n,1,1,1,la);vertex(vc,m,nm,p10,cam,t10[0],t10[1],n,1,1,1,la);
                    vertex(vc,m,nm,p11,cam,t11[0],t11[1],n,1,1,1,la);vertex(vc,m,nm,p01,cam,t01[0],t01[1],n,1,1,1,la);
                }
            }
        }
    }
    /** A point of the whirl: {@code r} out from its eye, its eye lifting up into it (a shallow funnel). */
    static Vec3d whirl(Vec3d o,double rad,double r,double a,double lift){double u=r/rad;return o.add(Math.cos(a)*r,lift+WHIRL_DEPTH*(1-u)*(1-u),Math.sin(a)*r);}
    static float[] whirlUv(double rad,double r,double a,double spin,int layer){
        double u=r/rad*.5,b=(layer==0?a+spin:-(a+spin));
        return new float[]{(float)(.5+u*Math.cos(b)),(float)(.5+u*Math.sin(b))};
    }

    // ───────────────────────────── the effect layer ─────────────────────────────

    /** When she called it, what it is drawn from: the cast's own place (she is held there). */
    static Vec3d feet(Fx c){return c.origin;}
    /** The light at her fingertip, the stroke it draws, the cross before her face; the whirl's eye and lightning; the tablet's edges, its fall's wind, its strike. */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;Vec3d feet=feet(c);float yaw=c.cast.yaw();
        boolean gone=!Float.isNaN(off(c.cast));
        if(!gone)fingertip(c,m,t,feet,yaw);
        sky(c,m,t);
        var tab=tablet(c.cast,t);
        if(tab!=null){
            float glow=tab.heat()*tab.alpha()*(1-tab.dark());
            if(glow>.02f){
                for(int i=0;i<OUTLINE.length;i++){
                    double[] a=OUTLINE[i],b=OUTLINE[(i+1)%OUTLINE.length];
                    Vec3d p=tab.at(a[0],a[1],THICK*.5),q=tab.at(b[0],b[1],THICK*.5);
                    m.softLine(p,q,.55,GOLD,.18f*glow);m.line(p,q,.05,GOLD_WHITE,.4f*glow);
                }
            }
            // the wind it tears through as it falls: streaks along its way off its edges
            if(t>=IsosangRules.G_FALL&&t<IsosangRules.G_STRIKE+.08f){
                float w=Ease.smooth(t,IsosangRules.G_FALL,IsosangRules.G_FALL+.06f)*(1-Ease.smooth(t,IsosangRules.G_STRIKE,IsosangRules.G_STRIKE+.08f));
                Vec3d back=tab.axis();
                for(int i=0;i<14;i++){
                    double[] a=OUTLINE[(i*OUTLINE.length)/14];
                    Vec3d p=tab.at(a[0]*1.05,a[1],(i%2==0?1:-1)*THICK*.6);
                    double len=4+3*Ease.hash(i*7+3);
                    m.softLine(p,p.add(back.multiply(len)),.08,0xeaf4ff,.4f*w);
                }
            }
        }
        // the strike: a flash where its point bites
        float ks=FxLib.kick(t,IsosangRules.G_STRIKE,.18f);
        if(ks>.01f&&!gone){Vec3d s=strike(c.cast).add(0,1,0);m.softDisc(s,5.5,GOLD,.55f*ks);m.star(s,4.2,WHITE,.7f*ks);}
    }
    /**
     * The light at her fingertip (0.2 to 1.4 s): it gathers, a beam shoots off it as her hand sweeps up, it glows as she
     * points at the sky, draws a stroke of gold as her hand crosses, and flares into a cross before her face; a few blue
     * shards spin round her hand.
     */
    static void fingertip(Fx c,EffectMesh m,float t,Vec3d feet,float yaw){
        if(t<.15f||t>1.5f)return;
        Vec3d tip=tip(feet,yaw,t);
        float on=Ease.window(t,.17f,1.42f,.06f,.12f);
        float pulse=.85f+.15f*(float)Math.sin(t*31);
        m.softDisc(tip,.16,GOLD,.6f*on);m.star(tip,(.16+.05*pulse)*on,GOLD_WHITE,.85f*on);
        // the beam as her hand sweeps up, along the way the finger points
        float beam=Ease.window(t,.28f,.52f,.03f,.08f);
        if(beam>.01f){
            Vec3d h=hand(feet,yaw,t),d=tip.subtract(h);d=d.lengthSquared()<1e-6?UP:d.normalize();
            Vec3d end=tip.add(d.multiply(1.6));
            m.softLine(tip,end,.12,GOLD,.5f*beam);m.line(tip,end,.022,GOLD_WHITE,.8f*beam);
        }
        // the stroke: where the fingertip went from 0.83 s, left hanging in the air, fading
        if(t>.83f){
            float fade=1-Ease.smooth(t,1.02f,1.3f);
            if(fade>.01f){
                int n=10;Vec3d prev=null;float to=Math.min(t,.97f);
                for(int i=0;i<=n;i++){
                    float s=.83f+(to-.83f)*i/n;Vec3d p=tip(feet,yaw,s);
                    if(prev!=null){float w=i/(float)n;m.softLine(prev,p,.07+.05*w,GOLD,.55f*fade);m.line(prev,p,.015+.01*w,GOLD_WHITE,.85f*fade);}
                    prev=p;
                }
            }
        }
        // the cross before her face (1.0 to 1.35 s): a long streak across and a shorter one up and down, facing the eye
        float cross=FxLib.kick(t,1.02f,.12f);
        if(cross>.01f){
            Vec3d look=tip.subtract(c.camera);look=look.lengthSquared()<1e-6?c.f:look.normalize();
            Vec3d right=look.crossProduct(UP);right=right.lengthSquared()<1e-6?c.right:right.normalize();Vec3d up=right.crossProduct(look).normalize();
            m.softLine(tip.subtract(right.multiply(2.4)),tip.add(right.multiply(2.4)),.05,GOLD,.6f*cross);
            m.line(tip.subtract(right.multiply(1.8)),tip.add(right.multiply(1.8)),.012,WHITE,.9f*cross);
            m.line(tip.subtract(up.multiply(.7)),tip.add(up.multiply(.7)),.012,WHITE,.8f*cross);
            m.star(tip,.45*cross,WHITE,cross);m.softDisc(tip,.5,GOLD_WHITE,.4f*cross);
        }
        // blue shards spinning round her hand
        float sh=Ease.window(t,.38f,1.02f,.08f,.12f);
        if(sh>.01f){
            Vec3d h=hand(feet,yaw,t);
            for(int i=0;i<6;i++){
                double a=t*(2.2+i*.3)+i*1.05,r=.35+.12*Ease.hash(i*13+1),y=-.1+.3*Ease.hash(i*17+2);
                Vec3d p=h.add(Math.cos(a)*r,y+.06*Math.sin(t*5+i),Math.sin(a)*r);
                Vec3d u=new Vec3d(Math.cos(a+1.3),.4,Math.sin(a+1.3)).normalize().multiply(.06),v=new Vec3d(-Math.sin(a),.2,Math.cos(a)).normalize().multiply(.025);
                m.triangle(p.add(u),p.subtract(u).add(v),p.subtract(u).subtract(v),SHARD,.8f*sh);
            }
        }
    }
    /** The whirl's eye shining gold as the tablet shows in it, and lightning flickering in the whirl. */
    static void sky(Fx c,EffectMesh m,float t){
        if(t<WHIRL0||t>=WHIRL_OUT1)return;
        float gone=off(c.cast);float g=Float.isNaN(gone)?1:1-Ease.smooth(t,gone,gone+GONE);
        Vec3d o=eye(c.cast);
        float light=Ease.window(t,1.25f,3.0f,.25f,.35f)*g;
        if(light>.01f){m.softDisc(o.add(0,1,0),7,EYE,.5f*light);m.softDisc(o,3.2,GOLD_WHITE,.45f*light);}
        // lightning in the whirl, a few quick bolts
        for(int k=0;k<5;k++){
            float at=1.55f+k*.27f+.08f*(float)Ease.hash(k*31+7);
            float a=Ease.window(t,at,at+.09f,.01f,.05f)*g;if(a<=.01f)continue;
            double ang=Ease.hash(k*19+5)*Math.PI*2,r0=WHIRL_R*(.45+.4*Ease.hash(k*23+7));
            Vec3d p=o.add(Math.cos(ang)*r0,1.5,Math.sin(ang)*r0);
            for(int s=0;s<5;s++){
                double ang2=ang+(Ease.hash(k*41+s)-.5)*.5,r1=r0*(1-.16*(s+1));
                Vec3d q=o.add(Math.cos(ang2)*r1,1.5-.5*(s+1)+(Ease.hash(k*43+s)-.5)*.8,Math.sin(ang2)*r1);
                m.line(p,q,.07,0xe8f2ff,.85f*a);m.softLine(p,q,.4,0x9cc4ff,.3f*a);p=q;
            }
        }
    }
    static void alpha(Fx c,EffectMesh m){}
    static void overlay(Fx c,EffectMesh m){}
    /** Lights: the fingertip, the tablet's glow over the ground, the strike, the fire leaping up; afterglow cut short in the quick parts. */
    static void frame(Fx c,FxFrame f){
        float t=c.sec;Vec3d feet=feet(c);float yaw=c.cast.yaw();boolean gone=!Float.isNaN(off(c.cast));
        float on=Ease.window(t,.17f,1.42f,.06f,.12f);
        if(on>.01f&&!gone)f.light(tip(feet,yaw,t),0xffd08a,3,.6f*on);
        var tab=tablet(c.cast,t);
        if(tab!=null){float glow=tab.heat()*tab.alpha()*(1-tab.dark());if(glow>.02f)f.light(tab.at(0,IsosangRules.TABLET_HEIGHT*.5,0),0xffb060,16,.8f*glow);}
        float ks=FxLib.kick(t,IsosangRules.G_STRIKE,.5f);
        if(ks>.01f&&!gone)f.light(strike(c.cast).add(0,2,0),0xffa050,20,1.3f*ks);
        float ke=FxLib.kick(t,IsosangRules.G_ERUPT,.6f);
        if(ke>.01f&&!gone)f.light(feet.add(0,1.5,0),0xff9a40,16,1f*ke);
        if((t>.28f&&t<1.1f)||(t>IsosangRules.G_FALL&&t<IsosangRules.G_STRIKE+.3f))f.afterglow(.08f);
    }
    /** Shake as the tablet strikes and as the fire leaps up (in over two frames). */
    static float shake(StandClient.Cast cast,float sec){
        if(!Float.isNaN(off(cast))&&off(cast)<IsosangRules.G_STRIKE)return 0;
        return Math.max(2.6f*bump(sec,IsosangRules.G_STRIKE,.25f),1.2f*bump(sec,IsosangRules.G_ERUPT,.2f));
    }

    // ───────────────────────────── the news of it ─────────────────────────────

    /** G heard of (its swing begun on this client): its landing and the fire after it are drawn with a meteor's (MeteorFx). */
    static void begin(StandClient.Cast c){
        int seed=IsosangRules.formationSeed(c.id());
        dev.sangwoo.celestialfall.client.meteor.MeteorFx.blast(c.id(),strike(c),c.origin(),seed,c.age(0)-IsosangRules.G_IMPACT);
    }
    /** G called off before it struck: no landing. */
    static void calledOff(java.util.UUID id){dev.sangwoo.celestialfall.client.meteor.MeteorFx.unblast(id);}

    // ───────────────────────────── her cutscene ─────────────────────────────

    /** Her own G whose cutscene her camera is in now (null for none): until the game's camera has it back. */
    static StandClient.Cast cinematic(){
        var mc=MinecraftClient.getInstance();
        if(mc.player==null||!mc.player.isAlive()||!MapClient.enabled()||!CelestialFall.CONFIG.cinematicCamera)return null;
        var c=IsosangClient.ownUltimate();if(c==null)return null;
        float t=c.age(0)/20f;if(t<0||t>=IsosangRules.G_GAME)return null;
        return c;
    }
    /** A shot: where the eye is, what it looks at, its lens (degrees) and its roll. */
    record Shot(Vec3d eye,Vec3d look,double fov,float roll){}
    /** The shots' cuts (seconds): the hand, the whip round it, along the forearm, the stroke, the face, the sky, the fall, the strike. */
    static final float S_WHIP=.3f,S_ARM=.45f,S_STROKE=.83f,S_FACE=1f,S_SIDE=1.42f,S_SKY=IsosangRules.G_SKY,S_FALL=IsosangRules.G_FALL,S_STRIKE=IsosangRules.G_STRIKE;
    /** Her camera's shot at {@code t} of the cast {@code c}. */
    static Shot shot(StandClient.Cast c,float t){
        Vec3d feet=c.origin();float yaw=c.yaw();Vec3d f=forward(c),r=new Vec3d(-f.z,0,f.x);
        if(t<S_FACE){
            Vec3d h=hand(feet,yaw,t),tip=tip(feet,yaw,t);
            if(t<S_WHIP){float u=t/S_WHIP;return new Shot(h.add(f.multiply(.82)).add(r.multiply(-.14+.1*u)).add(0,-.08,0),h.add(r.multiply(-.06)),50,0);}
            Vec3d front=f.multiply(.82).add(r.multiply(-.04)).add(0,-.08,0),below=f.multiply(.42).subtract(r.multiply(.42)).add(0,-.5,0).normalize().multiply(.76);
            if(t<S_ARM){float u=Ease.smooth(t,S_WHIP,S_ARM);Vec3d off=slerp(front,below,u);return new Shot(h.add(off),h.lerp(tip,u),50+5*u,-20*u);}
            if(t<S_STROKE){float u=(t-S_ARM)/(S_STROKE-S_ARM);return new Shot(h.add(below.multiply(1-.18*u)),tip,55,-20+6*u);}
            float u=Ease.smooth(t,S_STROKE,S_FACE);Vec3d side=f.multiply(.72).subtract(r.multiply(.2)).add(0,-.24,0);
            return new Shot(h.add(slerp(below.multiply(.82),side,u)),tip,55,-14*(1-u));
        }
        if(t<S_SKY){
            Vec3d head=head(feet,yaw,t);
            if(t>=S_SIDE&&variant(c)==1)return new Shot(head.add(f.multiply(.58)).subtract(r.multiply(.52)).add(0,-.02,0),head,46,0);
            float u=(t-S_FACE)/(S_SKY-S_FACE);return new Shot(head.add(f.multiply(.86-.1*u)).add(0,-.02,0),head.add(0,-.03,0),48,0);
        }
        if(t<S_FALL){
            float u=(t-S_SKY)/(S_FALL-S_SKY);Vec3d head=head(feet,yaw,t);
            Vec3d eye=feet.add(f.multiply(2.9-.45*u)).add(r.multiply(.55)).add(0,.55+.15*u,0);
            return new Shot(eye,head.lerp(skyCentre(c),.42),80,-9);
        }
        Vec3d s=strike(c);
        if(t<S_STRIKE){
            var tab=tablet(c,t);Vec3d point=tab==null?s:tab.point();
            Vec3d eye=s.subtract(f.multiply(5.2)).add(r.multiply(3.4)).add(0,.9,0);
            return new Shot(eye,point.lerp(s.add(0,3,0),.35),78,4);
        }
        float u=Math.min(1,(t-S_STRIKE)/(IsosangRules.G_BACK-S_STRIKE));
        Vec3d eye=s.subtract(f.multiply(7.4-.9*u)).add(r.multiply(4.4)).add(0,1.3,0);
        return new Shot(eye,s.add(0,3.9,0),72,3);
    }
    static Vec3d slerp(Vec3d a,Vec3d b,double u){
        double la=a.length(),lb=b.length();Vec3d na=a.multiply(1/la),nb=b.multiply(1/lb);
        double dot=MathHelper.clamp(na.dotProduct(nb),-1,1),ang=Math.acos(dot);
        Vec3d d=ang<1e-4?na.lerp(nb,u):na.multiply(Math.sin((1-u)*ang)/Math.sin(ang)).add(nb.multiply(Math.sin(u*ang)/Math.sin(ang)));
        return d.normalize().multiply(la+(lb-la)*u);
    }
    /** How far her camera has come back to the game's (0 the cutscene's, 1 the game's). */
    static float handback(float t){return Ease.smooth(t,IsosangRules.G_BACK,IsosangRules.G_GAME);}
    /** The jolt of the strike on her shot of it (blocks: the eye shaken up and across, coming in over two frames). */
    static double jolt(float t){return .22*bump(t,S_STRIKE,.22f);}
    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){
        var c=cinematic();if(c==null)return null;
        float t=c.age(delta)/20f;var s=shot(c,t);var mc=MinecraftClient.getInstance();
        Vec3d eye=clear(mc,s.look(),s.eye());
        double j=jolt(t);if(j>1e-4)eye=eye.add(0,j*Math.sin(t*61),j*.6*Math.cos(t*53));
        Vec3d d=s.look().subtract(eye);if(d.lengthSquared()<1e-6)d=forward(c);d=d.normalize();
        float sy=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),sp=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(d.y,-1,1)));
        float k=handback(t);
        if(k<=0)return new CinematicDirector.Pose(eye,sy,sp,s.roll());
        return new CinematicDirector.Pose(eye.lerp(vanilla,k),sy+MathHelper.wrapDegrees(yaw-sy)*k,sp+(pitch-sp)*k,s.roll()*(1-k));
    }
    public static double fov(double vanilla,float delta){
        var c=cinematic();if(c==null)return vanilla;
        float t=c.age(delta)/20f;double fov=shot(c,t).fov()-8*bump(t,S_STRIKE,.2f);
        return fov+(vanilla-fov)*handback(t);
    }
    public static float roll(float delta){
        var c=cinematic();if(c==null)return 0;
        float t=c.age(delta)/20f;return (shot(c,t).roll()+(float)(3*bump(t,S_STRIKE,.18f)*Math.sin(t*47)))*(1-handback(t));
    }
    /** Keeps an eye out of the walls: pulled in toward what it looks at where a block stands between. */
    static Vec3d clear(MinecraftClient mc,Vec3d look,Vec3d eye){
        if(mc.world==null||mc.player==null)return eye;
        var hit=mc.world.raycast(new RaycastContext(look,eye,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
        if(hit.getType()==HitResult.Type.MISS||hit.isInsideBlock()||hit.getPos().squaredDistanceTo(look)<.04)return eye;
        Vec3d d=eye.subtract(look);double len=d.length();if(len<1e-4)return eye;
        return look.add(d.multiply(Math.max(.3,look.distanceTo(hit.getPos())-.3)/len));
    }
    /**
     * How dark the world is for her camera now (0 to 1): black round her hand through the first shots, as the videos'
     * hand is on black (the fog closing in to a couple of blocks, the sky gone), and a moment of it again as the game
     * comes back, before the fire leaps up. Only her own G's, only her camera's.
     */
    public static float dark(float delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null||!MapClient.enabled()||!CelestialFall.CONFIG.cinematicCamera)return 0;
        var c=IsosangClient.ownUltimate();if(c==null)return 0;
        float t=c.age(delta)/20f;
        if(!Float.isNaN(off(c)))return 0;
        return Math.max(Ease.window(t,0,S_FACE,.03f,.04f),.55f*Ease.window(t,IsosangRules.G_BACK+.04f,IsosangRules.G_ERUPT+.06f,.04f,.08f));
    }
}
