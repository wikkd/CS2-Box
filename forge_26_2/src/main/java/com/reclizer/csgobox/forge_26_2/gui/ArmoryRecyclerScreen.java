package com.reclizer.csgobox.forge_26_2.gui;

import com.reclizer.csgobox.forge_26_2.CsgoBox;
import com.reclizer.csgobox.forge_26_2.block.entity.ArmoryRecyclerBlockEntity;
import com.reclizer.csgobox.forge_26_2.menu.ArmoryRecyclerMenu;
import com.reclizer.csgobox.forge_26_2.utils.AnimRenderOps;
import com.reclizer.csgobox.forge_26_2.utils.RenderFontTool;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Vanilla furnace-style container screen for the armory recycler: input slot,
 * animated progress arrow and output slot. No dismantle button, no fuel/flame
 * slots: the block entity smelts one graded item every {@code SMELT_TICKS}
 * ticks and drops the Armory Point yield into the output slot for the player
 * to pick up.
 *
 * era: decoupled
 */
public class ArmoryRecyclerScreen extends AbstractContainerScreen<ArmoryRecyclerMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "textures/gui/armory_recycler.png");
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 166;
    /** Gold arrow sprite location within the texture (furnace convention). */
    private static final int ARROW_U = 176;
    private static final int ARROW_V = 14;
    private static final int ARROW_WIDTH = 22;
    private static final int ARROW_HEIGHT = 16;
    private static final int ARROW_X = 77;
    private static final int ARROW_Y = 36;

    /** Vanilla container label gray (matches AbstractContainerScreen.extractLabels). */
    private static final int LABEL_COLOR = 0xFF404040;

    public ArmoryRecyclerScreen(ArmoryRecyclerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(gg, mouseX, mouseY, partialTicks);
        AnimRenderOps.blitTextured(gg, TEXTURE, leftPos, topPos, imageWidth, imageHeight,
                0, 0, imageWidth, imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT, 0xFFFFFFFF);
        int progress = menu.getProgress();
        if (progress > 0) {
            int width = Math.min(ARROW_WIDTH,
                    Mth.ceil(progress * ARROW_WIDTH / (float) ArmoryRecyclerBlockEntity.SMELT_TICKS));
            AnimRenderOps.blitTextured(gg, TEXTURE, leftPos + ARROW_X, topPos + ARROW_Y,
                    width, ARROW_HEIGHT, ARROW_U, ARROW_V, width, ARROW_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT, 0xFFFFFFFF);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor gg, int mouseX, int mouseY) {
        Font font = minecraft.font;
        RenderFontTool.drawString(gg, font, fcs(title.getString()), 8, 6, 0, 0, 1.0F, LABEL_COLOR);
        RenderFontTool.drawString(gg, font,
                fcs(Component.translatable("gui.csgobox.armory_recycler.inventory").getString()),
                8, imageHeight - 94, 0, 0, 1.0F, LABEL_COLOR);
    }

    private static FormattedCharSequence fcs(String text) {
        return FormattedCharSequence.forward(text, Style.EMPTY);
    }
}
