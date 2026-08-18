package com.cie.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class MaterialUtil {

    private MaterialUtil() {}

    public static Identifier getMaterial(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem());
    }

    public static ItemStack setMaterial(ItemStack stack, Item newMaterial) {
        ItemStack newStack = new ItemStack(newMaterial, stack.getCount());

        // Копируем все компоненты со старого предмета на новый
        newStack.applyChanges(stack.getComponentChanges());

        return newStack;
    }
}