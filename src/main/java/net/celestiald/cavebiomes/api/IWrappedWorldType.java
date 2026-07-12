package net.celestiald.cavebiomes.api;

import net.minecraft.world.WorldType;

/** Exposes the selected base type when a finite-height world type wraps another generator. */
public interface IWrappedWorldType {
    WorldType getBaseWorldType();
}
