package com.xml.handlers;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XmlZoneExtractor amélioré :
 * - détection d'encodage prolog
 * - lecture sûre (BufferedReader avec charset)
 * - extraction par position limitée (prévenir OOM)
 * - meilleure détection du parent via regex
 */
public class XmlZoneExtractor {

    private static final int BUFFER_SIZE = 8192;
    private static final int ZONE_CONTEXT_LINES = 3;
    private static final int MAX_BYTES_FOR_APPROX_EXTRACT = 1 * 1024 * 1024; // 1 MiB max

    private static final Pattern XML_ENCODING = Pattern.compile("<\\?xml[^>]*encoding\\s*=\\s*['\"]([^'\"]+)['\"][^>]*\\?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPENING_TAG = Pattern.compile("<([A-Za-z0-9_:\\-\\.]+)(?=(\\s|>|/))");

    public static XmlZone extractErrorZone(File xmlFile, int errorLine) {
        return extractZone(xmlFile, errorLine, ZONE_CONTEXT_LINES);
    }

    public static XmlZone extractZone(File xmlFile, int centerLine, int contextLines) {
        try {
            if (xmlFile == null || !xmlFile.exists() || !xmlFile.canRead()) {
                return XmlZone.EMPTY_ZONE;
            }

            Charset cs = detectEncodingOrDefault(xmlFile);
            int startLine = Math.max(1, centerLine - contextLines);
            int endLine = centerLine + contextLines;

            List<String> zoneLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(xmlFile), cs), BUFFER_SIZE)) {
                String line;
                int currentLine = 0;
                while ((line = reader.readLine()) != null) {
                    currentLine++;
                    if (currentLine >= startLine && currentLine <= endLine) {
                        zoneLines.add(line);
                    } else if (currentLine > endLine) {
                        break;
                    }
                }
            }

