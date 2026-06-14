package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(AnvilChunkLoader.class)
public class MixinAnvilChunkLoader {

    /**
     * Replace hardcoded ExtendedBlockStorage[16] with a correctly-sized array,
     * and use (sectionY - minSection) as the array index so negative section
     * bytes (e.g. -4 for Y=-64) don't cause ArrayIndexOutOfBoundsException.
     *
     * ChunkDataEvent.Load is commented out in Forge 1.12.2 (line 168 in source).
     * The only Forge hook that must be preserved is capability deserialization.
     */
    @Inject(method = "readChunkFromNBT", at = @At("HEAD"), cancellable = true)
    private void fixReadChunkFromNBT(World worldIn, NBTTagCompound compound,
                                     CallbackInfoReturnable<Chunk> cir) {
        int chunkX = compound.getInteger("xPos");
        int chunkZ = compound.getInteger("zPos");
        Chunk chunk = new Chunk(worldIn, chunkX, chunkZ);
        chunk.setHeightMap(compound.getIntArray("HeightMap"));
        chunk.setTerrainPopulated(compound.getBoolean("TerrainPopulated"));
        chunk.setLightPopulated(compound.getBoolean("LightPopulated"));
        chunk.setInhabitedTime(compound.getLong("InhabitedTime"));

        int sectionCount = WorldHeightAPI.getSectionCount();
        int minSection   = WorldHeightAPI.getMinSection();
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[sectionCount];
        boolean hasSkyLight = worldIn.provider.hasSkyLight();

        NBTTagList nbtSections = compound.getTagList("Sections", 10);
        for (int l = 0; l < nbtSections.tagCount(); ++l) {
            NBTTagCompound sec = nbtSections.getCompoundTagAt(l);
            int sectionY = sec.getByte("Y");          // signed byte: e.g. -4 for Y=-64
            int idx = sectionY - minSection;           // normalized to 0-based index
            if (idx < 0 || idx >= sectionCount) continue;

            ExtendedBlockStorage storage = new ExtendedBlockStorage(sectionY << 4, hasSkyLight);
            byte[]      blocks = sec.getByteArray("Blocks");
            NibbleArray data   = new NibbleArray(sec.getByteArray("Data"));
            NibbleArray extra  = sec.hasKey("Add", 7)
                    ? new NibbleArray(sec.getByteArray("Add")) : null;
            storage.getData().setDataFromNBT(blocks, data, extra);
            storage.setBlockLight(new NibbleArray(sec.getByteArray("BlockLight")));
            if (hasSkyLight) {
                storage.setSkyLight(new NibbleArray(sec.getByteArray("SkyLight")));
            }
            storage.recalculateRefCounts();
            sections[idx] = storage;
        }

        chunk.setStorageArrays(sections);

        if (compound.hasKey("Biomes", 7)) {
            chunk.setBiomeArray(compound.getByteArray("Biomes"));
        }

        // Forge capability hook (present in forgeSrc, ChunkDataEvent.Load is commented out)
        if (chunk.getCapabilities() != null && compound.hasKey("ForgeCaps")) {
            chunk.getCapabilities().deserializeNBT(compound.getCompoundTag("ForgeCaps"));
        }

        cir.setReturnValue(chunk);
    }
}
