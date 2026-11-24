import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Checked links into the original anniversary binaries. Preserve their callbacks, mixin mappings and rollback logs. */
public final class ArenaRulesPatcher implements Opcodes {
    static final String G="com/megawalls/minigame/", M="com/megawalls/mixin/", B=G+"BedBowRules", U=G+"UhcRules";
    static final String SW="Lnet/minecraft/server/world/ServerWorld;",POS="Lnet/minecraft/util/math/BlockPos;",CIR="Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;",CI="Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    static JarFile jar;static Path out;static int touched;
    static ClassNode read(String name)throws Exception{var c=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class"))).accept(c,0);return c;}
    static void write(ClassNode c)throws Exception{var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Path p=out.resolve(c.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());touched++;}
    static MethodNode one(ClassNode c,String n){var x=c.methods.stream().filter(m->m.name.equals(n)).toList();if(x.size()!=1)throw new AssertionError(c.name+":"+n);return x.get(0);}
    static void end(MethodNode m,int opcode,java.util.function.Supplier<InsnList> list){int count=0;for(var n:m.instructions.toArray())if(n.getOpcode()==opcode){m.instructions.insertBefore(n,list.get());count++;}if(count==0)throw new AssertionError(m.name);}
    static InsnList call(String owner,String name,String desc,int...args){var l=new InsnList();for(int a:args)l.add(new VarInsnNode(ALOAD,a));l.add(new MethodInsnNode(INVOKESTATIC,owner,name,desc,false));return l;}
    static void body(MethodNode m,InsnList body){m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;body.add(new InsnNode(RETURN));m.instructions.add(body);}
    static AbstractInsnNode previousOp(AbstractInsnNode n){do{n=n.getPrevious();}while(n!=null&&n.getOpcode()<0);return n;}
    static void saveRestoredChunks(ClassNode arena,String method){
        var m=one(arena,method);int hooks=0;String save=G+"ArenaChunkSave";
        for(var n:m.instructions.toArray())if(n instanceof MethodInsnNode it&&it.owner.equals("net/minecraft/server/world/ServerWorld")&&it.name.equals("save")&&it.desc.equals("(Lnet/minecraft/util/ProgressListener;ZZ)V")){
            var skip=previousOp(n);var flush=previousOp(skip);var progress=previousOp(flush);var world=previousOp(progress);
            var gate=previousOp(world);var hasNext=previousOp(gate);
            if(skip.getOpcode()!=ICONST_0||flush.getOpcode()!=ICONST_1||progress.getOpcode()!=ACONST_NULL||!(world instanceof VarInsnNode w)||w.getOpcode()!=ALOAD||gate.getOpcode()!=IFNE||!(hasNext instanceof MethodInsnNode iterator)||!iterator.name.equals("hasNext"))throw new AssertionError("unexpected arena save in "+arena.name);
            var check=new InsnList();check.add(new VarInsnNode(ALOAD,w.var));check.add(new FieldInsnNode(GETSTATIC,arena.name,"originals","Ljava/util/Map;"));
            check.add(new MethodInsnNode(INVOKESTATIC,save,"more","(Z"+SW+"Ljava/util/Map;)Z",false));m.instructions.insertBefore(gate,check);
            for(var op:List.of(world,progress,flush,skip,n))m.instructions.remove(op);
            hooks++;
        }
        if(hooks!=1)throw new AssertionError("arena save count "+arena.name+": "+hooks);
        for(String clear:List.of("beginReset","clear")){
            var release=new InsnList();release.add(new FieldInsnNode(GETSTATIC,arena.name,"originals","Ljava/util/Map;"));
            release.add(new MethodInsnNode(INVOKESTATIC,save,"clear","(Ljava/util/Map;)V",false));one(arena,clear).instructions.insert(release);
        }
    }
    static void config(String owner,String parent)throws Exception{
        var c=read(G+owner+"$Config");if(!c.superName.equals("java/lang/Object"))throw new AssertionError();c.superName=parent;int count=0;
        for(var n:one(c,"<init>").instructions.toArray())if(n instanceof MethodInsnNode m&&m.getOpcode()==INVOKESPECIAL&&m.name.equals("<init>")&&m.owner.equals("java/lang/Object")){m.owner=parent;count++;}if(count!=1)throw new AssertionError();write(c);
        c=read(G+owner);end(one(c,"values"),IRETURN,()->call(parent,"valid","(ZLjava/lang/Object;)Z",0));
        if(owner.equals("BedWars")){
            int matched=0;for(var m:c.methods){boolean converts=false;for(var n:m.instructions.toArray())if(n instanceof TypeInsnNode type&&type.getOpcode()==NEW&&type.desc.equals(G+"BedWitherSkull"))converts=true;
                if(converts)for(var n:m.instructions.toArray())if(n instanceof MethodInsnNode it&&it.owner.equals(G+"BedWars")&&it.name.equals("deathmatch")){it.owner=B;it.name="active";matched++;}}
            if(matched!=1)throw new AssertionError("bow conversion hook "+matched);
        }write(c);
    }
    static void visualAudiences()throws Exception{
        String audience="com/megawalls/maintenance/VisualAudience";int particles=0,filters=0;
        for(var en:Collections.list(jar.entries())){var n=en.getName();if(!n.endsWith(".class")||!(n.startsWith("com/megawalls/skill/")||n.startsWith("com/megawalls/wither/")||n.startsWith("com/megawalls/passive/")))continue;
            var f=out.resolve(n);ClassNode c=new ClassNode();new ClassReader(Files.isRegularFile(f)?Files.readAllBytes(f):jar.getInputStream(en).readAllBytes()).accept(c,0);boolean changed=false;
            for(var m:c.methods)for(var inst:m.instructions.toArray())if(inst instanceof MethodInsnNode it){
                if(it.owner.equals("net/minecraft/server/world/ServerWorld")&&it.name.equals("spawnParticles")&&it.desc.equals("(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I")){
                    it.setOpcode(INVOKESTATIC);it.desc="("+SW+it.desc.substring(1);it.owner=audience;it.name="particles";it.itf=false;changed=true;particles++;}
                if((c.name.endsWith("/SkillEffects")||c.name.endsWith("/EnderRiftEffects"))&&it.owner.equals(G+"GameSessions")&&it.name.equals("mw")){
                    it.owner=audience;it.name="receives";changed=true;filters++;}
            }if(changed)write(c);
        }if(particles<10||filters!=3)throw new AssertionError("particle hooks: "+particles+" / "+filters);
        var c=read("com/megawalls/client/MWClient");int renderer=0;
        for(var m:c.methods)for(var n:m.instructions.toArray())if(n instanceof InvokeDynamicInsnNode ind)for(int i=0;i<ind.bsmArgs.length;i++)if(ind.bsmArgs[i] instanceof Handle h&&h.getTag()==H_NEWINVOKESPECIAL){
            String kind=switch(h.getOwner()){case "net/minecraft/client/render/entity/LightningEntityRenderer"->"Bolt";case "net/minecraft/client/render/entity/FallingBlockEntityRenderer"->"Iron";default->"";};
            if(!kind.isEmpty()){ind.bsmArgs[i]=new Handle(h.getTag(),"com/megawalls/maintenance/client/VisualRenderers$"+kind,h.getName(),h.getDesc(),false);renderer++;}}
        if(renderer!=2)throw new AssertionError("decorative renderers "+renderer);write(c);
    }
    public static void main(String[] args)throws Exception{out=Path.of(args[1]);try(var source=new JarFile(args[0])){jar=source;
        config("BedWars",B);config("ClassicDuel",U);
        var c=read(G+"BedShop");int count=0;
        for(String name:List.of("enableWitherBows","product"))for(var m:c.methods)if(m.name.equals(name))for(var n:m.instructions.toArray())if(n instanceof MethodInsnNode it&&it.owner.equals(G+"BedWars")&&it.name.equals("deathmatch")){it.owner=B;it.name="active";count++;}
        // Some releases name the shop preview factory differently. Every BedShop deathmatch call is solely a bow label.
        for(var m:c.methods)for(var n:m.instructions.toArray())if(n instanceof MethodInsnNode it&&it.owner.equals(G+"BedWars")&&it.name.equals("deathmatch")){it.owner=B;it.name="active";count++;}
        if(count!=2)throw new AssertionError("bow shop "+count);
        String specials=G+"BedSpecial",stack="Lnet/minecraft/item/ItemStack;";
        end(one(c,"reward"),ARETURN,()->call(specials,"describe","("+stack+")"+stack));write(c);
        c=read(G+"BedShop$Menu");int lore=0;
        for(var n:one(c,"show").instructions.toArray())if(n instanceof MethodInsnNode it&&it.owner.equals("net/minecraft/component/type/LoreComponent")&&it.name.equals("<init>")){
            var setter=n.getNext();while(setter!=null&&setter.getOpcode()<0)setter=setter.getNext();
            if(!(setter instanceof MethodInsnNode put)||!put.name.equals("set")||!put.desc.equals("(Lnet/minecraft/component/ComponentType;Ljava/lang/Object;)Ljava/lang/Object;"))throw new AssertionError("BedWars shop lore link");
            put.owner=specials;put.name="shopLore";put.setOpcode(INVOKESTATIC);put.desc="("+stack+put.desc.substring(1);lore++;
        }
        if(lore!=1)throw new AssertionError("BedWars shop lore hooks "+lore);write(c);
        c=read(G+"DuelArena");end(one(c,"editable"),IRETURN,()->call(U,"editable","(Z"+SW+POS+")Z",0,1));saveRestoredChunks(c,"reset");write(c);
        c=read(G+"BedArena");saveRestoredChunks(c,"restore");write(c);
        c=read(M+"MWPlacedBlockMixin");end(one(c,"duel$place"),RETURN,()->call(U,"place","(Lnet/minecraft/item/ItemPlacementContext;"+CIR+")V",1,3));write(c);
        c=read(M+"DuelBucketMixin");end(one(c,"duel$bucket"),RETURN,()->call(U,"bucket","(Ljava/lang/Object;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/World;"+POS+CIR+")V",0,1,2,3,5));write(c);
        c=read(M+"MWFluidProtectionMixin");body(one(c,"mw$protect"),call(U,"flow","(Lnet/minecraft/world/WorldAccess;"+POS+"Lnet/minecraft/util/math/Direction;Lnet/minecraft/fluid/FluidState;"+CI+")V",1,2,4,5,6));write(c);
        visualAudiences();
    }System.out.println("Verified arena rule links in "+touched+" game/config/mixin classes");}
}
