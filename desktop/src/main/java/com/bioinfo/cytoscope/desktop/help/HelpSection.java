package com.bioinfo.cytoscope.desktop.help;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Section of the user guide, introduced by a level-1 heading.
 *
 * @param id stable identifier, used to open the guide on a given section
 * @param title title shown in the list of sections
 * @param blocks content of the section
 */
public record HelpSection(String id, String title, List<Block> blocks) {

    public HelpSection {
        blocks = List.copyOf(blocks);
    }

    /** Returns the title and text of the section without markup. */
    public String plainText() {
        return title
                + "\n"
                + blocks.stream().map(Block::plainText).collect(Collectors.joining("\n"));
    }

    @Override
    public String toString() {
        return title;
    }
}
