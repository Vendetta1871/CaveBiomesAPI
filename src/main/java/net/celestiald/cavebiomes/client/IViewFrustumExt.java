package net.celestiald.cavebiomes.client;

import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;

/**
 * Duck interface added to {@link net.minecraft.client.renderer.ViewFrustum} by
 * MixinViewFrustum. ViewFrustum.getRenderChunk is protected and lives in a
 * different package than our mixins, so MixinRenderGlobal cannot call it directly
 * at compile time. This exposes a public, custom-named bridge (no obfuscated
 * symbol -> no refmap dependency) that forwards to the overwritten getRenderChunk.
 *
 * IMPORTANT: this MUST NOT live in the mixin package (net.celestiald.cavebiomes.mixin):
 * Mixin treats every class there as a mixin and refuses to let it be referenced as
 * a normal class ("is a mixin class and cannot be referenced directly"), which
 * crashed RenderGlobal at world load. Client-only: only referenced by client mixins.
 */
public interface IViewFrustumExt {

    RenderChunk cavebiomes$getRenderChunk(BlockPos pos);
}
