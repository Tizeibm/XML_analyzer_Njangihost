package com.xml;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.xml.lspserver.XmlLanguageServer;
import com.xml.models.UpdateFragmentParams;
import com.xml.models.ValidateFilesParams;
import com.xml.models.ValidationResponse;

public class LargeFileWorkflowTest {

    private File tempFile;
    private XmlLanguageServer server;

    @BeforeEach
    void setUp() throws Exception {
        // Créer un fichier XML de test
        tempFile = File.createTempFile("test-large", ".xml");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("<root>\n");
            for (int i = 0; i < 100; i++) {
                writer.write("  <item id=\"" + i + "\">Value " + i + "</item>\n");
            }
            // Une erreur
            writer.write("  <item id=\"error\">Missing closing tag\n");
            writer.write("</root>");
        }

        server = new XmlLanguageServer();
        // Initialize server to setup PatchManager
        org.eclipse.lsp4j.InitializeParams params = new org.eclipse.lsp4j.InitializeParams();
        params.setRootUri(tempFile.getParentFile().toURI().toString());
        server.initialize(params).get();
    }

    @AfterEach
    void tearDown() {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    void testFullWorkflow() throws ExecutionException, InterruptedException, Exception {
        // 1. Indexation
        String indexResult = server.indexFile(tempFile.toURI().toString()).get();
        assertTrue(indexResult.startsWith("OK:"), "Indexation failed: " + indexResult);

        // 2. Get Diagnostics
        ValidateFilesParams validateParams = new ValidateFilesParams(tempFile.toURI().toString(), null);
        ValidationResponse diagnostics = server.getDiagnostics(validateParams).get();
        
        assertFalse(diagnostics.success, "Validation should fail");
        assertFalse(diagnostics.errors.isEmpty(), "Should have errors");
        
        // Vérifier que l'erreur a des infos de fragment
        boolean foundFragmentInfo = false;
        String errorFragmentId = null;
        for (com.xml.models.XMLError error : diagnostics.errors) {
            if (error.getFragment() != null) {
                foundFragmentInfo = true;
                errorFragmentId = error.getFragment();
                System.out.println("Error mapped to fragment: " + errorFragmentId + " lines " + error.getFragmentStartLine() + "-" + error.getFragmentEndLine());
                break;
            }
        }
        assertTrue(foundFragmentInfo, "Errors should be mapped to fragments");

        // 3. Apply Patch (Simulate user fixing the error)
        // On suppose que l'erreur est dans le dernier item
        // On récupère le fragment pour être sûr
        String fragmentContent = server.getFragment(errorFragmentId).get();
        System.out.println("Original Fragment Content:\n" + fragmentContent);
        
        String fixedContent = "  <item id=\"error\">Fixed Value</item>";
        UpdateFragmentParams patchParams = new UpdateFragmentParams(errorFragmentId, fixedContent);
        String patchResult = server.applyPatch(patchParams).get();
        
        assertTrue(patchResult.startsWith("OK:"), "Patch application failed");
        assertTrue(patchResult.contains("1 patchs"), "Should have 1 patch pending");

        // 4. Save File
        String saveResult = server.saveFile(null).get(); // Save to same file
        assertTrue(saveResult.startsWith("OK:"), "Save failed");

        // 5. Verify File Content
        String content = Files.readString(tempFile.toPath());
        assertTrue(content.contains("Fixed Value"), "File should contain fixed value");
        assertFalse(content.contains("Missing closing tag"), "File should not contain error anymore");
        
        System.out.println("Workflow verified successfully!");
    }
}
