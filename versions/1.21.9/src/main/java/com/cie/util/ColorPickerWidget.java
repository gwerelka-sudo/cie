package com.cie.util;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.client.font.TextRenderer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Маленький перетаскиваемый color picker поверх чата.
 * НЕ Screen, НЕ ClickableWidget — рисуется и получает клики/клавиши вручную
 * из ChatScreenMixin, поэтому никогда не попадает в фокус-навигацию по
 * Tab/стрелкам, которая у Screen работает через список Selectable-детей.
 *
 * Лейаут (компактный):
 *  - строка сверху = drag-хэндл (потянуть за неё — двигать окно)
 *  - превью + палитра 2x3 слева
 *  - SV-квадрат + 3 вертикальные полосы (hue/sat/val) по центру
 *  - градиент-бар снизу
 *  - хекс-поле + кнопка формата
 */
public class ColorPickerWidget {

    private static final int DRAG_BAR_H = 12;

    public int x, y;
    public boolean open;

    private float hue, sat, val;

    private String[] palette;
    private boolean paletteEnabled;
    // Показана ли сама сетка 20 пресетов (раскрывается вниз по клику на eye).
    private boolean paletteGridOpen = false;

    private final List<ColorPickerDataUtil.GradientStop> stops = new ArrayList<>();
    private int selectedStop = -1;
    private boolean draggingStop = false;

    private String format;
    private boolean formatDropdownOpen = false;

    private boolean draggingWindow = false;
    private int dragOffsetX, dragOffsetY;

    private boolean draggingSV = false;
    private boolean draggingHue = false;
    private boolean draggingSat = false;
    private boolean draggingVal = false;

    // геометрия (компактная)
    private final int svSize = 110;
    private final int sliderW = 12;
    private final int sliderH = 110;
    private final int previewSize = 46;
    // Ширина левой колонки (превью + сетка пресетов) — берём максимум из
    // previewSize и ширины 5-колоночной сетки, чтобы сетка при раскрытии
    // вниз никогда не перекрывала SV-квадрат правее.
    private final int leftColW;
    private final int panelW;
    private int panelH;
    private final int panelHBase;

    private int svX, svY;
    private int hueX, satX, valX, sliderY;
    private int paletteX, paletteY;
    private int gradientX, gradientY, gradientW, gradientH;
    private int hexFieldX, hexFieldY, hexFieldW, hexFieldH;
    private int formatBtnX;
    private int eyeX, eyeY, eyeW, eyeH;

    // Сетка пресетов: 20 слотов, 5 колонок x 4 ряда, раскрывается ВНИЗ
    // от строки eye-переключателя (а не вбок, как раньше).
    private static final int PALETTE_COLS = 5;
    private static final int PALETTE_ROWS = 4;
    private static final int PALETTE_SLOTS = PALETTE_COLS * PALETTE_ROWS;
    private static final int PALETTE_CELL = 12;
    private static final int PALETTE_GAP = 2;
    private int paletteGridX, paletteGridY;
    private int paletteGridW, paletteGridH;

    private final TextFieldWidget hexField;
    private boolean hexFieldFocused = false;

    private final Consumer<String> insertIntoChatCallback;
    private Consumer<String> activeInsertCallback;

