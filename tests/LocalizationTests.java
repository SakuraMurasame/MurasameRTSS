import io.perfoverlay.app.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public final class LocalizationTests {
    static int assertions;
    static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    static Map<String,String> read(Path path) throws Exception {
        NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile()).getElementsByTagName("string");
        Map<String,String> values = new HashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i); String text = node.getTextContent();
            text = text.substring(1, text.length() - 1).replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\");
            values.put(node.getAttribute("name"), text);
        }
        return values;
    }
    public static void main(String[] args) throws Exception {
        Path res = Paths.get(args[0], "app/src/main/res");
        Map<String,String> original = read(res.resolve("values-zh/strings.xml"));
        String[] languages = {"en", "ja", "fr", "de", "zh"};
        String[] monitor = {"Monitor", "監視", "Suivi", "Monitor", "监控"};
        Metric cachedMetric = Metric.BAT_TEMP;
        for (int i = 0; i < languages.length; i++) {
            String language = languages[i];
            Map<String,String> translated = read(res.resolve((language.equals("en") ? "values" : "values-" + language) + "/strings.xml"));
            check(original.keySet().equals(translated.keySet()), "Complete catalog: " + language);
            Map<String,String> map = new HashMap<>();
            for (String key : original.keySet()) {
                String text = translated.get(key); check(!text.isEmpty(), key);
                check(!original.get(key).startsWith("\n") || text.startsWith("\n"), "Preserve diagnostic line breaks " + key);
                map.put(original.get(key), text);
            }
            I18n.install(map);
            check(I18n.t("监控").equals(monitor[i]), "Menu translation: " + language);
            check(I18n.t(cachedMetric.title).equals(map.get(cachedMetric.title)), "Enums update without restart");
            check(HistoryMath.values(I18n.raw("输出 ≈5.2 W"), false)[0] == 5.2, "Battery value unchanged");
            check(HistoryMath.values(I18n.raw("42% 充电"), false)[0] == 42, "Battery level unchanged");
            check(I18n.raw("3.2+2.8 GHz").equals("3.2+2.8 GHz"), "CPU values untouched");
            check(I18n.raw("普通模式 · Shizuku 已连接").equals(map.get("普通模式 · ") + map.get("Shizuku 已连接")), "Translate canonical combined status");
            check(I18n.t("unknown.vendor.node").equals("unknown.vendor.node"), "Unknown identifiers untouched");
            String stats = String.format(Locale.ROOT, I18n.t("最低 %.2f   最高 %.2f   均值 %.2f%s"), 1.25, 3.75, 2.5, "");
            check(stats.contains("1.25") && stats.contains("3.75") && stats.contains("2.50"), "Formatting placeholders " + language);
            check(HistoryMath.parseTime("00:01:30") == 90000, "Locale-neutral time parsing");
        }
        check(I18n.supported("it").equals("en"), "Unsupported system language falls back to English");
        check(I18n.supported("zh").equals("zh"), "Chinese retained");
        I18n.install(Collections.emptyMap());
        System.out.println("PASS: " + assertions + " localization assertions (all languages, live switching, canonical readings, formats)");
    }
}
