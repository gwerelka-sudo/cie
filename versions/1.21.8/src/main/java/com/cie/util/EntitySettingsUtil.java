package com.cie.util;

import com.cie.text.MiniMessageBridge;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Утилита для /cie edit EntitySettings — работает с сырым NBT сущности
 * через компонент `minecraft:entity_data` на яйце призыва (тот же
 * механизм, что и `minecraft:block_entity_data` у таблички).
 *
 * ВЕРСИЯ 1.21.8: DataComponentTypes.ENTITY_DATA типизирован как
 * ComponentType<NbtComponent> (не TypedEntityData<EntityType<?>> —
 * этот класс появился позже 1.21.8). Тип сущности в этой версии не
 * хранится отдельно от NBT — он часть самого NBT, под ключом "id"
 * (стандартный ванильный формат). getRoot()/getCurrentType() читают
 * NBT напрямую и резолвят "id" через Registries.ENTITY_TYPE; saveRoot()
 * сам записывает "id" в NBT перед сохранением — так публичный контракт
 * (type передаётся отдельным параметром, как и раньше) не меняется для
 * VillagerDataUtil и других вызывающих.
 *
 * ВАЖНО про CustomName: как и текст таблички (см. SignBlockEntityUtil),
 * "CustomName" сущности в этой версии тоже кодируется НАПРЯМУЮ в NBT
 * через NbtOps (TextCodecs.CODEC.encodeStart/parse), а НЕ как JSON-строка
 * внутри NBT-строки — если сделать иначе, игра покажет сырой JSON текстом
 * (ровно так и было до этого фикса).
 *
 * Equipment (компонент "equipment" в NBT сущности, формат 1.20.5+) —
 * самая рискованная часть: структура (ключи слотов, вложенный формат
 * предмета, drop_chances) собрана по общеизвестной документации формата
 * и не проверена вживую в вашей конкретной сборке. Если что-то не
 * скомпилируется или не сработает именно в equipment-блоке — присылайте
 * ошибки, поправим точечно, остальная часть класса написана по уже
 * проверенным на вашей версии API (см. SignBlockEntityUtil).
 */
public final class EntitySettingsUtil {

    private EntitySettingsUtil() {
    }

    private static final String MOTION_KEY = "Motion";
    private static final String ROTATION_KEY = "Rotation";
    private static final String CUSTOM_NAME_KEY = "CustomName";
    private static final String CUSTOM_NAME_VISIBLE_KEY = "CustomNameVisible";
    private static final String TAGS_KEY = "Tags";
    private static final String INVULNERABLE_KEY = "Invulnerable";
    private static final String SILENT_KEY = "Silent";
    private static final String NO_AI_KEY = "NoAI";
    private static final String CAN_PICK_UP_LOOT_KEY = "CanPickUpLoot";
    private static final String NO_GRAVITY_KEY = "NoGravity";
    private static final String VISUAL_FIRE_KEY = "HasVisualFire";
    private static final String GLOWING_KEY = "Glowing";
    private static final String HEALTH_KEY = "Health";
    private static final String EQUIPMENT_KEY = "equipment";
    private static final String EQUIPMENT_DROP_CHANCES_KEY = "equipment_drop_chances";

    /** Слоты, как в NBT ключах equipment (не путать с именами предметов "boots" в ТЗ — тут ключ "feet"). */
    public static final List<String> EQUIPMENT_SLOTS = List.of("head", "chest", "legs", "feet", "mainhand", "offhand");

    // ============================================================
    //  NBT доступ / тип сущности
    // ============================================================

    private static NbtComponent getData(ItemStack stack) {
        return stack.get(DataComponentTypes.ENTITY_DATA);
    }

    /** Публичный доступ нужен VillagerDataUtil — тот же механизм entity_data, только villager-специфичные поля. */
    public static NbtCompound getRoot(ItemStack stack) {
        NbtComponent data = getData(stack);
        if (data == null) {
            return new NbtCompound();
        }
        NbtCompound nbt = data.copyNbt();
        nbt.remove("id");
        return nbt;
    }

