package com.xml.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Index en mémoire optimisé (Lightweight).
 * Gère le StringPool et la liste des fragments.
 */
public class FragmentIndex {
    // Liste principale (accès par index = ID implicite)
    private final List<FragmentMetadata> fragments = Collections.synchronizedList(new ArrayList<>());
    
    // String Pool pour déduplication des noms de balises
    private final Map<String, Integer> stringToId = new ConcurrentHashMap<>();
    private final Map<Integer, String> idToString = new ConcurrentHashMap<>();
    private int nextStringId = 0;

    public void addFragment(FragmentMetadata fragment) {
        fragments.add(fragment);
    }

    public FragmentMetadata getFragment(int index) {
        if (index < 0 || index >= fragments.size()) return null;
        return fragments.get(index);
    }
    
    // Compatibilité temporaire : ID string "frag_X" -> int X
    public FragmentMetadata getFragmentById(String id) {
        try {
            if (id.startsWith("frag_")) {
                int index = Integer.parseInt(id.substring(5));
                return getFragment(index);
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }

    public List<FragmentMetadata> getAllFragments() {
        return List.copyOf(fragments);
    }

    /**
     * Trouve le fragment contenant l'offset donné.
     * Recherche binaire optimisée.
     */
    public FragmentMetadata findFragmentAtOffset(long offset) {
        synchronized (fragments) {
            // TODO: Vraie recherche binaire. Pour l'instant linéaire safe.
            // Note: Les fragments sont ajoutés dans l'ordre, donc triés par startOffset.
            int low = 0;
            int high = fragments.size() - 1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                FragmentMetadata midVal = fragments.get(mid);

                if (midVal.getEndOffset() <= offset)
                    low = mid + 1;
                else if (midVal.getStartOffset() > offset)
                    high = mid - 1;
                else
                    return midVal; // Trouvé
            }
        }
        return null;
    }

    public FragmentMetadata getFragmentForLine(int line) {
        synchronized (fragments) {
            // Recherche binaire possible aussi
            for (FragmentMetadata frag : fragments) {
                if (line >= frag.getStartLine() && line <= frag.getEndLine()) {
                    return frag;
                }
            }
        }
        return null;
    }

    // === String Pool Management ===

    public int internString(String s) {
        if (s == null) return -1;
        return stringToId.computeIfAbsent(s, k -> {
            int id = nextStringId++;
            idToString.put(id, k);
            return id;
        });
    }

    public String getString(int id) {
        return idToString.get(id);
    }

    public void clear() {
        fragments.clear();
        stringToId.clear();
        idToString.clear();
        nextStringId = 0;
    }

    public int size() {
        return fragments.size();
    }
}
