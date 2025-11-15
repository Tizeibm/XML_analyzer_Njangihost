package com.manus.xml;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Appelle l'analyseur SAX (+ XSD si fourni) et convertit les XMLError
 * en Diagnostic LSP.
 * Le fichier est LU EN STREAMING : pas de chargement complet en mémoire.
 */
public class XMLValidatorService {

    /* ---------- lancement de l'analyse ---------- */
    public List<Diagnostic> analyze(File xmlFile, File xsdFile) {
        ErrorCollector collector = new ErrorCollector();
        XMLParser parser = new XMLParser(collector);

        // 1) validation XSD (si fourni)
        if (xsdFile != null && xsdFile.exists()) {
            new Validator(collector).validate(xmlFile, xsdFile);
        }

        // 2) parsing SAX tolérant (détection structure/syntaxe)
        parser.parse(xmlFile);      // streaming

        // 3) conversion vers Diagnostic[]
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (XMLError err : collector.getErrors()) {
            Diagnostic d = new Diagnostic();
            d.setSeverity(DiagnosticSeverity.Error);
            d.setMessage(err.getMessage());
            // Plage : ligne entière (VS Code clamp si trop long)
            int line = Math.max(err.getLineNumber() - 1, 0);
            d.setRange(new Range(new Position(line, 0), new Position(line, 999)));
            diagnostics.add(d);
        }

        return diagnostics;
    }
}