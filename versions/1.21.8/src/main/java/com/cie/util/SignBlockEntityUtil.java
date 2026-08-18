package com.cie.util;

import com.cie.text.MiniMessageBridge;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилита для /cie edit sign — работает с сырым NBT предмета-таблички
 * через компонент `minecraft:block_entity_data` (в отличие от большинства
 * других компонентов в моде, у таблички нет отдельного типизированного
 * DataComponent — вся её структура (текст, цвет, свечение, воск) хранится
 * как raw NBT блок-энтити, применяемый при установке блока).
 *
 * ВЕРСИЯ 1.21.8: DataComponentTypes.BLOCK_ENTITY_DATA типизирован как
 * ComponentType<NbtComponent> (не TypedEntityData<BlockEntityType<?>> —
 * этот класс появился позже 1.21.8). NbtComponent.copyNbt() отдаёт NBT
 * блок-энтити напрямую, без id-обёртки, поэтому его можно использовать
 * так же, как раньше copyNbtWithoutId().
 *
 * Формат NBT (стабилен с 1.20, дуо-сайд таблички):
 * {
 *   "front_text": { "messages": [json,json,json,json], "color": "black", "has_glowing_text": false },
 *   "back_text":  { ...то же самое... },
 *   "is_waxed": false
 * }
 * Каждая строка в "messages" — это NBT-строка, содержащая JSON-сериализацию
 * ванильного Text.
 *
 * В командной структуре ТЗ сторона называется "frostSide" (видимо опечатка
 * "front" -> "frost") — здесь трактуется как front_text, "backSide" как
 * back_text.
 */
public final class SignBlockEntityUtil {

    private SignBlockEntityUtil() {
    }

    public static final int LINE_COUNT = 4;
    public static final String FRONT = "front_text";
    public static final String BACK = "back_text";

    private static final String MESSAGES_KEY = "messages";
    private static final String COLOR_KEY = "color";
    private static final String GLOWING_KEY = "has_glowing_text";
    private static final String WAXED_KEY = "is_waxed";

    // ============================================================
    //  NBT доступ
    // ============================================================

    private static NbtCompound getRoot(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        return data != null ? data.copyNbt() : new NbtCompound();
    }

