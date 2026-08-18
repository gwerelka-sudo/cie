package com.cie.util;

/**
 * Форматирует ОДИН hex-цвет (без прицепленного символа) под нужную нотацию
 * для хекс-поля color picker'а. Список форматов и custom-форматы берутся
 * из GradientFormatUtil (единый источник правды), но здесь без символа —
 * пикеру нужен просто код цвета, который можно скопировать/вставить.
 */
public final class ColorPickerFormatUtil {

    private ColorPickerFormatUtil() {
    }

    /** hexUpper — 6 hex-символов без '#'. */
    public static String render(String format, String hexUpper) {
        switch (format) {
            case "legacy":
                return "#" + hexUpper;
            case "legacy&":
                return "&#" + hexUpper;
            case "json":
                return "{\"color\":\"#" + hexUpper + "\"}";
            case "minimessage":
                return "<#" + hexUpper + ">";
            case "old":
                return renderOld(hexUpper);
            case "bbcode":
                return "[COLOR=#" + hexUpper + "]";
            case "motd":
                return renderMotd(hexUpper);
            case "xml":
                return "<color:#" + hexUpper + ">";
            default:
                GradientFormatUtil.CustomFormat custom = GradientFormatUtil.getCustomFormat(format);
                if (custom == null) {
                    return "#" + hexUpper;
                }
                return renderCustom(custom, hexUpper);
        }
    }

    private static String renderOld(String hex) {
        StringBuilder sb = new StringBuilder("&x");
        for (char c : hex.toCharArray()) {
            sb.append('&').append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String renderMotd(String hex) {
        StringBuilder sb = new StringBuilder("\u00a7x");
        for (char c : hex.toCharArray()) {
            sb.append('\u00a7').append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String renderCustom(GradientFormatUtil.CustomFormat format, String hex) {
        String sep = format.separator() == null ? "" : format.separator();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < hex.length(); i++) {
            if (i > 0) digits.append(sep);
            digits.append(Character.toLowerCase(hex.charAt(i)));
        }
        return format.prefix() + digits + format.suffix();
    }
}
