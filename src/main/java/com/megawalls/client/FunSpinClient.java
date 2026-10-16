package com.megawalls.client;

import com.megawalls.fun.FunSpinPayload;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

/** Observer rendering only: no aim, position, input or collision changes survive the frame. */
public final class FunSpinClient {
    private record Pose(int entityId,float yaw,float pitch,long received){}
    private record Before(float yaw,float pitch,float prevYaw,float prevPitch,float body,float prevBody,float head,float prevHead){}
    private static final Map<UUID,Pose> POSES=new HashMap<>();
    private static final Map<PlayerEntity,Before> BEFORE=new IdentityHashMap<>();
    private FunSpinClient(){}
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(FunSpinPayload.ID,(pose,context)->context.client().execute(()->{
            var world=context.client().world;
            if(!pose.enabled()){POSES.remove(pose.entityUuid());return;}
            if(world==null||!Float.isFinite(pose.yaw())||!Float.isFinite(pose.pitch()))return;
            var entity=world.getEntityById(pose.entityId());
            if(!(entity instanceof PlayerEntity)||entity==context.client().player||!entity.getUuid().equals(pose.entityUuid()))return;
            POSES.put(pose.entityUuid(),new Pose(pose.entityId(),pose.yaw(),pose.pitch(),world.getTime()));
        }));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
        WorldRenderEvents.START.register(context->{
            restore();var client=MinecraftClient.getInstance();var world=client.world;if(world==null)return;
            POSES.entrySet().removeIf(entry->world.getTime()-entry.getValue().received()>10);
            for(var entry:POSES.entrySet()){
                var pose=entry.getValue();var entity=world.getEntityById(pose.entityId());
                if(!(entity instanceof PlayerEntity player)||player==client.player||!player.getUuid().equals(entry.getKey()))continue;
                BEFORE.put(player,new Before(player.getYaw(),player.getPitch(),player.prevYaw,player.prevPitch,player.bodyYaw,player.prevBodyYaw,player.headYaw,player.prevHeadYaw));
                player.setYaw(pose.yaw());player.setPitch(pose.pitch());player.prevYaw=pose.yaw();player.prevPitch=pose.pitch();
                player.setBodyYaw(pose.yaw());player.prevBodyYaw=pose.yaw();player.setHeadYaw(pose.yaw());player.prevHeadYaw=pose.yaw();
            }
        });
        WorldRenderEvents.END.register(context->restore());
    }
    private static void restore(){
        for(var entry:BEFORE.entrySet()){
            var player=entry.getKey();var old=entry.getValue();player.setYaw(old.yaw());player.setPitch(old.pitch());player.prevYaw=old.prevYaw();player.prevPitch=old.prevPitch();
            player.setBodyYaw(old.body());player.prevBodyYaw=old.prevBody();player.setHeadYaw(old.head());player.prevHeadYaw=old.prevHead();
        }
        BEFORE.clear();
    }
    private static void clear(){restore();POSES.clear();}
}
