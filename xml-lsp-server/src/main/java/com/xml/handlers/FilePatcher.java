package com.xml.handlers;

import javax.xml.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class FilePatcher {

    public static PatchResult patchFile(
            String filePath,
            String modifiedFragment,
            int fragmentStartLine,
            int fragmentEndLine) {

        Path original = Paths.get(filePath);
        if (!Files.exists(original) || !Files.isRegularFile(original)) {
            return new PatchResult(false, "Fichier non trouvé: " + filePath);
        }

        Path tempFile = null;

        try {
            Path parentDir = original.getParent();
            if (parentDir == null) parentDir = Paths.get(".");

            tempFile = Files.createTempFile(parentDir, "xml-patch-", ".tmp");

            // Tenter de copier les permissions POSIX
            try {
                Files.setPosixFilePermissions(tempFile, Files.getPosixFilePermissions(original));
            } catch (UnsupportedOperationException ignored) {}

            // Déterminer le style de fin de ligne du fichier (LF ou CRLF)
            String eol = detectEOL(original);

            try (BufferedReader reader = Files.newBufferedReader(original, StandardCharsets.UTF_8);
                 BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {

                String line;
                int currentLine = 0;

                while ((line = reader.readLine()) != null) {
                    currentLine++;

                    if (currentLine < fragmentStartLine) {
                        writer.write(line);
                        writer.write(eol);
                    }
                    else if (currentLine == fragmentStartLine) {
                        // Écrire le fragment fourni
                        if (modifiedFragment != null) {
                            for (String fragLine : modifiedFragment.split("\r?\n")) {
                                writer.write(fragLine);
                                writer.write(eol);
                            }
                        }

                        // Sauter le fragment original
                        while (currentLine < fragmentEndLine && (line = reader.readLine()) != null) {
                            currentLine++;
                        }
                    }
                    else {
                        writer.write(line);
                        writer.write(eol);
                    }
                }
            }

            // Validation XML
            if (!isWellFormedXml(tempFile)) {
                Files.deleteIfExists(tempFile);
                return new PatchResult(false, "Le XML résultant n'est pas bien formé");
            }

            // Remplacement sécurisé
            return replaceFileSafely(original, tempFile);

        } catch (Exception e) {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
            return new PatchResult(false, "Erreur: " + e.getMessage());
        }
    }

    /** Détecte LF ou CRLF selon la première ligne du fichier */
    private static String detectEOL(Path file) {
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            int prev = -1;
            int cur;
            while ((cur = in.read()) != -1) {
                if (cur == '\n') {
                    return (prev == '\r') ? "\r\n" : "\n";
                }
                prev = cur;
            }
        } catch (IOException ignored) {}
        return "\n"; // défaut si non détecté
    }

    private static boolean isWellFormedXml(Path xmlFile) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        try (InputStream in = Files.newInputStream(xmlFile)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            try {
                while (reader.hasNext()) reader.next();
            } finally {
                reader.close();
            }
            return true;

        } catch (XMLStreamException | IOException e) {
            return false;
        }
    }

    private static PatchResult replaceFileSafely(Path original, Path tempFile) throws IOException {
        Path backup = Paths.get(original.toString() + ".backup." + System.currentTimeMillis());

        try {
            Files.copy(original, backup, StandardCopyOption.REPLACE_EXISTING);

            try {
                Files.move(tempFile, original,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, original, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.deleteIfExists(backup);
            return new PatchResult(true, "Patch appliqué avec succès", null);

        } catch (Exception e) {
            // En cas d’échec → restauration
            if (Files.exists(backup)) {
                Files.move(backup, original, StandardCopyOption.REPLACE_EXISTING);
            }
            return new PatchResult(false,
                    "Échec du remplacement, backup restauré",
                    backup.toString());
        }
    }

    public static class PatchResult {
        private final boolean success;
        private final String message;
        private final String backupPath;

        public PatchResult(boolean success, String message, String backupPath) {
            this.success = success;
            this.message = message;
            this.backupPath = backupPath;
        }

        public PatchResult(boolean success, String message) {
            this(success, message, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getBackupPath() { return backupPath; }
    }
}
