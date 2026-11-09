package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.isosang.GArenaBounds;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's G made pretty (2.106.0, GLights): a night of stars in the black round her hand, coming out one by one and
 * gone as the black lifts, far off so it stays put as the shots turn round her hand, enough of it in every frame; star dust
 * drawn in to her fingertips; the stroke's stars gone before her face is whole in the shot; a crescent moon beside her head
 * in the face shot, in its frame and clear of her head; stars flaring here and there along the inscriptions; star dust shed
 * off the falling sword's edges and dragged down in its wake; a golden circle round its foot drawn from her side round to
 * its back, beating with its heart and flaring at the blast; a star running up its edges on each beat; stardust drifting
 * down over the blast's whole ground after it. Every part is a continuous function of the cast's clock: nothing jumps
 * between two frames at 60 fps, and all of it is gone before the cast is.
 */
class GLightsTest {
    static final float FRAME=1/60f;
    static StandClient.Cast cast(){return GGesture.REFERENCE_CAST;}

    /** {@code p} in the frame of {@code s} (-1 to 1 across and up), its roll left out: its x, y and how far in front. */
    static double[] inFrame(FallingSword.Shot s,Vec3d p){
        Vec3d f=s.look().subtract(s.eye()).normalize(),r=f.crossProduct(FallingSword.UP).normalize(),u=r.crossProduct(f);
        Vec3d v=p.subtract(s.eye());double z=v.dotProduct(f),ty=Math.tan(Math.toRadians(s.fov()/2)),tx=ty*FallingSword.ASPECT;
        return new double[]{v.dotProduct(r)/z/tx,v.dotProduct(u)/z/ty,z};
    }

    @Test void theNightComesOutInTheBlackOneByOneAndGoesAsItLifts(){
        for(int i=0;i<GLights.NIGHT;i++){
            assertEquals(0,GLights.nightShown(i,.09f),1e-6,"not before the black has fallen");
            assertEquals(0,GLights.nightShown(i,1.12f),1e-6,"gone as the black lifts");
            assertEquals(1,GLights.nightShown(i,.8f),1e-6,"all out under her arm");
            for(float t=0;t<1.2f;t+=FRAME)assertTrue(Math.abs(GLights.nightShown(i,t+FRAME)-GLights.nightShown(i,t))<.36f,"a star pops at "+t);
        }
        int out=0;for(int i=0;i<GLights.NIGHT;i++)if(GLights.nightShown(i,.25f)>.5f)out++;
        assertTrue(out>GLights.NIGHT*.3&&out<GLights.NIGHT*.7,"they come out one by one: "+out);
        int[] octant=new int[8];
        for(int i=0;i<GLights.NIGHT;i++){
            Vec3d d=GLights.nightWay(i);assertEquals(1,d.length(),1e-9);
            octant[(d.x>0?1:0)+(d.y>0?2:0)+(d.z>0?4:0)]++;
        }
        for(int n:octant)assertTrue(Math.abs(n-GLights.NIGHT/8)<GLights.NIGHT/8*.25,"spread evenly round: "+java.util.Arrays.toString(octant));
        int[] kinds=new int[3];for(int i=0;i<GLights.NIGHT;i++)kinds[GLights.nightKind(i)]++;
        assertTrue(kinds[2]>20&&kinds[2]<90&&kinds[1]>kinds[2]*3&&kinds[0]>kinds[1]*2,"a few bright, more little stars, most faint: "+java.util.Arrays.toString(kinds));
    }

    @Test void theHandShotsSeeANightOfStarsThatStaysPut(){
        Vec3d o=GLights.nightCentre(cast());
        for(float t=.15f;t<1.0f;t+=.05f){
            var s=FallingSword.shot(cast(),t);
            assertTrue(s.eye().distanceTo(o)<2.5,"the camera stays by her hand, far from the stars, at "+t+": "+s.eye().distanceTo(o));
            int seen=0;
            for(int i=0;i<GLights.NIGHT;i++){
                double[] q=inFrame(s,o.add(GLights.nightWay(i).multiply(GLights.NIGHT_R)));
                if(q[2]>0&&Math.abs(q[0])<1&&Math.abs(q[1])<1)seen++;
            }
            assertTrue(seen>=20&&seen<=170,"a night of stars in the frame at "+t+": "+seen);
        }
    }

