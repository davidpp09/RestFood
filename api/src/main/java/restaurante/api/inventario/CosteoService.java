package restaurante.api.inventario;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fase 3: qué cuesta cada cosa. Todo se deriva de dos fuentes que ya existen —
 * las compras con costo (kardex) y las recetas — así que este servicio no
 * escribe nada: es puro cálculo.
 *
 * El costo de un insumo es el PROMEDIO PONDERADO de sus compras:
 * total pagado / piezas compradas, sobre TODAS las compras que traen costo.
 * Ponderado significa que una compra de 100 piezas pesa más que una de 10 —
 * dividir "promedio de precios" a secas dejaría que una compra chica de pánico
 * a precio alto moviera el costo como si fuera la norma.
 *
 * Se calcula en Java, no en SQL, por lo mismo que teoricoContraReal: se prueba
 * con un test y el volumen lo permite de sobra.
 */
@Service
public class CosteoService {

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    @Autowired
    private ProductoInsumoRepository recetaRepository;

    /** total pagado / piezas comparadas, por insumo. Solo compras con costo. */
    public Map<Long, BigDecimal> costoPromedioPorInsumo() {
        Map<Long, BigDecimal> pagado = new HashMap<>();
        Map<Long, Integer> piezas = new HashMap<>();

        for (MovimientoInventario compra : movimientoRepository.comprasConCosto()) {
            Long id = compra.getInsumo().getId_insumos();
            pagado.merge(id, compra.getCosto_total(), BigDecimal::add);
            piezas.merge(id, compra.getCantidad(), Integer::sum);
        }

        Map<Long, BigDecimal> promedio = new HashMap<>();
        pagado.forEach((id, total) -> {
            int n = piezas.get(id);
            if (n > 0) {
                // 4 decimales internos: el redondeo a centavos se hace al final,
                // en el costo del platillo — redondear aquí acumularía el error
                // una vez por insumo en cada receta.
                promedio.put(id, total.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP));
            }
        });
        return promedio;
    }

    /**
     * El reporte: cada platillo CON RECETA, su costo de insumos vigilados y el
     * porcentaje del precio que se va en ellos. Ordenado del food cost más alto
     * al más bajo — arriba quedan los platillos que menos dinero dejan.
     */
    public List<DatosFoodCost> foodCost() {
        Map<Long, BigDecimal> costoInsumo = costoPromedioPorInsumo();

        // Agrupar las líneas de receta por platillo.
        Map<Long, List<ProductoInsumo>> porProducto = new HashMap<>();
        for (ProductoInsumo linea : recetaRepository.todas()) {
            porProducto.computeIfAbsent(linea.getProducto().getId_productos(), k -> new java.util.ArrayList<>())
                       .add(linea);
        }

        return porProducto.values().stream()
                .map(lineas -> {
                    var producto = lineas.get(0).getProducto();
                    BigDecimal costo = BigDecimal.ZERO;
                    boolean incompleto = false;

                    for (ProductoInsumo linea : lineas) {
                        BigDecimal unitario = costoInsumo.get(linea.getInsumo().getId_insumos());
                        if (unitario == null) {
                            // Ese insumo aún no tiene una sola compra con costo.
                            // Se suma cero y se marca: el costo es un piso.
                            incompleto = true;
                            continue;
                        }
                        costo = costo.add(unitario.multiply(BigDecimal.valueOf(linea.getCantidad())));
                    }

                    return DatosFoodCost.de(
                            producto.getId_productos(), producto.getNombre(), costo,
                            producto.getPrecio_comida(), producto.getPrecio_desayuno(), incompleto);
                })
                .sorted(Comparator.comparing(DatosFoodCost::food_cost_pct).reversed())
                .toList();
    }
}
