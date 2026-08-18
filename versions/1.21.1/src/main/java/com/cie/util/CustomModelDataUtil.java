package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;

/**
 * Утилита для работы с компонентом `minecraft:custom_model_data`.
 *
 * ВЕРСИЯ 1.21.3: компонент — простое int-значение (CustomModelDataComponent
 * с единственным полем value), как это было ещё с NBT-эпохи (CustomModelData:
 * <int> до 1.20.5). Структура из четырёх независимых списков (floats/flags/
 * strings/colors) появилась только в 1.21.4 — на этой версии её нет.
 */
public final class CustomModelDataUtil {

    private CustomModelDataUtil() {
    }

    public static int get(ItemStack stack) {
        CustomModelDataComponent comp = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return comp != null ? comp.value() : 0;
    }

    public static boolean has(ItemStack stack) {
        return stack.contains(DataComponentTypes.CUSTOM_MODEL_DATA);
    }

    public static void set(ItemStack stack, int value) {
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(value));
    }

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
    }
}