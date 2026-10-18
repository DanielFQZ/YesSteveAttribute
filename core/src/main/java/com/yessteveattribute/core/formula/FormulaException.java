package com.yessteveattribute.core.formula;

/** Thrown when a configured numeric expression cannot be evaluated safely. */
public final class FormulaException extends RuntimeException {
    public FormulaException(String message) {
        super(message);
    }

    public FormulaException(String message, Throwable cause) {
        super(message, cause);
    }
}
