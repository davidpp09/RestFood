package restaurante.api.ordenDetalle;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import restaurante.api.orden.DatosAgregarPlatillo;
import restaurante.api.orden.Orden;
import restaurante.api.producto.Producto;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id_detalle")
@Table(name = "orden_detalle")
@Entity(name = "ordenDetalle")
public class OrdenDetalle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_detalle;
    private Integer cantidad;
    private BigDecimal precio_unitario;
    private BigDecimal subtotal;
    private String comentarios;
    @ManyToOne
    @JoinColumn(name = "id_orden")
    private Orden orden;
    @ManyToOne
    @JoinColumn(name = "id_producto")
    private Producto producto;


    public OrdenDetalle(DatosPlatilloLote datos,Producto producto, Orden orden) {
        this.id_detalle = null;
        this.cantidad = datos.cantidad();
        this.precio_unitario = producto.getPrecio_comida();
        this.subtotal = BigDecimal.valueOf(datos.cantidad()).multiply(producto.getPrecio_comida());
        this.comentarios = datos.comentarios();
        this.orden = orden;
        this.producto = producto;
    }

    public void actualizarPlatillo(DatosPlatilloLote datos) {
        this.cantidad = datos.cantidad();
        this.comentarios = datos.comentarios();
        this.subtotal = this.precio_unitario.multiply(BigDecimal.valueOf(this.cantidad));
    }
}
