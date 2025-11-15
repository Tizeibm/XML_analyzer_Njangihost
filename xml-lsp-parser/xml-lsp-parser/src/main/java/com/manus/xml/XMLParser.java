package com.manus.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Analyse SAX en streaming (adapté aux très gros fichiers).
 * Les erreurs sont collectées sans interrompre le parsing (tolérance).
 */
public class XMLParser {

    private static final Logger LOG = LoggerFactory.getLogger(XMLParser.class);
    private final ErrorCollector errorCollector;

    public XMLParser(ErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }

    /**
     * Parsing STREAMING d'un fichier XML (peut peser des Go).
     * Le fichier n'est JAMAIS chargé entièrement en mémoire.
     */
    public void parse(File xmlFile) {
        LOG.info("Début parsing SAX : {}", xmlFile.getAbsolutePath());
        errorCollector.clear(); // on remet à zéro

        try (InputStream in = new FileInputStream(xmlFile)) { // bufferisé par le FS
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            // Sécurité
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            SAXParser saxParser = factory.newSAXParser();
            XMLReader reader = saxParser.getXMLReader();
            TrackedSAXHandler handler = new TrackedSAXHandler(errorCollector);
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);

            reader.parse(new InputSource(in)); // streaming ici

            LOG.info("Parsing SAX terminé.");
        } catch (ParserConfigurationException | SAXException | IOException e) {
            // Erreur fatale (malformé au-delà du tolérable)
            errorCollector.addError("Parsing interrompu : " + e.getMessage(), 0, "FATAL_PARSE");
        }
    }
}