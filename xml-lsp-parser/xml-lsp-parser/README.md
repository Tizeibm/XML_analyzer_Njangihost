# XML-LSP-Parser (Java)

Ce projet implémente un **analyseur XML professionnel, robuste et modulaire** en Java, conçu pour être intégré dans une extension VSCode via le **Language Server Protocol (LSP)**.

L'objectif principal est de fournir un diagnostic précis et tolérant aux erreurs pour les fichiers XML de toute taille (streaming SAX), en détectant notamment les erreurs de structure (balises manquantes ou mal fermées) à la ligne exacte d'ouverture.

## 🎯 Fonctionnalités Clés

*   **Analyse en Streaming (SAX)** : Utilisation du modèle SAX pour une faible consommation mémoire, permettant l'analyse de fichiers de plusieurs Go.
*   **Détection d'Erreurs Précise** : Identification des balises manquantes ou mal fermées à la ligne exacte où l'élément a été ouvert.
*   **Tolérance aux Erreurs** : Le parseur continue l'analyse même après la détection d'erreurs non fatales, maximisant le nombre de diagnostics.
*   **Validation XSD** : Support de la validation contre un schéma XSD fourni.
*   **Sécurité** : Configuration stricte pour désactiver les entités externes (XXE, DTD externes) et garantir un traitement sécurisé.
*   **Intégration LSP** : Implémentation d'un serveur de langage minimaliste (basé sur `lsp4j`) pour fournir des diagnostics en temps réel à un éditeur (VSCode).

## ⚙️ Structure du Projet

Le projet est structuré comme une application Maven :

| Fichier/Classe | Rôle |
| :--- | :--- |
| `pom.xml` | Fichier de configuration Maven (dépendances `xercesImpl`, `lsp4j`). |
| `Main.java` | Point d'entrée CLI pour l'analyse de fichiers (XML + XSD). |
| `LSPServerLauncher.java` | Point d'entrée pour le lancement du serveur LSP (via `stdin`/`stdout`). |
| `ErrorCollector.java` | Collecte, log et stocke les erreurs pour le reporting (console, log, LSP). |
| `TrackedSAXHandler.java` | Gestionnaire SAX personnalisé. Maintient une pile pour la détection précise des erreurs structurelles. |
| `Validator.java` | Gère la validation XSD et configure les fonctionnalités de sécurité. |
| `XMLParser.java` | Orchestre le parsing SAX et la validation XSD. |
| `XMLLanguageServer.java` | Implémentation de l'interface `LanguageServer` (cycle de vie du serveur). |
| `XMLTextDocumentService.java` | Gère les événements de document (`didOpen`, `didChange`) et publie les diagnostics LSP. |
| `XMLWorkspaceService.java` | Implémentation minimale du service d'espace de travail. |

## 🚀 Compilation et Exécution

### Prérequis

*   Java Development Kit (JDK) 8 ou supérieur
*   Apache Maven

### Compilation

Naviguez jusqu'au répertoire racine du projet (`xml-lsp-parser`) et exécutez :

```bash
mvn clean package
```

Ceci générera un JAR exécutable avec toutes les dépendances dans le répertoire `target/`.

### Mode CLI (Analyse de Fichier)

Pour tester le parseur indépendamment du LSP, utilisez le mode CLI :

```bash
java -jar target/xml-lsp-parser-1.0-SNAPSHOT-jar-with-dependencies.jar books.xml books.xsd
```

**Arguments :**
1.  Chemin vers le fichier XML (obligatoire).
2.  Chemin vers le fichier XSD (optionnel).

Le rapport d'erreurs sera affiché dans la console et écrit dans `errors.log`.

### Mode LSP (Intégration VSCode)

Pour intégrer ce serveur dans une extension VSCode, l'extension doit lancer le JAR en utilisant le point d'entrée `LSPServerLauncher` et communiquer via `stdin`/`stdout`.

Le point d'entrée pour le LSP est :

```bash
java -cp target/xml-lsp-parser-1.0-SNAPSHOT-jar-with-dependencies.jar com.manus.xml.LSPServerLauncher
```

Le serveur LSP prendra le relais et commencera à écouter les messages JSON-RPC du client VSCode.

## 🧪 Fichiers de Test

*   `books.xml` : Fichier XML d'exemple contenant des erreurs structurelles intentionnelles pour tester la détection précise des balises manquantes et mal fermées.
*   `books.xsd` : Schéma XSD correspondant pour tester la validation.

---
*Projet généré par Manus AI.*
