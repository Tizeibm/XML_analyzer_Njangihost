package com.xml.lspserver;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lanceur du serveur LSP XML
 */
public class XmlLanguageServerLauncher {
    public static void main(String[] args) {
        try {
            System.err.println("Démarrage du serveur LSP XML...");
            
            XmlLanguageServer server = new XmlLanguageServer();
            
            InputStream in = System.in;
            OutputStream out = System.out;
            
            Launcher<LanguageClient> launcher = Launcher.createLauncher(
                server,
                LanguageClient.class,
                in,
                out
            );
            
            LanguageClient client = launcher.getRemoteProxy();
            server.connect(client);
            
            System.err.println("Serveur LSP XML démarré et en écoute...");
            
            launcher.startListening().get();
            
        } catch (Exception e) {
            System.err.println("Erreur fatale : " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
