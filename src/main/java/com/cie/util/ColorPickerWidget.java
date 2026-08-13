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
    private final int panelW;
    private final int panelH;

    private int svX, svY;
    private int hueX, satX, valX, sliderY;
    private int paletteX, paletteY;
    private int gradientX, gradientY, gradientW, gradientH;
    private int hexFieldX, hexFieldY, hexFieldW, hexFieldH;
    private int formatBtnX;
    private int eyeX, eyeY, eyeW, eyeH;

    private final TextFieldWidget hexField;
    private boolean hexFieldFocused = false;

    private final Consumer<String> insertIntoChatCallback;
    private Consumer<String> activeInsertCallback;

    public ColorPickerWidget(TextRenderer textRenderer, Consumer<String> insertIntoChatCallback) {
        this.insertIntoChatCallback = insertIntoChatCallback;
        this.activeInsertCallback = insertIntoChatCallback;

        float[] hsv = Color.RGBtoHSB(255, 0, 0, null);
        this.hue = hsv[0] * 360f;
        this.sat = hsv[1];
        this.val = hsv[2];

        this.palette = ColorPickerDataUtil.getPalette();
        this.paletteEnabled = ColorPickerDataUtil.isPaletteEnabled();
        this.format = ColorPickerDataUtil.getLastFormat();
        this.stops.addAll(ColorPickerDataUtil.getGradientStops());
        if (this.stops.isEmpty()) {
            this.stops.add(new ColorPickerDataUtil.GradientStop(0f, currentHexUpper()));
        }

        this.x = ColorPickerDataUtil.getWidgetX();
        this.y = ColorPickerDataUtil.getWidgetY();

        this.panelW = 12 + previewSize + 10 + svSize + 8 + (sliderW * 3 + 6 * 2) + 12;
        this.panelH = DRAG_BAR_H + 10 + svSize + 8 + 20 + 8 + 18 + 10;

        this.hexField = new TextFieldWidget(textRenderer, 0, 0, 10, 14, Text.literal("hex"));
        this.hexField.setMaxLength(64);

        layout();
        refreshHexField();
    }

    /** Пересчитать координаты дочерних элементов от x,y окна. Вызывать после каждого сдвига. */
    public void layout() {
        svX = x + 12 + previewSize + 10;
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
        eyeW = previewSize;
        eyeH = 12;

        hexField.setX(hexFieldX);
        hexField.setY(hexFieldY);
        hexField.setWidth(hexFieldW);
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

    public void render(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!open) return;

        context.fill(x, y, x + panelW, y + panelH, 0xE8181818);
        drawRectBorder(context, x, y, panelW, panelH, 0xFF707070);
        // drag-хэндл
        context.fill(x + 1, y + 1, x + panelW - 1, y + DRAG_BAR_H, 0xFF303030);

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

    private void drawEyeToggle(DrawContext context, net.minecraft.client.font.TextRenderer tr, int mouseX, int mouseY) {
        boolean hovered = inBounds(mouseX, mouseY, eyeX, eyeY, eyeW, eyeH);
        context.fill(eyeX, eyeY, eyeX + eyeW, eyeY + eyeH, hovered ? 0xFF505050 : 0xFF383838);
        context.drawText(tr, paletteEnabled ? "\u25C9 on" : "\u25CB off", eyeX + 3, eyeY + 2, 0xFFC0C0C0, false);
    }

    private void drawPalette(DrawContext context, int mouseX, int mouseY) {
        // палитра рисуется отдельной строкой ниже превью, если понадобится —
        // сейчас компактно используем клики по превью+eye для управления,
        // саму сетку слотов держим справа от eye-переключателя в одну строку
        int cell = 12;
        int gy = eyeY;
        for (int i = 0; i < 6; i++) {
            int sx = eyeX + eyeW + 4 + i * (cell + 2);
            String hex = palette[i];
            boolean hovered = inBounds(mouseX, mouseY, sx, gy, cell, cell);
            context.fill(sx, gy, sx + cell, gy + cell, hovered ? 0xFFA0A0A0 : 0xFF505050);
            if (hex != null) {
                int rgb = Integer.parseInt(hex, 16);
                context.fill(sx + 1, gy + 1, sx + cell - 1, gy + cell - 1, 0xFF000000 | rgb);
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
        drawRectBorder(context, svX - 1, svY - 1, svSize + 2, svSize + 2, 0xFF707070);

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
        drawRectBorder(context, sx - 1, sliderY - 1, sliderW + 2, sliderH + 2, 0xFF707070);
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
        drawRectBorder(context, gradientX - 1, gradientY - 1, gradientW + 2, gradientH + 2, 0xFF707070);
        for (int i = 0; i < stops.size(); i++) {
            int px = gradientX + Math.round(stops.get(i).position() * gradientW);
            int color = i == selectedStop ? 0xFFFFFFFF : 0xFFD0D0D0;
            context.fill(px - 2, gradientY + gradientH + 1, px + 2, gradientY + gradientH + 5, color);
        }
    }

    private void drawFormatButton(DrawContext context, net.minecraft.client.font.TextRenderer tr, int mouseX, int mouseY) {
        boolean hovered = inBounds(mouseX, mouseY, formatBtnX, hexFieldY, 16, hexFieldH);
        context.fill(formatBtnX, hexFieldY, formatBtnX + 16, hexFieldY + hexFieldH, hovered ? 0xFF505050 : 0xFF383838);
        context.drawText(tr, "\u25BE", formatBtnX + 5, hexFieldY + 4, 0xFFFFFFFF, false);
    }

    private void drawFormatDropdown(DrawContext context, net.minecraft.client.font.TextRenderer tr, int mouseX, int mouseY) {
        List<String> formats = GradientFormatUtil.listAllFormats();
        int w = 100;
        int rowH = 12;
        int h = formats.size() * rowH + 4;
        int dx = formatBtnX - 84;
        int dy = hexFieldY + hexFieldH + 2;
        context.fill(dx, dy, dx + w, dy + h, 0xF00C0C0C);
        drawRectBorder(context, dx, dy, w, h, 0xFF707070);
        for (int i = 0; i < formats.size(); i++) {
            int rowY = dy + 2 + i * rowH;
            boolean hovered = inBounds(mouseX, mouseY, dx, rowY, w, rowH);
            if (hovered) context.fill(dx, rowY, dx + w, rowY + rowH, 0xFF3050A0);
            context.drawText(tr, formats.get(i), dx + 3, rowY + 2, 0xFFEEEEEE, false);
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
        if (inBounds(mouseX, mouseY, formatBtnX, hexFieldY, 16, hexFieldH)) {
            formatDropdownOpen = !formatDropdownOpen;
            return true;
        }
        hexFieldFocused = onHexField;
        hexField.setFocused(onHexField);
        if (onHexField) {
            return true;
        }

        if (inBounds(mouseX, mouseY, eyeX, eyeY, eyeW, eyeH)) {
            paletteEnabled = !paletteEnabled;
            ColorPickerDataUtil.setPaletteEnabled(paletteEnabled);
            return true;
        }

        if (paletteEnabled && handlePaletteClick(mouseX, mouseY, button)) return true;

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

    public boolean charTyped(char chr, int modifiers) {
        if (!open || !hexFieldFocused) return false;
        return hexField.charTyped(chr, modifiers);
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
        int cell = 12;
        int gy = eyeY;
        for (int i = 0; i < 6; i++) {
            int sx = eyeX + eyeW + 4 + i * (cell + 2);
            if (inBounds(mouseX, mouseY, sx, gy, cell, cell)) {
                if (button == 1) {
                    palette[i] = currentHexUpper();
                    ColorPickerDataUtil.setPaletteSlot(i, palette[i]);
                } else if (palette[i] != null) {
                    applyHex(palette[i]);
                }
                return true;
            }
        }
        return false;
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
