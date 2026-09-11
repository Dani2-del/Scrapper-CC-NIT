package com.redprocesal.scraperapi.controller;

import com.redprocesal.scraperapi.service.PlaywrightRamaScraperService;
import com.redprocesal.scraperapi.service.VigenciaRutScraperService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.redprocesal.scraperapi.exception.ExternalServiceException;
import com.redprocesal.scraperapi.exception.UpstreamTimeoutException;
import com.redprocesal.scraperapi.exception.ServiceOverloadedException;

@RestController
@Slf4j 
public class LookupController {

    private static final Set<String> PARTICULAS = Set.of("de", "del", "la", "las", "los", "da", "das", "y", "e");
    private static final Set<String> PARTICULAS_DE = Set.of("de", "del");
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LookupController.class);

    private final PlaywrightRamaScraperService ramaScraperService;
    private final VigenciaRutScraperService vigenciaRutScraperService;

    public LookupController(PlaywrightRamaScraperService ramaScraperService,
                            VigenciaRutScraperService vigenciaRutScraperService) {
        this.ramaScraperService = ramaScraperService;
        this.vigenciaRutScraperService = vigenciaRutScraperService;
    }

    @GetMapping("/api/lookup")
    public ResponseEntity<Map<String, Object>> lookup(
            @RequestParam(value = "identificacion", required = false) String identificacion,
            @RequestParam(value = "cedula", required = false) String cedula,
            @RequestParam(value = "nit", required = false) String nit
    ) {
        Map<String, Object> response = new HashMap<>();

        String normalizedId = cleanDocument(identificacion);
        String normalizedCedula = normalizeCedulaOrNit(cedula, normalizedId, true);
        String normalizedNit = normalizeCedulaOrNit(nit, normalizedId, false);

        if (normalizedCedula != null) {
            response.put("cedula", normalizedCedula);
        } else {
            response.put("cedula", "");
        }

        if (normalizedNit != null) {
            response.put("nit", normalizedNit);
        } else {
            response.put("nit", "");
        }

        // 1. Procesamiento de Cédula (RUI)
        if (normalizedCedula != null) {
            try {
                String nombre = ramaScraperService.findNameByCedula(normalizedCedula);
                if (nombre != null && !nombre.isBlank()) {
                    NameParts parts = splitNameParts(nombre);
                    Map<String, String> person = new HashMap<>();
                    person.put("name", nombre);
                    person.put("firstNames", parts.firstNames);
                    person.put("lastNames", parts.lastNames);
                    response.put("person", person);
                }
            } catch (UpstreamTimeoutException e) {
                log.error("Timeout calling RUI for cedula {}", normalizedCedula, e);
                response.put("error", "RUI timeout");
                return ResponseEntity.status(504).body(response);
            } catch (ServiceOverloadedException e) {
                log.warn("Playwright concurrency saturated when calling RUI for cedula {}", normalizedCedula, e);
                response.put("error", "Service overloaded");
                return ResponseEntity.status(429).body(response);
            } catch (ExternalServiceException e) {
                log.error("Upstream error calling RUI for cedula {}", normalizedCedula, e);
                response.put("error", "RUI error");
                return ResponseEntity.status(502).body(response);
            } catch (Exception e) {
                log.error("Unexpected error while looking up cedula {}", normalizedCedula, e);
                response.put("error", "Internal server error");
                return ResponseEntity.status(500).body(response);
            }
        }

        // 2. Procesamiento de NIT (RUES + VigenciaRUT integrado)
        if (normalizedNit != null) {
            try {
                // Consulta original en RUES
                Map<String, String> company = ramaScraperService.findCompanyByNit(normalizedNit);
                if (company != null && !company.isEmpty()) {
                    response.put("company", company);
                }

                // Consulta de soporte y datos avanzados en VigenciaRUT
                Map<String, Object> vigenciaRutData = vigenciaRutScraperService.consultarYDescargarSoporte(normalizedNit);
                if (vigenciaRutData != null && !vigenciaRutData.isEmpty()) {
                    response.put("vigenciaRut", vigenciaRutData);
                }

            } catch (UpstreamTimeoutException e) {
                log.error("Timeout calling RUES/VigenciaRUT for nit {}", normalizedNit, e);
                response.put("error", "RUES timeout");
                return ResponseEntity.status(504).body(response);
            } catch (ServiceOverloadedException e) {
                log.warn("Playwright concurrency saturated when calling RUES/VigenciaRUT for nit {}", normalizedNit, e);
                response.put("error", "Service overloaded");
                return ResponseEntity.status(429).body(response);
            } catch (ExternalServiceException e) {
                log.error("Upstream error calling RUES/VigenciaRUT for nit {}", normalizedNit, e);
                response.put("error", "RUES error");
                return ResponseEntity.status(502).body(response);
            } catch (Exception e) {
                log.error("Unexpected error while looking up nit {}", normalizedNit, e);
                response.put("error", "Internal server error");
                return ResponseEntity.status(500).body(response);
            }
        }

        boolean hasPerson = response.containsKey("person");
        boolean hasCompany = response.containsKey("company") || response.containsKey("vigenciaRut");
        
        if (!hasPerson && !hasCompany) {
            response.put("message", "No se encontró");
            return ResponseEntity.ok(response);
        }

        if (hasPerson && hasCompany) {
            response.put("message", "Se encontraron la cédula y el NIT.");
        } else if (hasPerson) {
            response.put("message", "Se encontró la cédula en el RUI.");
        } else {
            response.put("message", "Se encontró información para el NIT en RUES / VigenciaRUT.");
        }

        return ResponseEntity.ok(response);
    }

    private String cleanDocument(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("\\D", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeCedulaOrNit(String explicitValue, String identificacion, boolean preferCedula) {
        String cleanedExplicit = cleanDocument(explicitValue);
        String cleanedId = cleanDocument(identificacion);
        String candidate = cleanedExplicit != null ? cleanedExplicit : cleanedId;
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        if (cleanedExplicit != null && cleanedId != null && !cleanedExplicit.equals(cleanedId)) {
            return preferCedula ? cleanedExplicit : cleanedId;
        }

        if (candidate.length() >= 9 && (candidate.startsWith("8") || candidate.startsWith("9"))) {
            return preferCedula ? null : candidate;
        }

        if (preferCedula) {
            return candidate;
        }

        return null;
    }

    private static NameParts splitNameParts(String fullName) {
        String normalized = fullName.trim().replaceAll("\\s+", " ");
        String[] words = normalized.split(" ");
        if (words.length == 0) {
            return new NameParts("", "");
        }
        if (words.length == 1) {
            return new NameParts(words[0], "");
        }
        if (words.length == 2) {
            return new NameParts(words[0], words[1]);
        }

        int start = words.length - 2;
        if (isParticle(words[start])) {
            start--;
        }

        if (words.length >= 4) {
            if (isParticle(words[words.length - 2]) && isDeParticle(words[words.length - 3])) {
                start = words.length - 3;
            } else if (isParticle(words[words.length - 3]) && isDeParticle(words[words.length - 4])) {
                start = words.length - 4;
            }
        }

        while (start > 0 && isParticle(words[start])) {
            start--;
        }

        if (start <= 0) {
            start = 1;
        }

        String firstNames = join(words, 0, start);
        String lastNames = join(words, start, words.length);
        return new NameParts(firstNames, lastNames);
    }

    private static boolean isParticle(String word) {
        return PARTICULAS.contains(word.toLowerCase(Locale.ROOT));
    }

    private static boolean isDeParticle(String word) {
        return PARTICULAS_DE.contains(word.toLowerCase(Locale.ROOT));
    }

    private static String join(String[] words, int fromInclusive, int toExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (i > fromInclusive) {
                builder.append(' ');
            }
            builder.append(words[i]);
        }
        return builder.toString();
    }

    private static class NameParts {
        private final String firstNames;
        private final String lastNames;

        private NameParts(String firstNames, String lastNames) {
            this.firstNames = firstNames;
            this.lastNames = lastNames;
        }
    }
}