package com.xml;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.xml.handlers.MicroParser;
import com.xml.handlers.MicroParser.ErrorType;
import com.xml.handlers.MicroParser.SyntaxError;

/**
 * Unit tests for MicroParser instant syntax checks.
 */
public class MicroParserTest {

    private final MicroParser parser = new MicroParser();

    @Test
    void testValidXml() {
        String xml = """
            <?xml version="1.0"?>
            <root>
                <child>text</child>
            </root>
            """;
        assertTrue(parser.isValid(xml));
    }

    @Test
    void testUnclosedTag() {
        String xml = "<root><child></root>";
        List<SyntaxError> errors = parser.parse(xml);
        
        // Parser reports: 1) MISMATCHED_TAG (expected </child> but found </root>)
        //                 2) UNCLOSED_TAG (<root> never closed after recovery)
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.MISMATCHED_TAG));
        assertTrue(errors.get(0).message().contains("child"));
    }

    @Test
    void testMissingCloseTag() {
        String xml = "<root><child>";
        List<SyntaxError> errors = parser.parse(xml);
        
        // Should report two unclosed tags
        assertEquals(2, errors.size());
        assertTrue(errors.stream().allMatch(e -> e.type() == ErrorType.UNCLOSED_TAG));
    }

    @Test
    void testUnexpectedCloseTag() {
        String xml = "</unexpected>";
        List<SyntaxError> errors = parser.parse(xml);
        
        assertEquals(1, errors.size());
        assertEquals(ErrorType.UNEXPECTED_CLOSE_TAG, errors.get(0).type());
    }

    @Test
    void testSelfClosingTag() {
        String xml = "<root><child/></root>";
        assertTrue(parser.isValid(xml));
    }

    @Test
    void testUnclosedComment() {
        String xml = "<root><!-- unclosed comment";
        List<SyntaxError> errors = parser.parse(xml);
        
        assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.UNCLOSED_COMMENT));
    }

    @Test
    void testValidComment() {
        String xml = "<root><!-- valid comment --></root>";
        assertTrue(parser.isValid(xml));
    }

    @Test
    void testUnclosedCData() {
        String xml = "<root><![CDATA[ unclosed cdata";
        List<SyntaxError> errors = parser.parse(xml);
        
        assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.UNCLOSED_CDATA));
    }

    @Test
    void testValidCData() {
        String xml = "<root><![CDATA[some <data> here]]></root>";
        assertTrue(parser.isValid(xml));
    }

    @Test
    void testUnclosedBracket() {
        String xml = "<root><unclosed";
        List<SyntaxError> errors = parser.parse(xml);
        
        assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.UNCLOSED_BRACKET));
    }

    @Test
    void testNestedTags() {
        String xml = "<a><b><c></c></b></a>";
        assertTrue(parser.isValid(xml));
    }

    @Test
    void testMismatchedNestedTags() {
        String xml = "<a><b><c></b></c></a>";
        List<SyntaxError> errors = parser.parse(xml);
        
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.MISMATCHED_TAG));
    }

    @Test
    void testXmlDeclaration() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root/>";
        assertTrue(parser.isValid(xml));
    }

    @Test
    void testDoctype() {
        String xml = "<!DOCTYPE html><html></html>";
        assertTrue(parser.isValid(xml));
    }

    @Test
    void testLineAndColumnTracking() {
        String xml = "<root>\n  <child>\n</root>";
        List<SyntaxError> errors = parser.parse(xml);
        
        // Should detect mismatch and report correct line
        assertFalse(errors.isEmpty());
        // The mismatch is on line 3 when </root> is found instead of </child>
        SyntaxError error = errors.stream()
            .filter(e -> e.type() == ErrorType.MISMATCHED_TAG)
            .findFirst()
            .orElse(null);
        assertNotNull(error);
        assertEquals(3, error.line());
    }

    @Test
    void testEmptyDocument() {
        String xml = "";
        assertTrue(parser.isValid(xml));
    }

    @Test
    void testWhitespaceOnly() {
        String xml = "   \n\n   ";
        assertTrue(parser.isValid(xml));
    }
}
