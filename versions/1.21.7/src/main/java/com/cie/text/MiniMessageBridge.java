package com.cie.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MiniMessageBridge {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.standard())
            .build();
    private static final GsonComponentSerializer GSON_SERIALIZER = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();

    // Перехватываем <sprite:atlas:sprite_path>
    private static final Pattern SPRITE_PATTERN = Pattern.compile("<sprite:([^:]+):([^>]+)>");

    private MiniMessageBridge() {}

    /**
     * Парсинг MiniMessage строки с поддержкой плейсхолдеров (TagResolver...)
     */
    public static Component parse(String miniMessage, TagResolver... resolvers) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(miniMessage, resolvers);
    }

    public static Text miniMessageToVanilla(String miniMessage, RegistryWrapper.WrapperLookup registries) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return Text.empty();
        }

        if (!miniMessage.contains("<sprite:")) {
            return toVanillaText(parse(miniMessage), registries);
        }

        // 1. Находим все спрайты и подменяем их на спец-теги MiniMessage
        Matcher matcher = SPRITE_PATTERN.matcher(miniMessage);
        java.util.List<String[]> sprites = new java.util.ArrayList<>();
        StringBuffer sb = new StringBuffer();

        int index = 0;
        while (matcher.find()) {
            sprites.add(new String[]{matcher.group(1), matcher.group(2)});
            matcher.appendReplacement(sb, "<lang:'renamix_sprite_" + index + "'>");
            index++;
        }
        matcher.appendTail(sb);

        // 2. Парсим всю строку со всеми её стилями
        Text vanillaText = toVanillaText(parse(sb.toString()), registries);

        // 3. Рекурсивно подменяем маркеры на реальные объекты спрайтов
        RegistryOps<JsonElement> ops = registries.getOps(JsonOps.INSTANCE);
        return replaceSpriteMarkers(vanillaText, sprites, ops);
    }

    private static Text replaceSpriteMarkers(Text text, java.util.List<String[]> sprites, RegistryOps<JsonElement> ops) {
        MutableText currentResult = null;

        if (text.getContent() instanceof net.minecraft.text.TranslatableTextContent translatable) {
            String key = translatable.getKey();
            if (key.startsWith("renamix_sprite_")) {
                int index = Integer.parseInt(key.replace("renamix_sprite_", ""));
                String[] spriteData = sprites.get(index);
                String atlas = spriteData[0];
                String sprite = spriteData[1];

                String spriteJson = String.format("{\"sprite\":\"%s\",\"atlas\":\"%s\"}", sprite, atlas);
                try {
                    JsonElement jsonElement = JsonParser.parseString(spriteJson);
                    Text spriteText = TextCodecs.CODEC.parse(ops, jsonElement).getOrThrow(IllegalStateException::new);

                    // Оборачиваем в MutableText для сохранения возможности делать .append()
                    currentResult = Text.empty().append(spriteText).setStyle(text.getStyle());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (currentResult == null) {
            currentResult = MutableText.of(text.getContent()).setStyle(text.getStyle());
        }

        for (Text sibling : text.getSiblings()) {
            currentResult.append(replaceSpriteMarkers(sibling, sprites, ops));
        }

        return currentResult;
    }

    public static Text toVanillaText(Component component, RegistryWrapper.WrapperLookup registries) {
        JsonElement json = GSON_SERIALIZER.serializeToTree(component);
        RegistryOps<JsonElement> ops = registries.getOps(JsonOps.INSTANCE);
        return TextCodecs.CODEC.parse(ops, json)
                .getOrThrow(error -> new IllegalStateException("Не удалось разобрать текст: " + error));
    }

    public static Component fromVanillaText(Text text, RegistryWrapper.WrapperLookup registries) {
        RegistryOps<JsonElement> ops = registries.getOps(JsonOps.INSTANCE);
        JsonElement json = TextCodecs.CODEC.encodeStart(ops, text)
                .getOrThrow(error -> new IllegalStateException("Не удалось сериализовать текст: " + error));

        JsonElement sanitizedJson = sanitizeSpriteJson(json);
        return GSON_SERIALIZER.deserializeFromTree(sanitizedJson);
    }

    private static JsonElement sanitizeSpriteJson(JsonElement element) {
        if (element.isJsonObject()) {
            com.google.gson.JsonObject obj = element.getAsJsonObject();

            if (obj.has("sprite")) {
                com.google.gson.JsonObject cleaned = new com.google.gson.JsonObject();
                cleaned.addProperty("text", "");

                if (obj.has("color")) cleaned.add("color", obj.get("color"));
                if (obj.has("bold")) cleaned.add("bold", obj.get("bold"));
                if (obj.has("italic")) cleaned.add("italic", obj.get("italic"));
                if (obj.has("extra")) cleaned.add("extra", sanitizeSpriteJson(obj.get("extra")));

                return cleaned;
            }

            com.google.gson.JsonObject result = new com.google.gson.JsonObject();
            for (java.util.Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                result.add(entry.getKey(), sanitizeSpriteJson(entry.getValue()));
            }
            return result;
        } else if (element.isJsonArray()) {
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            for (JsonElement item : element.getAsJsonArray()) {
                array.add(sanitizeSpriteJson(item));
            }
            return array;
        }

        return element;
    }

    public static String toJson(Text text, RegistryWrapper.WrapperLookup registries) {
        RegistryOps<JsonElement> ops = registries.getOps(JsonOps.INSTANCE);
        JsonElement json = TextCodecs.CODEC.encodeStart(ops, text)
                .getOrThrow(error -> new IllegalStateException("Не удалось сериализовать текст: " + error));
        return json.toString();
    }

    public static String toMiniMessage(Component component) {
        return MINI_MESSAGE.serialize(component);
    }

    public static String toPlain(Component component) {
        return PLAIN_SERIALIZER.serialize(component);
    }

    public static String vanillaToPlain(Text text, RegistryWrapper.WrapperLookup registries) {
        return toPlain(fromVanillaText(text, registries));
    }

    public static String vanillaToMiniMessage(Text text, RegistryWrapper.WrapperLookup registries) {
        return toMiniMessage(fromVanillaText(text, registries));
    }
}