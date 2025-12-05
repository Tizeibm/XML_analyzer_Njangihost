package com.xml.handlers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;

import com.xml.models.Patch;

/**
 * Custom InputStream that reconstructs a logical XML document by applying patches on-the-fly.
 * 
 * This stream seamlessly switches between reading from the original file and patch replacements,
 * enabling XSD validation and processing of the patched document without loading it into memory.
 * 
 * Memory usage: Only a small buffer (8KB default) is kept in memory at any time.
 * Suitable for files of any size, including 500GB+ files.
 */
public class PatchedInputStream extends InputStream {
    
    private final RandomAccessFile originalFile;
    private final List<Patch> patches; // Sorted by globalStartOffset
    private final long fileLength;
    
    // Current read state
    private long currentFilePosition;  // Position in the original file
    private long currentLogicalPosition; // Logical position in the reconstructed stream
    
    // Patch application state
    private int currentPatchIndex;
    private Patch activePatch; // Currently active patch we're reading from
    private int activePatchOffset; // Offset within the active patch's replacement text
    
    // Buffer for efficient reading
    private byte[] patchBuffer; // Cache for current patch replacement text
    
    /**
     * Creates a PatchedInputStream for the given file and patches.
     * 
     * @param file Original XML file
     * @param patches List of patches, must be sorted by globalStartOffset (ascending)
     * @throws IOException if file cannot be opened
     */
    public PatchedInputStream(File file, List<Patch> patches) throws IOException {
        this.originalFile = new RandomAccessFile(file, "r");
        this.patches = patches;
        this.fileLength = file.length();
        
        this.currentFilePosition = 0;
        this.currentLogicalPosition = 0;
        this.currentPatchIndex = 0;
        this.activePatch = null;
        this.activePatchOffset = 0;
        this.patchBuffer = null;
        
        // Validate patches are sorted
        for (int i = 1; i < patches.size(); i++) {
            if (patches.get(i).getGlobalStartOffset() < patches.get(i-1).getGlobalStartOffset()) {
                throw new IllegalArgumentException("Patches must be sorted by globalStartOffset");
            }
        }
    }
    
    @Override
    public int read() throws IOException {
        // If we're reading from an active patch
        if (activePatch != null) {
            // Check if we've finished reading the patch
            if (activePatchOffset >= patchBuffer.length) {
                deactivatePatch();
                // Continue to read from file below
            } else {
                // Read byte from patch buffer
                byte b = patchBuffer[activePatchOffset++];
                currentLogicalPosition++;
                return b & 0xFF; // Convert to unsigned
            }
        }
        
        // Read from original file
        if (currentFilePosition >= fileLength) {
            return -1; // EOF
        }
        
        // Check if we need to skip bytes (if next patch replaces upcoming content)
        if (currentPatchIndex < patches.size()) {
            Patch nextPatch = patches.get(currentPatchIndex);
            
            // If we've reached the start of the next patch, activate it
            if (currentFilePosition >= nextPatch.getGlobalStartOffset()) {
                activatePatch(nextPatch);
                return read(); // Recursive call to read from patch
            }
        }
        
        // Normal file read
        originalFile.seek(currentFilePosition);
        int b = originalFile.read();
        
        if (b != -1) {
            currentFilePosition++;
            currentLogicalPosition++;
        }
        
        return b;
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        
        int totalRead = 0;
        
        while (totalRead < len) {
            // If we're reading from an active patch
            if (activePatch != null) {
                int available = patchBuffer.length - activePatchOffset;
                if (available > 0) {
                    int toRead = Math.min(available, len - totalRead);
                    System.arraycopy(patchBuffer, activePatchOffset, b, off + totalRead, toRead);
                    activePatchOffset += toRead;
                    totalRead += toRead;
                    currentLogicalPosition += toRead;
                }
                
                if (activePatchOffset >= patchBuffer.length) {
                    deactivatePatch();
                }
                
                continue;
            }
            
            // Read from original file
            if (currentFilePosition >= fileLength) {
                // Check if we have pending patches at EOF (e.g. append)
               if (currentPatchIndex < patches.size()) {
                    Patch nextPatch = patches.get(currentPatchIndex);
                    if (currentFilePosition >= nextPatch.getGlobalStartOffset()) {
                         activatePatch(nextPatch);
                         continue;
                    }
                }
                break; // EOF
            }
            
            // Calculate how much we can read before potentially hitting a patch
            long maxRead = len - totalRead;
            
            if (currentPatchIndex < patches.size()) {
                Patch nextPatch = patches.get(currentPatchIndex);
                long distanceToPatch = nextPatch.getGlobalStartOffset() - currentFilePosition;
                
                if (distanceToPatch <= 0) {
                    // We're at the patch start, activate it
                    activatePatch(nextPatch);
                    continue;
                }
                
                maxRead = Math.min(maxRead, distanceToPatch);
            }
            
            // Read from file
            originalFile.seek(currentFilePosition);
            int bytesRead = originalFile.read(b, off + totalRead, (int) maxRead);
            
            if (bytesRead == -1) {
                break; // EOF
            }
            
            currentFilePosition += bytesRead;
            currentLogicalPosition += bytesRead;
            totalRead += bytesRead;
        }
        
        return totalRead > 0 ? totalRead : -1;
    }
    
    /**
     * Activates a patch, preparing to read from its replacement text.
     */
    private void activatePatch(Patch patch) throws IOException {
        this.activePatch = patch;
        this.activePatchOffset = 0;
        
        // Convert patch replacement text to bytes
        this.patchBuffer = patch.getReplacementText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        // Skip the bytes in the original file that are being replaced
        long bytesToSkip = patch.getGlobalEndOffset() - patch.getGlobalStartOffset();
        currentFilePosition += bytesToSkip;
        
        // Move to next patch for future reads
        currentPatchIndex++;
    }
    
    /**
     * Deactivates the current patch, returning to normal file reading.
     */
    private void deactivatePatch() {
        this.activePatch = null;
        this.activePatchOffset = 0;
        this.patchBuffer = null;
    }
    
    @Override
    public void close() throws IOException {
        originalFile.close();
    }
    
    @Override
    public int available() throws IOException {
        // This is an approximation
        if (activePatch != null) {
            return patchBuffer.length - activePatchOffset;
        }
        return 0; // Conservative estimate
    }
    
    /**
     * Gets the current logical position in the reconstructed stream.
     * This is useful for debugging and tracking read progress.
     */
    public long getLogicalPosition() {
        return currentLogicalPosition;
    }
    
    /**
     * Gets the current position in the original file.
     * This is useful for debugging patch application.
     */
    public long getFilePosition() {
        return currentFilePosition;
    }
}
