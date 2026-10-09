package com.megawalls.mixin;

import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** AAA 2.3.0 queries the bind target instead of its binding while loading an effect.
 * Correct only that query; leave the subsequent glBindBuffer target untouched.
 * The dependency is pinned and this mixin is registered only on the client.
 */
@Mixin(targets = "mod.chloeprime.aaaparticles.client.render.RenderUtil", remap = false)
public abstract class MWAAARenderStateMixin {
    @ModifyArg(method = "lambda$runEffekLoadCodeHealthily$0(Ljava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL41C;glGetInteger(I)I", ordinal = 1),
            index = 0, require = 1, remap = false)
    private static int mw$restoreActualVertexBufferBinding(int parameter) {
        return parameter == GL15.GL_ARRAY_BUFFER ? GL15.GL_ARRAY_BUFFER_BINDING : parameter;
    }
}
