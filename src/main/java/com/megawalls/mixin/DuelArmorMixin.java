package com.megawalls.mixin;
import com.megawalls.minigame.ClassicDuel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.tag.DamageTypeTags;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntity.class)
public abstract class DuelArmorMixin {
    @Shadow protected abstract void damageArmor(DamageSource source,float damage);
    @Inject(method="blockedByShield",at=@At("HEAD"),cancellable=true)
    private void duel$notShield(DamageSource source,CallbackInfoReturnable<Boolean> cir){if((Object)this instanceof ServerPlayerEntity p&&(ClassicDuel.fighter(p)||com.megawalls.minigame.PotDuel.fighter(p))&&p.getActiveItem().getItem() instanceof SwordItem)cir.setReturnValue(false);}
    @Inject(method="applyArmorToDamage",at=@At("HEAD"),cancellable=true)
    private void duel$armor(DamageSource source,float damage,CallbackInfoReturnable<Float> cir){
        if(!((Object)this instanceof ServerPlayerEntity p)||!(ClassicDuel.fighter(p)||com.megawalls.minigame.PotDuel.fighter(p)))return;
        boolean armored=!source.isIn(DamageTypeTags.BYPASSES_ARMOR)||source.isOf(DamageTypes.LAVA)||source.isOf(DamageTypes.IN_FIRE);
        if(armored){if(damage>0&&p.isUsingItem()&&p.getActiveItem().getItem() instanceof SwordItem)damage=(damage+1)*.5f;damageArmor(source,damage);damage*=1-Math.min(20,p.getArmor())*.04f;}
        cir.setReturnValue(damage);
    }
}
