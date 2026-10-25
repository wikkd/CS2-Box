package com.reclizer.csgobox.fabric_1_21_1.event;

import com.reclizer.csgobox.fabric_1_21_1.compat.TaczInspectViewport;
import com.reclizer.csgobox.fabric_1_21_1.gui.CsLookItemScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Drives the opt-in "first-person inspect" flow for {@link CsLookItemScreen}
 * (Fabric edition).
 *
 * <p>The gloves toolbar button asks TACZ to play its real native first-person
 * inspect animation: we inject the drawn gun into the player's main hand
 * (client-side render only), the look screen closes, TACZ's own first-person
 * renderer plays the inspect animation, and once the animation finishes this
 * handler restores the player's previous hand and reopens the look screen.</p>
 *
 * <p><b>Fabric note:</b> TACZ here is TaCZ: Refabricated (optional); without
 * it the facade reports unavailable and sessions never start. The NeoForge
 * handler's {@code RenderGuiEvent.Pre} HUD cancellation during the inspect
 * has no cancel-style Fabric equivalent (fabric-api only offers
 * post-render {@code HudRenderCallback}), so the HUD stays visible behind
 * the transparent {@code FirstPersonInspectScreen} for the duration — a
 * known minor visual difference vs v1_21_1, not worth a mixin.</p>
 */
public final class FirstPersonInspectHandler {
    /** Consecutive ticks with no inspect animation before we consider it done. */
    private static final int DONE_STREAK = 25;
    /** Never finish before this many ticks have elapsed. */
    private static final int MIN_ELAPSED = 40;
    /** Safety timeout: force-return even if completion was never detected. */
    private static final int MAX_TICKS = 220;
    /** Max ticks to wait for TACZ's client state lock to release after the draw. */
    private static final int MAX_WAIT_READY = 80;

    private static Session pending;

    private static final class Session {
        ItemStack item;
        ItemStack restoreHand;
        int grade;
        /** Screen to restore on finish; {@code null} in the box flow. */
        Screen returnScreen;
        int ticks;
        int doneStreak;
        boolean inspectTriggered;
    }

    private FirstPersonInspectHandler() {
    }

    /** Begin a first-person inspect session from the box look screen. */
    public static boolean start(LocalPlayer player, ItemStack item, int grade) {
        return start(player, item, grade, null);
    }

    /**
     * Begin a first-person inspect session, restoring {@code returnScreen}
     * when the animation finishes instead of reopening the box look screen.
     * Returns false if it could not start (caller keeps the GUI).
     */
    public static boolean start(LocalPlayer player, ItemStack item, int grade, Screen returnScreen) {
        if (player == null || item == null || item.isEmpty() || pending != null) {
            return false;
        }
        Session s = new Session();
        s.item = item.copy();
        s.grade = grade;
        s.returnScreen = returnScreen;
        s.restoreHand = player.getMainHandItem().copy();
        if (!TaczInspectViewport.startFirstPersonInspect(s.item, player)) {
            return false;
        }
        pending = s;
        Minecraft mc = Minecraft.getInstance();
        // Lock input behind a transparent full-screen that only lets Esc exit.
        mc.setScreen(new com.reclizer.csgobox.fabric_1_21_1.gui.FirstPersonInspectScreen());
        return true;
    }

    /** Returns true while a first-person inspect session is in flight. */
    public static boolean isActive() {
        return pending != null;
    }

    /** Ends the session immediately (Esc handler / lock-screen close). No-op when idle. */
    public static void requestFinish() {
        Minecraft mc = Minecraft.getInstance();
        if (pending != null) {
            finish(mc);
        }
    }

    /** Client tick driver (registered from {@code CsgoBoxClient}). */
    public static void onClientTick() {
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

    private static void finish(Minecraft mc) {
        Session s = pending;
        pending = null;
        LocalPlayer player = mc.player;
        if (player != null) {
            TaczInspectViewport.endFirstPersonInspect(s.item, player, s.restoreHand);
        }
        if (s.returnScreen != null) {
            mc.setScreen(s.returnScreen);
        } else {
            CsLookItemScreen.openQuietly(s.item, s.grade, mc);
        }
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
