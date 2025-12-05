package com.xml.lspserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.xml.handlers.LargeXmlValidator;
import com.xml.handlers.XMLValidationHandler;
import com.xml.models.ErrorListResponse;
import com.xml.models.NavigationParams;
import com.xml.models.NavigationResponse;
import com.xml.models.PatchParams;
import com.xml.models.PatchResponse;
import com.xml.models.UpdateFragmentParams;
import com.xml.models.ValidateDocumentParams;
import com.xml.models.ValidateFilesParams;
import com.xml.models.ValidationResponse;
import com.xml.models.ValidationResult;
import com.xml.models.XMLError;

/**
 * Serveur LSP XML avec support pour fichiers massifs et Virtual Patching
 */
@JsonSegment("xml")
public class XmlLanguageServer implements LanguageServer, LanguageClientAware {

    private final XmlTextDocumentService textService;
    private final XmlWorkspaceService workspaceService;
    private final XMLValidationHandler validationHandler = new XMLValidationHandler();
    private LanguageClient client;

    // Gestion des fichiers massifs avec Virtual Patching
    private final com.xml.models.FragmentIndex fragmentIndex = new com.xml.models.FragmentIndex();
    private com.xml.services.PatchManager patchManager; // Initialisé dans initialize()
    private com.xml.services.PatchedFragmentManager fragmentManager;
    private final com.xml.services.FragmentValidator fragmentValidator = new com.xml.services.FragmentValidator();
    private final com.xml.services.FileSaver fileSaver = new com.xml.services.FileSaver();
    private final LargeXmlValidator largeXmlValidator = new LargeXmlValidator();
    private java.io.File currentXsdFile;
    private java.io.File currentXmlFile;
    
    // Global error tracking for xml/getErrors
    private final List<XMLError> globalErrors = new ArrayList<>();

