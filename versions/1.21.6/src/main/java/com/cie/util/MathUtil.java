package com.cie.util;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилита для /cie math — вычисление арифметических выражений (+ - * / :,
 * скобки, унарный минус), хранение истории вычислений за текущую сессию
 * клиента и генерация случайного числа в диапазоне вида "15.5-23.7".
 *
 * ':' поддерживается как алиас деления наравне с '/' — так исторически
 * писали деление на некоторых раскладках/клавиатурах.
 */
public final class MathUtil {

    private MathUtil() {
    }

    private static final int HISTORY_LIMIT = 200;
    private static final Deque<String> HISTORY = new ArrayDeque<>();

    // ============================================================
    //  expression
    // ============================================================

    /** Вычисляет арифметическое выражение и кладёт запись в историю. Бросает IllegalArgumentException при синтаксической ошибке. */
    public static double evaluate(String expression) {
        double result = new ExpressionParser(expression).parse();
        pushHistory(expression.trim() + " = " + formatNumber(result));
        return result;
    }

    private static void pushHistory(String entry) {
        HISTORY.addFirst(entry);
        while (HISTORY.size() > HISTORY_LIMIT) {
            HISTORY.removeLast();
        }
    }

    public static List<String> getHistory(int count) {
        int n = Math.max(0, Math.min(count, HISTORY.size()));
        return List.copyOf(new java.util.ArrayList<>(HISTORY).subList(0, n));
    }

    public static void clearHistory() {
        HISTORY.clear();
    }

    /** Простой рекурсивный спуск: expr := term (('+'|'-') term)*; term := factor (('*'|'/'|':') factor)*; factor := ['-'] (number | '(' expr ')'). */
    private static final class ExpressionParser {
        private final String src;
        private int pos;

        ExpressionParser(String src) {
            this.src = src.replace(" ", "").replace(",", ".");
        }

        double parse() {
            if (src.isEmpty()) {
                throw new IllegalArgumentException("empty expression");
            }
            double result = parseExpr();
            if (pos != src.length()) {
                throw new IllegalArgumentException("unexpected character at " + pos + ": '" + src.charAt(pos) + "'");
            }
            return result;
        }

        private double parseExpr() {
            double value = parseTerm();
            while (pos < src.length() && (peek() == '+' || peek() == '-')) {
                char op = next();
                double rhs = parseTerm();
                value = op == '+' ? value + rhs : value - rhs;
            }
            return value;
        }

        private double parseTerm() {
            double value = parseFactor();
            while (pos < src.length() && (peek() == '*' || peek() == '/' || peek() == ':')) {
                char op = next();
                double rhs = parseFactor();
                if ((op == '/' || op == ':') && rhs == 0) {
                    throw new IllegalArgumentException("division by zero");
                }
                value = op == '*' ? value * rhs : value / rhs;
            }
            return value;
        }

        private double parseFactor() {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("unexpected end of expression");
            }
            if (peek() == '-') {
                next();
                return -parseFactor();
            }
            if (peek() == '+') {
                next();
                return parseFactor();
            }
            if (peek() == '(') {
                next();
                double value = parseExpr();
                if (pos >= src.length() || peek() != ')') {
                    throw new IllegalArgumentException("missing closing bracket");
                }
                next();
                return value;
            }
            return parseNumber();
        }

        private double parseNumber() {
            int start = pos;
            while (pos < src.length() && (Character.isDigit(peek()) || peek() == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("expected number at " + pos);
            }
            return Double.parseDouble(src.substring(start, pos));
        }

        private char peek() {
            return src.charAt(pos);
        }

        private char next() {
            return src.charAt(pos++);
        }
    }

    // ============================================================
    //  random
    // ============================================================

    private static final Pattern RANGE_PATTERN = Pattern.compile("^(-?\\d+(?:\\.\\d+)?)-(-?\\d+(?:\\.\\d+)?)$");

    /**
     * Разбирает диапазон вида "15.5-23.7" или "10-20" и возвращает случайное
     * число по правилам:
     *  - если хотя бы одна граница не целая — результат дробный (double),
     *    с точностью до максимального числа знаков после запятой среди границ;
     *  - если обе границы целые и обе чётные — результат тоже чётный
     *    (случайный выбор среди чётных чисел диапазона);
     *  - если обе границы целые и хотя бы одна нечётная — обычный
     *    равномерный случайный int без ограничения на чётность.
     */
    public static String randomInRange(String range) {
        Matcher matcher = RANGE_PATTERN.matcher(range.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("range must look like 15.5-23.7");
        }
        String rawMin = matcher.group(1);
        String rawMax = matcher.group(2);
        double min = Double.parseDouble(rawMin);
        double max = Double.parseDouble(rawMax);
        if (min > max) {
            double tmp = min;
            min = max;
            max = tmp;
        }

        boolean fractional = rawMin.contains(".") || rawMax.contains(".");
        if (fractional) {
            int decimals = Math.max(decimalsOf(rawMin), decimalsOf(rawMax));
            double value = ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
            double scale = Math.pow(10, decimals);
            value = Math.round(value * scale) / scale;
            return formatNumber(value);
        }

        int iMin = (int) min;
        int iMax = (int) max;
        boolean bothEven = iMin % 2 == 0 && iMax % 2 == 0;
        if (bothEven) {
            int evenCount = (iMax - iMin) / 2 + 1;
            int picked = iMin + 2 * ThreadLocalRandom.current().nextInt(evenCount);
            return String.valueOf(picked);
        }
        return String.valueOf(ThreadLocalRandom.current().nextInt(iMin, iMax + 1));
    }

    private static int decimalsOf(String raw) {
        int dot = raw.indexOf('.');
        return dot < 0 ? 0 : raw.length() - dot - 1;
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        String s = String.valueOf(value);
        return s;
    }
}