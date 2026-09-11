package com.redprocesal.scraperapi.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.redprocesal.scraperapi.parser.VigenciaRutPdfParser;
import com.redprocesal.scraperapi.playwright.PlaywrightBrowserPool;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class VigenciaRutScraperService {

    private static final Logger log = LoggerFactory.getLogger(VigenciaRutScraperService.class);

    private final PlaywrightBrowserPool browserPool;
    private final VigenciaRutPdfParser pdfParser;
    private final Timer scrapeTimer;
    private final Counter successCounter;
    private final Counter errorCounter;

    public VigenciaRutScraperService(PlaywrightBrowserPool browserPool, 
                                   VigenciaRutPdfParser pdfParser, 
                                   MeterRegistry meterRegistry) {
        this.browserPool = browserPool;
        this.pdfParser = pdfParser;
        this.scrapeTimer = meterRegistry.timer("vigenciarut.scrape.time");
        this.successCounter = meterRegistry.counter("vigenciarut.scrape.success");
        this.errorCounter = meterRegistry.counter("vigenciarut.scrape.error");
    }

@Cacheable(value = "vigenciaRutCache", key = "#nit")
    @Retry(name = "scraperService")
    @CircuitBreaker(name = "scraperService", fallbackMethod = "fallbackScrape")
    public Map<String, Object> consultarYDescargarSoporte(String nit) {
        if (nit == null || nit.isBlank()) return null;
        String normalized = nit.replaceAll("\\D", "");
        if (normalized.isBlank()) return null;

        return scrapeTimer.record(() -> {
            Map<String, Object> response = new HashMap<>();
            String url = "https://vigenciarut.co/";

            try {
                // Utilizamos el método del pool para obtener el HTML de forma segura y concurrente
                String contenidoHtml = browserPool.fetchHtml(url, 20000);
                if (contenidoHtml == null || contenidoHtml.isBlank()) {
                    errorCounter.increment();
                    throw new RuntimeException("No se pudo obtener contenido de VigenciaRUT");
                }

                response.put("html", contenidoHtml);
                
                // Nota: Si el flujo requiere descarga de PDF interactiva con la página, 
                // asegúrate de que el pool exponga la ejecución con Page o utiliza el HTML recuperado.
                successCounter.increment();
                return response;

            } catch (Exception e) {
                log.error("Error al consultar VigenciaRUT para el NIT {}", normalized, e);
                errorCounter.increment();
                throw new RuntimeException("Error al consultar VigenciaRUT", e);
            }
        });
    }
    // Método de respaldo (fallback) que no retorna null para cumplir con la regla 7 del README
    public Map<String, Object> fallbackScrape(String nit, Throwable t) {
        log.warn("Activando fallback para VigenciaRUT en NIT {} debido a: {}", nit, t.getMessage());
        errorCounter.increment();
        return Map.of(
            "success", false,
            "error", "SCRAPER_ERROR",
            "message", "El servicio externo de VigenciaRUT no se encuentra disponible temporalmente."
        );
    }
}