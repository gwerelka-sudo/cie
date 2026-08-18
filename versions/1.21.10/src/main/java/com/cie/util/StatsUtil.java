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
import java.util.Optional;

/**
 * Шуточная личная статистика — /cie stats. Хранится в
 * .minecraft/cie/stats.json (глобально, без привязки к нику — мод
 * клиентский и по духу "один игрок = один набор счётчиков", как и
 * .minecraft/cie/sounds.json у SoundSettingsUtil).
 *
 * Источники данных:
 *  - itemsEdited/undoUsed/redoUsed/chaosUsed — считаются в самих командах
 *    (CIECommand::undo/redo/chaos, UndoUtil.pushSnapshot) — там правка
 *    гарантированно произошла;
 *  - editedFieldCounts ("самый частый компонент") — считается по имени
 *    подкоманды edit из CommandHistoryUtil.onCommandSent, т.е. по факту
 *    ПОПЫТКИ редактирования поля, не по гарантированному успеху (иначе
 *    пришлось бы вручную протыкать хук в 40+ Util-классах компонентов).
 *    Для шуточной статистики такая точность более чем достаточна.
 */
public final class StatsUtil {

    private StatsUtil() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static long itemsEdited;
    private static long undoUsed;
    private static long redoUsed;
    private static long chaosUsed;
    private static long repeatUsed;
    private static long macrosRecorded;
    private static long macrosPlayed;
    private static final Map<String, Long> EDITED_FIELD_COUNTS = new LinkedHashMap<>();
    private static boolean loaded = false;

    private static Path file() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cie");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir.resolve("stats.json");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) {
            return;
        }
        try {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(text, JsonObject.class);
            if (root == null) {
                return;
            }
            itemsEdited = longOf(root, "itemsEdited");
            undoUsed = longOf(root, "undoUsed");
            redoUsed = longOf(root, "redoUsed");
            chaosUsed = longOf(root, "chaosUsed");
            repeatUsed = longOf(root, "repeatUsed");
            macrosRecorded = longOf(root, "macrosRecorded");
            macrosPlayed = longOf(root, "macrosPlayed");
            if (root.has("editedFieldCounts") && root.get("editedFieldCounts").isJsonObject()) {
                JsonObject fields = root.getAsJsonObject("editedFieldCounts");
                for (String key : fields.keySet()) {
                    try {
                        EDITED_FIELD_COUNTS.put(key, fields.get(key).getAsLong());
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
            // Битый stats.json — просто начинаем счёт заново, это не критичные данные.
        }
    }

    private static long longOf(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsLong() : 0L;
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("itemsEdited", itemsEdited);
        root.addProperty("undoUsed", undoUsed);
        root.addProperty("redoUsed", redoUsed);
        root.addProperty("chaosUsed", chaosUsed);
        root.addProperty("repeatUsed", repeatUsed);
        root.addProperty("macrosRecorded", macrosRecorded);
        root.addProperty("macrosPlayed", macrosPlayed);
        JsonObject fields = new JsonObject();
        for (Map.Entry<String, Long> e : EDITED_FIELD_COUNTS.entrySet()) {
            fields.addProperty(e.getKey(), e.getValue());
        }
        root.add("editedFieldCounts", fields);
        try {
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static synchronized void incrementItemsEdited() {
        ensureLoaded();
        itemsEdited++;
        save();
    }

    public static synchronized void incrementUndo() {
        ensureLoaded();
        undoUsed++;
        save();
    }

    public static synchronized void incrementRedo() {
        ensureLoaded();
        redoUsed++;
        save();
    }

    public static synchronized void incrementChaos() {
        ensureLoaded();
        chaosUsed++;
        save();
    }

    public static synchronized void incrementRepeat() {
        ensureLoaded();
        repeatUsed++;
        save();
    }

    public static synchronized void incrementMacrosRecorded() {
        ensureLoaded();
        macrosRecorded++;
        save();
    }

    public static synchronized void incrementMacrosPlayed() {
        ensureLoaded();
        macrosPlayed++;
        save();
    }

    public static synchronized void recordEditedField(String field) {
        ensureLoaded();
        EDITED_FIELD_COUNTS.merge(field, 1L, Long::sum);
        save();
    }

    public static final class Snapshot {
        public final long itemsEdited;
        public final long undoUsed;
        public final long redoUsed;
        public final long chaosUsed;
        public final long repeatUsed;
        public final long macrosRecorded;
        public final long macrosPlayed;
        public final Optional<Map.Entry<String, Long>> topField;

        Snapshot(long itemsEdited, long undoUsed, long redoUsed, long chaosUsed, long repeatUsed,
                 long macrosRecorded, long macrosPlayed, Optional<Map.Entry<String, Long>> topField) {
            this.itemsEdited = itemsEdited;
            this.undoUsed = undoUsed;
            this.redoUsed = redoUsed;
            this.chaosUsed = chaosUsed;
            this.repeatUsed = repeatUsed;
            this.macrosRecorded = macrosRecorded;
            this.macrosPlayed = macrosPlayed;
            this.topField = topField;
        }
    }

    public static synchronized Snapshot snapshot() {
        ensureLoaded();
        Optional<Map.Entry<String, Long>> top = EDITED_FIELD_COUNTS.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        return new Snapshot(itemsEdited, undoUsed, redoUsed, chaosUsed, repeatUsed, macrosRecorded, macrosPlayed, top);
    }
}
