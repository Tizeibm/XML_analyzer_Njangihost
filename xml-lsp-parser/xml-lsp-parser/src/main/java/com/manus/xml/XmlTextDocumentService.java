package com.manus.xml;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.TextDocumentService;
import java.io.File;
import java.net.URI;
import java.util.List;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Reçoit les événements classiques (didOpen, didChange...) MAIS surtout
 * la notification personnalisée « xml/validateFiles » déclenchée par la
 * palette de commandes VS Code.
 */
public class XmlTextDocumentService implements TextDocumentService {

    private final XmlLanguageServer server;

    public XmlTextDocumentService(XmlLanguageServer server) {
        this.server = server;
    }

    /* ---------- Notifications classiques (non utilisées ici) ---------- */
    @Override public void didOpen(DidOpenTextDocumentParams params)   {
        Diagnostic test = new Diagnostic();
        test.setMessage("Diagnostic de test LSP : ça marche !");
        test.setSeverity(DiagnosticSeverity.Warning);
        test.setRange(new Range(new Position(0, 0), new Position(0, 10)));
        server.getClient().publishDiagnostics(new PublishDiagnosticsParams(params.getTextDocument().getUri(), List.of(test)));
    }
    @Override public void didChange(DidChangeTextDocumentParams params) {}
    @Override public void didClose(DidCloseTextDocumentParams params) {}
    @Override public void didSave(DidSaveTextDocumentParams params)   {}

    /* ---------- Notification CUSTOM envoyée par VS Code ---------- */
    public static class ValidateFilesParams {
        public String xmlUri;   // file:///xxxx/yyyy.xml
        public String xsdUri;   // file:///xxxx/yyyy.xsd ou null
    }

    /**
     * Point d'entrée UNIQUE pour lancer l'analyse.
     * Appelé via la commande « xml.validateFiles » côté client.
     */
    /*@JsonNotification("xml/validateFiles")
    public void onValidateFiles(ValidateFilesParams params) {
        if (params.xmlUri == null) return;

        URI xmlUri = URI.create(params.xmlUri);
        File xmlFile = new File(xmlUri);
        File xsdFile = (params.xsdUri != null) ? new File(URI.create(params.xsdUri)) : null;

        LanguageClient client = server.getClient();

        XMLClientNotifications clientNotifications = client.getClass()
                .asSubclass(XMLClientNotifications.class)
                .cast(client);

        // On lance l'analyseur et on renvoie les diagnostics
        XMLValidatorService validator = new XMLValidatorService(server.getClient());
        List<Diagnostic> diagnostics = validator.analyze(xmlFile, xsdFile, xmlUri.toString()); // uri utilisée pour les diagnostics

        ValidationResults validationResults = new ValidationResults(params.xmlUri, diagnostics);
        client.<XMLClientNotifications>notify("xml/validationResults", validationResults);

    }*/
}