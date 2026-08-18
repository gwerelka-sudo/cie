package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.JukeboxPlayableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryPair;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.util.Identifier;

public class JukeboxPlayableUtil {

    /**
     * [1.21.4] JukeboxPlayableComponent на этой версии — record(RegistryPair<JukeboxSong> song, boolean showInTooltip),
     * а не record(LazyRegistryEntryReference<JukeboxSong>) как в 1.21.5+. RegistryPair — из пакета
     * net.minecraft.registry (НЕ .entry!), у него есть удобный конструктор RegistryPair(RegistryKey<T> key).
     * showInTooltip задаём true по умолчанию (как было в новом API до его появления явным параметром).
     *
     * @param songId identifier of the jukebox song, e.g. "minecraft:13" or "13"
     *                (namespace defaults to "minecraft" if omitted).
     */
    public static void set(ItemStack stack, String songId) {
        Identifier id = Identifier.of(songId);
        RegistryKey<JukeboxSong> songKey = RegistryKey.of(RegistryKeys.JUKEBOX_SONG, id);
        RegistryPair<JukeboxSong> pair = new RegistryPair<>(songKey);
        stack.set(DataComponentTypes.JUKEBOX_PLAYABLE, new JukeboxPlayableComponent(pair, true));
    }

    public static String get(ItemStack stack) {
        JukeboxPlayableComponent comp = stack.get(DataComponentTypes.JUKEBOX_PLAYABLE);
        if (comp == null) return "none";
        return comp.song().key().getValue().toString();
    }

    public static void reset(ItemStack stack) {
        stack.remove(DataComponentTypes.JUKEBOX_PLAYABLE);
    }
}