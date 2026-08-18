package com.cie.util;

import com.mojang.serialization.DataResult;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseRemainderComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryWrapper;

/**
 * Утилита для работы с компонентом {@code minecraft:use_remainder} —
 * предметом, который остаётся в руке после того, как исходный предмет
 * был использован (потрачен) целиком, например миска после супа.
 */
public final class UseRemainderUtil {

    private UseRemainderUtil() {
    }

    public static ItemStack getRemainder(ItemStack stack) {
        UseRemainderComponent component = stack.get(DataComponentTypes.USE_REMAINDER);
        if (component == null) return ItemStack.EMPTY;
        // Попробуйте один из этих методов (посмотрите подсказку IDE):
        return component.convert(stack, 1, false, null); // Ванильный метод конвертации
    }

    public static void setRemainder(ItemStack stack, ItemStack remainder) {
        stack.set(DataComponentTypes.USE_REMAINDER, new UseRemainderComponent(remainder));
    }

    public static void removeRemainder(ItemStack stack) {
        stack.remove(DataComponentTypes.USE_REMAINDER);
    }

    /**
     * Строит {@link ItemStack} для use_remainder из материала, количества
     * и (опционально) строки изменений компонентов в формате SNBT,
     * например {@code {minecraft:custom_name:'{"text":"Bowl"}'}}.
     */
    public static ItemStack buildRemainderStack(Item item, int count, String componentsSnbt, RegistryWrapper.WrapperLookup registries) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = new ItemStack(item, count);
        if (componentsSnbt == null || componentsSnbt.isBlank()) {
            return stack;
        }

        NbtElement nbt = StringNbtReader.fromOps(NbtOps.INSTANCE).read(componentsSnbt);
        DataResult<ComponentChanges> result = ComponentChanges.CODEC.parse(registries.getOps(NbtOps.INSTANCE), nbt);
        ComponentChanges changes = result.getOrThrow();
        stack.applyChanges(changes);
        return stack;
    }
}