package com.xml;

import java.io.File;

import com.xml.handlers.LargeXmlValidator;
import com.xml.models.FragmentIndex;
import com.xml.models.ValidationResult;
import com.xml.services.PatchManager;
import com.xml.services.PatchedFragmentManager;

/**
 * Point d'entrée de l'application en mode ligne de commande (CLI).
 * Permet de tester le parseur XML indépendamment du serveur LSP.
 *
 * Utilisation: java -jar xml-lsp-parser-1.0-SNAPSHOT-jar-with-dependencies.jar <chemin_xml> [chemin_xsd]
 */
public class Main {
    public static void main(String[] args) {
        File xmlFile = new File("C:\\Users\\FBI\\Desktop\\XML\\XML_analyzer_Njangihost\\xml-lsp-server\\books_1mb.xml");
        File xsdFile = new File("C:\\Users\\FBI\\Desktop\\XML\\XML_analyzer_Njangihost\\xml-lsp-server\\books.xsd");

        LargeXmlValidator largeXmlValidator = new LargeXmlValidator();
        FragmentIndex index = new FragmentIndex();
        PatchManager manager = new PatchManager();
        PatchedFragmentManager patchedFragmentManager = new PatchedFragmentManager(xmlFile, manager, index);
        //validator.validate(xmlFile, xsdFile);
        ValidationResult valide = largeXmlValidator.validate(xmlFile, xsdFile);


        valide.getErrors().forEach(xmlError ->{

        });
        System.out.println(valide.getSummary());
        System.out.println(valide.getErrorCount());

    }
}
