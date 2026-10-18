import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/** A standalone named-jar test needs the same package-access repair as the dev launcher.
 * Only access flags are changed, in memory; no Minecraft logic or deliverable is modified. */
public class MappingAccessAgent {
    public static void premain(String args,Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            int widened(int access) {return (access&Opcodes.ACC_PRIVATE)!=0?access:(access&~Opcodes.ACC_PROTECTED)|Opcodes.ACC_PUBLIC;}
            public byte[] transform(ClassLoader loader,String name,Class<?> redef,ProtectionDomain pd,byte[] bytes) {
                if(!name.startsWith("net/minecraft/"))return null;
                ClassWriter out=new ClassWriter(0);
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9,out) {
                    public void visit(int version,int access,String n,String sig,String parent,String[] interfaces) {
                        super.visit(version,widened(access),n,sig,parent,interfaces);
                    }
                    public FieldVisitor visitField(int access,String n,String desc,String sig,Object value) {
                        return super.visitField(widened(access),n,desc,sig,value);
                    }
                    public MethodVisitor visitMethod(int access,String n,String desc,String sig,String[] exceptions) {
                        return super.visitMethod(widened(access),n,desc,sig,exceptions);
                    }
                },0);
                return out.toByteArray();
            }
        });
    }
}
