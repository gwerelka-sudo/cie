package com.cie.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Пресеты списков hex-цветов для /cie gradient create ... — вместо
 * ручного ввода "hexs" каждый раз можно один раз сохранить список под
 * именем и потом всюду, где ожидается hexs, подставлять его через
 * "$имя" (см. GRADIENT_PRESET_SUGGESTIONS / resolveHexs в CIECommand).
 *
 * Хранилище: .minecraft/cie/formatting/presets/presets.json,
 * {"имя": "AABBCC,DDEEFF,..."} — та же схема хранения, что и у
 * кастомных форматов (GradientFormatUtil), только значение — не
 * триплет prefix/suffix/separator, а нормализованная CSV-строка
 * hex-цветов (без '#', верхний регистр — GradientColorUtil.normalizeHex).
 */
public final class GradientPresetUtil {

    private GradientPresetUtil() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** '$' и ',' тоже запрещены в имени — '$' используется как маркер пресета в hexs-аргументе, ',' ломает CSV-парсинг hexs. */
    private static final Pattern INVALID_NAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\s$,]");

    private static Path presetsFile() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cie").resolve("formatting").resolve("presets");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir.resolve("presets.json");
    }

    private static Map<String, String> loadPresets() {
        Path file = presetsFile();
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            Map<String, String> result = new LinkedHashMap<>();
            if (root != null) {
                for (String key : root.keySet()) {
                    result.put(key, root.get(key).getAsString());
                }
            }
            return result;
        } catch (IOException | JsonSyntaxException e) {
            return new LinkedHashMap<>();
        }
    }

    private static void savePresets(Map<String, String> presets) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, String> entry : presets.entrySet()) {
            root.addProperty(entry.getKey(), entry.getValue());
        }
        try {
            Files.writeString(presetsFile(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static List<String> list() {
        return new ArrayList<>(loadPresets().keySet());
    }

    public static boolean exists(String name) {
        return loadPresets().containsKey(name);
    }

    /** Нормализованный (uppercase, без '#') список hex-цветов пресета, либо null, если пресета с таким именем нет. */
    public static List<String> get(String name) {
        String csv = loadPresets().get(name);
        if (csv == null) {
            return null;
        }
        return GradientColorUtil.parseHexList(csv);
    }

    /**
     * Создаёт/перезаписывает пресет. Кидает IllegalArgumentException с
     * сообщением "invalid_name" (кривое имя) или самим кривым hex-токеном
     * (см. GradientColorUtil.normalizeHex) при некорректном списке цветов —
     * в обоих случаях ничего не сохраняется.
     */
    public static void create(String name, String hexsCsv) {
        if (name.isBlank() || INVALID_NAME_CHARS.matcher(name).find()) {
            throw new IllegalArgumentException("invalid_name");
        }
        List<String> normalized = GradientColorUtil.parseHexList(hexsCsv);
        Map<String, String> presets = loadPresets();
        presets.put(name, String.join(",", normalized));
        savePresets(presets);
    }

    public static boolean remove(String name) {
        Map<String, String> presets = loadPresets();
        boolean removed = presets.remove(name) != null;
        if (removed) {
            savePresets(presets);
        }
        return removed;
    }

    /** Удаляет ВСЕ пресеты. Возвращает, сколько было удалено. */
    public static int clear() {
        Map<String, String> presets = loadPresets();
        int count = presets.size();
        if (count > 0) {
            savePresets(new LinkedHashMap<>());
        }
        return count;
    }
}
