package com.xml;

/**
 * Représente une erreur XML détectée (syntaxe, structure, validation).
 */
public class XMLError {
    public final String message;
    public final int    lineNumber;
    public final String type;   // SYNTAX, STRUCTURE, VALIDATION_ERROR, WARNING, FATAL_*

    private String id;
    private int column;
    private String severity; // "error", "warning"
    private String code;
    private String fragment;
    private int fragmentStartLine;
    private int fragmentEndLine;

    public XMLError(String message, int lineNumber, String type) {
        this.message = message;
        this.lineNumber = lineNumber;
        this.type = type;
        this.id = generateId();
        this.column = 0;
        this.severity = mapSeverity(type);
        this.code = type;
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

    public String getId() { return id; }
    public int getColumn() { return column; }
    public void setColumn(int column) { this.column = column; }
    
    public String getSeverity() { return severity; }
    public String getCode() { return code; }
    
    public String getFragment() { return fragment; }
    public void setFragment(String fragment) { this.fragment = fragment; }
    
    public int getFragmentStartLine() { return fragmentStartLine; }
    public void setFragmentStartLine(int line) { this.fragmentStartLine = line; }
    
    public int getFragmentEndLine() { return fragmentEndLine; }
    public void setFragmentEndLine(int line) { this.fragmentEndLine = line; }

    @Override
    public String toString() {
        return "[" + type + "] ligne " + lineNumber + " : " + message;
    }

    private String generateId() {
        return "error-" + System.nanoTime();
    }

    private String mapSeverity(String type) {
        if (type.contains("FATAL")) return "error";
        if (type.equals("WARNING")) return "warning";
        return "error";
    }
}
