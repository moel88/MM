package dev.sangwoo.celestialfall.client.atomic;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/**
 * Fixed random layout of one G cast (pillars, orbs, blast curtains, crater slabs), seeded by the
 * cast id so every viewer sees the same scene. Sizes come from the review footage: 1 Roblox stud
 * is 0.36 blocks (a 5-stud character is 1.8 blocks tall).
 */
final class AtomicScene {
    static final double STUD=.36;
    final UUID id;
    final Vec3d origin;
    /** Cast facing, the caster's right. */
    final Vec3d f,right;
    final float seed;
    final GroundCache ground;
    final Pillar[] pillars;
    final Orb[] orbs;
    final Curtain[] curtains;
    final List<Slab> slabs=new ArrayList<>();
    /** When each slab erupts for viewers outside the pillar (as the opening wall reaches it), filled on first use. */
    float[] wallRise;
    private boolean slabsBuilt;
    /** Blast radius over the 28-block footage radius: every explosion size scales with it. */
    final double scale;
    final Chunk[] debris;
    final Puff[] wall,dust;
    final Ember[] embers;

    /** Thin light shaft (and its base flare spot) on the plaza edge, 25–40 studs out. */
    record Pillar(double x,double z,float spawn,float drop,float phase) {}
    /** Floating light ball drifting up and sideways. */
    record Orb(double angle,double radius,double height,double vx,double vy,double vz,float life,float phase,double size) {}
    /** Tall blurred blast column; dark or light. */
    record Curtain(double angle,double radius,double width,double height,double tilt,double spin,int color,float alpha,boolean extra) {}
    /** Crater slab: ring index, centre angle/radius, size, tilt and its life. */
    record Slab(int ring,double angle,double radius,double length,double width,double thick,double tilt,double twist,
                double lift,float rise,float vanish,double y,BlockState state) {}
    /** Ground chunk thrown by the blast: launch angle and radius, speeds (blast radii per second), half sizes, spin axis and rate. */
    record Chunk(double angle,double r0,double speed,double rise,double sx,double sy,double sz,Vec3d axis,double spin,float delay) {}
    /** Smoke puff of the base ring or the dust column. */
    record Puff(double angle,double phase,double size,float alpha) {}
    /** Glowing ember drifting out of the crater after the beam. */
    record Ember(double angle,double radius,double vx,double vy,double vz,float life,float phase,double size) {}

    AtomicScene(UUID id,Vec3d origin,float yaw) { this(id,origin,yaw,28); }
    AtomicScene(UUID id,Vec3d origin,float yaw,double radius) {
        this.id=id;this.origin=origin;f=Vec3d.fromPolar(0,yaw);right=new Vec3d(-f.z,0,f.x);scale=Math.max(8,radius)/28;
        Random r=new Random(id.getMostSignificantBits()^id.getLeastSignificantBits());
        seed=r.nextInt(997);
        ground=new GroundCache(origin,16);
        // ≈40 clusters of 2-4 shafts; 48 rise in the first 0.1 s, then ≈7 a second (≈18 visible per side).
        List<Pillar> list=new ArrayList<>();
        while(list.size()<120){
            double a=r.nextDouble()*Math.PI*2,rad=(25+15*r.nextDouble())*STUD;int n=2+r.nextInt(3);
            for(int k=0;k<n&&list.size()<120;k++){
                double t=a+(k-(n-1)*.5)*(.55+.15*r.nextDouble())/rad;
                double rr=rad+(r.nextDouble()-.5)*.4;
                list.add(new Pillar(Math.cos(t)*rr,Math.sin(t)*rr,0,r.nextFloat()<.5?5.40f+.15f*r.nextFloat():99,r.nextFloat()*6.28f));
            }
        }
        Collections.shuffle(list,r);
        pillars=new Pillar[list.size()];
        for(int k=0;k<pillars.length;k++){
            var p=list.get(k);
            float spawn=k<48?.317f+.10f*k/48f:.42f+3.9f*(k-48)/(pillars.length-48f);
            pillars[k]=new Pillar(p.x(),p.z(),spawn,p.drop(),p.phase());
        }
        orbs=new Orb[40];
        for(int k=0;k<orbs.length;k++)orbs[k]=new Orb(r.nextDouble()*Math.PI*2,1.5+11.5*Math.sqrt(r.nextDouble()),.2+3.3*r.nextDouble(),
            (r.nextDouble()-.5)*2.4,1+1.2*r.nextDouble(),(r.nextDouble()-.5)*2.4,1.6f+1.6f*r.nextFloat(),r.nextFloat(),(.5+1.25*r.nextDouble())*STUD);
        // Blast interior: tall blurred columns converging upward, alternating dark and light.
        curtains=new Curtain[60];
        for(int k=0;k<curtains.length;k++){
            boolean extra=k>=36;
            double rad=extra?2+6*r.nextDouble():1.5+24.5*Math.sqrt(r.nextDouble());
            boolean light=k%2==0;
            curtains[k]=new Curtain(r.nextDouble()*Math.PI*2,rad,2.5+6.5*r.nextDouble(),50+60*r.nextDouble(),
                Math.toRadians(5+9*r.nextDouble()),(r.nextDouble()-.4)*.2,light?0xd8ccff:0x0c0530,light?.45f+.3f*r.nextFloat():.55f+.3f*r.nextFloat(),extra);
        }
        // Explosion: lifted chunks, the smoke ring at the tower's foot, the dust column after the tower and embers.
        debris=new Chunk[96];
        for(int k=0;k<debris.length;k++){
            double base=.025+.055*r.nextDouble();
            Vec3d axis=new Vec3d(r.nextDouble()-.5,r.nextDouble()-.5,r.nextDouble()-.5);
            debris[k]=new Chunk(r.nextDouble()*Math.PI*2,.2+.9*r.nextDouble(),.6+.7*r.nextDouble(),.6+.8*r.nextDouble(),
                base*(.7+.8*r.nextDouble()),base*(.5+.9*r.nextDouble()),base*(.7+.8*r.nextDouble()),
                axis.lengthSquared()<1e-4?new Vec3d(0,1,0):axis.normalize(),(r.nextDouble()-.5)*9,.45f*r.nextFloat());
        }
        wall=new Puff[40];for(int k=0;k<wall.length;k++)wall[k]=new Puff((k+.5*r.nextDouble())*Math.PI*2/wall.length,r.nextDouble()*6.28,.7+.6*r.nextDouble(),.7f+.3f*r.nextFloat());
        dust=new Puff[16];for(int k=0;k<dust.length;k++)dust[k]=new Puff(r.nextDouble()*Math.PI*2,r.nextDouble()*6.28,r.nextDouble(),.6f+.4f*r.nextFloat());
        embers=new Ember[160];
        for(int k=0;k<embers.length;k++)embers[k]=new Ember(r.nextDouble()*Math.PI*2,.1+1.3*Math.sqrt(r.nextDouble()),(r.nextDouble()-.5)*.12,.12+.22*r.nextDouble(),(r.nextDouble()-.5)*.12,
            1.8f+2.2f*r.nextFloat(),r.nextFloat(),.02+.03*r.nextDouble());
    }

