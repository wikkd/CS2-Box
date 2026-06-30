package com.reclizer.csgobox.platform;
public interface IGuiGraphics {
    IPoseStack pose();
    void renderItem(Object entity, Object itemStack, int x, int y, int seed);
}
