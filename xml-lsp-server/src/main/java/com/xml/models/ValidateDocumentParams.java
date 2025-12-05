package com.xml.models;

/**
 * Parameters for xml/validateDocument request
 */
public class ValidateDocumentParams {
    private String xmlPath;
    private String xsdPath;
    private boolean applyPatches;

    public ValidateDocumentParams() {
    }

    public ValidateDocumentParams(String xmlPath, String xsdPath, boolean applyPatches) {
        this.xmlPath = xmlPath;
        this.xsdPath = xsdPath;
        this.applyPatches = applyPatches;
    }

    public String getXmlPath() {
        return xmlPath;
    }

    public void setXmlPath(String xmlPath) {
        this.xmlPath = xmlPath;
    }

    public String getXsdPath() {
        return xsdPath;
    }

    public void setXsdPath(String xsdPath) {
        this.xsdPath = xsdPath;
    }

    public boolean isApplyPatches() {
        return applyPatches;
    }

    public void setApplyPatches(boolean applyPatches) {
        this.applyPatches = applyPatches;
    }
}
