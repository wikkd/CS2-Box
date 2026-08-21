package com.reclizer.csgobox.v26_2.gui;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.terminal.TerminalPalette;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.utils.Easing;
import com.reclizer.csgobox.v26_2.gui.terminal.TerminalActionBar;
import com.reclizer.csgobox.v26_2.gui.terminal.TerminalBottomRow;
import com.reclizer.csgobox.v26_2.gui.terminal.TerminalChatRegion;
import com.reclizer.csgobox.v26_2.gui.terminal.TerminalConfirmDialog;
import com.reclizer.csgobox.v26_2.gui.terminal.TerminalOfferRegion;
import com.reclizer.csgobox.v26_2.gui.terminal.TerminalOfferItems;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_2.packet.PacketTerminalBuy;
import com.reclizer.csgobox.v26_2.packet.PacketTerminalBuyResult;
import com.reclizer.csgobox.v26_2.packet.PacketTerminalClose;
import com.reclizer.csgobox.v26_2.packet.PacketTerminalOpen;
import com.reclizer.csgobox.v26_2.packet.PacketTerminalReject;
import com.reclizer.csgobox.v26_2.packet.PacketTerminalState;
import com.reclizer.csgobox.v26_2.utils.AnimRenderOps;
import com.reclizer.csgobox.v26_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Terminal machine screen — the full HTML prototype (design/terminal-chat.html)
 * migrated to Java: chat + offer cards (left), action bar (bottom-left),
 * offer inspection panel (right), countdown / item slot / collection strip
 * (bottom-right). All animation timing lives in common
 * {@link com.reclizer.csgobox.terminal.TerminalAnims}; this screen only
 * assembles the four region helpers and forwards input.
 *
 * era: decoupled
 */
public class TerminalScreen extends Screen {

    /** The open terminal screen, or null. Only one screen can be open at a time. */
    private static TerminalScreen OPEN_INSTANCE;

    /** Current open terminal screen (buy result handler looks it up this way). */
    public static TerminalScreen getOpen() {
        return OPEN_INSTANCE;
    }

    private final NegotiationModel model = new NegotiationModel();
    private final TerminalChatRegion chatRegion = new TerminalChatRegion();
    private final TerminalActionBar actionBar = new TerminalActionBar();
    private final TerminalOfferRegion offerRegion = new TerminalOfferRegion();
    private final TerminalBottomRow bottomRow = new TerminalBottomRow();
    private final TerminalConfirmDialog confirmDialog = new TerminalConfirmDialog();
    private static final int INTRO_FADE_TICKS = 10;
    private int introTicks;
    private long nowMs;
    private long buyRequestId;
    /** When the buy request was sent — the waiting dialog must not hang forever. */
    private long buySentAtMs;
    /** True once the server's locked session state has been applied. */
    private boolean stateReceived;
    private boolean closeSynced;
    /** The server-side session uid — identifies THIS terminal on close. */
    private String terminalUid;
    /** Nonce echoed by the server so a stale reply for another terminal is dropped. */
    private final long requestId = System.nanoTime();
    /** When this open was requested — the server must answer within 5s
     *  (world clock, so a server lag-spike that stalls the world never
     *  triggers a false "unreachable"; a genuinely lost packet surfaces as
     *  the world still ticking past the deadline). */
    private final long openSentAtMs = worldNowMs();
    /** Terminal item stack (copy) — box_id travels with the buy request. */
    private final ItemStack terminalStack;
    /** Terminal item display name (config name or anvil rename). */
    private final Component terminalName;

