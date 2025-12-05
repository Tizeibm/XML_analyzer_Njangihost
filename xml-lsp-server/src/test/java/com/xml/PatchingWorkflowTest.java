package com.xml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.xml.models.Patch;
import com.xml.models.PatchType;
import com.xml.services.FileSaver;
import com.xml.services.PatchManager;

public class PatchingWorkflowTest {

    private Path tempDir;
    private PatchManager patchManager;
    private FileSaver fileSaver;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("patch-test");
        patchManager = new PatchManager(tempDir);
        fileSaver = new FileSaver();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Cleanup
        patchManager.clearAll();
        Files.walk(tempDir)
                .map(Path::toFile)
                .sorted((o1, o2) -> -o1.compareTo(o2))
                .forEach(File::delete);
    }

    @Test
    void testPatchAdditionAndSorting() {
        Patch p1 = new Patch(100, 110, "replacement1", PatchType.REPLACE, "f1");
        Patch p2 = new Patch(50, 60, "replacement2", PatchType.REPLACE, "f2");
        Patch p3 = new Patch(150, 150, "insertion", PatchType.INSERT, "f3");

        patchManager.addPatch(p1);
        patchManager.addPatch(p2);
        patchManager.addPatch(p3);

        List<Patch> sorted = patchManager.getAllPatchesSorted();
        assertEquals(3, sorted.size());
        assertEquals(p2, sorted.get(0)); // 50
        assertEquals(p1, sorted.get(1)); // 100
        assertEquals(p3, sorted.get(2)); // 150
    }

    @Test
    void testConflictResolution() {
        // Patch original: [100, 120)
        Patch p1 = new Patch(100, 120, "original", PatchType.REPLACE, "f1");
        patchManager.addPatch(p1);

        // Patch conflictuel: [110, 130) -> chevauche la fin de p1
        Patch p2 = new Patch(110, 130, "overlap", PatchType.REPLACE, "f1");
        patchManager.addPatch(p2);

        List<Patch> sorted = patchManager.getAllPatchesSorted();
        assertEquals(1, sorted.size());
        assertEquals(p2, sorted.get(0)); // Le dernier gagne
    }

    @Test
    void testPersistence() {
        Patch p1 = new Patch(100, 110, "persistent", PatchType.REPLACE, "f1");
        patchManager.addPatch(p1);

        // Simuler un redémarrage
        PatchManager newManager = new PatchManager(tempDir);
        List<Patch> loaded = newManager.getAllPatchesSorted();

        assertEquals(1, loaded.size());
        assertEquals(p1, loaded.get(0));
    }

    @Test
    void testFileSaving() throws IOException {
        // Créer un fichier XML dummy
        Path xmlPath = tempDir.resolve("test.xml");
        String content = "<root><item>Original</item><item>ToDelete</item></root>";
        Files.writeString(xmlPath, content);

        // Patch 1: Remplacer "Original" par "Patched"
        // <root><item>Original</item>...
        // 01234567890123456789012
        // "Original" est à l'offset 12 (longueur 8)
        Patch p1 = new Patch(12, 20, "Patched", PatchType.REPLACE, "f1");
        patchManager.addPatch(p1);

        // Patch 2: Supprimer le deuxième item
        // <item>ToDelete</item> commence après </item> (offset 27)
        // <item>ToDelete</item> -> offset 27 à 48 (longueur 21)
        Patch p2 = new Patch(27, 48, "", PatchType.DELETE, "f2");
        patchManager.addPatch(p2);

        // Sauvegarder
        fileSaver.saveWithPatches(xmlPath.toFile(), xmlPath.toFile(), null, patchManager);

        // Vérifier le contenu
        String newContent = Files.readString(xmlPath);
        String expected = "<root><item>Patched</item></root>";
        assertEquals(expected, newContent);
    }
}
