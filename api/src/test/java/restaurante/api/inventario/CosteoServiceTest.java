package restaurante.api.inventario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import restaurante.api.producto.Producto;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * El costeo de la Fase 3. Es dinero derivado de dinero: si el promedio
 * ponderado se calcula mal, TODOS los food cost salen mal a la vez y nadie
 * tiene un número contra el cual notarlo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CosteoServiceTest {

    @Mock private MovimientoInventarioRepository movimientoRepository;
    @Mock private ProductoInsumoRepository recetaRepository;

    @InjectMocks private CosteoService servicio;

    private Insumo insumo(long id, String nombre) {
        Insumo i = Mockito.mock(Insumo.class);
        when(i.getId_insumos()).thenReturn(id);
        when(i.getNombre()).thenReturn(nombre);
        return i;
    }

    private MovimientoInventario compra(Insumo insumo, int cantidad, String costoTotal) {
        MovimientoInventario m = Mockito.mock(MovimientoInventario.class);
        when(m.getInsumo()).thenReturn(insumo);
        when(m.getCantidad()).thenReturn(cantidad);
        when(m.getCosto_total()).thenReturn(new BigDecimal(costoTotal));
        return m;
    }

    private Producto producto(long id, String nombre, String precioComida) {
        Producto p = Mockito.mock(Producto.class);
        when(p.getId_productos()).thenReturn(id);
        when(p.getNombre()).thenReturn(nombre);
        when(p.getPrecio_comida()).thenReturn(new BigDecimal(precioComida));
        when(p.getPrecio_desayuno()).thenReturn(new BigDecimal(precioComida));
        return p;
    }

    private ProductoInsumo linea(Producto p, Insumo i, int cantidad) {
        ProductoInsumo l = Mockito.mock(ProductoInsumo.class);
        when(l.getProducto()).thenReturn(p);
        when(l.getInsumo()).thenReturn(i);
        when(l.getCantidad()).thenReturn(cantidad);
        return l;
    }

    @Test
    @DisplayName("El promedio es PONDERADO: la compra grande pesa más")
    void promedioPonderado() {
        Insumo pechuga = insumo(1, "Pechuga de pollo");
        // 100 piezas a $30 y 10 piezas a $60: el promedio simple diría $45,
        // pero la compra de pánico fue la chica. Ponderado: 3600/110 = 32.7273
        var compras = List.of(compra(pechuga, 100, "3000.00"), compra(pechuga, 10, "600.00"));
        when(movimientoRepository.comprasConCosto()).thenReturn(compras);

        var promedio = servicio.costoPromedioPorInsumo();

        assertEquals(0, new BigDecimal("32.7273").compareTo(promedio.get(1L)),
                "3600 pagados / 110 piezas. El promedio simple (45) dejaría que una "
                        + "compra chica a precio alto moviera el costo como si fuera la norma.");
    }

    @Test
    @DisplayName("El food cost cruza receta, promedio y precio de venta")
    void foodCostDeUnPlatillo() {
        Insumo carne = insumo(21, "Carne molida");
        var compras = List.of(compra(carne, 40, "1000.00"));   // $25 la porción
        when(movimientoRepository.comprasConCosto()).thenReturn(compras);

        Producto empanadas = producto(207, "Empanadas de platano", "100");
        var receta = List.of(linea(empanadas, carne, 2));      // 2 porciones por orden
        when(recetaRepository.todas()).thenReturn(receta);

        var reporte = servicio.foodCost();
        assertEquals(1, reporte.size());
        var fila = reporte.get(0);

        assertEquals(0, new BigDecimal("50.00").compareTo(fila.costo_insumos()),
                "2 porciones × $25 = $50 de carne por orden.");
        assertEquals(0, new BigDecimal("50.0").compareTo(fila.food_cost_pct()),
                "$50 de costo sobre $100 de precio = 50%. Muy arriba del 30-35% sano: "
                        + "este platillo casi no deja dinero, y eso es justo lo que "
                        + "este reporte existe para mostrar.");
        assertFalse(fila.costo_incompleto());
    }

    @Test
    @DisplayName("Un insumo sin compras con costo marca el platillo como incompleto")
    void insumoSinCostoMarcaIncompleto() {
        Insumo carne = insumo(21, "Carne molida");
        Insumo queso = insumo(19, "Queso manchego");
        var compras = List.of(compra(carne, 40, "1000.00"));   // el queso no tiene ni una compra con costo
        when(movimientoRepository.comprasConCosto()).thenReturn(compras);

        Producto platillo = producto(50, "Empanadas con queso", "100");
        var receta = List.of(linea(platillo, carne, 2), linea(platillo, queso, 1));
        when(recetaRepository.todas()).thenReturn(receta);

        var fila = servicio.foodCost().get(0);

        assertTrue(fila.costo_incompleto(),
                "El queso suma cero porque no tiene precio todavía. El número es un "
                        + "piso y hay que decirlo: un costo incompleto que se cree "
                        + "completo lleva a subir precios que no había que subir.");
        assertEquals(0, new BigDecimal("50.00").compareTo(fila.costo_insumos()),
                "Solo la carne: 2 × $25. El queso entra cuando tenga su primera compra con costo.");
    }

    @Test
    @DisplayName("Ordenado del food cost más alto al más bajo")
    void ordenadoDelPeorAlMejor() {
        Insumo carne = insumo(21, "Carne molida");
        var compras = List.of(compra(carne, 10, "250.00"));    // $25
        when(movimientoRepository.comprasConCosto()).thenReturn(compras);

        Producto caro = producto(1, "Casi regalado", "50");   // 25/50 = 50%
        Producto sano = producto(2, "Buen margen", "250");    // 25/250 = 10%
        var receta = List.of(linea(sano, carne, 1), linea(caro, carne, 1));
        when(recetaRepository.todas()).thenReturn(receta);

        var reporte = servicio.foodCost();
        assertEquals("Casi regalado", reporte.get(0).producto(),
                "Arriba el que menos dinero deja: es al que hay que mirarle el precio.");
        assertEquals("Buen margen", reporte.get(1).producto());
    }

    @Test
    @DisplayName("Sin compras con costo, el reporte no explota: todo sale incompleto")
    void sinCostosNoExplota() {
        Insumo carne = insumo(21, "Carne molida");
        when(movimientoRepository.comprasConCosto()).thenReturn(List.of());
        var receta = List.of(linea(producto(207, "Empanadas", "100"), carne, 2));
        when(recetaRepository.todas()).thenReturn(receta);

        var fila = servicio.foodCost().get(0);
        assertEquals(0, BigDecimal.ZERO.compareTo(fila.costo_insumos()));
        assertTrue(fila.costo_incompleto());
    }
}
