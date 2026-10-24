package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.StarPlatinumLook;

/**
 * A bone of Star Platinum's model moving on top of its stance. Floating at rest (2.51.0, user request: "머리카락 막
 * 움직이고 위 아래로 천천히 움직이고 전체적으로 모션 만들어줘") the whole body rises and sinks slowly with its breath, drifts
 * and sways a little, turns its head now and then, its arms float out and in, its legs dangle each a little after the
 * one above, and its hair, scarf and loincloth stream in a wind of its own: every lock and flap a wave running from the
 * root to the tip, a slow sway and a quick flutter, each lock out of step with the next. The art's wind (the hero
 * stance) and a gust (as it bursts out) blow them harder and still the float; following a running user it leans into
 * the run, its arms, legs and the back of its loincloth trailing. Every movement is a sine of the clock in seconds, so
 * each frame between ticks has its own pose.
 */
record StarPlatinumMotion(int kind,float index,int segment) {
    static final int STILL=0,ROOT=1,CHEST=2,HEAD=3,ARM=4,FOREARM=5,PAD=6,HAIR=7,TAIL=8,FLAP=10,SIDE=11,
        HIPS=12,TORSO=13,NECK=14,HAND=15,LEG=16,SHIN=17,FOOT=18;
    /** The breath and the float: one slow rise and sink (seconds), and how far it goes (units; 0.12 of a block at its size). */
    static final double BREATH=3.6;
    static final float BOB=2.4f;
    /** What part a bone of the model is, by its name in tools/stand/make_star_platinum.py. */
    static StarPlatinumMotion of(String name){
        int under=name.lastIndexOf('_');
        int segment=under>0&&Character.isDigit(name.charAt(name.length()-1))?Integer.parseInt(name.substring(under+1)):0;
        float side=name.startsWith("right")?1:-1;
        switch(name){
            case "root":return new StarPlatinumMotion(ROOT,0,0);
            case "hips":return new StarPlatinumMotion(HIPS,0,0);
            case "torso":return new StarPlatinumMotion(TORSO,0,0);
            case "chest":return new StarPlatinumMotion(CHEST,0,0);
            case "neck":return new StarPlatinumMotion(NECK,0,0);
            case "head":return new StarPlatinumMotion(HEAD,0,0);
            default:break;
        }
        if(name.endsWith("_arm"))return new StarPlatinumMotion(ARM,side,0);
        if(name.endsWith("_forearm"))return new StarPlatinumMotion(FOREARM,side,0);
        if(name.endsWith("_hand"))return new StarPlatinumMotion(HAND,side,0);
        if(name.endsWith("_pad"))return new StarPlatinumMotion(PAD,side,0);
        if(name.endsWith("_leg"))return new StarPlatinumMotion(LEG,side,0);
        if(name.endsWith("_shin"))return new StarPlatinumMotion(SHIN,side,0);
        if(name.endsWith("_foot"))return new StarPlatinumMotion(FOOT,side,0);
        if(name.startsWith("hair_"))return new StarPlatinumMotion(HAIR,Integer.parseInt(name.substring(5,7)),segment);
        if(name.startsWith("scarf_tail"))return new StarPlatinumMotion(TAIL,0,segment);
        if(name.startsWith("cloth_front"))return new StarPlatinumMotion(FLAP,0,segment);
        if(name.startsWith("cloth_back"))return new StarPlatinumMotion(FLAP,1.7f,segment);
        if(name.startsWith("cloth_right"))return new StarPlatinumMotion(SIDE,0,segment);
        if(name.startsWith("cloth_left"))return new StarPlatinumMotion(SIDE,2.1f,segment);
        return new StarPlatinumMotion(STILL,0,0);
    }
    static float wave(double t,double period,double phase){return (float)Math.sin(2*Math.PI*t/period+phase);}
    /**
     * Adds this bone's movement at t seconds to its pose (turn x, y, z in degrees as Blockbench writes them, offset x, y,
     * z in units). A positive x turn tips a bone's top forward and swings a hanging one back.
     */
    void add(float[] p,double t,StarPlatinumLook look){
        float calm=1-Math.min(1,look.wind),move=Math.min(1,look.speed/6f);
        switch(kind){
            case ROOT->{
                p[4]+=calm*BOB*wave(t,BREATH,0);
                p[3]+=calm*.35f*wave(t,7.3,1);p[5]+=calm*.3f*wave(t,5.9,2);
                p[1]+=calm*2.5f*wave(t,9.7,0);p[2]+=calm*1f*wave(t,6.1,.4);
            }
            case HIPS->{p[0]+=look.lean+.5f*calm*wave(t,BREATH,-.4);p[2]-=look.roll;}
            case TORSO->p[0]+=.6f*wave(t,BREATH,.2);
            case CHEST->p[0]-=1.4f*wave(t,BREATH,.5);
            case NECK->p[0]+=.5f*wave(t,BREATH,1);
            case HEAD->{p[0]+=1f*wave(t,BREATH,1.4)-.5f*look.lean;p[1]+=calm*4f*wave(t,11.3,0);p[2]+=calm*1.2f*wave(t,8.1,.7);}
            case ARM->{p[2]+=index*calm*1.8f*wave(t,BREATH,.3);p[0]+=calm*2.2f*wave(t,4.9,index)+9*move;}
            case FOREARM->p[0]-=calm*1.5f*(1+wave(t,BREATH,.9))+6*move;
            case HAND->p[0]+=calm*3f*wave(t,BREATH,1.3);
            case PAD->p[2]-=index*.8f*wave(t,BREATH,.5);
            case LEG->{p[0]+=calm*2.4f*wave(t,BREATH,-.9+index*.5)+10*move;p[2]+=index*calm*1.2f*wave(t,BREATH,-.6);}
            case SHIN->p[0]+=calm*3.2f*wave(t,BREATH,-1.5+index*.5)+12*move;
            case FOOT->p[0]+=calm*4.2f*wave(t,BREATH,-2.1+index*.5)+8*move;
            case HAIR->{
                float f=(1+.5f*look.gust)*(1+.3f*move)*(1+.4f*look.wind);
                float slow=(3.2f+4f*segment)*f,fast=(.9f+1.4f*segment)*f;
                double ph=-(index*.83+segment*.85),phf=-(index*1.37+segment*1.1);
                p[0]+=slow*wave(t,2.4,ph)+fast*wave(t,.78,phf);
                p[2]+=.7f*slow*wave(t,2.9,ph*1.13+1.9)+.6f*fast*wave(t,.93,phf+.7);
            }
            case TAIL->{
                float f=(1+.7f*look.gust)*(1+.5f*move)*(1+.6f*look.wind);
                float slow=(3f+2.2f*segment)*f,fast=(.7f+.6f*segment)*f;
                p[0]+=slow*wave(t,1.7,-.9*segment)+fast*wave(t,.58,-1.3*segment);
                p[2]+=.6f*slow*wave(t,2.3,-.9*segment+1)+.5f*fast*wave(t,.71,-1.3*segment+.4);
            }
            case FLAP->{
                float a=(1.4f+1.6f*segment)*(1+.6f*look.gust)*(1+.5f*look.wind);
                p[0]+=a*wave(t,2.0,-.75*segment+index)+.35f*a*wave(t,.7,-segment+index*1.3)+(index>0?14+6*segment:4)*move;
                p[2]+=.5f*a*wave(t,2.7,-.75*segment+index);
            }
            case SIDE->{
                float a=(1.6f+1.6f*segment)*(1+.6f*look.gust)*(1+.5f*look.wind);
                p[2]+=a*wave(t,2.2,-.7*segment+index)+.35f*a*wave(t,.75,-segment+index);
                p[0]+=.5f*a*wave(t,2.9,-.7*segment+index)+(8+4*segment)*move;
            }
            default->{}
        }
    }
}
