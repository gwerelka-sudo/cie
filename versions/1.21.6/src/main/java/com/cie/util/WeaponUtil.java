package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WeaponComponent;
import net.minecraft.item.ItemStack;

/**
 * Утилита для работы с компонентом {@code minecraft:weapon}.
 * Компонент отвечает за то, сколько прочности снимается с предмета
 * при ударе по мобу, и на сколько секунд отключается блокирование
 * щитом при ударе таким предметом.
 */
public final class WeaponUtil {

    private static final int DEFAULT_DAMAGE_PER_ATTACK = 1;
    private static final float DEFAULT_DISABLE_BLOCKING_SECONDS = 0.0f;

    private WeaponUtil() {
    }

    public static WeaponComponent getWeapon(ItemStack stack) {
        return stack.get(DataComponentTypes.WEAPON);
    }

    public static int getDamagePerAttack(ItemStack stack) {
        WeaponComponent comp = getWeapon(stack);
        return comp != null ? comp.itemDamagePerAttack() : DEFAULT_DAMAGE_PER_ATTACK;
    }

    public static void setDamagePerAttack(ItemStack stack, int damagePerAttack) {
        WeaponComponent current = getWeapon(stack);
        float disablingSeconds = current != null ? current.disableBlockingForSeconds() : DEFAULT_DISABLE_BLOCKING_SECONDS;
        stack.set(DataComponentTypes.WEAPON, new WeaponComponent(damagePerAttack, disablingSeconds));
    }

    public static float getDisablingSeconds(ItemStack stack) {
        WeaponComponent comp = getWeapon(stack);
        return comp != null ? comp.disableBlockingForSeconds() : DEFAULT_DISABLE_BLOCKING_SECONDS;
    }

    public static void setDisablingSeconds(ItemStack stack, float seconds) {
        WeaponComponent current = getWeapon(stack);
        int damagePerAttack = current != null ? current.itemDamagePerAttack() : DEFAULT_DAMAGE_PER_ATTACK;
        stack.set(DataComponentTypes.WEAPON, new WeaponComponent(damagePerAttack, seconds));
    }

    public static void clearWeapon(ItemStack stack) {
        stack.remove(DataComponentTypes.WEAPON);
    }
}