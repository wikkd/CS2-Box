package com.reclizer.csgobox.v1_21_1.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.reclizer.csgobox.v1_21_1.event.FirstPersonInspectHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Transparent full-screen lock shown while the first-person inspect plays.
 *
 * <p>It renders nothing, so the 3D world and the player's first-person gun
 * (with the native TACZ inspect animation) stay fully visible. Because a
 * {@link Screen} is open, vanilla swallows all keyboard/mouse input — the
 * player cannot move, jump, open inventory or shoot. The only escape hatch is
 * Esc, which ends the inspect session immediately and returns to the look
 * screen (see {@link FirstPersonInspectHandler#requestFinish()}).
 */
public class FirstPersonInspectScreen extends Screen {
    public FirstPersonInspectScreen() {
        super(Component.literal("first_person_inspect"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // No background, no widgets: the first-person view shows through.
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            FirstPersonInspectHandler.requestFinish();
            return true;
        }
        // Swallow every other key so the player can't act during the inspect.
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        // Keep the game ticking so the TACZ inspect animation advances.
        return false;
    }

    @Override
    public void onClose() {
        // Belt-and-braces: if the screen is closed through any other path,
        // end the inspect session instead of silently dropping the player
        // mid-animation.
        FirstPersonInspectHandler.requestFinish();
    }
}