package com.megawalls.mixin;
import com.megawalls.minigame.LobbyItems;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Protect required game items even if an older client sends REI's original packet. */
@Pseudo
@Mixin(targets="me.shedaniel.rei.RoughlyEnoughItemsNetwork",remap=false)
public abstract class MWReiDeleteGuardMixin {
    @Redirect(method="lambda$onInitialize$0",remap=false,at=@At(value="INVOKE",
            target="Lnet/minecraft/screen/ScreenHandler;setCursorStack(Lnet/minecraft/item/ItemStack;)V",remap=true))
    private static void mw$keepBoundCursor(ScreenHandler screen,ItemStack replacement){
        if(!LobbyItems.isBound(screen.getCursorStack()))screen.setCursorStack(replacement);
        else screen.syncState();
    }
}
