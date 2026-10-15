package dev.sangwoo.celestialfall.client;

import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.KagenoMotion.smooth;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/**
 * Kageno skill geometry rebuilt shot-by-shot from the supplied review footage (30 fps).
 * Every shape is a pure function of the cast clock so late viewers see the same frame.
 * {@code light} selects the additive emission batch; the other batch is alpha blended.
 */
final class KagenoFx {
    static final Vec3d UP=new Vec3d(0,1,0);
    private KagenoFx(){}
    static float fade(float t,float hold,float end){return 1-smooth(t,hold,end);}
    static double hash(int i){double x=Math.sin(i*12.9898+78.233)*43758.5453;return x-Math.floor(x);}
    static Vec3d dir(Vec3d f,Vec3d right,double yawDeg,double pitchDeg){
        double y=Math.toRadians(yawDeg),p=Math.toRadians(pitchDeg);
        return right.multiply(Math.cos(y)*Math.cos(p)).add(f.multiply(Math.sin(y)*Math.cos(p))).add(0,Math.sin(p),0);
    }
    /** A glowing white blade-light: wide faint halo, violet body, white hot core. */
    static void beam(EffectMesh m,Vec3d a,Vec3d b,double width,float alpha,int tint,boolean light){
        if(alpha<=0)return;
        if(light){m.needle(a,b,width*4.5,.5,tint,alpha*.10f);m.needle(a,b,width*2.2,.5,tint,alpha*.28f);m.needle(a,b,width,.5,0xffffff,alpha);}
        else{m.needle(a,b,width*1.6,.5,tint,alpha*.35f);m.needle(a,b,width*.8,.5,0xffffff,alpha*.95f);}
    }
    /** Small four-pointed sparkle (the white ✦ glints in T, T-hold and H). */
    static void sparkle(EffectMesh m,Vec3d p,double r,int color,float alpha){
        if(alpha<=0)return;Vec3d cam=m.camera(),n=cam.subtract(p).normalize(),u=n.crossProduct(UP).normalize();if(u.lengthSquared()<.1)u=new Vec3d(1,0,0);Vec3d v=u.crossProduct(n).normalize();
        m.needle(p.subtract(u.multiply(r)),p.add(u.multiply(r)),r*.32,.5,color,alpha);
        m.needle(p.subtract(v.multiply(r)),p.add(v.multiply(r)),r*.32,.5,color,alpha);
        m.softDisc(p,r*.55,color,alpha*.5f);
    }

