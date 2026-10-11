package com.bioinfo.cytoscope.scale;

/** The kinds of axis scales offered to the user. */
public enum ScaleType {
    /** Values spread evenly: suited to light scatter and to DNA content. */
    LINEAR,
    /** One decade per equal length: the classic scale for fluorescence, without negatives. */
    LOG,
    /** Inverse hyperbolic sine: linear near zero, logarithmic for large values. */
    ARCSINH,
    /** Logicle (biexponential): like arcsinh, tuned so that compensated negatives stay visible. */
    LOGICLE
}
