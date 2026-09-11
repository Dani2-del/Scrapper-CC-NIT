# Scraper VigenciaRUT

Servicio backend encargado de consultar información de RUT en `vigenciarut.co`, automatizando el navegador mediante Playwright y procesando el soporte/PDF obtenido desde el sitio.

## Stack actual

* Java 17
* Spring Boot 3.1.4
* Spring Web
* Playwright Java 1.41.0
* Jsoup 1.16.1
* Apache PDFBox 3.0.1
* Resilience4j
* Bucket4j
* Redis
* Micrometer + Prometheus
* Lombok

El proyecto está configurado como una aplicación Maven/JAR.

---

# ⚠️ Consideraciones importantes del scraper

El scraper depende directamente del comportamiento de un sitio web externo.

Por esta razón, **no se debe asumir que los selectores, tiempos de espera o flujo de descarga permanecerán estables**.

Cualquier cambio en el HTML, JavaScript, endpoints, CAPTCHA, flujo de consulta o generación del PDF puede romper el scraper.

---

# 1. No crear un Chromium nuevo por cada consulta

## Problema

No se debe utilizar este patrón dentro del servicio de scraping:

```java
try (Browser browser = Playwright.create()
        .chromium()
        .launch(new BrowserType.LaunchOptions().setHeadless(true))) {
    // consulta
}
```

Esto provoca que cada petición:

1. Cree una instancia de Playwright.
2. Inicie Chromium.
3. Cree el contexto/página.
4. Realice la consulta.
5. Cierre Chromium.

Bajo concurrencia esto genera consumo innecesario de:

* CPU
* RAM
* procesos del sistema
* tiempo de respuesta

## Solución

Utilizar el `PlaywrightBrowserPool` existente.

El proyecto ya utiliza Playwright como dependencia y dispone de mecanismos para limitar la concurrencia.

La arquitectura esperada es:

```text
VigenciaRutScraperService
          │
          ▼
 PlaywrightBrowserPool
          │
          ▼
      Browser
          │
          ▼
   BrowserContext
          │
          ▼
        Page
```

El navegador debe reutilizarse y los contextos/páginas deben aislarse por operación.

---

# 2. Evitar selectores genéricos

## ❌ Evitar

```java
page.fill("input", nit);
```

```java
page.click("button:has-text('Consultar')");
```

```java
page.waitForSelector("div:has-text('Razón social')");
```

Estos selectores dependen demasiado de la estructura general de la página.

Si aparecen varios `input`, `button` o elementos con el mismo texto, el comportamiento puede cambiar.

## ✅ Preferir

Utilizar selectores específicos y estables:

```java
page.locator("#nit").fill(nit);
page.locator("#btnConsultar").click();
```

O atributos semánticos:

```java
page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Consultar"))
    .click();
```

También pueden utilizarse:

```text
id
name
data-testid
aria-label
role
placeholder
```

### Regla

Los selectores deben ser lo más específicos posible sin depender de clases CSS generadas dinámicamente.

---

# 3. No utilizar esperas fijas para sincronización

## ❌ Evitar

```java
page.waitForTimeout(3000);
```

Una espera fija no garantiza que el contenido haya terminado de cargar.

Puede suceder:

```text
Respuesta rápida
   ↓
espera innecesaria
```

o:

```text
Respuesta lenta
   ↓
3 segundos no son suficientes
   ↓
scraper falla
```

## ✅ Preferir

Esperar una condición real:

```java
page.waitForSelector(".resultado");
```

O:

```java
page.locator(".resultado").waitFor();
```

Cuando sea posible, esperar:

* un elemento específico
* una respuesta HTTP
* un cambio de estado
* la desaparición de un loader
* la aparición del resultado

---

# 4. Revisar cuidadosamente `waitForDownload()`

Actualmente el scraper asume que el botón:

```text
Descargar soporte
```

produce un evento de descarga de navegador.

Eso debe verificarse contra el comportamiento real de `vigenciarut.co`.

El sitio podría estar:

* descargando un archivo realmente
* abriendo una nueva pestaña
* navegando hacia un PDF
* generando un `Blob`
* ejecutando `fetch()`
* generando el documento mediante JavaScript

Por lo tanto, no asumir automáticamente que:

```java
page.waitForDownload(...)
```

es siempre el mecanismo correcto.

## Recomendación

Inspeccionar primero el comportamiento real del botón y determinar si:

```text
click
  ↓
Download
```

o:

```text
click
  ↓
HTTP response
```

o:

```text
click
  ↓
Nueva página
```

o:

```text
click
  ↓
Blob / JavaScript
```