    // ───────────────────────────── R · 시간을 걷는 자 ─────────────────────────────
    // 31.8 s step, 32.4 grey, 32.8–33.4 long white needles accumulate in an X burst,
    // 33.6 colour returns, 33.8 horizontal release beam + shards, 34–35.2 drifting petals.
    // {yaw from caster-left toward forward, pitch, length, lateral offset, height offset}; mostly along the dash axis.
    private static final double[][] R_LINES={{96,-4,10.5,-.6,.1},{84,6,10,.8,-.15},{70,20,9,.2,.35},{108,-12,10,-1.2,-.3},{90,1,11,.3,.55},{62,-24,8.5,1.4,0},{116,16,9,-.4,-.5},{78,-8,10.5,-1.6,.25},{100,26,8,.9,.4},{88,-18,9.5,.5,-.6}};
    static final int R_LINE_START=18,R_LINE_STEP=2;
    static void r(EffectMesh m,Vec3d origin,Vec3d f,Vec3d right,float a,boolean light){
        Vec3d c=origin.add(0,1.05,0).add(f.multiply(.6));
        // Frozen needles. Grey world keeps them pure white; after release they tint violet and fade.
        float release=smooth(a,35,36),gone=fade(a,35,37.5f);
        for(int i=0;i<R_LINES.length;i++){
            float life=a-(R_LINE_START+i*R_LINE_STEP);if(life<0||gone<=0)continue;
            double[] l=R_LINES[i];Vec3d d=dir(f,right,l[0],l[1]);
            double grow=smooth(life,0,1.2f),len=l[2]*(.6+.4*grow);
            Vec3d mid=c.add(right.multiply(l[3])).add(0,l[4],0).add(d.multiply((hash(i+9)-.5)*3));
            Vec3d a0=mid.subtract(d.multiply(len*.5)),b0=mid.add(d.multiply(len*.5));
            float alpha=smooth(life,0,.6f)*gone;
            int tint=release>0?0xb46bff:0xffffff;
            beam(m,a0,b0,.12+.05*hash(i+3),alpha,tint,light);
            // thin parallel threads give the "bundle of cuts" look
            for(int k=0;k<2;k++){Vec3d o=d.crossProduct(UP).normalize().multiply((k==0?-1:1)*(.12+.05*hash(i*3+k))).add(0,(k-.5)*.07,0);
                beam(m,a0.add(o).add(d.multiply(len*.12)),b0.add(o).subtract(d.multiply(len*.08)),.035,alpha*.85f,tint,light);}
        }
        if(a>=36&&a<56){
            float t=a-36,beamA=smooth(t,0,1.2f)*fade(t,4,14);
            Vec3d l=c.subtract(f.multiply(6.5)).add(0,-.25,0),r=c.add(f.multiply(7.5)).add(0,-.25,0);
            beam(m,l,r,.6,beamA,0xa760ff,light);
            beam(m,l.add(0,.2,0),r.add(0,.05,0),.2,beamA*.8f,0xd9a3ff,light);
            beam(m,l.add(0,-.15,0),r.add(0,-.3,0),.12,beamA*.7f,0xffffff,light);
            if(light)m.softDisc(c,2.2*beamA+.4,0xb070ff,beamA*.55f);
            // Shards burst outward from the cut line.
            for(int i=0;i<34;i++){
                double life=t/18.0;if(life>=1)break;
                double ang=hash(i)*Math.PI*2,sp=2+4*hash(i+40);
                Vec3d d=right.multiply(Math.cos(ang)).add(UP.multiply(Math.sin(ang)*.8)).add(f.multiply((hash(i+80)-.5)*.6)).normalize();
                Vec3d p=c.add(f.multiply((hash(i+5)-.5)*9)).add(d.multiply(sp*Math.sqrt(life))).add(0,-life*life*.8,0);
                beam(m,p,p.add(d.multiply(.25+.3*(1-life))),.07,(float)(1-life)*smooth(t,0,1),0xe0b0ff,light);
            }
        }
        petals(m,origin,f,right,a-38,34,4.2,light);
    }
    /** Slow pink-violet petals drifting after the time stop is released. */
    static void petals(EffectMesh m,Vec3d origin,Vec3d f,Vec3d right,float t,float life,double radius,boolean light){
        if(t<0||t>=life)return;
        for(int i=0;i<46;i++){
            float start=(float)(hash(i+300)*6),tt=t-start;if(tt<0)continue;float k=tt/(life-start);if(k>=1)continue;
            double ang=hash(i)*Math.PI*2,r=radius*(.25+.75*hash(i+1));
            Vec3d p=origin.add(right.multiply(Math.cos(ang)*r)).add(f.multiply(Math.sin(ang)*r)).add(0,.2+2.4*hash(i+2)+.35*k,0)
                .add(Math.sin(tt*.21+i)*.25,Math.sin(tt*.13+i*2)*.12,Math.cos(tt*.17+i)*.25);
            double spin=tt*.18+i,size=.12+.08*hash(i+3);
            Vec3d u=right.multiply(Math.cos(spin)*size).add(UP.multiply(Math.sin(spin)*size*.6)),v=f.multiply(size*.45).add(UP.multiply(Math.cos(spin)*size*.4));
            float alpha=(float)(smooth(tt,0,2)*(1-k*k));
            int col=i%3==2?0xd9a8ff:0xffffff;
            if(light)m.softDisc(p,size*2.2,0xc77dff,alpha*.35f);
            else m.quad(p.subtract(u),p.add(v),p.add(u),p.subtract(v),col,alpha*.95f);
        }
    }

