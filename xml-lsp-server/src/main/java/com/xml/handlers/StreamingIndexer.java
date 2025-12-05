package com.xml.handlers;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

import com.xml.models.FragmentIndex;
import com.xml.models.FragmentMetadata;

/**
 * Indexeur lexical robuste pour fichiers XML massifs.
 * Utilise une machine à états pour scanner la structure sans validation stricte.
 * Tolère les erreurs de syntaxe (attributs mal formés, contenu invalide) tant que la structure des balises est lisible.
 */
public class StreamingIndexer {

    private final FragmentIndex index;
    
    // State tracking for incremental parsing
    private final Deque<TagInfo> tagStack = new ArrayDeque<>();
    private long currentGlobalOffset = 0;
    private String lastIncompleteFragmentName = null;

    /**
     * Internal class to track tag information during parsing.
     */
    public static class TagInfo {
        public final String name;
        public final long offset;
        public final int line;
        
        public TagInfo(String name, long offset, int line) {
            this.name = name;
            this.offset = offset;
            this.line = line;
        }
        
        @Override
        public String toString() {
            return String.format("Tag<%s> at offset %d, line %d", name, offset, line);
        }
    }

    public StreamingIndexer(FragmentIndex index) {
        this.index = index;
    }
    
    /**
     * Get a copy of the current tag stack for debugging.
     * Returns open tags that haven't been closed yet.
     */
    public Deque<TagInfo> getTagStack() {
        return new ArrayDeque<>(tagStack);
    }
    
    /**
     * Get the current global offset in the file being parsed.
     */
    public long getCurrentGlobalOffset() {
        return currentGlobalOffset;
    }
    
    /**
     * Get the name of the last incomplete fragment encountered.
     * Useful for error recovery.
     */
    public String getLastIncompleteFragmentName() {
        return lastIncompleteFragmentName;
    }

    private enum State {
        CONTENT,
        TAG_START,      // Après '<'
        TAG_NAME,       // Dans le nom de la balise
        WAIT_GT,        // Dans la balise, attente de '>'
        IN_QUOTE_SINGLE,// Dans un attribut '...'
        IN_QUOTE_DOUBLE,// Dans un attribut "..."
        COMMENT_START,  // Après '<!'
        COMMENT,        // Dans <!-- ... -->
        CDATA,          // Dans <![CDATA[ ... ]]>
        PI              // Dans <? ... ?>
    }

