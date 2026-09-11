package com.redprocesal.scraperapi.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class VigenciaRutHtmlParser {

    private static final Logger log = LoggerFactory.getLogger(VigenciaRutHtmlParser.class);

    public static Map<String, String> parse(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) return null;

        Document doc = Jsoup.parse(htmlContent);
        Map<String, String> result = new LinkedHashMap<>();

        // Extraer estado principal de la tarjeta de consulta
        Element estadoEl = doc.selectFirst(".badge, .estado, div:has-text('ACTIVO'), div:has-text('CANCELADO'), div:has-text('SUSPENDIDO')");
        if (estadoEl != null) {
            result.put("estado", estadoEl.text().trim());
        }

        // Extracción estructurada por etiquetas comunes en la ficha
        extractField(doc, result, "(?i)nit", "nit");
        extractField(doc, result, "(?i)raz[oó]n social", "razonSocial");
        extractField(doc, result, "(?i)tipo contribuyente", "tipoContribuyente");
        extractField(doc, result, "(?i)ciudad", "ciudad");
        extractField(doc, result, "(?i)dato actualizado", "fechaActualizacion");

        // Validación defensiva: si no extrajo los datos mínimos, se descarta la respuesta
        if (result.size() < 2 || !result.containsKey("razonSocial")) {
            log.warn("El parser de VigenciaRUT descartó una respuesta incompleta o vacía.");
            return null;
        }

        return result;
    }

    private static void extractField(Document doc, Map<String, String> result, String pattern, String key) {
        Element label = doc.selectFirst(":matchesOwn(" + pattern + ")");
        if (label == null) {
            label = doc.selectFirst(":matches(" + pattern + ")");
        }
        
        if (label != null) {
            Element next = label.nextElementSibling();
            if (next != null && !next.text().isBlank()) {
                result.put(key, next.text().trim());
                return;
            }
            
            if (label.parent() != null) {
                Element parentNext = label.parent().nextElementSibling();
                if (parentNext != null && !parentNext.text().isBlank()) {
                    result.put(key, parentNext.text().trim());
                    return;
                }
                
                String parentText = label.parent().text();
                String lText = label.text();
                if (parentText != null && parentText.length() > lText.length()) {
                    String val = parentText.replaceFirst("(?i)" + java.util.regex.Pattern.quote(lText), "").trim();
                    if (!val.isBlank()) {
                        result.put(key, val);
                    }
                }
            }
        }
    }
}