import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Patch only budget/decoration boundaries, retaining the original effect geometry. */
public final class VisualPatcher implements Opcodes {
    private static final String T="com/megawalls/maintenance/client/ClientEffectTuning";
    private static int patches;
    private static void guard(MethodNode m,InsnList test) {
        LabelNode keep=new LabelNode();
        test.add(new JumpInsnNode(IFNE,keep));
        if(Type.getReturnType(m.desc).getSort()==Type.BOOLEAN){test.add(new InsnNode(ICONST_0));test.add(new InsnNode(IRETURN));}
        else test.add(new InsnNode(RETURN));
        test.add(keep);test.add(new FrameNode(F_SAME,0,null,0,null));
        m.instructions.insert(test);patches++;
    }
    private static InsnList boolCall(String name){InsnList l=new InsnList();l.add(new MethodInsnNode(INVOKESTATIC,T,name,"()Z",false));return l;}
    private static InsnList patternCall(String name){InsnList l=new InsnList();l.add(new VarInsnNode(ALOAD,0));l.add(new VarInsnNode(ALOAD,1));l.add(new MethodInsnNode(INVOKESTATIC,T,name,"(Lnet/minecraft/client/MinecraftClient;Lcom/megawalls/network/S2C_SkillPattern;)Z",false));return l;}
    private static void replaceInt(MethodNode m,int before,String method) {
        for(AbstractInsnNode n:m.instructions.toArray()) {
            if(n instanceof IntInsnNode i && i.operand==before) {m.instructions.set(n,new MethodInsnNode(INVOKESTATIC,T,method,"()I",false));patches++;}
        }
    }
    public static void main(String[] args)throws Exception {
        Path target=Path.of(args[1]);
        List<String> touched=new ArrayList<>();
        try(JarFile jar=new JarFile(args[0])) {
            for(String name:List.of("ClientSkillEffects","SkillVanillaParticles","ClientSkillCinematics","ClientEnderRifts","EnderRiftParticles","EnderRiftRenderer","SkillLayeredDetails","ClientClassSurfaces")) {
                String entry="com/megawalls/client/"+name+".class";
                Path maintained=target.resolve(entry);
                byte[] input=Files.isRegularFile(maintained)?Files.readAllBytes(maintained):jar.getInputStream(jar.getJarEntry(entry)).readAllBytes();
                ClassNode cls=new ClassNode();new ClassReader(input).accept(cls,0);
                int initial=patches;
                for(MethodNode m:cls.methods) {
                    if(name.equals("ClientSkillEffects")&&m.name.equals("play"))guard(m,patternCall("allowPattern"));
                    if(name.equals("ClientSkillCinematics")&&m.name.equals("play")){guard(m,patternCall("acceptPattern"));replaceInt(m,128,"maxEvents");}
                    if(name.equals("ClientSkillCinematics")&&m.name.equals("renderOne")) {
                        InsnList test=new InsnList();test.add(new VarInsnNode(ALOAD,1));test.add(new FieldInsnNode(GETFIELD,"com/megawalls/client/ClientSkillCinematics$Event","packet","Lcom/megawalls/network/S2C_SkillPattern;"));
                        test.add(new MethodInsnNode(INVOKEVIRTUAL,"com/megawalls/network/S2C_SkillPattern","origin","()Lnet/minecraft/util/math/Vec3d;",false));
                        test.add(new MethodInsnNode(INVOKESTATIC,T,"allowPosition","(Lnet/minecraft/util/math/Vec3d;)Z",false));guard(m,test);
                    }
                    if(name.equals("SkillVanillaParticles")&&m.name.equals("beginTick"))replaceInt(m,360,"particleLimit");
                    if(name.equals("ClientEnderRifts")&&m.name.equals("play")){
                        guard(m,boolCall("enabled"));
                        replaceInt(m,32,"maxRifts");
                        for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof LdcInsnNode l&&l.cst.equals(96.0)){m.instructions.set(n,new MethodInsnNode(INVOKESTATIC,T,"range","()D",false));patches++;}
                    }
                    if(name.equals("EnderRiftParticles")&&(m.name.equals("spawn")||m.name.startsWith("lambda$spawn")))guard(m,boolCall("allowNative"));
                    if(name.equals("EnderRiftRenderer")&&List.of("crawlingArcs","branches","wisps").contains(m.name))guard(m,boolCall("fullDetails"));
                    if((name.equals("SkillLayeredDetails")||name.equals("ClientClassSurfaces"))&&m.name.equals("render"))guard(m,boolCall("details"));
                    if(name.equals("EnderRiftRenderer")&&m.name.equals("drawEndpoint")){
                        InsnList test=new InsnList();test.add(new VarInsnNode(ALOAD,1));test.add(new MethodInsnNode(INVOKESTATIC,T,"allowPosition","(Lnet/minecraft/util/math/Vec3d;)Z",false));guard(m,test);
                    }
                    for(AbstractInsnNode n:m.instructions.toArray()) {
                        if(n instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/client/world/ClientWorld")&&call.name.equals("addParticle")&&call.desc.equals("(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V")){
                            call.setOpcode(INVOKESTATIC);call.owner=T;call.name="addParticle";call.desc="(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V";call.itf=false;patches++;
                        }
                    }
                }
                if(patches==initial)throw new IllegalStateException("No verified visual patch for "+name);
                ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);cls.accept(writer);
                Path file=target.resolve(entry);Files.createDirectories(file.getParent());Files.write(file,writer.toByteArray());touched.add(entry);
            }
        }
        System.out.println("Visual budgets applied: "+patches+" checked boundaries across "+touched.size()+" classes");
    }
}
