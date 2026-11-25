package com.reclizer.csgobox.forge_1_20_1.event;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.compat.TaczInspectViewport;
import com.reclizer.csgobox.forge_1_20_1.gui.CsLookItemScreen;
import com.reclizer.csgobox.forge_1_20_1.gui.FirstPersonInspectScreen;
import com.reclizer.csgobox.forge_1_20_1.utils.EpicFightInspect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the opt-in "first-person inspect" flow for {@link CsLookItemScreen}
 * on Forge 1.20.1: the inspect button closes the look screen, injects the gun
 * into the main hand (client-side) and triggers TACZ's native inspect; this
 * handler watches for the animation to finish, then restores the hand and
 * reopens the look screen. When Epic Fight's player mode is active the session
 * takes the player out of it first (EF renders the first-person hand itself,
 * so TACZ's inspect could never appear) and restores it afterwards. TACZ is
 * optional and each entry is facade-guarded.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = CsgoBox.MODID)
public final class FirstPersonInspectHandler {
    private static final int DONE_STREAK = 25;
    private static final int MIN_ELAPSED = 40;
    private static final int MAX_TICKS = 220;
    private static final int MAX_WAIT_READY = 80;

    private static Session pending;

    private static final class Session {
        ItemStack item;
        ItemStack restoreHand;
        int grade;
        /** Screen to restore on finish; {@code null} in the box flow (the
         *  look screen is then reopened). Non-null when started from the
         *  terminal offer panel so the negotiation screen comes back. */
        Screen returnScreen;
        /** The look screen's own previousScreen (right-click inspect context),
         *  handed back to {@link CsLookItemScreen#openQuietly} so the reopen
         *  keeps the inspect context (no wear button for non-shipment views). */
        Screen lookPreviousScreen;
        int ticks;
        int doneStreak;
        boolean inspectTriggered;
        /** True when the session took the player out of Epic Fight's player
         *  mode (EF renders the first-person hand itself, hiding TACZ's gun)
         *  and must put it back on finish/cancel. */
        boolean leftBattleMode;
    }

    private FirstPersonInspectHandler() {
    }

    public static boolean start(LocalPlayer player, ItemStack item, int grade) {
        return start(player, item, grade, null, null);
    }

    /**
     * Begin a first-person inspect session, restoring {@code returnScreen}
     * when the animation finishes instead of reopening the box look screen
     * (used by the terminal offer panel — the negotiation screen comes back).
     * Returns false if it could not start (caller keeps the GUI).
     */
    public static boolean start(LocalPlayer player, ItemStack item, int grade, Screen returnScreen) {
        return start(player, item, grade, returnScreen, null);
    }

    /**
     * Full form. {@code lookPreviousScreen} carries the look screen's own
     * previousScreen (right-click inspect context) through the session so the
     * reopen keeps the same toolbar (no wear button for non-shipment views).
     * Returns false if it could not start (caller keeps the GUI).
     */
    public static boolean start(LocalPlayer player, ItemStack item, int grade,
                                Screen returnScreen, Screen lookPreviousScreen) {
        if (player == null || item == null || item.isEmpty() || pending != null) {
            return false;
        }
        Session s = new Session();
        s.item = item.copy();
        s.grade = grade;
        s.returnScreen = returnScreen;
        s.lookPreviousScreen = lookPreviousScreen;
        s.restoreHand = player.getMainHandItem().copy();
        // Epic Fight's player mode renders the first-person hand itself:
        // TACZ's native draw/inspect can never appear there and the draw
        // leaves TACZ's client state lock up, so the session would sit on a
        // blank lock screen until it aborts (the 2026-11-23 report). Take
        // the player out of that mode for the session and put it back on
        // finish/cancel — EF's own mode-change path, server included.
        s.leftBattleMode = EpicFightInspect.leaveBattleMode(player);
        if (EpicFightInspect.isEpicFightMode(player)) {
            // Still in EF's player mode (another mod cancelled the change,
            // or an EF build without the mode setters): the hand renderer
            // would keep the gun invisible and trap the session. Refuse and
            // leave the caller's GUI up.
            EpicFightInspect.restoreBattleMode(player, s.leftBattleMode);
            return false;
        }
        if (!TaczInspectViewport.startFirstPersonInspect(s.item, player)) {
            EpicFightInspect.restoreBattleMode(player, s.leftBattleMode);
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

    public static boolean isActive() {
        return pending != null;
    }

    /** Ends the session immediately (Esc handler / lock-screen close). */
    public static void requestFinish() {
        Minecraft mc = Minecraft.getInstance();
        if (pending != null) {
            finish(mc);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) {
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
            return;
        }
        // The HUD-hiding screens (progress/bulk-result/look) set options.hideGui,
        // but ForgeGui's overlay path checks that flag per entry only: the
        // crosshair/hotbar entries skip while the chat panel entry renders
        // unguarded, so the chat box stayed visible behind our translucent
        // screens (2026-11-22 maintainer report). GameRenderer also renders the
        // whole GUI whenever a screen is open, which defeats the flag for every
        // screen-driven hide. Cancelling the whole GUI while a screen is up with
        // hideGui on suppresses every overlay at once; F1 without a screen keeps
        // the vanilla per-entry gating.
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui && mc.screen != null) {
            event.setCanceled(true);
        }
    }

    private static void finish(Minecraft mc) {
        Session s = pending;
        pending = null;
        LocalPlayer player = mc.player;
        if (player != null) {
            TaczInspectViewport.endFirstPersonInspect(s.item, player, s.restoreHand);
            EpicFightInspect.restoreBattleMode(player, s.leftBattleMode);
        }
        restoreUi(mc, s);
    }

    /** Puts the caller's UI back after a session. Aborting without this
     *  leaves the transparent lock screen up with the HUD suppressed and
     *  Esc dead (the session is already gone, so requestFinish is a no-op):
     *  the player cannot move, see the HUD or leave — the 2026-11-23 "TACZ
     *  inspect freezes in EF battle mode" trap. */
    private static void restoreUi(Minecraft mc, Session s) {
        if (s.returnScreen != null) {
            mc.setScreen(s.returnScreen);
        } else {
            CsLookItemScreen.openQuietly(s.item, s.grade, s.lookPreviousScreen, mc);
        }
    }

    private static void cancel(Minecraft mc) {
        Session s = pending;
        pending = null;
        LocalPlayer player = mc.player;
        boolean canRestore = player != null && player.isAlive() && !player.isRemoved();
        if (player != null) {
            TaczInspectViewport.endFirstPersonInspect(s.item, player, s.restoreHand);
            // Restore the mode even for a dead/removed player: the change
            // was sent to the server, and leaving it there would strand the
            // player in vanilla mode (the client session is over either way).
            EpicFightInspect.restoreBattleMode(player, s.leftBattleMode);
        }
        // Dead/removed players (respawn replaced the screen) must not get the
        // look screen back; everyone else does, so an aborted session never
        // strands the player on the invisible lock screen.
        if (canRestore) {
            restoreUi(mc, s);
        } else if (mc.screen instanceof FirstPersonInspectScreen) {
            mc.setScreen(null);
        }
    }
}