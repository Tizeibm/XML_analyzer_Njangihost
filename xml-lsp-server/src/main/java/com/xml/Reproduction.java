package com.xml;

import java.io.File;
import java.util.List;

import com.xml.handlers.TrackedStaxHandler;
import com.xml.models.ErrorCollector;
import com.xml.models.XMLError;

public class Reproduction {
    public static void main(String[] args) {
        System.out.println("Starting reproduction test...");
        
        ErrorCollector collector = new ErrorCollector();
        TrackedStaxHandler handler = new TrackedStaxHandler(collector);
        
        File file = new File("books_1mb.xml");
        System.out.println("Reading file: " + file.getAbsolutePath());
        
        try {
            handler.parse(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        List<XMLError> errors = collector.getErrors();
        System.out.println("Total errors found: " + errors.size());
        
        for (XMLError error : errors) {
            System.out.println("--------------------------------------------------");
            System.out.println("Line: " + error.getLineNumber());
            System.out.println("Type: " + error.getCode());
            System.out.println("Message: " + error.getMessage());
        }
    }
}