    @Test void starDustIsDrawnInToHerFingertips(){
        for(int i=0;i<GLights.GATHER;i++){
            float b=GLights.gatherBorn(i),e=GLights.gatherArrive(i);
            assertTrue(b>=.12f&&e<=.6f,"while the light gathers there: "+b+" to "+e);
            assertNull(GLights.gatherOffset(i,b-.01f));
            assertNull(GLights.gatherOffset(i,e+.01f));
            double start=GLights.gatherOffset(i,b).length();
            assertTrue(start>=.08&&start<=.18,"it sets out a little way off: "+start);
            assertEquals(0,GLights.gatherOffset(i,e).length(),1e-9,"and gets to her fingertips");
            double before=start;
            for(float t=b+FRAME;t<=e;t+=FRAME){
                Vec3d now=GLights.gatherOffset(i,t);
                assertTrue(now.length()<=before+1e-9,"it only comes in, at "+t);
                assertTrue(now.distanceTo(GLights.gatherOffset(i,t-FRAME))<.035,"smoothly, at "+t);
                before=now.length();
            }
        }
    }

    @Test void theStrokesStarsAreGoneBeforeHerFaceIsWhole(){
        for(int i=0;i<GLights.STROKE;i++){
            float b=GLights.strokeBorn(i);
            assertTrue(b>=GLights.STROKE0-1e-6&&b<=GLights.STROKE1+1e-6,"left along the stroke as her fingers draw it");
            assertTrue(b+GLights.strokeLife(i)<1.2f,"gone by 1.2 s");
            assertTrue(GLights.strokeDrift(i,.2f).y<0,"drifting down");
            assertTrue(GLights.strokeDrift(i,.2f).length()<.05,"a little");
        }
    }

    @Test void aCrescentMoonRisesBesideHerHeadInTheFaceShot(){
        assertEquals(0,GLights.moonShown(1.09f),1e-6,"not before the halo comes");
        assertEquals(0,GLights.moonShown(1.6f),1e-6,"gone as the shot pulls up to the sky");
        assertEquals(1,GLights.moonShown(1.4f),1e-6);
        for(float t=1.12f;t<1.58f;t+=.04f){
            var s=FallingSword.face(cast(),t);
            Vec3d head=FallingSword.head(cast().origin(),cast().yaw(),t);
            Vec3d view=head.subtract(s.eye()).normalize(),across=view.crossProduct(FallingSword.UP).normalize(),up=across.crossProduct(view).normalize();
            Vec3d o=GLights.moonAt(head.add(view.multiply(1.1)),across,up,t);
            double[] h=inFrame(s,head);
            double headHalf=.26/h[2]/Math.tan(Math.toRadians(s.fov()/2));
            for(int k=0;k<16;k++){
                double a=k*Math.PI/8;
                Vec3d rim=o.add(across.multiply(Math.cos(a)*GLights.MOON_R)).add(up.multiply(Math.sin(a)*GLights.MOON_R));
                double[] q=inFrame(s,rim);
                assertTrue(Math.abs(q[0])<.98&&Math.abs(q[1])<.98,"the moon is in the frame at "+t+": "+q[0]+", "+q[1]);
                double dx=(q[0]-h[0])*FallingSword.ASPECT,dy=q[1]-h[1];
                assertTrue(Math.abs(dx)>headHalf*1.3||Math.abs(dy)>headHalf*1.3,"and clear of her head at "+t);
            }
            double[] star=inFrame(s,GLights.moonStar(o,across,up)),m=inFrame(s,o);
            assertTrue(star[0]>m[0],"its little star in its arms, toward her");
            assertTrue(m[0]<h[0],"the moon on the frame's left of her head");
        }
    }

    @Test void starsFlareHereAndThereAlongTheInscriptions(){
        int bright=0,n=0;
        for(double x=-3.5;x<=3.5;x+=.1)for(double y=1;y<30;y+=.1){n++;if(GLights.runeStars(x,y,2.3)>.5)bright++;}
        assertTrue(bright>0&&bright<n*.08,"here and there, most of the face dark: "+bright+" of "+n);
        double most=0;
        for(float t=1.7f;t<4.8f;t+=FRAME){
            double c0=GGrand.rune(t)[2],c1=GGrand.rune(t+FRAME)[2];
            for(double x=-3;x<=3;x+=.7)for(double y=2;y<20;y+=1.3){
                double a=GLights.runeStars(x,y,c0),b=GLights.runeStars(x,y,c1);
                most=Math.max(most,a);
                assertTrue(Math.abs(b-a)<.3,"a star jumps at "+t);
            }
        }
        assertTrue(most>.5&&most<2.0,"a soft bright spot, no more: "+most);
    }