---

# 5. Evitar archivos temporales cuando no sean necesarios

Actualmente el flujo puede ser:

```text
Descargar PDF
      ↓
Guardar en disco
      ↓
Leer con PDFBox
      ↓
Eliminar archivo
```

Ejemplo:

```java
Path rutaPdf = Paths.get(
    "downloads/soporte_" + normalized + ".pdf"
);
```

y posteriormente:

```java
Files.deleteIfExists(rutaPdf);
```

## Riesgos

Este patrón introduce:

* I/O adicional
* problemas de permisos
* problemas de concurrencia
* archivos temporales abandonados
* posibles colisiones
* dependencia del filesystem

## Preferencia

Cuando sea posible:

```text
Download / Response
        ↓
     byte[]
        ↓
     PDFBox
        ↓
     resultado
```

Procesar el PDF en memoria es preferible cuando el tamaño del documento lo permita.

---

# 6. Cuidado con Retry + Playwright

El proyecto utiliza Resilience4j para tolerancia a errores. La dependencia está configurada en el proyecto.

El scraper utiliza mecanismos como:

```java
@Retry
@CircuitBreaker
```

Esto debe configurarse cuidadosamente.

## Problema

Si una consulta genera:

```text
Request
  ↓
Chromium
  ↓
Timeout
  ↓
Retry
  ↓
Nuevo Chromium
  ↓
Timeout
  ↓
Retry
```

los recursos pueden multiplicarse rápidamente.

## Regla

El retry debe repetir la operación lógica, pero **no debe provocar innecesariamente la creación de nuevas instancias completas del navegador**.

El pool debe controlar el ciclo de vida del navegador.

---

# 7. No devolver `null` como fallback

## ❌ Evitar

```java
public Map<String, Object> fallbackScrape(
        String nit,
        Throwable t) {

    return null;
}
```

Un `null` no permite distinguir entre:

```text
No existe información
```

```text
El RUT no fue encontrado
```

```text
El sitio está caído
```

```text
Timeout
```

```text
Error de Playwright
```

```text
Error al descargar PDF
```

## ✅ Preferir

Devolver una respuesta estructurada:

```json
{
  "success": false,
  "error": "SCRAPER_TIMEOUT",
  "message": "No fue posible obtener respuesta de VigenciaRUT"
}
```

O lanzar una excepción de dominio que sea procesada por el `GlobalExceptionHandler`.

---

# 8. Registrar errores útiles

El scraper debe registrar suficiente información para diagnosticar fallos.

Como mínimo:

```text
NIT consultado
URL
etapa del proceso
tiempo transcurrido
tipo de error
mensaje de excepción
```

Ejemplo:

```text
SCRAPER_ERROR
nit=900123456
stage=DOWNLOAD_PDF
elapsed=18234ms
error=TimeoutException
```

## No registrar

Nunca almacenar información sensible innecesaria en logs.

---

# 9. Definir etapas del scraping

El scraping debe dividirse conceptualmente en etapas:

```text
1. NORMALIZE_NIT
2. OPEN_SITE
3. FIND_INPUT
4. SUBMIT_QUERY
5. WAIT_RESULT
6. EXTRACT_RESULT
7. DOWNLOAD_SUPPORT
8. PARSE_PDF
9. VALIDATE_DATA
10. RETURN_RESPONSE
```

Esto facilita identificar exactamente dónde está fallando el proceso.

---

# 10. Validar que la página realmente cargó

No asumir que:

```java
page.goto(url);
```

significa que la aplicación está lista.

Después de navegar debe verificarse algún elemento estable:

```java
page.locator("#nit").waitFor();
```

o el elemento equivalente de la página.

También debe contemplarse:

```text
DNS error
timeout
HTTP error
sitio caído
cambio de URL
página de error
bloqueo
```

---

# 11. Detectar cambios del sitio

Debido a que el scraper depende de un sitio externo, debe existir una estrategia para detectar cambios.

Se recomienda tener pruebas automatizadas que validen:

```text
✓ Página carga
✓ Campo NIT existe
✓ Botón Consultar existe
✓ Consulta responde
✓ Resultado aparece
✓ Datos principales existen
✓ Soporte puede descargarse
✓ PDF puede procesarse
```

Una modificación del sitio debería hacer fallar estas pruebas antes de llegar a producción.

---

# 12. Pruebas de concurrencia

El scraper debe probarse con:

```text
1 consulta
5 consultas
10 consultas
20 consultas
50 consultas
```

Medir:

* tiempo promedio
* p95
* p99
* RAM
* CPU
* cantidad de procesos Chromium
* errores
* timeouts
* conexiones Redis
* utilización del pool

