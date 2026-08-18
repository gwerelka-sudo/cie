package com.cie.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Операции над minecraft:profile (ProfileComponent) — компонентом голов
 * игрока (player_head), который несёт GameProfile (имя/uuid/свойства,
 * включая "textures" со скином). Тот же общий паттерн, что и у
 * EquipableComponentUtil/TrimComponentUtil: не проверяет, что предмет в
 * руке — именно player_head, просто читает/пишет DataComponentTypes.PROFILE
 * у любого предмета с этим компонентом.
 *
 * ВАЖНО про getFromPlayer: НЕ делает никаких сетевых запросов к
 * Mojang/session-серверу самостоятельно. Профиль (и его properties с
 * base64-текстурой) берётся из уже полученного клиентом PlayerListEntry
 * (клиентский таб-лист). Это единственный надёжный способ корректно
 * подхватить скин, подменённый прокси-плагинами вроде SkinsRestorer —
 * такие плагины переписывают именно пакет player_info (тот, из которого
 * строится PlayerListEntry) на сервере ещё до отправки клиенту, так что к
 * моменту, когда игрок появляется в таб-листе, клиент уже видит
 * подменённые properties. Если запрашивать текстуру напрямую с Mojang по
 * UUID/нику (как это делают многие "голова игрока" команды), скин
 * SkinsRestorer теряется — именно поэтому тут сознательно используется
 * только локальный таб-лист, а не отдельный HTTP-запрос.
 */
public final class PlayerHeadUtil {

    private PlayerHeadUtil() {}

    // ================================================================
    //  чтение
    // ================================================================

    public static ProfileComponent getProfile(ItemStack stack) {
        return stack.get(DataComponentTypes.PROFILE);
    }

    public static boolean hasProfile(ItemStack stack) {
        return stack.contains(DataComponentTypes.PROFILE);
    }

    /** Человекочитаемое summary для /cie edit playerHead get. */
    public static String describe(ItemStack stack) {
        ProfileComponent profile = getProfile(stack);
        if (profile == null) {
            return null;
        }
        String name = profile.name().orElse("?");
        String uuid = profile.uniqueId().map(UUID::toString).orElse("?");
        boolean hasTexture = profile.properties().get("textures").stream().findAny().isPresent();
        return name + " / " + uuid + " (текстура: " + (hasTexture ? "есть" : "нет") + ")";
    }

    // ================================================================
    //  запись
    // ================================================================

    /**
     * Ставит на предмет "голую" текстуру: случайный UUID (не привязан ни к
     * одному реальному игроку — это нормально для кастомных голов, ровно
     * так работает и ванильный /give ... player_head[profile={properties:...}])
     * и один property "textures" с переданным base64. Подпись (signature)
     * намеренно не выставляется — офлайн/самодельные текстуры её не имеют,
     * а на официальных серверах непроверенная подпись всё равно не даст
     * прохождения проверки подлинности, так что смысла добавлять пустышку нет.
     */
    public static void setTexture(ItemStack stack, String base64Texture) {
        PropertyMap properties = new PropertyMap();
        properties.put("textures", new Property("textures", base64Texture));
        ProfileComponent profile = new ProfileComponent(Optional.empty(), Optional.of(UUID.randomUUID()), properties);
        stack.set(DataComponentTypes.PROFILE, profile);
    }

    /**
     * Ставит на предмет ПОЛНЫЙ профиль из GameProfile — вместе с
     * реальным именем/uuid игрока и ВСЕМИ его properties как есть
     * (см. describe про SkinsRestorer — properties копируются один в
     * один из уже подменённого сервером профиля).
     */
    public static void setFromGameProfile(ItemStack stack, GameProfile source) {
        PropertyMap properties = new PropertyMap();
        for (Property property : source.getProperties().values()) {
            properties.put(property.name(), property);
        }
        ProfileComponent profile = new ProfileComponent(
                Optional.ofNullable(source.getName()),
                Optional.ofNullable(source.getId()),
                properties);
        stack.set(DataComponentTypes.PROFILE, profile);
    }

    public static void reset(ItemStack stack) {
        stack.remove(DataComponentTypes.PROFILE);
    }

    // ================================================================
    //  онлайн-игроки (для getFromPlayer и его таб-комплита)
    // ================================================================

    /** Список ников всех игроков, видимых сейчас в клиентском таб-листе. */
    public static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) {
            return names;
        }
        for (PlayerListEntry entry : handler.getPlayerList()) {
            names.add(entry.getProfile().getName());
        }
        return names;
    }

    /**
     * Находит GameProfile игрока по нику в клиентском таб-листе
     * (регистронезависимо). Возвращает null, если игрока сейчас не видно
     * в таб-листе (не в сети / ещё не пришёл player_info пакет).
     */
    public static GameProfile findOnlineProfile(String name) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) {
            return null;
        }
        for (PlayerListEntry entry : handler.getPlayerList()) {
            if (entry.getProfile().getName().equalsIgnoreCase(name)) {
                return entry.getProfile();
            }
        }
        return null;
    }
}
