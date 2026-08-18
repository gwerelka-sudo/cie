package com.cie.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.visitor.StringNbtWriter;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.cie.text.MiniMessageBridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Менеджер долговременного хранилища предметов: /cie storage ...
 *
 * Каждый предмет — отдельный файл .minecraft/cie/storage/<name>.json.
 * Формат файла — структурированный JSON:
 * {
 *   "material": "minecraft:diamond_sword",
 *   "name": "<MiniMessage строка кастомного имени или null>",
 *   "lore": ["строка1", "строка2", ...],
 *   "count": 1,
 *   "components": "<SNBT остальных компонентов, как в /rnm export>"
 * }
 *
 * Битые файлы (не парсится JSON, нет обязательных полей, материал не
 * существует в реестре, компоненты не парсятся StringNbtReader'ом) при
 * попытке чтения переносятся в .minecraft/cie/storage/corrupt/ — так,
 * чтобы одна проблемная запись не мешала работать с остальными.
 */
public final class StorageUtil {

    private StorageUtil() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern INVALID_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    /** Имена, ожидающие финального подтверждения /cie storage clear confirm. */
    private static final java.util.Set<String> PENDING_CLEAR_CONFIRMATION = new java.util.HashSet<>();

    public static final class InvalidNameException extends Exception {
        public InvalidNameException(String message) {
            super(message);
        }
    }

    public static final class CorruptEntryException extends Exception {
        public CorruptEntryException(String message) {
            super(message);
        }
    }

    // ============================================================
    //  Пути
    // ============================================================

    private static Path storageDir() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cie").resolve("storage");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            // Если папку создать не удалось — упадём чуть позже с понятной ошибкой при записи/чтении.
        }
        return dir;
    }

    private static Path corruptDir() {
        Path dir = storageDir().resolve("corrupt");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    private static void validateName(String name) throws InvalidNameException {
        if (name == null || name.isBlank()) {
            throw new InvalidNameException("empty");
        }
        if (INVALID_CHARS.matcher(name).find()) {
            throw new InvalidNameException(name);
        }
        if (name.equals(".") || name.equals("..")) {
            throw new InvalidNameException(name);
        }
    }

    private static Path fileFor(String name) {
        return storageDir().resolve(name + ".json");
    }

    // ============================================================
    //  Список / существование
    // ============================================================

    public static boolean exists(String name) {
        try {
            validateName(name);
        } catch (InvalidNameException e) {
            return false;
        }
        return Files.isRegularFile(fileFor(name));
    }

    public static List<String> names() {
        Path dir = storageDir();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    // ============================================================
    //  Перезагрузка — /cie storage reload
    // ============================================================

    public static final class ReloadResult {
        public final int validCount;
        public final List<String> quarantined;

        ReloadResult(int validCount, List<String> quarantined) {
            this.validCount = validCount;
            this.quarantined = quarantined;
        }
    }

    /**
     * Перечитывает содержимое storage/ с диска и заново проверяет каждый
     * файл на валидность (как при старте мода), перенося вновь найденные
     * битые записи в corrupt/. У names()/load()/info() и так нет кэша —
     * они каждый раз читают файлы напрямую, так что единственное, что
     * реально может "устареть" между запусками этого скана, — статус
     * corrupt-файлов, изменённых руками вне игры.
     */
    public static ReloadResult reload() {
        List<String> quarantined = scanAndQuarantineCorrupt();
        int validCount = names().size();
        return new ReloadResult(validCount, quarantined);
    }

    // ============================================================
    //  Сохранение (save)
    // ============================================================

    public static void save(String name, ItemStack stack, RegistryWrapper.WrapperLookup registries) throws InvalidNameException, IOException {
        validateName(name);

        JsonObject json = new JsonObject();
        json.addProperty("material", Registries.ITEM.getId(stack.getItem()).toString());
        json.addProperty("count", stack.getCount());

        Text customName = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
        if (customName != null) {
            json.addProperty("name", MiniMessageBridge.vanillaToMiniMessage(customName, registries));
        } else {
            json.add("name", com.google.gson.JsonNull.INSTANCE);
        }

        var loreComponent = stack.get(net.minecraft.component.DataComponentTypes.LORE);
        JsonArray loreArray = new JsonArray();
        if (loreComponent != null) {
            for (Text line : loreComponent.lines()) {
                loreArray.add(MiniMessageBridge.vanillaToMiniMessage(line, registries));
            }
        }
        json.add("lore", loreArray);

        json.addProperty("components", encodeOtherComponents(stack, registries));

        Path target = fileFor(name);
        String text = GSON.toJson(json);
        Files.writeString(target, text, StandardCharsets.UTF_8);
    }

    /**
     * Кодирует в SNBT все компоненты предмета, КРОМЕ CUSTOM_NAME и LORE
     * (те хранятся отдельными полями). Логика идентична ExportUtil —
     * одна проблемная запись пропускается, а не валит весь экспорт.
     *
     * ВАЖНО: используем RegistryOps (registries.getOps(NbtOps.INSTANCE)),
     * а НЕ голый NbtOps.INSTANCE. Компоненты вроде minecraft:enchantments
     * внутри ссылаются на RegistryEntry<Enchantment> — без контекста
     * реестра их кодек не может закодировать значение (падает молча,
     * ловится в catch ниже), из-за чего чары просто пропадали при
     * сохранении.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String encodeOtherComponents(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        var ops = registries.getOps(NbtOps.INSTANCE);
        StringBuilder components = new StringBuilder();
        for (ComponentType<?> type : Registries.DATA_COMPONENT_TYPE) {
            if (type == net.minecraft.component.DataComponentTypes.CUSTOM_NAME
                    || type == net.minecraft.component.DataComponentTypes.LORE) {
                continue;
            }
            if (!stack.contains(type)) {
                continue;
            }
            Codec codec = type.getCodec();
            if (codec == null) {
                continue;
            }
            try {
                Object value = stack.get(type);
                DataResult<NbtElement> result = codec.encodeStart(ops, value);
                NbtElement nbt = result.getOrThrow();
                StringNbtWriter writer = new StringNbtWriter();
                nbt.accept(writer);
                String snbt = writer.getString();

                Identifier typeId = Registries.DATA_COMPONENT_TYPE.getId((ComponentType) type);
                if (typeId == null) {
                    continue;
                }
                if (components.length() > 0) {
                    components.append(',');
                }
                components.append(typeId).append('=').append(snbt);
            } catch (Exception ignored) {
                // Пропускаем компонент без стабильного кодека / с ошибкой сериализации.
            }
        }
        return components.toString();
    }

    // ============================================================
    //  Загрузка (give / info)
    // ============================================================

    public static final class StoredItem {
        public final ItemStack stack;
        public final String materialId;
        public final String rawName;
        public final List<String> rawLore;

        StoredItem(ItemStack stack, String materialId, String rawName, List<String> rawLore) {
            this.stack = stack;
            this.materialId = materialId;
            this.rawName = rawName;
            this.rawLore = rawLore;
        }
    }

    /**
     * Читает и полностью восстанавливает предмет из файла. Если файл
     * повреждён (битый JSON, отсутствуют обязательные поля, неизвестный
     * материал, компоненты не парсятся) — переносит его в corrupt/ и
     * бросает CorruptEntryException с человекочитаемой причиной.
     */
    public static StoredItem load(String name, RegistryWrapper.WrapperLookup registries) throws InvalidNameException, CorruptEntryException, IOException {
        validateName(name);
        Path file = fileFor(name);
        if (!Files.isRegularFile(file)) {
            throw new IOException("not_found");
        }

        String reason = null;
        JsonObject json = null;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            json = JsonParser.parseString(text).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            reason = "invalid_json";
        }

        if (reason == null && (!json.has("material") || json.get("material").isJsonNull())) {
            reason = "missing_material";
        }

        Identifier materialId = null;
        Item item = null;
        String materialIdStr = null;
        if (reason == null) {
            materialIdStr = json.get("material").getAsString();
            materialId = Identifier.tryParse(materialIdStr);
            if (materialId == null || !Registries.ITEM.containsId(materialId)) {
                reason = "unknown_material";
            } else {
                item = Registries.ITEM.get(materialId);
            }
        }

        int count = 1;
        if (reason == null && json.has("count") && !json.get("count").isJsonNull()) {
            try {
                count = Math.max(1, json.get("count").getAsInt());
            } catch (Exception e) {
                reason = "invalid_count";
            }
        }

        String rawName = null;
        List<String> rawLore = new ArrayList<>();
        String componentsSnbt = "";
        if (reason == null) {
            if (json.has("name") && !json.get("name").isJsonNull()) {
                rawName = json.get("name").getAsString();
            }
            if (json.has("lore") && json.get("lore").isJsonArray()) {
                for (var el : json.getAsJsonArray("lore")) {
                    rawLore.add(el.getAsString());
                }
            }
            if (json.has("components") && !json.get("components").isJsonNull()) {
                componentsSnbt = json.get("components").getAsString();
            }
        }

        ItemStack stack = null;
        if (reason == null) {
            stack = new ItemStack(item, count);
            try {
                if (rawName != null) {
                    Text nameText = MiniMessageBridge.miniMessageToVanilla(rawName, registries);
                    stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, nameText);
                }
                if (!rawLore.isEmpty()) {
                    List<Text> lines = new ArrayList<>();
                    for (String line : rawLore) {
                        lines.add(MiniMessageBridge.miniMessageToVanilla(line, registries));
                    }
                    stack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lines));
                }
                if (!componentsSnbt.isBlank()) {
                    applyComponentsSnbt(stack, componentsSnbt, registries);
                }
            } catch (Exception e) {
                reason = "invalid_components";
            }
        }

        if (reason != null) {
            moveToCorrupt(name, file);
            throw new CorruptEntryException(reason);
        }

        return new StoredItem(stack, materialIdStr, rawName, rawLore);
    }

    /**
     * Парсит "id1=snbt1,id2=snbt2,..." (формат ExportUtil/StorageUtil.encodeOtherComponents)
     * и применяет каждый компонент на стек через его кодек + StringNbtReader.
     * Один нераспарсенный компонент — весь файл считается битым (см. вызывающий код).
     *
     * Как и при кодировании — codec.parse тут должен идти через
     * RegistryOps (registries.getOps(NbtOps.INSTANCE)), а не голый
     * NbtOps.INSTANCE, иначе компоненты со ссылками на реестр
     * (minecraft:enchantments и т.п.) не распарсятся обратно.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyComponentsSnbt(ItemStack stack, String componentsSnbt, RegistryWrapper.WrapperLookup registries) throws Exception {
        var ops = registries.getOps(NbtOps.INSTANCE);
        for (String entry : splitTopLevel(componentsSnbt)) {
            int eq = entry.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("bad_entry: " + entry);
            }
            String typeIdStr = entry.substring(0, eq);
            String snbt = entry.substring(eq + 1);

            Identifier typeId = Identifier.tryParse(typeIdStr);
            if (typeId == null) {
                throw new IllegalArgumentException("bad_component_id: " + typeIdStr);
            }
            ComponentType<?> type = Registries.DATA_COMPONENT_TYPE.get(typeId);
            if (type == null) {
                throw new IllegalArgumentException("unknown_component: " + typeIdStr);
            }
            Codec codec = type.getCodec();
            if (codec == null) {
                throw new IllegalArgumentException("no_codec: " + typeIdStr);
            }

            NbtElement nbt = StringNbtReader.fromOps(NbtOps.INSTANCE).read(snbt);
            Object value = codec.parse(ops, nbt).getOrThrow();
            stack.set((ComponentType) type, value);
        }
    }

    /**
     * Разбивает "a=1,b={x:1,y:2},c=[1,2,3]" по запятым верхнего уровня,
     * не залезая внутрь {}/[]/"" — так же, как это делает сам SNBT.
     */
    private static List<String> splitTopLevel(String s) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        char stringQuote = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                current.append(c);
                if (c == '\\' && i + 1 < s.length()) {
                    current.append(s.charAt(++i));
                    continue;
                }
                if (c == stringQuote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                stringQuote = c;
                current.append(c);
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
                current.append(c);
                continue;
            }
            if (c == '}' || c == ']') {
                depth--;
                current.append(c);
                continue;
            }
            if (c == ',' && depth == 0) {
                result.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private static void moveToCorrupt(String name, Path file) {
        try {
            Path corrupt = corruptDir();
            Path target = corrupt.resolve(name + ".json");
            int suffix = 2;
            while (Files.exists(target)) {
                target = corrupt.resolve(name + "_" + suffix + ".json");
                suffix++;
            }
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Если даже перенос не удался — файл останется на месте, но
            // load() всё равно сообщит о повреждении вызывающему коду.
        }
    }

    // ============================================================
    //  Быстрая инфа (без полного восстановления предмета) — /cie storage info
    // ============================================================

    public static final class InfoResult {
        public final String material;
        public final String rawName;
        public final List<String> rawLore;

        InfoResult(String material, String rawName, List<String> rawLore) {
            this.material = material;
            this.rawName = rawName;
            this.rawLore = rawLore;
        }
    }

    public static InfoResult info(String name) throws InvalidNameException, CorruptEntryException, IOException {
        validateName(name);
        Path file = fileFor(name);
        if (!Files.isRegularFile(file)) {
            throw new IOException("not_found");
        }

        String reason = null;
        JsonObject json = null;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            json = JsonParser.parseString(text).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            reason = "invalid_json";
        }

        if (reason == null && (!json.has("material") || json.get("material").isJsonNull())) {
            reason = "missing_material";
        }
        String material = null;
        if (reason == null) {
            material = json.get("material").getAsString();
            Identifier id = Identifier.tryParse(material);
            if (id == null || !Registries.ITEM.containsId(id)) {
                reason = "unknown_material";
            }
        }

        if (reason != null) {
            moveToCorrupt(name, file);
            throw new CorruptEntryException(reason);
        }

        String rawName = (json.has("name") && !json.get("name").isJsonNull()) ? json.get("name").getAsString() : null;
        List<String> rawLore = new ArrayList<>();
        if (json.has("lore") && json.get("lore").isJsonArray()) {
            for (var el : json.getAsJsonArray("lore")) {
                rawLore.add(el.getAsString());
            }
        }
        return new InfoResult(material, rawName, rawLore);
    }

    // ============================================================
    //  Удаление
    // ============================================================

    public static boolean delete(String name) throws InvalidNameException {
        validateName(name);
        try {
            return Files.deleteIfExists(fileFor(name));
        } catch (IOException e) {
            return false;
        }
    }

    // ============================================================
    //  Очистка (двухфазное подтверждение)
    // ============================================================

    /** Запрашивает очистку — возвращает количество файлов, которые будут удалены. */
    public static int requestClear() {
        PENDING_CLEAR_CONFIRMATION.clear();
        PENDING_CLEAR_CONFIRMATION.add("pending");
        return names().size();
    }

    public static boolean hasPendingClear() {
        return !PENDING_CLEAR_CONFIRMATION.isEmpty();
    }

    public static void cancelClear() {
        PENDING_CLEAR_CONFIRMATION.clear();
    }

    /** Выполняет реальную очистку. Возвращает количество удалённых файлов. Требует, чтобы requestClear() был вызван ранее. */
    public static int confirmClear() {
        PENDING_CLEAR_CONFIRMATION.clear();
        int removed = 0;
        for (String name : names()) {
            try {
                if (delete(name)) {
                    removed++;
                }
            } catch (InvalidNameException ignored) {
            }
        }
        return removed;
    }

    // ============================================================
    //  Проверка при загрузке мода — /cie storage list corrupt-предметов
    // ============================================================

    /** Список имён файлов (без .json), которые уже лежат в corrupt/ на момент вызова. */
    public static List<String> corruptNames() {
        Path dir = corruptDir();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Сканирует ВСЕ файлы в storage/ на валидность (без восстановления
     * предмета) и переносит повреждённые в corrupt/. Вызывается один раз
     * при загрузке мода, чтобы дать игроку знать о битых записях сразу,
     * а не только когда он попробует их вызвать.
     *
     * Возвращает список имён (без .json), перенесённых в corrupt/ в
     * рамках этого скана.
     */
    public static List<String> scanAndQuarantineCorrupt() {
        List<String> movedNames = new ArrayList<>();
        Path dir = storageDir();
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return movedNames;
        }

        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String name = fileName.substring(0, fileName.length() - ".json".length());
            String reason = validateFileContents(file);
            if (reason != null) {
                moveToCorrupt(name, file);
                movedNames.add(name);
            }
        }
        return movedNames;
    }

    /** Проверка синтаксиса + обязательных полей, БЕЗ восстановления полного ItemStack (не требует RegistryWrapper во время init мода). */
    private static String validateFileContents(Path file) {
        JsonObject json;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            json = JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            return "invalid_json";
        }

        if (!json.has("material") || json.get("material").isJsonNull()) {
            return "missing_material";
        }
        String materialStr = json.get("material").getAsString();
        Identifier id = Identifier.tryParse(materialStr);
        if (id == null || !Registries.ITEM.containsId(id)) {
            return "unknown_material";
        }

        if (json.has("lore") && !json.get("lore").isJsonNull() && !json.get("lore").isJsonArray()) {
            return "invalid_lore";
        }
        if (json.has("count") && !json.get("count").isJsonNull()) {
            try {
                json.get("count").getAsInt();
            } catch (Exception e) {
                return "invalid_count";
            }
        }
        if (json.has("components") && !json.get("components").isJsonNull()) {
            try {
                json.get("components").getAsString();
            } catch (Exception e) {
                return "invalid_components_field";
            }
        }
        return null;
    }
}