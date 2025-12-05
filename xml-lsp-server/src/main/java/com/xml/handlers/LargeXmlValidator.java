package com.xml.handlers;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.xml.models.ErrorCollector;
import com.xml.models.ValidationResult;
import com.xml.models.XMLError;
import com.xml.services.PatchManager;

/**
 * Service de validation optimisé pour les très gros fichiers XML.
 * Version améliorée : thread safe, propre, sans état global.
 */
public class LargeXmlValidator {

    /**
     * Effectue une validation XML + XSD sans extraction de zones.
     * Parfait pour un premier passage rapide sur très grands fichiers.
     */
    public ValidationResult validate(File xmlFile, File xsdFile) {

        long startTime = System.currentTimeMillis();

        ErrorCollector collector = new ErrorCollector();

        // STEP 1: Always parse for structural validation (detects unclosed tags, etc.)
        XMLParser parser = new XMLParser(collector);
        parser.parse(xmlFile);

        // STEP 2: Validate with XSD only if no parsing errors (strict mode)
        boolean xsdValid = true;
        if (collector.getErrors().isEmpty()) {
            if (xsdFile != null && xsdFile.exists()) {
                Validators validator = new Validators(collector);
                xsdValid = validator.validate(xmlFile, xsdFile);
            }
        }

        // Version "lightweight" des erreurs
        List<XMLError> errors = convertToErrorsWithoutZones(collector.getErrors());

        long time = System.currentTimeMillis() - startTime;

        return new ValidationResult(xsdValid && errors.isEmpty(), errors, time, xmlFile.length());
    }

    /**
     * Valide un fichier XML avec XSD en appliquant les patches virtuellement.
     * Utilise PatchedInputStream pour reconstruire le document logique à la volée.
     * 
     * @param xmlFile Fichier XML original
     * @param xsdFile Fichier XSD pour validation
     * @param patchManager Gestionnaire de patches contenant les modifications virtuelles
     * @return Résultat de validation avec erreurs détectées
     */
    public ValidationResult validateWithPatches(File xmlFile, File xsdFile, PatchManager patchManager) {
        
        long startTime = System.currentTimeMillis();
        ErrorCollector collector = new ErrorCollector();
        
        // Récupérer les patches triés
        List<com.xml.models.Patch> patches = patchManager.getAllPatchesSorted();
        
        try {
            // STEP 1: Always parse for structural validation
            try (InputStream patchedStream = new PatchedInputStream(xmlFile, patches)) {
                XMLParser parser = new XMLParser(collector);
                parser.parse(patchedStream);
            }
            
            // STEP 2: Validate with XSD only if no parsing errors (strict mode)
            boolean xsdValid = true;
            if (xsdFile != null && xsdFile.exists()) {
                // Check for errors from parsing (Strict Mode)
                // Ignore WARNINGs
                boolean hasBlockingErrors = collector.getErrors().stream()
                    .anyMatch(e -> !e.getType().equals("WARNING"));
                
                if (!hasBlockingErrors) {
                    // XML is well-formed, safe to validate against schema
                    try (InputStream patchedStream = new PatchedInputStream(xmlFile, patches)) {
                        Validators validator = new Validators(collector);
                        xsdValid = validator.validateStream(patchedStream, xsdFile);
                    }
                } else {
                    // Skip XSD validation if XML has structural errors
                    xsdValid = false;
                }
            }
            
            // Conversion vers erreurs légères
            List<XMLError> errors = convertToErrorsWithoutZones(collector.getErrors());
            
            long time = System.currentTimeMillis() - startTime;
            
            return new ValidationResult(xsdValid && errors.isEmpty(), errors, time, xmlFile.length());
            
        } catch (Exception e) {
            collector.addError("Erreur lors de la validation avec patches: " + e.getMessage(), 0, "IO_ERROR");
            List<XMLError> errors = convertToErrorsWithoutZones(collector.getErrors());
            long time = System.currentTimeMillis() - startTime;
            return new ValidationResult(false, errors, time, xmlFile.length());
        }
    }

    /**
     * Génère une version légère des erreurs, sans zone, pour minimiser
     * l'utilisation mémoire lors de validations massives.
     */
    private List<XMLError> convertToErrorsWithoutZones(List<XMLError> original) {

        List<XMLError> result = new ArrayList<>(original.size());

        for (XMLError e : original) {

            XMLError lite = new XMLError(e.getMessage(), e.getLineNumber(), e.getType());
            lite.setColumn(e.getColumn());
            lite.setTagName(e.getTagName());

            lite.setPrecisePosition(
                    e.getPreciseStartLine(), e.getPreciseStartColumn(),
                    e.getPreciseEndLine(), e.getPreciseEndColumn()
            );

            // Copy fragment fields to preserve fragment information
            if (e.getFragment() != null) {
                lite.setFragment(e.getFragment());
                lite.setFragmentStartLine(e.getFragmentStartLine());
                lite.setFragmentEndLine(e.getFragmentEndLine());
            }

            result.add(lite);
        }

        return result;
    }

}
