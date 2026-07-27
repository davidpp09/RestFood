package restaurante.api.inventario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El renglón del reporte teórico contra real. La varianza en porcentaje es lo
 * único comparable entre insumos: 10 piezas de diferencia en la pechuga —de la
 * que salen 67 platillos al día— no significan lo mismo que 10 en la arrachera,
 * que se mueve 12 veces por semana.
 */
class TeoricoRealTest {

    private Insumo insumo(String nombre) {
        Insumo i = Mockito.mock(Insumo.class);
        Mockito.when(i.getId_insumos()).thenReturn(1L);
        Mockito.when(i.getNombre()).thenReturn(nombre);
        Mockito.when(i.getUnidad()).thenReturn(Unidad.PIEZA);
        return i;
    }

    @Test
    @DisplayName("La varianza se mide contra el consumo, no contra la compra")
    void varianzaComoPorcentajeDelConsumo() {
        // 200 compradas, 180 vendidas, 10 de merma, y el conteo encontró 5 menos.
        var fila = DatosTeoricoReal.de(insumo("Pechuga de pollo"), 200, -180, -10, -5, 5);

        assertEquals(2.63, fila.porcentaje_varianza(),
                "5 sobre un consumo de 190 (180 vendidas + 10 de merma) = 2.63%. "
                        + "Debajo del 3-5% que se considera normal.");
    }

    @Test
    @DisplayName("Sin consumo, la varianza es cero y no divide entre cero")
    void sinConsumoNoRevienta() {
        var fila = DatosTeoricoReal.de(insumo("Arrachera"), 20, 0, 0, 0, 20);
        assertEquals(0.0, fila.porcentaje_varianza(),
                "Un insumo que se compró pero no se ha movido no tiene varianza que reportar.");
    }

    @Test
    @DisplayName("Una varianza alta se ve alta")
    void varianzaAltaDestaca() {
        // Se vendieron 100 y faltan 25: uno de cada cuatro no está explicado.
        var fila = DatosTeoricoReal.de(insumo("Milanesa de res"), 120, -100, 0, -25, -5);
        assertEquals(25.0, fila.porcentaje_varianza(),
                "25 sobre 100 de consumo = 25%. Cinco veces lo tolerable: aquí se mira.");
    }

    @Test
    @DisplayName("El stock negativo se marca aparte")
    void stockNegativoSeMarca() {
        var conNegativo = DatosTeoricoReal.de(insumo("Bistec de res"), 0, -30, 0, 0, -12);
        assertTrue(conNegativo.stock_negativo(),
                "Un negativo significa mercancía que entró y nadie capturó. Mientras "
                        + "haya negativos, la varianza del reporte no significa nada.");

        var sano = DatosTeoricoReal.de(insumo("Bistec de res"), 50, -30, 0, 0, 20);
        assertFalse(sano.stock_negativo());
    }

    @Test
    @DisplayName("Un ajuste POSITIVO también cuenta como varianza")
    void elSobranteTambienEsVarianza() {
        // El conteo encontró 8 de MÁS: casi siempre, una compra sin capturar.
        var fila = DatosTeoricoReal.de(insumo("Queso oaxaca"), 40, -50, 0, 8, 48);
        assertEquals(16.0, fila.porcentaje_varianza(),
                "Sobrar no es mejor que faltar: las dos cosas significan que el "
                        + "kardex no refleja la realidad.");
    }
}
