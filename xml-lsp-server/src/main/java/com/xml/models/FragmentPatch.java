package com.xml.models;

/**
 * Représente un patch appliqué à un fragment XML.
 * Le patch est stocké en mémoire jusqu'à la sauvegarde finale.
 */
public class FragmentPatch {
    private final String fragmentId;
    private final String newContent;
    private final long timestamp;
    
    public FragmentPatch(String fragmentId, String newContent) {
        this.fragmentId = fragmentId;
        this.newContent = newContent;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getFragmentId() {
        return fragmentId;
    }
    
    public String getNewContent() {
        return newContent;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "Patch[" + fragmentId + ", " + newContent.length() + " bytes, " + timestamp + "]";
    }
}
