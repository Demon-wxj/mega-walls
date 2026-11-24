package com.megawalls.maintenance.client;

import com.megawalls.network.S2C_SkillPattern;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.Vec3d;

/** Client-only quality choices never affect ability damage, targets or timing. */
public final class ClientEffectTuning {
    private static int level=1,adaptiveLevel=1,ticks,low,high,particles,visibleCount,detailedCount,incoming,spikeFrames;
    private static long lastFrame;
    private static boolean adaptive=true,enabled=true;
    private static ClientWorld budgetWorld;
    private static long budgetTick=Long.MIN_VALUE;
    private record PatternKey(int shape,int anchor,long x,long y,long z){}
    private static final Map<PatternKey,Long> SEEN=new HashMap<>();
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("megawalls-effects.properties");
    public static void load() {
        if(!Files.isRegularFile(FILE))return;
        try(var in=Files.newInputStream(FILE)) {Properties p=new Properties();p.load(in);level=Math.clamp(Integer.parseInt(p.getProperty("quality","1")),0,2);enabled=Boolean.parseBoolean(p.getProperty("enabled","true"));adaptive=Boolean.parseBoolean(p.getProperty("adaptive","true"));adaptiveLevel=level;}catch(Exception e){com.megawalls.MWMod.LOGGER.warn("Cannot read Mega Walls effect settings",e);}
    }
    private static void save() {
        try {Properties p=new Properties();p.setProperty("quality",Integer.toString(level));p.setProperty("enabled",Boolean.toString(enabled));p.setProperty("adaptive",Boolean.toString(adaptive));Files.createDirectories(FILE.getParent());try(var out=Files.newOutputStream(FILE)){p.store(out,"Mega Walls local visual settings");}}catch(Exception e){com.megawalls.MWMod.LOGGER.warn("Cannot save Mega Walls effect settings",e);}
    }
    public static String name(){return !enabled?"关闭":switch(level){case 0->"精简";case 2->"完整";default->"均衡";};}
    public static boolean enabled(){return enabled;}
    public static void announce(){if(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.megawalls.maintenance.SettingsPayload.ID)){var n=new net.minecraft.nbt.NbtCompound();n.putInt("visualMode",enabled?effective()+1:0);net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.megawalls.maintenance.SettingsPayload(n));}}
    public static void setMode(int mode){enabled=mode>0;if(enabled)level=Math.clamp(mode-1,0,2);adaptiveLevel=level;low=high=0;
        if(!enabled){com.megawalls.client.ClientSkillEffects.reset();com.megawalls.client.ClientEnderRifts.reset();SEEN.clear();}save();announce();}
    public static boolean adaptive(){return adaptive;}
    public static void cycle(){setMode(!enabled?1:(level+2)%4);}
    public static void toggleAdaptive(){adaptive=!adaptive;adaptiveLevel=level;low=high=0;save();announce();}
    public static int effective(){return adaptive?Math.min(level,adaptiveLevel):level;}
    public static void tick(MinecraftClient client) {
        if(client.world!=budgetWorld){budgetWorld=client.world;budgetTick=Long.MIN_VALUE;particles=visibleCount=detailedCount=incoming=spikeFrames=0;lastFrame=0;SEEN.clear();}
        if(++ticks%20!=0)return;
        if(!enabled||!adaptive)return;int old=effective();
        int fps=client.getCurrentFps();
        if(client.world==null||fps<=0){low=high=0;return;}
        if(fps<45){high=0;if(++low>=2)adaptiveLevel=0;}
        else if(fps>65){low=0;if(++high>=8)adaptiveLevel=level;}
        else{low=high=0;}
        if(old!=effective())announce();
    }
    public static int maxEvents(){return switch(effective()){case 0->10;case 2->64;default->24;};}
    public static int maxRifts(){return switch(effective()){case 0->2;case 2->8;default->4;};}
    public static double range(){return switch(effective()){case 0->24;case 2->64;default->40;};}
    public static int particleLimit(){return switch(effective()){case 0->64;case 2->320;default->160;};}
    public static boolean details(){return effective()>0&&(effective()==2&&!adaptive||visibleCount<=6&&incoming<=8&&spikeFrames==0);}
    public static boolean fullDetails(){return effective()==2&&(!adaptive||visibleCount<=12&&incoming<=12&&spikeFrames==0);}
    public static void beginFrame(){
        long now=System.nanoTime();
        if(adaptive&&lastFrame!=0&&visibleCount>0&&now-lastFrame>33_333_333L)spikeFrames=30;
        else if(spikeFrames>0)spikeFrames--;
        lastFrame=now;visibleCount=detailedCount=0;
    }
    public static void visibleEffects(int count){visibleCount=count;}
    public static void detailedEffects(int count){detailedCount=count;}
    public static int detailUnits(){
        int units=switch(effective()){case 0->0;case 2->adaptive?24:192;default->6;};
        return adaptive&&spikeFrames>0?Math.min(units,3):units;
    }
    public static boolean allowPosition(Vec3d at){var client=MinecraftClient.getInstance();return enabled&&client.player!=null&&client.player.squaredDistanceTo(at)<=range()*range();}
    public static boolean allowPattern(MinecraftClient client,S2C_SkillPattern p){return enabled&&client.player!=null&&Math.min(client.player.squaredDistanceTo(p.origin()),client.player.squaredDistanceTo(p.end()))<=range()*range();}
    public static boolean acceptPattern(MinecraftClient client,S2C_SkillPattern p) {
        if(!allowPattern(client,p)||client.world==null)return false;
        resetBudget(client.world);
        PatternKey key=new PatternKey(p.shape(),p.anchorEntityId(),Math.round(p.origin().x*2),Math.round(p.origin().y*2),Math.round(p.origin().z*2));
        long now=client.world.getTime();Long last=SEEN.get(key);
        if(last!=null&&now-last<(effective()==0?2:1))return false;
        SEEN.put(key,now);incoming++;return true;
    }
    private static void resetBudget(ClientWorld world) {
        if(world!=budgetWorld){budgetWorld=world;budgetTick=Long.MIN_VALUE;visibleCount=detailedCount=spikeFrames=0;lastFrame=0;SEEN.clear();}
        if(budgetTick!=world.getTime()){budgetTick=world.getTime();particles=incoming=0;SEEN.entrySet().removeIf(e->budgetTick-e.getValue()>40);}
    }
    public static void addParticle(ClientWorld world,ParticleEffect effect,boolean important,double x,double y,double z,double vx,double vy,double vz) {
        if(!enabled)return;resetBudget(world);if(particles>=particleLimit())return;particles++;
        world.addParticle(effect,false,x,y,z,vx,vy,vz);
    }
    public static boolean allowNative(){return enabled&&fullDetails()&&com.megawalls.client.EnderRiftParticles.debugActiveEmitters()<8;}
}
