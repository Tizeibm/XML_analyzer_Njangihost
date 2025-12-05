package com.xml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.xml.handlers.PatchedInputStream;
import com.xml.models.Patch;
import com.xml.models.PatchType;

/**
 * Tests for PatchedInputStream to verify on-the-fly patch application
 * and streaming behavior without loading entire file into memory.
 */
public class PatchedInputStreamTest {

    private Path tempDir;
    private File testFile;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("patched-stream-test");
        testFile = tempDir.resolve("test.xml").toFile();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Cleanup
        Files.walk(tempDir)
                .map(Path::toFile)
                .sorted((o1, o2) -> -o1.compareTo(o2))
                .forEach(File::delete);
    }

    @Test
    void testNoPatchesStreamIdentical() throws IOException {
        // Write test content
        String content = "<root><item>Test</item></root>";
        Files.writeString(testFile.toPath(), content);

        // Create stream with no patches
        List<Patch> patches = new ArrayList<>();
        
        try (PatchedInputStream pis = new PatchedInputStream(testFile, patches)) {
            String result = new String(pis.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(content, result);
        }
    }

    @Test
    void testSingleReplacementPatch() throws IOException {
        // Create original file
        String original = "<root><item>Original</item></root>";
        Files.writeString(testFile.toPath(), original);
        
        // Patch: Replace "Original" with "Patched"
        // Offset 12-20 contains "Original" (8 bytes)
        List<Patch> patches = new ArrayList<>();
        patches.add(new Patch(12, 20, "Patched", PatchType.REPLACE, "f1"));
        
        // Read patched stream
        try (PatchedInputStream pis = new PatchedInputStream(testFile, patches)) {
            String result = new String(pis.readAllBytes(), StandardCharsets.UTF_8);
            String expected = "<root><item>Patched</item></root>";
            assertEquals(expected, result);
        }
    }

    @Test
    void testMultiplePatches() throws IOException {
        // Original: "<root><a>A</a><b>B</b></root>"
        String original = "<root><a>A</a><b>B</b></root>";
        Files.writeString(testFile.toPath(), original);
        
        List<Patch> patches = new ArrayList<>();
        
        // Patch 1: Replace "A" (offset 9) with "Alpha"
        patches.add(new Patch(9, 10, "Alpha", PatchType.REPLACE, "f1"));
        
        // Patch 2: Replace "B" (offset 17) with "Beta"
        // <root><a> is 9 chars. A is at 9. </a><b> is 7 chars. 9+1+7 = 17.
        patches.add(new Patch(17, 18, "Beta", PatchType.REPLACE, "f2"));
        
        // Read patched stream
        try (PatchedInputStream pis = new PatchedInputStream(testFile, patches)) {
            String result = new String(pis.readAllBytes(), StandardCharsets.UTF_8);
            String expected = "<root><a>Alpha</a><b>Beta</b></root>";
            assertEquals(expected, result);
        }
    }

    @Test
    void testDeletionPatch() throws IOException {
        // Original: "<root><item>ToDelete</item><item>Keep</item></root>"
        String original = "<root><item>ToDelete</item><item>Keep</item></root>";
        Files.writeString(testFile.toPath(), original);
        
        List<Patch> patches = new ArrayList<>();
        
        // Delete first item: offset 6-27 (entire <item>ToDelete</item>)
        // <root> is 6. <item>ToDelete</item> is 6+8+7 = 21. 6+21 = 27.
        patches.add(new Patch(6, 27, "", PatchType.DELETE, "f1"));
        
        // Read patched stream
        try (PatchedInputStream pis = new PatchedInputStream(testFile, patches)) {
            String result = new String(pis.readAllBytes(), StandardCharsets.UTF_8);
            String expected = "<root><item>Keep</item></root>";
            assertEquals(expected, result);
        }
    }

    @Test
    void testInsertionPatch() throws IOException {
        // Original: "<root></root>"
        String original = "<root></root>";
        Files.writeString(testFile.toPath(), original);
        
        List<Patch> patches = new ArrayList<>();
        
        // Insert at position 6 (between > and <)
        patches.add(new Patch(6, 6, "<item>Inserted</item>", PatchType.INSERT, "f1"));
        
        // Read patched stream
        try (PatchedInputStream pis = new PatchedInputStream(testFile, patches)) {
            String result = new String(pis.readAllBytes(), StandardCharsets.UTF_8);
            String expected = "<root><item>Inserted</item></root>";
            assertEquals(expected, result);
        }
    }

    @Test
    void testByteByByteReading() throws IOException {
        // Test reading byte-by-byte to ensure the read() method works correctly
        String original = "ABCDEFGH";
        Files.writeString(testFile.toPath(), original);
        
        List<Patch> patches = new ArrayList<>();
        // Replace "DEF" (offset 3-6) with "123"
        patches.add(new Patch(3, 6, "123", PatchType.REPLACE, "f1"));
        
        // Read byte by byte
        StringBuilder result = new StringBuilder();
        try (PatchedInputStream pis = new PatchedInputStream(testFile, patches)) {
            int b;
            while ((b = pis.read()) != -1) {
                result.append((char) b);
            }
        }
        
        assertEquals("ABC123GH", result.toString());
    }

    @Test
    void testBufferedReading() throws IOException {
        // Test reading with buffer to ensure the read(byte[], offset, len) method works
        String original = "0123456789ABCDEFGHIJ";
        Files.writeString(testFile.toPath(), original);
        
        List<Patch> patches = new ArrayList<>();
        // Replace "56789" (offset 5-10) with "XXX"
        patches.add(new Patch(5, 10, "XXX", PatchType.REPLACE, "f1"));
        
        // Read with buffer
        byte[] buffer = new byte[8];
        StringBuilder result = new StringBuilder();
        
        try (PatchedInputStream pis = new PatchedInputStream(testFile, patches)) {
            int bytesRead;
            while ((bytesRead = pis.read(buffer, 0, buffer.length)) != -1) {
                result.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
        }
        
        assertEquals("01234XXXABCDEFGHIJ", result.toString());
    }

    @Test
    void testLogicalPositionTracking() throws IOException {
        String original = "0123456789";
        Files.writeString(testFile.toPath(), original);
        
        List<Patch> patches = new ArrayList<>();
        // Replace "456" with "ABCDEFGH" (expanding content)
        patches.add(new Patch(4, 7, "ABCDEFGH", PatchType.REPLACE, "f1"));
        
        try (PatchedInputStream pis = new PatchedInputStream(testFile, patches)) {
            // Read all content
            byte[] data = pis.readAllBytes();
            
            // Check logical position is correct (original 10 bytes - 3 replaced + 8 inserted = 15 bytes)
            assertEquals(15, pis.getLogicalPosition());
            assertEquals("0123ABCDEFGH789", new String(data, StandardCharsets.UTF_8));
        }
    }
}
