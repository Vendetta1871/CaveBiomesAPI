package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends vanilla Anvil decoding without replacing the loader or its compatibility hooks. */
@Mixin(AnvilChunkLoader.class)
public abstract class MixinAnvilChunkLoader {

    @Inject(method = "readChunkFromNBT", at = @At("HEAD"), require = 1)
    private void cavebiomes$rejectUnsupportedExtendedIds(World world, NBTTagCompound chunkData,
            CallbackInfoReturnable<?> cir) {
        if (!requiresRoughlyEnoughIds(chunkData) || hasRoughlyEnoughIds()) {
            return;
        }
        throw new IllegalStateException("Chunk " + chunkData.getInteger("xPos") + ','
                + chunkData.getInteger("zPos") + " uses RoughlyEnoughIDs palette or biome data, "
                + "but RoughlyEnoughIDs is not available. Refusing to decode and overwrite it.");
    }

    /** Resize the vanilla section array while leaving the rest of the decoder intact. */
    @ModifyConstant(method = "readChunkFromNBT",
            constant = @Constant(intValue = 16, ordinal = 1), require = 1)
    private int cavebiomes$sectionArrayLength(int vanillaLength) {
        return WorldHeightAPI.getSectionCount();
    }

    /**
     * Convert the persisted absolute section coordinate to an array index. Keeping vanilla's
     * method body is essential: RoughlyEnoughIDs and other coremods inject their palette and
     * biome decoding into this method before block data is consumed.
     */
    @Redirect(method = "readChunkFromNBT",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NBTTagCompound;getByte(Ljava/lang/String;)B",
                    ordinal = 0), require = 1)
    private byte cavebiomes$normalizePersistedSectionY(NBTTagCompound section, String key) {
        int sectionY = section.getByte(key);
        int index = WorldHeightAPI.sectionIndexFromSectionY(sectionY);
        if (index < 0 || index >= WorldHeightAPI.getSectionCount()) {
            throw new IllegalStateException("Saved chunk section Y=" + sectionY
                    + " is outside the configured Cave Biomes API range "
                    + WorldHeightAPI.getMinY() + ".." + WorldHeightAPI.getMaxY());
        }
        return (byte) index;
    }

    /** Restore the absolute Y passed to ExtendedBlockStorage after normalizing its array index. */
    @ModifyArg(method = "readChunkFromNBT",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;<init>(IZ)V"),
            index = 0, require = 1)
    private int cavebiomes$restoreSectionBaseY(int normalizedBaseY) {
        return normalizedBaseY + WorldHeightAPI.getMinY();
    }

    private static boolean requiresRoughlyEnoughIds(NBTTagCompound chunkData) {
        if (chunkData.hasKey("Biomes", 11)) {
            return true;
        }
        NBTTagList sections = chunkData.getTagList("Sections", 10);
        for (int index = 0; index < sections.tagCount(); index++) {
            NBTTagCompound section = sections.getCompoundTagAt(index);
            if (section.hasKey("Palette", 11) || section.hasKey("Add2", 7)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRoughlyEnoughIds() {
        try {
            Class.forName("org.dimdev.jeid.ducks.INewBlockStateContainer", false,
                    MixinAnvilChunkLoader.class.getClassLoader());
            Class.forName("tff.reid.api.BiomeApi", false,
                    MixinAnvilChunkLoader.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException unavailable) {
            return false;
        }
    }
}
