package com.megawalls.mixin;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(HandledScreen.class)
public interface MWHandledCursorAccessor {
    @Accessor("cursorDragging") void mw$cursorDragging(boolean dragging);
}
