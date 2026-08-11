package com.cie.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Обёртка над DataComponentTypes.FIREWORKS — время полёта ракеты + список
 * взрывов (форма, основные цвета, цвета затухания, след, мерцание).
 *
 * РИСК ДЛЯ КОМПИЛЯЦИИ: имена record-полей FireworksComponent/
 * FireworkExplosionComponent и вложенный enum Type — если что-то не
 * компилируется, открой genSources → FireworkExplosionComponent, поправь
 * только этот файл.
 */
public final class FireworksUtil {

    private FireworksUtil() {
    }

    public static FireworksComponent getOrCreate(ItemStack stack) {
        FireworksComponent existing = stack.get(DataComponentTypes.FIREWORKS);
        return existing != null ? existing : new FireworksComponent(1, List.of());
    }

    public static void save(ItemStack stack, FireworksComponent component) {
        stack.set(DataComponentTypes.FIREWORKS, component);
    }

    public static void remove(ItemStack stack) {
        stack.remove(DataComponentTypes.FIREWORKS);
    }

    public static FireworksComponent withFlightDuration(FireworksComponent component, int duration) {
        return new FireworksComponent(duration, component.explosions());
    }

    public static FireworksComponent withExtraExplosion(FireworksComponent component, FireworkExplosionComponent explosion) {
        List<FireworkExplosionComponent> list = new ArrayList<>(component.explosions());
        list.add(explosion);
        return new FireworksComponent(component.flightDuration(), list);
    }

    public static FireworksComponent withReplacedExplosion(FireworksComponent component, int index, FireworkExplosionComponent explosion) {
        List<FireworkExplosionComponent> list = new ArrayList<>(component.explosions());
        list.set(index, explosion);
        return new FireworksComponent(component.flightDuration(), list);
    }

    public static FireworksComponent withoutExplosion(FireworksComponent component, int index) {
        List<FireworkExplosionComponent> list = new ArrayList<>(component.explosions());
        list.remove(index);
        return new FireworksComponent(component.flightDuration(), list);
    }

    public static FireworksComponent withClearedExplosions(FireworksComponent component) {
        return new FireworksComponent(component.flightDuration(), List.of());
    }

    public static FireworkExplosionComponent withColor(FireworkExplosionComponent explosion, int color) {
        IntList colors = new IntArrayList(explosion.colors());
        colors.add(color);
        return new FireworkExplosionComponent(explosion.shape(), colors, explosion.fadeColors(), explosion.hasTrail(), explosion.hasTwinkle());
    }

    public static FireworkExplosionComponent withFadeColor(FireworkExplosionComponent explosion, int color) {
        IntList colors = new IntArrayList(explosion.fadeColors());
        colors.add(color);
        return new FireworkExplosionComponent(explosion.shape(), explosion.colors(), colors, explosion.hasTrail(), explosion.hasTwinkle());
    }

    public static FireworkExplosionComponent withoutColor(FireworkExplosionComponent explosion, int colorIndex, boolean fade) {
        if (fade) {
            IntList colors = new IntArrayList(explosion.fadeColors());
            colors.removeInt(colorIndex);
            return new FireworkExplosionComponent(explosion.shape(), explosion.colors(), colors, explosion.hasTrail(), explosion.hasTwinkle());
        }
        IntList colors = new IntArrayList(explosion.colors());
        colors.removeInt(colorIndex);
        return new FireworkExplosionComponent(explosion.shape(), colors, explosion.fadeColors(), explosion.hasTrail(), explosion.hasTwinkle());
    }

    public static FireworkExplosionComponent withClearedColors(FireworkExplosionComponent explosion, boolean fade) {
        if (fade) {
            return new FireworkExplosionComponent(explosion.shape(), explosion.colors(), new IntArrayList(), explosion.hasTrail(), explosion.hasTwinkle());
        }
        return new FireworkExplosionComponent(explosion.shape(), new IntArrayList(), explosion.fadeColors(), explosion.hasTrail(), explosion.hasTwinkle());
    }

    public static FireworkExplosionComponent withShape(FireworkExplosionComponent explosion, FireworkExplosionComponent.Type shape) {
        return new FireworkExplosionComponent(shape, explosion.colors(), explosion.fadeColors(), explosion.hasTrail(), explosion.hasTwinkle());
    }

    public static FireworkExplosionComponent withTrail(FireworkExplosionComponent explosion, boolean trail) {
        return new FireworkExplosionComponent(explosion.shape(), explosion.colors(), explosion.fadeColors(), trail, explosion.hasTwinkle());
    }

    public static FireworkExplosionComponent withTwinkle(FireworkExplosionComponent explosion, boolean twinkle) {
        return new FireworkExplosionComponent(explosion.shape(), explosion.colors(), explosion.fadeColors(), explosion.hasTrail(), twinkle);
    }
}