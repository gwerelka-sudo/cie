package com.cie.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Хранилище состояния color picker'а между открытиями экрана:
 *  - 20 сохранённых свотчей (палитра-пресеты, сетка 5x4, раскрывается вниз),
 *  - вкл/выкл палитры (кнопка "глаз"),
 *  - стопы кастомного градиента снизу (position 0..1 + hex),
 *  - последний использованный формат хекс-поля.
 *
 * Хранится в .minecraft/cie/colorpicker.json. Файл создаётся только
 * при первом реальном изменении (по аналогии с ColoringConfigUtil).
 */
public final class ColorPickerDataUtil {

    public record GradientStop(float position, String hex) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final int PALETTE_SIZE = 20;
    private static final String[] palette = new String[PALETTE_SIZE]; // null = пустой слот
    private static boolean paletteEnabled = true;
    private static final List<GradientStop> gradientStops = new ArrayList<>();
    private static String lastFormat = "legacy";
    private static int widgetX = -1;
    private static int widgetY = -1;
    private static boolean widgetOpen = false;

    // Позиция кнопки-переключателя над строкой чата. -1 = ещё не задавалась
    // пользователем вручную (используется дефолтная позиция у поля ввода).
    private static int buttonX = -1;
    private static int buttonY = -1;

    private static boolean loaded = false;

    private ColorPickerDataUtil() {
    }

    private static Path filePath() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cie");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir.resolve("colorpicker.json");
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

            if (root.has("paletteEnabled")) {
                paletteEnabled = root.get("paletteEnabled").getAsBoolean();
            }
            if (root.has("lastFormat")) {
                lastFormat = root.get("lastFormat").getAsString();
            }
            if (root.has("widgetX")) widgetX = root.get("widgetX").getAsInt();
            if (root.has("widgetY")) widgetY = root.get("widgetY").getAsInt();
            if (root.has("widgetOpen")) widgetOpen = root.get("widgetOpen").getAsBoolean();
            if (root.has("buttonX")) buttonX = root.get("buttonX").getAsInt();
            if (root.has("buttonY")) buttonY = root.get("buttonY").getAsInt();
            if (root.has("palette")) {
                JsonArray arr = root.getAsJsonArray("palette");
                for (int i = 0; i < arr.size() && i < palette.length; i++) {
                    palette[i] = arr.get(i).isJsonNull() ? null : arr.get(i).getAsString();
                }
            }
            if (root.has("gradientStops")) {
                gradientStops.clear();
                for (var el : root.getAsJsonArray("gradientStops")) {
                    JsonObject obj = el.getAsJsonObject();
                    gradientStops.add(new GradientStop(
                            obj.get("position").getAsFloat(),
                            obj.get("hex").getAsString()));
                }
            }
        } catch (Exception e) {
            System.err.println("[CIE] Ошибка загрузки colorpicker.json:");
            e.printStackTrace();
        }
    }

    private static synchronized void save() {
        JsonObject root = new JsonObject();
        root.addProperty("paletteEnabled", paletteEnabled);
        root.addProperty("lastFormat", lastFormat);
        root.addProperty("widgetX", widgetX);
        root.addProperty("widgetY", widgetY);
        root.addProperty("widgetOpen", widgetOpen);
        root.addProperty("buttonX", buttonX);
        root.addProperty("buttonY", buttonY);

        JsonArray paletteArr = new JsonArray();
        for (String hex : palette) {
            if (hex == null) paletteArr.add((String) null);
            else paletteArr.add(hex);
        }
        root.add("palette", paletteArr);

        JsonArray stopsArr = new JsonArray();
        for (GradientStop stop : gradientStops) {
            JsonObject obj = new JsonObject();
            obj.addProperty("position", stop.position());
            obj.addProperty("hex", stop.hex());
            stopsArr.add(obj);
        }
        root.add("gradientStops", stopsArr);

        try {
            Files.writeString(filePath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[CIE] Ошибка сохранения colorpicker.json:");
            e.printStackTrace();
        }
    }

    // ============================================================
    //  Палитра (20 слотов, сетка 5x4)
    // ============================================================

    public static String[] getPalette() {
        ensureLoaded();
        return palette.clone();
    }

    public static void setPaletteSlot(int index, String hexUpperNoHash) {
        ensureLoaded();
        if (index < 0 || index >= palette.length) return;
        palette[index] = hexUpperNoHash;
        save();
    }

    public static boolean isPaletteEnabled() {
        ensureLoaded();
        return paletteEnabled;
    }

    public static void setPaletteEnabled(boolean enabled) {
        ensureLoaded();
        paletteEnabled = enabled;
        save();
    }

    // ============================================================
    //  Стопы градиента
    // ============================================================

    public static List<GradientStop> getGradientStops() {
        ensureLoaded();
        return new ArrayList<>(gradientStops);
    }

    public static void setGradientStops(List<GradientStop> stops) {
        ensureLoaded();
        gradientStops.clear();
        gradientStops.addAll(stops);
        save();
    }

    // ============================================================
    //  Формат хекс-поля
    // ============================================================

    public static String getLastFormat() {
        ensureLoaded();
        return lastFormat;
    }

    public static void setLastFormat(String format) {
        ensureLoaded();
        lastFormat = format;
        save();
    }

    // ============================================================
    //  Позиция / открытость виджета
    // ============================================================

    public static int getWidgetX() {
        ensureLoaded();
        return widgetX;
    }

    public static int getWidgetY() {
        ensureLoaded();
        return widgetY;
    }

    public static void setWidgetPosition(int x, int y) {
        ensureLoaded();
        widgetX = x;
        widgetY = y;
        save();
    }

    public static boolean isWidgetOpen() {
        ensureLoaded();
        return widgetOpen;
    }

    public static void setWidgetOpen(boolean open) {
        ensureLoaded();
        widgetOpen = open;
        save();
    }

    // ============================================================
    //  Позиция кнопки-переключателя над строкой чата
    // ============================================================

    public static int getButtonX() {
        ensureLoaded();
        return buttonX;
    }

    public static int getButtonY() {
        ensureLoaded();
        return buttonY;
    }

    public static void setButtonPosition(int x, int y) {
        ensureLoaded();
        buttonX = x;
        buttonY = y;
        save();
    }
}