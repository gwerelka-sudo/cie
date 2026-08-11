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
 * Утилита форматов /cie gradient — превращает hex-цвет (6 символов,
 * БЕЗ '#') в текстовое представление, применяемое перед (или вокруг,
 * для bbcode) конкретным символом градиента.
 *
 * Встроенные форматы (BUILTIN_FORMATS) захардкожены и не могут быть
 * удалены/изменены. Кастомные форматы хранятся в
 * .minecraft/cie/formatting/custom/formats.json как
 * {"имя": {"prefix":"...", "suffix":"...", "separator":"..."}}
 * и применяются только к 6 hex-цифрам цвета (соединённым separator,
 * обёрнутым prefix/suffix) — символ добавляется сразу после, без обёртки
 * (в отличие от bbcode, который оборачивает и символ тоже).
 */
public final class GradientFormatUtil {

    private GradientFormatUtil() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern INVALID_NAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\s]");

    public static final List<String> BUILTIN_FORMATS = List.of(
            "legacy", "legacy&", "json", "minimessage", "old", "bbcode", "motd", "xml");

    public record CustomFormat(String prefix, String suffix, String separator) {
    }

    // ============================================================
    //  Хранилище кастомных форматов
    // ============================================================

    private static Path formatsFile() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cie").resolve("formatting").resolve("custom");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir.resolve("formats.json");
    }

    private static Map<String, CustomFormat> loadCustomFormats() {
        Path file = formatsFile();
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            Map<String, CustomFormat> result = new LinkedHashMap<>();
            if (root != null) {
                for (String key : root.keySet()) {
                    JsonObject obj = root.getAsJsonObject(key);
                    String prefix = obj.has("prefix") ? obj.get("prefix").getAsString() : "";
                    String suffix = obj.has("suffix") ? obj.get("suffix").getAsString() : "";
                    String separator = obj.has("separator") ? obj.get("separator").getAsString() : "";
                    result.put(key, new CustomFormat(prefix, suffix, separator));
                }
            }
            return result;
        } catch (IOException | JsonSyntaxException e) {
            return new LinkedHashMap<>();
        }
    }

    private static void saveCustomFormats(Map<String, CustomFormat> formats) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, CustomFormat> entry : formats.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("prefix", entry.getValue().prefix());
            obj.addProperty("suffix", entry.getValue().suffix());
            obj.addProperty("separator", entry.getValue().separator());
            root.add(entry.getKey(), obj);
        }
        try {
            Files.writeString(formatsFile(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static boolean isBuiltin(String format) {
        return BUILTIN_FORMATS.contains(format);
    }

    public static boolean exists(String format) {
        return isBuiltin(format) || loadCustomFormats().containsKey(format);
    }

    public static List<String> listAllFormats() {
        List<String> result = new ArrayList<>(BUILTIN_FORMATS);
        result.addAll(loadCustomFormats().keySet());
        return result;
    }

    public static List<String> listCustomFormats() {
        return new ArrayList<>(loadCustomFormats().keySet());
    }

    public static CustomFormat getCustomFormat(String name) {
        return loadCustomFormats().get(name);
    }

    public static void createCustomFormat(String name, String prefix, String suffix, String separator) {
        if (isBuiltin(name)) {
            throw new IllegalArgumentException("builtin_conflict");
        }
        if (name.isBlank() || INVALID_NAME_CHARS.matcher(name).find()) {
            throw new IllegalArgumentException("invalid_name");
        }
        Map<String, CustomFormat> formats = loadCustomFormats();
        formats.put(name, new CustomFormat(prefix, suffix, separator == null ? "" : separator));
        saveCustomFormats(formats);
    }

    /** Полностью удаляет кастомный формат (имя больше не существует). */
    public static boolean removeCustomFormat(String name) {
        Map<String, CustomFormat> formats = loadCustomFormats();
        boolean removed = formats.remove(name) != null;
        if (removed) {
            saveCustomFormats(formats);
        }
        return removed;
    }

    /** Сбрасывает prefix/suffix/separator формата в пустые строки, НЕ удаляя сам формат (в отличие от remove). */
    public static boolean clearCustomFormat(String name) {
        Map<String, CustomFormat> formats = loadCustomFormats();
        if (!formats.containsKey(name)) {
            return false;
        }
        formats.put(name, new CustomFormat("", "", ""));
        saveCustomFormats(formats);
        return true;
    }

    // ============================================================
    //  Рендеринг
    // ============================================================

    /**
     * Настоящий валидный JSON для формата "json" — в отличие от остальных
     * форматов, его нельзя собрать конкатенацией "префикс+символ" по одному
     * символу (получится не-JSON мешанина из объектов вперемешку с сырыми
     * символами). Строим ОДИН JSON-массив text-компонентов сразу для всего
     * градиента: [{"text":"a","color":"#RRGGBB"}, ...] — валидный формат
     * текста Minecraft (эквивалентен списку siblings).
     */
    public static String renderJsonArray(List<GradientColorUtil.CharColor> chars) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (GradientColorUtil.CharColor cc : chars) {
            JsonObject obj = new JsonObject();
            obj.addProperty("text", String.valueOf(cc.character()));
            obj.addProperty("color", "#" + cc.hexUpper());
            array.add(obj);
        }
        return array.toString();
    }

    /** hexUpper — ровно 6 hex-символов, без '#'. Регистр самого hex внутри вывода приводится к нижнему (как в примерах ТЗ: &x&f&f...). */
    public static String render(String format, String hexUpper, char character) {
        switch (format) {
            case "legacy":
                return "#" + hexUpper + character;
            case "legacy&":
                return "&#" + hexUpper + character;
            case "json":
                return "{\"color\":\"#" + hexUpper + "\"}" + character;
            case "minimessage":
                return "<#" + hexUpper + ">" + character;
            case "old":
                return renderOld(hexUpper) + character;
            case "bbcode":
                return "[COLOR=#" + hexUpper + "]" + character + "[/COLOR]";
            case "motd":
                return renderMotd(hexUpper) + character;
            case "xml":
                return "<color:#" + hexUpper + ">" + character;
            default:
                CustomFormat custom = getCustomFormat(format);
                if (custom == null) {
                    throw new IllegalArgumentException("unknown_format");
                }
                return renderCustom(custom, hexUpper) + character;
        }
    }

    private static String renderOld(String hex) {
        StringBuilder sb = new StringBuilder("&x");
        for (char c : hex.toCharArray()) {
            sb.append('&').append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String renderMotd(String hex) {
        StringBuilder sb = new StringBuilder("\u00a7x");
        for (char c : hex.toCharArray()) {
            sb.append('\u00a7').append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String renderCustom(CustomFormat format, String hex) {
        String sep = format.separator() == null ? "" : format.separator();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < hex.length(); i++) {
            if (i > 0) digits.append(sep);
            digits.append(Character.toLowerCase(hex.charAt(i)));
        }
        return format.prefix() + digits + format.suffix();
    }
}