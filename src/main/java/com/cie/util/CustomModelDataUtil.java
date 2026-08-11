package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилита для работы с компонентом `minecraft:custom_model_data`.
 *
 * Начиная с 1.21.4 этот компонент — не одно число, а четыре независимых
 * списка: floats, flags, strings, colors. Каждый под-раздел команды
 * /cie edit customModelData управляет своим списком независимо от
 * остальных трёх — сброс/установка одного не трогает другие.
 *
 * Цвета хранятся ванильным компонентом как int (RGB, 0xRRGGBB), а в
 * командах и в чате показываются/принимаются в виде hex-строк ("#ffffff"
 * или "ffffff") — конвертация происходит здесь же.
 */
public final class CustomModelDataUtil {

    private CustomModelDataUtil() {
    }

    private static CustomModelDataComponent get(ItemStack stack) {
        CustomModelDataComponent comp = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return comp != null ? comp : new CustomModelDataComponent(List.of(), List.of(), List.of(), List.of());
    }

    private static void set(ItemStack stack, CustomModelDataComponent comp) {
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, comp);
    }

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
    }

    // ============================================================
    //  floats
    // ============================================================

    public static final class Floats {
        private Floats() {
        }

        public static List<Float> get(ItemStack stack) {
            return CustomModelDataUtil.get(stack).floats();
        }

        public static void set(ItemStack stack, List<Float> values) {
            CustomModelDataComponent old = CustomModelDataUtil.get(stack);
            CustomModelDataUtil.set(stack, new CustomModelDataComponent(values, old.flags(), old.strings(), old.colors()));
        }

        public static void reset(ItemStack stack) {
            CustomModelDataComponent old = CustomModelDataUtil.get(stack);
            CustomModelDataUtil.set(stack, new CustomModelDataComponent(List.of(), old.flags(), old.strings(), old.colors()));
        }
    }

    // ============================================================
    //  strings
    // ============================================================

    public static final class Strings {
        private Strings() {
        }

        public static List<String> get(ItemStack stack) {
            return CustomModelDataUtil.get(stack).strings();
        }

        public static void set(ItemStack stack, List<String> values) {
            CustomModelDataComponent old = CustomModelDataUtil.get(stack);
            CustomModelDataUtil.set(stack, new CustomModelDataComponent(old.floats(), old.flags(), values, old.colors()));
        }

        public static void reset(ItemStack stack) {
            CustomModelDataComponent old = CustomModelDataUtil.get(stack);
            CustomModelDataUtil.set(stack, new CustomModelDataComponent(old.floats(), old.flags(), List.of(), old.colors()));
        }
    }

    // ============================================================
    //  flags
    // ============================================================

    public static final class Flags {
        private Flags() {
        }

        public static List<Boolean> get(ItemStack stack) {
            return CustomModelDataUtil.get(stack).flags();
        }

        public static void set(ItemStack stack, List<Boolean> values) {
            CustomModelDataComponent old = CustomModelDataUtil.get(stack);
            CustomModelDataUtil.set(stack, new CustomModelDataComponent(old.floats(), values, old.strings(), old.colors()));
        }

        public static void reset(ItemStack stack) {
            CustomModelDataComponent old = CustomModelDataUtil.get(stack);
            CustomModelDataUtil.set(stack, new CustomModelDataComponent(old.floats(), List.of(), old.strings(), old.colors()));
        }
    }

    // ============================================================
    //  colors (хранятся как int 0xRRGGBB, наружу отдаём/принимаем hex)
    // ============================================================

    public static final class Colors {
        private Colors() {
        }

        public static List<Integer> getRaw(ItemStack stack) {
            return CustomModelDataUtil.get(stack).colors();
        }

        public static List<String> getHex(ItemStack stack) {
            List<String> result = new ArrayList<>();
            for (int rgb : getRaw(stack)) {
                result.add(toHex(rgb));
            }
            return result;
        }

        public static void add(ItemStack stack, int rgb) {
            List<Integer> colors = new ArrayList<>(getRaw(stack));
            colors.add(rgb);
            setRaw(stack, colors);
        }

        /** Удаляет первое вхождение цвета из списка. Возвращает false, если такого цвета не было. */
        public static boolean remove(ItemStack stack, int rgb) {
            List<Integer> colors = new ArrayList<>(getRaw(stack));
            boolean removed = colors.remove(Integer.valueOf(rgb));
            if (removed) {
                setRaw(stack, colors);
            }
            return removed;
        }

        public static void clear(ItemStack stack) {
            setRaw(stack, List.of());
        }

        private static void setRaw(ItemStack stack, List<Integer> colors) {
            CustomModelDataComponent old = CustomModelDataUtil.get(stack);
            CustomModelDataUtil.set(stack, new CustomModelDataComponent(old.floats(), old.flags(), old.strings(), colors));
        }

        /** Принимает "#ffffff", "ffffff" или "&#ffffff" — везде ровно 6 hex-символов. */
        public static int parseHex(String raw) throws IllegalArgumentException {
            String cleaned = raw.replace("&", "").replace("#", "").trim();
            if (cleaned.length() != 6) {
                throw new IllegalArgumentException(raw);
            }
            return Integer.parseInt(cleaned, 16);
        }

        public static String toHex(int rgb) {
            return String.format("#%06X", rgb & 0xFFFFFF);
        }
    }
}