    /** Crater rings at 14, 27, 36, 48, 63 and 80 studs, then large up-heaved plates out to ≈120. */
    static final double[] RINGS={14*STUD,27*STUD,36*STUD,48*STUD,63*STUD,80*STUD,96*STUD,112*STUD};
    void buildSlabs(ClientWorld world) {
        if(slabsBuilt)return;slabsBuilt=true;
        Random r=new Random(id.getLeastSignificantBits()*31+7);
        int[] counts={14,18,20,24,28,32,26,24};
        for(int ring=0;ring<RINGS.length;ring++){
            boolean plate=ring>=6;
            double rad=RINGS[ring],next=ring+1<RINGS.length?RINGS[ring+1]:rad+6;
            double band=Math.min(next-rad,6);
            for(int k=0;k<counts[ring];k++){
                double a=(k+.5+(r.nextDouble()-.5)*.25)*Math.PI*2/counts[ring];
                double length=2*Math.PI*rad/counts[ring]*(plate?.8+.25*r.nextDouble():.92+.06*r.nextDouble());
                double width=plate?3+1.5*r.nextDouble():band*(.55+.15*r.nextDouble());
                double thick=plate?1.3+.6*r.nextDouble():.8+.16*ring+.2*r.nextDouble();
                double tilt=Math.toRadians(plate?25+20*r.nextDouble():15+3.5*ring+(r.nextDouble()-.5)*10);
                double twist=Math.toRadians(plate?(r.nextDouble()-.5)*24:(r.nextDouble()-.5)*6);
                double lift=plate?.4+1.6*r.nextDouble():.15+.32*ring;
                float rise=7.07f+.06f*ring+.08f*r.nextFloat();
                // Outer pieces vanish first, one or two frames each, in random order.
                float order=(float)Math.max(0,Math.min(1,.78*(1-ring/(double)(RINGS.length-1))+.22*r.nextDouble()));
                float vanish=24.47f+3.05f*order;
                double cx=origin.x+Math.cos(a)*(rad+width*.5),cz=origin.z+Math.sin(a)*(rad+width*.5);
                slabs.add(new Slab(ring,a,rad,length,width,thick,tilt,twist,lift,rise,vanish,groundY(world,cx,cz),material(world,cx,cz)));
            }
        }
    }
    /** Ground block under a point (the crater uses the ground's own material). */
    BlockState material(ClientWorld world,double x,double z) {
        float h=GroundCache.surface(world,(int)Math.floor(x),(int)Math.floor(z),origin.y);
        if(Float.isNaN(h))return Blocks.STONE.getDefaultState();
        var pos=BlockPos.ofFloored(x,h-.01,z);var state=world.getBlockState(pos);
        return state.isOpaqueFullCube(world,pos)?state:Blocks.STONE.getDefaultState();
    }
    double groundY(ClientWorld world,double x,double z) {
        float h=ground.at((int)Math.floor(x),(int)Math.floor(z));
        if(Float.isNaN(h))h=GroundCache.surface(world,(int)Math.floor(x),(int)Math.floor(z),origin.y);
        return Float.isNaN(h)?origin.y:h;
    }
}
