package com.manus.xml;

import org.eclipse.lsp4j.launch.LSPLauncher;

/**
 * Lance le serveur LSP côté Java.
 * Le serveur écoute sur stdin/stdout (format JSON-RPC).
 */
public class XmlLanguageServerLauncher {
    public static void main(String[] args) throws Exception {
        XmlLanguageServer server = new XmlLanguageServer();
        var launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();   // bloquant
    }
}