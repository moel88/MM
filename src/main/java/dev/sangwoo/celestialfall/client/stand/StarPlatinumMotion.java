package dev.sangwoo.celestialfall.client.stand;

/**
 * A bone of Star Platinum's model moving on top of its stance (2.50.0): breathing, the slow float of the idle stance,
 * and the hair, the scarf and the loincloth stirring, each piece of a lock or a flap a little after the one above it.
 * Every movement is a sine of the entity's clock in seconds, so each frame between ticks has its own pose; the wind of
 * the art's stance (0 to 1) blows the hair, scarf and cloth harder and stills the idle float.
 */
record StarPlatinumMotion(int kind,float index,int segment) {
    static final int STILL=0,ROOT=1,CHEST=2,HEAD=3,ARM=4,FOREARM=5,PAD=6,HAIR=7,TAIL=8,BIB=9,FLAP=10,SIDE=11;
    /** What part a bone of the model is, by its name in tools/stand/make_star_platinum.py. */
    static StarPlatinumMotion of(String name){
        int under=name.lastIndexOf('_');
        int segment=under>0&&Character.isDigit(name.charAt(name.length()-1))?Integer.parseInt(name.substring(under+1)):0;
        float side=name.startsWith("right")?1:-1;
        if(name.equals("root"))return new StarPlatinumMotion(ROOT,0,0);
        if(name.equals("chest"))return new StarPlatinumMotion(CHEST,0,0);
        if(name.equals("head"))return new StarPlatinumMotion(HEAD,0,0);
        if(name.endsWith("_arm"))return new StarPlatinumMotion(ARM,side,0);
        if(name.endsWith("_forearm"))return new StarPlatinumMotion(FOREARM,side,0);
        if(name.endsWith("_pad"))return new StarPlatinumMotion(PAD,side,0);
        if(name.startsWith("hair_"))return new StarPlatinumMotion(HAIR,Integer.parseInt(name.substring(5,7)),segment);
        if(name.startsWith("scarf_tail"))return new StarPlatinumMotion(TAIL,0,segment);
        if(name.startsWith("scarf_bib"))return new StarPlatinumMotion(BIB,0,segment);
        if(name.startsWith("cloth_front"))return new StarPlatinumMotion(FLAP,0,segment);
        if(name.startsWith("cloth_back"))return new StarPlatinumMotion(FLAP,1.7f,segment);
        if(name.startsWith("cloth_right"))return new StarPlatinumMotion(SIDE,0,segment);
        if(name.startsWith("cloth_left"))return new StarPlatinumMotion(SIDE,2.1f,segment);
        return new StarPlatinumMotion(STILL,0,0);
    }
    static float wave(double t,double period,double phase){return (float)Math.sin(2*Math.PI*t/period+phase);}
    /** Adds this bone's movement at t seconds to its pose (turn x, y, z in degrees as Blockbench writes them, offset x, y, z in units). */
    void add(float[] p,double t,float wind){
        float calm=1-wind;
        switch(kind){
            case ROOT->p[4]+=calm*.6f*wave(t,3.4,0);
            case CHEST->p[0]+=1.1f*wave(t,3.4,.5);
            case HEAD->{p[0]+=.7f*wave(t,3.4,1.1);p[1]+=calm*2f*wave(t,7.7,0);}
            case ARM->{p[2]+=index*calm*1.4f*wave(t,3.4,.3);p[0]+=calm*1.2f*wave(t,4.6,index);}
            case FOREARM->p[0]-=calm*.8f*(1+wave(t,3.4,.8));
            case PAD->p[2]-=index*.5f*wave(t,3.4,.5);
            case HAIR->{float a=(.9f+.9f*segment)*(1+1.4f*wind),ph=index*.83f+segment*.6f;p[0]+=a*wave(t,2.3,ph);p[2]+=.7f*a*wave(t,3.1,ph*1.3f);}
            case TAIL->{float a=(2.5f+2f*segment)*(1+wind);p[0]+=a*wave(t,1.7,segment*.8f);p[2]+=.6f*a*wave(t,2.3,segment*.8f+1);}
            case BIB->p[0]+=(.8f+.6f*segment)*(1+wind)*wave(t,2.6,segment*.7f);
            case FLAP->{float a=(1.2f+1.2f*segment)*(1+1.5f*wind);p[0]+=a*wave(t,2.0,segment*.75f+index);p[2]+=.5f*a*wave(t,2.7,segment*.75f+index);}
            case SIDE->{float a=(1.5f+1.5f*segment)*(1+1.5f*wind);p[2]+=a*wave(t,2.2,segment*.7f+index);p[0]+=.5f*a*wave(t,2.9,segment*.7f+index);}
            default->{}
        }
    }
}