    // ───────────────────────────── Q · 오버드라이브 ─────────────────────────────
    // Purple electric crackles that flicker over the body + a glowing left eye.
    static void q(EffectMesh m,Vec3d feet,Vec3d f,Vec3d right,float now,float bodyHeight,boolean light){
        int frame=(int)(now*1.5f);
        for(int i=0;i<16;i++){
            int seed=frame*31+i*7;if(hash(seed)<.3)continue;
            double h=.15+hash(seed+1)*(bodyHeight-.25),ang=hash(seed+2)*Math.PI*2,r=.32+.12*hash(seed+3);
            Vec3d p=feet.add(right.multiply(Math.cos(ang)*r)).add(f.multiply(Math.sin(ang)*r*.7)).add(0,h,0);
            Vec3d d=dir(f,right,hash(seed+4)*360,(hash(seed+5)-.5)*140);
            Vec3d q=p;double seg=.07+.06*hash(seed+6);
            for(int s=0;s<4;s++){
                Vec3d n=q.add(d.multiply(seg)).add((hash(seed+10+s)-.5)*.12,(hash(seed+20+s)-.5)*.12,(hash(seed+30+s)-.5)*.12);
                if(light){m.line(q,n,.14,0x9d3bff,.3f);m.line(q,n,.04,0xf0d6ff,1f);}else m.line(q,n,.05,0x7a1fff,.9f);
                q=n;
            }
            if(light)m.softDisc(p,.22,0xa24dff,.45f);
        }
    }
    static void eye(EffectMesh m,Vec3d eye,Vec3d look,Vec3d right,float now,boolean light){
        if(!light)return;Vec3d p=eye.add(look.multiply(.26)).add(right.multiply(-.11)).add(0,.02,0);
        float flick=.85f+.15f*(float)Math.sin(now*.9);
        m.softDisc(p,.32,0x9b45ff,.55f*flick);m.softDisc(p,.12,0xffffff,.95f*flick);
        m.needle(p.subtract(right.multiply(.3)),p.add(right.multiply(.3)),.05,.5,0xe7c6ff,.8f*flick);
    }

    // ───────────────────────────── Y · 어둠 속으로 빠지다 ─────────────────────────────
    // Raise → crouch → slam (≈16 t) → white flash with vertical light columns →
    // a ring of large tilted ground plates, violet sparkles and white orbs (≈3 s).
    static final int Y_SLAM=16,Y_BLAST=24,Y_END=92;
    static void y(EffectMesh m,Vec3d origin,Vec3d f,Vec3d right,float a,boolean light){
        if(a<Y_BLAST)return;float t=a-Y_BLAST;
        // Vertical light columns during the flash.
        if(t<12){float al=smooth(t,0,1)*fade(t,3,12);
            for(int i=0;i<12;i++){double ang=hash(i+50)*Math.PI*2,r=1+5.5*hash(i+51);Vec3d base=origin.add(Math.cos(ang)*r,0,Math.sin(ang)*r);double h=4+6*hash(i+52);
                beam(m,base,base.add(0,h,0),.3+.4*hash(i+53),al*(.6f+.4f*(float)hash(i+54)),light?0xd9c8ff:0x6a1fff,light);}
            if(light)m.softDisc(origin.add(0,1,0),6*smooth(t,0,3),0xffffff,al*.7f);
        }
        float life=1-smooth(a,Y_END-14,Y_END);
        // Violet sparkles and drifting white orbs.
        for(int i=0;i<30;i++){
            double s=hash(i+100)*8,tt=t-s;if(tt<0)continue;double k=tt/(Y_END-Y_BLAST-s);if(k>=1)continue;
            double ang=hash(i+101)*Math.PI*2,r=.8+4.5*hash(i+102);
            Vec3d p=origin.add(Math.cos(ang)*r,.2+1.8*hash(i+103)+k*1.2,Math.sin(ang)*r);
            float al=(float)(smooth((float)tt,0,2)*(1-k))*life;
            if(i%4==0){if(light){m.softDisc(p,.42,0xffffff,al*.6f);m.softDisc(p,.16,0xffffff,al);}}
            else sparkle(m,p,.09+.06*hash(i+104),i%3==0?0xffffff:0xc77dff,al);
        }
    }
    /** Plate layout for the Y crater: angle, radius, size x/z, tilt (deg), thickness. */
    static final double[][] Y_PLATES={{0.1,3.4,3.6,3.0,48,.55},{0.75,4.0,4.2,3.4,38,.6},{1.4,3.1,3.2,2.8,55,.5},{2.0,4.8,4.8,3.8,34,.7},{2.6,3.6,3.4,3.0,50,.55},
        {3.2,4.4,4.0,3.4,42,.6},{3.9,3.3,3.3,2.8,56,.5},{4.5,4.6,4.5,3.6,36,.65},{5.1,3.5,3.5,3.0,47,.55},{5.7,4.2,4.1,3.3,40,.6},
        {0.42,6.4,5.2,4.0,30,.7},{1.75,6.7,5.4,4.2,26,.75},{3.55,6.2,5.0,3.9,32,.7},{4.85,6.8,5.6,4.4,24,.75}};
    static float plateRise(float a){return smooth(a,Y_BLAST+1,Y_BLAST+5)*(1-smooth(a,Y_END-12,Y_END));}

