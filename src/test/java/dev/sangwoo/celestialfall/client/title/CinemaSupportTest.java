package dev.sangwoo.celestialfall.client.title;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class CinemaSupportTest {
    @Test void missingIndexedQueryIsRejectedEvenWhenVersionClaims33(){
        assertEquals("unavailable entry point: glGetIntegeri_v",CinemaSupport.rejection(true,false,false,n->n.equals("glGetIntegeri_v")?0:1));
    }
    @Test void everyDesktopEntryPointIsRequired(){
        var functions=new ArrayList<String>();
        assertNull(CinemaSupport.rejection(true,false,false,n->{functions.add(n);return 1;}));
        assertTrue(functions.contains("glBindSampler"));assertTrue(functions.contains("glGenFramebuffers"));
        for(String missing:functions)assertNotNull(CinemaSupport.rejection(true,false,false,n->n.equals(missing)?0:1),missing);
    }
    @Test void gles21AndForcedCompatibilityNeverProbeDesktopFunctions(){
        java.util.function.ToLongFunction<String> forbidden=n->{throw new AssertionError("Desktop path reached "+n);};
        assertNotNull(CinemaSupport.rejection(false,false,false,forbidden));
        assertNotNull(CinemaSupport.rejection(true,true,false,forbidden));
        assertNotNull(CinemaSupport.rejection(true,false,true,forbidden));
    }
    @Test void scopeAndGpuRejectBeforeAnyNativeCallWithoutAContext(){
        assertThrows(IllegalStateException.class,CinemaGpu.Scope::new);
        assertThrows(IllegalStateException.class,CinemaGpu::new);
    }
}
