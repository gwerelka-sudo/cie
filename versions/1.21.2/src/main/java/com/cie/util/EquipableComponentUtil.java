package com.cie.util;


import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;                  // можно оставить, если используется ещё где-то в файле; если больше нигде — тоже удалить
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class EquipableComponentUtil {

    private EquipableComponentUtil() {}

    public static EquippableComponent getEquipable(ItemStack stack) {
        return stack.get(DataComponentTypes.EQUIPPABLE);
    }

    public static void setSlot(ItemStack stack, EquipmentSlot slot) {
        EquippableComponent current = getEquipable(stack);

        EquippableComponent.Builder builder = EquippableComponent.builder(slot);

        if (current != null) {
            if (current.equipSound() != null) builder.equipSound(current.equipSound());

            current.model().ifPresent(builder::model);
            current.cameraOverlay().ifPresent(builder::cameraOverlay);
            current.allowedEntities().ifPresent(builder::allowedEntities);

            builder.dispensable(current.dispensable());
            builder.swappable(current.swappable());
            builder.damageOnHurt(current.damageOnHurt());
        }

        stack.set(DataComponentTypes.EQUIPPABLE, builder.build());
    }

    public static void setSound(ItemStack stack, RegistryEntry<SoundEvent> sound) {
        EquippableComponent current = getEquipable(stack);
        EquipmentSlot slot = current != null ? current.slot() : EquipmentSlot.CHEST;
        EquippableComponent.Builder builder = EquippableComponent.builder(slot);

        builder.equipSound(sound);
        stack.set(DataComponentTypes.EQUIPPABLE, builder.build());
    }

    public static void setModel(ItemStack stack, @Nullable Identifier modelId) {
        EquippableComponent current = getEquipable(stack);
        EquipmentSlot slot = current != null ? current.slot() : EquipmentSlot.CHEST;
        EquippableComponent.Builder builder = EquippableComponent.builder(slot);

        if (modelId != null) {
            builder.model(modelId);
        }
        stack.set(DataComponentTypes.EQUIPPABLE, builder.build());
    }

    public static void setFlag(ItemStack stack, String flag, boolean value) {
        EquippableComponent current = getEquipable(stack);
        EquipmentSlot slot = current != null ? current.slot() : EquipmentSlot.CHEST;
        EquippableComponent.Builder builder = EquippableComponent.builder(slot);

        // Переносим старые свойства если они были
        boolean dispensable = current != null ? current.dispensable() : true;
        boolean swappable = current != null ? current.swappable() : true;
        boolean damageOnHurt = current != null ? current.damageOnHurt() : true;

        switch (flag.toLowerCase()) {
            case "dispensable" -> dispensable = value;
            case "swappable" -> swappable = value;
            case "damageonhurt" -> damageOnHurt = value;
        }

        builder.dispensable(dispensable);
        builder.swappable(swappable);
        builder.damageOnHurt(damageOnHurt);

        stack.set(DataComponentTypes.EQUIPPABLE, builder.build());
    }

    public static void removeEquipable(ItemStack stack) {
        stack.remove(DataComponentTypes.EQUIPPABLE);
    }

    /** Сбрасывает только модель (assetId), оставляя остальные поля как есть. */
    public static void resetModel(ItemStack stack) {
        EquippableComponent current = getEquipable(stack);
        EquipmentSlot slot = current != null ? current.slot() : EquipmentSlot.CHEST;
        EquippableComponent.Builder builder = EquippableComponent.builder(slot);

        if (current != null) {
            if (current.equipSound() != null) builder.equipSound(current.equipSound());
            current.cameraOverlay().ifPresent(builder::cameraOverlay);
            current.allowedEntities().ifPresent(builder::allowedEntities);
            builder.dispensable(current.dispensable());
            builder.swappable(current.swappable());
            builder.damageOnHurt(current.damageOnHurt());
        }
        // model() намеренно не вызывается — вернётся дефолт для слота

        stack.set(DataComponentTypes.EQUIPPABLE, builder.build());
    }

    /** Сбрасывает только звук экипировки, оставляя остальные поля как есть. */
    public static void resetSound(ItemStack stack) {
        EquippableComponent current = getEquipable(stack);
        EquipmentSlot slot = current != null ? current.slot() : EquipmentSlot.CHEST;
        EquippableComponent.Builder builder = EquippableComponent.builder(slot);

        if (current != null) {
            current.model().ifPresent(builder::model);
            current.cameraOverlay().ifPresent(builder::cameraOverlay);
            current.allowedEntities().ifPresent(builder::allowedEntities);
            builder.dispensable(current.dispensable());
            builder.swappable(current.swappable());
            builder.damageOnHurt(current.damageOnHurt());
        }
        // equipSound() намеренно не вызывается — вернётся дефолт для слота

        stack.set(DataComponentTypes.EQUIPPABLE, builder.build());
    }
    public static void setEquipableFull(
            ItemStack stack,
            EquipmentSlot slot,
            RegistryEntry<SoundEvent> equipSound,
            Identifier modelId,
            Identifier cameraOverlay,
            boolean dispensable,
            boolean swappable,
            boolean damageOnHurt
    ) {
        EquippableComponent.Builder builder = EquippableComponent.builder(slot);

        if (equipSound != null) builder.equipSound(equipSound);
        if (modelId != null) {
            builder.model(modelId);
        }
        if (cameraOverlay != null) builder.cameraOverlay(cameraOverlay);

        builder.dispensable(dispensable);
        builder.swappable(swappable);
        builder.damageOnHurt(damageOnHurt);

        stack.set(DataComponentTypes.EQUIPPABLE, builder.build());
    }
}