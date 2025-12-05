package com.xml.handlers;

import java.io.File;
import java.io.InputStream;

import com.xml.models.ErrorCollector;

/**
 * Analyse SAX en streaming (adapté aux très gros fichiers).
 * Les erreurs sont collectées sans interrompre le parsing (tolérance).
 * Aucun fichier n'est chargé entièrement en mémoire.
 */
public class XMLParser {

    private final ErrorCollector errorCollector;

    public XMLParser(ErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }

    /**
     * Parsing STREAMING d'un fichier XML (peut peser des Go).
     * Le fichier n'est JAMAIS chargé entièrement en mémoire.
     */
    public void parse(File xmlFile) {
        
        try {
            new TrackedStaxHandler(errorCollector).parse(xmlFile);
            
        } catch (Exception e) {
            errorCollector.addError("Fichier illisible : " + e.getMessage(), 0, "FATAL_PARSE");

        }
    }

    /**
     * Parsing STREAMING depuis un InputStream (utile pour PatchedInputStream).
     * Le stream n'est pas chargé entièrement en mémoire.
     */
    public void parse(InputStream inputStream) {
        
        try {
            new TrackedStaxHandler(errorCollector).parse(inputStream);
            
        } catch (Exception e) {
            errorCollector.addError("Stream illisible : " + e.getMessage(), 0, "FATAL_PARSE");

        }
    }
}