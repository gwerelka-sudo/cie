package com.cie.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Настройки звука фидбека мода: 4 категории — error/warn/get/success.
 * У каждой — включён ли звук (enabled) и какой именно (soundId, обычная
 * ванильная строка вида "minecraft:entity.experience_orb.pickup").
 * Хранится в .minecraft/cie/sounds.json.
 */
public final class SoundSettingsUtil {

    private SoundSettingsUtil() {
    }

    public enum Category {
        ERROR("error"), WARN("warn"), GET("get"), SUCCESS("success");

        public final String key;

        Category(String key) {
            this.key = key;
        }
    }

    public record SoundSetting(boolean enabled, String soundId) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Category, SoundSetting> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(Category.SUCCESS, new SoundSetting(true, "minecraft:entity.experience_orb.pickup"));
        DEFAULTS.put(Category.ERROR, new SoundSetting(true, "minecraft:entity.villager.no"));
        DEFAULTS.put(Category.WARN, new SoundSetting(true, "minecraft:block.note_block.bass"));
        DEFAULTS.put(Category.GET, new SoundSetting(false, "minecraft:ui.button.click"));
    }

    private static Map<Category, SoundSetting> cache;

    private static Path file() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cie");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir.resolve("sounds.json");
    }

    private static Map<Category, SoundSetting> load() {
        if (cache != null) {
            return cache;
        }
        Map<Category, SoundSetting> result = new LinkedHashMap<>(DEFAULTS);
        Path file = file();
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                if (root != null) {
                    for (Category category : Category.values()) {
                        if (root.has(category.key)) {
                            JsonObject obj = root.getAsJsonObject(category.key);
                            boolean enabled = obj.has("enabled") && obj.get("enabled").getAsBoolean();
                            String soundId = obj.has("sound") ? obj.get("sound").getAsString() : DEFAULTS.get(category).soundId();
                            result.put(category, new SoundSetting(enabled, soundId));
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        cache = result;
        return result;
    }

    private static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<Category, SoundSetting> entry : load().entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", entry.getValue().enabled());
            obj.addProperty("sound", entry.getValue().soundId());
            root.add(entry.getKey().key, obj);
        }
        try {
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static SoundSetting get(Category category) {
        return load().getOrDefault(category, DEFAULTS.get(category));
    }

    public static void setEnabled(Category category, boolean enabled) {
        Map<Category, SoundSetting> map = load();
        SoundSetting current = map.getOrDefault(category, DEFAULTS.get(category));
        map.put(category, new SoundSetting(enabled, current.soundId()));
        save();
    }

    /** Устанавливает конкретный звук — заодно неявно включает категорию. */
    public static void setSound(Category category, String soundId) {
        Map<Category, SoundSetting> map = load();
        map.put(category, new SoundSetting(true, soundId));
        save();
    }

    public static Category parseCategory(String name) {
        for (Category c : Category.values()) {
            if (c.key.equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }
}