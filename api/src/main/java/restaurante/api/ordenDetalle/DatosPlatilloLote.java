package restaurante.api.ordenDetalle;

public record DatosPlatilloLote(

        Long id_detalle,
        Long id_producto,
        Integer cantidad,
        String comentarios

) {

}
