package com.xml.models;

/**
 * Métadonnées d'un fragment XML (Version Compacte / Lightweight).
 * 
 * Optimisé pour stocker des millions de fragments en mémoire (100GB+ files).
 * Utilise uniquement des types primitifs. Les chaînes de caractères sont externalisées
 * dans un StringPool (géré par FragmentIndex).
 */
public class FragmentMetadata {
    // Coordonnées physiques (16 + 8 = 24 bytes)
    private final int id;          // Index unique (0, 1, 2...)
    private final long startOffset;
    private final long endOffset;
    private final int startLine;
    private final int endLine;

    // Structure & Hiérarchie (12 bytes)
    private final int parentIndex; // Index du parent dans la liste globale (-1 si racine)
    private final int tagId;       // ID du nom de la balise dans le StringPool
    private final int depth;       // Profondeur d'imbrication
    
    // Flags (1 byte)
    private final byte flags;
    public static final byte FLAG_CONTINUATION = 1;

    // État (4 bytes ref)
    private FragmentStatus status = FragmentStatus.UNKNOWN;

    public enum FragmentStatus {
        UNKNOWN,
        VALID,
        INVALID,
        MALFORMED
    }

    public FragmentMetadata(int id, long startOffset, long endOffset, int startLine, int endLine, 
                          int parentIndex, int tagId, int depth, byte flags) {
        this.id = id;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.startLine = startLine;
        this.endLine = endLine;
        this.parentIndex = parentIndex;
        this.tagId = tagId;
        this.depth = depth;
        this.flags = flags;
    }
    
    public FragmentMetadata(int id, long startOffset, long endOffset, int startLine, int endLine, 
                          int parentIndex, int tagId, int depth) {
        this(id, startOffset, endOffset, startLine, endLine, parentIndex, tagId, depth, (byte)0);
    }

    public String getId() { return "frag_" + id; }
    public int getIndex() { return id; }
    public boolean isContinuation() { return (flags & FLAG_CONTINUATION) != 0; }
    
    public long getStartOffset() { return startOffset; }
    public long getEndOffset() { return endOffset; }
    public int getStartLine() { return startLine; }
    public int getEndLine() { return endLine; }
    
    public int getParentIndex() { return parentIndex; }
    public int getTagId() { return tagId; }
    public int getDepth() { return depth; }

    public FragmentStatus getStatus() { return status; }
    public void setStatus(FragmentStatus status) { this.status = status; }

    public long getLength() {
        return endOffset - startOffset;
    }
}
