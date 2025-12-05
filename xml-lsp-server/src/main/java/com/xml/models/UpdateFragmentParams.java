package com.xml.models;

/**
 * Paramètres pour mettre à jour un fragment.
 */
public class UpdateFragmentParams {
    private String fragmentId;
    private String newContent;
    
    public UpdateFragmentParams() {}
    
    public UpdateFragmentParams(String fragmentId, String newContent) {
        this.fragmentId = fragmentId;
        this.newContent = newContent;
    }
    
    public String getFragmentId() {
        return fragmentId;
    }
    
    public void setFragmentId(String fragmentId) {
        this.fragmentId = fragmentId;
    }
    
    public String getNewContent() {
        return newContent;
    }
    
    public void setNewContent(String newContent) {
        this.newContent = newContent;
    }
}
