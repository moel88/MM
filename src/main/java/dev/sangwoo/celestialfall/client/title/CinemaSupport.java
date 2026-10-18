package dev.sangwoo.celestialfall.client.title;

import org.lwjgl.opengl.GL;
import java.util.function.ToLongFunction;

/** Java-only preflight: a missing LWJGL entry point can abort the VM, not throw. */
public final class CinemaSupport {
    private static final String[] REQUIRED={
        "glGetIntegerv","glGetIntegeri_v","glGetBooleanv","glGetFloatv","glGetDoublev","glIsEnabled",
        "glGenVertexArrays","glBindVertexArray","glDeleteVertexArrays","glBindSampler",
        "glGenFramebuffers","glBindFramebuffer","glDeleteFramebuffers","glFramebufferTexture2D","glCheckFramebufferStatus",
        "glGenBuffers","glBindBuffer","glBufferData","glDeleteBuffers","glEnableVertexAttribArray","glVertexAttribPointer",
        "glCreateShader","glShaderSource","glCompileShader","glGetShaderiv","glGetShaderInfoLog","glDeleteShader",
        "glCreateProgram","glAttachShader","glBindAttribLocation","glBindFragDataLocation","glLinkProgram",
        "glGetProgramiv","glGetProgramInfoLog","glUseProgram","glDeleteProgram","glGetUniformLocation",
        "glUniformMatrix4fv","glUniform1i","glUniform1f","glUniform2f","glUniform3f",
        "glActiveTexture","glBindTexture","glGenTextures","glDeleteTextures","glTexParameteri","glTexImage2D",
        "glDrawArrays","glDrawBuffer","glReadBuffer","glViewport","glEnable","glDisable",
        "glDepthFunc","glDepthMask","glColorMask","glClearDepth","glClearColor","glClear",
        "glPolygonOffset","glBlendEquationSeparate","glBlendFuncSeparate"
    };
    /** Also usable without a GL context, so every missing pointer can be regression-tested. */
    public static String rejection(boolean desktop33,boolean translated,boolean forced,ToLongFunction<String> address){
        if(forced)return "compatibility renderer requested";
        if(translated)return "Android/GLES translation backend";
        if(!desktop33)return "desktop OpenGL 3.3 unavailable";
        for(String name:REQUIRED)if(address.applyAsLong(name)==0)return "unavailable entry point: "+name;
        return null;
    }
    static String incompatibility(){
        try{
            var caps=GL.getCapabilities();
            boolean translated=System.getenv("MOD_ANDROID_RUNTIME")!=null||System.getenv("LIBGL_ES")!=null;
            return rejection(caps.OpenGL33,translated,Boolean.getBoolean("botand.cinema.compatibility"),name->{
                try{return caps.getClass().getField(name).getLong(caps);}catch(ReflectiveOperationException e){return 0;}
            });
        }catch(IllegalStateException e){return "no current desktop GL capabilities";}
    }
    static void requireDesktop(){String why=incompatibility();if(why!=null)throw new IllegalStateException(why);}
    private CinemaSupport(){}
}