El objetivo es evitar que una subida de tráfico genere una cascada de procesos Chromium.

---

# 13. Gestión correcta del BrowserContext

Cada consulta debería tener aislamiento.

Conceptualmente:

```java
Browser browser = pool.acquire();

try {
    BrowserContext context = browser.newContext();

    try {
        Page page = context.newPage();

        // scraping

    } finally {
        context.close();
    }

} finally {
    pool.release(browser);
}
```

No compartir una misma `Page` entre peticiones concurrentes.

---

# 14. Timeouts

Los timeouts deben ser explícitos y diferenciados.

Por ejemplo:

```text
PAGE_LOAD_TIMEOUT
QUERY_TIMEOUT
RESULT_TIMEOUT
DOWNLOAD_TIMEOUT
PDF_TIMEOUT
```

No utilizar un único timeout enorme para todo el proceso.

Esto permite diagnosticar mejor los problemas.

---

# 15. Observabilidad

El proyecto ya incluye Actuator y Micrometer/Prometheus.

Aprovecharlos para medir:

```text
scraper.requests
scraper.success
scraper.errors
scraper.timeouts
scraper.download.errors
scraper.pdf.errors
scraper.duration
scraper.active.browser.sessions
```

Idealmente separar los errores por etapa.

---

# 16. Checklist antes de modificar el scraper

Antes de cambiar código:

* [ ] Verificar HTML actual de `vigenciarut.co`.
* [ ] Verificar selectores actuales.
* [ ] Verificar cómo se ejecuta la consulta.
* [ ] Verificar si existe CAPTCHA.
* [ ] Verificar qué petición HTTP realiza la consulta.
* [ ] Verificar cómo se genera el soporte.
* [ ] Verificar si el soporte es un download real.
* [ ] Verificar si abre una nueva pestaña.
* [ ] Verificar si el PDF se genera dinámicamente.
* [ ] Verificar tiempos reales de respuesta.
* [ ] Revisar comportamiento con concurrencia.

---

# 17. Arquitectura recomendada

La arquitectura objetivo debería ser:

```text
                    ┌────────────────────┐
                    │    REST API        │
                    └─────────┬──────────┘
                              │
                              ▼
                 ┌────────────────────────┐
                 │ VigenciaRutService     │
                 └────────────┬───────────┘
                              │
                              ▼
                 ┌────────────────────────┐
                 │ Browser Pool           │
                 │                        │
                 │ max concurrent = N     │
                 └────────────┬───────────┘
                              │
                              ▼
                       ┌─────────────┐
                       │   Browser   │
                       └──────┬──────┘
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
               Context 1           Context 2
                    │                   │
                    ▼                   ▼
                  Page                Page
                    │                   │
                    └─────────┬─────────┘
                              ▼
                       vigenciarut.co
                              │
                              ▼
                       Resultado / PDF
                              │
                              ▼
                       PDF Parser
                              │
                              ▼
                         Response
```

---

# 18. Prioridad de correcciones

## 🔴 Prioridad crítica

1. Reutilizar `PlaywrightBrowserPool`.
2. Eliminar creación de Chromium por petición.
3. Revisar `waitForDownload()`.
4. Actualizar los selectores contra el HTML actual.
5. Eliminar esperas fijas de `3 segundos`.
6. Revisar Retry + CircuitBreaker.

## 🟠 Prioridad alta

7. Mejorar el manejo de errores.
8. Eliminar `return null` en fallbacks.
9. Procesar PDF en memoria cuando sea viable.
10. Separar timeouts por etapa.

## 🟡 Prioridad media

11. Añadir métricas por etapa.
12. Añadir pruebas de concurrencia.
13. Añadir pruebas de regresión contra el sitio.
14. Mejorar logging.

---

# 19. Regla principal de mantenimiento

> **No modificar selectores, timeouts o lógica de Playwright basándose únicamente en que "funciona en mi navegador".**

Cada cambio debe comprobarse contra:

```text
HTML real
        +
flujo real
        +
concurrencia
        +
descarga real
        +
procesamiento del PDF
```

El scraper debe considerarse una integración externa frágil y debe diseñarse para detectar y tolerar cambios del sitio.

---

# 20. Estado actual

El proyecto utiliza Java 17 y Spring Boot 3.1.4, con Playwright 1.41.0, PDFBox 3.0.1 y Jsoup 1.16.1 como parte del stack de scraping/procesamiento.
Este README debe utilizarse como **checklist técnico antes de realizar cambios en el scraper de VigenciaRUT**.
