package restaurante.api.controller.ordenes;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que protege este test: el nombre con el que la papelería recibe el menú.
 *
 * Es lo primero que ve quien lo imprime, y llega por WhatsApp sin más contexto,
 * así que un día de la semana equivocado manda a imprimir el menú del día que no
 * es. Vale la pena fijarlo aunque parezca una tontería de formato.
 */
class MenuDiaControllerTest {

    @Test
    void el_nombre_lleva_el_dia_de_la_semana_y_la_fecha() {
        // 2026-08-01 fue sábado.
        assertEquals("sabado-01-08-26",
                MenuDiaController.nombreDelArchivo(LocalDate.of(2026, 8, 1)));
    }

    @Test
    void los_siete_dias_salen_en_su_lugar() {
        // Semana completa del lunes 2026-08-03 al domingo 2026-08-09.
        String[] esperados = {
                "lunes-03-08-26", "martes-04-08-26", "miercoles-05-08-26", "jueves-06-08-26",
                "viernes-07-08-26", "sabado-08-08-26", "domingo-09-08-26"};

        for (int i = 0; i < 7; i++) {
            assertEquals(esperados[i],
                    MenuDiaController.nombreDelArchivo(LocalDate.of(2026, 8, 3).plusDays(i)));
        }
    }

    /**
     * El archivo viaja por WhatsApp y acaba en la computadora de la papelería. Un
     * acento en el nombre puede llegar como "mi%C3%A9rcoles" o directamente roto,
     * así que "miércoles" y "sábado" van sin él a propósito.
     */
    @Test
    void ningun_dia_lleva_acentos_ni_mayusculas() {
        for (int i = 0; i < 7; i++) {
            String nombre = MenuDiaController.nombreDelArchivo(LocalDate.of(2026, 8, 3).plusDays(i));
            assertTrue(nombre.matches("[a-z]+-\\d{2}-\\d{2}-\\d{2}"),
                    "El nombre trae algo que no es letra minúscula sin acento, guion o dígito: " + nombre);
        }
    }

    @Test
    void el_dia_y_el_mes_van_con_dos_digitos() {
        assertEquals("viernes-02-01-26",
                MenuDiaController.nombreDelArchivo(LocalDate.of(2026, 1, 2)));
    }
}
