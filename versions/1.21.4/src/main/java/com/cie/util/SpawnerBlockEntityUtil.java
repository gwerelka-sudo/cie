package com.cie.util;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилита для /cie edit spawner — работает с сырым NBT спавнера через
 * компонент minecraft:block_entity_data (тот же механизм, что и у
 * таблички/яйца призыва, см. SignBlockEntityUtil/EntitySettingsUtil).
 *
 * Формат NBT спавнера (MobSpawnerLogic, стабилен с 1.18+):
 * {
 *   "SpawnCount": short,
 *   "SpawnRange": short,
 *   "Delay": short,
 *   "MinSpawnDelay": short,
 *   "MaxSpawnDelay": short,
 *   "MaxNearbyEntities": short,
 *   "RequiredPlayerRange": short,
 *   "SpawnData": { "entity": { "id": "minecraft:...", ...прочий nbt... } },
 *   "SpawnPotentials": [
 *     { "weight": int, "data": { "entity": {...} } }
 *   ]
 * }
 */
public final class SpawnerBlockEntityUtil {

    private SpawnerBlockEntityUtil() {
    }

    private static final String SPAWN_COUNT = "SpawnCount";
    private static final String SPAWN_RANGE = "SpawnRange";
    private static final String DELAY = "Delay";
    private static final String MIN_SPAWN_DELAY = "MinSpawnDelay";
    private static final String MAX_SPAWN_DELAY = "MaxSpawnDelay";
    private static final String MAX_NEARBY_ENTITIES = "MaxNearbyEntities";
    private static final String REQUIRED_PLAYER_RANGE = "RequiredPlayerRange";
    private static final String SPAWN_DATA = "SpawnData";
    private static final String SPAWN_POTENTIALS = "SpawnPotentials";
    private static final String ENTITY = "entity";
    private static final String ID = "id";
    private static final String WEIGHT = "weight";
    private static final String DATA = "data";

    // Дефолты ваниллы (MobSpawnerLogic).
    public static final short DEFAULT_SPAWN_COUNT = 4;
    public static final short DEFAULT_SPAWN_RANGE = 4;
    public static final short DEFAULT_DELAY = 20;
    public static final short DEFAULT_MIN_SPAWN_DELAY = 200;
    public static final short DEFAULT_MAX_SPAWN_DELAY = 800;
    public static final short DEFAULT_MAX_NEARBY_ENTITIES = 6;
    public static final short DEFAULT_REQUIRED_PLAYER_RANGE = 16;

    // ============================================================
    //  NBT доступ
    // ============================================================

    private static NbtCompound getRoot(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (data == null) {
            return new NbtCompound();
        }
        NbtCompound copy = data.copyNbt();
        copy.remove("id");
        return copy;
    }

