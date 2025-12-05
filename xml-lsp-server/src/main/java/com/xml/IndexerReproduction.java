package com.xml;

import com.xml.handlers.StreamingIndexer;
import com.xml.models.FragmentIndex;
import com.xml.models.FragmentMetadata;

import java.io.File;

public class IndexerReproduction {
    public static void main(String[] args) {
        FragmentIndex index = new FragmentIndex();
        StreamingIndexer indexer = new StreamingIndexer(index);
        
        File file = new File("books_1mb.xml");
        System.out.println("Indexing " + file.getAbsolutePath());
        
        long start = System.currentTimeMillis();
        indexer.indexFile(file);
        long end = System.currentTimeMillis();
        
        System.out.println("Indexing took " + (end - start) + "ms");
        System.out.println("Fragments found: " + index.size());
        
        for (FragmentMetadata frag : index.getAllFragments()) {
            System.out.println(frag);
        }
    }
}
