package com.xml.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.xml.models.FragmentIndex;
import com.xml.models.Patch;

/**
 * Gestionnaire de sauvegarde pour appliquer les patchs au fichier final.
 * Utilise un streaming approach pour ne pas charger l'intégralité du fichier en mémoire.
 */
public class FileSaver {
    
    /**
     * Sauvegarde le fichier avec tous les patchs appliqués.
     * 
     * @param originalFile Fichier XML original
     * @param outputFile Fichier de sortie (peut être le même que l'original)
     * @param index Index des fragments (non utilisé avec la nouvelle logique de patchs, gardé pour compatibilité)
     * @param patchManager Gestionnaire de patchs
     */
    public void saveWithPatches(File originalFile, File outputFile, 
                                FragmentIndex index, PatchManager patchManager) throws IOException {

        long startTime = System.currentTimeMillis();
        
        // Si c'est le même fichier, créer un fichier temporaire
        File tempFile = null;
        File targetFile = outputFile;
        
        if (originalFile.getAbsolutePath().equals(outputFile.getAbsolutePath())) {
            tempFile = new File(outputFile.getParent(), outputFile.getName() + ".tmp");
            targetFile = tempFile;
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(originalFile, "r");
             java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(new FileOutputStream(targetFile))) {
            
            List<Patch> patches = patchManager.getAllPatchesSorted();
            long currentPos = 0;
            
            for (Patch patch : patches) {
                // Écrire le contenu original avant ce patch
                if (patch.getGlobalStartOffset() > currentPos) {
                    copyBytes(raf, bos, currentPos, patch.getGlobalStartOffset());
                }
                
                // Écrire le contenu du patch (convertir String -> bytes UTF-8)
                byte[] patchBytes = patch.getReplacementText().getBytes(StandardCharsets.UTF_8);
                bos.write(patchBytes);
                
                // Avancer la position courante
                currentPos = patch.getGlobalEndOffset();
            }
            
            // Copier le reste du fichier
            if (currentPos < originalFile.length()) {
                copyBytes(raf, bos, currentPos, originalFile.length());
            }
            
            bos.flush();
        }
        
        // Si c'était un fichier temporaire, remplacer l'original
        if (tempFile != null) {
            if (!originalFile.delete()) {
                throw new IOException("Impossible de supprimer le fichier original");
            }
            if (!tempFile.renameTo(outputFile)) {
                throw new IOException("Impossible de renommer le fichier temporaire");
            }
        }
        
        long saveTime = System.currentTimeMillis() - startTime;

        
        // Vider les patchs après sauvegarde réussie
        patchManager.clearAll();
    }
    
    /**
     * Copie des bytes depuis le fichier source vers l'output stream par petits blocs.
     */
    private void copyBytes(RandomAccessFile raf, java.io.BufferedOutputStream bos, long start, long end) throws IOException {
        if (start >= end) return;
        
        raf.seek(start);
        long remaining = end - start;
        byte[] buffer = new byte[8192]; // Buffer de 8KB
        
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = raf.read(buffer, 0, toRead);
            
            if (read == -1) break;
            
            bos.write(buffer, 0, read);
            remaining -= read;
        }
    }
}
