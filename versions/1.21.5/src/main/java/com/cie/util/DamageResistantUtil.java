package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DamageResistantComponent;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class DamageResistantUtil {

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.DAMAGE_RESISTANT);
    }

    /**
     * @param damageTypeTag identifier of the damage type tag, e.g. "minecraft:is_fire" or "is_fire"
     *                       (namespace defaults to "minecraft" if omitted).
     */
    public static void set(ItemStack stack, String damageTypeTag) {
        Identifier id = Identifier.of(damageTypeTag);
        TagKey<DamageType> tag = TagKey.of(RegistryKeys.DAMAGE_TYPE, id);
        stack.set(DataComponentTypes.DAMAGE_RESISTANT, new DamageResistantComponent(tag));
    }

    public static String get(ItemStack stack) {
        DamageResistantComponent comp = stack.get(DataComponentTypes.DAMAGE_RESISTANT);
        if (comp == null) return "none";
        return comp.types().id().toString();
    }
}