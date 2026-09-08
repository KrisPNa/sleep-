package com.example.seriestracker.data.watchlinks;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Транслитерация русского названия в латиницу для сравнения со слагом URL.
 * Генерирует много распространённых вариантов написания.
 */
final class RussianTransliterator {

    private enum Scheme {
        /** Сайты/блоги: h, e, sch, yu/ya, y */
        WEB_SIMPLE,
        /** Паспортный/ГОСТ-подобный: kh, yo, shch */
        PASSPORT,
        /** «Мягкий» веб: j для й/ж, x для х, ja/ju */
        WEB_SOFT,
        /** ISO-подобный: c для ц, ju/ja, shch */
        ISO_LIKE,
        /** Старый/форумный: w для в, tch, cz */
        FORUM
    }

    private RussianTransliterator() {
    }

    @NonNull
    static String toLatin(@NonNull String input) {
        List<String> variants = toLatinVariants(input);
        return variants.isEmpty() ? "" : variants.get(0);
    }

    /**
     * Много вариантов для сравнения со слагом найденных ссылок.
     */
    @NonNull
    static List<String> toLatinVariants(@NonNull String input) {
        Set<String> variants = new LinkedHashSet<>();
        for (Scheme scheme : Scheme.values()) {
            addExpanded(variants, transliterate(input, scheme));
        }
        // Дополнительные точечные замены поверх уже собранных строк
        expandCommonSubstitutions(variants);
        return new ArrayList<>(variants);
    }

    /**
     * Короткий набор для HTTP-поиска по сайту (чтобы не делать десятки запросов).
     */
    @NonNull
    static List<String> toLatinSearchVariants(@NonNull String input) {
        Set<String> variants = new LinkedHashSet<>();
        addIfNotEmpty(variants, transliterate(input, Scheme.WEB_SIMPLE));
        addIfNotEmpty(variants, transliterate(input, Scheme.PASSPORT));
        addIfNotEmpty(variants, transliterate(input, Scheme.WEB_SOFT));
        addIfNotEmpty(variants, transliterate(input, Scheme.ISO_LIKE));

        List<String> result = new ArrayList<>();
        for (String variant : variants) {
            if (result.size() >= 4) {
                break;
            }
            result.add(variant);
        }
        return result;
    }

    private static void addExpanded(@NonNull Set<String> variants, @NonNull String value) {
        if (value.isEmpty()) {
            return;
        }
        variants.add(value);
        variants.add(value.replace(" ", "-"));
        variants.add(value.replace(" ", ""));
        variants.add(value.replace(" ", "_"));
    }

    private static void expandCommonSubstitutions(@NonNull Set<String> variants) {
        List<String> snapshot = new ArrayList<>(variants);
        for (String base : snapshot) {
            // пары взаимозамен, часто встречающиеся в слагах
            swapAdd(variants, base, "kh", "h");
            swapAdd(variants, base, "h", "kh");
            swapAdd(variants, base, "kh", "x");
            swapAdd(variants, base, "h", "x");
            swapAdd(variants, base, "x", "h");
            swapAdd(variants, base, "x", "kh");

            swapAdd(variants, base, "yo", "e");
            swapAdd(variants, base, "e", "yo");
            swapAdd(variants, base, "yo", "jo");
            swapAdd(variants, base, "jo", "yo");
            swapAdd(variants, base, "ye", "e");

            swapAdd(variants, base, "zh", "j");
            swapAdd(variants, base, "j", "zh");

            swapAdd(variants, base, "shch", "sch");
            swapAdd(variants, base, "sch", "shch");
            swapAdd(variants, base, "shch", "sh");
            swapAdd(variants, base, "sch", "sh");
            swapAdd(variants, base, "shch", "sc");
            swapAdd(variants, base, "sch", "sc");

            swapAdd(variants, base, "yu", "ju");
            swapAdd(variants, base, "ju", "yu");
            swapAdd(variants, base, "yu", "iu");
            swapAdd(variants, base, "ju", "iu");
            swapAdd(variants, base, "yu", "u");

            swapAdd(variants, base, "ya", "ja");
            swapAdd(variants, base, "ja", "ya");
            swapAdd(variants, base, "ya", "ia");
            swapAdd(variants, base, "ja", "ia");

            swapAdd(variants, base, "ts", "c");
            swapAdd(variants, base, "c", "ts");
            swapAdd(variants, base, "ts", "cz");
            swapAdd(variants, base, "cz", "ts");

            swapAdd(variants, base, "ch", "tch");
            swapAdd(variants, base, "tch", "ch");

            // й/ы чаще отличаются в конце или в сочетаниях, а не во всех буквах слова
            swapAdd(variants, base, "iy", "y");
            swapAdd(variants, base, "yi", "y");
            swapAdd(variants, base, "yy", "y");
            swapAdd(variants, base, "ii", "i");
            swapAdd(variants, base, "ij", "y");
            swapAdd(variants, base, "yj", "y");
            if (base.endsWith("y") && base.length() > 2) {
                addExpanded(variants, base.substring(0, base.length() - 1) + "i");
                addExpanded(variants, base.substring(0, base.length() - 1) + "j");
            }
            if (base.endsWith("i") && base.length() > 2) {
                addExpanded(variants, base.substring(0, base.length() - 1) + "y");
                addExpanded(variants, base.substring(0, base.length() - 1) + "j");
            }

            swapAdd(variants, base, "v", "w");
            swapAdd(variants, base, "w", "v");

            swapAdd(variants, base, "f", "ph");
            swapAdd(variants, base, "ph", "f");
        }
    }

