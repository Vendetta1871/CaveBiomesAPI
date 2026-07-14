package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"net.celestiald.cavebiomes.core"})
public class CaveBiomesLoadingPlugin implements IFMLLoadingPlugin {

    @Override public String[] getASMTransformerClass() {
        return new String[]{
                NetworkQueueRaceTransformer.class.getName(),
                ClientBlockChangeGuardTransformer.class.getName(),
                OptiFineRenderChunkTransformer.class.getName(),
                OptiFineChunkVisibilityTransformer.class.getName(),
                DynmapTransformer.class.getName(),
                FluidloggedApiTransformer.class.getName(),
                XaeroMapTransformer.class.getName(),
                LegacyModSectionTransformer.class.getName(),
                LegacyWorldgenSectionTransformer.class.getName(),
                LegacyPlacementSectionTransformer.class.getName(),
                CqrSectionTransformer.class.getName()
        };
    }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {
        MixinBootstrapRequirement.initialize(Launch.classLoader);
    }
    @Override public String getAccessTransformerClass() { return null; }
}
