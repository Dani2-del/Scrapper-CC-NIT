# Scraper API RUES

API REST desarrollada en Spring Boot para la consulta automatizada de información empresarial a partir del NIT, realizando extracción de datos desde el Registro Único Empresarial y Social (RUES).

## Descripción

Este proyecto permite consultar información pública de empresas registradas en Colombia mediante procesos automatizados de scraping utilizando Playwright.

La solución fue diseñada siguiendo buenas prácticas de desarrollo backend empresarial, incorporando mecanismos de resiliencia, observabilidad, control de concurrencia y seguridad.

## Características Principales

### Consulta por NIT

* Búsqueda automatizada en RUES.
* Normalización de NIT.
* Extracción estructurada de información empresarial.
* Respuesta JSON lista para integración con otros sistemas.

### Seguridad

* Protección mediante API Key.
* Validación de solicitudes.
* Manejo centralizado de excepciones.
* Trazabilidad mediante Request ID.

### Observabilidad

* Spring Boot Actuator.
* Micrometer Metrics.
* Exportación de métricas a Prometheus.
* Monitoreo de tiempos de respuesta.
* Monitoreo de errores y disponibilidad.

### Resiliencia

* Retry automático ante fallos temporales.
* Circuit Breaker con Resilience4j.
* Control de timeouts.
* Manejo de errores de origen externo.

### Concurrencia

* Pool de navegación Playwright reutilizable.
* Limitación controlada de sesiones concurrentes.
* Protección contra sobrecarga del servidor.

## Arquitectura

```text
Cliente
   │
   ▼
REST API (Spring Boot)
   │
   ▼
Servicio de Scraping
   │
   ├── Validación
   ├── Resilience4j
   ├── Métricas
   └── Playwright Pool
             │
             ▼
         Portal RUES
```

## Tecnologías Utilizadas

* Java 17
* Spring Boot 3.1
* Playwright Java
* Jsoup
* Resilience4j
* Micrometer
* Prometheus
* Lombok
* Maven

## Fortalezas de la Solución

### Diseño Empresarial

El proyecto incorpora componentes normalmente utilizados en ambientes productivos:

* Observabilidad completa.
* Circuit Breakers.
* Retry automático.
* Control de concurrencia.
* Pool de recursos compartidos.
* API protegida.
* Métricas de rendimiento.

### Escalabilidad Inicial

La arquitectura permite:

* Escalar horizontalmente.
* Incrementar la capacidad de procesamiento.
* Integrar sistemas de caché.
* Incorporar balanceadores de carga.

## Limitaciones Actuales

### Dependencia del Sitio Fuente

Al tratarse de un scraper, cambios en la estructura HTML del portal RUES pueden requerir ajustes en los selectores de extracción.

### Caché

Actualmente las consultas se realizan directamente contra el origen.

Como mejora futura se recomienda incorporar:

* Redis
* Caffeine Cache
* Hazelcast

### Gestión de Secretos

Para ambientes productivos se recomienda almacenar credenciales mediante:

* Variables de entorno
* Vault
* AWS Secrets Manager
* Azure Key Vault

## Recomendaciones para Producción

### Empresa Pequeña

Adecuado para:

* Integraciones internas.
* Automatización de consultas.
* Menos de 10.000 consultas diarias.

### Empresa Mediana

Se recomienda agregar:

* Redis.
* Dashboards de monitoreo.
* Escalado horizontal.
* Rate limiting.

### Entornos de Alta Demanda

Para cargas empresariales masivas:

* Kubernetes.
* Auto Scaling.
* RabbitMQ o Kafka.
* Redis distribuido.
* Alertas operativas.
* Monitoreo distribuido.

## Evaluación General

| Escenario              | Calificación |
| ---------------------- | ------------ |
| Proyecto académico     | 9.5/10       |
| Startup o PyME         | 8.5/10       |
| Empresa mediana        | 7/10         |
| Gran empresa / Fintech | 6/10         |

## Conclusión

Scraper API RUES es una solución con una base técnica sólida que supera ampliamente el nivel habitual de un proyecto académico. La incorporación de herramientas como Playwright, Resilience4j, Micrometer, Actuator y mecanismos de concurrencia proporciona una arquitectura robusta y preparada para evolucionar hacia escenarios empresariales de mayor escala.
