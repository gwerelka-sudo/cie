package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;

public final class FoodComponentUtil {

    private FoodComponentUtil() {}

    public static FoodComponent getFood(ItemStack stack) {
        return stack.get(DataComponentTypes.FOOD);
    }

    public static void setNutrition(ItemStack stack, int nutrition) {
        FoodComponent current = getFood(stack);
        float saturation = current != null ? current.saturation() : 0.6f;
        boolean canAlwaysEat = current != null && current.canAlwaysEat();

        FoodComponent.Builder builder = new FoodComponent.Builder()
                .nutrition(nutrition)
                .saturationModifier(saturation);

        if (canAlwaysEat) builder.alwaysEdible();
        stack.set(DataComponentTypes.FOOD, builder.build());
    }

    public static void setSaturation(ItemStack stack, float saturation) {
        FoodComponent current = getFood(stack);
        int nutrition = current != null ? current.nutrition() : 1;
        boolean canAlwaysEat = current != null && current.canAlwaysEat();

        FoodComponent.Builder builder = new FoodComponent.Builder()
                .nutrition(nutrition)
                .saturationModifier(saturation);

        if (canAlwaysEat) builder.alwaysEdible();
        stack.set(DataComponentTypes.FOOD, builder.build());
    }

    public static void setCanAlwaysEat(ItemStack stack, boolean canAlwaysEat) {
        FoodComponent current = getFood(stack);
        int nutrition = current != null ? current.nutrition() : 1;
        float saturation = current != null ? current.saturation() : 0.6f;

        FoodComponent.Builder builder = new FoodComponent.Builder()
                .nutrition(nutrition)
                .saturationModifier(saturation);

        if (canAlwaysEat) builder.alwaysEdible();
        stack.set(DataComponentTypes.FOOD, builder.build());
    }

    public static void removeFood(ItemStack stack) {
        stack.remove(DataComponentTypes.FOOD);
    }
}