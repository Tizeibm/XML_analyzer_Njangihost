package com.xml.models;

import java.util.List;

/**
     * Résultat propre de validation XML/XSD.
     * Amélioré pour rapporter davantage de détails.
     */
    public  class ValidationResult {
        private final boolean success;
        private final List<XMLError> errors;
        private final long validationTimeMs;
        private final long fileSizeBytes;
        private final int errorCount;
        private final int warningCount;


        public ValidationResult(boolean success, List<XMLError> errors,
                                long validationTimeMs, long fileSizeBytes) {

            this.success = success;
            this.errors = errors;
            this.validationTimeMs = validationTimeMs;
            this.fileSizeBytes = fileSizeBytes;
            int e = 0, w = 0;
            for (XMLError err : errors) {
                if ("error".equals(err.getSeverity())) e++;
                if ("warning".equals(err.getSeverity())) w++;
            }
            this.errorCount = e;
            this.warningCount = w;

        }

        public boolean isSuccess() { return success; }
        public List<XMLError> getErrors() { return errors; }
        public long getValidationTime() { return validationTimeMs; }
        public long getFileSize() { return fileSizeBytes; }
        public int getErrorCount() { return errorCount; }
        public int getWarningCount() { return warningCount; }
        public String getSummary(){
            return "Fichier " + fileSizeBytes / (1024 * 1024) + " MB | Temps " + validationTimeMs
                    + "ms | Erreurs " + errorCount + " | Warnings " + warningCount;
        }
    }