package com.xml.models;



import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Accumule les erreurs détectées pendant le parsing SAX et la validation XSD.
 * Thread-safe pour support multi-threading.
 * 
 * AMÉLIORATION: Déduplication automatique des erreurs identiques.
 * CRITICAL FIX: Limite le nombre d'erreurs pour éviter OutOfMemoryError sur fichiers massifs.
 */
public class ErrorCollector {

    // Default maximum errors to prevent OOM on large files with many errors
    private static final int DEFAULT_MAX_ERRORS = 1000;
    
    private final List<XMLError> errors = Collections.synchronizedList(new ArrayList<>());
    // Set pour déduplication rapide (ligne + message)
    private final Set<String> errorSignatures = Collections.synchronizedSet(new HashSet<>());
    
    private final int maxErrors;
    private volatile boolean limitReached = false;

    public ErrorCollector() {
        this(DEFAULT_MAX_ERRORS);
    }
    
    /**
     * Constructor with configurable error limit.
     * 
     * @param maxErrors Maximum number of errors to collect. Use Integer.MAX_VALUE for unlimited (not recommended).
     */
    public ErrorCollector(int maxErrors) {
        this.maxErrors = maxErrors;
    }

    public void addError(String message, int lineNumber, String type) {
        // Check if we've reached the error limit
        if (errors.size() >= maxErrors) {
            if (!limitReached) {
                limitReached = true;
                // Add a special error indicating limit was reached
                XMLError limitError = new XMLError(
                    "Error limit reached (" + maxErrors + " errors). Additional errors not reported to prevent memory issues.",
                    lineNumber,
                    "LIMIT_REACHED"
                );
                errors.add(limitError);
            }
            return; // Stop adding more errors
        }
        
        // Créer une signature unique pour cette erreur
        String signature = lineNumber + ":" + message;
        
        // Vérifier si cette erreur existe déjà
        if (errorSignatures.contains(signature)) {
            // Erreur en doublon, ignorer
            return;
        }
        
        // Ajouter l'erreur
        XMLError err = new XMLError(message, lineNumber, type);
        errors.add(err);
        errorSignatures.add(signature);

    }

    public List<XMLError> getErrors() {
        return List.copyOf(errors); // snapshot immuable
    }

    public void clear() {
        errors.clear();
        errorSignatures.clear();
        limitReached = false;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public int getErrorCount() {
        return errors.size();
    }
    
    /**
     * @return true if the error limit was reached during collection
     */
    public boolean isLimitReached() {
        return limitReached;
    }
    
    /**
     * @return the maximum number of errors this collector will store
     */
    public int getMaxErrors() {
        return maxErrors;
    }
}