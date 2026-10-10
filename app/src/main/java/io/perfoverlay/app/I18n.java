package io.perfoverlay.app;

import java.util.*;

/** Immutable translation snapshot shared by UI and sampling threads. Numeric data stays locale-neutral. */
public final class I18n {
    private static volatile Catalog catalog = new Catalog(Collections.emptyMap());
    private static final class Catalog {
        final Map<String, String> translations;
        final List<String> keys;
        Catalog(Map<String, String> source) {
            translations = Collections.unmodifiableMap(new HashMap<>(source));
            keys = new ArrayList<>(source.keySet());
            keys.sort(Comparator.comparingInt(String::length).reversed());
        }
    }
    public static void install(Map<String, String> translations) { catalog = new Catalog(translations); }
    public static String t(String source) { return source == null ? "" : catalog.translations.getOrDefault(source, source); }
    /** Translate legacy/canonical diagnostic text once, without changing stored files or numeric units. */
    public static String raw(String source) {
        if (source == null) return "";
        Catalog current = catalog;
        String exact = current.translations.get(source);
        if (exact != null) return exact;
        boolean chinese = false;
        for (int i = 0; i < source.length(); i++) if (source.charAt(i) >= '\u4e00' && source.charAt(i) <= '\u9fff') { chinese = true; break; }
        if (!chinese || current.keys.isEmpty()) return source;
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < source.length();) {
            String match = null;
            for (String key : current.keys) if (source.startsWith(key, offset)) { match = key; break; }
            if (match == null) out.append(source.charAt(offset++));
            else { out.append(current.translations.get(match)); offset += match.length(); }
        }
        return out.toString();
    }
    public static String supported(String language) {
        return Arrays.asList("zh", "ja", "en", "fr", "de").contains(language) ? language : "en";
    }
    private I18n() { }
}
