package com.redprocesal.scraperapi.parser;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompanyHtmlParserTest {

@Test
    void testParseHtmlSuccess() {
        String htmlMock = "<html><body>" +
                "<div>EMPRESA DE PRUEBAS S.A.S.</div>" +
                "<div><span class='label'>Identificación:</span> <span class='value'>900123456-1</span></div>" +
                "<div><span class='label'>Inscripción:</span> <span class='value'>12345</span></div>" +
                "<div><span class='label'>Categoría:</span> <span class='value'>Principal</span></div>" +
                "<div><span class='label'>Cámara de Comercio:</span> <span class='value'>Cartagena</span></div>" +
                "<div><span class='label'>Matrícula:</span> <span class='value'>654321</span></div>" +
                "<div><span class='label'>Estado:</span> <span class='value'>ACTIVO</span></div>" +
                "</body></html>";

        Map<String, String> result = CompanyHtmlParser.parse(htmlMock);

        assertNotNull(result, "El resultado no debería ser nulo");
        assertEquals("EMPRESA DE PRUEBAS S.A.S.", result.get("razonSocial"));
        assertEquals("900123456-1", result.get("identificacion"));
        assertEquals("ACTIVO", result.get("estado"));
    }
    @Test
    void testParseHtmlReturnsNullWhenIncomplete() {
        String htmlIncompleto = "<html><body>" +
                "<h1>EMPRESA INCOMPLETA</h1>" +
                "<div>Estado: ACTIVO</div>" +
                "</body></html>";

        Map<String, String> result = CompanyHtmlParser.parse(htmlIncompleto);

        assertNull(result, "Debería retornar nulo si encuentra menos de 4 campos clave");
    }

    @Test
    void testParseHtmlWithNullOrBlank() {
        assertNull(CompanyHtmlParser.parse(null));
        assertNull(CompanyHtmlParser.parse("   "));
    }
}