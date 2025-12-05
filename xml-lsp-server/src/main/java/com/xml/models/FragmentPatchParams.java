package com.xml.models;

/**
 * Paramètres pour la commande xml/applyFragmentPatch.
 * Correspond à une modification granulaire (patch) envoyée par le client.
 */
public class FragmentPatchParams {
    private String fragmentId;
    private long globalStartOffset;
    private long globalEndOffset;
    private String replacementText;

    public FragmentPatchParams() {}

    public FragmentPatchParams(String fragmentId, long globalStartOffset, long globalEndOffset, String replacementText) {
        this.fragmentId = fragmentId;
        this.globalStartOffset = globalStartOffset;
        this.globalEndOffset = globalEndOffset;
        this.replacementText = replacementText;
    }

    public String getFragmentId() {
        return fragmentId;
    }

    public void setFragmentId(String fragmentId) {
        this.fragmentId = fragmentId;
    }

    public long getGlobalStartOffset() {
        return globalStartOffset;
    }

    public void setGlobalStartOffset(long globalStartOffset) {
        this.globalStartOffset = globalStartOffset;
    }

    public long getGlobalEndOffset() {
        return globalEndOffset;
    }

    public void setGlobalEndOffset(long globalEndOffset) {
        this.globalEndOffset = globalEndOffset;
    }

    public String getReplacementText() {
        return replacementText;
    }

    public void setReplacementText(String replacementText) {
        this.replacementText = replacementText;
    }
}
