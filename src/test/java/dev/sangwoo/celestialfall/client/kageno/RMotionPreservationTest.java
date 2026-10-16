package dev.sangwoo.celestialfall.client.kageno;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Golden samples were generated from the user's untouched 2.28.1 R/Anim/R6 sources. */
class RMotionPreservationTest {
    @Test void everyRJointAndRootMatches2281At120Hz() throws Exception {
        try(var in=getClass().getResourceAsStream("/kageno/r_2_28_1.csv")){
            assertNotNull(in);
            var lines=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)).lines().toList();
            int count=0;
            for(String line:lines){
                if(line.startsWith("#"))continue;
                String[] values=line.split(",");float sec=Float.parseFloat(values[0]);
                var f=TimeWalkerSkill.CLIP.frame(sec);
                double[] actual=new double[25];int n=0;
                actual[n++]=f.grip();actual[n++]=f.fw();actual[n++]=f.up();actual[n++]=f.rt();
                actual[n++]=f.yaw();actual[n++]=f.pitch();actual[n++]=f.roll();
                for(float[] joint:f.joints().joint)for(float angle:joint)actual[n++]=angle;
                for(int j=0;j<n;j++)assertEquals(Double.parseDouble(values[j+1]),actual[j],.00001,"R at "+sec+", component "+j);
                count++;
            }
            assertEquals(361,count);
        }
    }
}
