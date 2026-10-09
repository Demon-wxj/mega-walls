package com.megawalls.rules;

import com.google.gson.Gson;
import com.megawalls.menu.EntryFunctions;
import com.megawalls.menu.HubMenu.Destination;

/** Pure saved-config compatibility checks; never accesses a server, world or user save. */
public final class EntryFunctionChecks {
    private static int checks;
    private static void check(boolean result,String message) {
        if(!result)throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) {
        for(String id:new String[]{"sao_story","flip7","clocktower"}) {
            check(EntryFunctions.supported(id),"supported entry "+id);
            check(EntryFunctions.valid(id,"story:entry/start"),"named loaded-function ID "+id);
        }
        check(!EntryFunctions.supported("megawalls"),"unrelated modes cannot bind entry hooks");
        check(!EntryFunctions.valid("megawalls","story:start"),"edited JSON cannot attach hooks to unrelated modes");
        check(EntryFunctions.valid("megawalls",null)&&EntryFunctions.valid("hub"," "),"old and blank hooks remain disabled");
        for(String function:new String[]{"#story:group","/story:start","story:start\nsay injected","story:start with entity @s","Story:start","story:start;kill @s"})
            check(!EntryFunctions.valid("sao_story",function),"reject tag/command/invalid identifier: "+function);
        check(EntryFunctions.valid("sao_story","a:"+"x".repeat(126)),"128-character identifier limit");
        check(!EntryFunctions.valid("sao_story","a:"+"x".repeat(127)),"reject oversized identifier");
        String legacy="{\"id\":\"clocktower\",\"name\":\"染钟楼谜团\",\"icon\":\"tabletop:clock_box\",\"dimension\":\"minecraft:the_nether\",\"x\":12.5,\"y\":78,\"z\":-9.5,\"yaw\":30,\"pitch\":-10,\"configured\":true}";
        var json=new Gson();var old=json.fromJson(legacy,Destination.class);
        check(old.onEnter()==null&&old.configured(),"legacy menu remains configured without a trigger");
        var bound=old.withTrigger("story:entry/start");var loaded=json.fromJson(json.toJson(bound),Destination.class);
        check(loaded.dimension().equals(old.dimension())&&loaded.x()==old.x()&&loaded.y()==old.y()&&loaded.z()==old.z()&&loaded.yaw()==old.yaw()&&loaded.pitch()==old.pitch(),"binding preserves cross-dimensional destination and view");
        check("story:entry/start".equals(loaded.onEnter()),"function persists with the existing menu record");
        check(loaded.withTrigger(null).onEnter()==null&&loaded.withTrigger(null).configured(),"clearing preserves the entry location");
        var compatibility=new Destination("flip7","七连翻","tabletop:flip_deck","minecraft:overworld",0,0,0,0,0,false);
        check(compatibility.onEnter()==null,"existing fixture constructor remains compatible");
        System.out.println("ENTRY_FUNCTION_CHECKS_OK "+checks);
    }
}
