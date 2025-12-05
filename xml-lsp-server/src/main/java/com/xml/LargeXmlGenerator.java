package com.xml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Générateur de fichiers XML massifs pour tester la scalabilité.
 * Crée des fichiers de taille paramétrable avec une structure cohérente.
 */
public class LargeXmlGenerator {
    
    public static void generateLargeXmlFile(File outputFile, int numberOfBooks) throws IOException {
        System.out.println("Génération de " + numberOfBooks + " livres dans " + outputFile.getName() + "...");
        
        long startTime = System.currentTimeMillis();
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            // Header XML
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<catalog xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            writer.write("         xsi:noNamespaceSchemaLocation=\"books.xsd\">\n");
            
            // Générer N livres
            for (int i = 1; i <= numberOfBooks; i++) {
                writer.write("    <book id=\"bk" + String.format("%06d", i) + "\">\n");
                writer.write("        <author>Author " + i + "</author>\n");
                writer.write("        <title>Book Title " + i + "</title>\n");
                writer.write("        <genre>" + getRandomGenre(i) + "</genre>\n");
                writer.write("        <price>" + getRandomPrice(i) + "</price>\n");
                writer.write("        <publish_date>" + getRandomDate(i) + "</publish_date>\n");
                writer.write("        <description>This is the description for book number " + i + ". ");
                writer.write("It contains interesting content about various topics.</description>\n");
                writer.write("    </book>\n");
                
                // Afficher la progression tous les 10000 livres
                if (i % 10000 == 0) {
                    long currentTime = System.currentTimeMillis();
                    long elapsed = currentTime - startTime;
                    System.out.println("  " + i + " livres générés en " + elapsed + "ms");
                }
            }
            
            writer.write("</catalog>\n");
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        long fileSize = outputFile.length();
        
        System.out.println("✓ Génération terminée :");
        System.out.println("  Livres : " + numberOfBooks);
        System.out.println("  Taille : " + (fileSize / 1024 / 1024) + " MB");
        System.out.println("  Temps : " + totalTime + "ms");
    }
    
    private static String getRandomGenre(int seed) {
        String[] genres = {"Computer", "Fantasy", "Romance", "Horror", "Science", "History"};
        return genres[seed % genres.length];
    }
    
    private static String getRandomPrice(int seed) {
        double price = 5.99 + (seed % 50);
        return String.format("%.2f", price).replace(',', '.');
    }
    
    private static String getRandomDate(int seed) {
        int year = 2000 + (seed % 24);
        int month = 1 + (seed % 12);
        int day = 1 + (seed % 28);
        return String.format("%04d-%02d-%02d", year, month, day);
    }
    
    public static void main(String[] args) {
        try {
            // Générer un fichier de 1MB (~3000 livres)
            File smallFile = new File("books_1mb.xml");
            generateLargeXmlFile(smallFile, 10);
            
            System.out.println("\nPour générer un fichier de 100MB : 300 000 livres");
            System.out.println("Pour générer un fichier de 1GB : 3 000 000 livres");
            System.out.println("Pour générer un fichier de 10GB : 30 000 000 livres");
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