    private static void swapAdd(@NonNull Set<String> variants,
                                @NonNull String base,
                                @NonNull String from,
                                @NonNull String to) {
        if (!base.contains(from)) {
            return;
        }
        String replaced = base.replace(from, to);
        addExpanded(variants, replaced);
        // Однократная замена только первого вхождения — полезно для длинных названий
        int index = base.indexOf(from);
        if (index >= 0) {
            String once = base.substring(0, index) + to + base.substring(index + from.length());
            addExpanded(variants, once);
        }
    }

    private static void addIfNotEmpty(@NonNull Set<String> set, @NonNull String value) {
        if (!value.isEmpty()) {
            set.add(value);
        }
    }

    @NonNull
    private static String transliterate(@NonNull String input, @NonNull Scheme scheme) {
        StringBuilder builder = new StringBuilder();
        String lower = input.toLowerCase(Locale.getDefault());
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            boolean wordStart = i == 0 || !Character.isLetter(lower.charAt(i - 1));
            String mapped = mapChar(ch, scheme, wordStart);
            if (mapped != null) {
                builder.append(mapped);
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                builder.append(ch);
            } else if (Character.isWhitespace(ch) || ch == '-' || ch == '_') {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ' ') {
                    builder.append(' ');
                }
            }
        }
        return builder.toString().trim().replaceAll("\\s+", " ");
    }

    @NonNull
    private static String mapChar(char ch, @NonNull Scheme scheme, boolean wordStart) {
        switch (ch) {
            case 'а':
                return "a";
            case 'б':
                return "b";
            case 'в':
                return scheme == Scheme.FORUM ? "w" : "v";
            case 'г':
                return "g";
            case 'д':
                return "d";
            case 'е':
                if (scheme == Scheme.WEB_SOFT && wordStart) {
                    return "ye";
                }
                return "e";
            case 'ё':
                switch (scheme) {
                    case PASSPORT:
                    case ISO_LIKE:
                        return "yo";
                    case WEB_SOFT:
                        return "jo";
                    case FORUM:
                        return "yo";
                    case WEB_SIMPLE:
                    default:
                        return "e";
                }
            case 'ж':
                return (scheme == Scheme.WEB_SOFT || scheme == Scheme.FORUM) ? "j" : "zh";
            case 'з':
                return "z";
            case 'и':
                return "i";
            case 'й':
                switch (scheme) {
                    case WEB_SOFT:
                        return "j";
                    case ISO_LIKE:
                        return "j";
                    case FORUM:
                        return "y";
                    default:
                        return "y";
                }
            case 'к':
                return "k";
            case 'л':
                return "l";
            case 'м':
                return "m";
            case 'н':
                return "n";
            case 'о':
                return "o";
            case 'п':
                return "p";
            case 'р':
                return "r";
            case 'с':
                return "s";
            case 'т':
                return "t";
            case 'у':
                return "u";
            case 'ф':
                return scheme == Scheme.FORUM ? "ph" : "f";
            case 'х':
                switch (scheme) {
                    case PASSPORT:
                    case ISO_LIKE:
                        return "kh";
                    case WEB_SOFT:
                        return "x";
                    case FORUM:
                        return "h";
                    case WEB_SIMPLE:
                    default:
                        return "h";
                }
            case 'ц':
                switch (scheme) {
                    case ISO_LIKE:
                    case WEB_SOFT:
                        return "c";
                    case FORUM:
                        return "cz";
                    default:
                        return "ts";
                }
            case 'ч':
                return scheme == Scheme.FORUM ? "tch" : "ch";
            case 'ш':
                return "sh";
            case 'щ':
                switch (scheme) {
                    case PASSPORT:
                    case ISO_LIKE:
                        return "shch";
                    case WEB_SOFT:
                        return "sch";
                    case FORUM:
                        return "sh";
                    case WEB_SIMPLE:
                    default:
                        return "sch";
                }
            case 'ъ':
            case 'ь':
                return "";
            case 'ы':
                return scheme == Scheme.ISO_LIKE ? "y" : (scheme == Scheme.WEB_SOFT ? "i" : "y");
            case 'э':
                return scheme == Scheme.FORUM ? "eh" : "e";
            case 'ю':
                switch (scheme) {
                    case WEB_SOFT:
                    case ISO_LIKE:
                        return "ju";
                    case FORUM:
                        return "iu";
                    default:
                        return "yu";
                }
            case 'я':
                switch (scheme) {
                    case WEB_SOFT:
                    case ISO_LIKE:
                        return "ja";
                    case FORUM:
                        return "ia";
                    default:
                        return "ya";
                }
            default:
                return "";
        }
    }
}
