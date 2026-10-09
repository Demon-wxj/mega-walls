package com.megawalls.menu;

import com.megawalls.MWMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.function.MacroException;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Optional entry hooks use loaded data-pack functions, never arbitrary command strings. */
public final class EntryFunctions {
    private EntryFunctions() {}
    private record Invocation(String entry,int tick) {}
    private static final Map<UUID,Invocation> LAST_RUN = new HashMap<>();

    public static boolean supported(String id) { return "sao_story".equals(id)||"flip7".equals(id)||"clocktower".equals(id); }
    public static boolean bound(String function) { return function!=null&&!function.isBlank(); }
    public static boolean valid(String entry,String function) {
        return !bound(function)||(supported(entry)&&function.length()<=128&&Identifier.tryParse(function)!=null);
    }
    public static void clear() { LAST_RUN.clear(); }
    public static void forget(UUID player) { LAST_RUN.remove(player); }

    /** Preflight before switching a saved profile, giving items or teleporting. */
    public static boolean ready(ServerPlayerEntity player,String entry,String function) {
        if(!valid(entry,function))return deny(player,"进入触发设置无效，请联系管理员");
        var server=player.getServer();
        var loaded=server.getCommandFunctionManager().getFunction(Identifier.of(function));
        if(loaded.isEmpty())return deny(player,"进入触发尚未加载，请管理员检查数据包");
        try {
            loaded.get().withMacroReplaced(null,server.getCommandManager().getDispatcher());
        } catch(MacroException e) {
            return deny(player,"进入触发需要额外参数，请管理员改为无参数函数");
        } catch(RuntimeException e) {
            MWMod.LOGGER.error("Cannot prepare entry function {} for {}",function,entry,e);
            return deny(player,"进入触发暂不可用，请联系管理员");
        }
        var last=LAST_RUN.get(player.getUuid());
        if(last!=null&&last.entry().equals(entry)&&server.getTicks()-last.tick()<20)return deny(player,"请稍候再进入");
        return true;
    }

    /** Call only after successful entry. The fresh player source contains the destination world/position. */
    public static void run(ServerPlayerEntity player,String entry,String function) {
        if(!bound(function))return;
        LAST_RUN.put(player.getUuid(),new Invocation(entry,player.getServer().getTicks()));
        try {
            player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(2).withSilent(),"function "+function);
        } catch(RuntimeException e) {
            // Entry has already completed. Do not roll back/retry a function with possible partial side effects.
            MWMod.LOGGER.error("Entry function {} failed after entering {}",function,entry,e);
            player.sendMessage(Text.literal("已进入场地，触发执行异常；请联系管理员"),true);
        }
    }
    private static boolean deny(ServerPlayerEntity player,String message) { player.sendMessage(Text.literal(message),true);return false; }
}
