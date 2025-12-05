package com.xml.services;

import com.xml.models.FragmentMetadata;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gère l'accès aux fragments XML sur le disque.
 * Utilise RandomAccessFile pour une lecture rapide et thread-safe.
 * Intègre un cache LRU pour les fragments fréquemment accédés.
 */
public class FragmentManager {

    private final File file;
    private final Map<String, String> cache;
    private static final int CACHE_SIZE = 50; // Nombre de fragments en cache

    public FragmentManager(File file) {
        this.file = file;
        
        // Cache LRU simple thread-safe
        this.cache = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            }
        );
    }

    /**
     * Récupère le contenu textuel d'un fragment.
     * Utilise le cache si disponible, sinon lit sur le disque.
     */
    public String getFragmentContent(FragmentMetadata fragment) throws IOException {
        if (cache.containsKey(fragment.getId())) {
            return cache.get(fragment.getId());
        }

        String content = readFromDisk(fragment);
        cache.put(fragment.getId(), content);
        return content;
    }

    /**
     * Lit les octets bruts du fragment depuis le fichier.
     */
    private String readFromDisk(FragmentMetadata fragment) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = fragment.getLength();
            if (length > Integer.MAX_VALUE) {
                throw new IOException("Fragment trop grand pour être chargé en mémoire : " + length);
            }

            byte[] buffer = new byte[(int) length];
            raf.seek(fragment.getStartOffset());
            raf.readFully(buffer);

            return new String(buffer, StandardCharsets.UTF_8);
        }
    }

    /**
     * Vide le cache (utile si le fichier change ou pour libérer la mémoire).
     */
    public void clearCache() {
        cache.clear();
    }
}
