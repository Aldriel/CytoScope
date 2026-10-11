package com.bioinfo.cytoscope.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SampleAnnotatorTest {

    private static SampleInfo guess(String name, Map<String, String> keywords) {
        return SampleAnnotator.guess("id", Tubes.tube(name, 0, keywords));
    }

    @ParameterizedTest
    @CsvSource({
        "081120_Tube_004 TMZ 1, TMZ",
        "NC 2, NC",
        "Specimen_001_Tube_003_DOXY_3, DOXY",
        "12, 12",
        "Cd_2.5uM_rep2, CD 2.5UM",
        "tmz+azi, TMZ+AZI"
    })
    void cleansFileNamesIntoGroups(String name, String group) {
        assertEquals(group, SampleAnnotator.groupOf(name));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "Cd_2.5uM_1; 2.5",
        "IL-2 10 ng/mL; 10",
        "Dox 1,5 µM; 1.5",
        "H2O2 0.1%; 0.1"
    })
    void readsDoses(String name, double dose) {
        assertEquals(dose, SampleAnnotator.parseDose(name), 1e-12);
    }

    @Test
    void namesWithoutDoseHaveNone() {
        assertTrue(Double.isNaN(SampleAnnotator.parseDose("NC 1")));
    }

    @Test
    void cytoScopeKeywordsComeFirst() {
        SampleInfo stain = guess("Control_PI_only", Map.of(
                SampleAnnotator.ROLE_KEYWORD, "single_stain:PI-A",
                SampleAnnotator.GROUP_KEYWORD, "ignored"));
        assertEquals(SampleRole.SINGLE_STAIN, stain.role());
        assertEquals("PI-A", stain.stainChannel());
        assertEquals("", stain.group());

        SampleInfo sample = guess("Cd_5uM_1", Map.of(SampleAnnotator.ROLE_KEYWORD, "SAMPLE",
                SampleAnnotator.GROUP_KEYWORD, "5 µM", SampleAnnotator.DOSE_KEYWORD, "5"));
        assertEquals(new SampleInfo("id", "Cd_5uM_1", SampleRole.SAMPLE, "5 µM", 5, ""),
                sample);

        SampleInfo unknownRole = guess("x", Map.of(SampleAnnotator.ROLE_KEYWORD, "PLATE",
                SampleAnnotator.DOSE_KEYWORD, "abc"));
        assertEquals(SampleRole.SAMPLE, unknownRole.role());
        assertTrue(Double.isNaN(unknownRole.dose()));

        SampleInfo unstained = guess("u", Map.of(SampleAnnotator.ROLE_KEYWORD, "UNSTAINED",
                SampleAnnotator.DOSE_KEYWORD, "-2"));
        assertEquals(SampleRole.UNSTAINED, unstained.role());
        assertTrue(Double.isNaN(unstained.dose()));

        SampleInfo grouped = guess("Tube_001", Map.of(SampleAnnotator.GROUP_KEYWORD, "NC",
                SampleAnnotator.DOSE_KEYWORD, "0"));
        assertEquals("NC", grouped.group());
        assertEquals(0, grouped.dose());
    }

    @ParameterizedTest
    @CsvSource({
        "Unstained, UNSTAINED, ''",
        "Tube_002 Non marqué, UNSTAINED, ''",
        "Autofluorescence, UNSTAINED, ''",
        "PI only, SINGLE_STAIN, PI-A",
        "FITC-A, SINGLE_STAIN, FITC-A",
        "Annexin V seul, SINGLE_STAIN, FITC-A",
        "Annexin, SINGLE_STAIN, FITC-A",
        "FSC, SAMPLE, ''",
        "TMZ 2, SAMPLE, ''"
    })
    void recognizesControlsByName(String name, SampleRole role, String channel) {
        SampleInfo info = guess(name, Map.of());
        assertEquals(role, info.role());
        assertEquals(channel, info.stainChannel());
    }

    @Test
    void annexinWithoutFitcIsASample() {
        FcsSample sample = new FcsSample("Annexin only", Map.of(),
                List.of(new Channel("FSC-A", "", 1), new Channel("PE-A", "", 1)),
                new float[][] {{1}, {1}});
        assertEquals(SampleRole.SAMPLE, SampleAnnotator.guess("a", sample).role());
        FcsSample labeled = new FcsSample("CD4", Map.of(),
                List.of(new Channel("PE-A", "CD4-PE", 1)), new float[][] {{1}});
        assertEquals("PE-A", SampleAnnotator.guess("b", labeled).stainChannel());
    }
}