    /** Тип сущности, который реально будет заспавнен — из entity_data ("id" внутри NBT), либо (если не задан) выведенный из самого предмета-яйца. */
    public static EntityType<?> getCurrentType(ItemStack stack) {
        NbtComponent data = getData(stack);
        if (data == null) {
            return getDefaultTypeForEgg(stack);
        }
        NbtCompound nbt = data.copyNbt();
        if (!nbt.contains("id")) {
            return getDefaultTypeForEgg(stack);
        }
        String idStr = nbt.getString("id", "");
        Identifier entityId = Identifier.tryParse(idStr);
        if (entityId == null || !Registries.ENTITY_TYPE.containsId(entityId)) {
            return getDefaultTypeForEgg(stack);
        }
        return Registries.ENTITY_TYPE.get(entityId);
    }

    /** "zombie_spawn_egg" -> "zombie". Если предмет вообще не яйцо (или тип не нашёлся) — PIG как безопасный дефолт. */
    public static EntityType<?> getDefaultTypeForEgg(ItemStack stack) {
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        String path = itemId.getPath();
        String suffix = "_spawn_egg";
        if (path.endsWith(suffix)) {
            Identifier entityId = Identifier.of(itemId.getNamespace(), path.substring(0, path.length() - suffix.length()));
            if (Registries.ENTITY_TYPE.containsId(entityId)) {
                return Registries.ENTITY_TYPE.get(entityId);
            }
        }
        return EntityType.PIG;
    }

    /** Публичный — переиспользуется VillagerDataUtil. */
    public static void saveRoot(ItemStack stack, EntityType<?> type, NbtCompound root) {
        root.putString("id", EntityType.getId(type).toString());
        stack.set(DataComponentTypes.ENTITY_DATA, NbtComponent.of(root));
    }

    /** Общий паттерн для сеттеров: взять текущий тип+NBT, поменять NBT, сохранить обратно с тем же типом. Публичный — переиспользуется VillagerDataUtil. */
    public static void mutate(ItemStack stack, Consumer<NbtCompound> mutator) {
        EntityType<?> type = getCurrentType(stack);
        NbtCompound root = getRoot(stack);
        mutator.accept(root);
        saveRoot(stack, type, root);
    }

    public static void resetEntity(ItemStack stack) {
        stack.remove(DataComponentTypes.ENTITY_DATA);
    }

    public static void setEntity(ItemStack stack, EntityType<?> newType) {
        NbtCompound root = getRoot(stack);
        saveRoot(stack, newType, root);
    }

    // ============================================================
    //  motion (Motion: [x,y,z] doubles)
    // ============================================================

    public static double getMotion(ItemStack stack, int axis) {
        NbtList motion = getRoot(stack).getListOrEmpty(MOTION_KEY);
        return motion.getDouble(axis, 0.0);
    }

    public static void setMotion(ItemStack stack, int axis, double value) {
        mutate(stack, root -> {
            NbtList motion = ensureVectorD(root.getListOrEmpty(MOTION_KEY), 3);
            motion.set(axis, net.minecraft.nbt.NbtDouble.of(value));
            root.put(MOTION_KEY, motion);
        });
    }

    /** Полностью удаляет ключ "Motion" из NBT (не просто ставит 0,0,0) — сущность вернётся к обычной физике без принудительного motion. */
    public static void resetMotion(ItemStack stack, int axis) {
        mutate(stack, root -> root.remove(MOTION_KEY));
    }

    // ============================================================
    //  facing (Rotation: [yaw, pitch] floats)
    // ============================================================

    public static float getYaw(ItemStack stack) {
        return getRoot(stack).getListOrEmpty(ROTATION_KEY).getFloat(0, 0f);
    }

    public static float getPitch(ItemStack stack) {
        return getRoot(stack).getListOrEmpty(ROTATION_KEY).getFloat(1, 0f);
    }

    public static void setYaw(ItemStack stack, float yaw) {
        mutate(stack, root -> {
            NbtList rot = ensureVectorF(root.getListOrEmpty(ROTATION_KEY), 2);
            rot.set(0, net.minecraft.nbt.NbtFloat.of(yaw));
            root.put(ROTATION_KEY, rot);
        });
    }

    public static void setPitch(ItemStack stack, float pitch) {
        mutate(stack, root -> {
            NbtList rot = ensureVectorF(root.getListOrEmpty(ROTATION_KEY), 2);
            rot.set(1, net.minecraft.nbt.NbtFloat.of(pitch));
            root.put(ROTATION_KEY, rot);
        });
    }

    /** Полностью удаляет ключ "Rotation" (и yaw, и pitch разом — раздельно хранить нельзя, это один NBT-список). */
    public static void resetYaw(ItemStack stack) {
        mutate(stack, root -> root.remove(ROTATION_KEY));
    }

