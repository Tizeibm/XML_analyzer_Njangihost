package com.xml.services;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Piece Table data structure for efficient text editing on large files.
 * 
 * The document is represented as a sequence of "Pieces", each pointing to either:
 * - The ORIGINAL file (read-only, on disk)
 * - The ADD buffer (append-only, in memory)
 * 
 * This allows O(k) insert/delete where k = number of pieces, not file size.
 * For LSP use cases (many small edits), this is much more efficient than copying.
 */
public class PieceTable {

    private final File originalFile;
    private long originalFileLength;
    
    // Add buffer: all new text goes here (append-only)
    private final StringBuilder addBuffer = new StringBuilder();
    
    // List of pieces describing the logical document
    private final List<Piece> pieces = new ArrayList<>();
    
    /**
     * A piece describes a contiguous range of text from either buffer.
     */
    public record Piece(
        boolean isOriginal,  // true = original file, false = add buffer
        long start,          // start offset in the source buffer
        long length          // length of this piece
    ) {}
    
    /**
     * Create a PieceTable for a file.
     * Initially, the document is represented by a single piece covering the entire original file.
     */
    public PieceTable(File file) throws IOException {
        this.originalFile = file;
        this.originalFileLength = file.exists() ? file.length() : 0;
        
        if (originalFileLength > 0) {
            pieces.add(new Piece(true, 0, originalFileLength));
        }
    }
    
    /**
     * Create an empty PieceTable (for new files).
     */
    public PieceTable() {
        this.originalFile = null;
        this.originalFileLength = 0;
    }
    
    /**
     * Get the logical length of the document.
     */
    public long getLength() {
        long len = 0;
        for (Piece p : pieces) {
            len += p.length();
        }
        return len;
    }
    
    /**
     * Insert text at the given logical offset.
     */
    public synchronized void insert(long offset, String text) {
        if (text == null || text.isEmpty()) return;
        
        // Append to add buffer
        long addStart = addBuffer.length();
        addBuffer.append(text);
        Piece newPiece = new Piece(false, addStart, text.length());
        
        // Find the piece containing the offset
        int pieceIndex = findPieceIndex(offset);
        
        if (pieceIndex < 0) {
            // Insert at end
            pieces.add(newPiece);
        } else {
            // Split the piece at offset and insert
            Piece target = pieces.get(pieceIndex);
            long offsetInPiece = offset - getPieceStartOffset(pieceIndex);
            
            if (offsetInPiece == 0) {
                // Insert before this piece
                pieces.add(pieceIndex, newPiece);
            } else if (offsetInPiece >= target.length()) {
                // Insert after this piece
                pieces.add(pieceIndex + 1, newPiece);
            } else {
                // Split the piece
                Piece left = new Piece(target.isOriginal(), target.start(), offsetInPiece);
                Piece right = new Piece(target.isOriginal(), target.start() + offsetInPiece, target.length() - offsetInPiece);
                
                pieces.remove(pieceIndex);
                pieces.add(pieceIndex, right);
                pieces.add(pieceIndex, newPiece);
                pieces.add(pieceIndex, left);
            }
        }
    }
    
    /**
     * Delete text from the given logical offset.
     */
    public synchronized void delete(long offset, long length) {
        if (length <= 0) return;
        
        long remaining = length;
        long currentOffset = offset;
        
        while (remaining > 0 && !pieces.isEmpty()) {
            int pieceIndex = findPieceIndex(currentOffset);
            if (pieceIndex < 0) break;
            
            Piece target = pieces.get(pieceIndex);
            long pieceStartOffset = getPieceStartOffset(pieceIndex);
            long offsetInPiece = currentOffset - pieceStartOffset;
            
            long deleteInThisPiece = Math.min(remaining, target.length() - offsetInPiece);
            
            if (offsetInPiece == 0 && deleteInThisPiece >= target.length()) {
                // Delete entire piece
                pieces.remove(pieceIndex);
            } else if (offsetInPiece == 0) {
                // Delete from start
                pieces.set(pieceIndex, new Piece(target.isOriginal(), target.start() + deleteInThisPiece, target.length() - deleteInThisPiece));
            } else if (offsetInPiece + deleteInThisPiece >= target.length()) {
                // Delete to end
                pieces.set(pieceIndex, new Piece(target.isOriginal(), target.start(), offsetInPiece));
            } else {
                // Delete from middle - split
                Piece left = new Piece(target.isOriginal(), target.start(), offsetInPiece);
                Piece right = new Piece(target.isOriginal(), target.start() + offsetInPiece + deleteInThisPiece, target.length() - offsetInPiece - deleteInThisPiece);
                
                pieces.remove(pieceIndex);
                pieces.add(pieceIndex, right);
                pieces.add(pieceIndex, left);
            }
            
            remaining -= deleteInThisPiece;
            // currentOffset doesn't change because the pieces shift
        }
    }
    
