package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.StringNbtReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Низкоуровневые операции над NBT armor stand'а, которая живёт в компоненте
 * ENTITY_DATA предмета в руке — тот же механизм, что и EntitySettingsUtil /
 * VillagerDataUtil (см. /cie edit EntitySettings, /cie edit villagerData),
 * просто набор полей, специфичных для armor_stand: флаги, поза шести частей
 * и отдельное дисковое хранилище пресетов поз.
 *
 * Как и EntitySettingsUtil, НЕ проверяет, что предмет в руке — именно
 * armor_stand: команды /cie edit armorStand ... остаются рабочими для
 * любого предмета с ENTITY_DATA, по аналогии с остальным редактором.
 * Единственное место, где тип предмета проверяется жёстко — /cie edit
 * armorStand menu (см. CIECommand#openArmorStandMenu), потому что там
 * превью реально рендерит ArmorStandEntity.
 *
 * ВАЖНО: имена методов NbtCompound/NbtList (getBoolean(key, def),
 * getCompoundOrEmpty, getListOrEmpty, getFloat(index, def)) соответствуют
 * актуальным на момент написания Yarn-маппингам после рефакторинга NBT API
 * (default-value геттеры). Если в вашей версии маппинги старее/новее —
 * это единственное место, которое может потребовать правки под точные
 * сигнатуры (nbt.getCompound("Pose") vs getCompoundOrEmpty("Pose") и т.п.).
 */
public final class ArmorStandDataUtil {

    private ArmorStandDataUtil() {}

    public enum Part {
        HEAD("Head"),
        BODY("Body"),
        LEFT_ARM("LeftArm"),
        RIGHT_ARM("RightArm"),
        LEFT_LEG("LeftLeg"),
        RIGHT_LEG("RightLeg");

        public final String nbtKey;

        Part(String nbtKey) {
            this.nbtKey = nbtKey;
        }
    }

    public enum Axis {
        X(0), Y(1), Z(2);

        public final int index;

        Axis(int index) {
            this.index = index;
        }
    }

    /**
     * Пять булевых флагов armor stand'а, которые входят в снимок пресета
     * наравне с позой и экипировкой (см. {@link #capturePresetData(ItemStack)}).
     */
    public static final List<String> FLAG_KEYS =
            List.of("NoBasePlate", "Small", "ShowArms", "Invisible", "Marker");

    /**
     * Ключ NBT-компаунда с экипировкой внутри ENTITY_DATA (тот же самый,
     * что читает/пишет EntitySettingsUtil.getEquipment/setEquipment —
     * никакого отдельного хранилища для шмота нет, это подтег того же nbt,
     * что и Pose). Если у EntitySettingsUtil другой ключ — поменять только
     * тут, остальной код ниже завязан на константу, а не на строку.
     */
    private static final String EQUIPMENT_KEY = "equipment";

// ================================================================
    //  сырой доступ к ENTITY_DATA
    // ================================================================

    public static NbtCompound read(ItemStack stack) {
        TypedEntityData<EntityType<?>> data = stack.get(DataComponentTypes.ENTITY_DATA);

        return data != null
                ? data.copyNbtWithoutId()
                : new NbtCompound();
    }

    public static void write(ItemStack stack, NbtCompound nbt) {
        TypedEntityData<EntityType<?>> data = stack.get(DataComponentTypes.ENTITY_DATA);

        EntityType<?> entityType = data != null
                ? data.getType()
                : EntityType.ARMOR_STAND;

        stack.set(
                DataComponentTypes.ENTITY_DATA,
                TypedEntityData.create(entityType, nbt)
        );
    }

    // ================================================================
    //  флаги: NoBasePlate / Small / ShowArms / Invisible / Marker
    // ================================================================

    public static boolean getFlag(ItemStack stack, String nbtKey) {
        return read(stack).getBoolean(nbtKey, false);
    }

    public static void setFlag(ItemStack stack, String nbtKey, boolean value) {
        NbtCompound nbt = read(stack);
        if (value) {
            nbt.putBoolean(nbtKey, true);
        } else {
            // false — значение по умолчанию у всех пяти флагов, не засоряем NBT
            nbt.remove(nbtKey);
        }
        write(stack, nbt);
    }

    // ================================================================
    //  поза (Pose: {Head/Body/LeftArm/RightArm/LeftLeg/RightLeg: [x,y,z]})
    // ================================================================

    public static float getPoseAxis(ItemStack stack, Part part, Axis axis) {
        NbtCompound pose = read(stack).getCompoundOrEmpty("Pose");
        NbtList list = pose.getListOrEmpty(part.nbtKey);
        return list.size() > axis.index ? list.getFloat(axis.index, 0f) : 0f;
    }

    public static float[] getPoseAll(ItemStack stack, Part part) {
        return new float[]{
                getPoseAxis(stack, part, Axis.X),
                getPoseAxis(stack, part, Axis.Y),
                getPoseAxis(stack, part, Axis.Z)
        };
    }

    public static void setPoseAxis(ItemStack stack, Part part, Axis axis, float value) {
        float[] cur = getPoseAll(stack, part);
        cur[axis.index] = value;
        setPoseAll(stack, part, cur);
    }

    public static void setPoseAll(ItemStack stack, Part part, float[] xyz) {
        NbtCompound nbt = read(stack);
        NbtCompound pose = nbt.getCompoundOrEmpty("Pose").copy();
        pose.put(part.nbtKey, floatList(xyz));
        nbt.put("Pose", pose);
        write(stack, nbt);
    }

    public static void resetPoseAxis(ItemStack stack, Part part, Axis axis) {
        setPoseAxis(stack, part, axis, 0f);
    }

    public static void resetPoseAll(ItemStack stack, Part part) {
        NbtCompound nbt = read(stack);
        NbtCompound pose = nbt.getCompoundOrEmpty("Pose").copy();
        pose.remove(part.nbtKey);
        nbt.put("Pose", pose);
        write(stack, nbt);
    }

    private static NbtList floatList(float[] values) {
        NbtList list = new NbtList();
        for (float v : values) {
            list.add(NbtFloat.of(v));
        }
        return list;
    }

    // ================================================================
    //  пресеты — отдельное JSON-хранилище на диске
    //  (.minecraft/cie/armor_stand_presets.json), совсем не /cie storage:
    //  там целые предметы в 100-страничном хранилище, тут только снимок
    //  позы + флагов + экипировки одной стойки на пресет.
    //
    //  Каждый пресет хранится как SNBT-строка (то, что отдаёт
    //  NbtCompound#toString()) одного компаунда с ключами Pose, equipment
    //  и теми из FLAG_KEYS, что были true на момент снятия. SNBT выбран
    //  вместо GSON-сериализации структуры, потому что equipment — это
    //  полноценные ItemStack-NBT (id, count, components), а не примитивы,
    //  и через готовый NBT-парсер их не нужно гонять через ItemStack-кодек
    //  с RegistryOps — сохраняем/читаем ровно тот же NBT, что уже лежит
    //  в ENTITY_DATA предмета.
    // ================================================================

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PRESETS_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static Path presetsFile() {
        return FabricLoader.getInstance().getGameDir().resolve("cie").resolve("armor_stand_presets.json");
    }

    private static Map<String, String> loadPresets() {
        Path file = presetsFile();
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, String> map = GSON.fromJson(reader, PRESETS_TYPE);
            return map != null ? map : new LinkedHashMap<>();
        } catch (IOException | JsonParseException e) {
            return new LinkedHashMap<>();
        }
    }

    private static void savePresets(Map<String, String> presets) {
        Path file = presetsFile();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(presets, PRESETS_TYPE, writer);
            }
        } catch (IOException ignored) {
            // как и остальное хранилище мода — тихо игнорируем I/O ошибки,
            // не роняем клиент из-за диска
        }
    }

    public static List<String> presetNames() {
        return new ArrayList<>(loadPresets().keySet());
    }

    public static boolean hasPreset(String name) {
        return loadPresets().containsKey(name);
    }

    /**
     * Снимает текущую позу всех 6 частей, включённые флаги (NoBasePlate/
     * Small/ShowArms/Invisible/Marker) и всю экипировку стойки в предмете
     * в руке — единый компаунд, который потом целиком идёт в пресет.
     */
    public static NbtCompound capturePresetData(ItemStack stack) {
        NbtCompound src = read(stack);
        NbtCompound snapshot = new NbtCompound();

        NbtCompound pose = new NbtCompound();
        for (Part part : Part.values()) {
            pose.put(part.nbtKey, floatList(getPoseAll(stack, part)));
        }
        snapshot.put("Pose", pose);

        for (String flagKey : FLAG_KEYS) {
            if (src.getBoolean(flagKey, false)) {
                snapshot.putBoolean(flagKey, true);
            }
        }

        NbtCompound equipment = src.getCompoundOrEmpty(EQUIPMENT_KEY);
        if (!equipment.isEmpty()) {
            snapshot.put(EQUIPMENT_KEY, equipment.copy());
        }

        return snapshot;
    }

    /** Сохраняет снимок текущего состояния предмета в руке под именем name. */
    public static void addPreset(String name, ItemStack stack) {
        Map<String, String> presets = loadPresets();
        presets.put(name, capturePresetData(stack).toString());
        savePresets(presets);
    }

    public static boolean removePreset(String name) {
        Map<String, String> presets = loadPresets();
        boolean removed = presets.remove(name) != null;
        if (removed) {
            savePresets(presets);
        }
        return removed;
    }

    public static void clearPresets() {
        savePresets(new LinkedHashMap<>());
    }

    /**
     * Применяет сохранённый пресет к предмету в руке: полностью
     * перезаписывает Pose и equipment, для флагов — сперва сбрасывает все
     * пять в false, затем выставляет true те, что были в пресете (то есть
     * пресет — это абсолютное состояние, а не диф поверх текущего).
     */
    public static boolean applyPreset(ItemStack stack, String name) {
        String raw = loadPresets().get(name);
        if (raw == null) {
            return false;
        }

        NbtCompound snapshot;
        try {
            snapshot = StringNbtReader.readCompound(raw);
        } catch (Exception e) {
            return false;
        }

        NbtCompound nbt = read(stack);

        nbt.put("Pose", snapshot.getCompoundOrEmpty("Pose").copy());

        for (String flagKey : FLAG_KEYS) {
            nbt.remove(flagKey);
        }
        for (String flagKey : FLAG_KEYS) {
            if (snapshot.getBoolean(flagKey, false)) {
                nbt.putBoolean(flagKey, true);
            }
        }

        if (snapshot.contains(EQUIPMENT_KEY)) {
            nbt.put(EQUIPMENT_KEY, snapshot.getCompoundOrEmpty(EQUIPMENT_KEY).copy());
        } else {
            nbt.remove(EQUIPMENT_KEY);
        }

        write(stack, nbt);
        return true;
    }
}