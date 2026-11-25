package com.reclizer.csgobox.v1_21_1.utils;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * SlashBlade (拔刀剑: Resharped) combo-motion inspect: the look screen's
 * preview player wields the blade and plays the blade's own attack chain.
 * Everything that moves is the mod's own code — this class only performs
 * the clicks.
 *
 * <p>How the mod animates a blade (javap of the 2.1.9-1.21.1 jar): the
 * {@code LayerSlashBlade} render layer it adds to every player renderer
 * reads the <em>rendered entity's</em> main-hand {@code ISlashBladeState}
 * through {@code BladeStateAccess.of(stack)} ({@code peekCurrentComboStateTicks}
 * off {@code comboSeq} + {@code lastActionTime}), seeks the mod's MMD
 * motion to that instant and shows the combo root as the rest pose; a
 * {@code BladeMotionEvent} on the NeoForge bus is what its playerAnimator
 * handler consumes to layer the body motion on top. Neither path looks at
 * the real player, so a parked {@code AbstractClientPlayer} holding a copy
 * of the blade animates through the very same code the game runs.</p>
 *
 * <p>The chain follows the mod's own selectors: the first swing is the
 * combo root's {@code ComboState.getNext(entity)} (the stock root resolves
 * to {@code ComboCommands.initStandByCommand}), every later link is the
 * current state's {@code getNext}, and the round ends when that returns
 * {@code none}. Each of those evaluations is one click — the mod's
 * selectors read the entity's live input commands, so the {@code L_CLICK}
 * command is held on the preview player for exactly that call (see
 * {@link #click}). The mod's own click entry point
 * ({@code ISlashBladeState.progressCombo} / {@code updateComboSeq}) is
 * deliberately NOT used: those also run the state's {@code clickAction},
 * which for the stock combos spawns slash-effect entities and area
 * attacks. The state setters plus the motion event give the same picture
 * without any of the gameplay side effects.</p>
 *
 * <p>1.21.1 surface differences from the 1.20.1 file: the blade state is a
 * data component ({@code BladeStateAccess.of(stack)} returning an
 * {@code Optional}), the input commands live in an entity attachment
 * ({@code CapabilityInputState.INPUT_STATE} + {@code LivingEntity.getData}),
 * and the combo registry is a plain {@code Registry} looked up with
 * {@code get(ResourceLocation)} / {@code getKey(value)}. Everything else —
 * combo ids, timeout chain, motion event, zero compile-time SlashBlade
 * references — is the same.</p>
 *
 * <p>Body motion needs the playerAnimator library, which the mod loads
 * when present (its own handler registers only then). Without it the
 * figure keeps its rest pose and only the blade moves — the same look the
 * mod has in game on such an install. With it, the preview player is not
 * part of the world and never ticks, so the animation layer is ticked
 * here once per screen tick ({@link #tick}). Everything is reflective:
 * zero SlashBlade / playerAnimator classes at compile time, and any
 * unreachable surface simply keeps the rack preview.</p>
 */
public final class SlashBladeComboInspect {

    private SlashBladeComboInspect() {
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Rest between combo rounds, in ms: the carry pose has to be
     *  readable before the next swing starts. */
    private static final long IDLE_MS = 650L;

    /** Where in a segment's airtime the next click lands. The mod's click
     *  gates ({@code ComboState$TimeoutNext}) open a few frames into a
     *  segment, and its render layer hands a segment over to its own
     *  timeout successor once {@code getTimeoutMS()} has passed — this
     *  point sits between the two. */
    private static final double HANDOVER = 0.8D;

    /** Bounds for one segment's airtime when a state declares none (or
     *  an endless one, e.g. an aerial loop). */
    private static final int MIN_SEGMENT_MS = 200;
    private static final int MAX_SEGMENT_MS = 2500;

    // ---- SlashBlade surface (resolved once; null/unavailable on failure) ----

    private static boolean probed;
    private static boolean available;
    private static Method bladeStateOf;
    private static Method setComboSeq;
    private static Method setLastActionTime;
    private static Method getComboRoot;
    private static Object inputAttachment;
    private static Method entityGetData;
    private static Method inputCommands;
    private static Object clickCommand;
    private static Object comboRegistry;
    private static Method registryGet;
    private static Method registryGetKey;
    private static Method comboNext;
    private static Method comboTimeout;
    private static ResourceLocation noneId;
    private static Object standbyCombo;
    private static Constructor<?> motionEventCtor;

    // ---- playerAnimator surface (optional; resolved lazily) ----

    private static boolean bodyResolved;
    private static boolean bodyAvailable;
    private static boolean bodyFailed;
    private static Method bodyLayerOf;
    private static Method bodyTick;
    private static Method bodyRemoveLayer;

    // ---- playback (render thread only) ----

    private static boolean active;
    /** Set when a playback step threw: further starts are refused (the
     *  button stays a pose flip) until the game restarts. */
    private static boolean broken;
    private static LivingEntity carrier;
    private static Object state;
    private static ItemStack installedSource;
    private static ResourceLocation currentId;
    private static boolean resting;
    private static long phaseStartMs;
    private static double phaseMs;

    /** One-shot INFO guard per combo id (diagnosis aid for the first
     *  in-game check). */
    private static final Set<String> SEGMENT_LOG = new HashSet<>();

    /** True while the look screen plays the blade's own combo motion. */
    public static boolean isActive() {
        return active;
    }

    /** Switches the preview to the combo motion. False (and nothing
     *  changed) when SlashBlade's surface is unreachable — the caller then
     *  keeps the rack preview. The blade is handed to the preview player by
     *  the first {@link #drive}. */
    public static boolean start() {
        if (broken || !ensureProbe()) {
            return false;
        }
        stop();
        active = true;
        return true;
    }

    /** Ends the combo motion: the blade returns to its rest state and the
     *  preview player is left empty-handed. */
    public static void stop() {
        if (!active) {
            return;
        }
        active = false;
        if (state != null) {
            try {
                setComboSeq.invoke(state, noneId);
            } catch (Throwable ignored) {
                // The state is dropped right below; nothing depends on it.
            }
        }
        clearBodyLayer();
        LivingEntity stopped = carrier;
        if (stopped != null) {
            try {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    stopped.setItemSlot(slot, ItemStack.EMPTY);
                }
            } catch (Throwable ignored) {
                // Cosmetic only: the carrier is rendered by nothing else.
            }
        }
        state = null;
        installedSource = null;
        currentId = null;
    }

    /** Screen tick: advances the body-motion layer of the preview player.
     *  A no-op without playerAnimator (the blade follows game time on its
     *  own). */
    public static void tick() {
        LivingEntity current = active ? carrier : null;
        if (!(current instanceof AbstractClientPlayer player)) {
            return;
        }
        try {
            Object layer = bodyLayer(player);
            if (layer != null) {
                bodyTick.invoke(layer);
            }
        } catch (Throwable t) {
            bodyAvailable = false;
            bodyFailed = true;
            LOGGER.warn("[csgobox] slashblade combo inspect: body motion ticking failed; "
                    + "the figure keeps its rest pose", t);
        }
    }

    /** Per-frame driver, called by the combo preview render: puts a copy of
     *  {@code blade} into {@code previewCarrier}'s main hand when it is not
     *  the blade already installed, then performs the next click of the
     *  chain when the current phase is due. False when the combo motion is
     *  off, SlashBlade is unreachable or the playback broke — the caller
     *  then falls back to the rack preview. */
    static boolean drive(LivingEntity previewCarrier, ItemStack blade) {
        if (!active || !ensureProbe()) {
            return false;
        }
        try {
            if (previewCarrier != carrier || installedSource == null
                    || !ItemStack.isSameItemSameComponents(installedSource, blade)) {
                install(previewCarrier, blade);
            }
            if (state == null) {
                broken = true;
                LOGGER.warn("[csgobox] slashblade combo inspect: the blade copy carries no blade state; "
                        + "staying on the rack preview");
                stop();
                return false;
            }
            advance(previewCarrier);
            return true;
        } catch (Throwable t) {
            broken = true;
            LOGGER.warn("[csgobox] slashblade combo inspect: playback failed; "
                    + "falling back to the rack preview", t);
            stop();
            return false;
        }
    }

    /** Equips the preview player with a private copy of the blade and
     *  puts that copy's state at rest. The state is a data component on the
     *  stack ({@code BladeStateAccess.of(copy)}), so the inspected stack is
     *  never touched. The player reads as standing on the ground so the
     *  mod's selectors pick the grounded branches. */
    private static void install(LivingEntity previewCarrier, ItemStack blade) throws ReflectiveOperationException {
        clearBodyLayer();
        carrier = previewCarrier;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            carrier.setItemSlot(slot, ItemStack.EMPTY);
        }
        ItemStack copy = blade.copy();
        copy.setEntityRepresentation(null);
        carrier.setItemSlot(EquipmentSlot.MAINHAND, copy);
        carrier.setOnGround(true);
        Optional<?> resolved = (Optional<?>) bladeStateOf.invoke(null, copy);
        state = resolved == null ? null : resolved.orElse(null);
        installedSource = blade;
        currentId = null;
        rest(Util.getMillis());
    }

    /** One state-machine step: when the current phase has run its course,
     *  performs the next click — the combo root's first swing out of a
     *  rest, else the current state's successor — and returns to the rest
     *  pose when the chain ends. */
    private static void advance(LivingEntity entity) throws ReflectiveOperationException {
        long now = Util.getMillis();
        if (now - phaseStartMs < phaseMs) {
            return;
        }
        ResourceLocation next = click(entity, resting ? comboRootState() : comboStateById(currentId));
        // none, a null link, a link back to itself, or an id outside the
        // registry: the chain is over.
        if (next == null || next.equals(noneId) || next.equals(currentId)
                || comboStateById(next) == null) {
            rest(now);
            return;
        }
        long clicked = Util.getMillis();
        setComboSeq.invoke(state, next);
        setLastActionTime.invoke(state, entity.level().getGameTime());
        // The mod's own motion event: its playerAnimator handler layers the
        // body motion for this combo id (its network broadcaster only acts
        // for server players, so a client-side preview sends nothing).
        NeoForge.EVENT_BUS.post((Event) motionEventCtor.newInstance(entity, next));
        currentId = next;
        resting = false;
        phaseStartMs = clicked;
        int timeout = (Integer) comboTimeout.invoke(comboStateById(next));
        phaseMs = Math.max(MIN_SEGMENT_MS, Math.min(MAX_SEGMENT_MS, timeout)) * HANDOVER;
        if (SEGMENT_LOG.add(next.toString())) {
            LOGGER.info("[csgobox] slashblade combo inspect: segment {} (timeout {} ms, next click at {} ms)",
                    next, timeout, (int) phaseMs);
        }
    }

    /** The blade's combo root state ({@code standby} for the stock
     *  blades, a custom root for blades that declare one), or the mod's
     *  standby state when the root is not registered — the same fallback
     *  its render layer uses for the rest pose. */
    private static Object comboRootState() throws ReflectiveOperationException {
        ResourceLocation root = (ResourceLocation) getComboRoot.invoke(state);
        Object rootCombo = root == null ? null : comboStateById(root);
        return rootCombo != null ? rootCombo : standbyCombo;
    }

    /** Combo state registered under {@code id}, or null. */
    private static Object comboStateById(ResourceLocation id) throws ReflectiveOperationException {
        return id == null ? null : registryGet.invoke(comboRegistry, id);
    }

    /** Performs one click on {@code entity}: evaluates the mod's own next
     *  combo selector of {@code combo}. Those selectors read the entity's
     *  live input commands — the stock standby chooser
     *  ({@code ComboCommands.initStandByCommand}) hands out
     *  {@code combo_a1} only for {@code ON_GROUND + L_CLICK} and nothing at
     *  all for an input-free entity — so the click is the {@code L_CLICK}
     *  command, held in the entity's input state for exactly this call
     *  (ground contact is derived from the entity itself). Without it the
     *  chain would never start. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceLocation click(LivingEntity entity, Object combo) throws ReflectiveOperationException {
        Object inputState = inputAttachment == null ? null : entityGetData.invoke(entity, inputAttachment);
        EnumSet commands = inputState == null ? null : (EnumSet) inputCommands.invoke(inputState);
        if (commands != null) {
            commands.add(clickCommand);
        }
        try {
            return combo == null ? null : (ResourceLocation) comboNext.invoke(combo, entity);
        } finally {
            if (commands != null) {
                commands.remove(clickCommand);
            }
        }
    }

    /** Back to the carry pose: {@code comboSeq = none} makes the mod's
     *  layer show the combo root's motion. No motion event here — the mod
     *  posts none for a rest either. */
    private static void rest(long now) throws ReflectiveOperationException {
        currentId = noneId;
        resting = true;
        phaseStartMs = now;
        phaseMs = IDLE_MS;
        if (state != null) {
            setComboSeq.invoke(state, noneId);
        }
    }

    /** The preview player's playerAnimator animation layer, or null when
     *  the library is absent or its surface moved. */
    private static Object bodyLayer(AbstractClientPlayer player) throws ReflectiveOperationException {
        if (!bodyResolved) {
            bodyResolved = true;
            try {
                Class<?> access = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess");
                bodyLayerOf = access.getMethod("getPlayerAnimLayer", AbstractClientPlayer.class);
                bodyAvailable = true;
                LOGGER.info("[csgobox] slashblade combo inspect: playerAnimator present; body motion on");
            } catch (Throwable t) {
                bodyAvailable = false;
                LOGGER.info("[csgobox] slashblade combo inspect: playerAnimator absent; "
                        + "the figure keeps its rest pose (blade motion still plays)");
            }
        }
        if (!bodyAvailable || bodyFailed) {
            return null;
        }
        Object layer = bodyLayerOf.invoke(null, player);
        if (layer == null) {
            return null;
        }
        if (bodyTick == null) {
            bodyTick = layer.getClass().getMethod("tick");
            bodyRemoveLayer = layer.getClass().getMethod("removeLayer", int.class);
        }
        return layer;
    }

    /** Drops the body-motion layer the mod's handler installs at priority
     *  0 ({@code AnimationStack.addAnimLayer(0, ...)}); best-effort. */
    private static void clearBodyLayer() {
        LivingEntity current = carrier;
        if (!(current instanceof AbstractClientPlayer player)) {
            return;
        }
        try {
            Object layer = bodyLayer(player);
            if (layer != null && bodyRemoveLayer != null) {
                bodyRemoveLayer.invoke(layer, 0);
            }
        } catch (Throwable ignored) {
            // Nothing keeps a stale pose: an un-ticked animation layer is
            // simply never sampled once the player stops being rendered.
        }
    }

    /** One-shot reflection probe; false when SlashBlade is not installed
     *  or its surface moved (silently unavailable either way). */
    private static boolean ensureProbe() {
        if (probed) {
            return available;
        }
        probed = true;
        try {
            Class<?> accessCls = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess");
            bladeStateOf = accessCls.getMethod("of", ItemStack.class);
            Class<?> stateCls = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
            setComboSeq = stateCls.getMethod("setComboSeq", ResourceLocation.class);
            setLastActionTime = stateCls.getMethod("setLastActionTime", long.class);
            getComboRoot = stateCls.getMethod("getComboRoot");
            Class<?> inputCaps = Class.forName(
                    "mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState");
            Object inputSupplier = inputCaps.getField("INPUT_STATE").get(null);
            inputAttachment = ((Supplier<?>) inputSupplier).get();
            entityGetData = LivingEntity.class.getMethod("getData", inputAttachment.getClass());
            inputCommands = Class.forName("mods.flammpfeil.slashblade.capability.inputstate.IInputState")
                    .getMethod("getCommands");
            clickCommand = Class.forName("mods.flammpfeil.slashblade.util.InputCommand").getField("L_CLICK").get(null);
            Class<?> registryHolder = Class.forName("mods.flammpfeil.slashblade.registry.ComboStateRegistry");
            comboRegistry = registryHolder.getField("REGISTRY").get(null);
            registryGet = Class.forName("net.minecraft.core.Registry")
                    .getMethod("get", ResourceLocation.class);
            registryGetKey = Class.forName("net.minecraft.core.Registry")
                    .getMethod("getKey", Object.class);
            Class<?> comboCls = Class.forName("mods.flammpfeil.slashblade.registry.combo.ComboState");
            Method holderGet = Class.forName("net.neoforged.neoforge.registries.DeferredHolder")
                    .getMethod("get");
            Object noneCombo = holderGet.invoke(registryHolder.getField("NONE").get(null));
            standbyCombo = holderGet.invoke(registryHolder.getField("STANDBY").get(null));
            noneId = (ResourceLocation) registryGetKey.invoke(comboRegistry, noneCombo);
            comboNext = comboCls.getMethod("getNext", LivingEntity.class);
            comboTimeout = comboCls.getMethod("getTimeoutMS");
            motionEventCtor = Class.forName("mods.flammpfeil.slashblade.event.BladeMotionEvent")
                    .getConstructor(LivingEntity.class, ResourceLocation.class);
            available = bladeStateOf != null && comboRegistry != null && noneId != null && standbyCombo != null
                    && inputAttachment != null && clickCommand != null;
        } catch (Throwable t) {
            LOGGER.warn("[csgobox] slashblade combo inspect: SlashBlade surface unreachable; "
                    + "staying on the rack preview", t);
            available = false;
        }
        return available;
    }
}
