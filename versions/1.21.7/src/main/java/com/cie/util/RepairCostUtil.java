package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;

public class RepairCostUtil {

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.REPAIR_COST);
    }

    public static void set(ItemStack stack, int cost) {
        stack.set(DataComponentTypes.REPAIR_COST, cost);
    }

    public static int get(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.REPAIR_COST, 0);
    }

    public static void reset(ItemStack stack) {
        clear(stack);
    }
}