package com.xml;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.Location;
import java.io.File;
import java.io.FileInputStream;

public class IndexerTest {
    public static void main(String[] args) {
        try {
            // Force Woodstox
            System.setProperty("javax.xml.stream.XMLInputFactory", "com.ctc.wstx.stax.WstxInputFactory");
            
            XMLInputFactory factory = XMLInputFactory.newFactory();
            System.out.println("Factory: " + factory.getClass().getName());
            
            File file = new File("books_1mb.xml");
            FileInputStream fis = new FileInputStream(file);
            XMLEventReader reader = factory.createXMLEventReader(fis);
            
            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                Location loc = event.getLocation();
                
                if (event.isStartElement()) {
                    System.out.println("START " + event.asStartElement().getName() + 
                        " offset=" + loc.getCharacterOffset());
                } else if (event.isEndElement()) {
                    System.out.println("END " + event.asEndElement().getName() + 
                        " offset=" + loc.getCharacterOffset());
                }
            }
            reader.close();
            fis.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