    public XmlLanguageServer() {
        this.textService = new XmlTextDocumentService(this);
        this.workspaceService = new XmlWorkspaceService(this);
        // PatchManager sera initialisé avec le workspace root
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        // Initialiser le PatchManager avec le dossier racine du workspace
        String rootUri = params.getRootUri();
        java.nio.file.Path rootPath = java.nio.file.Paths.get(".");
        if (rootUri != null) {
            try {
                rootPath = java.nio.file.Paths.get(new java.net.URI(rootUri));
            } catch (Exception e) {
                System.err.println("Invalid root URI: " + rootUri);
            }
        }
        this.patchManager = new com.xml.services.PatchManager(rootPath);
        
        ServerCapabilities caps = new ServerCapabilities();
        caps.setTextDocumentSync(TextDocumentSyncKind.Full);

        caps.setExecuteCommandProvider(new ExecuteCommandOptions(
                java.util.Arrays.asList(
                        "xml.validateFiles",
                        "xml.navigateToError",
                        "xml.patchFragment",
                        "xml.indexFile",
                        "xml.getFragment",
                        "xml.validateFragment",
                        "xml.updateFragment",
                        "xml.saveFile",
                        "xml/getDiagnostics",
                        "xml/applyFragmentPatch")));

        InitializeResult result = new InitializeResult();
        result.setCapabilities(caps);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void initialized(InitializedParams params) {
        logInfo("Serveur LSP XML démarré - Support fichiers massifs + Virtual Patching");
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        logInfo("Arrêt du serveur LSP XML");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    public LanguageClient getClient() {
        return client;
    }

    public void logInfo(String message) {
        if (client != null) {
            client.logMessage(new MessageParams(MessageType.Info, message));
        }
    }

    public void logWarning(String message) {
        if (client != null) {
            client.logMessage(new MessageParams(MessageType.Warning, message));
        }
    }

    public void logError(String message) {
        if (client != null) {
            client.logMessage(new MessageParams(MessageType.Error, message));
        }
    }

    // === Commandes LSP Standards ===

    @JsonRequest("validateFiles")
    public CompletableFuture<ValidationResponse> validateFiles(ValidateFilesParams params) {
        return validationHandler.validateFiles(params);
    }

    @JsonRequest("navigateToError")
    public CompletableFuture<NavigationResponse> navigateToError(NavigationParams params) {
        return validationHandler.navigateToError(params);
    }

    @JsonRequest("patchFragment")
    public CompletableFuture<PatchResponse> patchFragment(PatchParams params) {
        return validationHandler.patchFragment(params);
    }

    @JsonRequest("getDiagnostics")
    public CompletableFuture<ValidationResponse> getDiagnostics(ValidateFilesParams params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Valider le fichier
                ValidationResponse response = validationHandler.validateFiles(params).join();

                if (response.errors == null || response.errors.isEmpty()) {
                    return response;
                }

                // 2. Enrichir les erreurs avec les infos de fragment
                for (com.xml.models.XMLError error : response.errors) {
                    com.xml.models.FragmentMetadata frag = fragmentIndex.getFragmentForLine(error.getLineNumber());
                    if (frag != null) {
                        error.setFragment(frag.getId());
                        error.setFragmentStartLine(frag.getStartLine());
                        error.setFragmentEndLine(frag.getEndLine());
                    }
                }

                return response;
            } catch (Exception e) {
                logError("Erreur getDiagnostics : " + e.getMessage());
                return new ValidationResponse(false, "Erreur interne : " + e.getMessage());
            }
        });
    }

    // === Commandes pour Fichiers Massifs ===

    @JsonRequest("indexFile")
    public CompletableFuture<String> indexFile(String fileUri) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logInfo("Indexation : " + fileUri);
                java.net.URI uri = new java.net.URI(fileUri);
                java.io.File file = new java.io.File(uri);

                if (!file.exists()) {
                    throw new java.io.FileNotFoundException("Fichier introuvable");
                }

                String xsdPath = file.getAbsolutePath().replace(".xml", ".xsd");
                currentXsdFile = new java.io.File(xsdPath);
                if (!currentXsdFile.exists()) {
                    currentXsdFile = null;
                }

                com.xml.handlers.StreamingIndexer indexer = new com.xml.handlers.StreamingIndexer(fragmentIndex);
                indexer.indexFile(file);

                this.currentXmlFile = file;
                // Initialisation avec FragmentIndex pour supporter updateFragment
                this.fragmentManager = new com.xml.services.PatchedFragmentManager(file, patchManager, fragmentIndex);

                int count = fragmentIndex.getAllFragments().size();
                logInfo("Indexation OK : " + count + " fragments");
                return "OK:" + count;
            } catch (Exception e) {
                logError("Erreur indexation : " + e.getMessage());
                return "ERROR:" + e.getMessage();
            }
        });
    }

    @JsonRequest("getFragment")
    public CompletableFuture<String> getFragment(String fragmentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (fragmentManager == null) {
                    return "ERROR:Aucun fichier indexé";
                }

                com.xml.models.FragmentMetadata frag = fragmentIndex.getFragmentById(fragmentId);
                if (frag == null) {
                    return "ERROR:Fragment introuvable";
                }

                return fragmentManager.getFragmentContent(frag);
            } catch (Exception e) {
                logError("Erreur lecture : " + e.getMessage());
                return "ERROR:" + e.getMessage();
            }
        });
    }

    @JsonRequest("validateFragment")
    public CompletableFuture<ValidationResult> validateFragment(String fragmentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (fragmentManager == null) {
                    return new ValidationResult(false, java.util.Collections.emptyList(), 0, 0);
                }

                com.xml.models.FragmentMetadata frag = fragmentIndex.getFragmentById(fragmentId);
                if (frag == null) {
                    return new ValidationResult(false, java.util.Collections.emptyList(), 0, 0);
                }

                String content = fragmentManager.getFragmentContent(frag);
                return fragmentValidator.validateFragment(content, currentXsdFile, false);
            } catch (Exception e) {
                logError("Erreur validation : " + e.getMessage());
                return new ValidationResult(false, java.util.Collections.emptyList(), 0, 0);
            }
        });
    }

    @JsonRequest("updateFragment")
    public CompletableFuture<String> updateFragment(UpdateFragmentParams params) {
        return applyPatchInternal(params.getFragmentId(), params.getNewContent());
    }

    @JsonRequest("applyPatch")
    public CompletableFuture<String> applyPatch(UpdateFragmentParams params) {
        return applyPatchInternal(params.getFragmentId(), params.getNewContent());
    }

    @JsonRequest("applyFragmentPatch")
    public CompletableFuture<String> applyFragmentPatch(com.xml.models.FragmentPatchParams params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (patchManager == null) {
                    return "ERROR:PatchManager non initialisé";
                }

                // Créer le patch
                com.xml.models.PatchType type = com.xml.models.PatchType.REPLACE;
                if (params.getReplacementText().isEmpty()) {
                    type = com.xml.models.PatchType.DELETE;
                } else if (params.getGlobalStartOffset() == params.getGlobalEndOffset()) {
                    type = com.xml.models.PatchType.INSERT;
                }

                com.xml.models.Patch patch = new com.xml.models.Patch(
                    params.getGlobalStartOffset(),
                    params.getGlobalEndOffset(),
                    params.getReplacementText(),
                    type,
                    params.getFragmentId()
                );

                // Ajouter au gestionnaire (gère conflits, fusion, persistence)
                patchManager.addPatch(patch);

                int patchCount = patchManager.getPatchCount();
                logInfo("Patch granulaire appliqué : " + params.getFragmentId() + " [" + params.getGlobalStartOffset() + "-" + params.getGlobalEndOffset() + ")");
                return "OK:" + patchCount;
            } catch (Exception e) {
                logError("Erreur applyFragmentPatch : " + e.getMessage());
                return "ERROR:" + e.getMessage();
            }
        });
    }

    private CompletableFuture<String> applyPatchInternal(String fragmentId, String newContent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (fragmentManager == null) {
                    return "ERROR:Aucun fichier indexé";
                }
                
                fragmentManager.updateFragment(fragmentId, newContent);
                
                int patchCount = fragmentManager.getUnsavedPatchCount();
                logInfo("Patch appliqué : " + fragmentId);
                return "OK:" + patchCount + " patchs";
            } catch (Exception e) {
                logError("Erreur applyPatch : " + e.getMessage());
                return "ERROR:" + e.getMessage();
            }
        });
    }

    @JsonRequest("saveFile")
    public CompletableFuture<String> saveFile(String fileUriOrNull) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (fragmentManager == null || currentXmlFile == null) {
                    return "ERROR:Aucun fichier indexé";
                }

                int patchCount = fragmentManager.getUnsavedPatchCount();
                if (patchCount == 0) {
                    return "OK:Aucune modification";
                }

                java.io.File outputFile = currentXmlFile;
                if (fileUriOrNull != null && !fileUriOrNull.isEmpty()) {
                    outputFile = new java.io.File(new java.net.URI(fileUriOrNull));
                }

                fileSaver.saveWithPatches(currentXmlFile, outputFile, fragmentIndex, patchManager);

                logInfo("Sauvegarde OK : " + patchCount + " patchs appliqués");
                return "OK:" + patchCount + " patchs appliqués";
            } catch (Exception e) {
                logError("Erreur sauvegarde : " + e.getMessage());
                return "ERROR:" + e.getMessage();
            }
        });
    }

    // === New Custom Requests for Streaming Architecture ===

    /**
     * xml/validateDocument : Valide XML avec XSD, optionnellement avec patches appliqués.
     * Utilise PatchedInputStream pour la reconstruction à la volée sans charger le fichier en mémoire.
     */
    @JsonRequest("validateDocument")
    public CompletableFuture<ValidationResult> validateDocument(ValidateDocumentParams params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logInfo("Validation document : " + params.getXmlPath() + " (applyPatches=" + params.isApplyPatches() + ")");
                
                java.io.File xmlFile = new java.io.File(new java.net.URI(params.getXmlPath()));
                java.io.File xsdFile = params.getXsdPath() != null ? 
                    new java.io.File(new java.net.URI(params.getXsdPath())) : null;
                
                ValidationResult result;
                
                if (params.isApplyPatches() && patchManager != null && patchManager.getPatchCount() > 0) {
                    // Validation avec patches (streaming)
                    result = largeXmlValidator.validateWithPatches(xmlFile, xsdFile, patchManager);
                    logInfo("Validation avec " + patchManager.getPatchCount() + " patches : " + 
                           (result.isSuccess() ? "OK" : result.getErrors().size() + " erreurs"));
                } else {
                    // Validation normale (sans patches)
                    result = largeXmlValidator.validate(xmlFile, xsdFile);
                    logInfo("Validation normale : " + (result.isSuccess() ? "OK" : result.getErrors().size() + " erreurs"));
                }
                
                // Enrich errors with fragment information before storing
                for (XMLError error : result.getErrors()) {
                    com.xml.models.FragmentMetadata frag = fragmentIndex.getFragmentForLine(error.getLineNumber());
                    if (frag != null) {
                        error.setFragment(frag.getId());
                        error.setFragmentStartLine(frag.getStartLine());
                        error.setFragmentEndLine(frag.getEndLine());
                    }
                }
                
                // Stocker les erreurs globalement
                synchronized (globalErrors) {
                    globalErrors.clear();
                    globalErrors.addAll(result.getErrors());
                }
                
                return result;
                
            } catch (Exception e) {
                logError("Erreur validateDocument : " + e.getMessage());
                List<XMLError> errors = new ArrayList<>();
                errors.add(new XMLError("Erreur lors de la validation: " + e.getMessage(), 0, "FATAL"));
                return new ValidationResult(false, errors, 0, 0);
            }
        });
    }

    /**
     * xml/getErrors : Récupère toutes les erreurs détectées lors de la dernière validation.
     * Les erreurs incluent les offsets globaux et les infos de fragments.
     */
    @JsonRequest("getErrors")
    public CompletableFuture<ErrorListResponse> getErrors() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logInfo("Récupération des erreurs globales : " + globalErrors.size() + " erreurs");
                
                String filePath = currentXmlFile != null ? currentXmlFile.getAbsolutePath() : "";
                
                return new ErrorListResponse(new ArrayList<>(globalErrors), filePath);
                
            } catch (Exception e) {
                logError("Erreur getErrors : " + e.getMessage());
                return new ErrorListResponse(new ArrayList<>(), "");
            }
        });
    }
}
