package com.redprocesal.scraperapi.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
@Slf4j
public class CompanyHtmlParser {

    public static Map<String, String> parse(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) return null;

        Document doc = Jsoup.parse(bodyText);

        Element labelIdent = doc.selectFirst(":matchesOwn((?i)identificaci[oó]n)");
        if (labelIdent == null) {
            labelIdent = doc.selectFirst(":matches((?i)identificaci[oó]n)");
        }

        String razonSocial = null;
        if (labelIdent != null) {
            Element prev = labelIdent.previousElementSibling();
            if (prev != null && !prev.text().isBlank()) {
                razonSocial = prev.text().trim();
            } else if (labelIdent.parent() != null) {
                Element parentPrev = labelIdent.parent().previousElementSibling();
                if (parentPrev != null && !parentPrev.text().isBlank()) {
                    razonSocial = parentPrev.text().trim();
                }
            }
        }

        if (razonSocial == null || razonSocial.isBlank()) {
            Element header = doc.selectFirst("h1, h2, h3, .titulo, .company-name, .nombre-empresa");
            if (header != null) razonSocial = header.text().trim();
        }

        Map<String, String> result = new LinkedHashMap<>();
        if (razonSocial != null && !razonSocial.isBlank()) {
            result.put("razonSocial", razonSocial);
        }

        Function<Element, String> findFollowingText = label -> {
            if (label == null) return null;
            Element next = label.nextElementSibling();
            if (next != null && !next.text().isBlank()) return next.text().trim();
            Element parentNext = label.parent() != null ? label.parent().nextElementSibling() : null;
            if (parentNext != null && !parentNext.text().isBlank()) return parentNext.text().trim();
            if (label.parent() != null) {
                String parentText = label.parent().text();
                String ltext = label.text();
                if (parentText != null && parentText.length() > ltext.length()) {
                    return parentText.replaceFirst("(?i)" + java.util.regex.Pattern.quote(ltext), "").trim();
                }
            }
            return null;
        };

        Map<String, String> labelToKey = new LinkedHashMap<>();
        labelToKey.put("(?i)identificaci[oó]n", "identificacion");
        labelToKey.put("(?i)inscripc", "numeroInscripcion");
        labelToKey.put("(?i)categor[ií]a", "categoria");
        labelToKey.put("(?i)cam[aá]ra.*comercio", "camaraComercio");
        labelToKey.put("(?i)matricula", "numeroMatricula");
        labelToKey.put("(?i)estado", "estado");

        for (Map.Entry<String, String> e : labelToKey.entrySet()) {
            String pattern = e.getKey();
            String key = e.getValue();
            Element found = doc.selectFirst(":matchesOwn(" + pattern + ")");
            if (found == null) found = doc.selectFirst(":matches(" + pattern + ")");
            String value = null;
            if (found != null) {
                value = findFollowingText.apply(found);
            }
            if ((value == null || value.isBlank())) {
                Element th = doc.selectFirst("th:matchesOwn(" + pattern + ")");
                if (th != null) {
                    Element td = th.nextElementSibling();
                    if (td != null) value = td.text().trim();
                }
            }
            if (value != null && !value.isBlank()) {
                result.put(key, value);
            }
        }

        if (!result.containsKey("razonSocial") && labelIdent != null) {
            Element nameCandidate = labelIdent.parent() != null ? labelIdent.parent().previousElementSibling() : null;
            if (nameCandidate != null && !nameCandidate.text().isBlank()) {
                result.put("razonSocial", nameCandidate.text().trim());
            }
        }

        // Fallback: iterate .label/.value pairs and match normalized labels
        for (Element lab : doc.select(".label")) {
            String ltext = lab.text();
            String norm = normalizeText(ltext);
            Element valEl = lab.nextElementSibling();
            if (valEl == null && lab.parent() != null) {
                valEl = lab.parent().selectFirst(".value");
            }
            String vtext = valEl != null ? valEl.text().trim() : null;
            if (vtext == null || vtext.isBlank()) continue;
            if (!result.containsKey("identificacion") && norm.contains("identificacion")) result.put("identificacion", vtext);
            else if (!result.containsKey("numeroInscripcion") && norm.contains("inscrip")) result.put("numeroInscripcion", vtext);
            else if (!result.containsKey("categoria") && norm.contains("categoria")) result.put("categoria", vtext);
            else if (!result.containsKey("camaraComercio") && norm.contains("camara") && norm.contains("comercio")) result.put("camaraComercio", vtext);
            else if (!result.containsKey("numeroMatricula") && norm.contains("matricula")) result.put("numeroMatricula", vtext);
            else if (!result.containsKey("estado") && norm.contains("estado")) result.put("estado", vtext);
        }

        if (result.size() < 4) {
            return null;
        }

        // Filtro de seguridad anti-datos genéricos o pantallas de directorio vacías
        String razon = result.get("razonSocial");
        String estado = result.get("estado");
        if (razon != null && (razon.equalsIgnoreCase("RUES") || razon.equalsIgnoreCase("Directorio"))) {
            return null;
        }
        if (estado != null && estado.toLowerCase().contains("directorio")) {
            return null;
        }

        return result;
    }

    private static String normalizeText(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).trim();
        return n;
    }
}