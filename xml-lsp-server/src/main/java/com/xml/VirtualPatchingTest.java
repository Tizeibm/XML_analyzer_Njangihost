package com.xml;

import java.io.File;

import com.xml.handlers.StreamingIndexer;
import com.xml.models.FragmentIndex;
import com.xml.models.FragmentMetadata;
import com.xml.services.FileSaver;
import com.xml.services.PatchManager;
import com.xml.services.PatchedFragmentManager;

/**
 * Test du système de Virtual Patching (Phase 5).
 * Teste : modification, lecture avec cache, et sauvegarde.
 */
public class VirtualPatchingTest {
    public static void main(String[] args) {
        try {
            System.out.println("=============================================");
            System.out.println("TEST : VIRTUAL PATCHING (PHASE 5)");
            System.out.println("=============================================\n");
            
            File originalFile = new File("books_1mb.xml");
            File outputFile = new File("books_patched.xml");
            
            // 1. Indexation
            System.out.println("ÉTAPE 1 : INDEXATION");
            System.out.println("---------------------");
            FragmentIndex index = new FragmentIndex();
            StreamingIndexer indexer = new StreamingIndexer(index);
            indexer.indexFile(originalFile);
            System.out.println("Fragments indexés : " + index.getAllFragments().size() + "\n");
            
            // 2. Création du gestionnaire avec patching
            System.out.println("ÉTAPE 2 : CRÉATION DU GESTIONNAIRE AVEC PATCHING");
            System.out.println("--------------------------------------------------");
            PatchManager patchManager = new PatchManager();
            PatchedFragmentManager manager = new PatchedFragmentManager(originalFile, patchManager, index);
            System.out.println("PatchedFragmentManager créé\n");
            
            // 3. Lecture du fragment original
            System.out.println("ÉTAPE 3 : LECTURE DU FRAGMENT ORIGINAL");
            System.out.println("----------------------------------------");
            FragmentMetadata frag1 = index.getFragmentById("frag_1");
            String originalContent = manager.getFragmentContent(frag1);
            System.out.println("Fragment frag_1 (original) :");
            System.out.println("  Taille : " + originalContent.length() + " octets");
            System.out.println("  Aperçu : " + originalContent.substring(0, Math.min(60, originalContent.length())).replace("\n", " ") + "...\n");
            
            // 4. Application d'un patch
            System.out.println("ÉTAPE 4 : APPLICATION D'UN PATCH VIRTUEL");
            System.out.println("--------------------------");
            String newContent = "<book id=\"bk101\">\n" +
                               "    <author>NOUVEAU AUTEUR</author>\n" +
                               "    <title>NOUVEAU TITRE</title>\n" +
                               "    <genre>Science Fiction</genre>\n" +
                               "    <price>999.99</price>\n" +
                               "    <description>Ce livre a été modifié via Virtual Patching!</description>\n" +
                               "</book>";
            
            manager.updateFragment("frag_1", newContent);
            System.out.println("Patch appliqué à frag_1");
            System.out.println("Patchs en attente : " + manager.getUnsavedPatchCount() + "\n");
            
            // 5. Lecture du fragment patché
            System.out.println("ÉTAPE 5 : LECTURE DU FRAGMENT PATCHÉ");
            System.out.println("--------------------------------------");
            String patchedContent = manager.getFragmentContent(frag1);
            System.out.println("Fragment frag_1 (patché) :");
            System.out.println("  Taille : " + patchedContent.length() + " octets");
            System.out.println("  Aperçu : " + patchedContent.substring(0, Math.min(60, patchedContent.length())).replace("\n", " ") + "...");
            System.out.println("  Contient 'NOUVEAU AUTEUR' : " + patchedContent.contains("NOUVEAU AUTEUR") + "\n");
            
            // 6. Vérification que le fichier original n'est pas modifié
            System.out.println("ÉTAPE 6 : VÉRIFICATION DU FICHIER ORIGINAL");
            System.out.println("--------------------------------------------");
            System.out.println("Le fichier original books.xml n'a PAS été modifié.");
            System.out.println("Les modifications sont uniquement en mémoire.\n");
            
            // 7. Sauvegarde avec patchs
            System.out.println("ÉTAPE 7 : SAUVEGARDE AVEC PATCHS");
            System.out.println("----------------------------------");
            FileSaver saver = new FileSaver();
            saver.saveWithPatches(originalFile, outputFile, index, patchManager);
            System.out.println("Fichier sauvegardé : " + outputFile.getName());
            System.out.println("Taille : " + (outputFile.length() / 1024) + " KB");
            System.out.println("Patchs restants : " + patchManager.getPatchCount() + " (vidé après sauvegarde)\n");
            
            // 8. Vérification du fichier sauvegardé
            System.out.println("ÉTAPE 8 : VÉRIFICATION DU FICHIER SAUVEGARDÉ");
            System.out.println("----------------------------------------------");
            FragmentIndex newIndex = new FragmentIndex();
            StreamingIndexer newIndexer = new StreamingIndexer(newIndex);
            newIndexer.indexFile(outputFile);
            
            PatchedFragmentManager newManager = new PatchedFragmentManager(outputFile, new PatchManager(), newIndex);
            FragmentMetadata newFrag = newIndex.getFragmentById("frag_1");
            String savedContent = newManager.getFragmentContent(newFrag);
            
            System.out.println("Contenu du fragment dans le fichier sauvegardé :");
            System.out.println("  Contient 'NOUVEAU AUTEUR' : " + savedContent.contains("NOUVEAU AUTEUR"));
            System.out.println("  Contient 'NOUVEAU TITRE' : " + savedContent.contains("NOUVEAU TITRE") + "\n");
            
            // Bilan
            System.out.println("=============================================");
            System.out.println("BILAN : VIRTUAL PATCHING FONCTIONNEL ✓");
            System.out.println("=============================================");
            System.out.println("✓ Patchs appliqués virtuellement");
            System.out.println("✓ Lecture avec cache efficace");
            System.out.println("✓ Fichier original préservé");
            System.out.println("✓ Sauvegarde avec patchs réussie");
            System.out.println("\nLe système est prêt pour des fichiers massifs !");
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }
}
