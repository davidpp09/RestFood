package restaurante.api.producto;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import restaurante.api.categoria.Categoria;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id_productos")
@Table(name = "productos")
@Entity(name = "producto")
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_productos;
    private String nombre;
    private BigDecimal precio_comida;
    private BigDecimal precio_desayuno;
    private Boolean disponibilidad;
    @ManyToOne
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    public Producto(DatosRegistroProducto datosRegistroProducto) {
        this.id_productos = null;
        this.nombre = datosRegistroProducto.nombre();
        this.precio_comida = datosRegistroProducto.precio_comida();
        this.precio_desayuno = datosRegistroProducto.precio_desayuno();
        this.disponibilidad = datosRegistroProducto.disponibilidad();
        this.categoria = new Categoria(datosRegistroProducto.id_categoria());
    }

    public Producto(Long aLong) {
    }
}
