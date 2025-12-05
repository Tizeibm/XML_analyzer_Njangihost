package com.xml;

import com.xml.models.ErrorCollector;
import com.xml.models.XMLError;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test ErrorCollector's error limiting capability to prevent OutOfMemoryError.
 */
public class ErrorCollectorTest {

    @Test
    void testErrorLimitPreventsOOM() {
        // Create collector with low limit for testing
        ErrorCollector collector = new ErrorCollector(100);
        
        // Try to add 1000 errors
        for (int i = 0; i < 1000; i++) {
            collector.addError("Error " + i, i, "TEST");
        }
        
        // Should only have 101 errors (100 + 1 limit reached message)
        assertTrue(collector.getErrorCount() <= 101, 
                  "Should cap at max errors + 1: " + collector.getErrorCount());
        assertTrue(collector.isLimitReached(), "Limit should be marked as reached");
        
        System.out.println("Error collector properly limited to " + collector.getErrorCount() + " errors");
    }

    @Test
    void testDefaultLimit() {
        ErrorCollector collector = new ErrorCollector();
        assertEquals(1000, collector.getMaxErrors(), "Default max should be 1000");
    }

    @Test
    void testDeduplication() {
        ErrorCollector collector = new ErrorCollector(100);
        
        // Add same error multiple times
        collector.addError("Duplicate error", 10, "TEST");
        collector.addError("Duplicate error", 10, "TEST");
        collector.addError("Duplicate error", 10, "TEST");
        
        // Should only have 1 error
        assertEquals(1, collector.getErrorCount(), "Should deduplicate identical errors");
    }

    @Test
    void testClear() {
        ErrorCollector collector = new ErrorCollector(100);
        
        for (int i = 0; i < 150; i++) {
            collector.addError("Error " + i, i, "TEST");
        }
        
        assertTrue(collector.isLimitReached());
        
        collector.clear();
        
        assertEquals(0, collector.getErrorCount());
        assertFalse(collector.isLimitReached(), "Limit flag should be reset after clear");
    }
}
