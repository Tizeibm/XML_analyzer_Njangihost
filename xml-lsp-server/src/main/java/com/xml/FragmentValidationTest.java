package com.xml;

import java.io.File;

import com.xml.handlers.StreamingIndexer;
import com.xml.models.FragmentIndex;
import com.xml.models.FragmentMetadata;
import com.xml.models.ValidationResult;
import com.xml.services.FragmentManager;
import com.xml.services.FragmentValidator;

public class FragmentValidationTest {
    public static void main(String[] args) {
        try {
            System.out.println("Fragment Validation Test");
            System.out.println("========================");
            
            File xmlFile = new File("books_1mb.xml");
            File xsdFile = new File("books.xsd");
            
            // 1. Indexation
            FragmentIndex index = new FragmentIndex();
            StreamingIndexer indexer = new StreamingIndexer(index);
            indexer.indexFile(xmlFile);
            
            // 2. Récupération d'un fragment
            FragmentManager manager = new FragmentManager(xmlFile);
            FragmentMetadata frag = index.getFragmentById("frag_1");
            
            if (frag == null) {
                System.err.println("Fragment frag_1 not found!");
                return;
            }
            
            String content = manager.getFragmentContent(frag);
            System.out.println("Fragment Content:");
            System.out.println(content.substring(0, Math.min(100, content.length())));
            System.out.println("...");
            
            // 3. Validation du fragment
            FragmentValidator validator = new FragmentValidator();
            
            // Test 1: Sans wrapper
            System.out.println("\n--- Test 1: Validation sans wrapper ---");
            ValidationResult result1 = validator.validateFragment(content, xsdFile, false);
            System.out.println("Valid: " + result1.isSuccess());
            System.out.println("Errors: " + result1.getErrors().size());
            if (!result1.getErrors().isEmpty()) {
                System.out.println("First error: " + result1.getErrors().get(0).getMessage());
            }
            
            // Test 2: Avec wrapper
            System.out.println("\n--- Test 2: Validation avec wrapper <root> ---");
            ValidationResult result2 = validator.validateFragment(content, xsdFile, true);
            System.out.println("Valid: " + result2.isSuccess());
            System.out.println("Errors: " + result2.getErrors().size());
            if (!result2.getErrors().isEmpty()) {
                System.out.println("First error: " + result2.getErrors().get(0).getMessage());
            }
            
            System.out.println("\nTest completed successfully!");
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }
}
