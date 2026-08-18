package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
// В зависимости от вашей версии компонента анимации руки (например, use_animation или кастомные данные)
public class SwingAnimationUtil {

    public static class AnimationSub {
        public static void set(ItemStack stack, String type) { }
        public static String get(ItemStack stack) { return "normal"; }
        public static void reset(ItemStack stack) { }
    }

    public static class DurationSub {
        public static void set(ItemStack stack, int duration) { }
        public static int get(ItemStack stack) { return 0; }
        public static void reset(ItemStack stack) { }
    }

    public static void clear(ItemStack stack) {
        // Очистка параметров анимации
    }
}