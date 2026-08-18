package com.cie.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * /cie template — снимок КОМПОНЕНТОВ предмета (не привязан к конкретному
 * item), который можно наложить на любой другой предмет в руке.
 *
 * "Сохраняются все компоненты с предмета" читаем так же, как и в фиксе
 * /cie export (см. ComponentDiffUtil): не весь смёрженный ComponentMap
 * (там всегда сидят и ванильные дефолты item'а-источника — max_stack_size,
 * rarity и т.д., которые при apply на другой предмет были бы мусором или
 * вредом), а только РЕАЛЬНО изменённые относительно дефолта записи —
 * явные OVERRIDE (значение отличается от дефолта источника) и явные
 * REMOVED (дефолтный компонент источника был снят). apply переносит
 * ровно это: OVERRIDE -> stack.set(type, value), REMOVED -> stack.remove(type).
 *
 * Хранилище: .minecraft/cie/templates/<имя>.json:
 * {
 *   "components": { "minecraft:custom_name": <закодированное значение>, ... },
 *   "removed": ["minecraft:food", ...]
 * }
 */
public final class TemplateUtil {

    private TemplateUtil() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern INVALID_NAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\s]");

    private static Path templatesDir() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cie").resolve("templates");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    private static Path templateFile(String name) {
        return templatesDir().resolve(name + ".json");
    }

    public static List<String> list() {
        List<String> result = new ArrayList<>();
        Path dir = templatesDir();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> {
                        String fileName = p.getFileName().toString();
                        return fileName.substring(0, fileName.length() - ".json".length());
                    })
                    .sorted()
                    .forEach(result::add);
        } catch (IOException ignored) {
        }
        return result;
    }

    public static boolean exists(String name) {
        return Files.exists(templateFile(name));
    }

    /**
     * Сохраняет (перезаписывает) шаблон под именем name из реального диффа
     * компонентов стека относительно дефолта его item'а. Кидает
     * IllegalArgumentException("invalid_name") при кривом имени.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void create(String name, ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        if (name.isBlank() || INVALID_NAME_CHARS.matcher(name).find()) {
            throw new IllegalArgumentException("invalid_name");
        }

        RegistryOps<JsonElement> ops = registries.getOps(JsonOps.INSTANCE);
        JsonObject componentsJson = new JsonObject();
        JsonArray removedJson = new JsonArray();

        for (ComponentDiffUtil.Entry entry : ComponentDiffUtil.diffFromDefault(stack)) {
            ComponentType<?> type = entry.type();
            Identifier typeId = Registries.DATA_COMPONENT_TYPE.getId((ComponentType) type);
            if (typeId == null) continue;

            if (entry.kind() == ComponentDiffUtil.Kind.REMOVED) {
                removedJson.add(typeId.toString());
                continue;
            }

            Codec codec = type.getCodec();
            if (codec == null) continue;
            try {
                DataResult<JsonElement> result = codec.encodeStart(ops, entry.value());
                componentsJson.add(typeId.toString(), result.getOrThrow());
            } catch (Exception ignored) {
                // Компонент без честного кодека/с ошибкой сериализации — пропускаем именно его,
                // не роняем сохранение всего шаблона.
            }
        }

        JsonObject root = new JsonObject();
        root.add("components", componentsJson);
        root.add("removed", removedJson);

        try {
            Files.writeString(templateFile(name), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("io_error");
        }
    }

    /** Результат apply(): сколько компонентов реально выставлено/снято. */
    public record ApplyResult(int set, int removed) {
    }

    /**
     * Накладывает сохранённый шаблон на stack (мутирует его in-place).
     * Возвращает null, если шаблона с таким именем нет.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ApplyResult apply(String name, ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        Path file = templateFile(name);
        if (!Files.exists(file)) {
            return null;
        }

        JsonObject root;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            root = GSON.fromJson(json, JsonObject.class);
        } catch (IOException | JsonSyntaxException e) {
            return null;
        }
        if (root == null) {
            return new ApplyResult(0, 0);
        }

        RegistryOps<JsonElement> ops = registries.getOps(JsonOps.INSTANCE);
        int set = 0;
        int removed = 0;

        if (root.has("components")) {
            for (var entry : root.getAsJsonObject("components").entrySet()) {
                Identifier typeId = Identifier.tryParse(entry.getKey());
                if (typeId == null) continue;
                ComponentType type = Registries.DATA_COMPONENT_TYPE.get(typeId);
                if (type == null) continue;
                Codec codec = type.getCodec();
                if (codec == null) continue;
                try {
                    Object value = codec.parse(ops, entry.getValue()).getOrThrow();
                    stack.set(type, value);
                    set++;
                } catch (Exception ignored) {
                    // Не смогли восстановить конкретный компонент (например, шаблон делали
                    // на другой версии мода/игры) — пропускаем именно его.
                }
            }
        }

        if (root.has("removed")) {
            for (JsonElement el : root.getAsJsonArray("removed")) {
                Identifier typeId = Identifier.tryParse(el.getAsString());
                if (typeId == null) continue;
                ComponentType<?> type = Registries.DATA_COMPONENT_TYPE.get(typeId);
                if (type == null) continue;
                if (stack.contains(type)) {
                    stack.remove(type);
                    removed++;
                }
            }
        }

        return new ApplyResult(set, removed);
    }

    public static boolean remove(String name) {
        try {
            return Files.deleteIfExists(templateFile(name));
        } catch (IOException e) {
            return false;
        }
    }

    /** Удаляет ВСЕ шаблоны. Возвращает, сколько было удалено. */
    public static int clear() {
        int count = 0;
        for (String name : list()) {
            if (remove(name)) count++;
        }
        return count;
    }
}