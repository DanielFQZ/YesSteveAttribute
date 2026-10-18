package com.yessteveattribute.core.config;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Raised when one or more YSA configuration files cannot be loaded or validated.
 * The exception keeps every diagnostic so a reload can report all mistakes at
 * once instead of forcing a designer to fix them one at a time.
 */
public final class ConfigurationException extends RuntimeException {
    private final List<String> diagnostics;

    public ConfigurationException(String message) {
        this(Collections.singletonList(message));
    }

    public ConfigurationException(List<String> diagnostics) {
        super(format(diagnostics));
        List<String> safeDiagnostics = diagnostics == null ? Collections.emptyList() : diagnostics;
        this.diagnostics = Collections.unmodifiableList(safeDiagnostics.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }

    private static String format(List<String> diagnostics) {
        return diagnostics == null || diagnostics.isEmpty()
                ? "Invalid configuration"
                : String.join(System.lineSeparator(), diagnostics);
    }
}
