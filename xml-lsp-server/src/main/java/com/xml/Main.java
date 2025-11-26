package com.xml;

import com.xml.handlers.LargeXmlValidator;
import com.xml.models.ErrorCollector;
import com.xml.models.ValidationResponse;

import java.io.File;
import java.util.Scanner;

/**
 * Point d'entrée de l'application en mode ligne de commande (CLI).
 * Permet de tester le parseur XML indépendamment du serveur LSP.
 *
 * Utilisation: java -jar xml-lsp-parser-1.0-SNAPSHOT-jar-with-dependencies.jar <chemin_xml> [chemin_xsd]
 */
public class Main {


    public static void main(String[] args) {
        File xmlFile = new File("C:\\Users\\FBI\\Desktop\\XML\\XML_analyzer_Njangihost\\xml-lsp-server\\books.xml");
        File xsdFile = new File("C:\\Users\\FBI\\Desktop\\XML\\XML_analyzer_Njangihost\\xml-lsp-server\\books.xsd");

        LargeXmlValidator largeXmlValidator = new LargeXmlValidator();
        ErrorCollector errorCollector = new ErrorCollector();

        Validator validator = new Validator(errorCollector);
        //validator.validate(xmlFile, xsdFile);

        LargeXmlValidator.ValidationResult valide = largeXmlValidator.validate(xmlFile, xsdFile);



        valide.getErrors().forEach(xmlError ->{
            System.out.println(xmlError.getMessage());
            System.out.println(xmlError.getLineNumber());
            System.out.println(xmlError.getCode());
        });

    }
}
