package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.registry.entry.RegistryEntry;

public final class TrimComponentUtil {

    private TrimComponentUtil() {
    }

    public static ArmorTrim getTrim(ItemStack stack) {
        return stack.get(DataComponentTypes.TRIM);
    }

    public static void setTrim(ItemStack stack, RegistryEntry<ArmorTrimPattern> pattern, RegistryEntry<ArmorTrimMaterial> material) {
        // Конструктор принимает только material и pattern
        stack.set(DataComponentTypes.TRIM, new ArmorTrim(material, pattern));
    }

    public static void removeTrim(ItemStack stack) {
        stack.remove(DataComponentTypes.TRIM);
    }
}