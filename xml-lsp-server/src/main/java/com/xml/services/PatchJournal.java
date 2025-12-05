package com.xml.services;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.xml.models.Patch;
import com.xml.models.PatchType;

/**
 * Gère la persistance des patchs dans un journal (append-only).
 * Format simple JSON-like par ligne pour éviter les dépendances lourdes.
 */
public class PatchJournal {

    private final Path journalPath;

    public PatchJournal(Path workspaceRoot) {
        this.journalPath = workspaceRoot.resolve(".xml-massive-lsp").resolve("patches.log");
    }

    public void logPatch(Patch patch) {
        ensureJournalExists();
        String json = serializePatch(patch);
        try (BufferedWriter writer = Files.newBufferedWriter(journalPath, StandardCharsets.UTF_8, 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(json);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écriture du patch dans le journal: " + e.getMessage());
        }
    }

    public List<Patch> loadPatches() {
        List<Patch> patches = new ArrayList<>();
        if (!Files.exists(journalPath)) {
            return patches;
        }

        try (BufferedReader reader = Files.newBufferedReader(journalPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    Patch patch = deserializePatch(line);
                    if (patch != null) {
                        patches.add(patch);
                    }
                } catch (Exception e) {
                    System.err.println("Patch corrompu ignoré: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la lecture du journal de patchs: " + e.getMessage());
        }
        return patches;
    }

    public void clearJournal() {
        try {
            Files.deleteIfExists(journalPath);
        } catch (IOException e) {
            System.err.println("Impossible de supprimer le journal: " + e.getMessage());
        }
    }

    private void ensureJournalExists() {
        try {
            if (!Files.exists(journalPath.getParent())) {
                Files.createDirectories(journalPath.getParent());
            }
        } catch (IOException e) {
            throw new RuntimeException("Impossible de créer le dossier du journal", e);
        }
    }

    // Sérialisation manuelle simple pour éviter les dépendances externes comme Gson/Jackson si non disponibles
    private String serializePatch(Patch patch) {
        // Échappement basique des guillemets et backslashes
        String safeText = patch.getReplacementText()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");

        return String.format("{\"start\":%d,\"end\":%d,\"type\":\"%s\",\"text\":\"%s\",\"fragment\":\"%s\"}",
                patch.getGlobalStartOffset(),
                patch.getGlobalEndOffset(),
                patch.getType().name(),
                safeText,
                patch.getFragmentId() != null ? patch.getFragmentId() : "");
    }

    private Patch deserializePatch(String json) {
        // Parsing manuel très basique
        try {
            long start = Long.parseLong(extractValue(json, "start"));
            long end = Long.parseLong(extractValue(json, "end"));
            String typeStr = extractString(json, "type");
            String text = extractString(json, "text");
            String fragment = extractString(json, "fragment");

            // Unescape is now handled by extractString
            // text = text.replace("\\r", "\r") ...

            return new Patch(start, end, text, PatchType.valueOf(typeStr), fragment.isEmpty() ? null : fragment);
        } catch (Exception e) {
            throw new IllegalArgumentException("Format JSON invalide", e);
        }
    }

    private String extractValue(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) throw new IllegalArgumentException("Clé manquante: " + key);
        start += search.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return json.substring(start, end).trim();
    }

    private String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) throw new IllegalArgumentException("Clé manquante: " + key);
        start += search.length();
        
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else {
                if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
        }
        throw new IllegalArgumentException("Chaîne non terminée pour: " + key);
    }
}
