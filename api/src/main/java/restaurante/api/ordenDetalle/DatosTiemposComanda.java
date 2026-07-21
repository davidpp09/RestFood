package restaurante.api.ordenDetalle;

/**
 * Tiempos/opciones marcados en una orden PARA LLEVAR. Campo opcional del payload
 * de sincronizar comanda: si viene y trae algún valor, se imprime un talón en la
 * impresora de la zona de repartidores (o en COCINA2 para repartidores
 * configurados, p. ej. SRA.ANGELES).
 *
 * Según el servicio de la orden se llena un grupo u otro:
 *  - COMIDA:   1er tiempo (consomé, sopa/crema) y 2do tiempo (arroz, espaguetti).
 *  - DESAYUNO: bebida (café, jugo).
 */
public record DatosTiemposComanda(
        Integer consome,
        Integer sopa_crema,
        Integer arroz,
        Integer espaguetti,
        Integer cafe,
        Integer jugo
) {
    private static int v(Integer n) {
        return n == null ? 0 : Math.max(0, n);
    }

    public int consomeSeguro()    { return v(consome); }
    public int sopaCremaSegura()  { return v(sopa_crema); }
    public int arrozSeguro()      { return v(arroz); }
    public int espaguettiSeguro() { return v(espaguetti); }
    public int cafeSeguro()       { return v(cafe); }
    public int jugoSeguro()       { return v(jugo); }

    /** true si hay algún tiempo de COMIDA marcado. */
    public boolean tieneComida() {
        return consomeSeguro() > 0 || sopaCremaSegura() > 0 || arrozSeguro() > 0 || espaguettiSeguro() > 0;
    }

    /** true si hay alguna bebida de DESAYUNO marcada. */
    public boolean tieneDesayuno() {
        return cafeSeguro() > 0 || jugoSeguro() > 0;
    }

    public boolean tieneAlguno() {
        return tieneComida() || tieneDesayuno();
    }
}
