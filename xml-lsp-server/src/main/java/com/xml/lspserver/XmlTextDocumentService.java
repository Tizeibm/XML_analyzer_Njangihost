package com.xml.lspserver;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Service de gestion des documents texte pour le serveur LSP
 */
public class XmlTextDocumentService implements TextDocumentService {
    
    private final XmlLanguageServer server;
    
    public XmlTextDocumentService(XmlLanguageServer server) {
        this.server = server;
    }
    
    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        server.logInfo("Document ouvert : " + params.getTextDocument().getUri());
    }
    
    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        // Géré par les commandes personnalisées
    }
    
    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        server.logInfo("Document fermé : " + params.getTextDocument().getUri());
    }
    
    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        server.logInfo("Document sauvegardé : " + params.getTextDocument().getUri());
    }
}
