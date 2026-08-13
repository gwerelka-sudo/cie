package com.cie.util;

/**
 * Общее состояние между ChatScreenMixin (создаёт виджет и кнопку при
 * открытии чата) и ScreenMixin (рисует и прокидывает клики/клавиши —
 * висит на Screen.class, а не на ChatScreen, потому что render/mouseClicked
 * и т.п. гарантированно объявлены именно в Screen, а не переопределены в
 * ChatScreen).
 */
public final class ColorPickerHolder {

    private ColorPickerHolder() {
    }

    public static ColorPickerWidget widget;

    public static int buttonX, buttonY, buttonW, buttonH;
}