    // ───────────────────────────── M1 · 검술의 대가 ─────────────────────────────
    // 1: right-high → left-low diagonal brush, 2: flatter reverse brush, 3: expanding horizontal
    // ring and screen-wide wind bands, 4: overhead vertical crescents + dark smoke burst.
    record Plane(Vec3d u,Vec3d v,double start,double sweep,double radius,double width){}
    static Plane m1Plane(int combo,Vec3d f,Vec3d right){
        Vec3d side=right.negate();
        return switch(combo){
            case 1->new Plane(f,side.multiply(Math.cos(.62)).add(UP.multiply(Math.sin(.62))).normalize(),Math.toRadians(115),Math.toRadians(-215),2.3,1.0);
            case 2->new Plane(f,side.multiply(Math.cos(-.22)).add(UP.multiply(Math.sin(-.22))).normalize(),Math.toRadians(-120),Math.toRadians(225),2.4,.9);
            case 3->new Plane(f,side,Math.toRadians(-140),Math.toRadians(400),2.0,1.2);
            default->new Plane(f,UP,Math.toRadians(150),Math.toRadians(-200),1.9,.8);
        };
    }
    static double head(float life){return smooth(life,0,2.6f);}
    static double tail(float life){return smooth(life,1.6f,7.5f);}
    static void m1Strokes(Vec3d origin,Vec3d f,Vec3d right,float a,int combo){
        float life=a-2;if(life<0||life>=12)return;
        var pl=m1Plane(combo,f,right);Vec3d c=origin.add(0,1.0,0).add(f.multiply(.25));
        double h=head(life),t=tail(life);float alpha=fade(life,4,11);
        double radius=pl.radius+(combo==3?4.2*smooth(life,1,10):0);
        double s0=pl.start+pl.sweep*t,len=pl.sweep*(h-t);
        KagenoFire.stroke(c,pl.u,pl.v,radius,s0,len,pl.width*(combo==3?1+.6*smooth(life,1,8):1),combo*13,alpha);
        KagenoFire.stroke(c.add(0,.08,0),pl.u,pl.v,radius*.82,s0,len*.92,pl.width*.45,combo*13+5,alpha*.7f);
        if(combo==3&&life>=4&&life<11){
            // 9.0–9.2 s: broad wind bands sweep across the whole frame.
            float w=smooth(life,4,5)*fade(life,7,11);
            for(int i=0;i<5;i++){double r=5.5+i*1.1,y=-.6+i*.32;
                KagenoFire.stroke(c.add(0,y,0),f,right.negate(),r,Math.toRadians(-160+i*35)+life*.12,Math.toRadians(150),.9+.3*(i%2),70+i,w*(.85f-i*.08f));}
        }
        if(combo==4){
            Vec3d side=right.negate();
            for(int j=-1;j<=1;j+=2){Vec3d u=f.multiply(Math.cos(.42)).add(side.multiply(j*Math.sin(.42))).normalize();
                KagenoFire.stroke(c.add(side.multiply(j*.3)),u,UP,1.6,pl.start+pl.sweep*t,pl.sweep*(h-t)*.8,.45,90+j,alpha*.85f);}
        }
    }
    static void m1Smoke(java.util.List<Vec3d> pts,java.util.List<Double> sizes,Vec3d origin,Vec3d f,Vec3d right,float a,int combo){
        float life=a-2;if(life<0||life>=16)return;
        if(combo==4&&life>=3){double k=(life-3)/13;Vec3d hit=origin.add(f.multiply(1.8));
            for(int i=0;i<11;i++){double ang=hash(i+600)*Math.PI*2,r=.2+1.3*hash(i+601)*Math.sqrt(k);
                pts.add(hit.add(right.multiply(Math.cos(ang)*r)).add(f.multiply(Math.sin(ang)*r*.6)).add(0,.35+.9*hash(i+602)+k*.5,0));sizes.add(.45+.55*hash(i+603)+k*.5);}}
        if(combo==3&&life>=2){double k=(life-2)/14;
            for(int i=0;i<14;i++){double ang=i*Math.PI*2/14+hash(i+610),r=2+4.5*Math.sqrt(k);
                pts.add(origin.add(Math.cos(ang)*r,.5+.4*hash(i+611),Math.sin(ang)*r));sizes.add(.55+.4*hash(i+612));}}
    }
    static float m1SmokeAlpha(float a,int combo){float life=a-2;return combo==4?smooth(life,3,4)*fade(life,7,16)*.85f:combo==3?smooth(life,2,4)*fade(life,5,14)*.45f:0;}
    /** Thin white wind rings and the hit-4 crescents (mesh batches). */
    static void m1(EffectMesh m,Vec3d origin,Vec3d f,Vec3d right,float a,int combo,boolean light){
        float life=a-2;if(life<0||life>=12)return;
        var pl=m1Plane(combo,f,right);Vec3d c=origin.add(0,1.0,0).add(f.multiply(.25));float alpha=fade(life,3,10);
        int col=light?0xf3e6ff:0xffffff;double w=light?.05:.025;
        for(int j=0;j<3;j++){double r=pl.radius+.45+j*.32+(combo==3?4.5*smooth(life,1,10):life*.06);
            double rot=pl.start+pl.sweep*head(life)-Math.signum(pl.sweep)*(2.2+j*.6);
            m.ring(c.add(0,(j-1)*.12,0),pl.u,pl.v,r,72,combo==3?.95f:.42f,w,col,alpha*(.5f-j*.12f),Math.min(rot,rot+Math.signum(pl.sweep)*2.6));}
        if(combo==4){Vec3d side=right.negate(),hit=c.add(f.multiply(1.5));
            for(int j=0;j<4;j++){Vec3d u=f.multiply(Math.cos(j*.35-.5)).add(side.multiply(Math.sin(j*.35-.5))).normalize();
                m.ring(hit.add(0,-.2,0),u,UP,1.1+j*.28,64,.38f,light?.09:.04,col,alpha*fade(life,4,10),Math.toRadians(-40)+life*.05);}
            for(int i=0;i<16;i++){double k=Math.min(1,life/10.),ang=hash(i+620)*Math.PI*2;Vec3d d=dir(f,right,ang*57.3,(hash(i+621)-.3)*80);
                sparkle(m,hit.add(0,-.6,0).add(d.multiply(.3+2.2*Math.sqrt(k))).add(0,-k*k*.6,0),.08,i%2==0?0xffffff:0xc77dff,(float)(1-k)*smooth(life,3,4));}
        }
    }

