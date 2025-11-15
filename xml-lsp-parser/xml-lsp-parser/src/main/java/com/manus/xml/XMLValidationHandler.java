package com.manus.xml;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class XMLValidationHandler {

    private final LanguageClient client;

    public XMLValidationHandler(LanguageClient client) {
        this.client = client;
    }

    @JsonRequest("xml/validateFiles")
    public CompletableFuture<ValidationResults> onValidateFiles(ValidateFilesParams params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File xmlFile = new File(URI.create(params.xmlUri));
                File xsdFile = params.xsdUri == null ? null : new File(URI.create(params.xsdUri));

                XMLValidatorService validator = new XMLValidatorService();
                List<Diagnostic> diagnostics = validator.analyze(xmlFile, xsdFile);

                return new ValidationResults(params.xmlUri, diagnostics);
            } catch (Exception e) {
                e.printStackTrace();
                return new ValidationResults(params.xmlUri, List.of());
            }
        });
    }
}
