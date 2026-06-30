package com.reclizer.csgobox.platform;
public interface IPayloadContext {
    void reply(Object payload);
    <T> T enqueueWork(java.util.function.Supplier<T> work);
    Object player();
    boolean isClientSide();
}
