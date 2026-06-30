package com.reclizer.csgobox.platform;
public interface IAttachmentRegistrar {
    void registerPlayerAttachment(String name, java.util.function.Supplier<?> defaultFactory, Object codec);
}
