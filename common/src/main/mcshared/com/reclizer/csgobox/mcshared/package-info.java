/**
 * Cross-era shared Minecraft-bound sources (CONSTRAINT-001 exception root).
 *
 * <p>Files here are compiled into every platform module via
 * {@code srcDir project(':common').file('src/main/mcshared')} and therefore
 * MUST only use Minecraft APIs that exist unchanged from MC 1.20.1 through
 * 26.2, plus loader-neutral APIs (WTHIT, Cloth Config). Unlike
 * {@code common/src/main/java}, importing {@code net.minecraft.*} is allowed
 * here — but a file may only live here when all six platform copies are
 * (or can be) byte-identical. Platform-adapted code belongs in the platform
 * modules, not here.</p>
 */
package com.reclizer.csgobox.mcshared;
