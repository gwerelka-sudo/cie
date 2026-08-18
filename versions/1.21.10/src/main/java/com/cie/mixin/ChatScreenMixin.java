package com.cie.mixin;

import com.cie.util.ColorPickerDataUtil;
import com.cie.util.ColorPickerHolder;
import com.cie.util.ColorPickerWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * При каждом открытии чата: создаёт (один раз) или переиспользует
 * ColorPickerWidget через ColorPickerHolder, и ставит кнопку-переключатель
 * над строкой ввода — той же высоты/стиля, что и сама строка (не
 * ButtonWidget, рисуется вручную в ScreenMixin). Кнопка и виджет НЕ
 * регистрируются как дети Screen, поэтому не участвуют в
 * Tab/стрелка-навигации по фокусу.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow
    protected TextFieldWidget chatField;

    // Отступ кнопки от правого края поля ввода (сдвиг влево).
    // Увеличьте, если кнопка всё ещё выходит за пределы экрана.
    private static final int cie$BUTTON_RIGHT_OFFSET = 24;

    // Минимальный отступ от правого края экрана — подстраховка на случай
    // если chatField сам подходит слишком близко к краю монитора.
    private static final int cie$SCREEN_EDGE_MARGIN = 4;

    @Inject(method = "init", at = @At("TAIL"))
    private void cie$setupColorPicker(CallbackInfo ci) {
        ChatScreen self = (ChatScreen) (Object) this;

        ColorPickerHolder.buttonW = 12;
        ColorPickerHolder.buttonH = 12;

        int screenWidth = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();

        int savedX = ColorPickerDataUtil.getButtonX();
        int savedY = ColorPickerDataUtil.getButtonY();

        if (savedX >= 0 && savedY >= 0) {
            // Пользователь уже перетаскивал кнопку раньше — используем
            // сохранённую позицию, но подстраховываемся, чтобы она не
            // оказалась за пределами текущего окна (например, после
            // изменения разрешения).
            int maxX = screenWidth - ColorPickerHolder.buttonW - cie$SCREEN_EDGE_MARGIN;
            int maxY = screenHeight - ColorPickerHolder.buttonH - cie$SCREEN_EDGE_MARGIN;
            ColorPickerHolder.buttonX = Math.max(cie$SCREEN_EDGE_MARGIN, Math.min(savedX, maxX));
            ColorPickerHolder.buttonY = Math.max(cie$SCREEN_EDGE_MARGIN, Math.min(savedY, maxY));
        } else {
            // Дефолтная позиция: у правого края поля ввода, с отступом влево.
            int desiredX = chatField.getX() + chatField.getWidth()
                    - ColorPickerHolder.buttonW - cie$BUTTON_RIGHT_OFFSET;

            // Не даём кнопке выйти за правый край экрана.
            int maxX = screenWidth - ColorPickerHolder.buttonW - cie$SCREEN_EDGE_MARGIN;
            // Не даём кнопке уйти за левый край поля ввода.
            int minX = chatField.getX();

            ColorPickerHolder.buttonX = Math.max(minX, Math.min(desiredX, maxX));
            ColorPickerHolder.buttonY = chatField.getY() - ColorPickerHolder.buttonH - 2;
        }

        if (ColorPickerHolder.widget == null) {
            ColorPickerHolder.widget = new ColorPickerWidget(
                    MinecraftClient.getInstance().textRenderer,
                    text -> chatField.write(text));
            // изначальная позиция — рядом со строкой чата, если ещё не сохранялась
            if (ColorPickerHolder.widget.x < 0 || ColorPickerHolder.widget.y < 0) {
                ColorPickerHolder.widget.x = Math.max(4, chatField.getX());
                ColorPickerHolder.widget.y = Math.max(4, chatField.getY() - 20 - ColorPickerHolder.widget.height());
                ColorPickerHolder.widget.layout();
            }
        } else {
            ColorPickerHolder.widget.setInsertCallback(text -> chatField.write(text));
        }
    }
}