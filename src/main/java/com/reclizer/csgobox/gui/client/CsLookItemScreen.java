package com.reclizer.csgobox.gui.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reclizer.csgobox.capability.CsboxPlayerData;
import com.reclizer.csgobox.capability.ModCapability;
import com.reclizer.csgobox.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.sounds.ModSounds;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.utils.GuiItemMove;
import com.reclizer.csgobox.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class CsLookItemScreen extends Screen {
    private final Player player;
    public ItemStack openItem;
    public int grade;
    private float rotX = 0;
    private float rotY = 0;
    private boolean openSwitch = true;
    private boolean soundPlayed = false;


    public CsLookItemScreen() {
        super(Component.literal("look_item"));
        this.player = Minecraft.getInstance().player;
        CsboxPlayerData data = player.getData(ModCapability.PLAYER_DATA);
        ItemStack item = data.item();
        if (!item.isEmpty()) {
            this.openItem = item;
            this.grade = data.grade();
            this.openSwitch = false;
            player.playSound(ModSounds.CS_FINSH.get(), 10F, 1F);
            this.soundPlayed = true;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderLookBackground(guiGraphics);
        this.renderLabels(guiGraphics, mouseX, mouseY);
        this.renderBg(guiGraphics, partialTicks, mouseX, mouseY);
    }

    private void renderLookBackground(GuiGraphics guiGraphics) {
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height,
                    OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        }
    }

    private void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = true;
        }

        if (openItem == null) return;

        int FrameWidth = width * 26 / 100;
        float scale = FrameWidth / 16F;

        guiGraphics.fill(this.width * 25 / 100, this.height * 92 / 100,
                this.width * 75 / 100, this.height * 92 / 100 + 1, 0xFFD3D3D3);

        guiGraphics.fill(this.width * 37 / 100, this.height * 16 / 100,
                this.width * 63 / 100, this.height * 16 / 100 + 4, ColorTools.colorItems(grade));

        GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics, this.width * 37 / 100, this.height * 30 / 100,
                this.rotX, this.rotY, openItem, this.player, scale);

        int btnX = this.width * 72 / 100;
        int btnY = this.height * 94 / 100;
        int btnW = this.width * 4 / 100;
        int btnH = this.height * 5 / 100;
        boolean hoverButton = gx >= btnX && gx <= btnX + btnW && gy >= btnY && gy <= btnY + btnH;
        int outerColor = hoverButton ? 0xFFFF4444 : 0xFFFF0000;
        int innerColor = hoverButton ? 0xFFCC4444 : 0xFFAA0000;
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, outerColor);
        guiGraphics.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, innerColor);

        RenderSystem.disableBlend();
    }

    private void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (openItem == null) return;

        Style style = Style.EMPTY.withBold(true);
        FormattedCharSequence nameText = openItem.getItem().getName(openItem).getVisualOrderText();
        renderText(guiGraphics, nameText, this.width * 45F / 100F, this.height * 5F / 100F, 1.8F);

        renderText(guiGraphics,
                Component.translatable("gui.csgobox.csgo_box.grade" + grade).getVisualOrderText(),
                this.width * 45F / 100F, this.height * 11F / 100F, 1F);

        renderText(guiGraphics,
                Component.translatable("gui.csgobox.csgo_box.back_box").withStyle(style).getVisualOrderText(),
                (float) this.width * 72.5F / 100F, (float) this.height * 95 / 100, 0.8F);
    }

    private void renderText(GuiGraphics guiGraphics, FormattedCharSequence pText, float px, float py, float scale) {
        RenderFontTool.drawString(guiGraphics, this.font, pText, px, py, 0, 0, scale, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        boolean isInRange = (pMouseX >= this.width * 37F / 100 - 100 && pMouseX <= this.width * 37F / 100 + 100)
                && (pMouseY >= this.height * 30F / 100 - 88 && pMouseY <= this.height * 30F / 100 + 88);
        if (pButton == 0 && isInRange) {
            this.rotX = GuiItemMove.renderRotAngleX(pDragX, this.rotX);
            this.rotY = GuiItemMove.renderRotAngleY(pDragY, this.rotY);
        }
        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 0) {
            int btnX = this.width * 72 / 100;
            int btnY = this.height * 94 / 100;
            int btnW = this.width * 4 / 100;
            int btnH = this.height * 5 / 100;
            if (pMouseX >= btnX && pMouseX <= btnX + btnW && pMouseY >= btnY && pMouseY <= btnY + btnH) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean keyPressed(int key, int b, int c) {
        if (key == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(key, b, c);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = false;
        }
        super.onClose();
    }

    @Override
    public final void tick() {
        super.tick();
        if (this.minecraft == null) return;
        if (this.minecraft.player == null) return;
        if (this.minecraft.player.isAlive() && !this.minecraft.player.isRemoved()) {
            this.containerTick();
        } else {
            this.minecraft.player.closeContainer();
        }
    }

    public void containerTick() {
        if (!openSwitch) return;
        CsboxPlayerData data = player.getData(ModCapability.PLAYER_DATA);
        ItemStack itemStack = data.item();
        if (!itemStack.isEmpty()) {
            finishOpen(itemStack, data.grade());
            return;
        }
        ItemStack pendingItem = PacketBoxOpenResult.consumePendingItem();
        if (!pendingItem.isEmpty()) {
            finishOpen(pendingItem, PacketBoxOpenResult.consumePendingGrade());
        }
    }

    private void finishOpen(ItemStack itemStack, int itemGrade) {
        if (!soundPlayed) {
            player.playSound(ModSounds.CS_FINSH.get(), 10F, 1F);
            soundPlayed = true;
        }
        openItem = itemStack;
        grade = itemGrade;
        openSwitch = false;
    }

    @Override
    protected void init() {
        super.init();
    }
}
