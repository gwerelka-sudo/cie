package com.cie.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Optional;

/**
 * Утилита для /cie (без аргументов) — собирает info-экран мода: версия,
 * автор, кликабельные ссылки на Modrinth/Telegram/GitHub.
 *
 * Все значения читаются из fabric.mod.json ЧЕРЕЗ FabricLoader в рантайме
 * (не хардкодятся здесь) — так версия всегда актуальная (плейсхолдер
 * "${version}" в fabric.mod.json подставляется Loom при сборке).
 *
 * ВАЖНО: ClickEvent.OpenUrl — по аналогии с уже используемым в проекте
 * ClickEvent.CopyToClipboard (см. gradientNode/sendGradientResult в
 * CIECommand). Принимает java.net.URI. Если в вашей сборке record
 * называется иначе — пришлите ошибку компиляции, поправим точечно.
 */
public final class ModInfoUtil {

    private ModInfoUtil() {
    }

    private static final String MOD_ID = "cie";
    private static final int ACCENT_COLOR = 0x9FD4F2;
    private static final int LABEL_COLOR = 0xAAAAAA;
    private static final int LINK_COLOR = 0x66F2BA;

    public static MutableText buildInfoScreen() {
        Optional<net.fabricmc.loader.api.ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (container.isEmpty()) {
            return Text.literal("CIE").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(ACCENT_COLOR)));
        }
        ModMetadata meta = container.get().getMetadata();

        String name = meta.getName();
        String version = meta.getVersion().getFriendlyString();
        String authors = meta.getAuthors().isEmpty()
                ? "?"
                : String.join(", ", meta.getAuthors().stream().map(a -> a.getName()).toList());

        MutableText result = Text.empty();
        result.append(coloredLine(name, ACCENT_COLOR)).append(Text.literal("\n"));
        result.append(labeledLine("Версия", version)).append(Text.literal("\n"));
        result.append(labeledLine("Автор", authors)).append(Text.literal("\n"));

        meta.getContact().get("homepage").ifPresent(url ->
                result.append(linkLine("Modrinth", url)).append(Text.literal("\n")));
        meta.getContact().get("sources").ifPresent(url ->
                result.append(linkLine("GitHub", url)).append(Text.literal("\n")));
        meta.getContact().get("telegram").ifPresent(url ->
                result.append(linkLine("Telegram", url)));

        return result;
    }

    private static MutableText coloredLine(String text, int rgb) {
        return Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true));
    }

    private static MutableText labeledLine(String label, String value) {
        return Text.empty()
                .append(Text.literal(label + ": ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(LABEL_COLOR))))
                .append(Text.literal(value).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
    }

    private static MutableText linkLine(String label, String url) {
        Style linkStyle = Style.EMPTY
                .withColor(TextColor.fromRgb(LINK_COLOR))
                .withUnderline(true)
                .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)));
        return Text.empty()
                .append(Text.literal(label + ": ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(LABEL_COLOR))))
                .append(Text.literal(url).setStyle(linkStyle));
    }
}
