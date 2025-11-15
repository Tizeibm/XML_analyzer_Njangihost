package com.manus.xml;

public class XMLError {
    public final String message;
    public final int    lineNumber;
    public final String type;   // SYNTAX, STRUCTURE, VALIDATION...

    public XMLError(String message, int lineNumber, String type) {
        this.message = message;
        this.lineNumber = lineNumber;
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "[" + type + "] ligne " + lineNumber + " : " + message;
    }
}