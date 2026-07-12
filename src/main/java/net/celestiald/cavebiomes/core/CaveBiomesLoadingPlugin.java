package net.celestiald.cavebiomes.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
public class CaveBiomesLoadingPlugin implements IFMLLoadingPlugin {

    public CaveBiomesLoadingPlugin() {
        MixinBootstrapRequirement.initialize(
                CaveBiomesLoadingPlugin.class.getClassLoader());
    }

    @Override public String[] getASMTransformerClass() { return null; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {}
    @Override public String getAccessTransformerClass() { return null; }
}
