package restaurante.api.inventario;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import restaurante.api.infra.errores.RecursoNoEncontradoException;
import restaurante.api.infra.errores.ValidacionException;
import restaurante.api.producto.ProductoRepository;

import java.util.List;

@Service
public class RecetaService {

    @Autowired
    private InsumoRepository insumoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ProductoInsumoRepository recetaRepository;

    /** El panorama completo: todas las relaciones, para ver repetidos y huecos. */
    public List<DatosRelacionProducto> todas() {
        return recetaRepository.todas().stream().map(DatosRelacionProducto::new).toList();
    }

    public DatosRespuestaReceta obtener(Long idInsumo) {
        Insumo insumo = insumoRepository.findById(idInsumo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el insumo " + idInsumo));
        return DatosRespuestaReceta.de(insumo, recetaRepository.porInsumo(idInsumo));
    }

    /**
     * Reemplaza la receta completa del insumo. Borrar y volver a crear es más
     * simple que reconciliar altas, bajas y cambios — y con un puñado de
     * renglones por insumo el costo es irrelevante frente al riesgo de dejar
     * una relación fantasma descontando inventario en silencio.
     */
    @Transactional
    public DatosRespuestaReceta guardar(Long idInsumo, DatosGuardarReceta datos) {
        Insumo insumo = insumoRepository.findById(idInsumo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el insumo " + idInsumo));

        List<DatosRecetaLinea> lineas = datos.lineas() == null ? List.of() : datos.lineas();

        // Un platillo repetido en la misma receta violaría la restricción única
        // de la base; se atrapa antes para dar un mensaje entendible.
        long distintos = lineas.stream().map(DatosRecetaLinea::id_producto).distinct().count();
        if (distintos != lineas.size()) {
            throw new ValidacionException("Hay un platillo repetido en la receta. Si lleva 2, pon la cantidad en 2.");
        }

        recetaRepository.borrarPorInsumo(idInsumo);
        recetaRepository.flush();

        for (DatosRecetaLinea linea : lineas) {
            var producto = productoRepository.findById(linea.id_producto())
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el platillo " + linea.id_producto()));
            recetaRepository.save(new ProductoInsumo(producto, insumo, linea.cantidad()));
        }
        return DatosRespuestaReceta.de(insumo, recetaRepository.porInsumo(idInsumo));
    }
}
