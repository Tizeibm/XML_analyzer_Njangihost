package com.xml.services;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.xml.models.Patch;

/**
 * Gestionnaire de patchs robuste.
 * Maintient une liste triée de patchs et un index par fragment.
 * Gère la fusion des conflits et la persistance.
 */
public class PatchManager {

    // Liste globale triée par offset (pour l'application finale)
    private final List<Patch> patchesSortedByOffset = new CopyOnWriteArrayList<>();

    // Index par fragment (pour l'édition locale)
    private final Map<String, List<Patch>> patchesByFragment = new ConcurrentHashMap<>();

    private final PatchJournal journal;

    public PatchManager(Path workspaceRoot) {
        this.journal = new PatchJournal(workspaceRoot);
        reloadPatches();
    }
    
    // Constructeur sans journal pour les tests ou usage temporaire
    public PatchManager() {
        this.journal = null;
    }

    /**
     * Ajoute un patch avec gestion des conflits et normalisation.
     */
    public synchronized void addPatch(Patch newPatch) {
        // 1. Normalisation (déjà faite par le constructeur de Patch, mais on pourrait en faire plus ici)
        
        // 2. Gestion des conflits et fusion
        List<Patch> conflictingPatches = findConflictingPatches(newPatch);
        
        if (!conflictingPatches.isEmpty()) {
            // Retirer les patchs en conflit
            patchesSortedByOffset.removeAll(conflictingPatches);
            removeFromFragmentIndex(conflictingPatches);
            
            // Fusionner
            newPatch = mergePatches(conflictingPatches, newPatch);
        }

        // 3. Insertion triée
        insertSorted(newPatch);
        
        // 4. Indexation par fragment
        addToFragmentIndex(newPatch);

        // 5. Persistance
        if (journal != null) {
            journal.logPatch(newPatch);
        }
    }

    public List<Patch> getPatchesForFragment(String fragmentId) {
        return patchesByFragment.getOrDefault(fragmentId, Collections.emptyList());
    }

    public List<Patch> getAllPatchesSorted() {
        return new ArrayList<>(patchesSortedByOffset);
    }

    public int getPatchCount() {
        return patchesSortedByOffset.size();
    }

    public synchronized void clearAll() {
        patchesSortedByOffset.clear();
        patchesByFragment.clear();
        if (journal != null) {
            journal.clearJournal();
        }
    }

    private void reloadPatches() {
        if (journal == null) return;
        List<Patch> loaded = journal.loadPatches();
        for (Patch p : loaded) {
            // On réinsère sans relogger pour reconstruire les structures en mémoire
            // Note: On suppose que le journal est déjà une suite d'états valides, 
            // mais pour être sûr on repasse par la logique de fusion si nécessaire.
            // Cependant, le journal est append-only, donc les derniers patchs prévalent.
            // Une stratégie simple est de tout rejouer.
            
            // Optimisation: insérer directement si on fait confiance au journal, 
            // mais addPatch est plus sûr.
            // Attention: addPatch va relogger. Il faut éviter ça.
            insertSorted(p);
            addToFragmentIndex(p);
        }
    }

    private List<Patch> findConflictingPatches(Patch newPatch) {
        List<Patch> conflicts = new ArrayList<>();
        for (Patch existing : patchesSortedByOffset) {
            // Chevauchement: start1 < end2 && start2 < end1
            if (existing.getGlobalStartOffset() < newPatch.getGlobalEndOffset() &&
                newPatch.getGlobalStartOffset() < existing.getGlobalEndOffset()) {
                conflicts.add(existing);
            }
        }
        return conflicts;
    }

    private Patch mergePatches(List<Patch> existingPatches, Patch newPatch) {
        // Stratégie simplifiée : le nouveau patch gagne et écrase les anciens sur sa plage.
        // Mais pour être vraiment robuste (Git style), il faudrait fusionner les textes.
        // Ici, on suppose que l'utilisateur envoie un patch qui représente l'état désiré 
        // d'une zone qui inclut potentiellement les anciens patchs.
        
        // Si le nouveau patch couvre entièrement les anciens, on garde le nouveau.
        // Si chevauchement partiel, c'est complexe.
        // Pour l'instant, on adopte la stratégie "Le dernier gagne" sur la plage concernée.
        // On étend la plage du nouveau patch pour couvrir les anciens si nécessaire ?
        // Non, le patch entrant est la vérité terrain de l'éditeur.
        
        // Cependant, il faut faire attention aux "trous" si on supprime un vieux patch 
        // qui dépassait du nouveau.
        // Pour simplifier : on supprime tout ce qui touche et on remplace par le nouveau.
        // C'est destructif pour les parties non couvertes des anciens patchs, 
        // mais c'est le comportement attendu si on édite par dessus.
        
        return newPatch; 
    }

    private void insertSorted(Patch patch) {
        int index = Collections.binarySearch(patchesSortedByOffset, patch);
        if (index < 0) {
            index = -(index + 1);
        }
        patchesSortedByOffset.add(index, patch);
    }

    private void addToFragmentIndex(Patch patch) {
        if (patch.getFragmentId() != null) {
            patchesByFragment.computeIfAbsent(patch.getFragmentId(), k -> new CopyOnWriteArrayList<>()).add(patch);
            // Trier aussi la liste du fragment
            Collections.sort(patchesByFragment.get(patch.getFragmentId()));
        }
    }

    private void removeFromFragmentIndex(List<Patch> patches) {
        for (Patch p : patches) {
            if (p.getFragmentId() != null) {
                List<Patch> list = patchesByFragment.get(p.getFragmentId());
                if (list != null) {
                    list.remove(p);
                    if (list.isEmpty()) {
                        patchesByFragment.remove(p.getFragmentId());
                    }
                }
            }
        }
    }
}
