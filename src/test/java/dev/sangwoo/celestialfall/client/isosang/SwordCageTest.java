package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlashTest.turn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's Y as drawn (2.97.0, user request: "이번에는 Y스킬 거대한 검 6개를 적 위에 소환하고 2개씩 빠르게 적에게
 * 떨궈서 원 모양으로 적을 가두고 잉크가 많이 튀는 스킬이야", the reading confirmed): her motion (from the stance back to it,
 * no joint jumping, never a foot in the ground, up in the air reaching out at the foe while the swords show, down on her
 * sword before they strike), the six swords (each showing in its turn over the foe, points down and above its head,
 * coming down two at a time to stand in a ring round it, gone with the cage), the ink (its columns and what it flings,
 * all gone while Y is drawn), and her camera (round to her side, low behind her, never through her, never jumping).
 */
class SwordCageTest {
    static final Anim CLIP=SwordCage.CLIP;
    @Test void itRunsFromTheStanceBackToItAsLongAsYIsDrawn(){
        var stance=IsosangSlash.stance();
        var first=CLIP.frame(0);var last=CLIP.frame(CLIP.length());
        for(int j=0;j<6;j++){assertTrue(turn(first,stance,j)<.5,"Y starts in the stance");assertTrue(turn(last,stance,j)<.5,"Y ends in the stance (the formation's goes on from it)");}
        assertEquals(IsosangRules.LENGTH[IsosangRules.CAGE]/20f,CLIP.length(),1e-6);
        assertSame(CLIP,IsosangSlash.hit(IsosangRules.CAGE),"its swing is drawn with its own clip");
        assertTrue(SwordCage.LIFE>IsosangRules.CAGE_LENGTH/20f+1,"drawn till its ink on the ground has dried");
    }
    @Test void noJointJumpsOrFlips(){
        double h=1/960.0;
        for(double t=h;t+2*h<=CLIP.length();t+=h){
            var p0=CLIP.frame((float)(t-h));var p1=CLIP.frame((float)t);var p2=CLIP.frame((float)(t+h));var p3=CLIP.frame((float)(t+2*h));
            for(int j=0;j<6;j++){
                double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"joint "+j+" jumps "+now+" deg at "+t);
            }
        }
        for(double t=0;t+1/60.0<=CLIP.length();t+=1/60.0)
            for(int j=0;j<6;j++)assertTrue(turn(CLIP.frame((float)t),CLIP.frame((float)(t+1/60.0)),j)<60,"joint "+j+" flips at "+t);
    }
    @Test void neverAFootInThePommelAndHerHandOutAtTheFoe(){
        for(float t=0;t<=CLIP.length();t+=1/120f){
            double[] f=IsosangSlash.feet(CLIP.frame(t));
            assertTrue(f[0]>-.02&&f[1]>-.02,"a foot in the pommel at "+t+": "+f[0]+" "+f[1]);
        }
        // her free hand out ahead of her while she reaches (in the air, and on the pommel as the swords strike)
        for(float t:new float[]{.3f,.4f,.7f,1.0f}){
            var p=dev.sangwoo.celestialfall.client.kageno.R6.poseOf(CLIP.frame(t));
            Vec3d hand=dev.sangwoo.celestialfall.client.kageno.KagenoClips.freeHand(Vec3d.ZERO,0,p),shoulder=dev.sangwoo.celestialfall.client.kageno.KagenoClips.freeShoulder(Vec3d.ZERO,0,p);
            assertTrue(hand.subtract(shoulder).z>.4,"her hand reaches out ahead at "+t+": "+hand.subtract(shoulder));
        }
    }
    static final Vec3d RING=new Vec3d(10,64,4),HER=new Vec3d(2,65.7,-3);
    @Test void sixSwordsShowOverTheFoeInTurnAndComeDownTwoAtATimeIntoARing(){
        float none=Float.NaN;
        for(int k=0;k<IsosangRules.CAGE_SWORDS;k++){
            float show=SwordCage.shows(k),land=SwordCage.lands(k);
            assertNull(SwordCage.blade(RING,HER,k,show-.02f,none),"not before it shows");
            assertTrue(show>=.09f&&show<=.3f,"each shows between the videos' 0.1 and 0.29 s");
            // hanging over the foe well above its head, some 4 to 5 blocks out, aimed at it (2.97.1, the user: "검 방향이
            // 너무 1자로 되어있어 영상은 검 방향이 적으로 되어있고"): its point down and in, slanting, its line coming
            // down under the foe's feet
            var hang=SwordCage.blade(RING,HER,k,show+.15f,none);assertNotNull(hang);assertEquals(0,hang.phase());
            assertTrue(hang.tip().y-RING.y>2.6,"its point above a player's head: "+(hang.tip().y-RING.y));
            double out=Math.hypot(hang.grip().x-RING.x,hang.grip().z-RING.z);
            assertTrue(out>3.5&&out<5.5,"hanging some 4 to 5 blocks out: "+out);
            Vec3d hd=hang.dir();double lean=Math.toDegrees(Math.acos(-hd.y));
            assertTrue(lean>15&&lean<30,"slanting, not upright: "+lean);
            assertTrue(new Vec3d(hd.x,0,hd.z).dotProduct(RING.subtract(hang.grip()).multiply(1,0,1))>0,"its point in toward the foe");
            Vec3d down=hang.grip().add(hd.multiply((RING.y-hang.grip().y)/hd.y));
            assertTrue(Math.hypot(down.x-RING.x,down.z-RING.z)<1.4,"its line comes down at the foe's feet: "+Math.hypot(down.x-RING.x,down.z-RING.z));
            // coming down just before it strikes, in the ground from then
            assertEquals(1,SwordCage.blade(RING,HER,k,land-.02f,none).phase());
            var stuck=SwordCage.blade(RING,HER,k,land+.05f,none);assertEquals(2,stuck.phase());
            // down along the line it hung on, point first, never turning
            Vec3d off=stuck.grip().subtract(hang.grip());
            assertTrue(off.subtract(hd.multiply(off.dotProduct(hd))).length()<.1,"it stands on the line it hung on");
            assertTrue(off.dotProduct(hd)>4,"it came a long way down that line: "+off.dotProduct(hd));
            assertEquals(1,stuck.dir().dotProduct(hd),1e-9,"its blade's way the same");
            // standing in the ring: where its blade meets the ground about the ring's radius out, its point under the ground,
            // its hilt up over it, leaning its point in
            Vec3d g=stuck.grip(),d=stuck.dir();double s=(RING.y-g.y)/d.y;Vec3d at=g.add(d.multiply(s));
            assertEquals(IsosangRules.CAGE_RADIUS,Math.hypot(at.x-RING.x,at.z-RING.z),.15,"in the ground on the ring");
            assertTrue(stuck.tip().y<RING.y,"its point in the ground");assertTrue(g.y-RING.y>1.5,"its hilt well over it");
            assertTrue(Math.hypot(stuck.tip().x-RING.x,stuck.tip().z-RING.z)<Math.hypot(g.x-RING.x,g.z-RING.z),"its point leans in");
            // gone as the cage is over (its light gone by Y's end)
            assertNull(SwordCage.blade(RING,HER,k,SwordCage.GONE1+.01f,none));
            assertTrue(SwordCage.GONE1<=IsosangRules.CAGE_LENGTH/20f+1e-6);
        }
        // all round the ring, a sixth of a turn apart; each two across it from one another come down together
        for(int j=0;j<3;j++){
            int[] p=IsosangRules.cagePair(j);
            var a=SwordCage.blade(RING,HER,p[0],SwordCage.lands(p[0])+.05f,none).grip().subtract(RING).multiply(1,0,1).normalize();
            var b=SwordCage.blade(RING,HER,p[1],SwordCage.lands(p[1])+.05f,none).grip().subtract(RING).multiply(1,0,1).normalize();
            assertEquals(-1,a.dotProduct(b),1e-6,"pair "+j+" across the ring");
            assertEquals(SwordCage.lands(p[0]),SwordCage.lands(p[1]),1e-6);
        }
        // called off before a sword is down, it goes; one in the ground fades
        float end=SwordCage.lands(1)-.05f;
        assertNull(SwordCage.blade(RING,HER,1,end+.12f,end));
        assertTrue(SwordCage.blade(RING,HER,0,end+.2f,end).alpha()<.5f);
    }
    @Test void theSwordsMoveWithoutJumping(){
        float none=Float.NaN;
        for(int k=0;k<IsosangRules.CAGE_SWORDS;k++){
            SwordCage.Blade prev=null;
            for(float t=SwordCage.shows(k);t<SwordCage.GONE1;t+=1/120f){
                var b=SwordCage.blade(RING,HER,k,t,none);if(b==null){prev=null;continue;}
                if(prev!=null){
                    double step=b.grip().distanceTo(prev.grip());
                    // hanging and standing it hardly moves; coming down (a streak out of the sky) it goes fast, quicker and
                    // quicker, never more than 1.2 blocks a 120th of a second
                    if(b.phase()!=1&&prev.phase()!=1)assertTrue(step<.05,"sword "+k+" jumps "+step+" at "+t);
                    else assertTrue(step<1.2,"sword "+k+" leaps "+step+" at "+t);
                    assertTrue(b.dir().dotProduct(prev.dir())>.95,"sword "+k+" turns at once at "+t);
                }
                prev=b;
            }
        }
    }
    @Test void itsInkBurstsAndIsGoneWhileYIsDrawn(){
        for(int n=0;n<SwordCage.COLUMNS;n++){
            var c=SwordCage.column(n);
            assertTrue(c.born()>=SwordCage.COLUMN0&&c.born()<=SwordCage.COLUMN0+SwordCage.COLUMN_SPREAD,"the columns burst up from 0.68 s");
            // (2.97.1, the user: "잉크가 너무 많이 튀고 있어 잉크를 좀 많이 줄여줘": low and narrow, 2.97.0's up to 7.5 tall)
            assertTrue(c.height()>2&&c.height()<4.5,"low strokes, up about as high as the swords' hilts stand: "+c.height());
            assertTrue(c.width()>.5&&c.width()<1,"brush strokes, not spikes, not walls: "+c.width());
            assertEquals(1,SwordCage.columnEaten(c,c.born()+.7f),1e-6,"eaten from below and gone");
            assertTrue(c.born()+.7f<SwordCage.LIFE);
            assertTrue(Math.hypot(c.fw(),c.rt())<3,"round the ring");
        }
        Vec3d toHer=HER.subtract(RING).multiply(1,0,1).normalize();
        int down=0;
        for(int kind=0;kind<3;kind++){
            int n=kind==0?SwordCage.CLUMPS:kind==1?SwordCage.FLAKES:SwordCage.DROPS;
            for(int i=0;i<n;i++){
                var b=SwordCage.bit(RING,toHer,kind,i);
                assertTrue(b.velocity().y>0,"flung up");
                assertTrue(b.from().y>RING.y,"out of a column over the ground");
                assertTrue(b.born()+SwordCage.BIT_LIFE<SwordCage.LIFE,"gone while Y is drawn");
                if(b.lands(RING.y)<SwordCage.BIT_LIFE)down++;
            }
        }
        // (the videos: the clumps fly off and drift and are gone in the air; some come down and splash the ground)
        assertTrue(down>=1,"some of it comes down onto the ground round it: "+down);
        // ink still bursting (the user's first ask: 잉크가 많이 튀는), but much less of it (2.97.1: a quarter of 2.97.0's)
        assertTrue(SwordCage.COLUMNS<=6,"six columns at most");
        int bits=SwordCage.CLUMPS+SwordCage.FLAKES+SwordCage.DROPS;
        assertTrue(bits>=20&&bits<=30,"a little ink flung, not a storm of it: "+bits);
        assertTrue(SwordCage.SPLASHES<=4);
    }
    @Test void herCameraGoesRoundToHerSideThenLowBehindHerNeverThroughHerOrJumping(){
        int n=(int)(IsosangRules.CAGE_BACK1*120);Vec3d[] cams=new Vec3d[n+1],looks=new Vec3d[n+1];
        for(int i=0;i<=n;i++){
            Vec3d[] f=SwordCage.framing(HER,RING,0,1,i/120f);cams[i]=f[0];looks[i]=f[1];
            double off=Math.hypot(f[0].x-HER.x,f[0].z-HER.z);
            assertTrue(off>3,"never through her at "+i/120f+": "+off);
        }
        // never a jump: no step much bigger than the ones either side of it, and swung round quick but no faster than 0.5
        // blocks a 120th of a second (the videos' camera goes round behind her in about a tenth of a second)
        for(int i=2;i+1<=n;i++){
            double before=cams[i-1].distanceTo(cams[i-2]),now=cams[i].distanceTo(cams[i-1]),after=cams[i+1].distanceTo(cams[i]);
            assertFalse(now>.02&&now>3*Math.max(before,after),"the camera jumps at "+i/120f);
            assertTrue(now<.5,"the camera too fast at "+i/120f+": "+now);
            double lb=looks[i-1].distanceTo(looks[i-2]),ln=looks[i].distanceTo(looks[i-1]),la=looks[i+1].distanceTo(looks[i]);
            assertFalse(ln>.02&&ln>3*Math.max(lb,la),"what it looks at jumps at "+i/120f);
        }
        Vec3d u=RING.subtract(HER).multiply(1,0,1).normalize(),right=new Vec3d(-u.z,0,u.x);
        // at her side as the swords show: well round to her right (side 1), level with her, both in view
        Vec3d[] side=SwordCage.framing(HER,RING,0,1,.3f);Vec3d sd=side[0].subtract(HER).multiply(1,0,1);
        assertTrue(sd.dotProduct(right)>3.5,"round at her side: "+sd.dotProduct(right));
        assertTrue(Math.abs(sd.dotProduct(u))<2.5,"not far behind her yet");
        // low behind her as she comes down and the swords strike, looking past her at the foe
        Vec3d[] back=SwordCage.framing(HER,RING,0,1,.8f);Vec3d bd=back[0].subtract(HER).multiply(1,0,1);
        assertTrue(bd.dotProduct(u)<-3,"behind her: "+bd.dotProduct(u));
        assertTrue(back[0].y-HER.y<1,"low: "+(back[0].y-HER.y));
        Vec3d look=back[1].subtract(back[0]).multiply(1,0,1).normalize();
        assertTrue(look.dotProduct(u)>.9,"looking past her at the foe");
        // the other side mirrored
        Vec3d[] left=SwordCage.framing(HER,RING,0,-1,.3f);
        assertTrue(left[0].subtract(HER).multiply(1,0,1).dotProduct(right)<-3.5,"round at her left");
        // she and the foe both in the picture (70° up and down, 16:9) from the side shot until she stands behind the
        // camera: her head well clear of the top (the health bar sits there: the first take lost it there as the camera
        // swung round, she still high in her leap), the foe's feet clear of the bottom
        double th=Math.tan(Math.toRadians(35));
        for(int i=(int)(IsosangRules.CAGE_SIDE_IN*120);i<=(int)(.7*120);i++){
            float t=i/120f;Vec3d[] f=SwordCage.framing(HER,RING,0,1,t);
            Vec3d fw=f[1].subtract(f[0]).normalize(),rt=fw.crossProduct(new Vec3d(0,1,0)).normalize(),up=rt.crossProduct(fw);
            Vec3d head=HER.add(0,IsosangRules.cageLeap(t*20)+1.8,0).subtract(f[0]),feet=RING.subtract(f[0]);
            double hy=head.dotProduct(up)/(head.dotProduct(fw)*th),hx=head.dotProduct(rt)/(head.dotProduct(fw)*th*16/9);
            double fy=feet.dotProduct(up)/(feet.dotProduct(fw)*th),fx=feet.dotProduct(rt)/(feet.dotProduct(fw)*th*16/9);
            assertTrue(hy<.62,"her head clear of the top at "+t+": "+hy);
            assertTrue(Math.abs(hx)<.9,"her in the picture at "+t+": "+hx);
            assertTrue(fy>-.86&&Math.abs(fx)<.9,"the foe in the picture at "+t+": "+fx+" "+fy);
        }
    }
}
