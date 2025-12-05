package com.xml.handlers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.stream.Location;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import com.xml.models.ErrorCollector;

/**
 * StAX-based handler pour détecter les balises non fermées avec précision.
 * Rapporte l'erreur à la ligne/colonne exacte de la balise ouvrante.
 * 
 * AMÉLIORATION: Détecte TOUTES les erreurs syntaxiques même après une erreur fatale
 * en faisant une analyse supplémentaire ligne par ligne.
 */
public class TrackedStaxHandler {
    private final ErrorCollector collector;

    // Stack pour suivre les balises ouvertes avec leur position exacte
    private final Deque<TagInfo> tagStack = new ArrayDeque<>();

    private static class TagInfo {
        final String name;
        final int lineNumber;
        final int columnNumber;

        TagInfo(String name, int lineNumber, int columnNumber) {
            this.name = name;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
        }
    }

    public TrackedStaxHandler(ErrorCollector collector) {
        this.collector = collector;
    }

    public void parse(java.io.File file) {
        // Tentative de parsing StAX normal
        boolean staxSucceeded = false;
        try (InputStream in = new java.io.FileInputStream(file)) {
            staxSucceeded = tryStaxParsing(in);
        } catch (IOException e) {
            collector.addError("Erreur de lecture du fichier : " + e.getMessage(), 0, "IO_ERROR");
            return;
        }

        // Si StAX a échoué (erreur fatale), faire une analyse ligne par ligne
        // pour détecter TOUTES les erreurs syntaxiques
        if (!staxSucceeded) {
            performLineByLineAnalysis(file);
        }
    }

    /**
     * Parse an InputStream using StAX streaming.
     * 
     * IMPORTANT: For true streaming with minimal memory usage, this method ONLY performs
     * StAX-based parsing. Line-by-line error recovery is NOT available for InputStreams
     * to avoid buffering the entire stream in memory.
     * 
     * For comprehensive error detection with line-by-line recovery, use parse(File) instead.
     * 
     * This design choice ensures we can handle files of any size (up to 500GB+) when
     * using PatchedInputStream without memory constraints.
     */
    public void parse(InputStream in) {
        // TRUE STREAMING: Only StAX parsing, no mark/reset buffering
        // This ensures zero memory overhead for large files
        tryStaxParsing(in);
        
        // Note: Line-by-line recovery is not performed for InputStreams
        // to maintain streaming behavior. If comprehensive error detection
        // is needed, use parse(File) which can read the file twice.
    }