    /** Shader brush strokes for every skill (one batch). */
    static void strokes(int skill,Vec3d pos,Vec3d origin,Vec3d f,Vec3d right,float a,int combo){
        switch(skill){case M1->m1Strokes(pos,f,right,a,combo);case E->eStrokes(pos,f,right,a);case RUSH->rushStrokes(pos,f,right,a);case H->hStrokes(origin,f,right,a);default->{}}
    }
    static float smoke(java.util.List<Vec3d> pts,java.util.List<Double> sizes,int skill,Vec3d pos,Vec3d origin,Vec3d f,Vec3d right,float a,int combo){
        switch(skill){case M1->{m1Smoke(pts,sizes,pos,f,right,a,combo);return m1SmokeAlpha(a,combo);}default->{return 0;}}
    }

    // ───────────────────────────── E · 신속한 맹공 ─────────────────────────────
    // 19.0–20.9 s: a white-hot ball of thrusts in front of the chest with needles firing forward
    // and violet swirls on its shell; 21.4 s: rising vertical crescent + lightning pillar.
    static final int E_FINISH=50;
    static Vec3d eCenter(Vec3d pos,Vec3d f){return pos.add(0,1.0,0).add(f.multiply(.55));}
    static float eBall(float a){return smooth(a,6,9)*fade(a,E_FINISH-3,E_FINISH+1);}
    static void e(EffectMesh m,Vec3d pos,Vec3d f,Vec3d right,float a,boolean light){
        Vec3d c=eCenter(pos,f);float ball=eBall(a);
        if(ball>0){
            float flick=.85f+.15f*(float)Math.sin(a*2.7);
            if(light){m.softDisc(c,3.2,0x9d4dff,ball*.5f);m.softDisc(c,2.2,0xf0c8ff,ball*.8f*flick);m.softDisc(c,1.4,0xffffff,ball*flick);}
            else{m.softDisc(c,1.9,0xfff4ff,ball*.7f);m.softDisc(c,2.3,0xb978ff,ball*.35f);}
            for(int i=0;i<90;i++){
                float born=8+i*.47f,life=a-born;if(life<0||life>=3.2f)continue;
                Vec3d d=dir(f,right,90+(hash(i)-.5)*80,(hash(i+1)-.5)*55);
                double r0=.3+.5*hash(i+2),len=1.4+2.6*hash(i+3);
                Vec3d p0=c.add(d.multiply(r0+life*.6)),p1=p0.add(d.multiply(len*smooth(life,0,.8f)));
                beam(m,p0,p1,.06+.05*hash(i+4),ball*fade(life,1,3.2f),0xb36bff,light);
            }
            // Short angular forks breaking out of the shell.
            for(int i=0;i<10;i++){int seed=(int)(a*2)*17+i;Vec3d d=dir(f,right,hash(seed)*360,(hash(seed+1)-.5)*120);
                Vec3d p0=c.add(d.multiply(1.0)),p1=p0.add(d.multiply(.35)).add(d.crossProduct(UP).multiply(.15*(hash(seed+2)-.5))),p2=p1.add(d.multiply(.4));
                m.line(p0,p1,light?.05:.025,0xffffff,ball*.9f);m.line(p1,p2,light?.04:.02,0xffffff,ball*.8f);}
        }
        if(a>=E_FINISH&&a<64){
            float t=a-E_FINISH,al=smooth(t,0,1)*fade(t,4,14);
            Vec3d base=pos.add(f.multiply(1.0));
            // jagged vertical lightning pillar
            Vec3d q=base;for(int s=0;s<14;s++){Vec3d n=base.add(f.multiply((hash(s+700)-.5)*.35)).add(right.multiply((hash(s+701)-.5)*.35)).add(0,(s+1)*.75*smooth(t,0,2),0);
                beam(m,q,n,.28,al,0x9b3cff,light);q=n;}
            m.ring(pos.add(0,1.2,0).add(f.multiply(.6)),f,UP,2.3,64,.45f,light?.06:.03,0xf0e6ff,al*.7f,Math.toRadians(-60));
            m.ring(pos.add(0,1.2,0).add(f.multiply(.6)),f,UP,2.7,64,.38f,light?.05:.025,0xf0e6ff,al*.5f,Math.toRadians(-50));
        }
    }
    static void eStrokes(Vec3d pos,Vec3d f,Vec3d right,float a){
        Vec3d c=eCenter(pos,f);float ball=eBall(a);
        if(ball>0)for(int j=0;j<7;j++){
            Vec3d u=dir(f,right,hash(j+710)*360,(hash(j+711)-.5)*60),v=u.crossProduct(dir(f,right,hash(j+712)*360,40)).normalize();
            double spin=a*(.45+.2*j)*(j%2==0?1:-1)+j;
            KagenoFire.stroke(c,u,v,1.55+.3*hash(j+713),spin,2.6,.45,200+j,ball);
        }
        if(a>=E_FINISH-1&&a<62){
            float t=a-(E_FINISH-1);Vec3d o=pos.add(0,.95,0).add(f.multiply(.75));
            double h=smooth(t,0,2.2f),tl=smooth(t,2,9);float al=fade(t,5,12);
            double start=Math.toRadians(-100),sweep=Math.toRadians(200);
            KagenoFire.stroke(o,f,UP,1.9,start+sweep*tl,sweep*(h-tl),.95,240,al);
            KagenoFire.stroke(o,f,UP,1.55,start+sweep*tl,sweep*(h-tl)*.9,.4,241,al*.8f);
        }
    }

