package com.yessteveattribute.forge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DamageResourcesTest {
    private static JsonObject read(String path) throws Exception {
        try (var input = DamageResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void calculatedDamageOnlyBypassesArmorAndNeverDifficultyScales() throws Exception {
        var damage = read("/data/yessteveattribute/damage_type/calculated.json");
        assertEquals("never", damage.get("scaling").getAsString());
        var armor = read("/data/minecraft/tags/damage_type/bypasses_armor.json");
        assertFalse(armor.get("replace").getAsBoolean());
        assertEquals("yessteveattribute:calculated", armor.getAsJsonArray("values").get(0).getAsString());
        try (var tags = Files.list(Path.of("src/main/resources/data/minecraft/tags/damage_type"))) {
            assertEquals(java.util.List.of("bypasses_armor.json"), tags.map(p -> p.getFileName().toString()).sorted().toList());
        }
        for (String locale : new String[]{"zh_cn", "en_us"}) {
            var language = read("/assets/yessteveattribute/lang/" + locale + ".json");
            String key = "death.attack." + damage.get("message_id").getAsString();
            assertTrue(language.has(key));
            assertTrue(language.has(key + ".player"));
            assertTrue(language.has(key + ".item"));
        }
    }
}