    public ColorPickerWidget(TextRenderer textRenderer, Consumer<String> insertIntoChatCallback) {
        UiColorUtil.registerDefault("colorPicker.panelBackground", 0x40FFFFFF);
        UiColorUtil.registerDefault("colorPicker.panelBorder", 0xFFFFFFFF);
        UiColorUtil.registerDefault("colorPicker.dragHandle", 0xFFE8E8E8);

        UiColorUtil.registerDefault("colorPicker.eyeButtonBackground", 0xFFE0E0E0);
        UiColorUtil.registerDefault("colorPicker.eyeButtonBackgroundHover", 0xFFFFFFFF);
        UiColorUtil.registerDefault("colorPicker.eyeButtonText", 0xFF202020);

        UiColorUtil.registerDefault("colorPicker.formatButtonBackground", 0xFFE0E0E0);
        UiColorUtil.registerDefault("colorPicker.formatButtonBackgroundHover", 0xFFFFFFFF);
        UiColorUtil.registerDefault("colorPicker.formatButtonText", 0xFF202020);

        UiColorUtil.registerDefault("colorPicker.formatDropdownBackground", 0xF0FFFFFF);
        UiColorUtil.registerDefault("colorPicker.formatDropdownBorder", 0xFFC0C0C0);
        UiColorUtil.registerDefault("colorPicker.formatDropdownRowHover", 0xFFD8E8FF);
        UiColorUtil.registerDefault("colorPicker.formatDropdownText", 0xFF202020);

        UiColorUtil.registerDefault("colorPicker.paletteGridBackground", 0xE0FFFFFF);
        UiColorUtil.registerDefault("colorPicker.paletteGridBorder", 0xFFC0C0C0);
        UiColorUtil.registerDefault("colorPicker.paletteSlotEmpty", 0xFFD8D8D8);
        UiColorUtil.registerDefault("colorPicker.paletteSlotHover", 0xFFFFFFFF);
        UiColorUtil.registerDefault("colorPicker.paletteSlotEmptyMark", 0xFF606060);

        UiColorUtil.registerDefault("colorPicker.gradientStopMarker", 0xFF404040);
        UiColorUtil.registerDefault("colorPicker.gradientStopMarkerSelected", 0xFFFFFFFF);

        this.insertIntoChatCallback = insertIntoChatCallback;
        this.activeInsertCallback = insertIntoChatCallback;

        float[] hsv = Color.RGBtoHSB(255, 0, 0, null);
        this.hue = hsv[0] * 360f;
        this.sat = hsv[1];
        this.val = hsv[2];

        this.palette = ColorPickerDataUtil.getPalette(); // 20 слотов
        this.paletteEnabled = ColorPickerDataUtil.isPaletteEnabled();
        this.format = ColorPickerDataUtil.getLastFormat();
        this.stops.addAll(ColorPickerDataUtil.getGradientStops());
        if (this.stops.isEmpty()) {
            this.stops.add(new ColorPickerDataUtil.GradientStop(0f, currentHexUpper()));
        }

        this.x = ColorPickerDataUtil.getWidgetX();
        this.y = ColorPickerDataUtil.getWidgetY();

        int paletteGridWCalc = PALETTE_COLS * PALETTE_CELL + (PALETTE_COLS - 1) * PALETTE_GAP;
        this.leftColW = Math.max(previewSize, paletteGridWCalc);

        this.panelW = 12 + leftColW + 10 + svSize + 8 + (sliderW * 3 + 6 * 2) + 12;
        this.panelHBase = DRAG_BAR_H + 10 + svSize + 8 + 20 + 8 + 18 + 10;
        this.panelH = panelHBase;

        this.hexField = new TextFieldWidget(textRenderer, 0, 0, 10, 14, Text.literal("hex"));
        this.hexField.setMaxLength(64);

        layout();
        refreshHexField();
    }

    /** Пересчитать координаты дочерних элементов от x,y окна. Вызывать после каждого сдвига. */
    public void layout() {
        svX = x + 12 + leftColW + 10;
        svY = y + DRAG_BAR_H + 10;

        sliderY = svY;
        hueX = svX + svSize + 8;
        satX = hueX + sliderW + 6;
        valX = satX + sliderW + 6;

        paletteX = x + 12;
        paletteY = svY;

        gradientX = svX;
        gradientY = svY + svSize + 8;
        gradientW = (valX + sliderW) - svX;
        gradientH = 14;

        hexFieldX = svX;
        hexFieldY = gradientY + gradientH + 10;
        hexFieldH = 16;
        hexFieldW = gradientW - 22;
        formatBtnX = hexFieldX + hexFieldW + 4;

        eyeX = paletteX;
        eyeY = paletteY + previewSize + 6;
        eyeW = leftColW;
        eyeH = 12;

        // Сетка пресетов раскрывается ВНИЗ от eye-кнопки (не вбок).
        paletteGridW = PALETTE_COLS * PALETTE_CELL + (PALETTE_COLS - 1) * PALETTE_GAP;
        paletteGridH = PALETTE_ROWS * PALETTE_CELL + (PALETTE_ROWS - 1) * PALETTE_GAP;
        paletteGridX = eyeX;
        paletteGridY = eyeY + eyeH + 4;

        hexField.setX(hexFieldX);
        hexField.setY(hexFieldY);
        hexField.setWidth(hexFieldW);

        // Когда сетка открыта, панель должна визуально вмещать её —
        // растягиваем высоту окна вниз (позиция x,y окна не меняется).
        panelH = panelHBase;
        if (paletteEnabled && paletteGridOpen) {
            int gridBottom = (paletteGridY + paletteGridH) - y;
            panelH = Math.max(panelHBase, gridBottom + 10);
        }
    }