            return new XmlZone(String.join("\n", zoneLines), startLine, Math.max(startLine, startLine + zoneLines.size() - 1),
                    centerLine, zoneLines.size());
        } catch (Exception e) {
            return XmlZone.EMPTY_ZONE;
        }
    }

    /**
     * Extrait autour d'une position approximative (offset en octets).
     * Limite la taille lue pour éviter OOM.
     */
    public static XmlZone extractZoneByApproximatePosition(File xmlFile, long approximatePosition, int contextSize) {
        try {
            if (xmlFile == null || !xmlFile.exists() || !xmlFile.canRead()) return XmlZone.EMPTY_ZONE;

            long fileSize = xmlFile.length();
            int cap = Math.min(contextSize, MAX_BYTES_FOR_APPROX_EXTRACT);
            long startPos = Math.max(0L, approximatePosition - cap);
            long endPos = Math.min(fileSize, approximatePosition + cap);
            int readLen = (int) (endPos - startPos);
            if (readLen <= 0) return XmlZone.EMPTY_ZONE;

            byte[] buffer = new byte[readLen];
            try (RandomAccessFile raf = new RandomAccessFile(xmlFile, "r")) {
                raf.seek(startPos);
                int total = 0;
                while (total < readLen) {
                    int r = raf.read(buffer, total, readLen - total);
                    if (r < 0) break;
                    total += r;
                }
            }

            Charset cs = detectEncodingOrDefault(xmlFile);
            String content = new String(buffer, cs);
            int lines = countLines(content);
            return new XmlZone(content, 0, 0, 0, lines);

        } catch (Exception e) {
            return XmlZone.EMPTY_ZONE;
        }
    }

    /**
     * Extrait une zone plus large et tente d'identifier la balise parente la plus proche
     */
    public static XmlZone extractZoneWithParentContext(File xmlFile, int errorLine, String tagName) {
        try {
            if (xmlFile == null || !xmlFile.exists() || !xmlFile.canRead()) return XmlZone.EMPTY_ZONE;

            Charset cs = detectEncodingOrDefault(xmlFile);
            int startLine = Math.max(1, errorLine - 10);
            int endLine = errorLine + 5;

            List<String> contextLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(xmlFile), cs), BUFFER_SIZE)) {
                String line;
                int currentLine = 0;
                while ((line = reader.readLine()) != null) {
                    currentLine++;
                    if (currentLine >= startLine && currentLine <= endLine) {
                        contextLines.add(line);
                    } else if (currentLine > endLine) break;
                }
            }

            String zoneContent = String.join("\n", contextLines);
            String parent = findLikelyParent(zoneContent, tagName);
            return new XmlZone(zoneContent, startLine, Math.max(startLine, startLine + contextLines.size() - 1),
                    errorLine, contextLines.size(), parent);

        } catch (Exception e) {
            return XmlZone.EMPTY_ZONE;
        }
    }

    /** Essaie d'extraire le nom du parent en recherchant la dernière balise ouvrante non fermante et non self-closing */
    private static String findLikelyParent(String snippet, String excludeTag) {
        if (snippet == null || snippet.isEmpty()) return null;

        // parcourir toutes les occurrences d'ouverture et prendre la dernière qui n'est pas self-closing ni fermante
        Matcher m = OPENING_TAG.matcher(snippet);
        String last = null;
        while (m.find()) {
            String name = m.group(1);
            int start = m.start();
            // vérifier si c'est une balise fermante (preceded by </) ou self-closing (tailing / before >)
            boolean isClosing = (start > 0 && snippet.charAt(start - 1) == '/');
            if (isClosing) continue;
            // check self-closing by looking ahead a small window
            int endIdx = Math.min(snippet.length(), m.end() + 100);
            String tail = snippet.substring(m.end(), endIdx);
            if (tail.contains("/>")) continue;
            if (excludeTag != null && excludeTag.equals(name)) continue;
            last = name;
        }
        return last;
    }

    private static Charset detectEncodingOrDefault(File xmlFile) {
        try (InputStream is = new FileInputStream(xmlFile)) {
            byte[] buf = new byte[4096];
            int r = is.read(buf);
            if (r <= 0) return StandardCharsets.UTF_8;
            String head = new String(buf, 0, r, StandardCharsets.UTF_8);
            Matcher m = XML_ENCODING.matcher(head);
            if (m.find()) {
                String enc = m.group(1);
                try {
                    return Charset.forName(enc);
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {}
        return StandardCharsets.UTF_8;
    }

    private static int countLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        int lines = 0;
        int idx = 0;
        while ((idx = text.indexOf('\n', idx)) != -1) {
            lines++;
            idx++;
        }
        // last line may not end with \n
        if (!text.endsWith("\n")) lines++;
        return lines;
    }

    public static class XmlZone {
        public static final XmlZone EMPTY_ZONE = new XmlZone("", 0, 0, 0, 0);

        private final String content;
        private final int startLine;
        private final int endLine;
        private final int centerLine;
        private final int lineCount;
        private final String parentTag;

        public XmlZone(String content, int startLine, int endLine, int centerLine, int lineCount) {
            this(content, startLine, endLine, centerLine, lineCount, null);
        }

        public XmlZone(String content, int startLine, int endLine, int centerLine, int lineCount, String parentTag) {
            this.content = content == null ? "" : content;
            this.startLine = startLine;
            this.endLine = endLine;
            this.centerLine = centerLine;
            this.lineCount = lineCount;
            this.parentTag = parentTag;
        }

        public String getContent() { return content; }
        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
        public int getCenterLine() { return centerLine; }
        public int getLineCount() { return lineCount; }
        public String getParentTag() { return parentTag; }
        public boolean isEmpty() { return content == null || content.trim().isEmpty(); }

        /** Fournit une version formatée, avec numéros de ligne et marquage de la ligne centrale */
        public String getFormattedContent() {
            if (isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            String[] lines = content.split("\r\n|\r|\n", -1);
            int base = Math.max(1, startLine);
            for (int i = 0; i < lines.length; i++) {
                int lineno = base + i;
                String prefix = String.format("%4d | ", lineno);
                if (lineno == centerLine) {
                    sb.append("> ").append(prefix).append(lines[i]).append('\n');
                } else {
                    sb.append("  ").append(prefix).append(lines[i]).append('\n');
                }
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("Zone[lignes %d-%d, centre: %d, lignes: %d, parent=%s]",
                    startLine, endLine, centerLine, lineCount, parentTag);
        }
    }
}
