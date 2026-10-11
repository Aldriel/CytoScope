package com.bioinfo.cytoscope.desktop.help;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User guide parsed from a Markdown file into sections, one per level-1 heading.
 *
 * <p>Supported syntax: {@code #}/{@code ##}/{@code ###} headings, paragraphs, {@code -} and {@code
 * 1.} lists, {@code >} notes, pipe tables, fenced code blocks, images alone on their line, and
 * inline {@code **bold**}, {@code *italic*}, {@code `code`} and {@code [links](url)}. A comment
 * {@code <!-- section: id -->} before a level-1 heading gives the section a stable identifier, used
 * to open the guide on the section of a tab.
 */
public final class HelpDocument {

    /** Folder of the guides and their images on the class path. */
    public static final String RESOURCE_ROOT = "/com/bioinfo/cytoscope/desktop/help/";

    private static final Pattern SECTION_ID =
            Pattern.compile("^<!--\\s*section:\\s*([\\w-]+)\\s*-->$");
    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+)$");
    private static final Pattern IMAGE = Pattern.compile("^!\\[([^]]*)]\\(([^)]+)\\)$");
    private static final Pattern BULLET = Pattern.compile("^[-*]\\s+(.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^\\d+\\.\\s+(.+)$");
    private static final Pattern INLINE =
            Pattern.compile(
                    "\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^]]+)]\\(([^)]+)\\)|\\*([^*\\s][^*]*?)\\*");

    private final List<HelpSection> sections;

    private HelpDocument(List<HelpSection> sections) {
        this.sections = List.copyOf(sections);
    }

    /** Loads the guide of a language from the class path ({@code guide_<lang>.md}). */
    public static HelpDocument load(Locale locale) {
        String name = RESOURCE_ROOT + "guide_" + locale.getLanguage() + ".md";
        try (InputStream in = HelpDocument.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Missing user guide " + name);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parses a guide written in the supported Markdown subset. */
    public static HelpDocument parse(String markdown) {
        return new Parser(markdown).parse();
    }

    public List<HelpSection> sections() {
        return sections;
    }

    /** Returns the section with this identifier. */
    public Optional<HelpSection> section(String id) {
        return sections.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /**
     * Searches the guide. Every word of the query must appear in a section for it to match; the
     * comparison ignores case and accents.
     *
     * @return matching sections in document order with their number of occurrences; every section
     *     (with a count of zero) when the query is blank
     */
    public List<SearchHit> search(String query) {
        List<String> words = queryWords(query);
        List<SearchHit> hits = new ArrayList<>();
        for (HelpSection section : sections) {
            String text = fold(section.plainText());
            int total = 0;
            boolean all = true;
            for (String word : words) {
                int count = countOccurrences(text, word);
                if (count == 0) {
                    all = false;
                    break;
                }
                total += count;
            }
            if (all) {
                hits.add(new SearchHit(section, total));
            }
        }
        return hits;
    }

    /** A section matching a search, with the number of occurrences of the query words. */
    public record SearchHit(HelpSection section, int occurrences) {}

    /** Returns the distinct words of a query, folded with {@link #fold(String)}. */
    public static List<String> queryWords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fold(query.strip()).split("\\s+"))
                .filter(w -> !w.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * Lower-cases a text and removes accents character by character, so the result has the same
     * length as the input and positions found in it are valid in the original text.
     */
    public static String fold(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 128) {
                sb.append(Character.toLowerCase(c));
            } else {
                String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
                sb.append(Character.toLowerCase(decomposed.charAt(0)));
            }
        }
        return sb.toString();
    }

    private static int countOccurrences(String text, String word) {
        int count = 0;
        for (int i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + word.length())) {
            count++;
        }
        return count;
    }

    /** Splits a line of text into styled spans. */
    static List<Span> inline(String text) {
        List<Span> spans = new ArrayList<>();
        Matcher m = INLINE.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                spans.add(new Span(text.substring(last, m.start()), Span.Style.PLAIN, null));
            }
            if (m.group(1) != null) {
                spans.add(new Span(m.group(1), Span.Style.BOLD, null));
            } else if (m.group(2) != null) {
                spans.add(new Span(m.group(2), Span.Style.CODE, null));
            } else if (m.group(3) != null) {
                spans.add(new Span(m.group(3), Span.Style.LINK, m.group(4)));
            } else {
                spans.add(new Span(m.group(5), Span.Style.ITALIC, null));
            }
            last = m.end();
        }
        if (last < text.length()) {
            spans.add(new Span(text.substring(last), Span.Style.PLAIN, null));
        }
        return spans;
    }

    /** Line-oriented parser of the Markdown subset. */
    private static final class Parser {

        private final String[] lines;
        private final List<HelpSection> sections = new ArrayList<>();
        private int index;
        private String pendingId;
        private String title;
        private String id;
        private List<Block> blocks;

        Parser(String markdown) {
            this.lines = markdown.replace("\r\n", "\n").split("\n", -1);
        }

        HelpDocument parse() {
            while (index < lines.length) {
                String line = lines[index].strip();
                Matcher sectionId = SECTION_ID.matcher(line);
                Matcher heading = HEADING.matcher(line);
                if (sectionId.matches()) {
                    pendingId = sectionId.group(1);
                    index++;
                } else if (heading.matches() && heading.group(1).length() == 1) {
                    startSection(heading.group(2).strip());
                    index++;
                } else if (blocks == null
                        || line.isEmpty()
                        || line.equals("---")
                        || line.startsWith("<!--")) {
                    // Text before the first section, blank lines, rules and comments carry no
                    // content.
                    index++;
                } else if (heading.matches()) {
                    blocks.add(
                            new Block.Heading(
                                    heading.group(1).length(), inline(heading.group(2).strip())));
                    index++;
                } else if (line.startsWith("```")) {
                    blocks.add(code());
                } else if (IMAGE.matcher(line).matches()) {
                    Matcher image = IMAGE.matcher(line);
                    image.matches();
                    blocks.add(new Block.Image(image.group(1), image.group(2)));
                    index++;
                } else if (line.startsWith("|")) {
                    blocks.add(table());
                } else if (line.startsWith(">")) {
                    blocks.add(new Block.Note(inline(note())));
                } else if (BULLET.matcher(line).matches()) {
                    blocks.add(new Block.ListBlock(false, items(BULLET)));
                } else if (NUMBERED.matcher(line).matches()) {
                    blocks.add(new Block.ListBlock(true, items(NUMBERED)));
                } else {
                    blocks.add(new Block.Paragraph(inline(paragraph())));
                }
            }
            finishSection();
            return new HelpDocument(sections);
        }

        private void startSection(String sectionTitle) {
            finishSection();
            title = sectionTitle;
            id = pendingId != null ? pendingId : slug(sectionTitle);
            pendingId = null;
            blocks = new ArrayList<>();
        }

        private void finishSection() {
            if (blocks != null) {
                sections.add(new HelpSection(id, title, blocks));
            }
        }

        private Block code() {
            index++;
            StringBuilder sb = new StringBuilder();
            while (index < lines.length && !lines[index].strip().startsWith("```")) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(lines[index]);
                index++;
            }
            index++; // closing fence
            return new Block.Code(sb.toString());
        }

        private Block table() {
            List<List<List<Span>>> rows = new ArrayList<>();
            while (index < lines.length && lines[index].strip().startsWith("|")) {
                String row = lines[index].strip();
                index++;
                if (row.matches("^\\|[\\s|:-]+\\|?$")) {
                    continue; // separator line under the header
                }
                String inner =
                        row.substring(1, row.endsWith("|") ? row.length() - 1 : row.length());
                rows.add(
                        Arrays.stream(inner.split("\\|", -1))
                                .map(cell -> inline(cell.strip()))
                                .toList());
            }
            return new Block.Table(rows.getFirst(), rows.subList(1, rows.size()));
        }

        private String note() {
            StringBuilder sb = new StringBuilder();
            while (index < lines.length && lines[index].strip().startsWith(">")) {
                append(sb, lines[index].strip().substring(1).strip());
                index++;
            }
            return sb.toString();
        }

        private List<List<Span>> items(Pattern marker) {
            List<List<Span>> items = new ArrayList<>();
            while (index < lines.length) {
                Matcher m = marker.matcher(lines[index].strip());
                if (!m.matches()) {
                    break;
                }
                StringBuilder sb = new StringBuilder(m.group(1).strip());
                index++;
                // Indented lines continue the current item.
                while (index < lines.length
                        && lines[index].startsWith("  ")
                        && !lines[index].isBlank()
                        && !BULLET.matcher(lines[index].strip()).matches()
                        && !NUMBERED.matcher(lines[index].strip()).matches()) {
                    append(sb, lines[index].strip());
                    index++;
                }
                items.add(inline(sb.toString()));
            }
            return items;
        }

        private String paragraph() {
            StringBuilder sb = new StringBuilder(lines[index].strip());
            index++;
            while (index < lines.length) {
                String line = lines[index].strip();
                if (line.isEmpty()
                        || line.startsWith("#")
                        || line.startsWith("|")
                        || line.startsWith(">")
                        || line.startsWith("```")
                        || line.startsWith("<!--")
                        || IMAGE.matcher(line).matches()
                        || BULLET.matcher(line).matches()
                        || NUMBERED.matcher(line).matches()) {
                    break;
                }
                append(sb, line);
                index++;
            }
            return sb.toString();
        }

        private static void append(StringBuilder sb, String text) {
            if (!sb.isEmpty() && !text.isEmpty()) {
                sb.append(' ');
            }
            sb.append(text);
        }

        private static String slug(String text) {
            return fold(text).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        }
    }
}
