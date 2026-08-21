package com.reclizer.csgobox.forge_1_20_1.event;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.compat.TaczInspectViewport;
import com.reclizer.csgobox.forge_1_20_1.gui.CsLookItemScreen;
import com.reclizer.csgobox.forge_1_20_1.gui.FirstPersonInspectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the opt-in "first-person inspect" flow for {@link CsLookItemScreen}
 * on Forge 1.20.1: the gloves button closes the look screen, injects the gun
 * into the main hand (client-side) and triggers TACZ's native inspect; this
 * handler watches for the animation to finish, then restores the hand and
 * reopens the look screen. TACZ is optional and each entry is facade-guarded.
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
        int ticks;
        int doneStreak;
        boolean inspectTriggered;
    }

    private FirstPersonInspectHandler() {
    }

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