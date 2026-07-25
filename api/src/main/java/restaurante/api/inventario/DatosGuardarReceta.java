package restaurante.api.inventario;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Reemplaza por completo la receta de un insumo: los platillos que no vengan
 * en la lista quedan desligados.
 *
 * Se manda todo junto y no platillo por platillo porque así la pantalla puede
 * ser "marca los que lleven pechuga y guarda". Con altas y bajas sueltas, una
 * desmarcada perdida dejaría una relación fantasma descontando inventario sin
 * que nadie lo note.
 */
public record DatosGuardarReceta(
        @Valid List<DatosRecetaLinea> lineas
) {
}
