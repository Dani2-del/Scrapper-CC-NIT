package com.redprocesal.scraperapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import com.redprocesal.scraperapi.playwright.PlaywrightBrowserPool;
import com.redprocesal.scraperapi.exception.ExternalServiceException;
import com.redprocesal.scraperapi.exception.UpstreamTimeoutException;
import com.redprocesal.scraperapi.exception.ServiceOverloadedException;

@Slf4j
@Service
public class PlaywrightRamaScraperService {

    private static final String TARGET = "https://ventanillasocial.dnp.gov.co/Home/ObtenerDatosRUI";
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaywrightRamaScraperService.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PlaywrightBrowserPool playwrightBrowserPool;
    
    // Métricas de Micrometer
    private final Timer scrapeTimer;
    private final Counter successCounter;
    private final Counter errorCounter;

    public PlaywrightRamaScraperService(PlaywrightBrowserPool playwrightBrowserPool, MeterRegistry meterRegistry) {
        this.playwrightBrowserPool = playwrightBrowserPool;

        this.scrapeTimer = Timer.builder("scraper.rama.duration")
                .description("Tiempo de ejecución del scraping RUES/RUI")
                .register(meterRegistry);

        this.successCounter = Counter.builder("scraper.rama.requests")
                .tag("status", "success")
                .register(meterRegistry);

        this.errorCounter = Counter.builder("scraper.rama.requests")
                .tag("status", "error")
                .register(meterRegistry);
    }

    @Cacheable(value = "ruesCache", key = "#cedula")
    @Retry(name = "scraperService")
    @CircuitBreaker(name = "scraperService")
    public String findNameByCedula(String cedula) {
        if (cedula == null || cedula.isBlank()) return null;
        String normalized = cedula.replaceAll("\\D", "");
        if (normalized.isBlank()) return null;

        return scrapeTimer.record(() -> {
            try {
                String formBody = "pNumDoc=" + URLEncoder.encode(normalized, StandardCharsets.UTF_8)
                        + "&pTipDoc=" + URLEncoder.encode("CC", StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder(URI.create(TARGET))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(formBody))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    LOGGER.warn("Unexpected status {} from RUI for cedula {}", response.statusCode(), normalized);
                    errorCounter.increment();
                    throw new ExternalServiceException("RUI returned status " + response.statusCode());
                }

                JsonNode json = OBJECT_MAPPER.readTree(response.body());
                if (json == null || json.has("ok") && !json.get("ok").asBoolean()) {
                    successCounter.increment();
                    return null;
                }

                JsonNode nombre = json.get("nombre");
                successCounter.increment();
                return nombre == null || nombre.isNull() ? null : nombre.asText();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("Interrupted while finding name for cedula {}", cedula, e);
                errorCounter.increment();
                throw new ExternalServiceException("Interrupted while calling RUI", e);
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Error finding name for cedula {}", cedula, e);
                errorCounter.increment();
                throw new ExternalServiceException("Error calling RUI", e);
            }
        });
    }

    @Cacheable(value = "ruesCache", key = "#nit")
    @Retry(name = "scraperService")
    @CircuitBreaker(name = "scraperService", fallbackMethod = "fallbackScrape")
    public Map<String, String> findCompanyByNit(String nit) {
        if (nit == null || nit.isBlank()) return null;
        String normalized = nit.replaceAll("\\D", "");
        if (normalized.isBlank()) return null;

        String url = "https://www.rues.org.co/buscar/RM/" + normalized;
        return scrapeTimer.record(() -> {
            try {
                String bodyText = playwrightBrowserPool.fetchHtml(url, 30000);
                if (bodyText == null || bodyText.isBlank()) {
                    successCounter.increment();
                    return null;
                }
                Map<String, String> result = com.redprocesal.scraperapi.parser.CompanyHtmlParser.parse(bodyText);
                successCounter.increment();
                return result;
            } catch (UpstreamTimeoutException | ServiceOverloadedException e) {
                errorCounter.increment();
                throw e;
            } catch (RuntimeException e) {
                LOGGER.error("Error fetching RUES page for nit {}", nit, e);
                errorCounter.increment();
                throw new ExternalServiceException("Error calling RUES", e);
            }
        });
    }

    private String normalize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    // Método de respaldo (fallback) si el circuito se abre o fallan los reintentos
    public Map<String, String> fallbackScrape(String nit, Throwable t) {
        throw new ExternalServiceException("El servicio externo no está disponible temporalmente. Intente más tarde.");
    }
}