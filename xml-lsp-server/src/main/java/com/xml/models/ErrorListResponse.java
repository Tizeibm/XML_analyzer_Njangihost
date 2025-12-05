package com.xml.models;

import java.util.List;

/**
 * Response for xml/getErrors request
 */
public class ErrorListResponse {
    private List<XMLError> errors;
    private int totalErrors;
    private String filePath;

    public ErrorListResponse() {
    }

    public ErrorListResponse(List<XMLError> errors, String filePath) {
        this.errors = errors;
        this.totalErrors = errors != null ? errors.size() : 0;
        this.filePath = filePath;
    }

    public List<XMLError> getErrors() {
        return errors;
    }

    public void setErrors(List<XMLError> errors) {
        this.errors = errors;
        this.totalErrors = errors != null ? errors.size() : 0;
   }

    public int getTotalErrors() {
        return totalErrors;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
