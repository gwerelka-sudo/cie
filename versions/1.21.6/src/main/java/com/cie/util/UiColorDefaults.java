package com.cie.util;

/**
 * Централизованный реестр дефолтов UiColorUtil. Раньше registerDefault(...)
 * вызывался прямо в конструкторе ColorPickerWidget / static-блоке
 * armorStandEditScreen — а значит ключ появлялся в таб-комплите
 * "/cie paint <key>" только ПОСЛЕ того, как соответствующий экран
 * реально открыли хотя бы раз (JVM грузит класс лениво).
 *
 * Этот класс собирает все те же вызовы в одном месте и должен вызываться
 * ОДИН РАЗ при старте клиента — тогда все ключи видны в автодополнении
 * сразу, без открытия экранов.
 *
 * ПОДКЛЮЧЕНИЕ: добавьте одну строку в самое начало метода onInitializeClient()
 * вашего ClientModInitializer (обычно класс называется что-то вроде
 * CIEClientMod / CIEModClient — я не вижу этот файл в текущей выгрузке,
 * так что добавьте сами):
 *
 *     UiColorDefaults.registerAll();
 *
 * Повторные вызовы (например если где-то ещё остался старый
 * registerDefault в конструкторе виджета/static-блоке экрана) не страшны —
 * registerDefault просто перезаписывает дефолт тем же значением, override
 * пользователя (то, что реально лежит в ui_colors.json) это не трогает.
 */
public final class UiColorDefaults {

    private UiColorDefaults() {
    }

    public static void registerAll() {
        // ---- colorPicker: панель / drag-хэндл ----
        UiColorUtil.registerDefault("colorPicker.panelBackground", 0x40FFFFFF);
        UiColorUtil.registerDefault("colorPicker.panelBorder", 0xFFFFFFFF);
        UiColorUtil.registerDefault("colorPicker.dragHandle", 0xFFE8E8E8);

        // ---- colorPicker: кнопка-переключатель над чатом ----
        UiColorUtil.registerDefault("colorPicker.toggleButtonBackground", 0x90000000);
        UiColorUtil.registerDefault("colorPicker.toggleButtonBackgroundHover", 0xC0000000);
        UiColorUtil.registerDefault("colorPicker.toggleButtonText", 0xFFFFFFFF);

        // ---- colorPicker: eye-переключатель палитры ----
        UiColorUtil.registerDefault("colorPicker.eyeButtonBackground", 0xFFE0E0E0);
        UiColorUtil.registerDefault("colorPicker.eyeButtonBackgroundHover", 0xFFFFFFFF);
        UiColorUtil.registerDefault("colorPicker.eyeButtonText", 0xFF202020);

        // ---- colorPicker: кнопка формата хекс-поля ----
        UiColorUtil.registerDefault("colorPicker.formatButtonBackground", 0xFFE0E0E0);
        UiColorUtil.registerDefault("colorPicker.formatButtonBackgroundHover", 0xFFFFFFFF);
        UiColorUtil.registerDefault("colorPicker.formatButtonText", 0xFF202020);

        // ---- colorPicker: выпадающий список форматов ----
        UiColorUtil.registerDefault("colorPicker.formatDropdownBackground", 0xF0FFFFFF);
        UiColorUtil.registerDefault("colorPicker.formatDropdownBorder", 0xFFC0C0C0);
        UiColorUtil.registerDefault("colorPicker.formatDropdownRowHover", 0xFFD8E8FF);
        UiColorUtil.registerDefault("colorPicker.formatDropdownText", 0xFF202020);

        // ---- colorPicker: сетка палитры-пресетов ----
        UiColorUtil.registerDefault("colorPicker.paletteGridBackground", 0xE0FFFFFF);
        UiColorUtil.registerDefault("colorPicker.paletteGridBorder", 0xFFC0C0C0);
        UiColorUtil.registerDefault("colorPicker.paletteSlotEmpty", 0xFFD8D8D8);
        UiColorUtil.registerDefault("colorPicker.paletteSlotHover", 0xFFFFFFFF);
        UiColorUtil.registerDefault("colorPicker.paletteSlotEmptyMark", 0xFF606060);

        // ---- colorPicker: маркеры стопов градиента ----
        UiColorUtil.registerDefault("colorPicker.gradientStopMarker", 0xFF404040);
        UiColorUtil.registerDefault("colorPicker.gradientStopMarkerSelected", 0xFFFFFFFF);

        // ---- armorStandMenu ----
        UiColorUtil.registerDefault("armorStandMenu.panelBackground", 0xE0202020);
        UiColorUtil.registerDefault("armorStandMenu.titleBar", 0xE0303030);
        UiColorUtil.registerDefault("armorStandMenu.panelBorder", 0xFF606060);
        UiColorUtil.registerDefault("armorStandMenu.previewBackground", 0xFF101010);
        UiColorUtil.registerDefault("armorStandMenu.titleText", 0xFFFFFF);
        UiColorUtil.registerDefault("armorStandMenu.previewFallbackText", 0x808080);
        UiColorUtil.registerDefault("armorStandMenu.poseLabelText", 0xA0F5D0);
        UiColorUtil.registerDefault("armorStandMenu.statusLineText", 0xF2C866);

        // ---- colorPick: /cie pickColor — экран-глазпипетка (замороженный кадр) ----
        UiColorUtil.registerDefault("colorPick.magnifierBackground", 0xD0000000);
        UiColorUtil.registerDefault("colorPick.magnifierBorder", 0xFFFFFFFF);
        UiColorUtil.registerDefault("colorPick.swatchBorder", 0xFF000000);
        UiColorUtil.registerDefault("colorPick.hexText", 0xFFFFFFFF);
        UiColorUtil.registerDefault("colorPick.hintText", 0xFFFFFFFF);
    }
}