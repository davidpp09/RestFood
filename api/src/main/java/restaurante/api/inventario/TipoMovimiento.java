package restaurante.api.inventario;

/**
 * El signo de cada tipo no es libre: lo impone el propio significado del
 * movimiento. `signoEsperado` lo hace explícito para poder validarlo en un
 * solo lugar en vez de confiar en que quien llame lo mande bien.
 *
 * AJUSTE es el único que puede ir en las dos direcciones, porque un conteo
 * físico tanto puede encontrar de más como de menos.
 */
public enum TipoMovimiento {
    INICIAL(1),
    COMPRA(1),
    MERMA(-1),
    VENTA(-1),
    AJUSTE(0),
    /**
     * Deshace un movimiento anterior porque cambió su causa, no porque el
     * número estuviera mal. Hoy solo la usa la cancelación de un platillo ya
     * mandado a cocina: revierte la VENTA para que la MERMA ocupe su lugar.
     *
     * Existe separada de AJUSTE a propósito. AJUSTE es la diferencia de un
     * conteo físico — lo que NADIE pudo explicar, y el número por el que existe
     * el reporte de teórico contra real. Una reversa está explicada al detalle,
     * así que mezclarlas inflaba la varianza con ruido.
     */
    REVERSA(0);

    private final int signoEsperado;

    TipoMovimiento(int signoEsperado) {
        this.signoEsperado = signoEsperado;
    }

    /** true si `cantidad` va en la dirección que este tipo permite. */
    public boolean permiteCantidad(int cantidad) {
        if (cantidad == 0) return false;              // un movimiento de cero no es un movimiento
        if (signoEsperado == 0) return true;          // AJUSTE: cualquier dirección
        return Integer.signum(cantidad) == signoEsperado;
    }
}
