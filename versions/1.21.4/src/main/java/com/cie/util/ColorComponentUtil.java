package com.cie.util;

import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.MapColorComponent;
import net.minecraft.item.ItemStack;

public final class ColorComponentUtil {

    private ColorComponentUtil() {}

    public static Integer getDyedColor(ItemStack stack) {
        DyedColorComponent comp = stack.get(DataComponentTypes.DYED_COLOR);
        return comp != null ? comp.rgb() : null;
    }

    public static void setDyedColor(ItemStack stack, int color) {
        stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color, true));
    }

    public static void removeDyedColor(ItemStack stack) {
        stack.remove(DataComponentTypes.DYED_COLOR);
    }

    public static Integer getMapColor(ItemStack stack) {
        MapColorComponent comp = stack.get(DataComponentTypes.MAP_COLOR);
        return comp != null ? comp.rgb() : null;
    }

    public static void setMapColor(ItemStack stack, int color) {
        stack.set(DataComponentTypes.MAP_COLOR, new MapColorComponent(color));
    }

    public static void removeMapColor(ItemStack stack) {
        stack.remove(DataComponentTypes.MAP_COLOR);
    }
}