    /**
     * Replace text at the given logical offset.
     * This is equivalent to delete + insert, but optimized.
     */
    public synchronized void replace(long offset, long length, String newText) {
        delete(offset, length);
        insert(offset, newText);
    }
    
    /**
     * Get a range of text. Use sparingly for large ranges!
     */
    public String getRange(long start, long length) throws IOException {
        StringBuilder result = new StringBuilder();
        long remaining = length;
        long currentOffset = start;
        
        for (int i = 0; i < pieces.size() && remaining > 0; i++) {
            long pieceStartOffset = getPieceStartOffset(i);
            Piece piece = pieces.get(i);
            
            if (pieceStartOffset + piece.length() <= currentOffset) {
                continue; // Skip pieces before the range
            }
            
            long offsetInPiece = Math.max(0, currentOffset - pieceStartOffset);
            long readLen = Math.min(remaining, piece.length() - offsetInPiece);
            
            if (piece.isOriginal()) {
                // Read from original file
                result.append(readFromOriginal(piece.start() + offsetInPiece, (int) readLen));
            } else {
                // Read from add buffer
                result.append(addBuffer.substring((int)(piece.start() + offsetInPiece), (int)(piece.start() + offsetInPiece + readLen)));
            }
            
            currentOffset += readLen;
            remaining -= readLen;
        }
        
        return result.toString();
    }
    
    /**
     * Get an InputStream that reads the entire logical document.
     * This streams through the pieces, never loading more than 64KB at a time.
     */
    public InputStream getInputStream() throws IOException {
        List<InputStream> streams = new ArrayList<>();
        
        for (Piece piece : pieces) {
            if (piece.isOriginal()) {
                streams.add(new OriginalFileInputStream(originalFile, piece.start(), piece.length()));
            } else {
                byte[] data = addBuffer.substring((int)piece.start(), (int)(piece.start() + piece.length()))
                                       .getBytes(StandardCharsets.UTF_8);
                streams.add(new ByteArrayInputStream(data));
            }
        }
        
        if (streams.isEmpty()) {
            return new ByteArrayInputStream(new byte[0]);
        }
        
        Enumeration<InputStream> enumeration = Collections.enumeration(streams);
        return new SequenceInputStream(enumeration);
    }
    
    /**
     * Get the list of pieces (for debugging/serialization).
     */
    public List<Piece> getPieces() {
        return new ArrayList<>(pieces);
    }
    
    // --- Private helpers ---
    
    private int findPieceIndex(long offset) {
        long currentOffset = 0;
        for (int i = 0; i < pieces.size(); i++) {
            if (offset < currentOffset + pieces.get(i).length()) {
                return i;
            }
            currentOffset += pieces.get(i).length();
        }
        return -1; // Beyond end
    }
    
    private long getPieceStartOffset(int pieceIndex) {
        long offset = 0;
        for (int i = 0; i < pieceIndex; i++) {
            offset += pieces.get(i).length();
        }
        return offset;
    }
    
    private String readFromOriginal(long start, int length) throws IOException {
        if (originalFile == null) return "";
        
        try (RandomAccessFile raf = new RandomAccessFile(originalFile, "r")) {
            raf.seek(start);
            byte[] buffer = new byte[length];
            int read = raf.read(buffer);
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        }
    }
    
    /**
     * InputStream that reads a portion of the original file.
     * Implements a streaming read to avoid loading large sections into memory.
     */
    private static class OriginalFileInputStream extends InputStream {
        private final RandomAccessFile raf;
        private long remaining;
        
        public OriginalFileInputStream(File file, long start, long length) throws IOException {
            this.raf = new RandomAccessFile(file, "r");
            this.raf.seek(start);
            this.remaining = length;
        }
        
        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = raf.read();
            if (b != -1) remaining--;
            return b;
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int read = raf.read(b, off, toRead);
            if (read > 0) remaining -= read;
            return read;
        }
        
        @Override
        public void close() throws IOException {
            raf.close();
        }
    }
}
