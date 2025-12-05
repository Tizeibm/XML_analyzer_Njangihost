package com.xml.lspserver;

import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;

/**
 * Service de gestion du workspace pour le serveur LSP
 */
public class XmlWorkspaceService implements WorkspaceService {
    
    private final XmlLanguageServer server;
    
    public XmlWorkspaceService(XmlLanguageServer server) {
        this.server = server;
    }
    
    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        server.logInfo("Configuration modifiée");
    }
    
    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        server.logInfo("Fichiers surveillés modifiés");
    }
}
