package com.reclizer.csgobox.v1_21_1.create;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * v2.2.0: zero-compile-dependency access to Create's platform item storage.
 *
 * <p>Create keeps items riding a belt / resting on a depot in its own
 * {@code TransportedItemStackHandlerBehaviour} (not a standard container), so
 * this class talks to it purely through reflection against stable public API:
 *
 * <ul>
 *   <li>{@code BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE)}</li>
 *   <li>{@code behaviour.handleCenteredProcessingOnAllItems(float, Function<TransportedItemStack, TransportedResult>)}</li>
 *   <li>{@code TransportedResult.removeItem()} / {@code convertTo(new TransportedItemStack(stack))}</li>
 * </ul>
 *
 * <p>Both Create 0.5.x (MC 1.20.1) and 0.6.x (MC 1.21.1) expose this exact
 * API, and every failure degrades to a silent no-op — without Create installed
 * the classes never load and nothing happens.</p>
 */
final class CreatePlatformItems {

    /** What the caller wants done with the platform item. */
    enum Action { NONE, REMOVE, REPLACE }

    static final class ProcessResult {
        final Action action;
        final ItemStack replacement;

        private ProcessResult(Action action, ItemStack replacement) {
            this.action = action;
            this.replacement = replacement;
        }

        static ProcessResult none() {
            return new ProcessResult(Action.NONE, ItemStack.EMPTY);
        }

        static ProcessResult remove() {
            return new ProcessResult(Action.REMOVE, ItemStack.EMPTY);
        }

        static ProcessResult replace(ItemStack replacement) {
            return new ProcessResult(Action.REPLACE, replacement == null ? ItemStack.EMPTY : replacement.copy());
        }
    }

    private static final String TISHB =
            "com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour";
    private static final String TISHB_RESULT = TISHB + "$TransportedResult";
    private static final String TI_STACK =
            "com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack";
    private static final String BE_BEHAVIOUR =
            "com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour";

    private static boolean resolved = false;
    private static boolean available = false;
    private static boolean resolveWarned = false;
    private static Object behaviourType;
    private static Method methodGet;
    private static Method methodHandle;
    private static Method methodRemoveItem;
    private static Method methodConvertTo;
    private static Constructor<?> ctorTransportedItemStack;
    private static Field fieldStack;

    private CreatePlatformItems() {
    }

    /**
     * Applies {@code handler} to the first centered platform item and executes
     * the requested action (remove / replace / none). Returns true when the
     * item was actually consumed or replaced.
     */
    static boolean process(LevelAccessor level, BlockPos pos,
                           Function<ItemStack, ProcessResult> handler) {
        if (!resolve()) {
            return false;
        }
        try {
            Object behaviour = methodGet.invoke(null, level, pos, behaviourType);
            if (behaviour == null) {
                com.reclizer.csgobox.v1_21_1.CsgoBox.LOGGER.debug(
                        "[csgobox-create] no TransportedItemStackHandlerBehaviour at {} — belt/depot has no riding item",
                        pos);
                return false;
            }
            AtomicBoolean changed = new AtomicBoolean(false);
            Function<Object, Object> fn = t -> {
                try {
                    ItemStack stack = (ItemStack) fieldStack.get(t);
                    ProcessResult result = handler.apply(stack);
                    if (result == null || result.action == Action.NONE) {
                        return null; // TransportedResult.doNothing()
                    }
                    if (result.action == Action.REMOVE) {
                        changed.set(true);
                        return methodRemoveItem.invoke(null);
                    }
                    Object transported = ctorTransportedItemStack.newInstance(result.replacement);
                    changed.set(true);
                    return methodConvertTo.invoke(null, transported);
                } catch (Throwable ex) {
                    return null;
                }
            };
            // 0.75 blocks around the platform center covers a full belt segment
            // and the depot's stationary item (segment spacing is 1 block).
            methodHandle.invoke(behaviour, 0.75f, fn);
            return changed.get();
        } catch (Throwable ex) {
            return false;
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return available;
        }
        resolved = true;
        try {
            Class<?> tishb = Class.forName(TISHB);
            Class<?> result = Class.forName(TISHB_RESULT);
            Class<?> tiStack = Class.forName(TI_STACK);
            Class<?> beBehaviour = Class.forName(BE_BEHAVIOUR);

            behaviourType = tishb.getField("TYPE").get(null);
            methodGet = beBehaviour.getMethod("get",
                    net.minecraft.world.level.BlockGetter.class,
                    BlockPos.class,
                    behaviourType.getClass());
            methodHandle = tishb.getMethod("handleCenteredProcessingOnAllItems",
                    float.class, Function.class);
            methodRemoveItem = result.getMethod("removeItem");
            methodConvertTo = result.getMethod("convertTo", tiStack);
            ctorTransportedItemStack = tiStack.getConstructor(ItemStack.class);
            fieldStack = tiStack.getField("stack");
            available = true;
        } catch (Throwable ex) {
            // Create not installed, or its API changed — degrade silently, but
            // surface the reason once so deployer-box-opening issues are
            // diagnosable instead of a black box.
            if (!resolveWarned) {
                resolveWarned = true;
                com.reclizer.csgobox.v1_21_1.CsgoBox.LOGGER.warn(
                        "[csgobox-create] Create platform API resolve failed — mechanical deployer box opening disabled",
                        ex);
            }
            available = false;
        }
        return available;
    }
}