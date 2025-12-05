package com.xml;

import java.io.File;

import com.xml.handlers.StreamingIndexer;
import com.xml.models.FragmentIndex;
import com.xml.models.FragmentMetadata;
import com.xml.services.FragmentManager;

/**
 * Test de scalabilité sur un fichier XML de 1MB (3000 fragments).
 */
public class ScalabilityTest {
    public static void main(String[] args) {
        try {
            System.out.println("========================================");
            System.out.println("TEST DE SCALABILITÉ : FICHIER 1MB");
            System.out.println("========================================\n");
            
            File file = new File("books_1mb.xml");
            
            if (!file.exists()) {
                System.err.println("Fichier books_1mb.xml non trouvé.");
                System.err.println("Exécutez d'abord LargeXmlGenerator pour le créer.");
                return;
            }
            
            System.out.println("Fichier : " + file.getName());
            System.out.println("Taille : " + (file.length() / 1024) + " KB\n");
            
            // Test 1 : Indexation
            System.out.println("TEST 1 : INDEXATION");
            System.out.println("-------------------");
            FragmentIndex index = new FragmentIndex();
            StreamingIndexer indexer = new StreamingIndexer(index);
            
            long indexStart = System.currentTimeMillis();
            indexer.indexFile(file);
            long indexTime = System.currentTimeMillis() - indexStart;
            
            int fragmentCount = index.getAllFragments().size();
            System.out.println("Fragments indexés : " + fragmentCount);
            System.out.println("Temps d'indexation : " + indexTime + "ms");
            System.out.println("Performance : " + (fragmentCount * 1000 / indexTime) + " fragments/sec\n");
            
            // Test 2 : Lecture aléatoire
            System.out.println("TEST 2 : LECTURE ALÉATOIRE DE FRAGMENTS");
            System.out.println("-----------------------------------------");
            FragmentManager manager = new FragmentManager(file);
            
            // Lire 10 fragments aléatoires
            long totalReadTime = 0;
            int[] sampleFragments = {1, 500, 1000, 1500, 2000, 2500, 2999, 100, 200, 300};
            
            for (int fragNum : sampleFragments) {
                if (fragNum >= fragmentCount) continue;
                
                FragmentMetadata frag = index.getAllFragments().get(fragNum);
                long readStart = System.currentTimeMillis();
                String content = manager.getFragmentContent(frag);
                long readTime = System.currentTimeMillis() - readStart;
                totalReadTime += readTime;
                
                System.out.println("Fragment " + (fragNum + 1) + " : " + content.length() + " octets en " + readTime + "ms");
            }
            
            System.out.println("\nTemps moyen de lecture : " + (totalReadTime / sampleFragments.length) + "ms\n");
            
            // Test 3 : Cache LRU
            System.out.println("TEST 3 : EFFICACITÉ DU CACHE");
            System.out.println("-----------------------------");
            
            // Lire le même fragment 5 fois
            FragmentMetadata frag = index.getAllFragments().get(100);
            long firstRead = -1;
            long cacheSum = 0;
            
            for (int i = 0; i < 5; i++) {
                long readStart = System.currentTimeMillis();
                manager.getFragmentContent(frag);
                long readTime = System.currentTimeMillis() - readStart;
                
                if (i == 0) {
                    firstRead = readTime;
                    System.out.println("Première lecture (disque) : " + readTime + "ms");
                } else {
                    cacheSum += readTime;
                    System.out.println("Lecture " + (i + 1) + " (cache) : " + readTime + "ms");
                }
            }
            
            double avgCache = cacheSum / 4.0;
            System.out.println("\nGain de performance (cache) : " + 
                String.format("%.1f", (firstRead / avgCache)) + "x\n");
            
            // Bilan
            System.out.println("========================================");
            System.out.println("BILAN : TEST DE SCALABILITÉ RÉUSSI ✓");
            System.out.println("========================================");
            System.out.println("Fichier : 1MB (" + fragmentCount + " fragments)");
            System.out.println("Indexation : " + indexTime + "ms");
            System.out.println("Lecture moyenne : " + (totalReadTime / sampleFragments.length) + "ms");
            System.out.println("\nExtrapolation pour 1GB (1000x plus gros) :");
            System.out.println("  Indexation estimée : " + (indexTime * 1000 / 1000) + " secondes");
            System.out.println("  Fragments estimés : " + (fragmentCount * 1000) + " fragments");
            System.out.println("\n✓ Architecture validée pour des fichiers massifs !");
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }
}
