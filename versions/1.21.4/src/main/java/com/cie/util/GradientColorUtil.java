package com.cie.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Вычисляет список (символ -> hex-цвет) для /cie gradient create ...
 * Сама отрисовка в конкретный формат — в GradientFormatUtil, здесь
 * только цветовая математика.
 */
public final class GradientColorUtil {

    private GradientColorUtil() {
    }

    public record CharColor(char character, String hexUpper) {
    }

    private static final Pattern HEX_CLEAN = Pattern.compile("^#?([0-9a-fA-F]{6})$");

    /**
     * "<spc>" -> обычный пробел. РАНЬШЕ использовался во всех create-командах
     * перед обработкой текста, потому что <text> был не последним аргументом
     * и парсился как StringArgumentType.string() (без пробелов). Теперь
     * <text> — последний аргумент в каждой gradient create-подкоманде и
     * читается как greedyString(), так что пробелы вводятся как есть и
     * этот плейсхолдер больше не нужен. Метод оставлен на случай, если
     * где-то ещё пригодится ручная замена "<spc>" на пробел.
     */
    public static String resolveSpacePlaceholder(String text) {
        return text.replace("<spc>", " ");
    }

    public static List<String> parseHexList(String csv) {
        List<String> result = new ArrayList<>();
        for (String raw : csv.split(",")) {
            result.add(normalizeHex(raw.trim()));
        }
        return result;
    }

    public static String normalizeHex(String raw) {
        var matcher = HEX_CLEAN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(raw);
        }
        return matcher.group(1).toUpperCase();
    }

    // ============================================================
    //  gradient — линейная интерполяция между N цветами-стопами
    // ============================================================

    public static List<CharColor> gradient(String text, List<String> hexColors) {
        if (hexColors.isEmpty()) {
            throw new IllegalArgumentException("empty_colors");
        }
        List<CharColor> result = new ArrayList<>();
        int length = text.length();
        if (length == 0) {
            return result;
        }
        if (hexColors.size() == 1 || length == 1) {
            String hex = hexColors.get(0);
            for (int i = 0; i < length; i++) {
                result.add(new CharColor(text.charAt(i), hex));
            }
            return result;
        }

        int segments = hexColors.size() - 1;
        for (int i = 0; i < length; i++) {
            double position = (double) i / (length - 1); // 0..1
            double scaled = position * segments;
            int segmentIndex = Math.min((int) scaled, segments - 1);
            double t = scaled - segmentIndex;

            int[] from = hexToRgb(hexColors.get(segmentIndex));
            int[] to = hexToRgb(hexColors.get(segmentIndex + 1));
            int r = lerp(from[0], to[0], t);
            int g = lerp(from[1], to[1], t);
            int b = lerp(from[2], to[2], t);
            result.add(new CharColor(text.charAt(i), rgbToHex(r, g, b)));
        }
        return result;
    }

    // ============================================================
    //  rainbow — HSB-радуга
    // ============================================================

    /** 7 классических цветов радуги (ROYGBIV), стартовый hue для shade 1..7. */
    private static final float[] SHADE_HUES = {0f, 30f, 60f, 120f, 240f, 275f, 300f};

    public static List<CharColor> rainbow(String text, int saturationPercent, int brightnessPercent, int shade, int step) {
        int length = text.length();
        List<CharColor> result = new ArrayList<>();
        if (length == 0) {
            return result;
        }

        float startHue = SHADE_HUES[Math.max(0, Math.min(shade - 1, SHADE_HUES.length - 1))];
        float saturation = clamp01(saturationPercent / 100f);
        float brightness = clamp01(brightnessPercent / 100f);
        // step (1-100) -> какая доля полного круга (360°) растягивается по всей длине текста
        float totalHueSpan = 360f * (Math.max(1, Math.min(step, 100)) / 100f);

        for (int i = 0; i < length; i++) {
            float progress = length == 1 ? 0f : (float) i / (length - 1);
            float hue = (startHue + progress * totalHueSpan) % 360f;
            int rgb = java.awt.Color.HSBtoRGB(hue / 360f, saturation, brightness) & 0xFFFFFF;
            result.add(new CharColor(text.charAt(i), String.format("%06X", rgb)));
        }
        return result;
    }

    // ============================================================
    //  alternation — чередование цветов блоками по distance символов
    // ============================================================

    public static List<CharColor> alternation(String text, int distance, List<String> hexColors) {
        List<String> colors = (hexColors == null || hexColors.isEmpty())
                ? List.of("FFFFFF", "AAAAAA")
                : hexColors;
        int dist = Math.max(1, Math.min(distance, 100));

        List<CharColor> result = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            int colorIndex = (i / dist) % colors.size();
            result.add(new CharColor(text.charAt(i), colors.get(colorIndex)));
        }
        return result;
    }

    // ============================================================
    //  random — colorsCount случайных hex-цветов, дальше как обычный градиент
    // ============================================================

    public static List<CharColor> random(String text, int colorsCount) {
        int count = Math.max(1, colorsCount);
        List<String> colors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int rgb = ThreadLocalRandom.current().nextInt(0x1000000);
            colors.add(String.format("%06X", rgb));
        }
        return gradient(text, colors);
    }

    // ============================================================
    //  вспомогательное
    // ============================================================

    private static int[] hexToRgb(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
    }

    private static String rgbToHex(int r, int g, int b) {
        return String.format("%06X", (r << 16) | (g << 8) | b);
    }

    private static int lerp(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}