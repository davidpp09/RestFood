package restaurante.api.ordenDetalle;

/**
 * Tiempos marcados en una orden PARA LLEVAR (1er tiempo: consomé y sopa/crema;
 * 2do tiempo: arroz y espaguetti). Campo opcional del payload de sincronizar
 * comanda: si viene y trae algún valor, se imprime un talón de tiempos en la
 * impresora de la zona de repartidores.
 */
public record DatosTiemposComanda(
        Integer consome,
        Integer sopa_crema,
        Integer arroz,
        Integer espaguetti
) {
    private static int v(Integer n) {
        return n == null ? 0 : Math.max(0, n);
    }

    public int consomeSeguro()    { return v(consome); }
    public int sopaCremaSegura()  { return v(sopa_crema); }
    public int arrozSeguro()      { return v(arroz); }
    public int espaguettiSeguro() { return v(espaguetti); }

    public boolean tieneAlguno() {
        return consomeSeguro() > 0 || sopaCremaSegura() > 0 || arrozSeguro() > 0 || espaguettiSeguro() > 0;
    }
}
