package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Утилита для работы с компонентом {@code minecraft:item_model}.
 * Компонент хранит идентификатор модели, которая используется
 * для рендера предмета (вместо модели самого материала предмета).
 */
public final class ItemModelUtil {

    private ItemModelUtil() {
    }

    public static Identifier getItemModel(ItemStack stack) {
        return stack.get(DataComponentTypes.ITEM_MODEL);
    }

    public static void setItemModel(ItemStack stack, Identifier model) {
        stack.set(DataComponentTypes.ITEM_MODEL, model);
    }

    public static void removeItemModel(ItemStack stack) {
        stack.remove(DataComponentTypes.ITEM_MODEL);
    }
}