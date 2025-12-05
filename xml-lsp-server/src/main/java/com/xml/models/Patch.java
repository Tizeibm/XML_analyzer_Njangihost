package com.xml.models;

import java.util.Objects;

/**
 * Représente une modification atomique sur le fichier XML.
 * Les offsets sont globaux (par rapport au fichier original).
 */
public class Patch implements Comparable<Patch> {
    
    private final long globalStartOffset;
    private final long globalEndOffset;
    private final String replacementText;
    private final PatchType type;
    private final String fragmentId;

    public Patch(long globalStartOffset, long globalEndOffset, String replacementText, PatchType type, String fragmentId) {
        if (globalStartOffset < 0 || globalEndOffset < globalStartOffset) {
            throw new IllegalArgumentException("Offsets invalides: [" + globalStartOffset + ", " + globalEndOffset + ")");
        }
        this.globalStartOffset = globalStartOffset;
        this.globalEndOffset = globalEndOffset;
        this.replacementText = replacementText != null ? replacementText : "";
        this.type = type;
        this.fragmentId = fragmentId;
    }

    public long getGlobalStartOffset() {
        return globalStartOffset;
    }

    public long getGlobalEndOffset() {
        return globalEndOffset;
    }

    public String getReplacementText() {
        return replacementText;
    }

    public PatchType getType() {
        return type;
    }

    public String getFragmentId() {
        return fragmentId;
    }

    /**
     * Trie les patchs par offset de début croissant.
     */
    @Override
    public int compareTo(Patch other) {
        return Long.compare(this.globalStartOffset, other.globalStartOffset);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Patch patch = (Patch) o;
        return globalStartOffset == patch.globalStartOffset &&
                globalEndOffset == patch.globalEndOffset &&
                Objects.equals(replacementText, patch.replacementText) &&
                type == patch.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(globalStartOffset, globalEndOffset, replacementText, type);
    }

    @Override
    public String toString() {
        return "Patch{" +
                "[" + globalStartOffset + ", " + globalEndOffset + ")" +
                ", type=" + type +
                ", text='" + (replacementText.length() > 20 ? replacementText.substring(0, 20) + "..." : replacementText) + '\'' +
                ", frag='" + fragmentId + '\'' +
                '}';
    }
}