    @Test void theFallingSwordLeavesAComet(){
        for(int i=0;i<GLights.DUST;i++){
            float b=GLights.dustBorn(i),life=GLights.dustLife(i);
            assertTrue(b>=IsosangRules.G_FALL&&b<=IsosangRules.G_STRIKE+.03f,"shed as it falls");
            assertTrue(b+life<IsosangRules.G_STRIKE+.7f,"gone soon after it strikes");
            assertNull(GLights.dustAt(cast(),i,b-.01f,false));
            var bl=GScene.blade(cast(),b);
            Vec3d at=GLights.dustAt(cast(),i,b,false),rel=at.subtract(bl.point());
            double x=rel.dotProduct(bl.across().normalize()),y=rel.dotProduct(bl.axis().normalize());
            assertTrue(Math.abs(Math.abs(x)-FallingSword.half(y))<.4,"off its edge: "+x+" against "+FallingSword.half(y));
            assertTrue(y>.5&&y<16,"along its lower part: "+y);
            float later=b+Math.min(.1f,life*.9f);
            double drop=Math.max(0,GScene.height(b))-Math.max(0,GScene.height(later));
            double fell=-GLights.dustAt(cast(),i,later,false).subtract(at).dotProduct(bl.axis().normalize());
            assertEquals(GLights.WAKE*drop,fell,.3,"dragged down some way in its wake");
            assertTrue(fell<drop+.3,"falling behind it");
            Vec3d prev=at;
            for(float t=b+FRAME;t<b+life;t+=FRAME){
                Vec3d p=GLights.dustAt(cast(),i,t,false);
                assertTrue(p.distanceTo(prev)<2.6,"no further between two frames than the wake takes it, at "+t);
                prev=p;
            }
        }
    }

    @Test void aGoldenCircleIsDrawnRoundTheSwordsFootFromHerSide(){
        assertEquals(0,GLights.sigilBright(IsosangRules.G_STRIKE),1e-6,"not before the sword strikes");
        assertEquals(0,GLights.sigilBright(5.76f),1e-6,"gone after the blast, with the cracks");
        assertEquals(0,GGrand.ground(5.81f),1e-6,"(the cracks gone by then too)");
        assertTrue(GLights.sigilDrawn(GLights.SIGIL_DRAWN)>.999f,"drawn all round within half a second");
        Vec3d s=FallingSword.strike(cast()),toHer=cast().origin().subtract(s);
        double front=GLights.sigilFront(cast());
        assertEquals(Math.atan2(toHer.z,toHer.x),front,1e-9,"from the side facing her");
        float tFront=Float.NaN,tBack=Float.NaN;
        for(float t=GLights.SIGIL0;t<GLights.SIGIL_DRAWN+.1f;t+=.005f){
            if(Float.isNaN(tFront)&&GLights.reached(cast(),front,t)>.5f)tFront=t;
            if(Float.isNaN(tBack)&&GLights.reached(cast(),front+Math.PI,t)>.5f)tBack=t;
        }
        assertTrue(tFront<tBack-.15f,"round to its back: "+tFront+" then "+tBack);
        assertEquals(GLights.reached(cast(),front+1,4.0f),GLights.reached(cast(),front-1,4.0f),1e-6,"both ways alike");
        for(int i=0;i+1<GGrand.BEATS.length;i++){
            float on=GLights.sigilBright(GGrand.BEATS[i]+.04f),off=GLights.sigilBright((GGrand.BEATS[i]+GGrand.BEATS[i+1])*.5f+.05f);
            assertTrue(on>off+.2f,"it beats with the sword's heart at "+GGrand.BEATS[i]);
        }
        assertTrue(GLights.sigilBright(4.88f)>GLights.sigilBright(4.8f)+.3f,"flaring at the blast");
        assertTrue(GLights.sigilBright(4.6f)>GLights.sigilBright(3.4f),"brighter toward the blast");
        Vec3d p=GLights.onGround(cast(),GLights.SIGIL_STARS,front);
        assertEquals(s.y+GLights.SIGIL_LIFT,p.y,1e-9,"lying on the ground");
        assertEquals(GLights.SIGIL_STARS,Math.hypot(p.x-s.x,p.z-s.z),1e-9);
        assertTrue(GLights.SIGIL_CORE<GLights.SIGIL_MOONS&&GLights.SIGIL_MOONS<GLights.SIGIL_INNER&&GLights.SIGIL_INNER<GLights.SIGIL_STARS&&GLights.SIGIL_STARS<GLights.SIGIL_R,
            "the moons between the inner rings, the stars between the outer");
        assertTrue(GLights.starAngle(cast(),0,4.5f)>GLights.starAngle(cast(),0,3.5f)&&GLights.moonAngle(cast(),0,4.5f)<GLights.moonAngle(cast(),0,3.5f),"turning, stars and moons opposite ways");
        for(float t=3.0f;t<5.6f;t+=FRAME/4){
            // (the flare at the blast comes in over a frame and a half, as the codebase's kicks do)
            if(t<4.83f||t>4.9f)assertTrue(Math.abs(GLights.sigilBright(t+FRAME)-GLights.sigilBright(t))<.5f,"its light jumps at "+t);
            assertTrue(Math.abs(GLights.sigilDrawn(t+FRAME)-GLights.sigilDrawn(t))<.15f,"its drawing jumps at "+t);
        }
    }

