package com.bioinfo.cytoscope.server.workspace;

import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.service.Example;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps the workspaces in memory: each one is forgotten after a period without request, and the
 * least recently used ones make room when the memory set aside is full. The visitors' files are
 * never written to disk.
 */
public final class WorkspaceRegistry {

    /** Time a workspace is kept after its last request. */
    public static final Duration DEFAULT_TIME_TO_LIVE = Duration.ofMinutes(30);

    /** Memory set aside for all the workspaces together. */
    public static final long DEFAULT_MAX_BYTES = 512L * 1024 * 1024;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Duration timeToLive;
    private final long maxBytes;
    private final Clock clock;
    private final Map<String, Workspace> workspaces = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Instant> lastAccess = new LinkedHashMap<>();

    /** Creates a registry with the default limits. */
    public WorkspaceRegistry() {
        this(DEFAULT_TIME_TO_LIVE, DEFAULT_MAX_BYTES, Clock.systemUTC());
    }

    /**
     * Creates a registry.
     *
     * @param timeToLive time a workspace is kept after its last request
     * @param maxBytes memory set aside for all the workspaces
     * @param clock source of the current time
     */
    public WorkspaceRegistry(Duration timeToLive, long maxBytes, Clock clock) {
        this.timeToLive = timeToLive;
        this.maxBytes = maxBytes;
        this.clock = clock;
    }

    /**
     * Returns the time a workspace is kept after its last request.
     *
     * @return the duration
     */
    public Duration timeToLive() {
        return timeToLive;
    }

    /**
     * Creates a workspace.
     *
     * @param example the example it is opened from, or null
     * @param language language of the names it creates
     * @param experiment the initial set-up
     * @param samples the initial tubes, by identifier
     * @return the workspace
     * @throws CapacityException if the tubes do not fit in memory
     */
    public synchronized Workspace create(Example example, String language, Experiment experiment,
                                         Map<String, FcsSample> samples) {
        removeExpired();
        Workspace workspace = new Workspace(newId(), example, language, experiment);
        makeRoom(workspace, samples.values().stream().mapToLong(Workspace::footprint).sum());
        samples.forEach(workspace::put);
        workspaces.put(workspace.id(), workspace);
        lastAccess.put(workspace.id(), clock.instant());
        return workspace;
    }

    /**
     * Returns a workspace and restarts its time to live.
     *
     * @param id identifier of the workspace
     * @return the workspace
     * @throws WorkspaceGoneException if it has expired or never existed
     */
    public synchronized Workspace get(String id) {
        removeExpired();
        Workspace workspace = workspaces.get(id);
        if (workspace == null) {
            throw new WorkspaceGoneException(id);
        }
        lastAccess.put(id, clock.instant());
        return workspace;
    }

    /**
     * Adds a tube to a workspace, making room if needed.
     *
     * @param workspace the workspace
     * @param sampleId identifier of the tube
     * @param sample the tube
     * @throws CapacityException if the tube does not fit, even after forgetting the other
     *     workspaces that are not in use
     */
    public synchronized void store(Workspace workspace, String sampleId, FcsSample sample) {
        makeRoom(workspace, Workspace.footprint(sample));
        workspace.put(sampleId, sample);
    }

    /**
     * Forgets a workspace.
     *
     * @param id identifier of the workspace
     */
    public synchronized void delete(String id) {
        workspaces.remove(id);
        lastAccess.remove(id);
    }

    /**
     * Forgets the workspaces whose time to live has passed.
     *
     * @return the number of workspaces forgotten
     */
    public synchronized int removeExpired() {
        Instant limit = clock.instant().minus(timeToLive);
        int removed = 0;
        Iterator<Map.Entry<String, Instant>> entries = lastAccess.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Instant> entry = entries.next();
            if (entry.getValue().isBefore(limit)) {
                workspaces.remove(entry.getKey());
                entries.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Returns the number of workspaces kept.
     *
     * @return the count
     */
    public synchronized int size() {
        return workspaces.size();
    }

    /**
     * Returns the memory taken by all the workspaces.
     *
     * @return the size, in bytes
     */
    public synchronized long bytes() {
        return workspaces.values().stream().mapToLong(Workspace::bytes).sum();
    }

    private void makeRoom(Workspace owner, long needed) {
        if (needed > maxBytes) {
            throw new CapacityException("The files are too large for this server");
        }
        long used = bytes();
        Iterator<Workspace> oldest = workspaces.values().iterator();
        while (used + needed > maxBytes && oldest.hasNext()) {
            Workspace other = oldest.next();
            if (other != owner) {
                used -= other.bytes();
                lastAccess.remove(other.id());
                oldest.remove();
            }
        }
        if (used + needed > maxBytes) {
            throw new CapacityException("The workspace holds too many events for this server: "
                    + "remove some tubes first");
        }
    }

    private static String newId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
