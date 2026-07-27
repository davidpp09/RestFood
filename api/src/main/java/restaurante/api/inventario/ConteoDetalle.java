package restaurante.api.inventario;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Lo contado frente a lo que el kardex decía en ese momento.
 *
 * `cantidad_teorica` se guarda aunque se pueda recalcular: después del AJUSTE
 * el kardex ya cuadra, y sin esta foto la varianza histórica se perdería. Es
 * justo el número que dice si algo se está yendo.
 */
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id_conteo_detalle")
@Table(name = "conteo_detalle")
@Entity(name = "conteoDetalle")
public class ConteoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_conteo_detalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_conteo", nullable = false)
    private ConteoFisico conteo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_insumo", nullable = false)
    private Insumo insumo;

    @Column(name = "cantidad_contada", nullable = false)
    private Integer cantidad_contada;

    @Column(name = "cantidad_teorica", nullable = false)
    private Integer cantidad_teorica;

    public ConteoDetalle(ConteoFisico conteo, Insumo insumo, int contada, int teorica) {
        this.conteo = conteo;
        this.insumo = insumo;
        this.cantidad_contada = contada;
        this.cantidad_teorica = teorica;
    }

    /** Positiva: sobró. Negativa: faltó. Cero: cuadró. */
    public int varianza() {
        return cantidad_contada - cantidad_teorica;
    }
}
