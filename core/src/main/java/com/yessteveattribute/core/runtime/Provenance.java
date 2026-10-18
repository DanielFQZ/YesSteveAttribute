package com.yessteveattribute.core.runtime;

import java.util.Objects;

/** Identifies why one contribution was added to an attribute snapshot. */
public record Provenance(String type, String source, String detail) {
    public Provenance {
        Objects.requireNonNull(type, "type");
        source = source == null ? "" : source;
        detail = detail == null ? "" : detail;
    }
}
