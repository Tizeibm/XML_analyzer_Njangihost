package com.xml;

import com.xml.handlers.StreamingIndexer;
import com.xml.models.FragmentIndex;
import com.xml.models.FragmentMetadata;
import com.xml.services.FragmentManager;

import java.io.File;
import java.io.IOException;

public class FragmentManagerTest {
    public static void main(String[] args) {
        try {
            File file = new File("books_1mb.xml");
            
            // 1. Indexation
            System.out.println("Indexing...");
            FragmentIndex index = new FragmentIndex();
            StreamingIndexer indexer = new StreamingIndexer(index);
            indexer.indexFile(file);
            
            // 2. Fragment Manager
            FragmentManager manager = new FragmentManager(file);
            
            // 3. Test de lecture
            System.out.println("Reading fragments...");
            for (FragmentMetadata frag : index.getAllFragments()) {
                System.out.println("--------------------------------------------------");
                System.out.println("Fragment: " + frag.getId());
                String content = manager.getFragmentContent(frag);
                System.out.println("Content Length: " + content.length());
                System.out.println("Preview: " + content.substring(0, Math.min(50, content.length())).replace("\n", " "));
                
                // Vérifier que le contenu commence bien par <
                if (!content.trim().startsWith("<")) {
                    System.err.println("ERROR: Fragment content does not start with '<'");
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
