package com.bioinfo.cytoscope.server.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.service.Example;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkspaceRegistryTest {

    /** A clock moved by hand. */
    private static final class ManualClock extends Clock {

        private Instant now = Instant.parse("2026-10-11T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** A tube of 100 events and 1 channel: 800 bytes with its compensated copy. */
    private static FcsSample tube() {
        return new FcsSample("t", Map.of(), List.of(new Channel("FSC-A", "", 1)),
                new float[][] {new float[100]});
    }

    private final ManualClock clock = new ManualClock();
    private final WorkspaceRegistry registry =
            new WorkspaceRegistry(Duration.ofMinutes(30), 2_000, clock);

    @Test
    void forgetsWorkspacesAfterTheirTimeToLive() {
        Workspace a = registry.create(null, "en", Experiment.empty("All"), Map.of());
        Workspace b = registry.create(Example.CADMIUM, "fr", Experiment.empty("Tous"), Map.of());
        assertNotEquals(a.id(), b.id());
        clock.advance(Duration.ofMinutes(20));
        assertSame(a, registry.get(a.id()));
        clock.advance(Duration.ofMinutes(15));
        assertEquals(1, registry.removeExpired());
        assertSame(a, registry.get(a.id()));
        assertThrows(WorkspaceGoneException.class, () -> registry.get(b.id()));
        registry.delete(a.id());
        assertEquals(0, registry.size());
        assertEquals(Duration.ofMinutes(30), registry.timeToLive());
        assertEquals(Example.CADMIUM, b.example().orElseThrow());
        assertEquals("fr", b.language());
        assertTrue(a.example().isEmpty());
    }

    @Test
    void makesRoomByForgettingTheLeastRecentlyUsed() {
        Workspace a = registry.create(null, "en", Experiment.empty("All"),
                Map.of("s1", tube()));
        Workspace b = registry.create(null, "en", Experiment.empty("All"), Map.of());
        assertEquals(800, registry.bytes());
        registry.store(b, "s1", tube());
        assertEquals(1_600, registry.bytes());
        registry.get(a.id());
        registry.store(b, b.nextSampleId(), tube());
        assertEquals(1, registry.size());
        assertThrows(WorkspaceGoneException.class, () -> registry.get(a.id()));
        assertEquals(List.of("s1", "s2"), List.copyOf(b.samples().keySet()));
        assertThrows(CapacityException.class, () -> registry.store(b, "s3", tube()));
        assertThrows(CapacityException.class, () -> registry.create(null, "en",
                Experiment.empty("All"), Map.of("x", new FcsSample("big", Map.of(),
                        List.of(new Channel("FSC-A", "", 1)), new float[][] {new float[300]}))));
    }

    @Test
    void workspaceKeepsTubesAndSetUp() {
        Workspace workspace = registry.create(null, "en", Experiment.empty("All"),
                Map.of("s1", tube()));
        assertEquals("s2", workspace.nextSampleId());
        assertTrue(workspace.find("s1").isPresent());
        assertEquals("All", workspace.update(e -> e).gating().root().name());
        assertThrows(NullPointerException.class, () -> workspace.update(e -> null));
        assertTrue(workspace.remove("s1"));
        assertFalse(workspace.remove("s1"));
        assertEquals(0, workspace.bytes());
    }
}
