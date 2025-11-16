package com.xml;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Gestionnaire des requêtes xml/validateFiles du client LSP.
 */
public class XMLValidationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(XMLValidationHandler.class);
    private final NotificationHandler notificationHandler;

    public XMLValidationHandler(NotificationHandler notificationHandler) {
        this.notificationHandler = notificationHandler;
    }

    @JsonRequest("xml/validateFiles")
    public CompletableFuture<ValidationResults> onValidateFiles(ValidateFilesParams params) {
        LOG.info("Requête validation reçue : XML={}, XSD={}", params.xmlUri, params.xsdUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                File xmlFile = new File(URI.create(params.xmlUri));
                File xsdFile = params.xsdUri == null ? null : new File(URI.create(params.xsdUri));

                if (!xmlFile.exists()) {
                    throw new IllegalArgumentException("Fichier XML introuvable : " + xmlFile.getAbsolutePath());
                }

                XMLValidatorService validator = new XMLValidatorService();
                List<XMLError> errors = validator.analyzeAndGetErrors(xmlFile, xsdFile);
                List<org.eclipse.lsp4j.Diagnostic> diagnostics = validator.convertToDiagnostics(errors);

                // ENVOYER LES NOTIFICATIONS PERSONNALISÉES
                notificationHandler.sendValidationResults(params.xmlUri, errors);

                // Envoyer des notifications individuelles pour les erreurs importantes
                for (XMLError error : errors) {
                    if ("STRUCTURE".equals(error.getType()) &&
                            error.getMessage().contains("non fermée")) {
                        notificationHandler.sendStructureError(params.xmlUri, error);
                    }
                }

                ValidationResults results = new ValidationResults(params.xmlUri, diagnostics);
                LOG.info("Validation terminée : {} diagnostiques", diagnostics.size());

                return results;
            } catch (Exception e) {
                LOG.error("Erreur lors de la validation", e);
                return new ValidationResults(params.xmlUri, List.of());
            }
        });
    }
}