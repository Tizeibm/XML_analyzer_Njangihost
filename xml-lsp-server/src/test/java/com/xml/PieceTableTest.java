package com.xml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.xml.services.PieceTable;

/**
 * Unit tests for PieceTable data structure.
 */
public class PieceTableTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("piecetable-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .map(Path::toFile)
                .sorted((o1, o2) -> -o1.compareTo(o2))
                .forEach(File::delete);
    }

    @Test
    void testInsertAtStart() throws IOException {
        File file = createTempFile("Hello World");
        PieceTable table = new PieceTable(file);
        
        table.insert(0, "***");
        
        assertEquals("***Hello World", readAll(table));
    }

    @Test
    void testInsertAtEnd() throws IOException {
        File file = createTempFile("Hello World");
        PieceTable table = new PieceTable(file);
        
        table.insert(11, "!!!");
        
        assertEquals("Hello World!!!", readAll(table));
    }

    @Test
    void testInsertInMiddle() throws IOException {
        File file = createTempFile("Hello World");
        PieceTable table = new PieceTable(file);
        
        table.insert(5, " Beautiful");
        
        assertEquals("Hello Beautiful World", readAll(table));
    }

    @Test
    void testDeleteFromStart() throws IOException {
        File file = createTempFile("Hello World");
        PieceTable table = new PieceTable(file);
        
        table.delete(0, 6);
        
        assertEquals("World", readAll(table));
    }

    @Test
    void testDeleteFromEnd() throws IOException {
        File file = createTempFile("Hello World");
        PieceTable table = new PieceTable(file);
        
        table.delete(5, 6);
        
        assertEquals("Hello", readAll(table));
    }

    @Test
    void testDeleteFromMiddle() throws IOException {
        File file = createTempFile("Hello Beautiful World");
        PieceTable table = new PieceTable(file);
        
        table.delete(5, 10);
        
        assertEquals("Hello World", readAll(table));
    }

    @Test
    void testReplaceSmaller() throws IOException {
        File file = createTempFile("Hello World");
        PieceTable table = new PieceTable(file);
        
        table.replace(6, 5, "XML");
        
        assertEquals("Hello XML", readAll(table));
    }

    @Test
    void testReplaceLarger() throws IOException {
        File file = createTempFile("Hello World");
        PieceTable table = new PieceTable(file);
        
        table.replace(6, 5, "Piece Table");
        
        assertEquals("Hello Piece Table", readAll(table));
    }

    @Test
    void testMultipleOperations() throws IOException {
        File file = createTempFile("ABCDEF");
        PieceTable table = new PieceTable(file);
        
        table.insert(3, "123");    // ABC123DEF
        table.delete(0, 1);       // BC123DEF
        table.replace(2, 3, "X"); // BCXDEF
        
        assertEquals("BCXDEF", readAll(table));
    }

    @Test
    void testGetRange() throws IOException {
        File file = createTempFile("Hello World");
        PieceTable table = new PieceTable(file);
        
        table.insert(5, " Beautiful");
        
        String range = table.getRange(6, 9); // "Beautiful"
        assertEquals("Beautiful", range);
    }

    @Test
    void testInputStream() throws IOException {
        File file = createTempFile("Hello World");
        PieceTable table = new PieceTable(file);
        
        table.insert(5, " Amazing");
        
        String expected = "Hello Amazing World";
        
        try (InputStream is = table.getInputStream()) {
            String result = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(expected, result);
        }
    }

    @Test
    void testEmptyPieceTable() throws IOException {
        PieceTable table = new PieceTable();
        
        table.insert(0, "Hello");
        table.insert(5, " World");
        
        assertEquals("Hello World", readAll(table));
    }

    @Test
    void testGetLength() throws IOException {
        File file = createTempFile("Hello");
        PieceTable table = new PieceTable(file);
        
        assertEquals(5, table.getLength());
        
        table.insert(5, " World");
        assertEquals(11, table.getLength());
        
        table.delete(0, 6);
        assertEquals(5, table.getLength());
    }

    // --- Helpers ---

    private File createTempFile(String content) throws IOException {
        File file = tempDir.resolve("test.txt").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    private String readAll(PieceTable table) throws IOException {
        try (InputStream is = table.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
