package com.reclizer.csgobox.platform;
public interface IPoseStack {
    void push();
    void pop();
    void translate(float x, float y);
    void scale(float sx, float sy);
    void rotate(float angleRadians);
}