    public void indexFile(File file) {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            long offset = 0;
            int currentLine = 1; // 1-based line numbering
            int depth = 0;
            int b;
            
            State state = State.CONTENT;
            StringBuilder tagNameBuffer = new StringBuilder();
            
            // État pour le fragment courant
            long fragmentStart = -1;
            int fragmentStartLine = -1;
            String fragmentName = null;
            int fragmentCount = 0;
            int fragmentSplitCount = 0; // Track sub-chunks for dynamic fragmentation
            
            // État temporaire pour la balise en cours
            long currentTagStart = -1;
            int currentTagStartLine = -1;
            boolean isClosingTag = false;
            boolean isSelfClosing = false;
            
            // Buffers pour détection de fin de commentaire/CDATA
            int dashCount = 0; // Pour -->
            int bracketCount = 0; // Pour ]]>

            while ((b = in.read()) != -1) {
                char c = (char) b;
                
                if (c == '\n') {
                    currentLine++;
                }
                
                switch (state) {
                    case CONTENT:
                        if (c == '<') {
                            state = State.TAG_START;
                            currentTagStart = offset;
                            currentTagStartLine = currentLine;
                            tagNameBuffer.setLength(0);
                            isClosingTag = false;
                            isSelfClosing = false;
                        }
                        break;
                        
                    case TAG_START:
                        if (c == '/') {
                            isClosingTag = true;
                            state = State.TAG_NAME;
                        } else if (c == '?') {
                            state = State.PI;
                        } else if (c == '!') {
                            state = State.COMMENT_START;
                        } else if (Character.isWhitespace(c)) {
                            // < espace : invalide mais on ignore et retourne au contenu
                            state = State.CONTENT; 
                        } else {
                            // Début du nom de balise
                            tagNameBuffer.append(c);
                            state = State.TAG_NAME;
                        }
                        break;
                        
                    case TAG_NAME:
                        if (Character.isWhitespace(c)) {
                            state = State.WAIT_GT;
                        } else if (c == '>') {
                            // Fin de balise directe (ex: <tag>)
                            processTagEnd(tagNameBuffer.toString(), isClosingTag, isSelfClosing, offset, currentTagStart, 
                                          depth, fragmentStart, fragmentName, fragmentCount);
                            
                            if (isClosingTag) {
                                depth--;
                                if (depth == 1 && fragmentStart != -1) {
                                    byte flags = fragmentSplitCount > 0 ? FragmentMetadata.FLAG_CONTINUATION : 0;
                                    indexFragment(fragmentName, ++fragmentCount, fragmentStart, offset + 1, fragmentStartLine, currentLine, flags);
                                    fragmentStart = -1;
                                    fragmentName = null;
                                    fragmentSplitCount = 0; // Reset for next fragment
                                }
                            } else {
                                if (depth == 1) {
                                    fragmentStart = currentTagStart;
                                    fragmentStartLine = currentTagStartLine;
                                    fragmentName = tagNameBuffer.toString();
                                    fragmentSplitCount = 0; // Reset for new fragment
                                }
                                depth++;
                            }
                            state = State.CONTENT;
                        } else if (c == '/') {
                            state = State.WAIT_GT;
                            isSelfClosing = true;
                        } else if (c == '<') {
                            // RECOVERY: Balise précédente mal formée (manque '>'), nouvelle balise trouvée.
                            processTagEnd(tagNameBuffer.toString(), isClosingTag, isSelfClosing, offset, currentTagStart, 
                                          depth, fragmentStart, fragmentName, fragmentCount);
                            
                            if (isClosingTag) {
                                depth--;
                                if (depth == 1 && fragmentStart != -1) {
                                    indexFragment(fragmentName, ++fragmentCount, fragmentStart, offset, fragmentStartLine, currentLine);
                                    fragmentStart = -1;
                                    fragmentName = null;
                                }
                            } else {
                                if (depth == 1) {
                                    fragmentStart = currentTagStart;
                                    fragmentStartLine = currentTagStartLine;
                                    fragmentName = tagNameBuffer.toString();
                                }
                                depth++;
                            }
                            
                            // Démarrer la nouvelle balise
                            state = State.TAG_START;
                            currentTagStart = offset;
                            currentTagStartLine = currentLine;
                            tagNameBuffer.setLength(0);
                            isClosingTag = false;
                            isSelfClosing = false;
                        } else {
                            tagNameBuffer.append(c);
                        }
                        break;
                        
                    case WAIT_GT:
                        if (c == '>') {
                            processTagEnd(tagNameBuffer.toString(), isClosingTag, isSelfClosing, offset, currentTagStart, 
                                          depth, fragmentStart, fragmentName, fragmentCount);
                            
                            if (isClosingTag) {
                                depth--;
                                if (depth == 1 && fragmentStart != -1) {
                                    indexFragment(fragmentName, ++fragmentCount, fragmentStart, offset + 1, fragmentStartLine, currentLine);
                                    fragmentStart = -1;
                                    fragmentName = null;
                                }
                            } else {
                                if (isSelfClosing) {
                                     if (depth == 1) {
                                        indexFragment(tagNameBuffer.toString(), ++fragmentCount, currentTagStart, offset + 1, currentTagStartLine, currentLine);
                                     }
                                } else {
                                    if (depth == 1) {
                                        fragmentStart = currentTagStart;
                                        fragmentStartLine = currentTagStartLine;
                                        fragmentName = tagNameBuffer.toString();
                                    }
                                    depth++;
                                }
                            }
                            state = State.CONTENT;
                        } else if (c == '<') {
                            // RECOVERY: Balise précédente mal formée (manque '>'), nouvelle balise trouvée.
                            processTagEnd(tagNameBuffer.toString(), isClosingTag, isSelfClosing, offset, currentTagStart, 
                                          depth, fragmentStart, fragmentName, fragmentCount);
                            
                            if (isClosingTag) {
                                depth--;
                                if (depth == 1 && fragmentStart != -1) {
                                    indexFragment(fragmentName, ++fragmentCount, fragmentStart, offset, fragmentStartLine, currentLine);
                                    fragmentStart = -1;
                                    fragmentName = null;
                                }
                            } else {
                                if (isSelfClosing) {
                                     if (depth == 1) {
                                        indexFragment(tagNameBuffer.toString(), ++fragmentCount, currentTagStart, offset, currentTagStartLine, currentLine);
                                     }
                                } else {
                                    if (depth == 1) {
                                        fragmentStart = currentTagStart;
                                        fragmentStartLine = currentTagStartLine;
                                        fragmentName = tagNameBuffer.toString();
                                    }
                                    depth++;
                                }
                            }
                            
                            // Démarrer la nouvelle balise
                            state = State.TAG_START;
                            currentTagStart = offset;
                            currentTagStartLine = currentLine;
                            tagNameBuffer.setLength(0);
                            isClosingTag = false;
                            isSelfClosing = false;
                            
                        } else if (c == '"') {
                            state = State.IN_QUOTE_DOUBLE;
                        } else if (c == '\'') {
                            state = State.IN_QUOTE_SINGLE;
                        } else if (c == '/') {
                            isSelfClosing = true;
                        }
                        break;
                        
                    case IN_QUOTE_DOUBLE:
                        if (c == '"') state = State.WAIT_GT;
                        break;
                        
                    case IN_QUOTE_SINGLE:
                        if (c == '\'') state = State.WAIT_GT;
                        break;
                        
                    case PI:
                        if (c == '>') state = State.CONTENT;
                        break;
                        
                    case COMMENT_START:
                        if (c == '-') {
                            state = State.COMMENT;
                            dashCount = 0;
                        } else if (c == '[') {
                            state = State.CDATA;
                            bracketCount = 0;
                        } else {
                            state = State.PI; 
                        }
                        break;
                        
                    case COMMENT:
                        if (c == '-') {
                            dashCount++;
                        } else if (c == '>') {
                            if (dashCount >= 2) state = State.CONTENT;
                            dashCount = 0;
                        } else {
                            dashCount = 0;
                        }
                        break;
                        
                    case CDATA:
                        if (c == ']') {
                            bracketCount++;
                        } else if (c == '>') {
                            if (bracketCount >= 2) state = State.CONTENT;
                            bracketCount = 0;
                        } else {
                            bracketCount = 0;
                        }
                        break;
                }
                
                offset++;
                
                // Dynamic Fragmentation Check
                // If we are in a fragment (depth 1) and it exceeds the threshold, split it.
                if (fragmentStart != -1 && (offset - fragmentStart) > MAX_FRAGMENT_SIZE) {
                    // Emit a chunk - this is NOT the first part anymore, so set Flag
                    // Actually, the FIRST chunk we emit (the beginning of the element) should NOT have FLAG_CONTINUATION.
                    // The second, third etc. chunks SHOULD have FLAG_CONTINUATION.
                    // We need to track how many chunks we already emitted for THIS fragment.
                    
                    // `fragmentCount` is already incremented for each fragment.
                    // But we need LOCAL tracking for sub-chunks of a single element.
                    // Let's use a simple boolean: isContinuation = true after first emit.
                    
                    boolean isContinuation = (fragmentSplitCount > 0);
                    byte flags = isContinuation ? FragmentMetadata.FLAG_CONTINUATION : 0;
                    
                    indexFragment(fragmentName, ++fragmentCount, fragmentStart, offset, fragmentStartLine, currentLine, flags);
                    fragmentSplitCount++;
                    
                    // Start next chunk
                    fragmentStart = offset;
                    fragmentStartLine = currentLine;
                }
            }
            
            // Update final state
            currentGlobalOffset = offset;
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Stack to track parent indices
    private final Deque<Integer> parentIndexStack = new ArrayDeque<>();
    
    // Configuration
    private static final long MAX_FRAGMENT_SIZE = 5 * 1024 * 1024; // 5MB threshold

    private void processTagEnd(String tagName, boolean closing, boolean selfClosing, long offset, long tagStart,
                               int depth, long fragStart, String fragName, int count) {
        // Logic handled in the main loop mostly, but we need to manage the parent stack here
    }
    
    private void indexFragment(String name, int count, long start, long end, int startLine, int endLine) {
        indexFragment(name, count, start, end, startLine, endLine, (byte)0);
    }

    private void indexFragment(String name, int count, long start, long end, int startLine, int endLine, byte flags) {
        int tagId = index.internString(name);
        
        // Determine parent
        int parentIndex = -1;
        if (!parentIndexStack.isEmpty()) {
            parentIndex = parentIndexStack.peek();
        }
        
        // Current fragment index will be the next one added
        int currentIndex = index.size();
        
        FragmentMetadata fragment = new FragmentMetadata(
            currentIndex, start, end, startLine, endLine,
            parentIndex, tagId, parentIndexStack.size(), flags // depth is stack size
        );
        
        index.addFragment(fragment);
    }
}
