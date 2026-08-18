package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.JukeboxPlayableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.util.Identifier;

public class JukeboxPlayableUtil {

    /**
     * @param songId identifier of the jukebox song, e.g. "minecraft:13" or "13"
     *                (namespace defaults to "minecraft" if omitted).
     */
    public static void set(ItemStack stack, String songId) {
        Identifier id = Identifier.of(songId);
        RegistryKey<JukeboxSong> songKey = RegistryKey.of(RegistryKeys.JUKEBOX_SONG, id);
        net.minecraft.registry.entry.LazyRegistryEntryReference<JukeboxSong> ref =
                new net.minecraft.registry.entry.LazyRegistryEntryReference<>(songKey);
        stack.set(DataComponentTypes.JUKEBOX_PLAYABLE, new JukeboxPlayableComponent(ref));
    }

    public static String get(ItemStack stack) {
        JukeboxPlayableComponent comp = stack.get(DataComponentTypes.JUKEBOX_PLAYABLE);
        if (comp == null) return "none";
        return comp.song().getKey().toString();
    }

    public static void reset(ItemStack stack) {
        stack.remove(DataComponentTypes.JUKEBOX_PLAYABLE);
    }
}