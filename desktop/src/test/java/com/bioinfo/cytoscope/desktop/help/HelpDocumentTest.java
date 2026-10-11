package com.bioinfo.cytoscope.desktop.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.desktop.controller.MainController;
import com.bioinfo.cytoscope.desktop.i18n.Messages;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

class HelpDocumentTest {

    /** One screenshot per tab, plus the dose-response curve and the cell cycle. */
    private static final int SCREENSHOTS = 8;

    private static final String MARKDOWN =
            """
**Title before the first section is ignored**

<!-- section: first -->
# First section

Intro line one
continued on line two with **bold**, *italic*, `code` and a [link](https://example.org).

## Sub heading
### Sub sub heading

- bullet one
  continued
- bullet two
1. step one
2. step two

> A note
> on two lines

![Alt text](images/en/00-overview.png)

| # | Name |
|---|---|
| 1 | `Peptide` |
| 2 | Spectres |

```json
{ "id": 1 }
```

---
<!-- free comment -->
# Second Section: Éléments
Tolérance et précision.
""";

    private final HelpDocument document = HelpDocument.parse(MARKDOWN);

    @Test
    void sectionsAndIdentifiers() {
        assertEquals(2, document.sections().size());
        HelpSection first = document.sections().getFirst();
        assertEquals("first", first.id());
        assertEquals("First section", first.title());
        assertEquals("First section", first.toString());
        assertEquals(
                "second-section-elements",
                document.sections().get(1).id(),
                "slug when no explicit id");
        assertTrue(document.section("first").isPresent());
        assertTrue(document.section("missing").isEmpty());
    }

    @Test
    void blocksOfTheFirstSection() {
        List<Block> blocks = document.sections().getFirst().blocks();
        assertEquals(9, blocks.size(), blocks.toString());

        Block.Paragraph paragraph = assertInstanceOf(Block.Paragraph.class, blocks.get(0));
        assertEquals(
                "Intro line one continued on line two with bold, italic, code and a link.",
                paragraph.plainText());
        assertEquals(
                List.of(
                        Span.Style.PLAIN,
                        Span.Style.BOLD,
                        Span.Style.PLAIN,
                        Span.Style.ITALIC,
                        Span.Style.PLAIN,
                        Span.Style.CODE,
                        Span.Style.PLAIN,
                        Span.Style.LINK,
                        Span.Style.PLAIN),
                paragraph.spans().stream().map(Span::style).toList());
        assertEquals("https://example.org", paragraph.spans().get(7).url());

        assertEquals(2, assertInstanceOf(Block.Heading.class, blocks.get(1)).level());
        assertEquals(3, assertInstanceOf(Block.Heading.class, blocks.get(2)).level());

        Block.ListBlock bullets = assertInstanceOf(Block.ListBlock.class, blocks.get(3));
        assertFalse(bullets.numbered());
        assertEquals("bullet one continued\nbullet two", bullets.plainText());
        Block.ListBlock steps = assertInstanceOf(Block.ListBlock.class, blocks.get(4));
        assertTrue(steps.numbered());
        assertEquals(2, steps.items().size());

        assertEquals(
                "A note on two lines",
                assertInstanceOf(Block.Note.class, blocks.get(5)).plainText());

        Block.Image image = assertInstanceOf(Block.Image.class, blocks.get(6));
        assertEquals("images/en/00-overview.png", image.path());
        assertEquals("Alt text", image.plainText());

        Block.Table table = assertInstanceOf(Block.Table.class, blocks.get(7));
        assertEquals(2, table.header().size());
        assertEquals(2, table.rows().size());
        assertEquals("# Name\n1 Peptide\n2 Spectres", table.plainText());

        assertEquals("{ \"id\": 1 }", assertInstanceOf(Block.Code.class, blocks.get(8)).text());
    }

    @Test
    void searchIgnoresCaseAndAccents() {
        assertEquals(2, document.search("").size(), "blank query lists every section");
        assertEquals(0, document.search(null).getFirst().occurrences());

        List<HelpDocument.SearchHit> hits = document.search("TOLERANCE");
        assertEquals(1, hits.size());
        assertEquals("second-section-elements", hits.getFirst().section().id());
        assertEquals(1, hits.getFirst().occurrences());

        assertEquals(1, document.search("elements precision").size(), "all words must match");
        assertTrue(document.search("elements bullet").isEmpty());
        assertEquals(2, document.search("bullet").getFirst().occurrences());
        assertEquals(
                List.of("tolerance", "da"), HelpDocument.queryWords("  Tolérance   Da tolerance "));
    }

