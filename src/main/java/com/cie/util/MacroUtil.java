package com.cie.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Хранилище записей /cie macro: каждый сохранённый макрос — отдельный
 * файл .minecraft/cie/macros/<name>.json вида {"commands": ["...", ...]}
 * (список тех же сырых командных строк, что копит CommandHistoryUtil во
 * время записи — их можно снова скормить тому же CommandDispatcher).
 *
 * Имя при /cie macro stop генерируется автоматически (macro_1, macro_2,
 * ...) — сама команда stop, по спеке задания, имени не принимает.
 */
public final class MacroUtil {

    private MacroUtil() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern INVALID_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    private static Path dir() {
        Path d = FabricLoader.getInstance().getGameDir().resolve("cie").resolve("macros");
        try {
            Files.createDirectories(d);
        } catch (IOException ignored) {
        }
        return d;
    }

    private static Path fileFor(String name) {
        return dir().resolve(name + ".json");
    }

    private static boolean validName(String name) {
        return name != null && !name.isBlank() && !INVALID_CHARS.matcher(name).find()
                && !name.equals(".") && !name.equals("..");
    }

    public static List<String> names() {
        try (Stream<Path> stream = Files.list(dir())) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public static boolean exists(String name) {
        return validName(name) && Files.isRegularFile(fileFor(name));
    }

    /** Находит первое свободное имя вида macro_N, сохраняет под ним и возвращает выбранное имя. */
    public static String saveAutoNamed(List<String> commands) throws IOException {
        int i = 1;
        String name;
        do {
            name = "macro_" + i;
            i++;
        } while (exists(name));
        save(name, commands);
        return name;
    }

    public static void save(String name, List<String> commands) throws IOException {
        if (!validName(name)) {
            throw new IOException("invalid_name");
        }
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String c : commands) {
            arr.add(c);
        }
        root.add("commands", arr);
        Files.writeString(fileFor(name), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    public static List<String> load(String name) throws IOException {
        if (!validName(name)) {
            throw new IOException("invalid_name");
        }
        Path file = fileFor(name);
        if (!Files.isRegularFile(file)) {
            throw new IOException("not_found");
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        JsonObject root;
        try {
            root = JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("invalid_json");
        }
        List<String> result = new ArrayList<>();
        if (root.has("commands") && root.get("commands").isJsonArray()) {
            for (var el : root.getAsJsonArray("commands")) {
                result.add(el.getAsString());
            }
        }
        return result;
    }

    public static boolean delete(String name) {
        if (!validName(name)) {
            return false;
        }
        try {
            return Files.deleteIfExists(fileFor(name));
        } catch (IOException e) {
            return false;
        }
    }

    public static int clear() {
        int removed = 0;
        for (String name : names()) {
            if (delete(name)) {
                removed++;
            }
        }
        return removed;
    }
}
