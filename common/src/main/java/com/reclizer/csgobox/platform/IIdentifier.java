package com.reclizer.csgobox.platform;
public interface IIdentifier {
    String getNamespace();
    String getPath();
    default String toShortString() { return getNamespace() + ":" + getPath(); }
    @Override boolean equals(Object o);
    @Override int hashCode();
}
