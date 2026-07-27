package restaurante.api.inventario;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import restaurante.api.orden.Orden;
import restaurante.api.usuario.Usuario;

import java.time.LocalDateTime;

/**
 * Un renglón del kardex. Append-only: no hay setters, no hay método
 * `actualizar()`, y eso es a propósito. Un error no se edita — se corrige con
 * otro movimiento que lo compensa, igual que en contabilidad. Así el historial
 * siempre explica cómo se llegó a las existencias de hoy.
 *
 * `cantidad` lleva signo: positivo entra, negativo sale.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id_movimiento")
@Table(name = "movimientos_inventario")
@Entity(name = "movimientoInventario")
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_movimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_insumo", nullable = false)
    private Insumo insumo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimiento tipo;

    @Column(nullable = false)
    private Integer cantidad;

    private String motivo;

    /**
     * Solo COMPRA lo llena: lo pagado EN TOTAL por esta entrada, tal como viene
     * en la nota del proveedor. El unitario se deriva; nunca se captura.
     * Nullable: una compra sin costo sigue contando piezas (ver V4).
     */
    @Column(name = "costo_total")
    private java.math.BigDecimal costo_total;

    /** Solo lo llenan VENTA y la MERMA por cancelación (Fase 2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden")
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public MovimientoInventario(Insumo insumo, TipoMovimiento tipo, int cantidad,
                                String motivo, Usuario usuario, LocalDateTime fecha) {
        this.insumo = insumo;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.motivo = motivo;
        this.usuario = usuario;
        this.fecha = fecha;
        this.orden = null;
        this.costo_total = null;
    }

    /** Compra con costo: el único caso en que el dinero entra al kardex. */
    public MovimientoInventario(Insumo insumo, TipoMovimiento tipo, int cantidad,
                                String motivo, java.math.BigDecimal costoTotal,
                                Usuario usuario, LocalDateTime fecha) {
        this(insumo, tipo, cantidad, motivo, usuario, fecha);
        this.costo_total = costoTotal;
    }
}
