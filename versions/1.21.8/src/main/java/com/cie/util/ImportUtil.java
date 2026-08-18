package com.cie.util;

import com.mojang.serialization.DataResult;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;

/**
 * /cie import <snbt> — строит ItemStack напрямую из полного SNBT предмета
 * (тот же формат, в котором ItemStack сериализуется целиком с 1.20.5+ —
 * {id:"...", count:N, components:{...}}), используя штатный
 * ItemStack.CODEC. Это по сути обратная операция к StringNbtWriter'у,
 * которым уже пользуется /cie export (см. ExportUtil), только на уровне
 * целого стека, а не отдельных компонентов.
 *
 * РИСК ДЛЯ КОМПИЛЯЦИИ: как и в UseRemainderUtil, DataResult.getOrThrow()
 * здесь вызывается без аргумента — если в вашей ревизии Mojang
 * serialization API сигнатура другая (например, требует
 * Function<String,X>), поправь по аналогии с уже работающим вызовом в
 * UseRemainderUtil.buildRemainderStack.
 */
public final class ImportUtil {

    private ImportUtil() {
    }

    public static ItemStack fromSnbt(String snbt, RegistryWrapper.WrapperLookup registries) throws Exception {
        NbtElement nbt = StringNbtReader.fromOps(NbtOps.INSTANCE).read(snbt);
        RegistryOps<NbtElement> ops = registries.getOps(NbtOps.INSTANCE);
        DataResult<ItemStack> result = ItemStack.CODEC.parse(ops, nbt);
        return result.getOrThrow();
    }
}
