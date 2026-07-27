package restaurante.api.inventario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import restaurante.api.infra.errores.ValidacionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El signo de un movimiento no se le pide a quien captura: la cocinera dice
 * "se echaron a perder 3", no "menos 3". Que el signo salga del tipo y no de
 * la pantalla es lo que impide que una captura mal hecha sume cuando debía
 * restar — un error que nadie notaría hasta el conteo físico.
 */
class InventarioServiceTest {

    @Test
    @DisplayName("Lo que entra suma: INICIAL y COMPRA quedan en positivo")
    void loQueEntraSuma() {
        assertEquals(20, InventarioService.aplicarSigno(TipoMovimiento.COMPRA, 20));
        assertEquals(15, InventarioService.aplicarSigno(TipoMovimiento.INICIAL, 15));
    }

    @Test
    @DisplayName("Lo que sale resta: MERMA y VENTA se vuelven negativos solos")
    void loQueSaleResta() {
        assertEquals(-3, InventarioService.aplicarSigno(TipoMovimiento.MERMA, 3),
                "Si una merma sumara, el sistema creería tener más de lo que hay.");
        assertEquals(-2, InventarioService.aplicarSigno(TipoMovimiento.VENTA, 2));
    }

    @Test
    @DisplayName("Un movimiento de cero no es un movimiento")
    void ceroNoEsMovimiento() {
        // ValidacionException y no una excepción genérica: es la del proyecto,
        // y es la que TratadorDeErrores convierte en un 400 con mensaje legible
        // en vez de un 500 sin explicación.
        assertThrows(ValidacionException.class,
                () -> InventarioService.aplicarSigno(TipoMovimiento.COMPRA, 0));
    }

    @Test
    @DisplayName("AJUSTE es el único que puede ir en las dos direcciones")
    void ajusteVaEnAmbasDirecciones() {
        // Un conteo físico tanto puede encontrar de más como de menos.
        assertTrue(TipoMovimiento.AJUSTE.permiteCantidad(5));
        assertTrue(TipoMovimiento.AJUSTE.permiteCantidad(-5));
        assertFalse(TipoMovimiento.AJUSTE.permiteCantidad(0));
    }

    @Test
    @DisplayName("Una compra no puede ser negativa ni una merma positiva")
    void cadaTipoRespetaSuDireccion() {
        assertFalse(TipoMovimiento.COMPRA.permiteCantidad(-1));
        assertFalse(TipoMovimiento.MERMA.permiteCantidad(1));
        assertTrue(TipoMovimiento.COMPRA.permiteCantidad(1));
        assertTrue(TipoMovimiento.MERMA.permiteCantidad(-1));
    }
}
