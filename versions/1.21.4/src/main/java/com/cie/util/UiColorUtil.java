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
 * Менеджер покраски элементов GUI (фонов, рамок и т.п.) — общий на весь
 * мод, не привязан к конкретному экрану. Каждый закрашиваемый кусок
 * (текстурка) идентифицируется строковым ключом вида
 * "colorPicker.panelBackground", "armorStandMenu.titleBar" и т.д. —
 * называйте как удобно, единственное правило: уникально в рамках мода.
 *
 * СЕМАНТИКА:
 *  - registerDefault(key, argb) — вызывается ОДИН РАЗ из кода экрана
 *    (например в static-блоке или конструкторе виджета), задаёт цвет
 *    "из кода", на который откатывает reset(). Если экран вызывает это
 *    при каждой инициализации — не страшно, повторная регистрация того
 *    же ключа просто перезаписывает дефолт (override пользователя это
 *    не трогает).
 *  - set(key, argb) — покрасить руками (например через будущую команду
 *    /cie colorPicker paint <key> <argb> или armorStandMenu). Значение
 *    персистится на диск.
 *  - get(key) — вернуть текущий цвет: override, если есть, иначе дефолт
 *    из кода, иначе непрозрачный белый (0xFFFFFFFF) как крайний фолбэк.
 *  - reset(key) — убрать override, вернуться к дефолту из кода.
 *  - resetAll() — сбросить вообще все ключи разом.
 *
 * Цвет — обычный Minecraft ARGB int (как везде в DrawContext.fill), но
 * есть хелперы rgba(r,g,b,a) для сборки из отдельных RGBA-компонент и
 * red()/green()/blue()/alpha() для разборки обратно.
 *
 * Хранится в .minecraft/cie/ui_colors.json, по той же схеме, что
 * ColorPickerDataUtil/ColoringConfigUtil — файл создаётся только при
 * первом реальном set().
 */
public final class UiColorUtil {

    private UiColorUtil() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Ручные переопределения пользователя — то, что реально хранится на диске. */
    private static final Map<String, Integer> overrides = new LinkedHashMap<>();

    /** Дефолты "из кода" — НЕ персистятся, регистрируются заново при каждом запуске игры. */
    private static final Map<String, Integer> defaults = new LinkedHashMap<>();

    private static final int FALLBACK = 0xFFFFFFFF;

    private static boolean loaded = false;

    // ============================================================
    //  set / get / reset
    // ============================================================

    /** Регистрирует цвет "из кода" для ключа. Вызывать из static-блока/конструктора экрана. */
    public static void registerDefault(String key, int argb) {
        defaults.put(key, argb);
    }

    /** Текущий цвет: override -> дефолт из кода -> белый непрозрачный. */
    public static int get(String key) {
        ensureLoaded();
        Integer override = overrides.get(key);
        if (override != null) return override;
        Integer def = defaults.get(key);
        return def != null ? def : FALLBACK;
    }

    /** Установить ручной цвет — персистится на диск немедленно. */
    public static void set(String key, int argb) {
        ensureLoaded();
        overrides.put(key, argb);
        save();
    }

    /** Убрать override для ключа — вернуться к цвету из кода. */
    public static void reset(String key) {
        ensureLoaded();
        if (overrides.remove(key) != null) {
            save();
        }
    }

    /** Сбросить ВСЕ override'ы разом. */
    public static void resetAll() {
        ensureLoaded();
        if (!overrides.isEmpty()) {
            overrides.clear();
            save();
        }
    }

    public static boolean isOverridden(String key) {
        ensureLoaded();
        return overrides.containsKey(key);
    }

    /** Все ключи, у которых сейчас есть и override, и дефолт (удобно для UI-списка покраски). */
    public static Iterable<String> knownKeys() {
        return defaults.keySet();
    }

    // ============================================================
    //  RGBA хелперы
    // ============================================================

    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int alpha(int argb) {
        return (argb >> 24) & 0xFF;
    }

    // ============================================================
    //  Персистентность
    // ============================================================

    private static Path filePath() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cie");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir.resolve("ui_colors.json");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Path path = filePath();
        if (!Files.exists(path)) return;

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;

            for (String key : root.keySet()) {
                try {
                    // храним как hex-строку "AARRGGBB", чтобы файл был читаем глазами
                    long argbLong = Long.parseLong(root.get(key).getAsString(), 16);
                    overrides.put(key, (int) argbLong);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception e) {
            System.err.println("[CIE] Ошибка загрузки ui_colors.json:");
            e.printStackTrace();
        }
    }

    private static synchronized void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Integer> e : overrides.entrySet()) {
            root.addProperty(e.getKey(), String.format("%08X", e.getValue()));
        }
        try {
            Files.writeString(filePath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[CIE] Ошибка сохранения ui_colors.json:");
            e.printStackTrace();
        }
    }
}
