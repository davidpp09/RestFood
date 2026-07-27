package restaurante.api.inventario;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un insumo es algo que se cuenta y se controla: pechuga, milanesa, una porción
 * de queso. No todos los ingredientes son insumos — solo los que valen la pena
 * vigilar (control selectivo). Lo que no está aquí, el sistema ni lo mira.
 *
 * Nota deliberada: NO hay campo `stock`. Las existencias se calculan sumando
 * los movimientos. Un número guardado aparte puede acabar contradiciendo su
 * propia historia, y entonces no hay forma de saber cuál de los dos miente.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id_insumos")
@Table(name = "insumos")
@Entity(name = "insumo")
public class Insumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_insumos;

    @Column(unique = true, nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unidad unidad;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stock_minimo;

    @Column(nullable = false)
    private Boolean activo = true;

    public Insumo(DatosRegistroInsumo datos) {
        this.id_insumos = null;
        this.nombre = datos.nombre();
        this.unidad = datos.unidad();
        this.stock_minimo = datos.stock_minimo() != null ? datos.stock_minimo() : 0;
        this.activo = true;
    }

    public void actualizar(DatosActualizacionInsumo datos) {
        if (datos.nombre() != null)       this.nombre       = datos.nombre();
        if (datos.unidad() != null)       this.unidad       = datos.unidad();
        if (datos.stock_minimo() != null) this.stock_minimo = datos.stock_minimo();
        if (datos.activo() != null)       this.activo       = datos.activo();
    }

    /**
     * Baja lógica. Nunca se borra: sus movimientos son historia y la FK los
     * protege, igual que pasa con los productos que ya tienen ventas.
     */
    public void desactivar() {
        this.activo = false;
    }
}
