package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Rarity;

public class RarityUtil {

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.RARITY);
    }

    public static void set(ItemStack stack, String rarityName) {
        Rarity rarity = switch (rarityName.toLowerCase()) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
        stack.set(DataComponentTypes.RARITY, rarity);
    }

    public static String get(ItemStack stack) {
        Rarity rarity = stack.get(DataComponentTypes.RARITY);
        if (rarity == null) rarity = stack.getItem().getComponents().get(DataComponentTypes.RARITY);
        if (rarity == null) rarity = Rarity.COMMON;
        return rarity.name().toLowerCase();
    }
}