package com.megawalls.client;
import com.megawalls.player.MWPlayerData;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import java.util.UUID;
public final class ClassSkinTextures {
    public static Identifier texture(int id){return Identifier.of("megawalls","textures/skin/"+(id==2?"hoops":"enderman")+".png");}
    public static SkinTextures replace(UUID player,SkinTextures original){
        var data=MWPlayerData.getClient(player);
        if(data==null||data.getClassId()!=5||data.getSkinId()==0)return original;
        return new SkinTextures(texture(data.getSkinId()),null,null,null,SkinTextures.Model.WIDE,true);
    }
}