    private boolean tryStaxParsing(InputStream in) {
        XMLInputFactory factory = XMLInputFactory.newFactory();

        // Configuration de sécurité
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        XMLEventReader reader = null;

        try {
            reader = factory.createXMLEventReader(in);

            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();

                if (event.isStartElement()) {
                    handleStartElement(event.asStartElement());
                } else if (event.isEndElement()) {
                    handleEndElement(event.asEndElement());
                }
            }

            // À la fin du parsing, vérifier les balises non fermées
            checkUnclosedTags();
            return true; // Parsing réussi

        } catch (XMLStreamException ex) {
            handleParseError(ex);
            return false; // Parsing échoué, il faut analyser ligne par ligne

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException e) {
                    // Ignore
                }
            }
        }
    }


    private void performLineByLineAnalysis(java.io.File file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            performLineByLineAnalysis(reader);
        } catch (IOException e) {
            collector.addError("Erreur lors de l'analyse ligne par ligne : " + e.getMessage(), 0, "IO_ERROR");
        }
    }

    private void performLineByLineAnalysis(BufferedReader reader) throws IOException {
            
            String line;
            int lineNumber = 0;
            
            // Buffer pour gérer les balises multi-lignes
            StringBuilder tagBuffer = null;
            int tagStartLine = 0;
            boolean inTag = false;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                String trimmed = line.trim();
                
                // Ignorer les commentaires, déclarations et lignes vides
                if (trimmed.startsWith("<!--") || trimmed.startsWith("<?") || trimmed.isEmpty()) {
                    continue;
                }

                // Si on est dans une balise multi-lignes, continuer à accumuler
                if (inTag && tagBuffer != null) {
                    tagBuffer.append(" ").append(trimmed);
                    
                    // Vérifier si la balise se termine sur cette ligne
                    if (trimmed.contains(">")) {
                        inTag = false;
                        tagBuffer = null;
                    }
                    continue;
                }

                // Détecter toutes les balises (ouvrantes ET fermantes) sur la ligne
                int pos = 0;
                while (pos < line.length()) {
                    int openBracket = line.indexOf('<', pos);
                    if (openBracket == -1) break;
                    
                    // Vérifier si c'est un commentaire ou déclaration
                    if (openBracket + 1 < line.length() && 
                        (line.charAt(openBracket + 1) == '!' ||
                         line.charAt(openBracket + 1) == '?')) {
                        pos = openBracket + 1;
                        continue;
                    }
                    
                    // Déterminer si c'est une balise fermante
                    boolean isClosingTag = false;
                    int tagNameStart = openBracket + 1;
                    
                    if (openBracket + 1 < line.length() && line.charAt(openBracket + 1) == '/') {
                        isClosingTag = true;
                        tagNameStart = openBracket + 2;
                    }
                    
                    // Chercher le '>' correspondant
                    int closeBracket = line.indexOf('>', openBracket);
                    
                    if (closeBracket == -1) {
                        // Pas de '>' trouvé sur cette ligne
                        // Cela peut être :
                        // 1. Une balise mal formée (sans >)
                        // 2. Une balise qui continue sur la ligne suivante
                        
                        // Extraire le nom de la balise
                        String tagPart = line.substring(tagNameStart);
                        String tagName = extractTagName(tagPart);
                        
                        if (tagName != null && !tagName.isEmpty()) {
                            // Vérifier si c'est une balise multi-lignes valide
                            // (contient des attributs qui continuent)
                            if (tagPart.contains("=") && !tagPart.endsWith("/")) {
                                // Probablement une balise multi-lignes
                                inTag = true;
                                tagBuffer = new StringBuilder(tagPart);
                                tagStartLine = lineNumber;
                            } else {
                                // Balise mal formée
                                if (isClosingTag) {
                                    collector.addError(
                                        "Balise fermante </" + tagName + "> mal formée : caractère '>' manquant",
                                        lineNumber,
                                        "SYNTAX"
                                    );
                                } else {
                                    collector.addError(
                                        "Balise <" + tagName + "> mal formée : caractère '>' manquant",
                                        lineNumber,
                                        "SYNTAX"
                                    );
                                }
                            }
                        }
                        break; // Passer à la ligne suivante
                    }
                    
                    pos = closeBracket + 1;
                }
            }
            

            
            // Si on termine avec une balise inachevée
            if (inTag && tagBuffer != null) {
                String tagName = extractTagName(tagBuffer.toString());
                if (tagName != null && !tagName.isEmpty()) {
                    collector.addError(
                        "Balise <" + tagName + "> mal formée : caractère '>' manquant (balise multi-lignes)",
                        tagStartLine,
                        "SYNTAX"
                    );
                }
            }

    }

    /**
     * Extrait le nom de balise d'une chaîne commençant après '<'
     */
    private String extractTagName(String tagPart) {
        StringBuilder tagName = new StringBuilder();
        for (int i = 0; i < tagPart.length(); i++) {
            char c = tagPart.charAt(i);
            if (Character.isWhitespace(c) || c == '>' || c == '/') {
                break;
            }
            if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-' || c == '.') {
                tagName.append(c);
            }
        }
        return tagName.toString();
    }



    private void handleStartElement(javax.xml.stream.events.StartElement startElement) {
        String tagName = startElement.getName().getLocalPart();
        Location location = startElement.getLocation();

        int line = location.getLineNumber();
        int column = location.getColumnNumber();

        //LOG.debug("Balise ouvrante: <{}> à ligne {}, colonne {}", tagName, line, column);

        // Empiler la balise avec sa position
        tagStack.push(new TagInfo(tagName, line, column));
    }

    private void handleEndElement(javax.xml.stream.events.EndElement endElement) {
        String tagName = endElement.getName().getLocalPart();
        Location location = endElement.getLocation();

        //LOG.debug("Balise fermante: </{}> à ligne {}", tagName, location.getLineNumber());

        if (tagStack.isEmpty()) {
            // Balise fermante sans balise ouvrante correspondante
            collector.addError(
                    "Balise fermante </" + tagName + "> sans balise ouvrante correspondante",
                    location.getLineNumber(),
                    "STRUCTURE"
            );
            return;
        }

        TagInfo lastOpenedTag = tagStack.peek();

        if (!lastOpenedTag.name.equals(tagName)) {
            // Mismatch de balises - cela indique souvent une fermeture manquante
            collector.addError(
                    "Balise fermante </" + tagName + "> inattendue. Attendue : </" + lastOpenedTag.name + ">",
                    location.getLineNumber(),
                    "STRUCTURE"
            );

            // On peut essayer de retrouver la balise correspondante dans la stack
            recoverFromMismatch(tagName, location.getLineNumber());
        } else {
            // Balises correspondantes - dépiler normalement
            tagStack.pop();
        }
    }

    private void recoverFromMismatch(String foundTagName, int errorLine) {
        // Rechercher dans la stack si cette balise a été ouverte précédemment
        Deque<TagInfo> tempStack = new ArrayDeque<>();
        boolean found = false;

        while (!tagStack.isEmpty()) {
            TagInfo tag = tagStack.pop();
            tempStack.push(tag);

            if (tag.name.equals(foundTagName)) {
                found = true;
                // Signaler la balise ouvrante non fermée
                collector.addError(
                        "Balise <" + foundTagName + "> non fermée (ouverte à la ligne " + tag.lineNumber + ")",
                        tag.lineNumber,
                        "STRUCTURE"
                );
                break;
            }
        }

        // Remettre les autres balises dans la stack
        while (!tempStack.isEmpty()) {
            TagInfo tag = tempStack.pop();
            if (!tag.name.equals(foundTagName) || !found) {
                tagStack.push(tag);
            }
        }

        if (!found) {
            // Balise fermante orpheline
            collector.addError(
                    "Balise fermante </" + foundTagName + "> sans ouvrante correspondante",
                    errorLine,
                    "STRUCTURE"
            );
        }
    }

    private void checkUnclosedTags() {
        // Après le parsing complet, toutes les balises encore dans la stack sont non fermées
        while (!tagStack.isEmpty()) {
            TagInfo unclosedTag = tagStack.pop();

            String message = String.format(
                    "Balise <%s> non fermée (ouverte à la ligne %d, colonne %d)",
                    unclosedTag.name,
                    unclosedTag.lineNumber,
                    unclosedTag.columnNumber
            );

            collector.addError(
                    message,
                    unclosedTag.lineNumber,  // Ligne de la balise OUVERTE
                    "STRUCTURE"
            );

        }
    }

    private void handleParseError(XMLStreamException ex) {
        Location location = ex.getLocation();
        int line = (location != null) ? location.getLineNumber() : 0;

        String message = ex.getMessage();
        String type = "SYNTAX";

        // Analyser le type d'erreur pour un message plus précis et traduire en français
        if (message != null) {
            if (message.contains("must be terminated by the matching end-tag")) {
                type = "STRUCTURE";
                // Extraire le nom de la balise concernée si possible
                if (message.contains("element type")) {
                    String tagName = extractTagNameFromErrorMessage(message);
                    if (tagName != null && !tagStack.isEmpty()) {
                        // Chercher la dernière balise ouverte correspondante
                        for (TagInfo tag : tagStack) {
                            if (tag.name.equals(tagName)) {
                                message = "Balise <" + tagName + "> non fermée (ouverte à la ligne " +
                                        tag.lineNumber + ", colonne " + tag.columnNumber + ")";
                                line = tag.lineNumber;
                                break;
                            }
                        }
                    } else if (tagName != null) {
                        message = "Balise <" + tagName + "> non fermée correctement";
                    }
                }
            } else if (message.contains("DOCTYPE")) {
                type = "WARNING";
                message = "Déclaration DOCTYPE présente (potentiel risque de sécurité)";
            } else if (message.contains("namespace") || message.contains("Prefix")) {
                type = "NAMESPACE";
                if (message.contains("Prefix")) {
                    message = "Erreur de préfixe d'espace de noms : " + message;
                } else {
                    message = "Erreur d'espace de noms : " + message;
                }
            } else if (message.contains("Illegal processing instruction target")) {
                message = "Instruction de traitement invalide";
            } else if (message.contains("Content is not allowed in prolog")) {
                message = "Contenu non autorisé avant la déclaration XML";
            } else if (message.contains("XML declaration")) {
                message = "Erreur dans la déclaration XML";
            } else if (message.contains("expected")) {
                // Erreur générique "expected '>' but got ..."
                message = "Erreur de syntaxe XML : " + message;
            }
        }

        collector.addError(message, line, type);
    }

    private String extractTagNameFromErrorMessage(String message) {
        // Tente d'extraire le nom de balise d'un message d'erreur comme:
        // "The element type \"book\" must be terminated by the matching end-tag \"</book>\"."
        try {
            int start = message.indexOf('"');
            if (start != -1) {
                int end = message.indexOf('"', start + 1);
                if (end != -1) {
                    return message.substring(start + 1, end);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
