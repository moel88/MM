package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.xchara.XSlashRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The great X slash's slash made anew (2.136.0, the user's "참격을 여기서 갈아엎고 최대로 멋있게 만들어줘"): each blade
 * springs open from a line as it is cut and settles, all light for a moment and then cooled; flying, its horns sweep
 * back and a wake streams off it, grown over its first blocks with no jump; the line of each cut is drawn ahead of its
 * blade and hangs where it was cut for a moment after the X has gone; its trails follow its horns and its heart and are
 * gone soon after its way ends.
 */
class XBladeTest {
    static final Vec3d FROM=new Vec3d(3,64,-2),F=new Vec3d(0,0,1),RIGHT=new Vec3d(-1,0,0);
    static XSlash.Way way(double reach){return new XSlash.Way(FROM,F,RIGHT,reach,null);}
    static double ahead(Vec3d p){return p.subtract(FROM).dotProduct(F);}

    @Test void aBladeSpringsOpenFromALineAndSettles(){
        assertEquals(0,XBlade.open(0),1e-9);assertEquals(0,XBlade.open(-.1f),1e-9);
        float most=0,last=0;
        for(float s=1/960f;s<.5f;s+=1/960f){
            float o=XBlade.open(s);assertTrue(o>=0&&o<1.2,"at "+s+": "+o);assertTrue(Math.abs(o-last)<.06,"no jump at "+s);
            most=Math.max(most,o);last=o;
        }
        assertTrue(most>1.04,"a little past whole: "+most);
        assertEquals(1,XBlade.open(.3f),.002,"settled");
        // open before its cut is whole: the blade is its full depth as the knife finishes
        for(int a=0;a<2;a++)assertTrue(XBlade.open(XBlade.to(a)-XBlade.from(a))>.9);
    }

    @Test void itIsAllLightAsItIsCutThenCools(){
        for(int a=0;a<2;a++){
            assertEquals(1,XBlade.fresh(a,XBlade.from(a)),1e-6);
            assertEquals(0,XBlade.fresh(a,XBlade.to(a)+XBlade.FRESH_AFTER),1e-6);
            float last=1;
            for(float t=XBlade.from(a);t<1;t+=1/240f){float f=XBlade.fresh(a,t);assertTrue(f<=last+1e-6&&last-f<.12,"cooling, no jump at "+t);last=f;}
        }
        assertTrue(XBlade.fresh(0,XSlash.FIRE)==0,"the first blade cooled before the X goes");
    }

    @Test void flyingItsHornsSweepBackAndItsWakeStreams(){
        var w=way(XSlashRules.RANGE);
        assertEquals(0,XBlade.grown(XSlashRules.NEAR),1e-9);assertEquals(1,XBlade.grown(XSlashRules.NEAR+XBlade.SWEEP_OVER),1e-6);
        double last=0;
        for(double fw=XSlashRules.NEAR;fw<=XSlashRules.RANGE;fw+=.01){double g=XBlade.grown(fw);assertTrue(g>=last-1e-9&&g-last<.01,"growing, no jump at "+fw);last=g;}
        double fw=10,half=XSlashRules.HALF,tall=XSlashRules.TALL;
        for(int a=0;a<2;a++){
            var flat=XBlade.arm(w,fw,half,tall,a,1,1,0);var swept=XBlade.arm(w,fw,half,tall,a,1,1,XBlade.SWEEP);int n=swept.n();
            assertEquals(fw,ahead(swept.outer()[n/2]),.02,"its middle leads");
            double back=fw-ahead(swept.outer()[0]);
            assertEquals(XBlade.SWEEP*2*Math.hypot(half,tall),back,1e-6,"its horns trail");assertTrue(back>1&&back<2.2,"by a block or two: "+back);
            assertEquals(back,fw-ahead(swept.outer()[n-1]),1e-6);
            for(int i=0;i<n;i++){
                // swept back only: what it is in its own plane is the flat blade's
                Vec3d d=swept.outer()[i].subtract(flat.outer()[i]);
                assertEquals(0,d.subtract(F.multiply(d.dotProduct(F))).length(),1e-9);assertTrue(d.dotProduct(F)<=1e-9);
            }
            // its wake: off its cutting edge, back the way it came, longest at its middle, none at its horns
            var wake=XBlade.wake(w,swept,fw,half);
            for(int i=0;i<n;i++){
                assertSame(swept.outer()[i],wake.outer()[i]);
                Vec3d d=wake.inner()[i].subtract(wake.outer()[i]);
                assertEquals(0,d.subtract(F.multiply(d.dotProduct(F))).length(),1e-9);assertTrue(d.dotProduct(F)<=1e-9,"back");
            }
            assertEquals(XBlade.WAKE,wake.inner()[n/2].distanceTo(wake.outer()[n/2]),.02);
            assertEquals(0,wake.inner()[0].distanceTo(wake.outer()[0]),1e-6);
        }
        assertEquals(0,XBlade.wake(XSlashRules.NEAR,XSlashRules.HALF0,.5),1e-9,"none as it goes");
        assertTrue(XBlade.WAKE<XSlashRules.NEAR+XBlade.SWEEP_OVER,"never back past where it went from once grown");
        // the halo is the lens, wider each way
        var blade=XBlade.arm(w,fw,half,tall,0,1,1,0);
        var halo=XBlade.arm(w,fw,half,tall,0,1,1,0,XBlade.INNER*XBlade.HALO_IN-XBlade.HALO_MORE,XBlade.OUTER*XBlade.HALO_OUT+XBlade.HALO_MORE);
        int n=blade.n();assertTrue(halo.outer()[n/2].y>blade.outer()[n/2].y&&halo.inner()[n/2].y<blade.inner()[n/2].y);
    }

