package com.manus.xml;

import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.lsp4j.*;

public class XmlWorkspaceService implements WorkspaceService {
    public XmlWorkspaceService(XmlLanguageServer server) {}
    @Override public void didChangeConfiguration(DidChangeConfigurationParams params) {}
    @Override public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {}
}