package com.xml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.xml.models.ErrorCollector;

public class Validators {
    private final ErrorCollector collector;

    public Validators(ErrorCollector collector) {
        this.collector = collector;
    }

    public boolean validate(File xmlFile, File xsdFile) {

        if (xsdFile == null || !xsdFile.exists()) {
            return true;
        }

        try {
            // Les System.setProperty sont maintenant dans XmlLanguageServerLauncher (bloc static)
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            // Activation réelle continue-after-fatal-error (supporté par Xerces)
            factory.setFeature("http://apache.org/xml/features/continue-after-fatal-error", true);

            // Sécurité anti-XXE
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            Schema schema = factory.newSchema(xsdFile);
            javax.xml.validation.Validator validator = schema.newValidator();
            validator.setErrorHandler(new XsdErrorHandler(collector));

            try (var is = Files.newInputStream(xmlFile.toPath())) {
                validator.validate(new StreamSource(is));
            } catch (SAXException e) {
                // Avec Xerces + continue-after-fatal-error, le parsing NE s'arrête plus ici.
            }

            return true;

        } catch (IOException e) {
            collector.addError("Erreur d'entrée/sortie lors de la validation : " + e.getMessage(), 0, "IO_ERROR");
            return false;

        } catch (Exception e) {
            collector.addError("Erreur inattendue lors de la validation : " + e.getMessage(), 0, "UNEXPECTED_ERROR");
            return false;
        }
    }

    private static class XsdErrorHandler extends DefaultHandler {
        private final ErrorCollector col;

        XsdErrorHandler(ErrorCollector c) {
            col = c;
        }

        @Override
        public void warning(SAXParseException e) {
            col.addError(e.getMessage(), e.getLineNumber(), "VALIDATION_WARNING");
        }

        @Override
        public void error(SAXParseException e) {
            col.addError(e.getMessage(), e.getLineNumber(), "VALIDATION_ERROR");
        }

        @Override
        public void fatalError(SAXParseException e) {
            // Grâce à Xerces, on ne stoppe plus : on collecte aussi !
            col.addError(e.getMessage(), e.getLineNumber(), "FATAL_VALIDATION");
        }
    }
}