    private static void saveRoot(ItemStack stack, NbtCompound root) {
        NbtCompound withId = root.copy();
        withId.putString("id", Registries.BLOCK_ENTITY_TYPE.getId(BlockEntityType.MOB_SPAWNER).toString());
        stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(withId));
    }

    public static void resetAll(ItemStack stack) {
        stack.remove(DataComponentTypes.BLOCK_ENTITY_DATA);
    }

    // ============================================================
    //  Простые short-поля: get/set/reset
    // ============================================================

    public static int getShort(ItemStack stack, String key, short def) {
        return getRoot(stack).getShort(key);
    }

    public static void setShort(ItemStack stack, String key, int value) {
        NbtCompound root = getRoot(stack);
        root.putShort(key, (short) value);
        saveRoot(stack, root);
    }

    public static void resetKey(ItemStack stack, String key) {
        NbtCompound root = getRoot(stack);
        root.remove(key);
        saveRoot(stack, root);
    }

    public static int getSpawnCount(ItemStack stack) {
        return getShort(stack, SPAWN_COUNT, DEFAULT_SPAWN_COUNT);
    }

    public static void setSpawnCount(ItemStack stack, int value) {
        setShort(stack, SPAWN_COUNT, value);
    }

    public static void resetSpawnCount(ItemStack stack) {
        resetKey(stack, SPAWN_COUNT);
    }

    public static int getSpawnRange(ItemStack stack) {
        return getShort(stack, SPAWN_RANGE, DEFAULT_SPAWN_RANGE);
    }

    public static void setSpawnRange(ItemStack stack, int value) {
        setShort(stack, SPAWN_RANGE, value);
    }

    public static void resetSpawnRange(ItemStack stack) {
        resetKey(stack, SPAWN_RANGE);
    }

    public static int getDelay(ItemStack stack) {
        return getShort(stack, DELAY, DEFAULT_DELAY);
    }

    public static void setDelay(ItemStack stack, int value) {
        setShort(stack, DELAY, value);
    }

    public static void resetDelay(ItemStack stack) {
        resetKey(stack, DELAY);
    }

    public static int getMinSpawnDelay(ItemStack stack) {
        return getShort(stack, MIN_SPAWN_DELAY, DEFAULT_MIN_SPAWN_DELAY);
    }

    public static void setMinSpawnDelay(ItemStack stack, int value) {
        setShort(stack, MIN_SPAWN_DELAY, value);
    }

    public static void resetMinSpawnDelay(ItemStack stack) {
        resetKey(stack, MIN_SPAWN_DELAY);
    }

    public static int getMaxSpawnDelay(ItemStack stack) {
        return getShort(stack, MAX_SPAWN_DELAY, DEFAULT_MAX_SPAWN_DELAY);
    }

    public static void setMaxSpawnDelay(ItemStack stack, int value) {
        setShort(stack, MAX_SPAWN_DELAY, value);
    }

    public static void resetMaxSpawnDelay(ItemStack stack) {
        resetKey(stack, MAX_SPAWN_DELAY);
    }

    public static int getMaxNearbyEntities(ItemStack stack) {
        return getShort(stack, MAX_NEARBY_ENTITIES, DEFAULT_MAX_NEARBY_ENTITIES);
    }

    public static void setMaxNearbyEntities(ItemStack stack, int value) {
        setShort(stack, MAX_NEARBY_ENTITIES, value);
    }

    public static void resetMaxNearbyEntities(ItemStack stack) {
        resetKey(stack, MAX_NEARBY_ENTITIES);
    }

    public static int getRequiredPlayerRange(ItemStack stack) {
        return getShort(stack, REQUIRED_PLAYER_RANGE, DEFAULT_REQUIRED_PLAYER_RANGE);
    }

    public static void setRequiredPlayerRange(ItemStack stack, int value) {
        setShort(stack, REQUIRED_PLAYER_RANGE, value);
    }

    public static void resetRequiredPlayerRange(ItemStack stack) {
        resetKey(stack, REQUIRED_PLAYER_RANGE);
    }

    // ============================================================
    //  spawnData — { "entity": { "id": ..., ...entity_data } }
    // ============================================================

    /** entity_data — произвольный SNBT-компаунд сущности (без "id", он передаётся отдельно). */
    public static void setSpawnData(ItemStack stack, Identifier entityId, String entityDataSnbt) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        NbtCompound entity = parseSnbtOrEmpty(entityDataSnbt);
        entity.putString(ID, entityId.toString());

        NbtCompound spawnData = new NbtCompound();
        spawnData.put(ENTITY, entity);

        NbtCompound root = getRoot(stack);
        root.put(SPAWN_DATA, spawnData);
        saveRoot(stack, root);
    }

    private static NbtCompound parseSnbtOrEmpty(String snbt) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (snbt == null || snbt.isBlank()) {
            return new NbtCompound();
        }
        return StringNbtReader.parse(snbt);
    }

    /** Возвращает id сущности из SpawnData, либо null, если ключ не задан. */
    public static Identifier getSpawnDataEntityId(ItemStack stack) {
        NbtCompound spawnData = getRoot(stack).getCompound(SPAWN_DATA);
        NbtCompound entity = spawnData.getCompound(ENTITY);
        String idStr = entity.getString(ID);
        return idStr.isEmpty() ? null : Identifier.tryParse(idStr);
    }

    /** Возвращает SpawnData целиком как SNBT-строку (для показа в чате), либо null. */
    public static String getSpawnDataSnbt(ItemStack stack) {
        NbtCompound root = getRoot(stack);
        if (!root.contains(SPAWN_DATA)) {
            return null;
        }
        return root.getCompound(SPAWN_DATA).toString();
    }

    public static void resetSpawnData(ItemStack stack) {
        resetKey(stack, SPAWN_DATA);
    }

    // ============================================================
    //  spawnPotential — список { weight, data: { entity: {...} } }
    // ============================================================

    public static void addSpawnPotential(ItemStack stack, Identifier entityId, String entityDataSnbt, int weight) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        NbtCompound entity = parseSnbtOrEmpty(entityDataSnbt);
        entity.putString(ID, entityId.toString());

        NbtCompound entryData = new NbtCompound();
        entryData.put(ENTITY, entity);

        NbtCompound entry = new NbtCompound();
        entry.putInt(WEIGHT, Math.max(1, weight));
        entry.put(DATA, entryData);

        NbtCompound root = getRoot(stack);
        NbtList list = root.getList(SPAWN_POTENTIALS, NbtCompound.COMPOUND_TYPE).copy();
        list.add(entry);
        root.put(SPAWN_POTENTIALS, list);
        saveRoot(stack, root);
    }

    /** Список описаний записей вида "minecraft:zombie (weight=1)" для вывода в чат. */
    public static List<String> listSpawnPotentials(ItemStack stack) {
        NbtList list = getRoot(stack).getList(SPAWN_POTENTIALS, NbtCompound.COMPOUND_TYPE);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof NbtCompound entry)) continue;
            int weight = entry.getInt(WEIGHT);
            NbtCompound entity = entry.getCompound(DATA).getCompound(ENTITY);
            String id = entity.getString(ID);
            result.add(id + " (weight=" + weight + ")");
        }
        return result;
    }

    /** Удаляет все записи с указанным entityId. Возвращает количество удалённых. */
    public static int removeSpawnPotential(ItemStack stack, Identifier entityId) {
        NbtCompound root = getRoot(stack);
        NbtList list = root.getList(SPAWN_POTENTIALS, NbtCompound.COMPOUND_TYPE);
        NbtList filtered = new NbtList();
        int removed = 0;
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof NbtCompound entry)) {
                continue;
            }
            NbtCompound entity = entry.getCompound(DATA).getCompound(ENTITY);
            String id = entity.getString(ID);
            if (id.equals(entityId.toString())) {
                removed++;
            } else {
                filtered.add(entry);
            }
        }
        root.put(SPAWN_POTENTIALS, filtered);
        saveRoot(stack, root);
        return removed;
    }

    public static void clearSpawnPotentials(ItemStack stack) {
        resetKey(stack, SPAWN_POTENTIALS);
    }
}