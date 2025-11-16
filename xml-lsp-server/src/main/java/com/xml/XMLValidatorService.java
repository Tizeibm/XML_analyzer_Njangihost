package com.xml;

import org.eclipse.lsp4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Service principal de validation XML.
 * Orchestre le parsing SAX, la validation XSD et la conversion en Diagnostic LSP.
 * Fichiers traités en streaming : pas de chargement complet en mémoire.
 */
public class XMLValidatorService {

    private static final Logger LOG = LoggerFactory.getLogger(XMLValidatorService.class);

    /**
     * Lance l'analyse complète du fichier XML.
     * 1) Parsing SAX (structure et syntaxe)
     * 2) Validation XSD (si schéma fourni)
     * 3) Conversion en Diagnostic LSP
     */
    public List<Diagnostic> analyze(File xmlFile, File xsdFile) {
        LOG.info("Analyse XML : {}", xmlFile.getAbsolutePath());
        
        ErrorCollector collector = new ErrorCollector();
        
        if (xsdFile != null && xsdFile.exists()) {
            new Validator(collector).validate(xmlFile, xsdFile);
        }

        // Parsing SAX tolérant (détection structure/syntaxe)
        XMLParser parser = new XMLParser(collector);
        parser.parse(xmlFile);

        // Conversion vers Diagnostic[]
        List<Diagnostic> diagnostics = convertToDiagnostics(collector.getErrors());
        LOG.info("Analyse complétée : {} diagnostiques", diagnostics.size());
        
        return diagnostics;
    }

    /**
     * Version qui retourne les XMLError bruts pour les notifications personnalisées
     */
    public List<XMLError> analyzeAndGetErrors(File xmlFile, File xsdFile) {
        LOG.info("Analyse XML (raw errors) : {}", xmlFile.getAbsolutePath());

        ErrorCollector collector = new ErrorCollector();

        if (xsdFile != null && xsdFile.exists()) {
            new Validator(collector).validate(xmlFile, xsdFile);
        }

        // Parsing SAX tolérant (détection structure/syntaxe)
        XMLParser parser = new XMLParser(collector);
        parser.parse(xmlFile);

        LOG.info("Analyse complétée : {} erreurs brutes", collector.getErrorCount());

        return collector.getErrors();
    }

    /**
     * Convertit les com.xml.XMLError en Diagnostic LSP.
     * Assigne des sévérités appropriées selon le type d'erreur.
     */
    public List<Diagnostic> convertToDiagnostics(List<XMLError> errors) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        
        for (XMLError err : errors) {
            Diagnostic d = new Diagnostic();
            
            switch (err.getType()) {
                case "FATAL_SYNTAX":
                case "FATAL_VALIDATION":
                case "FATAL_PARSE":
                    d.setSeverity(DiagnosticSeverity.Error);
                    break;
                case "SYNTAX":
                case "STRUCTURE":
                case "VALIDATION_ERROR":
                    d.setSeverity(DiagnosticSeverity.Error);
                    break;
                case "VALIDATION_WARNING":
                case "WARNING":
                    d.setSeverity(DiagnosticSeverity.Warning);
                    break;
                default:
                    d.setSeverity(DiagnosticSeverity.Information);
            }
            
            d.setMessage(err.getMessage());
            
            // Plage : ligne entière (VS Code clamp si trop long)
            int line = Math.max(err.getLineNumber() - 1, 0);
            d.setRange(new Range(new Position(line, 0), new Position(line, 999)));
            
            d.setCode(err.getType());
            d.setSource("xml-validator");
            
            diagnostics.add(d);
        }
        
        return diagnostics;
    }
}
