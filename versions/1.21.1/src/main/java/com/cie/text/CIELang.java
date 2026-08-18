package com.cie.text;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Локализация мода. С этой версии языковые файлы больше НЕ зашиты в
 * jar-ресурсы (было /assets/cie/strings.json) — они лежат на диске, в
 * .minecraft/cie/languages/&lt;lang&gt;.json, и их можно редактировать
 * или добавлять свои: мод подхватывает любой *.json из этой папки как
 * язык (см. {@link #listLanguages()}).
 *
 * При первом запуске (если файлов ещё нет на диске) стандартные
 * ru_ru.json и en_us.json копируются туда из jar-ресурсов мода
 * (/assets/cie/lang/ru_ru.json, /assets/cie/lang/en_us.json) — это
 * единственный момент, когда языковые строки вообще читаются из jar.
 * Уже существующие на диске файлы НИКОГДА не перезаписываются автоматом
 * (в т.ч. если пользователь их отредактировал) — seed срабатывает только
 * при полном отсутствии файла.
 *
 * Текущий выбранный язык хранится отдельным файлом
 * cie/languages/current.txt (просто имя языка текстом, без JSON) — этот
 * файл НЕ .json, поэтому не попадает в список доступных языков.
 */
public final class CIELang {

    private static final Map<String, String> STRINGS = new HashMap<>();
    private static final String DEFAULT_LANGUAGE = "en_us";
    private static String currentLanguage = DEFAULT_LANGUAGE;

    private CIELang() {
    }

    private static Path languagesDir() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cie").resolve("languages");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    private static Path currentLanguageFile() {
        return languagesDir().resolve("current.txt");
    }

    /** Копирует ru_ru.json/en_us.json из jar в cie/languages/, если их там ещё нет. */
    private static void seedDefaultLanguagesIfMissing() {
        for (String lang : new String[]{"ru_ru", "en_us"}) {
            Path target = languagesDir().resolve(lang + ".json");
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream stream = CIELang.class.getResourceAsStream("/assets/cie/lang/" + lang + ".json")) {
                if (stream == null) {
                    System.err.println("[CIE] Не найден дефолтный языковой ресурс в jar: /assets/cie/lang/" + lang + ".json");
                    continue;
                }
                Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("[CIE] Не удалось скопировать дефолтный язык " + lang + ":");
                e.printStackTrace();
            }
        }
    }

    private static String readCurrentLanguageName() {
        Path file = currentLanguageFile();
        if (Files.exists(file)) {
            try {
                String value = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            } catch (IOException ignored) {
            }
        }
        return DEFAULT_LANGUAGE;
    }

    private static void writeCurrentLanguageName(String lang) {
        try {
            Files.writeString(currentLanguageFile(), lang, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[CIE] Не удалось сохранить текущий язык:");
            e.printStackTrace();
        }
    }

    public static void load() {
        seedDefaultLanguagesIfMissing();
        currentLanguage = readCurrentLanguageName();
        loadLanguageFile(currentLanguage);
    }

    private static void loadLanguageFile(String lang) {
        STRINGS.clear();
        Path file = languagesDir().resolve(lang + ".json");
        if (!Files.exists(file)) {
            System.err.println("[CIE] Языковой файл не найден: " + file + " — используется " + DEFAULT_LANGUAGE);
            if (!lang.equals(DEFAULT_LANGUAGE)) {
                loadLanguageFile(DEFAULT_LANGUAGE);
            }
            return;
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (String key : json.keySet()) {
                STRINGS.put(key, json.get(key).getAsString());
            }
            System.out.println("[CIE] Загружено " + STRINGS.size() + " строк локализации (" + lang + ").");
        } catch (Exception e) {
            System.err.println("[CIE] Ошибка загрузки языкового файла " + file + ":");
            e.printStackTrace();
        }
    }

    public static String getCurrentLanguage() {
        return currentLanguage;
    }

    /** Список доступных языков — все *.json в cie/languages/ (без расширения, без current.txt). */
    public static List<String> listLanguages() {
        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(languagesDir())) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        result.add(name.substring(0, name.length() - ".json".length()));
                    });
        } catch (IOException ignored) {
        }
        result.sort(String::compareTo);
        return result;
    }

    public static boolean languageExists(String lang) {
        return Files.exists(languagesDir().resolve(lang + ".json"));
    }

    /**
     * Создаёт новый язык &lt;name&gt;.json, взяв за основу содержимое en_us.json
     * (копия всех ключей — дальше пользователь правит файл вручную под свой
     * язык). Возвращает false, если такой язык уже существует, имя пустое,
     * или en_us.json (база) почему-то отсутствует.
     */
    public static boolean createLanguage(String name) {
        if (name == null || name.isBlank() || languageExists(name)) {
            return false;
        }
        Path base = languagesDir().resolve(DEFAULT_LANGUAGE + ".json");
        if (!Files.exists(base)) {
            return false;
        }
        try {
            Files.copy(base, languagesDir().resolve(name + ".json"));
            return true;
        } catch (IOException e) {
            System.err.println("[CIE] Не удалось создать язык " + name + ":");
            e.printStackTrace();
            return false;
        }
    }

    /** Переключает язык и перечитывает строки. Возвращает false, если такого языка (файла) нет на диске. */
    public static boolean setLanguage(String lang) {
        if (!languageExists(lang)) {
            return false;
        }
        writeCurrentLanguageName(lang);
        currentLanguage = lang;
        loadLanguageFile(lang);
        return true;
    }

    public static String get(String key) {
        return STRINGS.getOrDefault(key, "<red>Missing string: " + key);
    }

    /**
     * Подставляет позиционные плейсхолдеры вида {0}, {1}... в строку локализации.
     */
    public static String getFormatted(String key, Object... args) {
        String raw = get(key);
        if (args == null || args.length == 0) {
            return raw;
        }
        String result = raw;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }
}