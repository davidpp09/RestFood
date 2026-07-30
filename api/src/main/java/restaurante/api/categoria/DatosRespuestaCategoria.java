package restaurante.api.categoria;

public record DatosRespuestaCategoria(Long id, String nombre, String impresora, Integer maxActivos) {

    /**
     * maxActivos viaja al front para que la pantalla no tenga que saberse el tope
     * de memoria. Antes estaba escrito a mano en dos lados (aquí y en el panel de
     * platillos del día) y bastaba cambiar uno para que dejaran de coincidir.
     */
    public DatosRespuestaCategoria(Categoria categoria) {
        this(
                categoria.getId_categorias(),
                categoria.getNombre(),
                categoria.getImpresora(),
                categoria.getMaxActivos() != null
                        ? categoria.getMaxActivos()
                        : Categoria.TOPE_POR_DEFECTO
        );
    }
}
