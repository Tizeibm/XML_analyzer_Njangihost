package com.xml.handlers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Lightweight parser for instant syntax error detection.
 * 
 * This parser performs O(n) single-pass syntax checks without building a DOM:
 * - Bracket matching (<, >)
 * - Tag close detection (</tag> must match <tag>)
 * - CDATA/Comment boundary checks
 * 
 * Designed for <10ms response on typical edits. Does NOT validate against schema.
 * Use LargeXmlValidator (in background) for full validation.
 */
public class MicroParser {
    
    /**
     * A syntax error detected by the MicroParser.
     */
    public record SyntaxError(
        int line,
        int column,
        String message,
        ErrorType type
    ) {}
    
    public enum ErrorType {
        UNCLOSED_TAG,
        MISMATCHED_TAG,
        UNCLOSED_BRACKET,
        UNCLOSED_COMMENT,
        UNCLOSED_CDATA,
        INVALID_TAG_NAME,
        UNEXPECTED_CLOSE_TAG
    }
    
    /**
     * Parse text and return syntax errors.
     * This is designed to be very fast (single pass, no allocations except errors).
     */
    public List<SyntaxError> parse(String text) {
        List<SyntaxError> errors = new ArrayList<>();
        Deque<TagInfo> tagStack = new ArrayDeque<>();
        
        int line = 1;
        int column = 1;
        int i = 0;
        int length = text.length();
        
        while (i < length) {
            char c = text.charAt(i);
            
            if (c == '\n') {
                line++;
                column = 1;
                i++;
                continue;
            }
            
            if (c == '<') {
                int tagStart = i;
                int tagLine = line;
                int tagColumn = column;
                
                // Scan ahead to find the tag type
                i++;
                column++;
                
                if (i >= length) {
                    errors.add(new SyntaxError(tagLine, tagColumn, "Unclosed bracket '<'", ErrorType.UNCLOSED_BRACKET));
                    break;
                }
                
                char next = text.charAt(i);
                
                // Comment: <!-- ... -->
                if (next == '!' && i + 2 < length && text.charAt(i + 1) == '-' && text.charAt(i + 2) == '-') {
                    i += 3;
                    column += 3;
                    int commentEnd = findCommentEnd(text, i);
                    if (commentEnd < 0) {
                        errors.add(new SyntaxError(tagLine, tagColumn, "Unclosed comment", ErrorType.UNCLOSED_COMMENT));
                        break;
                    }
                    // Update line/column for multi-line comments
                    for (int j = i; j < commentEnd; j++) {
                        if (text.charAt(j) == '\n') { line++; column = 1; } else { column++; }
                    }
                    i = commentEnd + 3; // Skip past -->
                    column += 3;
                    continue;
                }
                
                // CDATA: <![CDATA[ ... ]]>
                if (next == '!' && i + 8 < length && text.substring(i + 1, i + 8).equals("[CDATA[")) {
                    i += 9; // Skip past <![CDATA[
                    column += 9;
                    int cdataEnd = findCDataEnd(text, i);
                    if (cdataEnd < 0) {
                        errors.add(new SyntaxError(tagLine, tagColumn, "Unclosed CDATA section", ErrorType.UNCLOSED_CDATA));
                        break;
                    }
                    for (int j = i; j < cdataEnd; j++) {
                        if (text.charAt(j) == '\n') { line++; column = 1; } else { column++; }
                    }
                    i = cdataEnd + 3;
                    column += 3;
                    continue;
                }
                
                // XML declaration or DOCTYPE
                if (next == '?' || next == '!') {
                    int end = text.indexOf('>', i);
                    if (end < 0) {
                        errors.add(new SyntaxError(tagLine, tagColumn, "Unclosed bracket '<'", ErrorType.UNCLOSED_BRACKET));
                        break;
                    }
                    for (int j = i; j <= end; j++) {
                        if (text.charAt(j) == '\n') { line++; column = 1; } else { column++; }
                    }
                    i = end + 1;
                    continue;
                }
                
                // Closing tag: </tag>
                boolean isClosing = (next == '/');
                if (isClosing) {
                    i++;
                    column++;
                }
                
                // Parse tag name
                StringBuilder tagName = new StringBuilder();
                while (i < length) {
                    c = text.charAt(i);
                    if (c == '>' || c == '/' || c == ' ' || c == '\t' || c == '\n' || c == '\r') break;
                    tagName.append(c);
                    i++;
                    if (c == '\n') { line++; column = 1; } else { column++; }
                }
                
                String name = tagName.toString().trim();
                if (name.isEmpty()) {
                    errors.add(new SyntaxError(tagLine, tagColumn, "Invalid tag name", ErrorType.INVALID_TAG_NAME));
                    // Skip to end of tag
                    while (i < length && text.charAt(i) != '>') {
                        if (text.charAt(i) == '\n') { line++; column = 1; } else { column++; }
                        i++;
                    }
                    if (i < length) { i++; column++; }
                    continue;
                }
                
                // Skip attributes
                while (i < length && text.charAt(i) != '>') {
                    if (text.charAt(i) == '\n') { line++; column = 1; } else { column++; }
                    i++;
                }
                
                if (i >= length) {
                    errors.add(new SyntaxError(tagLine, tagColumn, "Unclosed tag '<" + name + "'", ErrorType.UNCLOSED_BRACKET));
                    break;
                }
                
                // Check for self-closing
                boolean selfClosing = (i > 0 && text.charAt(i - 1) == '/');
                
                i++; // Skip '>'
                column++;
                
                if (isClosing) {
                    // Closing tag - must match top of stack
                    if (tagStack.isEmpty()) {
                        errors.add(new SyntaxError(tagLine, tagColumn, "Unexpected closing tag '</" + name + ">'", ErrorType.UNEXPECTED_CLOSE_TAG));
                    } else {
                        TagInfo top = tagStack.peek();
                        if (!top.name.equals(name)) {
                            errors.add(new SyntaxError(tagLine, tagColumn, 
                                "Mismatched tag: expected '</" + top.name + ">' but found '</" + name + ">'", 
                                ErrorType.MISMATCHED_TAG));
                            // Pop anyway to try to recover
                            tagStack.pop();
                        } else {
                            tagStack.pop();
                        }
                    }
                } else if (!selfClosing) {
                    // Opening tag - push to stack
                    tagStack.push(new TagInfo(name, tagLine, tagColumn));
                }
                // Self-closing tags don't affect the stack
                
            } else {
                i++;
                column++;
            }
        }
        
        // Check for unclosed tags
        while (!tagStack.isEmpty()) {
            TagInfo unclosed = tagStack.pop();
            errors.add(new SyntaxError(unclosed.line, unclosed.column, 
                "Unclosed tag '<" + unclosed.name + ">'", ErrorType.UNCLOSED_TAG));
        }
        
        return errors;
    }
    
    /**
     * Quick check if text has any obvious syntax errors.
     * Returns true if text appears valid (no errors found).
     */
    public boolean isValid(String text) {
        return parse(text).isEmpty();
    }
    
    // --- Helpers ---
    
    private record TagInfo(String name, int line, int column) {}
    
    private int findCommentEnd(String text, int start) {
        int idx = text.indexOf("-->", start);
        return idx >= 0 ? idx : -1;
    }
    
    private int findCDataEnd(String text, int start) {
        int idx = text.indexOf("]]>", start);
        return idx >= 0 ? idx : -1;
    }
}
