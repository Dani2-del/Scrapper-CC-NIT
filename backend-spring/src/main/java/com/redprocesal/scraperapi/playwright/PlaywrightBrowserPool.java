package com.redprocesal.scraperapi.playwright;

import com.redprocesal.scraperapi.exception.ServiceOverloadedException;
import com.redprocesal.scraperapi.exception.UpstreamTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.options.WaitUntilState;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;

@Slf4j
@Component
public class PlaywrightBrowserPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaywrightBrowserPool.class);
    private final Playwright playwright;
    private final Browser browser;
    private final Semaphore semaphore;
    private final MeterRegistry meterRegistry;
    private final int maxPermits;

    public PlaywrightBrowserPool(MeterRegistry meterRegistry) {
        // permits and timeouts are tunable
        this.maxPermits = 4;
        this.semaphore = new Semaphore(this.maxPermits);
        this.meterRegistry = meterRegistry;

        // Register gauges for monitoring
        Gauge.builder("playwright.pool.active_sessions", () -> (double) (this.maxPermits - this.semaphore.availablePermits()))
                .description("Active Playwright sessions")
                .register(this.meterRegistry);
        Gauge.builder("playwright.pool.max_permits", () -> (double) this.maxPermits)
                .description("Max permits for Playwright pool")
                .register(this.meterRegistry);

        this.playwright = Playwright.create();
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList("--no-sandbox", "--disable-dev-shm-usage"));
        this.browser = playwright.chromium().launch(launchOptions);
        LOGGER.info("Initialized Playwright BrowserPool with permits={}", this.maxPermits);
    }

    public String fetchHtml(String url, long timeoutMillis) {
        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceOverloadedException("Interrupted while waiting for Playwright slot", e);
        }
        if (!acquired) {
            LOGGER.warn("No Playwright slot available to fetch {}", url);
            throw new ServiceOverloadedException("Playwright concurrency limit reached");
        }

        try {
            NewContextOptions ctxOpts = new NewContextOptions()
                    .setViewportSize(1200, 800)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            try (com.microsoft.playwright.BrowserContext context = browser.newContext(ctxOpts)) {
                Page page = context.newPage();
                try {
                    page.navigate(url, new Page.NavigateOptions().setTimeout(timeoutMillis).setWaitUntil(WaitUntilState.NETWORKIDLE));
                    // small wait to allow dynamic content
                    page.waitForTimeout(1000);
                    String html = page.content();
                    return html;
                } catch (com.microsoft.playwright.PlaywrightException e) {
                    LOGGER.error("Playwright navigation failed for {}", url, e);
                    if (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout")) {
                        throw new UpstreamTimeoutException("Playwright navigation timed out for " + url, e);
                    }
                    throw new RuntimeException(e);
                }
            }
        } finally {
            semaphore.release();
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (browser != null) browser.close();
        } catch (Exception ex) {
            LOGGER.warn("Error closing Playwright browser", ex);
        }
        try {
            if (playwright != null) playwright.close();
        } catch (Exception ex) {
            LOGGER.warn("Error closing Playwright", ex);
        }
    }
}
