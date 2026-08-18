package com.cie.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * /cie livePreview get/set — состояние маленького HUD-виджета, который
 * постоянно показывает предмет из основной руки (как отдельный "слот" на
 * экране) вместе с его тултипом (имя/лор/зачарования и т.д.), без
 * необходимости наводить мышь.
 *
 * Сам рендер виджета и перехват мыши для перетаскивания живут в
 * {@link LivePreviewRenderer} — здесь только хранимое состояние: вкл/выкл,
 * позиция на экране, размер слота и текущее перетаскивание. Позиция, флаг
 * "включено" и размер переживают перезапуск игры (cie/live_preview.json),
 * перетаскивание — нет (это чисто рантайм-состояние текущей сессии).
 */
public final class LivePreviewUtil {

    private LivePreviewUtil() {
    }

    /**
     * Мин/макс сторона виджета в пикселях GUI-координат.
     * MIN = ванильный размер слота (18px) — меньше предмет 16x16 обрезается.
     */
    public static final int MIN_SLOT_SIZE = 18;
    public static final int MAX_SLOT_SIZE = 64;
    private static final int DEFAULT_SLOT_SIZE = 24;

    private static final int DEFAULT_X = 12;
    private static final int DEFAULT_Y = 12;

    private static boolean enabled = false;
    private static int x = DEFAULT_X;
    private static int y = DEFAULT_Y;
    private static int slotSize = DEFAULT_SLOT_SIZE;

    private static boolean dragging = false;
    private static int dragOffsetX;
    private static int dragOffsetY;

    // Границы последнего отрисованного тултипа (обновляются рендерером каждый кадр) —
    // нужны, чтобы хватать виджет за тултип мышью, а не только за сам маленький слот.
    private static boolean tooltipVisible = false;
    private static int tooltipX;
    private static int tooltipY;
    private static int tooltipWidth;
    private static int tooltipHeight;

    private static boolean loaded = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("cie").resolve("live_preview.json");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path path = file();
        if (!Files.exists(path)) {
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("enabled")) {
                enabled = obj.get("enabled").getAsBoolean();
            }
            if (obj.has("x")) {
                x = obj.get("x").getAsInt();
            }
            if (obj.has("y")) {
                y = obj.get("y").getAsInt();
            }
            if (obj.has("slotSize")) {
                slotSize = clampSize(obj.get("slotSize").getAsInt());
            }
        } catch (Exception ignored) {
            // Битый/несовместимый файл — просто остаёмся на значениях по умолчанию,
            // это чисто косметическая настройка, не стоит мешать игроку командой из-за неё.
        }
    }

    private static void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("enabled", enabled);
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("slotSize", slotSize);
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(tmp, GSON.toJson(obj), StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            // Не роняем игру из-за неудачной записи косметической настройки на диск.
        }
    }

    private static int clampSize(int value) {
        return Math.min(Math.max(value, MIN_SLOT_SIZE), MAX_SLOT_SIZE);
    }

    public static boolean isEnabled() {
        ensureLoaded();
        return enabled;
    }

    public static void setEnabled(boolean value) {
        ensureLoaded();
        if (enabled == value) {
            return;
        }
        enabled = value;
        if (!value) {
            // Выключили из чата посреди перетаскивания — не оставляем "подвисший" drag-стейт.
            dragging = false;
        }
        save();
    }

    public static int getX() {
        ensureLoaded();
        return x;
    }

    public static int getY() {
        ensureLoaded();
        return y;
    }

    public static int getSlotSize() {
        ensureLoaded();
        return slotSize;
    }

    /** /cie livePreview size set <n> — значение зажимается между MIN_SLOT_SIZE и MAX_SLOT_SIZE. */
    public static void setSlotSize(int value) {
        ensureLoaded();
        int clamped = clampSize(value);
        if (slotSize == clamped) {
            return;
        }
        slotSize = clamped;
        save();
    }

    /** Не даёт виджету "уехать" за пределы окна, например после ресайза/смены разрешения. */
    public static void clampToScreen(int screenWidth, int screenHeight) {
        ensureLoaded();
        int maxX = Math.max(0, screenWidth - slotSize);
        int maxY = Math.max(0, screenHeight - slotSize);
        int newX = Math.min(Math.max(x, 0), maxX);
        int newY = Math.min(Math.max(y, 0), maxY);
        if (newX != x || newY != y) {
            x = newX;
            y = newY;
        }
    }

    public static boolean isInside(int mouseX, int mouseY) {
        ensureLoaded();
        boolean insideSlot = mouseX >= x && mouseX <= x + slotSize && mouseY >= y && mouseY <= y + slotSize;
        if (insideSlot) {
            return true;
        }
        if (!tooltipVisible) {
            return false;
        }
        return mouseX >= tooltipX && mouseX <= tooltipX + tooltipWidth
                && mouseY >= tooltipY && mouseY <= tooltipY + tooltipHeight;
    }

    /** Вызывается рендерером сразу после отрисовки тултипа — фиксирует его границы для перетаскивания мышью. */
    public static void setTooltipBounds(int tx, int ty, int tw, int th) {
        tooltipVisible = true;
        tooltipX = tx;
        tooltipY = ty;
        tooltipWidth = tw;
        tooltipHeight = th;
    }

    /** Вызывается рендерером, когда тултип в этом кадре не рисуется (нет предмета, экран закрыт и т.д.). */
    public static void clearTooltipBounds() {
        tooltipVisible = false;
    }

    public static boolean isDragging() {
        return dragging;
    }

    public static void beginDrag(int mouseX, int mouseY) {
        ensureLoaded();
        dragging = true;
        dragOffsetX = mouseX - x;
        dragOffsetY = mouseY - y;
    }

    /** Вызывается каждый кадр, пока dragging == true (см. ScreenEvents.afterRender в LivePreviewRenderer). */
    public static void updateDrag(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!dragging) {
            return;
        }
        x = mouseX - dragOffsetX;
        y = mouseY - dragOffsetY;
        clampToScreen(screenWidth, screenHeight);
    }

    public static void endDrag() {
        if (dragging) {
            dragging = false;
            save();
        }
    }
}