    // ─────────────── T · 어둠 속의 불꽃 / T-hold · 어둠을 가로지르다 ───────────────
    /** White ✦ glints that pop around the caster while the sword is raised (43.8 s, 61.25 s). */
    static void glints(EffectMesh m,Vec3d pos,Vec3d f,Vec3d right,float t,float end){
        if(t<0||t>=end)return;
        for(int i=0;i<8;i++){double ang=hash(i+800)*Math.PI*2;Vec3d p=pos.add(right.multiply(Math.cos(ang)*(.6+.6*hash(i+801)))).add(f.multiply(Math.sin(ang)*.5+.2)).add(0,.2+1.8*hash(i+802),0);
            float al=smooth(t,i*.35f,i*.35f+.8f)*fade(t,end-2,end);sparkle(m,p,.22+.12*hash(i+803),0xffffff,al);}
    }
    static void t(EffectMesh m,Vec3d pos,Vec3d origin,Vec3d f,Vec3d right,float a,java.util.List<Vec3d> targets,boolean light){
        glints(m,pos,f,right,a,T_STRIKE+1);
        float t=a-T_STRIKE;
        if(light&&t>=0&&t<8){float al=smooth(t,0,1)*fade(t,2,8);m.softDisc(origin.add(0,1.2,0),8*smooth(t,0,2.5f),0xffffff,al);m.softDisc(origin.add(0,1,0),4,0xffe6ff,al);}
        // Targets are wrapped in a dark violet orb with swirling black lines (53.3–56.3 s).
        float orb=smooth(t,6,10)*fade(t,62,74);
        if(orb>0)for(var p:targets){Vec3d c=p.add(0,1,0);
            if(!light){m.softDisc(c,1.15,0x1a0333,orb*.85f);m.softDisc(c,.8,0x3a0a73,orb*.6f);}
            for(int j=0;j<6;j++){Vec3d u=dir(f,right,hash(j+820)*360,(hash(j+821)-.5)*90),v=u.crossProduct(dir(f,right,hash(j+822)*360,30)).normalize();
                m.ring(c,u,v,.75+.2*hash(j+823),40,.55f,light?.05:.03,light?0xb46bff:0x05000c,orb*.9f,a*.25*(j%2==0?1:-1)+j);}
        }
    }
    static void rush(EffectMesh m,Vec3d pos,Vec3d origin,Vec3d f,Vec3d right,float a,boolean light){
        glints(m,pos,f,right,a,RUSH_IGNITE+1);
        float t=a-RUSH_IGNITE;
        if(light&&t>=0&&t<9){float al=smooth(t,0,1)*fade(t,2,9);m.softDisc(origin.add(0,1.2,0).add(f.multiply(1.5)),9*smooth(t,0,2.5f),0xffffff,al);}
        // Dash comet: a long white-pink streak from the launch point to the caster.
        if(a>=RUSH_START-1&&a<RUSH_START+10){float al=smooth(a,RUSH_START-1,RUSH_START+1)*fade(a,RUSH_START+3,RUSH_START+10);
            Vec3d tail=origin.add(0,.9,0).subtract(f.multiply(1.5)),head=pos.add(0,.9,0).add(f.multiply(.6));
            beam(m,tail,head,.85,al,0xff9cf0,light);beam(m,tail.add(0,.3,0),head,.35,al,0xffffff,light);
            if(light)m.softDisc(head,2.2,0xffd9ff,al*.8f);}
        // Bright white-pink cores along every rib of the dome.
        if(a>=RUSH_START+2&&a<RUSH_FINISH+2){float al=smooth(a,RUSH_START+2,RUSH_START+5)*fade(a,RUSH_FINISH-2,RUSH_FINISH+2);
            for(int j=0;j<14;j++){Vec3d u=dir(f,right,90-85+j*13.1,0);double grow=smooth(a,RUSH_START+2+j*.5f,RUSH_START+5+j*.5f);
                m.ring(pos.add(0,-.1,0).add(f.multiply(.4)),u,UP,2.6+.5*hash(j+840),48,(float)(130/360.*grow),light?.09:.04,light?0xffc8f6:0xffffff,al*(light?.9f:.6f),Math.toRadians(12+20*hash(j+843)));}}
        if(a>=RUSH_START+2&&a<RUSH_FINISH+2&&light){float al=smooth(a,RUSH_START+2,RUSH_START+4)*fade(a,RUSH_FINISH-2,RUSH_FINISH+2);m.softDisc(pos.add(0,.8,0),2.6,0xc06bff,al*.35f);}
    }
    static void rushStrokes(Vec3d pos,Vec3d f,Vec3d right,float a){
        // 63.05–63.45 s: a dome of vertical crescents closes around the low glide.
        if(a>=RUSH_START+2&&a<RUSH_FINISH+2){
            float al=smooth(a,RUSH_START+2,RUSH_START+5)*fade(a,RUSH_FINISH-2,RUSH_FINISH+2);
            for(int j=0;j<14;j++){double yaw=-85+j*13.1;Vec3d u=dir(f,right,90+yaw,0);
                double grow=smooth(a,RUSH_START+2+j*.5f,RUSH_START+5+j*.5f);
                KagenoFire.stroke(pos.add(0,-.1,0).add(f.multiply(.4)),u,UP,2.6+.5*hash(j+840),Math.toRadians(12+20*hash(j+843)),Math.toRadians(130)*grow,.2+.08*hash(j+841),300+j,al*.55f);}
        }
        // 63.6 s: a broad horizontal dark-violet cut across the frame.
        if(a>=RUSH_FINISH-4&&a<RUSH_FINISH+12){float t=a-(RUSH_FINISH-4);double h=smooth(t,0,2.5f),tl=smooth(t,2.5f,12);float al=fade(t,6,16);
            double start=Math.toRadians(-150),sweep=Math.toRadians(300);
            KagenoFire.stroke(pos.add(0,.75,0),f,right.negate(),4.2+2.2*h,start+sweep*tl,sweep*(h-tl),2.2,330,al);
            KagenoFire.stroke(pos.add(0,.55,0),f,right.negate(),5.4+1.2*h,start+sweep*tl+.3,sweep*(h-tl)*.85,.9,331,al*.8f);}
    }

