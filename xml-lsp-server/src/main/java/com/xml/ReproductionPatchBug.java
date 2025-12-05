package com.xml;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

import com.xml.handlers.StreamingIndexer;
import com.xml.models.FragmentIndex;
import com.xml.models.FragmentMetadata;
import com.xml.services.FileSaver;
import com.xml.services.PatchManager;
import com.xml.services.PatchedFragmentManager;

public class ReproductionPatchBug {
    public static void main(String[] args) {
        try {
            System.out.println("=== REPRODUCTION TEST ===");
            
            // 1. Create a sample XML file
            File originalFile = new File("repro_test.xml");
            try (FileWriter fw = new FileWriter(originalFile)) {
                fw.write("<root>\n");
                fw.write("    <item id=\"1\">Original Content</item>\n");
                fw.write("</root>");
            }
            System.out.println("Created " + originalFile.getAbsolutePath());

            // 2. Index the file
            FragmentIndex index = new FragmentIndex();
            StreamingIndexer indexer = new StreamingIndexer(index);
            indexer.indexFile(originalFile);
            System.out.println("Indexed " + index.getAllFragments().size() + " fragments");

            // 3. Setup PatchManager and FragmentManager
            PatchManager patchManager = new PatchManager(originalFile.getAbsoluteFile().getParentFile().toPath());
            // Clear previous journal
            patchManager.clearAll();
            
            PatchedFragmentManager manager = new PatchedFragmentManager(originalFile, patchManager, index);

            // 4. Get fragment
            FragmentMetadata frag = index.getFragmentById("1"); // Assuming ID is "1" based on logic or just get first
            if (frag == null) {
                // If ID strategy is different, get the first one
                frag = index.getAllFragments().get(0);
            }
            System.out.println("Target Fragment: " + frag.getId());

            // 5. Update fragment (Simulate user edit)
            String newContent = "<item id=\"1\">Modified Content with Backslash \\</item>";
            manager.updateFragment(frag.getId(), newContent);
            System.out.println("Updated fragment with: " + newContent);

            // Verify in-memory content
            String currentContent = manager.getFragmentContent(frag);
            System.out.println("In-memory content: " + currentContent);
            if (!currentContent.equals(newContent)) {
                System.err.println("ERROR: In-memory update failed!");
            }

            // 6. Simulate "closing tab" (nothing happens on server usually, but let's assume persistence check)
            // If we reload PatchManager from journal, does it work?
            System.out.println("Simulating server restart / journal reload...");
            PatchManager newPatchManager = new PatchManager(originalFile.getAbsoluteFile().getParentFile().toPath());
            if (newPatchManager.getPatchCount() == 0) {
                System.err.println("WARNING: Patches lost after reload! Journal issue?");
            } else {
                System.out.println("Patches loaded from journal: " + newPatchManager.getPatchCount());
            }

            // 7. Save file
            File outputFile = new File("repro_output.xml");
            FileSaver saver = new FileSaver();
            // Use the original patchManager (mimicking session persistence) or new one (mimicking restart)
            // The user says "close tab", not restart server. So likely original patchManager.
            saver.saveWithPatches(originalFile, outputFile, index, patchManager);
            
            // 8. Verify output
            String outputContent = new String(Files.readAllBytes(outputFile.toPath()));
            System.out.println("Output Content:\n" + outputContent);

            // Extract the item content to verify exact match
            int start = outputContent.indexOf("<item id=\"1\">");
            int end = outputContent.indexOf("</item>");
            if (start != -1 && end != -1) {
                String extracted = outputContent.substring(start, end + 7);
                System.out.println("Extracted: " + extracted);
                if (extracted.equals(newContent)) {
                     System.out.println("SUCCESS: Patch applied correctly (Exact match).");
                } else {
                     System.out.println("FAILURE: Content mismatch! Expected: " + newContent + ", Got: " + extracted);
                }
            } else {
                System.out.println("FAILURE: Could not find item tag.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