    public static void resetPitch(ItemStack stack) {
        mutate(stack, root -> root.remove(ROTATION_KEY));
    }

    private static NbtList ensureVectorD(NbtList list, int size) {
        while (list.size() < size) {
            list.add(net.minecraft.nbt.NbtDouble.of(0.0));
        }
        return list;
    }

    private static NbtList ensureVectorF(NbtList list, int size) {
        while (list.size() < size) {
            list.add(net.minecraft.nbt.NbtFloat.of(0f));
        }
        return list;
    }

    // ============================================================
    //  customName / customNameVisible
    // ============================================================

    public static String getCustomName(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        NbtCompound root = getRoot(stack);
        if (!root.contains(CUSTOM_NAME_KEY)) {
            return "";
        }
        try {
            var ops = registries.getOps(NbtOps.INSTANCE);
            Text text = net.minecraft.text.TextCodecs.CODEC.parse(ops, root.get(CUSTOM_NAME_KEY))
                    .getOrThrow(err -> new IllegalStateException(String.valueOf(err)));
            return MiniMessageBridge.vanillaToMiniMessage(text, registries);
        } catch (Exception e) {
            return "";
        }
    }

    public static void setCustomName(ItemStack stack, String miniMessage, RegistryWrapper.WrapperLookup registries) {
        mutate(stack, root -> {
            Text text = MiniMessageBridge.miniMessageToVanilla(miniMessage, registries);
            var ops = registries.getOps(NbtOps.INSTANCE);
            var encoded = net.minecraft.text.TextCodecs.CODEC.encodeStart(ops, text)
                    .getOrThrow(err -> new IllegalStateException(String.valueOf(err)));
            root.put(CUSTOM_NAME_KEY, encoded);
        });
    }

    public static void resetCustomName(ItemStack stack) {
        mutate(stack, root -> root.remove(CUSTOM_NAME_KEY));
    }

    public static boolean isCustomNameVisible(ItemStack stack) {
        return getRoot(stack).getBoolean(CUSTOM_NAME_VISIBLE_KEY, false);
    }

    public static void setCustomNameVisible(ItemStack stack, boolean visible) {
        mutate(stack, root -> root.putBoolean(CUSTOM_NAME_VISIBLE_KEY, visible));
    }

    // ============================================================
    //  tags (scoreboard-теги сущности)
    // ============================================================

