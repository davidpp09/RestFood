package restaurante.api.inventario;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import restaurante.api.orden.Orden;
import restaurante.api.producto.Producto;
import restaurante.api.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fase 2: el inventario se mueve solo cuando se manda comanda a la cocina.
 *
 * DOS REGLAS GOBIERNAN TODO ESTE ARCHIVO.
 *
 * 1. **Se descuenta al imprimir la comanda, no al cobrar.** Ese es el momento
 *    en que la carne sale del refrigerador. Descontar al cerrar la cuenta haría
 *    que un platillo cancelado después de cocinado nunca se descontara, y el
 *    teórico mentiría a favor — justo en la dirección que oculta las pérdidas.
 *
 * 2. **El inventario NUNCA detiene una comanda.** Si no alcanza la existencia,
 *    el movimiento se registra igual y el stock queda en negativo. Un negativo
 *    es un dato: significa que entró mercancía que nadie capturó. Bloquear la
 *    comanda pararía la cocina por un problema de captura, que es cambiar un
 *    problema de papeleo por uno de servicio. El negativo se ve en existencias
 *    y ahí es donde debe doler.
 *
 * Un platillo sin receta simplemente no mueve nada: el control es selectivo y
 * la mayoría del menú no toca insumos vigilados.
 */
@Service
public class ConsumoInventarioService {

    @Autowired
    private ProductoInsumoRepository recetaRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    /**
     * Descuenta del kardex lo que consume `cantidad` unidades de `producto`.
     *
     * Se llama con la cantidad que REALMENTE se manda a cocina en esta
     * sincronización: para un platillo nuevo es su cantidad completa, y para uno
     * modificado hacia arriba es solo el incremento — lo anterior ya se
     * descontó cuando se mandó la primera vez.
     */
    @Transactional
    public void descontarPorComanda(Producto producto, int cantidad, Orden orden, Usuario usuario) {
        if (cantidad <= 0) return;

        for (ProductoInsumo linea : recetaDe(producto)) {
            int consumo = linea.getCantidad() * cantidad;
            registrar(linea, TipoMovimiento.VENTA, -consumo, orden, usuario,
                    "Comanda #" + orden.getNumero_comanda() + " — " + cantidad + " × " + producto.getNombre());
        }
    }

    /**
     * Un platillo que ya se mandó a cocina y se cancela NO regresa al
     * inventario: la carne ya se cocinó. Pero tampoco puede quedar contado como
     * venta, porque entonces el food cost saldría bien mientras se tira comida.
     *
     * Se resuelve como en contabilidad, con un asiento de reclasificación: se
     * revierte la VENTA y se registra la MERMA por el mismo importe. El stock
     * no cambia —correcto, la carne ya no está— pero el consumo queda
     * clasificado donde de verdad ocurrió. Así las cancelaciones se convierten
     * en un reporte de dinero tirado en vez de esconderse dentro de las ventas.
     *
     * No se edita el movimiento original ni se borra: el kardex es append-only,
     * y un renglón que desaparece es un historial que ya no explica nada.
     */
    @Transactional
    public void reclasificarCancelacionComoMerma(Producto producto, int cantidad,
                                                 Orden orden, Usuario usuario) {
        if (cantidad <= 0) return;

        for (ProductoInsumo linea : recetaDe(producto)) {
            int consumo = linea.getCantidad() * cantidad;
            String referencia = "Comanda #" + orden.getNumero_comanda() + " — " + producto.getNombre();

            // 1. Revierte la VENTA: el consumo no fue una venta.
            registrar(linea, TipoMovimiento.AJUSTE, consumo, orden, usuario,
                    "Reversa de venta por cancelación. " + referencia);
            // 2. Lo reclasifica como merma: se cocinó y se tiró.
            registrar(linea, TipoMovimiento.MERMA, -consumo, orden, usuario,
                    "Platillo cancelado después de mandarse a cocina. " + referencia);
        }
    }

    private List<ProductoInsumo> recetaDe(Producto producto) {
        return recetaRepository.porProducto(producto.getId_productos());
    }

    private void registrar(ProductoInsumo linea, TipoMovimiento tipo, int cantidadConSigno,
                           Orden orden, Usuario usuario, String motivo) {
        movimientoRepository.save(new MovimientoInventario(
                null, linea.getInsumo(), tipo, cantidadConSigno, motivo, orden, usuario, LocalDateTime.now()));
    }
}
