package com.cie.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ВРЕМЕННЫЙ диагностический миксин: логирует КАЖДЫЙ вызов
 * MinecraftClient#setScreen вместе со стектрейсом, чтобы найти, кто именно
 * закрывает экраны CIE сразу после открытия. Удалить после диагностики.
 */
@Mixin(MinecraftClient.class)
public abstract class DebugSetScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void cie$debugSetScreen(Screen screen, CallbackInfo ci) {
        String name = (screen == null) ? "null" : screen.getClass().getName();
        if (name.contains("cie") || name.equals("null")) {
            System.out.println("CIE-DEBUG-SETSCREEN: setScreen(" + name + ") called");
            new Throwable("CIE-DEBUG-SETSCREEN stacktrace").printStackTrace();
        }
    }
}