    public static List<String> getTags(ItemStack stack) {
        NbtList tags = getRoot(stack).getListOrEmpty(TAGS_KEY);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            result.add(tags.getString(i, ""));
        }
        return result;
    }

    public static void addTag(ItemStack stack, String tag) {
        mutate(stack, root -> {
            NbtList tags = root.getListOrEmpty(TAGS_KEY);
            tags.add(NbtString.of(tag));
            root.put(TAGS_KEY, tags);
        });
    }

    /** Возвращает true, если тег реально был и его удалили. */
    public static boolean removeTag(ItemStack stack, String tag) {
        NbtCompound root = getRoot(stack);
        NbtList tags = root.getListOrEmpty(TAGS_KEY);
        boolean removed = false;
        NbtList newTags = new NbtList();
        for (int i = 0; i < tags.size(); i++) {
            String value = tags.getString(i, "");
            if (value.equals(tag)) {
                removed = true;
            } else {
                newTags.add(NbtString.of(value));
            }
        }
        if (removed) {
            EntityType<?> type = getCurrentType(stack);
            root.put(TAGS_KEY, newTags);
            saveRoot(stack, type, root);
        }
        return removed;
    }

    public static void clearTags(ItemStack stack) {
        mutate(stack, root -> root.remove(TAGS_KEY));
    }

    // ============================================================
    //  простые boolean-флаги
    // ============================================================

    public static boolean getInvulnerable(ItemStack stack) {
        return getRoot(stack).getBoolean(INVULNERABLE_KEY, false);
    }

    public static void setInvulnerable(ItemStack stack, boolean value) {
        mutate(stack, root -> root.putBoolean(INVULNERABLE_KEY, value));
    }

    public static boolean getSilent(ItemStack stack) {
        return getRoot(stack).getBoolean(SILENT_KEY, false);
    }

    public static void setSilent(ItemStack stack, boolean value) {
        mutate(stack, root -> root.putBoolean(SILENT_KEY, value));
    }

    public static boolean getNoAI(ItemStack stack) {
        return getRoot(stack).getBoolean(NO_AI_KEY, false);
    }

    public static void setNoAI(ItemStack stack, boolean value) {
        mutate(stack, root -> root.putBoolean(NO_AI_KEY, value));
    }

    public static boolean getCanPickUpLoot(ItemStack stack) {
        return getRoot(stack).getBoolean(CAN_PICK_UP_LOOT_KEY, false);
    }

    public static void setCanPickUpLoot(ItemStack stack, boolean value) {
        mutate(stack, root -> root.putBoolean(CAN_PICK_UP_LOOT_KEY, value));
    }

    public static boolean getNoGravity(ItemStack stack) {
        return getRoot(stack).getBoolean(NO_GRAVITY_KEY, false);
    }

    public static void setNoGravity(ItemStack stack, boolean value) {
        mutate(stack, root -> root.putBoolean(NO_GRAVITY_KEY, value));
    }

    public static boolean getVisualFire(ItemStack stack) {
        return getRoot(stack).getBoolean(VISUAL_FIRE_KEY, false);
    }

    public static void setVisualFire(ItemStack stack, boolean value) {
        mutate(stack, root -> root.putBoolean(VISUAL_FIRE_KEY, value));
    }

    public static boolean getGlowing(ItemStack stack) {
        return getRoot(stack).getBoolean(GLOWING_KEY, false);
    }

    public static void setGlowing(ItemStack stack, boolean value) {
        mutate(stack, root -> root.putBoolean(GLOWING_KEY, value));
    }

    // ============================================================
    //  health
    // ============================================================

    public static float getHealth(ItemStack stack) {
        return getRoot(stack).getFloat(HEALTH_KEY, 1.0f);
    }

    public static void setHealth(ItemStack stack, float value) {
        mutate(stack, root -> root.putFloat(HEALTH_KEY, value));
    }

    public static void resetHealth(ItemStack stack) {
        mutate(stack, root -> root.remove(HEALTH_KEY));
    }

    // ============================================================
    //  equipment (РИСКОВАННАЯ ЧАСТЬ — см. javadoc класса)
    // ============================================================

    /** Получить предмет в слоте экипировки, или ItemStack.EMPTY если не задан. */
    public static ItemStack getEquipment(ItemStack stack, String slot, RegistryWrapper.WrapperLookup registries) {
        NbtCompound equipment = getRoot(stack).getCompoundOrEmpty(EQUIPMENT_KEY);
        if (!equipment.contains(slot)) {
            return ItemStack.EMPTY;
        }
        NbtCompound itemNbt = equipment.getCompoundOrEmpty(slot);
        var ops = registries.getOps(NbtOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, itemNbt).result().orElse(ItemStack.EMPTY);
    }

    public static void setEquipment(ItemStack stack, String slot, ItemStack item, RegistryWrapper.WrapperLookup registries) {
        mutate(stack, root -> {
            NbtCompound equipment = root.getCompoundOrEmpty(EQUIPMENT_KEY);
            var ops = registries.getOps(NbtOps.INSTANCE);
            var encoded = ItemStack.CODEC.encodeStart(ops, item)
                    .getOrThrow(err -> new IllegalStateException(String.valueOf(err)));
            equipment.put(slot, encoded);
            root.put(EQUIPMENT_KEY, equipment);
        });
    }

    public static void removeEquipment(ItemStack stack, String slot) {
        mutate(stack, root -> {
            NbtCompound equipment = root.getCompoundOrEmpty(EQUIPMENT_KEY);
            equipment.remove(slot);
            root.put(EQUIPMENT_KEY, equipment);
        });
    }

    public static float getDropChance(ItemStack stack, String slot) {
        NbtCompound chances = getRoot(stack).getCompoundOrEmpty(EQUIPMENT_DROP_CHANCES_KEY);
        return chances.getFloat(slot, 0.0f);
    }

    public static void setDropChance(ItemStack stack, String slot, float chance) {
        mutate(stack, root -> {
            NbtCompound chances = root.getCompoundOrEmpty(EQUIPMENT_DROP_CHANCES_KEY);
            chances.putFloat(slot, chance);
            root.put(EQUIPMENT_DROP_CHANCES_KEY, chances);
        });
    }

    public static void resetDropChance(ItemStack stack, String slot) {
        mutate(stack, root -> {
            NbtCompound chances = root.getCompoundOrEmpty(EQUIPMENT_DROP_CHANCES_KEY);
            chances.remove(slot);
            root.put(EQUIPMENT_DROP_CHANCES_KEY, chances);
        });
    }
}