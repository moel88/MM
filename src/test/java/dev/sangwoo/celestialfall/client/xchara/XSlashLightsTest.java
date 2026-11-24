package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.xchara.XSlashRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The great X slash made grand (2.135.0, the user's "이제 이걸 엄청 멋있게 만들어봐"): everything it adds is where and
 * when the X is (the server's), comes in and goes out without a jump, and is gone within what is drawn of it.
 */
class XSlashLightsTest {
    /** An X let go facing +z (to its right is -x), from (3, 64, -2), as far as it can go. */
    static final Vec3d FROM=new Vec3d(3,64,-2),F=new Vec3d(0,0,1),RIGHT=new Vec3d(-1,0,0);
    static XSlash.Way way(double reach){return new XSlash.Way(FROM,F,RIGHT,reach,null);}
    static final int SEED=1234;
    static double ahead(Vec3d p){return p.subtract(FROM).dotProduct(F);}
    static double side(Vec3d p){return p.subtract(FROM).dotProduct(RIGHT);}

    @Test void everythingIsGoneWithinWhatIsDrawnOfIt(){
        assertTrue(XSlash.LIFE*20>=XSlashRules.burstAt(XSlashRules.RANGE)+XSlashLights.AFTER*20,"drawn until the burst's embers and thrown pieces are gone");
        for(int j=0;j<XSlashLights.EMBERS;j++)assertTrue(XSlashLights.emberBorn(j,SEED)+XSlashLights.emberLife(j,SEED)<=XSlashLights.AFTER+1e-6,"ember "+j);
        assertTrue(XSlashLights.PILLAR_STAGGER+XSlashLights.PILLAR_LIFE<=XSlashLights.AFTER,"the pillars");
        for(int j=0;j<XSlashLights.SHARDS;j++)assertTrue(XSlashLights.shardLife(j,SEED)<=1.05f,"shard "+j);
        assertTrue(new KnifeCombo().lifetime()>=XSlash.LIFE);
    }

    @Test void theKnifeDrinksInItsLightBeforeTheFirstCutSwings(){
        for(float u=0;u<=1;u+=.01f)assertEquals(u,Ease.smooth(XSlashLights.unsmooth(u),0,1),1e-4,"unsmooth undoes smoothstep at "+u);
        assertTrue(XSlashLights.GATHERED<=XSlash.ARM1_FROM+.02f,"all of it in as the first cut swings: "+XSlashLights.GATHERED);
        for(int j=0;j<XSlashLights.GATHER;j++){
            float born=XSlashLights.gatherBorn(j,SEED);
            assertTrue(born>=XSlash.LIT&&born<=XSlash.LIT+XSlashLights.GATHER_LAST,"sets out as the knife lights");
            assertTrue(Float.isNaN(XSlashLights.gatherAlong(j,SEED,born-.001f))&&Float.isNaN(XSlashLights.gatherAlong(j,SEED,born+XSlashLights.GATHER_TRAVEL+.001f)));
            float last=-1;
            for(float t=born;t<born+XSlashLights.GATHER_TRAVEL-.001f;t+=.002f){float e=XSlashLights.gatherAlong(j,SEED,t);assertTrue(e>=last&&e<1,"on its way, never back");last=e;}
            double[] o=XSlashLights.gatherFrom(j,SEED);double off=Math.hypot(o[0]+.1,Math.hypot(o[1]-1.8,o[2]-.6));
            assertTrue(off>.8&&off<2.6,"from round where the knife is wound up: "+off);
        }
    }

    @Test void theRingAtTheirFeetOpensFlaresAndIsThrownOutAsTheXGoes(){
        float nan=Float.NaN;
        assertEquals(0,XSlashLights.ringShown(XSlash.LIT-.01f,nan),1e-6);
        assertEquals(1,XSlashLights.ringShown(.3f,nan),1e-6);
        assertEquals(0,XSlashLights.ringShown(XSlash.FIRE+XSlashLights.RING_GONE+.01f,nan),1e-6);
        assertEquals(XSlashLights.RING_R+XSlashLights.RING_OUT,XSlashLights.ringRadius(XSlash.FIRE+XSlashLights.RING_GONE,nan),1e-6,"a little further out as it flares away");
        assertTrue(XSlashLights.RING_OUT<1.2,"not thrown out across the screen (a big ring by the camera filled it)");
        assertEquals(0,XSlashLights.ringShown(.35f,.2f),1e-6,"stopped short: gone soon after");
        assertTrue(XSlashLights.ringRadius(.45f,.2f)<XSlashLights.RING_R+.01,"and not thrown out");
        double h=1/960.0;
        for(float t=0;t<1;t+=(float)h){
            assertTrue(Math.abs(XSlashLights.ringRadius(t+(float)h,nan)-XSlashLights.ringRadius(t,nan))<.03,"its size never jumps at "+t);
            assertTrue(Math.abs(XSlashLights.ringShown(t+(float)h,nan)-XSlashLights.ringShown(t,nan))<.03,"nor how it shows at "+t);
        }
        assertTrue(XSlashLights.ringFlare(XSlash.CUT1+.01f)>.5f&&XSlashLights.ringFlare(XSlash.FIRE+.01f)>XSlashLights.ringFlare(XSlash.CUT1+.01f),"flaring as each cut lands, most as the X goes");
    }

    @Test void thePointsOfTheXAreItsArmsOwn(){
        var w=way(XSlashRules.RANGE);
        for(int a=0;a<2;a++){
            var sw=XSlash.arm(w,XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0,a,1);
            for(int i=0;i<sw.n();i++){
                double t=sw.along()[i];
                assertEquals(0,XSlashLights.armPoint(w,XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0,a,t,1).distanceTo(sw.outer()[i]),1e-6);
                assertEquals(0,XSlashLights.armPoint(w,XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0,a,t,0).distanceTo(sw.inner()[i]),1e-6);
            }
            // its cutting edge bows out the way its normal says, and its cut goes from the high side down
            Vec3d mid=XSlashLights.armPoint(w,XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0,a,.5,1),chord=w.at(XSlashRules.NEAR,0,XSlashRules.TALL0);
            assertTrue(mid.subtract(chord).dotProduct(XSlashLights.armNormal(w,XSlashRules.HALF0,XSlashRules.TALL0,a))>.3);
            assertTrue(XSlashLights.armWay(w,XSlashRules.HALF0,XSlashRules.TALL0,a).y<-.3,"down");
        }
        Vec3d heart=XSlashLights.heart(w,XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0);
        assertEquals(0,side(heart),1e-9);assertTrue(heart.y-FROM.y>XSlashRules.TALL0&&heart.y-FROM.y<2*XSlashRules.TALL0,"where the arms' middles cross: a little over its middle");
    }

    @Test void eachCutThrowsItsSparksOffWhereItsHeadIs(){
        var w=way(XSlashRules.RANGE);
        for(int a=0;a<2;a++){
            float last=0;
            for(int j=0;j<XSlashLights.CUT_SPARKS;j++){
                float born=XSlashLights.sparkBorn(a,j);
                assertTrue(born>last,"in order along the cut");last=born;
                // as the cut's head reaches its place
                assertEquals((j+.5)/XSlashLights.CUT_SPARKS,XSlash.cut(a,born),1e-3);
                Vec3d p0=XSlashLights.spark(w,a,j,SEED,0);
                assertEquals(0,p0.distanceTo(XSlashLights.armPoint(w,XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0,a,(j+.5)/XSlashLights.CUT_SPARKS,1)),1e-9,"off the cutting edge");
                Vec3d p1=XSlashLights.spark(w,a,j,SEED,.05f);
                assertTrue(p1.subtract(p0).dotProduct(XSlashLights.armNormal(w,XSlashRules.HALF0,XSlashRules.TALL0,a))>-.05,"out the way the edge bows (or along it)");
                float life=XSlashLights.sparkLife(a,j,SEED);
                assertNull(XSlashLights.spark(w,a,j,SEED,life+.001f));assertNull(XSlashLights.spark(w,a,j,SEED,-.001f));
                Vec3d prev=p0;
                for(float s=1/960f;s<life;s+=1/960f){Vec3d p=XSlashLights.spark(w,a,j,SEED,s);assertTrue(p.distanceTo(prev)<.03,"no jump");prev=p;}
            }
        }
    }

    @Test void theLockClosesOnTheXAsItIsWholeAndSnapsAsItGoes(){
        float nan=Float.NaN;
        assertEquals(0,XSlashLights.lockShown(XSlashLights.LOCK0-.01f,nan),1e-6);
        assertEquals(1,XSlashLights.lockShown(XSlash.FIRE-.01f,nan),1e-6);
        assertEquals(0,XSlashLights.lockShown(XSlash.FIRE+XSlashLights.LOCK_GONE+.001f,nan),1e-6);
        assertEquals(0,XSlashLights.lockShown(.47f,.44f),1e-6,"stopped short: gone");
        double last=99;
        for(float t=XSlashLights.LOCK0;t<=XSlash.FIRE;t+=.002f){double hh=XSlashLights.lockHalf(t);assertTrue(hh<=last+1e-9,"closing");last=hh;}
        assertEquals(.85,XSlashLights.lockHalf(XSlash.FIRE),1e-6);assertEquals(0,XSlashLights.lockTurn(XSlash.FIRE),1e-6,"square as it shuts");
        assertTrue(XSlashLights.lockTurn(XSlashLights.LOCK0)>.7,"turned as it begins");
    }

    @Test void flyingItsWakeAndHornSparksAreWhereTheXIs(){
        var w=way(XSlashRules.RANGE);
        double fl=XSlashRules.flight(XSlashRules.RANGE)/20;
        for(int k=0;k<2;k++)for(int j=0;j<XSlashLights.HORN_SPARKS;j++){
            float born=XSlashLights.hornBorn(w,k,j,SEED);
            assertTrue(born>=XSlash.FIRE&&born<=XSlash.FIRE+fl+1e-6,"while it flies");
            Vec3d p0=XSlashLights.hornSpark(w,k,j,SEED,born);
            double fw=XSlashRules.head((born-XSlash.FIRE)*20);
            assertEquals(fw,ahead(p0),1e-6,"off where the horn was");
            assertEquals((k==0?-1:1)*XSlashRules.half(fw)*.97,side(p0),1e-6);
            assertEquals(.08,p0.y-FROM.y,1e-9,"on the floor");
            Vec3d p1=XSlashLights.hornSpark(w,k,j,SEED,born+.04f);
            assertTrue(ahead(p1)<ahead(p0)&&p1.y>p0.y,"thrown back and up");
            assertNull(XSlashLights.hornSpark(w,k,j,SEED,born+XSlashLights.hornLife(k,j,SEED)+.001f));
        }
    }

    @Test void whereItsWayEndsItBreaksIntoShardsThatTumbleOnAndFall(){
        for(double reach:new double[]{XSlashRules.RANGE,7.5}){
            var w=way(reach);
            for(int j=0;j<XSlashLights.SHARDS;j++){
                var s0=XSlashLights.shard(w,j,SEED,0);
                assertEquals(reach,ahead(s0.at()),1e-6,"off the X where it stopped");
                var s1=XSlashLights.shard(w,j,SEED,.15f);
                assertTrue(ahead(s1.at())>reach+.1,"flung on");
                float life=XSlashLights.shardLife(j,SEED);
                assertNull(XSlashLights.shard(w,j,SEED,life+.001f));
                assertTrue(XSlashLights.shard(w,j,SEED,life-.002f).size()<.02,"shrunk to nothing as it goes");
                Vec3d prev=s0.at();
                for(float s=1/960f;s<life;s+=1/960f){var sh=XSlashLights.shard(w,j,SEED,s);assertTrue(sh.at().distanceTo(prev)<.03,"no jump");prev=sh.at();}
            }
        }
    }

    @Test void theXOnTheGroundCracksAsItIsBurnedInAndBurstsFromItsMiddleOut(){
        var w=way(XSlashRules.RANGE);var raw=XSlashRules.diagonals(XSlashRules.RANGE);
        for(int a=0;a<2;a++)for(int k=0;k<XSlashLights.CRACKS;k++){
            double[] cr=XSlashLights.crack(a,k,SEED);
            double fw=raw[a][0]+(raw[a][2]-raw[a][0])*cr[0];float over=XSlash.FIRE+(float)XSlashRules.reached(fw)/20f;
            assertEquals(0,XSlashLights.crackGrown(w,a,k,SEED,over-.001f),1e-6,"not before the X goes over its root");
            assertEquals(1,XSlashLights.crackGrown(w,a,k,SEED,over+.1f),1e-6,"grown at once after");
            assertTrue(cr[3]>=1&&cr[3]<=2.2&&cr[2]>Math.toRadians(30)&&cr[2]<Math.toRadians(80));
        }
        int n=XSlashLights.pillars(Math.hypot(XSlashRules.RANGE-XSlashRules.NEAR,2*XSlashRules.HALF));
        assertTrue(n>=8,"a pillar every couple of blocks: "+n);
        for(int j=0;j<n;j++){
            double u=(j+.5)/n;
            for(int i=0;i<n;i++)if(Math.abs((i+.5)/n-.5)>Math.abs(u-.5)+1e-9)assertTrue(XSlashLights.pillarDelay(i,n)>XSlashLights.pillarDelay(j,n),"from where its lines cross outward");
            assertTrue(XSlashLights.pillarTall(j,SEED,.2f)>=4.5,"tall");
            assertEquals(0,XSlashLights.pillarTall(j,SEED,0),1e-9,"from the ground up");
        }
    }

    @Test void onWhomItStrikesAnXIsStruckAcrossThem(){
        assertEquals(.42,XSlashLights.markSize(0),1e-9);
        double last=0;for(float s=0;s<XSlashLights.MARK_LIFE;s+=.004f){double z=XSlashLights.markSize(s);assertTrue(z>=last);last=z;}
        assertTrue(XSlashLights.markSize(.08f)>.9,"out quickly, then holding");
    }

    @Test void theBodyLeavesPurpleCopiesThroughTheCutsAndTheThrowOnly(){
        assertEquals(0,XSlash.afterimages(.1f),1e-6);assertEquals(0,XSlash.afterimages(.7f),1e-6);
        for(float t=.2f;t<=XSlash.FIRE+.03f;t+=.01f)assertEquals(1,XSlash.afterimages(t),1e-6,"at "+t);
        var combo=new KnifeCombo();
        assertEquals(0,combo.afterimages(.3f),1e-6,"the knife's other swings never copy the body");
    }
}
