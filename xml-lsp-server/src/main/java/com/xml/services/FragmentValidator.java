package com.xml.services;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.xml.handlers.TrackedStaxHandler;
import com.xml.handlers.Validators;
import com.xml.models.ErrorCollector;
import com.xml.models.ValidationResult;
import com.xml.models.XMLError;

/**
 * Validateur pour fragments XML isolés.
 * Contrairement à LargeXmlValidator qui travaille sur Files, celui-ci valide des Strings/Streams.
 */
public class FragmentValidator {

    /**
     * Valide un fragment XML contre un schéma XSD.
     * Le fragment peut être incomplet (ex: manque namespaces racine).
     * 
     * @param fragmentContent Contenu XML du fragment
     * @param xsdFile Fichier XSD pour validation
     * @param wrapWithRoot Si true, enveloppe le fragment dans une racine fictive
     * @return ValidationResult avec les erreurs détectées
     */
    public ValidationResult validateFragment(String fragmentContent, File xsdFile, boolean wrapWithRoot) {
        long startTime = System.currentTimeMillis();
        
        ErrorCollector collector = new ErrorCollector();
        
        // Préparer le contenu pour validation
        String contentToValidate = fragmentContent;
        if (wrapWithRoot) {
            contentToValidate = wrapFragmentWithRoot(fragmentContent);
        }
        
        // Parsing structurel avec TrackedStaxHandler
        TrackedStaxHandler handler = new TrackedStaxHandler(collector);
        try (InputStream in = new ByteArrayInputStream(contentToValidate.getBytes(StandardCharsets.UTF_8))) {
            handler.parse(in);
        } catch (Exception e) {
            collector.addError("Erreur de parsing du fragment : " + e.getMessage(), 0, "FRAGMENT_PARSE_ERROR");
        }
        
        // Validation XSD si aucune erreur structurelle critique
        boolean xsdValid = true;
        if (collector.getErrors().isEmpty() || !hasCriticalErrors(collector.getErrors())) {
            if (xsdFile != null && xsdFile.exists()) {
                // Pour la validation XSD, on doit créer un fichier temporaire
                // ou utiliser un StreamSource. Pour simplifier, on utilise StreamSource.
                xsdValid = validateAgainstXsd(contentToValidate, xsdFile, collector);
            }
        }
        
        List<XMLError> errors = convertToLightweightErrors(collector.getErrors());
        long time = System.currentTimeMillis() - startTime;
        
        return new ValidationResult(xsdValid, errors, time, fragmentContent.length());
    }

    /**
     * Enveloppe un fragment dans une racine fictive avec namespaces courants.
     */
    private String wrapFragmentWithRoot(String fragment) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<root xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");
        sb.append(fragment);
        sb.append("\n</root>");
        return sb.toString();
    }

    /**
     * Valide le contenu contre un schéma XSD.
     */
    private boolean validateAgainstXsd(String content, File xsdFile, ErrorCollector collector) {
        try {
            // Créer un fichier temporaire pour la validation XSD
            // Alternative: utiliser StreamSource directement
            java.io.File tempFile = java.io.File.createTempFile("fragment_", ".xml");
            tempFile.deleteOnExit();
            
            java.nio.file.Files.write(tempFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            
            Validators validator = new Validators(collector);
            boolean valid = validator.validate(tempFile, xsdFile);
            
            tempFile.delete();
            return valid;
        } catch (Exception e) {
            collector.addError("Erreur de validation XSD : " + e.getMessage(), 0, "XSD_ERROR");
            return false;
        }
    }

    /**
     * Vérifie si les erreurs contiennent des erreurs critiques bloquantes.
     */
    private boolean hasCriticalErrors(List<XMLError> errors) {
        for (XMLError error : errors) {
            if (error.getType().contains("FATAL") || error.getType().contains("MALFORMED")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Version allégée des erreurs sans zones de code.
     */
    private List<XMLError> convertToLightweightErrors(List<XMLError> original) {
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
