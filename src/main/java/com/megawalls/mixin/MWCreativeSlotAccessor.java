package com.megawalls.mixin;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen$CreativeSlot")
public interface MWCreativeSlotAccessor {
    @Accessor("slot") Slot mw$originalSlot();
}
