package com.redprocesal.scraperapi.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class VigenciaRutPdfParser {

    public Map<String, String> parsePdfBytes(byte[] pdfBytes) {
        Map<String, String> extractedData = new HashMap<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            extractedData.put("textoCompleto", text);
            // Implementar aquí la lógica específica de extracción por líneas o expresiones regulares (Regex) según el formato del PDF de VigenciaRUT.

        } catch (IOException e) {
            throw new RuntimeException("Error al procesar el archivo PDF en memoria con PDFBox", e);
        }

        return extractedData;
    }
}