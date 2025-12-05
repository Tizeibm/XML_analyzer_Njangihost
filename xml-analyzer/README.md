# XML Validator for Huge Files

<div align="center">

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)
![VS Code](https://img.shields.io/badge/VS%20Code-1.74%2B-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)

**Validate and edit XML files up to 100GB+ without loading them into memory.**

[Features](#features) • [Installation](#installation) • [Quick Start](#quick-start) • [Architecture](#architecture) • [API Reference](#api-reference)

</div>

---

## Features

### 🚀 Ultra-Fast Response Times
- **< 1ms** for syntax checks (MicroParser)
- **< 10ms** for LSP operations
- Instant feedback while typing

### 💾 Memory Efficient
- **Streaming indexing** - never loads the entire file
- **Piece Table** data structure for O(log n) edits
- **StringPool** deduplication for tag names
- Handles **100GB+ files** with minimal RAM

### ✏️ Virtual Patching
- Edit fragments without modifying the original file
- All changes stored in memory until explicitly saved
- Debounced background validation

### ✅ XSD Validation
- Structural parsing first (detect malformed XML)
- XSD validation only on well-formed documents
- Error mapping to specific fragments

---

## Installation

### Prerequisites
- **Java 17+** (JRE or JDK)
- **VS Code 1.74+**
- **Node.js 18+** (for development only)

### From VSIX (Recommended)
```bash
code --install-extension xml-validator-big-files-1.0.0.vsix
```

### From Source
```bash
# Clone the repository
git clone https://github.com/Njangihost/XML_analyzer_Njangihost.git
cd XML_analyzer_Njangihost

# Build the LSP Server
cd xml-lsp-server
mvn clean package -DskipTests
cp target/xml-lsp-server-1.0-SNAPSHOT-jar-with-dependencies.jar ../xml-analyzer/server/xml-lsp-server.jar

# Build the Extension
cd ../xml-analyzer
npm install
npm run compile
```

---

## Quick Start

### 1. Validate an XML File
1. Open a `.xml` file in VS Code
2. Press `Ctrl+Shift+P` → `XML: Validate with XSD`
3. Select your XML file and optionally an XSD schema

### 2. Browse Validation Errors
- Click the **XML Validator** icon in the Activity Bar
- Errors are grouped by fragment with line numbers

### 3. Edit a Fragment
1. Click on an error to preview the fragment
2. Make your corrections in the editor
3. Close the tab to apply the patch (in memory)

### 4. Save All Changes
- Run `XML: Save Patched File` to write all patches to disk

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    VS Code Extension                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ ErrorPanel  │  │ FragmentEdit│  │ LSP Client (JSON-RPC)│  │
│  └─────────────┘  └─────────────┘  └──────────┬──────────┘  │
└───────────────────────────────────────────────┼─────────────┘
                                                │
                              stdio (JSON-RPC)  │
                                                ▼
┌─────────────────────────────────────────────────────────────┐
│                    XML LSP Server (Java)                     │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                  XmlLanguageServer                   │    │
│  │  • indexFile()      • validateDocument()            │    │
│  │  • getFragment()    • applyPatch()                  │    │
│  │  • saveFile()       • getErrors()                   │    │
│  └─────────────────────────────────────────────────────┘    │
│                            │                                 │
│  ┌─────────────┐  ┌────────┴────────┐  ┌─────────────────┐  │
│  │ MicroParser │  │ StreamingIndexer │  │ PieceTable      │  │
│  │ (Instant)   │  │ (Fragments)      │  │ (Virtual Edits) │  │
│  └─────────────┘  └─────────────────┘  └─────────────────┘  │
│                            │                                 │
│  ┌─────────────────────────┴───────────────────────────────┐│
│  │              BackgroundValidationService                 ││
│  │  • Thread pool for heavy XSD validation                 ││
│  │  • 300ms debounce for rapid edits                       ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### Key Components

| Component | Purpose | Performance |
|-----------|---------|-------------|
| **MicroParser** | Instant syntax checks | < 1ms |
| **StreamingIndexer** | Fragment indexing without loading file | O(n) streaming |
| **PieceTable** | Virtual edits on large files | O(log k) edits |
| **FragmentIndex** | Fast fragment lookup by ID/line | O(1) lookup |
| **BackgroundValidationService** | Async XSD validation | Debounced 300ms |

---

## API Reference

### LSP Custom Requests

| Request | Description | Parameters |
|---------|-------------|------------|
| `xml/indexFile` | Index an XML file | `fileUri: string` |
| `xml/validateDocument` | Validate with XSD | `{xmlPath, xsdPath?, applyPatches}` |
| `xml/getErrors` | Get all validation errors | - |
| `xml/getFragment` | Get fragment content | `fragmentId: string` |
| `xml/applyPatch` | Apply virtual patch | `{fragmentId, newContent}` |
| `xml/saveFile` | Save with all patches | `fileUri?: string` |

---

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| `xmlValidator.javaPath` | `java` | Path to Java executable |
| `xmlValidator.serverJar` | (bundled) | Custom LSP server JAR path |

---

## Performance Benchmarks

| Metric | Result | Target |
|--------|--------|--------|
| MicroParser response | 0.48 ms | < 10ms ✅ |
| FragmentIndex lookup | 0.001 ms | < 1ms ✅ |
| PieceTable edit | 0.007 ms | < 1ms ✅ |
| Memory (1M fragments) | < 150 MB | < 150MB ✅ |

---

## Development

### Running Tests
```bash
cd xml-lsp-server
mvn test
```

### Test Coverage
- **62 tests** covering:
  - Streaming indexing
  - Dynamic fragmentation (5MB threshold)
  - PieceTable operations
  - MicroParser syntax detection
  - Scalability (100K+ fragments)

---