    @Test
    void foldKeepsTheLength() {
        String text = "Élément Œuf ÇA ppm";
        assertEquals(text.length(), HelpDocument.fold(text).length());
        assertEquals("element œuf ca ppm", HelpDocument.fold(text));
    }

    @Test
    void bundledGuidesAreConsistent() {
        HelpDocument fr = HelpDocument.load(Locale.FRENCH);
        HelpDocument en = HelpDocument.load(Locale.ENGLISH);
        List<String> ids = fr.sections().stream().map(HelpSection::id).toList();
        assertEquals(
                ids,
                en.sections().stream().map(HelpSection::id).toList(),
                "same sections in the same order");
        assertTrue(ids.containsAll(MainController.TAB_HELP_SECTIONS), "one section per tab");
        assertEquals("start", ids.getFirst());

        List<String> frImages = images(fr);
        List<String> enImages = images(en);
        assertEquals(
                frImages.stream().map(p -> p.replace("/fr/", "/")).toList(),
                enImages.stream().map(p -> p.replace("/en/", "/")).toList(),
                "same screenshots in both guides");
        for (String path : concat(frImages, enImages)) {
            assertNotNull(HelpDocument.class.getResource(HelpDocument.RESOURCE_ROOT + path), path);
        }
        assertEquals(SCREENSHOTS, frImages.size());

        for (Locale locale : Messages.SUPPORTED_LOCALES) {
            assertFalse(HelpDocument.load(locale).search("Dunnett").isEmpty());
        }
    }

    @Test
    void everyBlockEndsAParagraph() {
        List<Block> blocks =
                HelpDocument.parse(
                                String.join(
                                        "\n",
                                        "# A",
                                        "p1",
                                        "## h",
                                        "p2",
                                        "| x |",
                                        "p3",
                                        "> n",
                                        "p4",
                                        "```",
                                        "c",
                                        "```",
                                        "p5",
                                        "<!-- c -->",
                                        "p6",
                                        "![i](x.png)",
                                        "p7",
                                        "- b",
                                        "p8",
                                        "1. n",
                                        "p9"))
                        .sections()
                        .getFirst()
                        .blocks();
        assertEquals(
                List.of(
                        "p1", "h", "p2", "x", "p3", "n", "p4", "c", "p5", "p6", "i", "p7", "b",
                        "p8", "n", "p9"),
                blocks.stream().map(Block::plainText).toList());
    }

    @Test
    void listItemsContinueOnIndentedLines() {
        List<Block> blocks =
                HelpDocument.parse(
                                String.join(
                                        "\n",
                                        "# B",
                                        "- item",
                                        "  continued",
                                        "",
                                        "- two",
                                        "  - nested",
                                        "  1. number",
                                        "   ",
                                        "> note at the end"))
                        .sections()
                        .getFirst()
                        .blocks();
        assertEquals(4, blocks.size(), blocks.toString());
        assertEquals("item continued", blocks.get(0).plainText());
        assertEquals("two\nnested", blocks.get(1).plainText());
        assertTrue(((Block.ListBlock) blocks.get(2)).numbered());
        assertInstanceOf(Block.Note.class, blocks.get(3));
    }

    @Test
    void blocksAtTheEndOfTheFile() {
        Block table =
                HelpDocument.parse("# C\n| a | b\n| c | d |").sections().getFirst().blocks().get(0);
        assertEquals("a b\nc d", table.plainText(), "the last pipe is optional");
        assertEquals(
                "unterminated",
                HelpDocument.parse("# D\n```\nunterminated")
                        .sections()
                        .getFirst()
                        .blocks()
                        .getFirst()
                        .plainText());
        assertEquals(
                "last item",
                HelpDocument.parse("# E\n- last item")
                        .sections()
                        .getFirst()
                        .blocks()
                        .getFirst()
                        .plainText());
        assertTrue(HelpDocument.queryWords("   ").isEmpty());
    }

    @Test
    void missingGuideIsReported() {
        assertThrows(IllegalStateException.class, () -> HelpDocument.load(Locale.JAPANESE));
    }

    private static List<String> images(HelpDocument document) {
        return document.sections().stream()
                .flatMap(s -> s.blocks().stream())
                .filter(Block.Image.class::isInstance)
                .map(b -> ((Block.Image) b).path())
                .toList();
    }

    private static List<String> concat(List<String> a, List<String> b) {
        return Stream.concat(a.stream(), b.stream()).toList();
    }
}