    public int width() {
        return panelW;
    }

    public void setInsertCallback(Consumer<String> callback) {
        this.activeInsertCallback = callback;
    }

    public int height() {
        return panelH;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + panelW && my >= y && my < y + panelH;
    }

    public boolean isHexFieldFocused() {
        return hexFieldFocused;
    }

    // ============================================================
    //  Рендер
    // ============================================================

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!open) return;

        context.fill(x, y, x + panelW, y + panelH, UiColorUtil.get("colorPicker.panelBackground"));
        drawRectBorder(context, x, y, panelW, panelH, UiColorUtil.get("colorPicker.panelBorder"));
        // drag-хэндл
        context.fill(x + 1, y + 1, x + panelW - 1, y + DRAG_BAR_H, UiColorUtil.get("colorPicker.dragHandle"));

        drawPreview(context);
        if (paletteEnabled) drawPalette(context, mouseX, mouseY);
        drawEyeToggle(context, textRenderer, mouseX, mouseY);
        drawSvSquare(context);
        drawSlider(context, hueX, s -> Color.HSBtoRGB(s, 1f, 1f));
        drawSlider(context, satX, s -> Color.HSBtoRGB(hue / 360f, s, 1f));
        drawSlider(context, valX, s -> Color.HSBtoRGB(hue / 360f, 1f, 1f - s));
        drawSliderCursorFor(context);
        drawGradientBar(context);

        hexField.render(context, mouseX, mouseY, 0f);
        drawFormatButton(context, textRenderer, mouseX, mouseY);

        if (formatDropdownOpen) {
            drawFormatDropdown(context, textRenderer, mouseX, mouseY);
        }
    }

    private void drawRectBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawPreview(DrawContext context) {
        int rgb = currentRgb();
        context.fill(paletteX - 1, paletteY - 1, paletteX + previewSize + 1, paletteY + previewSize + 1, 0xFF808080);
        context.fill(paletteX, paletteY, paletteX + previewSize, paletteY + previewSize, 0xFF000000 | rgb);
    }

    private void drawEyeToggle(DrawContext context, TextRenderer tr, int mouseX, int mouseY) {
        boolean hovered = inBounds(mouseX, mouseY, eyeX, eyeY, eyeW, eyeH);
        context.fill(eyeX, eyeY, eyeX + eyeW, eyeY + eyeH,
                hovered ? UiColorUtil.get("colorPicker.eyeButtonBackgroundHover") : UiColorUtil.get("colorPicker.eyeButtonBackground"));
        String arrow = paletteGridOpen ? "⮭" : "⮮"; // указывает, раскрыта ли сетка вниз
        String label = "\uD83C\uDFA8 " + arrow; // символ палитры + стрелка показать/скрыть
        context.drawText(tr, label, eyeX + 3, eyeY + 2, UiColorUtil.get("colorPicker.eyeButtonText"), false);
    }

    private void drawPalette(DrawContext context, int mouseX, int mouseY) {
        // Сетка 20 пресетов (5 колонок x 4 ряда), раскрывается ВНИЗ от
        // eye-переключателя. Показывается только когда сетка открыта —
        // клик по eye её открывает/закрывает.
        if (!paletteGridOpen) return;

        context.fill(paletteGridX - 2, paletteGridY - 2,
                paletteGridX + paletteGridW + 2, paletteGridY + paletteGridH + 2, UiColorUtil.get("colorPicker.paletteGridBackground"));
        drawRectBorder(context, paletteGridX - 2, paletteGridY - 2,
                paletteGridW + 4, paletteGridH + 4, UiColorUtil.get("colorPicker.paletteGridBorder"));

        for (int i = 0; i < PALETTE_SLOTS; i++) {
            int col = i % PALETTE_COLS;
            int row = i / PALETTE_COLS;
            int sx = paletteGridX + col * (PALETTE_CELL + PALETTE_GAP);
            int sy = paletteGridY + row * (PALETTE_CELL + PALETTE_GAP);
            String hex = palette[i];
            boolean hovered = inBounds(mouseX, mouseY, sx, sy, PALETTE_CELL, PALETTE_CELL);
            context.fill(sx, sy, sx + PALETTE_CELL, sy + PALETTE_CELL,
                    hovered ? UiColorUtil.get("colorPicker.paletteSlotHover") : UiColorUtil.get("colorPicker.paletteSlotEmpty"));
            if (hex != null) {
                int rgb = Integer.parseInt(hex, 16);
                context.fill(sx + 1, sy + 1, sx + PALETTE_CELL - 1, sy + PALETTE_CELL - 1, 0xFF000000 | rgb);
            } else {
                // пустой слот — крестик-плейсхолдер, чтобы было видно, что сюда можно ПКМ сохранить цвет
                int markColor = UiColorUtil.get("colorPicker.paletteSlotEmptyMark");
                context.fill(sx + PALETTE_CELL / 2 - 1, sy + 3, sx + PALETTE_CELL / 2, sy + PALETTE_CELL - 3, markColor);
                context.fill(sx + 3, sy + PALETTE_CELL / 2 - 1, sx + PALETTE_CELL - 3, sy + PALETTE_CELL / 2, markColor);
            }
        }
    }

    private void drawSvSquare(DrawContext context) {
        int hueRgb = Color.HSBtoRGB(hue / 360f, 1f, 1f) & 0xFFFFFF;
        context.fill(svX, svY, svX + svSize, svY + svSize, 0xFF000000 | hueRgb);
        for (int px = 0; px < svSize; px += 2) {
            int alpha = 255 - (int) (255f * px / svSize);
            context.fill(svX + px, svY, svX + px + 2, svY + svSize, (alpha << 24) | 0xFFFFFF);
        }
        for (int py = 0; py < svSize; py += 2) {
            int alpha = (int) (255f * py / svSize);
            context.fill(svX, svY + py, svX + svSize, svY + py + 2, (alpha << 24));
        }
        drawRectBorder(context, svX - 1, svY - 1, svSize + 2, svSize + 2, 0xFFC0C0C0);

        int cx = svX + Math.round(sat * svSize);
        int cy = svY + Math.round((1f - val) * svSize);
        context.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFFFFFFFF);
        context.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF000000 | currentRgb());
    }

    private interface HueFn {
        int rgb(float s);
    }

    private void drawSlider(DrawContext context, int sx, HueFn fn) {
        for (int py = 0; py < sliderH; py++) {
            int rgb = fn.rgb(py / (float) sliderH) & 0xFFFFFF;
            context.fill(sx, sliderY + py, sx + sliderW, sliderY + py + 1, 0xFF000000 | rgb);
        }
        drawRectBorder(context, sx - 1, sliderY - 1, sliderW + 2, sliderH + 2, 0xFFC0C0C0);
    }

    private void drawSliderCursorFor(DrawContext context) {
        drawCursor(context, hueX, sliderY + Math.round(hue / 360f * sliderH));
        drawCursor(context, satX, sliderY + Math.round(sat * sliderH));
        drawCursor(context, valX, sliderY + Math.round((1f - val) * sliderH));
    }

    private void drawCursor(DrawContext context, int sx, int sy) {
        context.fill(sx - 1, sy - 1, sx + sliderW + 1, sy, 0xFFFFFFFF);
        context.fill(sx - 1, sy, sx + sliderW + 1, sy + 1, 0xFF000000);
    }

    private void drawGradientBar(DrawContext context) {
        List<ColorPickerDataUtil.GradientStop> sorted = new ArrayList<>(stops);
        sorted.sort((a, b) -> Float.compare(a.position(), b.position()));
        for (int px = 0; px < gradientW; px++) {
            float pos = px / (float) gradientW;
            int rgb = interpolateStops(sorted, pos);
            context.fill(gradientX + px, gradientY, gradientX + px + 1, gradientY + gradientH, 0xFF000000 | rgb);
        }
        drawRectBorder(context, gradientX - 1, gradientY - 1, gradientW + 2, gradientH + 2, 0xFFC0C0C0);
        for (int i = 0; i < stops.size(); i++) {
            int px = gradientX + Math.round(stops.get(i).position() * gradientW);
            int color = i == selectedStop
                    ? UiColorUtil.get("colorPicker.gradientStopMarkerSelected")
                    : UiColorUtil.get("colorPicker.gradientStopMarker");
            context.fill(px - 2, gradientY + gradientH + 1, px + 2, gradientY + gradientH + 5, color);
        }
    }

    private void drawFormatButton(DrawContext context, TextRenderer tr, int mouseX, int mouseY) {
        int w = hexFieldH;
        boolean hovered = inBounds(mouseX, mouseY, formatBtnX, hexFieldY, w, hexFieldH);
        context.fill(formatBtnX, hexFieldY, formatBtnX + w, hexFieldY + hexFieldH,
                hovered ? UiColorUtil.get("colorPicker.formatButtonBackgroundHover") : UiColorUtil.get("colorPicker.formatButtonBackground"));
        context.drawText(tr, "⮮", formatBtnX + w / 2 - 2, hexFieldY + hexFieldH / 2 - 3, UiColorUtil.get("colorPicker.formatButtonText"), false);
    }

    private void drawFormatDropdown(DrawContext context, TextRenderer tr, int mouseX, int mouseY) {
        List<String> formats = GradientFormatUtil.listAllFormats();
        int w = 100;
        int rowH = 12;
        int h = formats.size() * rowH + 4;
        int dx = formatBtnX - 84;
        int dy = hexFieldY + hexFieldH + 2;
        context.fill(dx, dy, dx + w, dy + h, UiColorUtil.get("colorPicker.formatDropdownBackground"));
        drawRectBorder(context, dx, dy, w, h, UiColorUtil.get("colorPicker.formatDropdownBorder"));
        for (int i = 0; i < formats.size(); i++) {
            int rowY = dy + 2 + i * rowH;
            boolean hovered = inBounds(mouseX, mouseY, dx, rowY, w, rowH);
            if (hovered) context.fill(dx, rowY, dx + w, rowY + rowH, UiColorUtil.get("colorPicker.formatDropdownRowHover"));
            context.drawText(tr, formats.get(i), dx + 3, rowY + 2, UiColorUtil.get("colorPicker.formatDropdownText"), false);
        }
    }

    // ============================================================
    //  Ввод
    // ============================================================

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;

        if (formatDropdownOpen) {
            if (handleFormatDropdownClick(mouseX, mouseY)) return true;
            formatDropdownOpen = false;
        }

        if (inBounds(mouseX, mouseY, x + 1, y + 1, panelW - 2, DRAG_BAR_H - 1)) {
            draggingWindow = true;
            dragOffsetX = (int) mouseX - x;
            dragOffsetY = (int) mouseY - y;
            return true;
        }

        boolean onHexField = inBounds(mouseX, mouseY, hexFieldX, hexFieldY, hexFieldW, hexFieldH);
        if (onHexField && button == 1) {
            activeInsertCallback.accept(ColorPickerFormatUtil.render(format, currentHexUpper()));
            return true;
        }
        if (inBounds(mouseX, mouseY, formatBtnX, hexFieldY, hexFieldH, hexFieldH)) {
            formatDropdownOpen = !formatDropdownOpen;
            return true;
        }
        hexFieldFocused = onHexField;
        hexField.setFocused(onHexField);
        if (onHexField) {
            return true;
        }

        if (inBounds(mouseX, mouseY, eyeX, eyeY, eyeW, eyeH)) {
            // И ЛКМ, и ПКМ по кнопке палитры делают одно и то же — просто
            // показывают/скрывают сетку пресетов. Полностью отключить эту
            // функцию через ПКМ больше нельзя.
            if (!paletteEnabled) {
                paletteEnabled = true;
                ColorPickerDataUtil.setPaletteEnabled(true);
            }
            paletteGridOpen = !paletteGridOpen;
            layout();
            return true;
        }

        if (paletteEnabled && paletteGridOpen && handlePaletteClick(mouseX, mouseY, button)) return true;

        if (inBounds(mouseX, mouseY, svX, svY, svSize, svSize)) {
            draggingSV = true;
            updateSv(mouseX, mouseY);
            return true;
        }
        if (inBounds(mouseX, mouseY, hueX, sliderY, sliderW, sliderH)) {
            draggingHue = true;
            updateHue(mouseY);
            return true;
        }
        if (inBounds(mouseX, mouseY, satX, sliderY, sliderW, sliderH)) {
            draggingSat = true;
            updateSat(mouseY);
            return true;
        }
        if (inBounds(mouseX, mouseY, valX, sliderY, sliderW, sliderH)) {
            draggingVal = true;
            updateVal(mouseY);
            return true;
        }
        if (inBounds(mouseX, mouseY, gradientX, gradientY - 2, gradientW, gradientH + 8)) {
            handleGradientClick(mouseX, mouseY, button);
            return true;
        }

        // клик внутри окна, но мимо всех элементов — всё равно поглощаем,
        // чтобы не проваливался в чат
        return contains(mouseX, mouseY);
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!open) return false;
        if (draggingWindow) {
            x = (int) mouseX - dragOffsetX;
            y = (int) mouseY - dragOffsetY;
            layout();
            return true;
        }
        if (draggingSV) {
            updateSv(mouseX, mouseY);
            return true;
        }
        if (draggingHue) {
            updateHue(mouseY);
            return true;
        }
        if (draggingSat) {
            updateSat(mouseY);
            return true;
        }
        if (draggingVal) {
            updateVal(mouseY);
            return true;
        }
        if (draggingStop && selectedStop >= 0) {
            float pos = clamp01((float) (mouseX - gradientX) / gradientW);
            stops.set(selectedStop, new ColorPickerDataUtil.GradientStop(pos, stops.get(selectedStop).hex()));
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY) {
        if (!open) return false;
        boolean was = draggingWindow || draggingSV || draggingHue || draggingSat || draggingVal || draggingStop;
        if (draggingWindow) {
            ColorPickerDataUtil.setWidgetPosition(x, y);
        }
        if (draggingStop) {
            ColorPickerDataUtil.setGradientStops(stops);
        }
        draggingWindow = draggingSV = draggingHue = draggingSat = draggingVal = draggingStop = false;
        return was;
    }

    /** true если клавиша "съедена" хекс-полем. */
    public boolean keyPressed(KeyInput input) {
        if (!open || !hexFieldFocused) return false;
        if (input.key() == 257 || input.key() == 335) { // Enter / KP_Enter
            tryApplyTypedHex();
            return true;
        }
        return hexField.keyPressed(input);
    }

    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (!open || !hexFieldFocused) return false;
        return hexField.charTyped(input);
    }

    private boolean handleFormatDropdownClick(double mouseX, double mouseY) {
        List<String> formats = GradientFormatUtil.listAllFormats();
        int w = 100;
        int rowH = 12;
        int h = formats.size() * rowH + 4;
        int dx = formatBtnX - 84;
        int dy = hexFieldY + hexFieldH + 2;
        if (!inBounds(mouseX, mouseY, dx, dy, w, h)) return false;
        int index = (int) ((mouseY - (dy + 2)) / rowH);
        if (index >= 0 && index < formats.size()) {
            format = formats.get(index);
            ColorPickerDataUtil.setLastFormat(format);
            refreshHexField();
        }
        formatDropdownOpen = false;
        return true;
    }

    private boolean handlePaletteClick(double mouseX, double mouseY, int button) {
        if (!inBounds(mouseX, mouseY, paletteGridX - 2, paletteGridY - 2, paletteGridW + 4, paletteGridH + 4)) {
            return false;
        }
        for (int i = 0; i < PALETTE_SLOTS; i++) {
            int col = i % PALETTE_COLS;
            int row = i / PALETTE_COLS;
            int sx = paletteGridX + col * (PALETTE_CELL + PALETTE_GAP);
            int sy = paletteGridY + row * (PALETTE_CELL + PALETTE_GAP);
            if (inBounds(mouseX, mouseY, sx, sy, PALETTE_CELL, PALETTE_CELL)) {
                if (button == 1) {
                    // ПКМ по слоту — сохранить текущий цвет в этот слот.
                    palette[i] = currentHexUpper();
                    ColorPickerDataUtil.setPaletteSlot(i, palette[i]);
                } else if (palette[i] != null) {
                    // ЛКМ по заполненному слоту — применить сохранённый цвет.
                    applyHex(palette[i]);
                }
                return true;
            }
        }
        // Клик внутри рамки сетки, но мимо всех слотов — всё равно поглощаем.
        return true;
    }

    private void handleGradientClick(double mouseX, double mouseY, int button) {
        for (int i = 0; i < stops.size(); i++) {
            int px = gradientX + Math.round(stops.get(i).position() * gradientW);
            if (Math.abs(mouseX - px) <= 4) {
                if (button == 1) {
                    if (stops.size() > 1) stops.remove(i);
                    ColorPickerDataUtil.setGradientStops(stops);
                } else {
                    selectedStop = i;
                    draggingStop = true;
                }
                return;
            }
        }
        float pos = clamp01((float) (mouseX - gradientX) / gradientW);
        stops.add(new ColorPickerDataUtil.GradientStop(pos, currentHexUpper()));
        ColorPickerDataUtil.setGradientStops(stops);
    }

    private void tryApplyTypedHex() {
        String raw = hexField.getText().replaceAll("[^0-9a-fA-F]", "");
        if (raw.length() >= 6) {
            applyHex(raw.substring(raw.length() - 6).toUpperCase());
        }
    }

    private void updateSv(double mouseX, double mouseY) {
        sat = clamp01((float) (mouseX - svX) / svSize);
        val = 1f - clamp01((float) (mouseY - svY) / svSize);
        refreshHexField();
    }

    private void updateHue(double mouseY) {
        hue = clamp01((float) (mouseY - sliderY) / sliderH) * 360f;
        refreshHexField();
    }

    private void updateSat(double mouseY) {
        sat = clamp01((float) (mouseY - sliderY) / sliderH);
        refreshHexField();
    }

    private void updateVal(double mouseY) {
        val = 1f - clamp01((float) (mouseY - sliderY) / sliderH);
        refreshHexField();
    }

    private void applyHex(String hexUpper) {
        int rgb = Integer.parseInt(hexUpper, 16);
        float[] hsv = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        hue = hsv[0] * 360f;
        sat = hsv[1];
        val = hsv[2];
        refreshHexField();
    }

    private void refreshHexField() {
        hexField.setText(ColorPickerFormatUtil.render(format, currentHexUpper()));
    }

    public int currentRgb() {
        return Color.HSBtoRGB(hue / 360f, sat, val) & 0xFFFFFF;
    }

    private String currentHexUpper() {
        return String.format("%06X", currentRgb());
    }

    private int interpolateStops(List<ColorPickerDataUtil.GradientStop> sorted, float pos) {
        if (sorted.isEmpty()) return 0xFFFFFF;
        if (sorted.size() == 1 || pos <= sorted.get(0).position()) {
            return Integer.parseInt(sorted.get(0).hex(), 16);
        }
        for (int i = 0; i < sorted.size() - 1; i++) {
            var a = sorted.get(i);
            var b = sorted.get(i + 1);
            if (pos >= a.position() && pos <= b.position()) {
                float t = b.position() == a.position() ? 0 : (pos - a.position()) / (b.position() - a.position());
                int rgbA = Integer.parseInt(a.hex(), 16);
                int rgbB = Integer.parseInt(b.hex(), 16);
                int r = lerp((rgbA >> 16) & 0xFF, (rgbB >> 16) & 0xFF, t);
                int g = lerp((rgbA >> 8) & 0xFF, (rgbB >> 8) & 0xFF, t);
                int bl = lerp(rgbA & 0xFF, rgbB & 0xFF, t);
                return (r << 16) | (g << 8) | bl;
            }
        }
        return Integer.parseInt(sorted.get(sorted.size() - 1).hex(), 16);
    }

    private int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private boolean inBounds(double mx, double my, int px, int py, int w, int h) {
        return mx >= px && mx < px + w && my >= py && my < py + h;
    }
}