package com.bioinfo.cytoscope.experiment;

import com.bioinfo.cytoscope.fcs.FcsSample;
import java.util.Optional;

/**
 * Keeps the values of the loaded tubes, by identifier. The desktop application keeps them in
 * memory; the web server keeps them for a limited time.
 */
public interface SampleStore {

    /**
     * Finds the values of a tube.
     *
     * @param id identifier of the tube
     * @return the sample, if it is still available
     */
    Optional<FcsSample> find(String id);

    /**
     * Returns the values of a tube that must be available.
     *
     * @param id identifier of the tube
     * @return the sample
     * @throws MissingSampleException if the tube is no longer available
     */
    default FcsSample require(String id) {
        return find(id).orElseThrow(() -> new MissingSampleException(id));
    }
}