    /** Full-frame flash (white blasts of T 44.2 s, T-hold 61.7 s, Y 76.7 s). */
    static float flash(int skill,float a){
        return switch(skill){
            case T->smooth(a,T_STRIKE,T_STRIKE+1)*fade(a,T_STRIKE+3,T_STRIKE+9);
            case RUSH->smooth(a,RUSH_IGNITE,RUSH_IGNITE+1)*fade(a,RUSH_IGNITE+3,RUSH_IGNITE+8);
            case Y->smooth(a,Y_BLAST,Y_BLAST+1)*fade(a,Y_BLAST+2,Y_BLAST+7);
            default->0;
        };
    }

    // ───────────────────────────── H · 최강 (마무리) ─────────────────────────────
    // 125.75 s: a bright horizontal cut fires out behind the caster over a dark crescent,
    // 126.06 s: hard cut to the wide view, a thin violet line stays on the ground.
    static void hFinal(EffectMesh m,Vec3d origin,Vec3d f,Vec3d right,float a,boolean light){
        if(a>=93&&a<106){float t=a-93,al=smooth(t,0,1.2f)*fade(t,5,13);
            Vec3d c=origin.add(0,.95,0),end=c.subtract(f.multiply(9)).add(0,-.2,0);
            beam(m,c.add(f.multiply(.6)),end,.5,al,0xa45cff,light);beam(m,c.add(0,.12,0),end.add(0,.25,0),.16,al,0xffffff,light);
            if(light)m.softDisc(c.subtract(f.multiply(2)),2.2,0x9a4cff,al*.45f);}
        if(a>=97&&a<116){float al=smooth(a,97,99)*fade(a,108,116);Vec3d g=origin.add(0,.05,0);
            beam(m,g.add(f.multiply(.3)),g.subtract(f.multiply(14)),.14,al,0x8f45ff,light);}
    }
    static void hStrokes(Vec3d origin,Vec3d f,Vec3d right,float a){
        if(a>=93&&a<106){float t=a-93;double h=smooth(t,0,2.5f),tl=smooth(t,2.5f,12);float al=fade(t,5,13);
            Vec3d o=origin.add(0,1.1,0).subtract(f.multiply(1.6));double start=Math.toRadians(25),sweep=Math.toRadians(-95);
            KagenoFire.stroke(o,f.negate(),UP,2.4,start+sweep*tl,sweep*(h-tl),.9,360,al);}
    }
}
