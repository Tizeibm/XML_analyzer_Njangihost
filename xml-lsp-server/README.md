# XML LSP Server

High-performance Language Server Protocol implementation for XML validation and editing of extremely large files (100GB+).

## Features

- **Streaming Indexing** - Index files without loading them into memory
- **Dynamic Fragmentation** - Split large elements (>5MB) automatically
- **Piece Table** - Efficient text editing data structure
- **MicroParser** - Instant syntax checking (<1ms)
- **Background Validation** - Async XSD validation with debouncing

## Building

```bash
mvn clean package
```

The JAR with dependencies will be created at:
```
target/xml-lsp-server-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Running

```bash
java -jar xml-lsp-server-1.0-SNAPSHOT-jar-with-dependencies.jar
```

The server communicates via stdio using JSON-RPC.

## LSP Methods

| Method | Description |
|--------|-------------|
| `xml/indexFile` | Index an XML file |
| `xml/validateDocument` | Validate XML with XSD |
| `xml/getErrors` | Get validation errors |
| `xml/getFragment` | Get fragment content |
| `xml/applyPatch` | Apply virtual patch |
| `xml/saveFile` | Save with patches |

## Testing

```bash
mvn test
```

62 tests covering streaming, fragmentation, and scalability.

## License

MIT