    public TerminalScreen(ItemStack terminalStack) {
        super(Component.translatable("gui.csgobox.terminal.title"));
        OPEN_INSTANCE = this;
        this.terminalStack = terminalStack.copy();
        this.terminalName = terminalStack.getHoverName();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(new PacketTerminalOpen(this.terminalStack, requestId)));
        }
    }

    /**
     * Applies the server's locked session snapshot (round/status/history,
     * sampled per-round items, slot item) so this open resumes exactly where
     * the player left off — or starts a fresh negotiation when the lock was
     * released by a buy or five rejects.
     */
    public void onTerminalState(PacketTerminalState state) {
        if (stateReceived) {
            return;
        }
        // A stale reply from a previously opened terminal (or a server push)
        // must never leak into this screen — every terminal of the same type
        // shares the box id, so only the matching request nonce proves this
        // reply belongs to THIS open.
        if (state.requestId() != requestId) {
            return;
        }
        TerminalOfferItems.reset();
        // A stale reply from a previously opened terminal (or a server push)
        // must never leak into a different terminal's screen.
        net.minecraft.resources.Identifier boxId = ItemCsgoBox.getBoxId(terminalStack);
        if (boxId == null || !boxId.toString().equals(state.boxId())) {
            return;
        }
        this.stateReceived = true;
        this.terminalUid = state.terminalUid();
        Map<Integer, NegotiationModel.Offer> offers = new HashMap<>();
        for (PacketTerminalState.RoundItem ri : state.rounds()) {
            offers.put(ri.round(), ri.offer());
            TerminalOfferItems.setRoundItem(ri.round(), ri.item(), ri.grade(), ri.offer().wearVal(), ri.price());
        }
        TerminalOfferItems.setSessionItem(state.sessionItem());
        model.setOfferSource(offers::get);
        NegotiationModel.Status status = NegotiationModel.Status.values()[
                Math.max(0, Math.min(state.status(), NegotiationModel.Status.values().length - 1))];
        model.restore(new NegotiationModel.Snapshot(
                state.round(), status, state.generation(), state.cap(),
                state.countdownDeadlineMs(), state.pending(), state.history()),
                worldNowMs());
    }

    // ---- layout fractions (HTML prototype) ----
    private int px(double f) {
        return (int) Math.round(width * f);
    }

    private int py(double f) {
        return (int) Math.round(height * f);
    }

    @Override
    public void tick() {
        super.tick();
        long worldNow = worldNowMs();
        if (this.introTicks < INTRO_FADE_TICKS) {
            this.introTicks++;
        }
        // The server never answered (held item changed mid-open, death, or an
        // unexpected server error): fail visibly instead of hanging forever.
        if (!stateReceived && worldNow - openSentAtMs > 5_000L) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(
                        Component.translatable("csgobox.terminal.sys.unreachable"));
            }
            onClose();
            return;
        }
        // A buy reply that never arrives must not trap the confirm dialog in
        // its input-consuming waiting state forever — bail out after 6 s.
        if (confirmDialog.isWaiting() && worldNow - buySentAtMs > 6_000L) {
            confirmDialog.close();
            model.dealerReconsider(worldNow);
            model.addSystem("csgobox.terminal.sys.unreachable", worldNow);
        }
    }

    /**
     * World running time in ms (world game ticks × 50) — the terminal
     * countdown follows the world's clock, so it pauses/stops with the world
     * instead of the player's wall clock.
     */
    private static long worldNowMs() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() * 50L : System.currentTimeMillis();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(gg, mouseX, mouseY, partialTicks);
        this.nowMs = worldNowMs();
        model.tick(nowMs);
        Player player = Minecraft.getInstance().player;

        // ---- stage background ----
        AnimRenderOps.fill(gg, 0, 0, width, height, TerminalPalette.OUTSIDE);

        // frame: olive border + inner bg
        int fx0 = px(0.010), fy0 = py(0.012), fx1 = px(0.990), fy1 = py(0.988);
        AnimRenderOps.fill(gg, fx0 - 2, fy0 - 2, fx1 + 2, fy1 + 2, TerminalPalette.FRAME);
        AnimRenderOps.fill(gg, fx0, fy0, fx1, fy1, 0xFF17191C);

        // ---- top bar (slim gradient strip, HTML .title-strip) ----
        int tx0 = fx0, ty0 = fy0, tx1 = fx1, ty1 = py(0.050);
        AnimRenderOps.fillGradient(gg, tx0, ty0, tx1, ty1, TerminalPalette.TITLE_TOP,
                TerminalPalette.TITLE);
        AnimRenderOps.fill(gg, tx0, ty1 - 1, tx1, ty1, TerminalPalette.FRAME);
        Font font = Minecraft.getInstance().font;
        // left: battery + signal (compact icons)
        int iconY = ty0 + (ty1 - ty0) / 2 - 3;
        AnimRenderOps.fill(gg, tx0 + 8, iconY, tx0 + 14, iconY + 6, 0xFF14181C);
        AnimRenderOps.fill(gg, tx0 + 9, iconY + 1, tx0 + 13, iconY + 5, TerminalPalette.BATTERY);
        for (int i = 0; i < 3; i++) {
            AnimRenderOps.fill(gg, tx0 + 18 + i * 3, iconY + 6 - (i + 1) * 2,
                    tx0 + 20 + i * 3, iconY + 6, TerminalPalette.BATTERY);
        }
        // centre: title
        Component title = Component.translatable("gui.csgobox.terminal.title");
        int titleW = RenderFontTool.widthSpaced(font, title.getString(), 0.7F, 0.55F);
        RenderFontTool.drawSpacedText(gg, font, title.getString(),
                (tx0 + tx1) / 2F - titleW / 2F, ty0 + 2, 0.7F, 0.55F, 0xFFE6EAED);
        // right: close button (glyph only; hover shows a subtle square)
        closeX = tx1 - 7;
        closeY = ty0 + 3;
        closeW = 6;
        closeH = 6;
        boolean closeHover = mouseX >= closeX - 1 && mouseX <= closeX + closeW + 1
                && mouseY >= closeY - 1 && mouseY <= closeY + closeH + 1;
        if (closeHover) {
            AnimRenderOps.fill(gg, closeX - 1, closeY - 1, closeX + closeW + 1,
                    closeY + closeH + 1, TerminalPalette.CLOSE_HOVER);
        }
        RenderFontTool.drawString(gg, font, fcs("✕"), closeX + 2, closeY + 1, 0, 0, 0.5F,
                closeHover ? 0xFFFFFFFF : 0xFFC7D0D9);

        // ---- left column: chat (region 4+5) ----
        int lx0 = px(0.020), ly0 = py(0.122), lx1 = px(0.358), ly1 = py(0.873);
        chatX0 = lx0;
        chatY0 = ly0;
        chatX1 = lx1;
        chatY1 = ly1;
        drawPanel(gg, lx0, ly0, lx1, ly1);
        chatRegion.render(gg, lx0, ly0, lx1, ly1, nowMs, model);

        // ---- action bar (region 6) ----
        int ax0 = px(0.020), ay0 = py(0.875), ax1 = px(0.358), ay1 = py(0.988);
        drawPanel(gg, ax0, ay0, ax1, ay1);
        actionBar.render(gg, ax0, ay0, ax1, ay1, nowMs, model, mouseX, mouseY);

        // ---- right column: offer panel (region 7+8) ----
        int rx0 = px(0.370), ry0 = py(0.122), rx1 = px(0.998), ry1 = py(0.855);
        drawPanel(gg, rx0, ry0, rx1, ry1);
        // region 7 title strip
        int rty = ry0 + 9;
        AnimRenderOps.fill(gg, rx0, ry0, rx1, rty, TerminalPalette.TITLE);
        Component offerTitle = Component.translatable("csgobox.terminal.offer.title");
        int offerTitleW = RenderFontTool.widthSpaced(font, offerTitle.getString(), 0.6F, 0.47F);
        RenderFontTool.drawSpacedText(gg, font, offerTitle.getString(),
                rx0 + 4, ry0 + 2, 0.6F, 0.47F, TerminalPalette.TEXT);
        offerRegion.render(gg, rx0, rty, rx1, ry1, nowMs, model, player, mouseX, mouseY);

        // ---- bottom row (region 9+10+11) ----
        int bx0 = px(0.370), by0 = py(0.875), bx1 = px(0.998), by1 = py(0.988);
        drawPanel(gg, bx0, by0, bx1, by1);
        bottomRow.render(gg, bx0, by0, bx1, by1, nowMs, model, player, terminalName);
        confirmDialog.render(gg, width, height, player);
        renderIntroFade(gg);
    }

    /** Black overlay fading out over the first ticks after opening. */
    private void renderIntroFade(GuiGraphicsExtractor gg) {
        if (this.introTicks >= INTRO_FADE_TICKS) {
            return;
        }
        float p = Easing.smoothstep(0F, 1F, Math.min(1F, this.introTicks / (float) INTRO_FADE_TICKS));
        int alpha = Math.round(255F * (1F - p));
        AnimRenderOps.fill(gg, 0, 0, this.width, this.height, ColorTools.withAlpha(0xFF000000, alpha));
    }

    /** FormattedCharSequence wrapper for plain strings. */
    private static FormattedCharSequence fcs(String s) {
        return FormattedCharSequence.forward(s, Style.EMPTY);
    }

    private void drawPanel(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1) {
        AnimRenderOps.fill(gg, x0 - 2, y0 - 2, x1 + 2, y1 + 2, TerminalPalette.FRAME);
        AnimRenderOps.fill(gg, x0, y0, x1, y1, 0xFF17191C);
    }

    // ---- close button rect (for hit-testing) ----
    private int closeX, closeY, closeW, closeH;
    // last-known pointer position (Screen has no mouse fields in 26.x)
    private int mouseX, mouseY;
    // chat panel rect (region 4+5, for wheel hit-testing)
    private int chatX0, chatY0, chatX1, chatY1;

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.mouseX = (int) event.x();
        this.mouseY = (int) event.y();
        long now = worldNowMs();
        if (confirmDialog.isOpen()) {
            TerminalConfirmDialog.Hit hit = confirmDialog.mouseDown(mouseX, mouseY, now);
            if (hit == TerminalConfirmDialog.Hit.CONFIRM) {
                sendBuyRequest(now);
            } else if (hit == TerminalConfirmDialog.Hit.CANCEL) {
                confirmDialog.close();
                model.dealerReconsider(now);
            }
            return true;
        }
        if (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH) {
            onClose();
            return true;
        }
        if (actionBar.mouseDown(mouseX, mouseY, now, model)) {
            return true;
        }
        if (offerRegion.mouseDown(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.mouseX = (int) event.x();
        this.mouseY = (int) event.y();
        if (confirmDialog.isOpen()) {
            offerRegion.mouseUp();
            return true;
        }
        long now = worldNowMs();
        TerminalActionBar.Fired fired = actionBar.mouseUp(mouseX, mouseY, now);
        if (fired == TerminalActionBar.Fired.ACCEPT) {
            NegotiationModel.Offer offer = model.pending();
            if (offer != null) {
                confirmDialog.open(TerminalOfferItems.itemFor(offer),
                        TerminalOfferItems.priceFor(offer),
                        TerminalOfferItems.basePriceFor(offer), offer.wearVal());
            }
        } else if (fired == TerminalActionBar.Fired.REJECT) {
            model.rejectNow(now);
            sendRejectRequest(model.round());
        }
        offerRegion.mouseUp();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        this.mouseX = (int) event.x();
        this.mouseY = (int) event.y();
        if (offerRegion.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= chatX0 && mouseX <= chatX1 && mouseY >= chatY0 && mouseY <= chatY1) {
            chatRegion.scrolled(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // GLFW_KEY_ESCAPE
            if (confirmDialog.isOpen()) {
                if (confirmDialog.isWaiting()) {
                    return true; // buy request in flight — never cancel mid-trade
                }
                long now = worldNowMs();
                confirmDialog.close();
                model.dealerReconsider(now);
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Sends the buy request for the offer currently shown in the dialog. */
    private void sendBuyRequest(long now) {
        NegotiationModel.Offer offer = model.pending();
        if (offer == null) {
            confirmDialog.close();
            return;
        }
        this.buyRequestId = System.nanoTime();
        this.buySentAtMs = now;
        confirmDialog.setWaiting();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(new PacketTerminalBuy(
                    buyRequestId, terminalStack, TerminalOfferItems.itemFor(offer),
                    offer.wearVal(), offer.round())));
        } else {
            confirmDialog.close();
            model.dealerReconsider(now);
        }
    }

    /** Tells the server the current round was rejected (server commits the advance). */
    private void sendRejectRequest(int round) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(new PacketTerminalReject(round)));
        }
    }

    /** Pins the session to the view we leave, so a reopen resumes identically. */
    private void syncCloseState() {
        if (closeSynced || !stateReceived) {
            return;
        }
        closeSynced = true;
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            return;
        }
        long pendingAtMs = 0L;
        NegotiationModel.OfferEntry lastOffer = model.lastOfferEntry();
        if (lastOffer != null) {
            pendingAtMs = lastOffer.atMs();
        }
        conn.send(new ServerboundCustomPayloadPacket(new PacketTerminalClose(
                terminalUid, model.round(), model.pending() != null,
                pendingAtMs, model.cap())));
    }

    /** Server verdict for the pending buy request. */
    public void onBuyResult(long requestId, int result, ItemStack givenItem) {
        if (requestId != buyRequestId || !confirmDialog.isOpen()) {
            return;
        }
        confirmDialog.close();
        long now = worldNowMs();
        if (result == PacketTerminalBuyResult.RESULT_SUCCESS) {
            model.acceptNow(now);
        } else if (result == PacketTerminalBuyResult.RESULT_INSUFFICIENT) {
            model.dealerReconsider(now);
            model.addSystem("csgobox.terminal.sys.poor", now);
        } else {
            model.dealerReconsider(now);
            model.addSystem("csgobox.terminal.sys.invalid", now);
        }
    }

    @Override
    public void onClose() {
        syncCloseState();
        if (OPEN_INSTANCE == this) {
            OPEN_INSTANCE = null;
        }
        actionBar.close();
        confirmDialog.close();
        super.onClose();
    }

    @Override
    public void removed() {
        // setScreen() replacement / death only calls removed(), never
        // onClose() — clear the stale singleton so late buy results are dropped.
        syncCloseState();
        if (OPEN_INSTANCE == this) {
            OPEN_INSTANCE = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
