package com.reclizer.csgobox.v1_21_1.event;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.compat.TaczInspectViewport;
import com.reclizer.csgobox.v1_21_1.gui.CsLookItemScreen;
import com.reclizer.csgobox.v1_21_1.gui.FirstPersonInspectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Drives the opt-in "first-person inspect" flow for {@link CsLookItemScreen}.
 *
 * <p>The gloves toolbar button no longer renders a 3D viewport inside the
 * look screen; instead it asks TACZ to play its real native first-person
 * inspect animation: we inject the drawn gun into the player's main hand
 * (client-side render only), the look screen closes, TACZ's own first-person
 * renderer plays the inspect animation, and once the animation finishes this
 * handler restores the player's previous hand and reopens the look screen.
 *
 * <p>TACZ is optional; every entry point is guarded by the facade, so in a
 * world without TACZ this handler never becomes active.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = CsgoBox.MODID)
public final class FirstPersonInspectHandler {
    /** Consecutive ticks with no inspect animation before we consider it done
     *  (the inspect state chains several sub-animations; allow ~1s of quiet so
     *  short gaps between segments are never mistaken for "finished"). */
    private static final int DONE_STREAK = 25;
    /** Never finish before this many ticks have elapsed, so a brief moment
     *  before the inspect animation kicks in can never cause an early return. */
    private static final int MIN_ELAPSED = 40;
    /** Safety timeout: force-return even if completion was never detected. */
    private static final int MAX_TICKS = 220;
    /** Max ticks to wait for TACZ's client state lock to release after the
     *  draw; if it never does, abort the session. */
    private static final int MAX_WAIT_READY = 80;

    private static Session pending;

    private static final class Session {
        ItemStack item;
        ItemStack restoreHand;
        int grade;
        int ticks;
        int doneStreak;
        boolean inspectTriggered;
    }

    private FirstPersonInspectHandler() {
    }

    /**
     * Begin a first-person inspect session. Captures the player's current main
     * hand for later restore, injects the gun and starts TACZ's native draw
     * (the inspect input fires once the draw releases the state lock).
     * Returns false if it could not start (caller keeps the GUI).
     */
    public static boolean start(LocalPlayer player, ItemStack item, int grade) {
        if (player == null || item == null || item.isEmpty() || pending != null) {
            return false;
        }
        Session s = new Session();
        s.item = item.copy();
        s.grade = grade;
        s.restoreHand = player.getMainHandItem().copy();
        if (!TaczInspectViewport.startFirstPersonInspect(s.item, player)) {
            return false;
        }
        pending = s;
        Minecraft mc = Minecraft.getInstance();
        // Lock input behind a transparent full-screen that only lets Esc exit
        // (vanilla swallows all input while a screen is up). The HUD is hidden
        // via RenderGuiEvent.Pre cancellation (NOT options.hideGui, which would
        // also disable the first-person hand/gun rendering).
        mc.setScreen(new FirstPersonInspectScreen());
        return true;
    }

    /** Returns true while a first-person inspect session is in flight. */
    public static boolean isActive() {
        return pending != null;
    }

    /** Ends the session immediately (Esc handler / lock-screen close): restore
     *  the hand and reopen the look screen. No-op when idle. */
    public static void requestFinish() {
        Minecraft mc = Minecraft.getInstance();
        if (pending != null) {
            finish(mc);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (pending == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isAlive() || player.isRemoved()) {
            cancel(mc);
            return;
        }
        Session s = pending;
        s.ticks++;
        // Phase 1: wait for the draw to release TACZ's state lock, then fire
        // the genuine native inspect input exactly once.
        if (!s.inspectTriggered) {
            if (TaczInspectViewport.isReadyForInspect(player)) {
                TaczInspectViewport.retryFirstPersonInspect(player);
                s.inspectTriggered = true;
                s.ticks = 0; // completion timing starts from the inspect trigger
            } else if (s.ticks >= MAX_WAIT_READY) {
                cancel(mc);
            }
            return;
        }
        // Phase 2: wait for the inspect animation to finish, then return UI.
        boolean playing = TaczInspectViewport.isInspectPlaying(s.item, player);
        s.doneStreak = playing ? 0 : s.doneStreak + 1;
        if (s.ticks >= MIN_ELAPSED && (s.doneStreak >= DONE_STREAK || s.ticks >= MAX_TICKS)) {
            finish(mc);
        }
    }

    /** Hides the HUD while a first-person inspect session is active. This is
     *  used instead of {@code options.hideGui} because hideGui also disables
     *  the first-person hand/gun render in GameRenderer#renderItemInHand. */
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (pending != null) {
            event.setCanceled(true);
        }
    }

    private static void finish(Minecraft mc) {
        Session s = pending;
        pending = null;
        LocalPlayer player = mc.player;
        if (player != null) {
            TaczInspectViewport.endFirstPersonInspect(s.item, player, s.restoreHand);
        }
        CsLookItemScreen.openQuietly(s.item, s.grade, mc);
    }

    private static void cancel(Minecraft mc) {
        Session s = pending;
        pending = null;
        LocalPlayer player = mc.player;
        if (player != null) {
            TaczInspectViewport.endFirstPersonInspect(s.item, player, s.restoreHand);
        }
    }
}