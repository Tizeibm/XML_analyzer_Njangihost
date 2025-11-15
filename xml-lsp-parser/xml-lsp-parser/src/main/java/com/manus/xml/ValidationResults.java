package com.manus.xml;

import org.eclipse.lsp4j.Diagnostic;
import java.util.List;

public class ValidationResults {
    public String uri;
    public List<Diagnostic> diagnostics;

    public ValidationResults(String uri, List<Diagnostic> diagnostics) {
        this.uri = uri;
        this.diagnostics = diagnostics;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }
}