    private static void saveRoot(ItemStack stack, NbtCompound root) {
        stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(root));
    }

    private static NbtCompound getSide(NbtCompound root, String sideKey) {
        return root.getCompoundOrEmpty(sideKey);
    }

    /** Дефолты, не требующие кодирования текста (цвет, свечение) — можно звать без registries. */
    private static NbtCompound ensureScalarDefaults(NbtCompound side) {
        if (!side.contains(COLOR_KEY)) {
            side.putString(COLOR_KEY, DyeColor.BLACK.getId());
        }
        if (!side.contains(GLOWING_KEY)) {
            side.putBoolean(GLOWING_KEY, false);
        }
        return side;
    }

    /** Дефолт для "messages" (4 пустые строки) — нужен кодек текста, поэтому отдельно и с registries. */
    private static NbtCompound ensureMessagesDefault(NbtCompound side, RegistryWrapper.WrapperLookup registries) {
        if (!side.contains(MESSAGES_KEY)) {
            NbtList messages = new NbtList();
            for (int i = 0; i < LINE_COUNT; i++) {
                messages.add(miniMessageToNbt("", registries));
            }
            side.put(MESSAGES_KEY, messages);
        }
        return side;
    }

    // ============================================================
    //  type — тип древесины (пересоздаёт предмет с новым Item)
    // ============================================================

    public static Item getType(ItemStack stack) {
        return stack.getItem();
    }

    /** Ищет предмет "minecraft:&lt;type&gt;_sign" в реестре (oak, spruce, birch, ..., pale_oak — что есть в вашей версии). */
    public static Item resolveTypeItem(String type) {
        Identifier id = Identifier.of("minecraft", type + "_sign");
        if (!Registries.ITEM.containsId(id)) {
            return null;
        }
        return Registries.ITEM.get(id);
    }

    /** Пересоздаёт стек с новым Item-типом таблички, перенося все компоненты (включая NBT текста) как есть. */
    public static ItemStack withType(ItemStack stack, Item newItem) {
        ItemStack newStack = new ItemStack(newItem, stack.getCount());
        newStack.applyChanges(stack.getComponentChanges());
        return newStack;
    }

    public static ItemStack reset(ItemStack stack) {
        return withType(stack, Items.OAK_SIGN);
    }

    // ============================================================
    //  waxed
    // ============================================================

    public static boolean isWaxed(ItemStack stack) {
        return getRoot(stack).getBoolean(WAXED_KEY, false);
    }

    public static void setWaxed(ItemStack stack, boolean waxed) {
        NbtCompound root = getRoot(stack);
        root.putBoolean(WAXED_KEY, waxed);
        saveRoot(stack, root);
    }

    // ============================================================
    //  glowing / baseColor / lines (общие для front_text и back_text)
    // ============================================================

    public static boolean isGlowing(ItemStack stack, String side) {
        NbtCompound sideNbt = ensureScalarDefaults(getSide(getRoot(stack), side));
        return sideNbt.getBoolean(GLOWING_KEY, false);
    }

    public static void setGlowing(ItemStack stack, String side, boolean glowing) {
        NbtCompound root = getRoot(stack);
        NbtCompound sideNbt = ensureScalarDefaults(getSide(root, side));
        sideNbt.putBoolean(GLOWING_KEY, glowing);
        root.put(side, sideNbt);
        saveRoot(stack, root);
    }

    public static DyeColor getBaseColor(ItemStack stack, String side) {
        NbtCompound sideNbt = ensureScalarDefaults(getSide(getRoot(stack), side));
        return DyeColor.byId(sideNbt.getString(COLOR_KEY, DyeColor.BLACK.getId()), DyeColor.BLACK);
    }

    public static void setBaseColor(ItemStack stack, String side, DyeColor color) {
        NbtCompound root = getRoot(stack);
        NbtCompound sideNbt = ensureScalarDefaults(getSide(root, side));
        sideNbt.putString(COLOR_KEY, color.getId());
        root.put(side, sideNbt);
        saveRoot(stack, root);
    }

    public static void resetBaseColor(ItemStack stack, String side) {
        setBaseColor(stack, side, DyeColor.BLACK);
    }

    /** Возвращает все 4 строки стороны как MiniMessage-строки. */
    public static List<String> getLines(ItemStack stack, String side, RegistryWrapper.WrapperLookup registries) {
        NbtCompound sideNbt = ensureMessagesDefault(getSide(getRoot(stack), side), registries);
        NbtList messages = sideNbt.getListOrEmpty(MESSAGES_KEY);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < LINE_COUNT; i++) {
            NbtElement element = i < messages.size() ? messages.get(i) : miniMessageToNbt("", registries);
            result.add(nbtToMiniMessage(element, registries));
        }
        return result;
    }

    public static String getLine(ItemStack stack, String side, int lineIndex, RegistryWrapper.WrapperLookup registries) {
        return getLines(stack, side, registries).get(lineIndex);
    }

    public static void setLine(ItemStack stack, String side, int lineIndex, String miniMessage, RegistryWrapper.WrapperLookup registries) {
        NbtCompound root = getRoot(stack);
        NbtCompound sideNbt = ensureMessagesDefault(getSide(root, side), registries);
        NbtList messages = sideNbt.getListOrEmpty(MESSAGES_KEY);
        while (messages.size() < LINE_COUNT) {
            messages.add(miniMessageToNbt("", registries));
        }
        messages.set(lineIndex, miniMessageToNbt(miniMessage, registries));
        sideNbt.put(MESSAGES_KEY, messages);
        root.put(side, sideNbt);
        saveRoot(stack, root);
    }

    public static void removeLine(ItemStack stack, String side, int lineIndex, RegistryWrapper.WrapperLookup registries) {
        setLine(stack, side, lineIndex, "", registries);
    }

    public static void clearLines(ItemStack stack, String side, RegistryWrapper.WrapperLookup registries) {
        for (int i = 0; i < LINE_COUNT; i++) {
            setLine(stack, side, i, "", registries);
        }
    }

    // ============================================================
    //  Text <-> NBT <-> MiniMessage
    // ============================================================

    /** Кодирует MiniMessage-строку напрямую в NBT-элемент через NbtOps (НЕ JsonOps — иначе на табличке будет виден сырой JSON как текст). */
    private static NbtElement miniMessageToNbt(String miniMessage, RegistryWrapper.WrapperLookup registries) {
        Text text = MiniMessageBridge.miniMessageToVanilla(miniMessage, registries);
        var ops = registries.getOps(NbtOps.INSTANCE);
        return TextCodecs.CODEC.encodeStart(ops, text).getOrThrow(err -> new IllegalStateException("bad sign line encode: " + err));
    }

    private static String nbtToMiniMessage(NbtElement element, RegistryWrapper.WrapperLookup registries) {
        try {
            var ops = registries.getOps(NbtOps.INSTANCE);
            Text text = TextCodecs.CODEC.parse(ops, element)
                    .getOrThrow(err -> new IllegalStateException("bad sign line nbt: " + err));
            return MiniMessageBridge.vanillaToMiniMessage(text, registries);
        } catch (Exception e) {
            return "";
        }
    }
}