    @Test void onEachBeatAStarRunsUpTheSwordsEdges(){
        for(int k=0;k<GGrand.BEATS.length;k++){
            float b=GGrand.BEATS[k];
            assertTrue(Double.isNaN(GLights.glintHeight(k,b-.05f)),"not before its beat");
            assertTrue(Double.isNaN(GLights.glintHeight(k,b+.45f)),"nor long after");
            double buried=Math.max(0,-GScene.height(b)),y0=GLights.glintHeight(k,b-.025f),y1=GLights.glintHeight(k,b+.3f);
            assertTrue(y0>=buried+.3&&y0<buried+3,"from the ground: "+y0+" over "+buried);
            assertTrue(y1>y0+10,"running up the sword: "+y1);
            assertTrue(GLights.glintShown(k,b+.01f)>.9f,"in on the beat");
            assertEquals(0,GLights.glintShown(k,b+.43f),1e-6,"gone as it climbs");
            for(float t=b-.029f;t+FRAME<=b+.419f;t+=FRAME){
                assertTrue(GLights.glintHeight(k,t+FRAME)-GLights.glintHeight(k,t)<1.5,"it does not jump, at "+t);
                assertTrue(GLights.glintHeight(k,t+FRAME)>=GLights.glintHeight(k,t)-1e-9,"it only climbs, at "+t);
            }
        }
        var bl=GScene.blade(cast(),3.6f);
        Vec3d rel=GLights.edge(bl,1,8,1,false).subtract(bl.point());
        assertEquals(FallingSword.half(8)+.04,rel.dotProduct(bl.across()),1e-6,"just off its edge");
        assertEquals(.45,rel.dotProduct(bl.normal()),1e-6,"on the camera's side of its face");
    }

    @Test void stardustDriftsDownOverTheBlastsGroundAfterIt(){
        GArenaBounds area=GArenaClient.area(cast());
        Vec3d s=FallingSword.strike(cast());
        int[] quarter=new int[4];
        for(int i=0;i<GLights.FALL;i++){
            float b=GLights.fallBorn(i),life=GLights.fallLife(i);
            assertTrue(b>=GLights.FALL0&&b+life<=FallingSword.LIFE,"after the blast, gone before the cast is");
            assertTrue(life>.8f,"long enough to be seen drifting down: "+life);
            assertNull(GLights.fallAt(cast(),i,b-.01f));
            Vec3d prev=null;
            for(float t=b;t<=b+life;t+=FRAME){
                Vec3d p=GLights.fallAt(cast(),i,t);
                assertTrue(p.x>area.minX()&&p.x<area.maxX()&&p.z>area.minZ()&&p.z<area.maxZ(),"over the blast's ground");
                assertTrue(p.y>=s.y+.15-1e-9,"never below the ground");
                if(prev!=null){
                    assertTrue(p.y<=prev.y+1e-9,"falling, at "+t);
                    assertTrue(p.distanceTo(prev)<.1,"slowly and smoothly, at "+t);
                }
                prev=p;
            }
            Vec3d p=GLights.fallAt(cast(),i,b);
            quarter[(p.x>s.x?1:0)+(p.z>s.z?2:0)]++;
        }
        for(int n:quarter)assertTrue(n>GLights.FALL/4*.6,"over the whole of it: "+java.util.Arrays.toString(quarter));
    }

    @Test void allOfItIsGoneBeforeTheCastIs(){
        assertTrue(GLights.NIGHT_OUT1<1.2f&&GLights.MOON_OUT1<=FallingSword.S_PULLED&&GLights.SIGIL_OUT1<FallingSword.LIFE);
        float t=FallingSword.LIFE;
        for(int i=0;i<GLights.FALL;i++)assertNull(GLights.fallAt(cast(),i,t));
        for(int i=0;i<GLights.DUST;i++)assertNull(GLights.dustAt(cast(),i,t,false));
        assertEquals(0,GLights.sigilBright(t),1e-6);
        assertEquals(0,GLights.moonShown(t),1e-6);
        for(int i=0;i<GLights.NIGHT;i++)assertEquals(0,GLights.nightShown(i,t),1e-6);
    }
}
