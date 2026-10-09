package com.megawalls.minigame;

import com.google.gson.*;
import com.megawalls.MWMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.entity.player.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.potion.Potions;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class BedShop {
    public static final String[] CATEGORIES={"armor","swords","bows","eat","pickaxes","blocks","usefulstuff","drinks"};
    private static final String[] LABELS={"护甲","武器","弓箭","食物","工具","方块","特殊","药水"};
    private static final Item[] ICONS={Items.DIAMOND_CHESTPLATE,Items.DIAMOND_SWORD,Items.BOW,Items.COOKED_PORKCHOP,Items.DIAMOND_PICKAXE,Items.CUT_SANDSTONE,Items.TNT,Items.POTION};
    public static final class Offer {public String id,category,name,item,special="",potion="",note="";public int count=1;public Map<String,Integer> price=new LinkedHashMap<>(),enchants=new LinkedHashMap<>();}
    public static final class Config {public int schema=1;public String basis;public List<Offer> offers=new ArrayList<>();}
    private static Config config=new Config();
    private static boolean valid;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    // Only migrate the byte-for-byte former default; edited world shops stay authoritative.
    private static final String LEGACY_DEFAULT_SHA256="3b0fe6a55d460b0b395462bcf06a3859f25cd67131857ec0e8c908900b35d758";
    public static Path file(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("bedwars-shop.json");}
    private static byte[] defaults()throws java.io.IOException{
        try(var in=BedShop.class.getResourceAsStream("/bedwars-shop-default.json")){
            if(in==null)throw new java.io.IOException("Missing default shop");return in.readAllBytes();
        }
    }
    private static void migrate(Path path,byte[] original,byte[] updated)throws java.io.IOException{
        if(!Arrays.equals(Files.readAllBytes(path),original))throw new java.io.IOException("Shop changed while loading");
        var backup=path.resolveSibling("bedwars-shop.pre-1.18.33.json");
        if(Files.exists(backup)){
            if(!Arrays.equals(Files.readAllBytes(backup),original))throw new java.io.IOException("Shop migration backup differs");
        }else Files.write(backup,original,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        var temp=Files.createTempFile(path.getParent(),"bedwars-shop-",".tmp");
        try{
            Files.write(temp,updated);
            try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
        MWMod.LOGGER.info("Updated unmodified BedWars shop; previous config saved to {}",backup);
    }
    public static boolean load(MinecraftServer s){try{
        var path=file(s);boolean exists=Files.exists(path);var original=exists?Files.readAllBytes(path):defaults();
        boolean upgrade=exists&&LEGACY_DEFAULT_SHA256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)));
        var proposed=upgrade?defaults():original;
        var next=JSON.fromJson(new String(proposed,StandardCharsets.UTF_8),Config.class);if(next==null||next.schema!=1||next.offers==null||next.offers.isEmpty()||next.offers.size()>200)throw new IllegalArgumentException("Invalid shop");
        var ids=new HashSet<String>();for(var o:next.offers){
            if(o.id==null||!ids.add(o.id)||!Arrays.asList(CATEGORIES).contains(o.category)||o.name==null||o.name.isBlank()||o.count<1||o.count>64||Identifier.tryParse(o.item)==null||!Registries.ITEM.containsId(Identifier.of(o.item))||o.item.equals("minecraft:air")||o.price==null||o.price.isEmpty()||o.price.values().stream().anyMatch(v->v==null||v<0||v>4096)||o.price.values().stream().mapToInt(Integer::intValue).sum()==0||!Set.of("bronze","iron","gold").containsAll(o.price.keySet())||o.enchants==null||!Set.of("","magnet","rescue","trap","wall","warp","tnt").contains(o.special)||!Set.of("","healing","strong_healing","swiftness","strength","regeneration").contains(o.potion))throw new IllegalArgumentException("Invalid offer "+o.id);
            for(var en:o.enchants.entrySet())if(en.getValue()==null||en.getValue()<1||en.getValue()>5||s.getRegistryManager().get(RegistryKeys.ENCHANTMENT).get(Identifier.ofVanilla(en.getKey()))==null)throw new IllegalArgumentException("Invalid enchantment");
        }
        for(var c:CATEGORIES)if(next.offers.stream().filter(o->o.category.equals(c)).count()>27)throw new IllegalArgumentException("Category exceeds 27 offers");
        if(upgrade)migrate(path,original,proposed);
        else if(!exists)Files.write(path,proposed,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        config=next;valid=true;return true;
    }catch(Exception e){valid=false;MWMod.LOGGER.error("Cannot load bedwars shop; purchasing disabled",e);return false;}}
    public static boolean valid(){return valid;}
    public static List<Offer> offers(){return List.copyOf(config.offers);}
    public static ItemStack currency(String type,int count){
        var stack=new ItemStack(switch(type){case "bronze"->Items.BRICK;case "iron"->Items.IRON_INGOT;default->Items.GOLD_INGOT;},count);
        var n=new NbtCompound();n.putString("bw_currency",type);n.putString("bw_round",BedWars.roundId());n.putString("mg_item_game",BedWars.ID);stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(label(type)));return stack;
    }
    private static String label(String type){return switch(type){case "bronze"->"铜";case "iron"->"铁";default->"金";};}
    public static boolean money(ItemStack stack,String type){var n=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();return !stack.isEmpty()&&n.getString("bw_currency").equals(type)&&n.getString("bw_round").equals(BedWars.roundId())&&stack.isOf(switch(type){case "bronze"->Items.BRICK;case "iron"->Items.IRON_INGOT;default->Items.GOLD_INGOT;});}
    public static String special(ItemStack stack){var n=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();return n.getString("bw_round").equals(BedWars.roundId())?n.getString("bw_special"):"";}
    public static ItemStack reward(ServerPlayerEntity p,Offer o){
        var stack=new ItemStack(Registries.ITEM.get(Identifier.of(o.item)),o.count);var n=new NbtCompound();n.putString("mg_item_game",BedWars.ID);n.putString("bw_round",BedWars.roundId());n.putString("bw_special",o.special);
        if(stack.isOf(Items.BOW))n.putString("bw_bow_name",o.name);
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(stack.isOf(Items.BOW)&&BedWars.deathmatch()?"凋零弓 · "+o.name:o.name));
        for(var en:o.enchants.entrySet())stack.addEnchantment(p.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(RegistryKey.of(RegistryKeys.ENCHANTMENT,Identifier.ofVanilla(en.getKey()))),en.getValue());
        if(stack.getItem() instanceof SwordItem)LegacyDuelItems.sword(stack);
        if(o.item.contains("leather_"))stack.set(DataComponentTypes.DYED_COLOR,new DyedColorComponent(BedWars.color(p),false));
        if(!o.potion.isEmpty())stack.set(DataComponentTypes.POTION_CONTENTS,new PotionContentsComponent(switch(o.potion){case "healing"->Potions.HEALING;case "strong_healing"->Potions.STRONG_HEALING;case "swiftness"->Potions.SWIFTNESS;case "strength"->Potions.STRENGTH;default->Potions.REGENERATION;}));
        return stack;
    }
    /** Rename this round's existing shop bows after the authoritative phase transition. */
    public static void enableWitherBows(ServerPlayerEntity p){
        if(!BedWars.deathmatch()||!BedWars.inRound(p)||!BedWars.member(p))return;
        boolean changed=false;
        for(int i=0;i<p.getInventory().size();i++){
            var stack=p.getInventory().getStack(i);if(!stack.isOf(Items.BOW))continue;
            var n=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
            if(!n.getString("mg_item_game").equals(BedWars.ID)||!n.getString("bw_round").equals(BedWars.roundId())||n.getString("bw_bow_name").isBlank())continue;
            var name="凋零弓 · "+n.getString("bw_bow_name");
            if(!stack.getName().getString().equals(name)){stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(name));changed=true;}
        }
        if(p.currentScreenHandler instanceof Menu menu)menu.show(p,menu.category);
        if(changed)p.currentScreenHandler.syncState();
    }
    private static String price(Offer o){var list=new ArrayList<String>();o.price.forEach((type,n)->{if(n>0)list.add(n+" "+label(type));});return String.join(" + ",list);}
    public static boolean buy(ServerPlayerEntity p,Offer o){
        if(!valid||!BedWars.fighter(p)||!config.offers.contains(o))return false;
        var temp=new ArrayList<ItemStack>();for(int i=0;i<36;i++)temp.add(p.getInventory().getStack(i).copy());
        for(var cost:o.price.entrySet()){int remaining=cost.getValue();for(var stack:temp)if(money(stack,cost.getKey())){int take=Math.min(remaining,stack.getCount());stack.decrement(take);remaining-=take;}if(remaining>0){p.sendMessage(Text.literal("资源不足：需要 "+price(o)),true);return false;}}
        var reward=reward(p,o);for(var stack:temp)if(ItemStack.areItemsAndComponentsEqual(stack,reward)){int add=Math.min(reward.getCount(),stack.getMaxCount()-stack.getCount());stack.increment(add);reward.decrement(add);}
        for(int i=0;i<36&&!reward.isEmpty();i++)if(temp.get(i).isEmpty()){int n=Math.min(reward.getCount(),reward.getMaxCount());temp.set(i,reward.copyWithCount(n));reward.decrement(n);}
        if(!reward.isEmpty()){p.sendMessage(Text.literal("背包空间不足，没有扣款。"),true);return false;}
        for(int i=0;i<36;i++)p.getInventory().setStack(i,temp.get(i));p.currentScreenHandler.syncState();p.sendMessage(Text.literal("购买 "+o.name+" ×"+o.count+"，花费 "+price(o)),true);return true;
    }
    public static void open(ServerPlayerEntity p,VillagerEntity npc){if(!BedWars.shopAllowed(p,npc))return;p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,player)->new Menu(id,inv,npc),Text.literal("商店")));}
    public static final class Menu extends GenericContainerScreenHandler {
        private final UUID owner,npc;private final String round;private int lastBuy=Integer.MIN_VALUE;private String category="";private final Map<Integer,Offer> slots=new HashMap<>();
        public Menu(int id,PlayerInventory inv,VillagerEntity npc){super(ScreenHandlerType.GENERIC_9X6,id,inv,new SimpleInventory(54),6);owner=inv.player.getUuid();this.npc=npc.getUuid();round=BedWars.roundId();show((ServerPlayerEntity)inv.player,"");}
        private void show(ServerPlayerEntity p,String category){this.category=category;getInventory().clear();slots.clear();for(int i=0;i<CATEGORIES.length;i++){var icon=new ItemStack(ICONS[i]);icon.set(DataComponentTypes.CUSTOM_NAME,Text.literal(LABELS[i]));getInventory().setStack(i,icon);}
            int slot=18;for(var o:config.offers)if(o.category.equals(category)){var icon=reward(p,o);icon.set(DataComponentTypes.LORE,new LoreComponent(List.of(Text.literal(price(o)),Text.literal("左键购买"))));getInventory().setStack(slot,icon);slots.put(slot++,o);}
            var exit=new ItemStack(Items.BUCKET);exit.set(DataComponentTypes.CUSTOM_NAME,Text.literal("关闭商店"));getInventory().setStack(49,exit);sendContentUpdates();
        }
        @Override public boolean canUse(PlayerEntity p){return p instanceof ServerPlayerEntity sp&&p.getUuid().equals(owner)&&round.equals(BedWars.roundId())&&sp.getServerWorld().getEntity(npc) instanceof VillagerEntity v&&BedWars.shopAllowed(sp,v);}
        @Override public ItemStack quickMove(PlayerEntity p,int slot){return ItemStack.EMPTY;}
        @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity player){if(!(player instanceof ServerPlayerEntity p)||p.currentScreenHandler!=this||!canUse(p)||button!=0||action!=SlotActionType.PICKUP)return;
            if(slot>=0&&slot<CATEGORIES.length){show(p,CATEGORIES[slot]);return;}if(slot==49){p.closeHandledScreen();return;}
            var offer=slots.get(slot);if(offer!=null&&lastBuy!=p.getServer().getTicks()){lastBuy=p.getServer().getTicks();buy(p,offer);}
        }
    }
}
