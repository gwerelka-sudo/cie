package com.cie.util;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class AttributeComponentUtil {

    private AttributeComponentUtil() {
    }

    public static AttributeModifiersComponent getOrCreate(ItemStack stack) {
        AttributeModifiersComponent comp = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        return comp != null ? comp : AttributeModifiersComponent.DEFAULT;
    }

    public static AttributeModifiersComponent addModifier(
            AttributeModifiersComponent original,
            RegistryEntry<EntityAttribute> attribute,
            Identifier modifierId,
            double amount,
            EntityAttributeModifier.Operation operation,
            AttributeModifierSlot slot
    ) {
        List<AttributeModifiersComponent.Entry> modifiers = new ArrayList<>(original.modifiers());
        modifiers.removeIf(entry -> entry.modifier().id().equals(modifierId));

        EntityAttributeModifier modifier = new EntityAttributeModifier(modifierId, amount, operation);
        modifiers.add(new AttributeModifiersComponent.Entry(attribute, modifier, slot));

        return new AttributeModifiersComponent(modifiers, true);
    }

    public static AttributeModifiersComponent removeModifier(AttributeModifiersComponent original, Identifier modifierId) {
        List<AttributeModifiersComponent.Entry> modifiers = new ArrayList<>(original.modifiers());
        modifiers.removeIf(entry -> entry.modifier().id().equals(modifierId));

        return new AttributeModifiersComponent(modifiers, true);
    }
}