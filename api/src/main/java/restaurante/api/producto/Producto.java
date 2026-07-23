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

    @Column(unique = true, nullable = false)
    private String nombre;

    @Column(name = "precio_comida", nullable = false)
    private BigDecimal precio_comida;

    @Column(name = "precio_desayuno", nullable = false)
    private BigDecimal precio_desayuno;

    @Column(nullable = false)
    private Boolean disponibilidad;

    @Column(nullable = false)
    private Boolean eliminado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria", nullable = false)
    private Categoria categoria;

    public Producto(DatosRegistroProducto datosRegistroProducto, Categoria categoria) {
        this.id_productos = null;
        this.nombre = datosRegistroProducto.nombre();
        this.precio_comida = datosRegistroProducto.precio_comida();
        this.precio_desayuno = datosRegistroProducto.precio_desayuno();
        this.disponibilidad = datosRegistroProducto.disponibilidad();
        this.eliminado = false;
        this.categoria = categoria;
    }

    public void actualizar(DatosActualizacionProducto datos, Categoria categoriaCompleta) {
        if (datos.nombre() != null)          this.nombre          = datos.nombre();
        if (datos.precio_comida() != null)   this.precio_comida   = datos.precio_comida();
        if (datos.precio_desayuno() != null) this.precio_desayuno = datos.precio_desayuno();
        if (datos.disponibilidad() != null)  this.disponibilidad  = datos.disponibilidad();
        if (categoriaCompleta != null)       this.categoria        = categoriaCompleta;
    }

    // Borrado suave: conserva el registro para el historial de ventas
    public void marcarEliminado() {
        this.eliminado = true;
        this.disponibilidad = false;
    }

    // Reutiliza un producto marcado como eliminado cuando se registra otro con el mismo nombre
    public void restaurar(DatosRegistroProducto datos, Categoria categoria) {
        this.precio_comida = datos.precio_comida();
        this.precio_desayuno = datos.precio_desayuno();
        this.disponibilidad = datos.disponibilidad();
        this.categoria = categoria;
        this.eliminado = false;
    }

    public void actualizarDia(DatosActualizacionDia datos) {
        if (datos.precio_comida() != null)  this.precio_comida  = datos.precio_comida();
        if (datos.disponibilidad() != null) this.disponibilidad = datos.disponibilidad();
    }
}
