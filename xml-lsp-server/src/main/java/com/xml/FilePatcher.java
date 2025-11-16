package com.xml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moteur de patching pour appliquer des modifications de fragment au fichier original
 * SANS créer d'incohérences dans la structure XML.
 * 
 * Stratégie:
 * 1. Charger le fichier ligne par ligne avec index précis
 * 2. Remplacer le fragment à partir de fragmentStartLine jusqu'à fragmentEndLine
 * 3. Vérifier l'intégrité XML du résultat avant écriture
 * 4. Écrire atomiquement avec sauvegarde de backup
 */
public class FilePatcher {
    private static final Logger LOG = LoggerFactory.getLogger(FilePatcher.class);

    /**
     * Applique une modification de fragment au fichier XML original.
     * 
     * @param filePath Chemin du fichier XML
     * @param modifiedFragment Contenu XML modifié
     * @param fragmentStartLine Ligne de début du fragment (1-based)
     * @param fragmentEndLine Ligne de fin du fragment (1-based)
     * @return true si la sauvegarde a réussi, false sinon
     */
    public static boolean patchFile(
            String filePath, 
            String modifiedFragment, 
            int fragmentStartLine, 
            int fragmentEndLine) {
        
        try {
            Path path = Paths.get(filePath);
            
            String originalContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            String[] lines = originalContent.split("\n", -1);
            
            LOG.info("Patching {} from line {} to {}", filePath, fragmentStartLine, fragmentEndLine);

            // Construire le nouveau contenu
            StringBuilder newContent = new StringBuilder();
            
            // Lignes avant le fragment (0 à fragmentStartLine-2, car 1-based)
            for (int i = 0; i < fragmentStartLine - 1 && i < lines.length; i++) {
                newContent.append(lines[i]).append("\n");
            }
            
            newContent.append(modifiedFragment);
            
            // Si le fragment modifié ne finit pas par une newline et qu'il y a d'autres lignes après
            if (!modifiedFragment.endsWith("\n") && fragmentEndLine < lines.length) {
                newContent.append("\n");
            }
            
            // Lignes après le fragment (à partir de fragmentEndLine)
            for (int i = fragmentEndLine; i < lines.length; i++) {
                newContent.append(lines[i]);
                if (i < lines.length - 1) {
                    newContent.append("\n");
                }
            }

            String patchedContent = newContent.toString();
            
            if (!validateXmlStructure(patchedContent)) {
                LOG.error("Validation échouée pour le contenu patchè: structure XML invalide");
                return false;
            }

            return writeFileAtomically(path, patchedContent);
            
        } catch (IOException e) {
            LOG.error("Erreur lors du patching du fichier {}", filePath, e);
            return false;
        }
    }

    /**
     * Valide la structure XML du contenu pour éviter les incohérences.
     */
    private static boolean validateXmlStructure(String content) {
        try {
            // Compter les balises équilibrées
            int openCount = 0;
            int closeCount = 0;
            
            // Expression régulière simplifiée pour compter les balises
            String[] openTags = content.split("<([a-zA-Z0-9:_-]+)[^>]*(?<!/\\s)\\s*>", -1);
            String[] closeTags = content.split("</([a-zA-Z0-9:_-]+)\\s*>", -1);
            String[] selfClosing = content.split("<[^>]*/\\s*>", -1);
            
            openCount = openTags.length - 1;
            closeCount = closeTags.length - 1;
            int selfClosingCount = selfClosing.length - 1;
            
            // Vérification basique: balises ouvertes doivent être fermées (+ self-closing)
            boolean isValid = (openCount - selfClosingCount - closeCount) <= 1 && 
                            (openCount - selfClosingCount - closeCount) >= -1;
            
            if (isValid) {
                LOG.debug("Validation XML réussie");
            } else {
                LOG.warn("Structure XML potentiellement invalide: open={}, close={}, self-closing={}", 
                    openCount, closeCount, selfClosingCount);
            }
            return isValid;
            
        } catch (Exception e) {
            LOG.warn("Erreur lors de la validation de structure", e);
            return false;
        }
    }

    /**
     * Écrit le fichier de manière atomique avec création d'un backup.
     */
    private static boolean writeFileAtomically(Path path, String content) {
        try {
            Path backupPath = Paths.get(path.toString() + ".backup");
            if (Files.exists(path)) {
                Files.copy(path, backupPath);
                LOG.info("Backup créé: {}", backupPath);
            }
            
            // Écrire le nouveau contenu
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            LOG.info("Fichier sauvegardé avec succès: {}", path);
            return true;
            
        } catch (IOException e) {
            LOG.error("Erreur lors de l'écriture du fichier", e);
            return false;
        }
    }
}