    @Test void theLineOfEachCutLeadsItsBladeAndHangsWhereItWasCut(){
        var w=way(XSlashRules.RANGE);float nan=Float.NaN;
        for(int a=0;a<2;a++){
            assertEquals(0,XBlade.lineShown(a,XBlade.from(a)-XBlade.LINE_LEAD-.001f,nan),1e-9);
            for(float t=XBlade.from(a);t<XBlade.to(a);t+=1/240f)assertTrue(XBlade.lineDrawn(a,t)>=XSlash.cut(a,t)-1e-6,"ahead of its blade at "+t);
            assertEquals(1,XBlade.lineDrawn(a,XBlade.to(a)),1e-6);
            assertEquals(1,XBlade.lineShown(a,XSlash.FIRE,nan),1e-6,"there as the X goes");
            assertTrue(XBlade.lineShown(a,XSlash.FIRE+XBlade.LINE_LINGER*.5f,nan)>.2,"hanging after it");
            assertEquals(0,XBlade.lineShown(a,XSlash.FIRE+XBlade.LINE_LINGER,nan),1e-6);
            // stopped short, it goes out at once
            assertEquals(0,XBlade.lineShown(a,XBlade.to(a)+.11f,XBlade.to(a)),1e-6);
            Vec3d[] l=XBlade.line(w,a);
            assertEquals(XSlashRules.NEAR,ahead(l[0]),1e-9);assertEquals(XSlashRules.NEAR,ahead(l[1]),1e-9);
            assertTrue(l[0].y>FROM.y+2*XSlashRules.TALL0+.5,"out past its high horn");assertTrue(l[1].y>FROM.y&&l[1].y<FROM.y+.1,"down to its foot, over the ground");
            // through both of the blade's horns: one straight line
            var blade=XSlash.arm(w,XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0,a,1);
            Vec3d dir=l[1].subtract(l[0]).normalize(),horn=blade.outer()[0].subtract(l[0]);
            assertTrue(horn.subtract(dir.multiply(horn.dotProduct(dir))).length()<.05,"through its high horn");
        }
        assertEquals(0,XBlade.lineApart(XSlash.FIRE),1e-9);assertEquals(XBlade.LINE_APART,XBlade.lineApart(XSlash.FIRE+XBlade.LINE_LINGER),1e-6);
    }

    @Test void itsTrailsFollowItsHornsAndItsHeart(){
        var w=way(XSlashRules.RANGE);double reach=w.reach();
        assertEquals(0,XBlade.trailShown(XSlash.FIRE-.01f,reach),1e-9);assertEquals(1,XBlade.trailShown(XSlash.FIRE+.1f,reach),1e-6);
        assertEquals(0,XBlade.trailShown(XSlash.flown(reach)+XBlade.TRAIL,reach),1e-6);
        assertTrue(XSlash.flown(reach)+XBlade.TRAIL<XSlash.LIFE);
        for(int k=0;k<5;k++){
            Vec3d last=null;
            for(float t=XSlash.FIRE;t<XSlash.flown(reach)+.1f;t+=1/240f){
                Vec3d p=XBlade.anchor(w,k,XBlade.ahead(t,reach));
                assertTrue(p.y>=FROM.y,"over the ground");
                // (it flies forty blocks a second: a sixth of a block each 240th, and its horns grow and sweep as it goes)
                if(last!=null)assertTrue(p.distanceTo(last)<.4,"no jump of anchor "+k+" at "+t);
                last=p;
            }
        }
        // its horns where its blades' horns are drawn; its heart where they cross
        double fw=12;var blade=XBlade.arm(w,fw,XSlashRules.half(fw),XSlashRules.tall(fw),0,1,1,XBlade.SWEEP*XBlade.grown(fw));
        assertEquals(0,XBlade.anchor(w,0,fw).distanceTo(blade.outer()[0]),1e-6);
        assertEquals(0,XBlade.anchor(w,3,fw).distanceTo(blade.outer()[blade.n()-1]),.05);
        assertEquals(fw,ahead(XBlade.anchor(w,4,fw)),1e-9);
        assertEquals(XSlashRules.NEAR,XBlade.ahead(.2f,reach),1e-9);assertEquals(reach,XBlade.ahead(9,reach),1e-9);
    }
}
