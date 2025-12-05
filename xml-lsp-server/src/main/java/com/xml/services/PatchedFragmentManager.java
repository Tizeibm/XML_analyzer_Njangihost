package com.xml.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.xml.models.FragmentIndex;
import com.xml.models.FragmentMetadata;
import com.xml.models.Patch;
import com.xml.models.PatchType;

/**
 * Extension de FragmentManager qui applique les patchs virtuellement.
 * Supporte deux modes:
 * 1. PatchManager (legacy): liste de patchs appliqués à la demande
 * 2. PieceTable (nouveau): structure de données efficace pour l'édition
 */
public class PatchedFragmentManager extends FragmentManager {
    
    // Legacy mode
    private final PatchManager patchManager;
    
    // New mode (optional)
    private final PieceTable pieceTable;
    
    private final FragmentIndex fragmentIndex;
    
    /**
     * Constructeur legacy utilisant PatchManager.
     */
    public PatchedFragmentManager(File file, PatchManager patchManager, FragmentIndex fragmentIndex) {
        super(file);
        this.patchManager = patchManager;
        this.pieceTable = null;
        this.fragmentIndex = fragmentIndex;
    }
    
    /**
     * Constructeur utilisant PieceTable pour l'édition efficace.
     */
    public PatchedFragmentManager(File file, PieceTable pieceTable, FragmentIndex fragmentIndex) {
        super(file);
        this.patchManager = null;
        this.pieceTable = pieceTable;
        this.fragmentIndex = fragmentIndex;
    }

    /**
     * Récupère le contenu d'un fragment en appliquant les patchs si nécessaire.
     */
    @Override
    public String getFragmentContent(FragmentMetadata fragment) throws IOException {
        if (pieceTable != null) {
            // Use PieceTable directly
            long start = fragment.getStartOffset();
            long length = fragment.getEndOffset() - start;
            return pieceTable.getRange(start, length);
        }
        
        // Legacy: apply patches manually
        String originalContent = super.getFragmentContent(fragment);
        
        List<Patch> patches = patchManager.getPatchesForFragment(fragment.getId());
        
        if (patches == null || patches.isEmpty()) {
            return originalContent;
        }
        
        StringBuilder sb = new StringBuilder(originalContent);
        
        for (int i = patches.size() - 1; i >= 0; i--) {
            Patch patch = patches.get(i);
            
            long localStart = patch.getGlobalStartOffset() - fragment.getStartOffset();
            long localEnd = patch.getGlobalEndOffset() - fragment.getStartOffset();
            
            if (localStart < 0) localStart = 0;
            if (localEnd > sb.length()) localEnd = sb.length();
            if (localStart > localEnd) continue;
            
            sb.replace((int)localStart, (int)localEnd, patch.getReplacementText());
        }
        
        return sb.toString();
    }
    
    /**
     * Met à jour un fragment entier (remplace tout son contenu).
     */
    public void updateFragment(String fragmentId, String newContent) {
        if (fragmentIndex == null) {
            throw new IllegalStateException("FragmentIndex requis pour updateFragment");
        }
        
        FragmentMetadata fragment = fragmentIndex.getFragmentById(fragmentId);
        if (fragment == null) {
            throw new IllegalArgumentException("Fragment introuvable: " + fragmentId);
        }
        
        if (pieceTable != null) {
            // Use PieceTable
            pieceTable.replace(fragment.getStartOffset(), 
                              fragment.getEndOffset() - fragment.getStartOffset(), 
                              newContent);
        } else {
            // Legacy: add patch
            Patch patch = new Patch(
                fragment.getStartOffset(),
                fragment.getEndOffset(),
                newContent,
                PatchType.REPLACE,
                fragmentId
            );
            patchManager.addPatch(patch);
        }
    }
    
    /**
     * Obtient un InputStream pour lire le document complet avec modifications.
     * Utilise le PieceTable si disponible, sinon utilise PatchedInputStream.
     */
    public InputStream getFullInputStream() throws IOException {
        if (pieceTable != null) {
            return pieceTable.getInputStream();
        }
        // Fallback to legacy (would need PatchedInputStream integration)
        throw new UnsupportedOperationException("Legacy mode requires PatchedInputStream");
    }
    
    /**
     * Vérifie si un fragment a des modifications non sauvegardées.
     */
    public boolean hasUnsavedChanges(String fragmentId) {
        if (pieceTable != null) {
            // PieceTable always has "unsaved" state until flushed
            return pieceTable.getLength() > 0;
        }
        List<Patch> patches = patchManager.getPatchesForFragment(fragmentId);
        return patches != null && !patches.isEmpty();
    }
    
    /**
     * Nombre total de patchs non sauvegardés.
     */
    public int getUnsavedPatchCount() {
        if (pieceTable != null) {
            return pieceTable.getPieces().size() - 1; // -1 for original piece
        }
        return patchManager.getPatchCount();
    }
    
    /**
     * Check if using PieceTable mode.
     */
    public boolean isPieceTableMode() {
        return pieceTable != null;
    }
}

