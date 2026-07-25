package restaurante.api.inventario;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import restaurante.api.producto.Producto;

/**
 * Un renglón de receta: cuánto de un insumo consume un platillo.
 *
 * Un platillo sin renglones aquí simplemente no descuenta nada — así conviven
 * los 9 insumos controlados con los 225 productos del menú sin tener que
 * declarar recetas para todo.
 */
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id_producto_insumo")
@Table(name = "producto_insumo")
@Entity(name = "productoInsumo")
public class ProductoInsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_producto_insumo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_insumo", nullable = false)
    private Insumo insumo;

    /** En la unidad del insumo. Entera: 1 pechuga, 2 milanesas, 1 porción de queso. */
    @Column(nullable = false)
    private Integer cantidad;

    public ProductoInsumo(Producto producto, Insumo insumo, int cantidad) {
        this.producto = producto;
        this.insumo = insumo;
        this.cantidad = cantidad;
    }

    public void cambiarCantidad(int cantidad) {
        this.cantidad = cantidad;
    }
}
