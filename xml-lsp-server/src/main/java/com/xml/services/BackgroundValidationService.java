package com.xml.services;

import java.io.File;
import java.util.concurrent.*;
import java.util.function.Consumer;

import com.xml.handlers.LargeXmlValidator;
import com.xml.models.ValidationResult;

/**
 * Background validation service for heavy XML validation.
 * 
 * Runs LargeXmlValidator in a background thread to avoid blocking LSP responses.
 * Supports debouncing: only the last edit is validated if edits come in rapidly.
 */
public class BackgroundValidationService {
    
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingValidations;
    private final LargeXmlValidator validator;
    
    // Debounce delay in milliseconds
    private static final long DEBOUNCE_MS = 300;
    
    public BackgroundValidationService() {
        this.executor = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r, "xml-validator");
                t.setDaemon(true);
                return t;
            }
        );
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "validation-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.pendingValidations = new ConcurrentHashMap<>();
        this.validator = new LargeXmlValidator();
    }
    
    /**
     * Schedule a validation for a file.
     * If a validation is already pending for this file, it is cancelled (debounce).
     * 
     * @param xmlFile The XML file to validate
     * @param xsdFile The XSD schema (can be null)
     * @param onComplete Callback with validation result (called on background thread)
     */
    public void scheduleValidation(File xmlFile, File xsdFile, Consumer<ValidationResult> onComplete) {
        String key = xmlFile.getAbsolutePath();
        
        // Cancel any pending validation for this file
        ScheduledFuture<?> existing = pendingValidations.remove(key);
        if (existing != null) {
            existing.cancel(false);
        }
        
        // Schedule new validation after debounce delay
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pendingValidations.remove(key);
            executor.submit(() -> {
                try {
                    ValidationResult result = validator.validate(xmlFile, xsdFile);
                    onComplete.accept(result);
                } catch (Exception e) {
                    // Create error result
                    ValidationResult errorResult = new ValidationResult(false, 
                        java.util.List.of(), 0, xmlFile.length());
                    onComplete.accept(errorResult);
                }
            });
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        
        pendingValidations.put(key, future);
    }
    
    /**
     * Schedule a validation with patches.
     */
    public void scheduleValidationWithPatches(File xmlFile, File xsdFile, 
            PatchManager patchManager, Consumer<ValidationResult> onComplete) {
        String key = xmlFile.getAbsolutePath();
        
        ScheduledFuture<?> existing = pendingValidations.remove(key);
        if (existing != null) {
            existing.cancel(false);
        }
        
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pendingValidations.remove(key);
            executor.submit(() -> {
                try {
                    ValidationResult result = validator.validateWithPatches(xmlFile, xsdFile, patchManager);
                    onComplete.accept(result);
                } catch (Exception e) {
                    ValidationResult errorResult = new ValidationResult(false, 
                        java.util.List.of(), 0, xmlFile.length());
                    onComplete.accept(errorResult);
                }
            });
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        
        pendingValidations.put(key, future);
    }
    
    /**
     * Immediately validate (no debounce).
     * Returns a Future for the result.
     */
    public CompletableFuture<ValidationResult> validateNow(File xmlFile, File xsdFile) {
        return CompletableFuture.supplyAsync(() -> validator.validate(xmlFile, xsdFile), executor);
    }
    
    /**
     * Cancel all pending validations.
     */
    public void cancelAll() {
        for (ScheduledFuture<?> future : pendingValidations.values()) {
            future.cancel(true);
        }
        pendingValidations.clear();
    }
    
    /**
     * Shutdown the service.
     */
    public void shutdown() {
        cancelAll();
        executor.shutdown();
        scheduler.shutdown();
    }
    
    /**
     * Check if there are pending validations.
     */
    public boolean hasPendingValidations() {
        return !pendingValidations.isEmpty();
    }
}
