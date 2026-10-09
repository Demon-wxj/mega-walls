package com.megawalls.client;

import com.megawalls.MWMod;
import net.minecraft.client.render.VertexConsumerProvider;

import java.lang.reflect.Method;

/** Optional Iris batching bridge; vanilla clients and dedicated servers do not require Iris. */
final class EnderRiftRenderOrder {
    private static Class<?> groupable;
    private static Method maybeStart, end;
    private static boolean warned, announced;

    static {
        try {
            // Resolve methods on the public interface, not Iris's package-private wrapper class.
            groupable = Class.forName("net.irisshaders.batchedentityrendering.impl.Groupable");
            maybeStart = groupable.getMethod("maybeStartGroup");
            end = groupable.getMethod("endGroup");
        } catch (ClassNotFoundException ignored) {
            // Normal without Iris: vanilla Immediate preserves submission order.
        } catch (ReflectiveOperationException | LinkageError failure) {
            groupable = null; maybeStart = null; end = null;
            warn(failure);
        }
    }

    private EnderRiftRenderOrder() {}

    static boolean begin(VertexConsumerProvider source) {
        if (maybeStart == null || !groupable.isInstance(source)) return false;
        try {
            boolean started = (boolean)maybeStart.invoke(source);
            if (started && !announced) {
                MWMod.LOGGER.info("Ender rift Iris material ordering active (one group per endpoint).");
                announced = true;
            }
            return started;
        } catch (ReflectiveOperationException failure) {
            warn(failure);
            return false;
        }
    }

    static void finish(VertexConsumerProvider source, boolean started) {
        if (!started) return; // Never close a group owned by another renderer.
        try { end.invoke(source); }
        catch (ReflectiveOperationException failure) { warn(failure); }
    }

    private static void warn(Throwable failure) {
        if (!warned) {
            MWMod.LOGGER.warn("Ender rift could not establish Iris material ordering; check Iris compatibility.", failure);
            warned = true;
        }
    }
}
