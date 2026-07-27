package restaurante.api.inventario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La varianza es el número por el que existe todo esto: la diferencia entre lo
 * que el kardex creía y lo que de verdad hay en el refrigerador. El signo
 * importa y es fácil invertirlo sin darse cuenta — un faltante que apareciera
 * como sobrante sería exactamente el error que taparía lo que se busca.
 */
class ConteoDetalleTest {

    private static final Insumo PECHUGA = new Insumo(
            new DatosRegistroInsumo("Pechuga de pollo", Unidad.PIEZA, 20));

    @Test
    @DisplayName("Si hay menos de lo que decía el kardex, la varianza es negativa")
    void faltanteEsNegativo() {
        // El kardex decía 50, en el refri hay 44: faltan 6.
        var detalle = new ConteoDetalle(null, PECHUGA, 44, 50);
        assertEquals(-6, detalle.varianza(),
                "Un faltante tiene que verse como faltante, no como sobrante.");
    }

    @Test
    @DisplayName("Si hay más de lo que decía el kardex, la varianza es positiva")
    void sobranteEsPositivo() {
        // Casi siempre significa una compra que nadie capturó.
        var detalle = new ConteoDetalle(null, PECHUGA, 53, 50);
        assertEquals(3, detalle.varianza());
    }

    @Test
    @DisplayName("Cuando cuadra, la varianza es cero y no se genera ajuste")
    void cuadrarDaCero() {
        var detalle = new ConteoDetalle(null, PECHUGA, 50, 50);
        assertEquals(0, detalle.varianza());
    }
}
