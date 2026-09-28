package net.celestiald.cavebiomes.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.spongepowered.asm.mixin.Overwrite;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OverwriteVisibilityContractTest {
    /**
     * Fluidlogged API's access transformer makes Chunk#relightBlock public. Mixin 0.8 rejects a
     * private @Overwrite of a public target unless the config conforms overwrite visibility, and
     * a rejected MixinChunk leaves every chunk at 16 sections ("Chunk has 16 sections, expected
     * 24" on the first generated chunk).
     */
    @Test
    public void overwritesConformToVisibilityWidenedByOtherModsAccessTransformers() throws Exception {
        JsonObject config;
        InputStream input = OverwriteVisibilityContractTest.class
                .getResourceAsStream("/mixins.cavebiomes.json");
        assertNotNull(input);
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            config = new JsonParser().parse(reader).getAsJsonObject();
        }
        assertTrue(config.has("overwrites"));
        assertTrue(config.getAsJsonObject("overwrites").get("conformVisibility").getAsBoolean());
    }

    @Test
    public void relightBlockIsTheKnownPrivateOverwrite() throws Exception {
        Method relight = MixinChunk.class.getDeclaredMethod(
                "relightBlock", int.class, int.class, int.class);
        assertNotNull(relight.getAnnotation(Overwrite.class));
        assertTrue(Modifier.isPrivate(relight.getModifiers()));
    }
}
