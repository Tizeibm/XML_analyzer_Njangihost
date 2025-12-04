package com.xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.xml.handlers.TrackedStaxHandler;
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
        
        try (InputStream in = new FileInputStream(xmlFile)) {
            new TrackedStaxHandler(errorCollector).parse(in);
            
        } catch (IOException e) {
            errorCollector.addError("Fichier illisible : " + e.getMessage(), 0, "FATAL_PARSE");

        }
    }
}