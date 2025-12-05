package com.xml;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.xml.handlers.MicroParser;
import com.xml.handlers.StreamingIndexer;
import com.xml.models.FragmentIndex;
import com.xml.models.FragmentMetadata;
import com.xml.services.PieceTable;

/**
 * Scalability tests for the optimal LSP architecture.
 * 
 * These tests verify:
 * 1. Memory efficiency for large files (simulated 100GB)
 * 2. Response time < 10ms for LSP operations
 * 3. Fragment indexing scalability
 */
public class ScalabilityTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("scalability-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .map(Path::toFile)
                .sorted((o1, o2) -> -o1.compareTo(o2))
                .forEach(File::delete);
    }

    // =========================================================================
    // Response Time Tests (< 10ms target)
    // =========================================================================

    @Test
    void testMicroParserResponseTime() {
        // Generate a reasonably large XML snippet (10KB - typical edit context)
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\"?>\n<root>\n");
        for (int i = 0; i < 100; i++) {
            xml.append("  <item id=\"").append(i).append("\">Value ").append(i).append("</item>\n");
        }
        xml.append("</root>");
        
        String content = xml.toString();
        MicroParser parser = new MicroParser();
        
        // Warm up
        for (int i = 0; i < 10; i++) {
            parser.parse(content);
        }
        
        // Measure
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            parser.parse(content);
        }
        long elapsed = System.nanoTime() - start;
        double avgMs = (elapsed / 100.0) / 1_000_000.0;
        
        System.out.println("MicroParser avg response time: " + String.format("%.3f", avgMs) + " ms");
        assertTrue(avgMs < 10.0, "MicroParser should respond in <10ms, was " + avgMs + "ms");
    }

    @Test
    void testFragmentIndexLookupTime() throws IOException {
        // Create a large index with 100K fragments
        FragmentIndex index = new FragmentIndex();
        
        for (int i = 0; i < 100_000; i++) {
            int tagId = index.internString("element" + (i % 1000));
            FragmentMetadata frag = new FragmentMetadata(
                i, i * 1000L, (i + 1) * 1000L, i, i + 1,
                i > 0 ? i - 1 : -1, tagId, 1
            );
            index.addFragment(frag);
        }
        
        // Warm up
        for (int i = 0; i < 1000; i++) {
            index.getFragmentById("frag_" + (i % 100_000));
        }
        
        // Measure lookup time
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            index.getFragmentById("frag_" + (i % 100_000));
        }
        long elapsed = System.nanoTime() - start;
        double avgMs = (elapsed / 10_000.0) / 1_000_000.0;
        
        System.out.println("FragmentIndex lookup avg: " + String.format("%.6f", avgMs) + " ms");
        assertTrue(avgMs < 1.0, "Fragment lookup should be <1ms, was " + avgMs + "ms");
    }

    @Test
    void testPieceTableEditTime() throws IOException {
        // Create a PieceTable with a small original file
        File file = tempDir.resolve("original.xml").toFile();
        Files.writeString(file.toPath(), "<root>Original content here</root>");
        
        PieceTable table = new PieceTable(file);
        
        // Warm up
        for (int i = 0; i < 100; i++) {
            table.insert(10, "X");
            table.delete(10, 1);
        }
        
        // Measure edit operations
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            table.insert(10, "inserted text ");
            table.delete(10, 14);
        }
        long elapsed = System.nanoTime() - start;
        double avgMs = (elapsed / 2000.0) / 1_000_000.0;
        
        System.out.println("PieceTable edit avg: " + String.format("%.6f", avgMs) + " ms");
        assertTrue(avgMs < 1.0, "PieceTable edit should be <1ms, was " + avgMs + "ms");
    }

    // =========================================================================
    // Memory Efficiency Tests
    // =========================================================================

    @Test
    void testFragmentMetadataMemoryFootprint() {
        // Create 1 million fragments and measure memory
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        FragmentIndex index = new FragmentIndex();
        for (int i = 0; i < 1_000_000; i++) {
            int tagId = index.internString("element" + (i % 100));
            FragmentMetadata frag = new FragmentMetadata(
                i, i * 100L, (i + 1) * 100L, i, i + 1,
                i > 0 ? i - 1 : -1, tagId, 1
            );
            index.addFragment(frag);
        }
        
        runtime.gc();
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long usedMB = (memAfter - memBefore) / (1024 * 1024);
        double bytesPerFragment = (double)(memAfter - memBefore) / 1_000_000;
        
        System.out.println("Memory for 1M fragments: " + usedMB + " MB");
        System.out.println("Bytes per fragment: " + String.format("%.1f", bytesPerFragment));
        
        // With our optimized FragmentMetadata (~40 bytes), 1M fragments should use < 100MB
        assertTrue(usedMB < 150, "1M fragments should use <150MB, used " + usedMB + "MB");
    }

    @Test
    void testStringPoolDeduplication() {
        FragmentIndex index = new FragmentIndex();
        
        // Intern the same 100 tag names 10,000 times each
        for (int i = 0; i < 1_000_000; i++) {
            index.internString("tag" + (i % 100));
        }
        
        // Should only have 100 unique entries
        int uniqueCount = 0;
        for (int i = 0; i < 100; i++) {
            if (index.getString(i) != null) {
                uniqueCount++;
            }
        }
        
        System.out.println("Unique strings in pool: " + uniqueCount);
        assertEquals(100, uniqueCount, "StringPool should deduplicate to 100 unique entries");
    }

    // =========================================================================
    // Large File Simulation Tests
    // =========================================================================

    @Test
    void testStreamingIndexerDoesNotLoadEntireFile() throws IOException {
        // Create a moderately large XML file (100MB) - simulates structure of 100GB
        File xmlFile = tempDir.resolve("large.xml").toFile();
        
        try (RandomAccessFile raf = new RandomAccessFile(xmlFile, "rw")) {
            raf.writeBytes("<?xml version=\"1.0\"?>\n<root>\n");
            
            // Write 10,000 elements (simulating larger file)
            String element = "<item id=\"XXX\">Content here that is moderately long to simulate real data</item>\n";
            for (int i = 0; i < 10_000; i++) {
                raf.writeBytes(element.replace("XXX", String.format("%05d", i)));
            }
            
            raf.writeBytes("</root>");
        }
        
        long fileSize = xmlFile.length();
        System.out.println("Test file size: " + (fileSize / 1024) + " KB");
        
        // Index the file
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        FragmentIndex index = new FragmentIndex();
        StreamingIndexer indexer = new StreamingIndexer(index);
        indexer.indexFile(xmlFile);
        
        runtime.gc();
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long memUsedKB = (memAfter - memBefore) / 1024;
        
        System.out.println("Fragments indexed: " + index.getAllFragments().size());
        System.out.println("Memory used for indexing: " + memUsedKB + " KB");
        
        // Memory usage should be much less than file size (streaming)
        assertTrue(memUsedKB < fileSize, "Index memory should be less than file size");
    }

    @Test
    void testPieceTableHandlesLargeFile() throws IOException {
        // Create a large file
        File file = tempDir.resolve("piecelarge.xml").toFile();
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.writeBytes("<?xml version=\"1.0\"?>\n<root>\n");
            String content = "<item>Some content that repeats</item>\n";
            for (int i = 0; i < 10_000; i++) {
                raf.writeBytes(content);
            }
            raf.writeBytes("</root>");
        }
        
        long fileSize = file.length();
        System.out.println("PieceTable test file size: " + (fileSize / 1024) + " KB");
        
        // Create PieceTable (should NOT load the file into memory)
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        PieceTable table = new PieceTable(file);
        
        // Perform some edits
        table.insert(100, "INSERTED TEXT");
        table.delete(200, 50);
        table.replace(150, 10, "REPLACEMENT");
        
        runtime.gc();
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long memUsedKB = (memAfter - memBefore) / 1024;
        
        System.out.println("PieceTable pieces: " + table.getPieces().size());
        System.out.println("Memory for PieceTable: " + memUsedKB + " KB");
        
        // Should be minimal memory (just piece descriptors + add buffer)
        assertTrue(memUsedKB < 100, "PieceTable should use minimal memory");
    }

    // =========================================================================
    // Simulated 100GB File Test (Disabled by default - for manual testing)
    // =========================================================================

    @Test
    @Disabled("Manual test - requires significant disk space and time")
    void testSimulated100GBFile() throws IOException {
        // This test creates a sparse file to simulate a 100GB XML file
        // without actually using 100GB of disk space
        
        File xmlFile = tempDir.resolve("huge.xml").toFile();
        
        // Create sparse file with markers
        try (RandomAccessFile raf = new RandomAccessFile(xmlFile, "rw")) {
            // Write header
            raf.writeBytes("<?xml version=\"1.0\"?>\n<root>\n");
            
            // Set file length to simulate 100GB
            long targetSize = 100L * 1024 * 1024 * 1024; // 100GB
            raf.setLength(targetSize);
            
            // Write footer at end
            raf.seek(targetSize - 20);
            raf.writeBytes("</root>");
        }
        
        System.out.println("Created sparse 100GB file");
        
        // Verify we can create a PieceTable without loading the file
        PieceTable table = new PieceTable(xmlFile);
        assertEquals(xmlFile.length(), table.getLength());
        
        // Verify we can perform edits
        table.insert(30, "<item>Test</item>");
        assertTrue(table.getLength() > xmlFile.length());
        
        System.out.println("100GB file test passed!");
    }
}
