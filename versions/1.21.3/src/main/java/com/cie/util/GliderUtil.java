package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Unit;

/**
 * Утилита для работы с маркерным компонентом {@code minecraft:glider}.
 * Компонент ничего не хранит (тип {@link Unit}) — его наличие на предмете
 * включает у элитр-подобных предметов способность планировать.
 */
public final class GliderUtil {

    private GliderUtil() {
    }

    public static boolean hasGlider(ItemStack stack) {
        return stack.contains(DataComponentTypes.GLIDER);
    }

    public static void setGlider(ItemStack stack, boolean value) {
        if (value) {
            stack.set(DataComponentTypes.GLIDER, Unit.INSTANCE);
        } else {
            stack.remove(DataComponentTypes.GLIDER);
        }
    }
}