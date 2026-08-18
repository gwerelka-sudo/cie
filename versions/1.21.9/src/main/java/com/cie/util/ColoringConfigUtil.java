package com.cie.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Настраиваемые цвета подсветки структурированного вывода (JSON / компоненты
 * /give ...). Хранится в .minecraft/cie/coloring.json, по умолчанию не создаётся
 * (пока пользователь не поменяет хотя бы один цвет) — до этого используются
 * DEFAULTS в памяти.
 */
public final class ColoringConfigUtil {

    public enum Slot {
        KEY,
        VALUE,
        COUNT,
        BRACKET
    }

    private static final Map<Slot, Integer> DEFAULTS = new EnumMap<>(Slot.class);

    static {
        DEFAULTS.put(Slot.KEY, 0x81BEF7);
        DEFAULTS.put(Slot.VALUE, 0x81F7AA);
        DEFAULTS.put(Slot.COUNT, 0xF78181);
        DEFAULTS.put(Slot.BRACKET, 0xFFFFFF);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Slot, Integer> COLORS = new EnumMap<>(DEFAULTS);
    private static boolean loaded = false;

    private ColoringConfigUtil() {
    }

    private static Path configPath() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("cie").resolve("coloring.json");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Path path = configPath();
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<?, ?> raw = GSON.fromJson(reader, Map.class);
            if (raw == null) return;

            for (Slot slot : Slot.values()) {
                Object value = raw.get(slot.name().toLowerCase(Locale.ROOT));
                if (value instanceof String hex) {
                    try {
                        COLORS.put(slot, Integer.parseInt(hex.replace("#", ""), 16));
                    } catch (NumberFormatException ignored) {
                        // оставляем значение по умолчанию для этого слота
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CIE] Ошибка загрузки coloring.json:");
            e.printStackTrace();
        }
    }

    public static int get(Slot slot) {
        ensureLoaded();
        return COLORS.getOrDefault(slot, DEFAULTS.get(slot));
    }

    public static void set(Slot slot, int rgb) {
        ensureLoaded();
        COLORS.put(slot, rgb);
        save();
    }

    public static void reset(Slot slot) {
        ensureLoaded();
        COLORS.put(slot, DEFAULTS.get(slot));
        save();
    }

    public static int getDefault(Slot slot) {
        return DEFAULTS.get(slot);
    }

    /** Принудительно перечитывает coloring.json с диска, отбрасывая всё, что было в памяти. */
    public static synchronized void reload() {
        loaded = false;
        COLORS.clear();
        COLORS.putAll(DEFAULTS);
        ensureLoaded();
    }

    private static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());

            Map<String, String> out = new LinkedHashMap<>();
            for (Slot slot : Slot.values()) {
                out.put(slot.name().toLowerCase(Locale.ROOT),
                        String.format("#%06X", COLORS.getOrDefault(slot, DEFAULTS.get(slot))));
            }

            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(out, writer);
            }
        } catch (IOException e) {
            System.err.println("[CIE] Ошибка записи coloring.json:");
            e.printStackTrace();
        }
    }
}