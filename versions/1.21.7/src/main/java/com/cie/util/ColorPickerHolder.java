package com.cie.util;

/**
 * Общее состояние — используется внутри ChatScreenMixin (создаёт виджет
 * и кнопку при открытии чата, а также рисует и прокидывает клики/клавиши —
 * висит на Screen.class, а не на ChatScreen, потому что render/mouseClicked
 * и т.п. гарантированно объявлены именно в Screen, а не переопределены в
 * ChatScreen).
 */
public final class ColorPickerHolder {

    private ColorPickerHolder() {
    }

    public static ColorPickerWidget widget;

    public static int buttonX, buttonY, buttonW, buttonH;

    // Перетаскивание самой кнопки-переключателя (не окна пикера).
    public static boolean draggingButton = false;
    public static int buttonDragOffsetX, buttonDragOffsetY;
    // true, как только мышь реально сдвинулась во время drag — используется
    // чтобы отличить "просто клик" (тогда открываем/закрываем пикер) от
    // "перетаскивание" (тогда клик не должен переключать открытость).
    public static boolean buttonDragMoved = false;
}
