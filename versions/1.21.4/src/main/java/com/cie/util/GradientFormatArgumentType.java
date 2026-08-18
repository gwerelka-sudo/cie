package com.cie.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Аргумент "имя формата градиента" — читает подряд идущие символы до
 * ближайшего пробела, БЕЗ ограничения на допустимый набор символов
 * (в отличие от StringArgumentType.word()/string(), у которых даже
 * без кавычек разрешены только 0-9 A-Z a-z _ - . + — символ '&' в
 * "legacy&" туда не входит и требует кавычек).
 *
 * Кавычки по-прежнему поддерживаются опционально: если ввод начинается
 * с '"', делегируем обычному чтению quoted-строки Brigadier (на случай,
 * если имя формата всё же содержит пробел — маловероятно, но пусть
 * работает единообразно).
 */
public final class GradientFormatArgumentType implements ArgumentType<String> {

    private static final GradientFormatArgumentType INSTANCE = new GradientFormatArgumentType();

    public static GradientFormatArgumentType formatName() {
        return INSTANCE;
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '"') {
            return reader.readQuotedString();
        }
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }
}