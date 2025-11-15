package com.manus.xml;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;
import java.util.concurrent.CompletableFuture;

/**
 * Implémentation minimale mais suffisante d'un LanguageServer.
 * On déclare uniquement la syncro « Full » et on laisse le client
 * envoyer la commande « xml/validateFiles » via la palette.
 */
public class XmlLanguageServer implements LanguageServer {

    private final XmlTextDocumentService textService;
    private final XmlWorkspaceService workspaceService;
    private LanguageClient client;
    XMLValidationHandler validationHandler = new XMLValidationHandler(this.client);


    public XmlLanguageServer() {
        this.textService = new XmlTextDocumentService(this);
        this.workspaceService = new XmlWorkspaceService(this);
    }

    /* ---------- Lifecycle LSP ---------- */
    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities caps = new ServerCapabilities();
        // On se contente d'une synchro full : le client nous renverra tout le texte
        caps.setTextDocumentSync(TextDocumentSyncKind.Full);
        return CompletableFuture.completedFuture(new InitializeResult(caps));
    }

    @Override public void initialized(InitializedParams params) {}
    @Override public CompletableFuture<Object> shutdown() { return CompletableFuture.completedFuture(null); }
    @Override public void exit() {}

    /* ---------- Accès aux services ---------- */
    @Override public TextDocumentService getTextDocumentService() { return textService; }
    @Override public WorkspaceService getWorkspaceService() { return workspaceService; }

    /* ---------- Communication client ---------- */
    public void connect(LanguageClient client) { this.client = client; }
    public LanguageClient getClient() { return client; }

}