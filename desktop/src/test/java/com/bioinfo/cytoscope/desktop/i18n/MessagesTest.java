package com.bioinfo.cytoscope.desktop.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellFate;
import com.bioinfo.cytoscope.desktop.view.plot.PlotTool;
import com.bioinfo.cytoscope.experiment.AnalysisIssue;
import com.bioinfo.cytoscope.experiment.CompensationSource;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.scale.ScaleType;
import com.bioinfo.cytoscope.service.Example;

import javafx.beans.binding.StringBinding;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

class MessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");
    private static final Pattern KEY = Pattern.compile("^[a-z]+(\\.[A-Za-z_]+)+$");
    private static final List<String> FXML_VIEWS =
            List.of(
                    "main-view.fxml",
                    "tubes-tab.fxml",
                    "compensation-tab.fxml",
                    "gating-tab.fxml",
                    "analysis-tab.fxml",
                    "results-tab.fxml",
                    "report-tab.fxml",
                    "footer.fxml");

    private final Messages en = Messages.forLocale(Locale.ENGLISH);
    private final Messages fr = Messages.forLocale(Locale.FRENCH);

    @Test
    void bothLanguagesDefineTheSameKeys() {
        assertEquals(new TreeSet<>(en.keys()), new TreeSet<>(fr.keys()));
    }

    @Test
    void translationsUseTheSamePlaceholders() {
        for (String key : en.keys()) {
            assertEquals(placeholders(en.pattern(key)), placeholders(fr.pattern(key)), key);
        }
    }

    @Test
    void noStraightApostrophes() {
        for (Messages m : List.of(en, fr)) {
            for (String key : m.keys()) {
                assertFalse(m.pattern(key).contains("'"), m.locale() + " " + key + " must use ’");
            }
        }
    }

    @Test
    void everyKeyFormats() {
        for (Messages m : List.of(en, fr)) {
            for (String key : m.keys()) {
                Object[] args = Stream.generate(() -> (Object) 1).limit(10).toArray();
                assertFalse(m.get(key, args).contains("!"), key);
            }
        }
    }

    @Test
    void everyEnumConstantShownInTheViewIsTranslated() {
        List<String> keys = new ArrayList<>(List.of("paper.LETTER", "paper.A4"));
        Stream.of(Example.values()).forEach(v -> keys.add("example." + v.name()));
        Stream.of(Example.values()).forEach(v -> keys.add("status.opened." + v.name()));
        Stream.of(SampleRole.values()).forEach(v -> keys.add("role." + v.name()));
        Stream.of(CompensationSource.values()).forEach(v -> keys.add("source." + v.name()));
        Stream.of(ScaleType.values()).forEach(v -> keys.add("scale." + v.name()));
        Stream.of(CellFate.values()).forEach(v -> keys.add("fate." + v.name()));
        Stream.of(CellCyclePhase.values()).forEach(v -> keys.add("phase." + v.name()));
        Stream.of(AnalysisIssue.values()).forEach(v -> keys.add("issue." + v.name()));
        for (PlotTool tool : PlotTool.values()) {
            keys.add("gating.tool." + tool.name());
            keys.add("gating.hint." + tool.name());
        }
        for (String key : keys) {
            assertTrue(en.has(key) && fr.has(key), key);
        }
    }

    @Test
    void everyKeyOfTheFxmlViewsExists() throws Exception {
        Set<String> used = new TreeSet<>();
        for (String view : FXML_VIEWS) {
            collectKeys("/com/bioinfo/cytoscope/desktop/view/" + view, used);
        }
        assertTrue(used.size() > 50, "keys found: " + used.size());
        for (String key : used) {
            assertTrue(en.has(key), "missing key " + key);
        }
    }

    @Test
    void unknownKeysAndLanguages() {
        assertEquals("!nope!", en.get("nope"));
        assertEquals(null, en.pattern("nope"));
        assertFalse(en.has(null));
        assertEquals(Locale.ENGLISH, Messages.forLocale(Locale.JAPANESE).locale());
        assertEquals(Locale.ENGLISH, Messages.supportedLocale(null));
        assertEquals(Locale.FRENCH, Messages.supportedLocale(Locale.CANADA_FRENCH));
    }

    @Test
    void i18nBindingsFollowTheLanguage() {
        I18n i18n = new I18n(Locale.ENGLISH);
        StringBinding menu = i18n.text("menu.file");
        StringBinding version = i18n.text("about.version", "1.2");
        assertEquals("File", menu.get());
        i18n.setLocale(Locale.FRENCH);
        assertEquals("Fichier", menu.get());
        assertEquals("Version 1.2", version.get());
        assertEquals(Locale.FRENCH, i18n.locale());
        Messages before = i18n.messages();
        i18n.setLocale(Locale.CANADA_FRENCH);
        assertTrue(before == i18n.messages(), "same language: no change");
        i18n.setLocale(Locale.GERMAN);
        assertEquals("File", menu.get());
    }

    private static Set<String> placeholders(String pattern) {
        Set<String> found = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(pattern);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    private static void collectKeys(String resource, Set<String> keys) throws Exception {
        try (InputStream in = MessagesTest.class.getResourceAsStream(resource)) {
            assertTrue(in != null, "missing view " + resource);
            NodeList all =
                    DocumentBuilderFactory.newInstance()
                            .newDocumentBuilder()
                            .parse(in)
                            .getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                NamedNodeMap attributes = ((Element) all.item(i)).getAttributes();
                for (String name : List.of("text", "promptText", "label")) {
                    if (attributes.getNamedItem(name) != null) {
                        String value = attributes.getNamedItem(name).getNodeValue();
                        if (KEY.matcher(value).matches()) {
                            keys.add(value);
                        }
                    }
                }
            }
        }
    }
}
