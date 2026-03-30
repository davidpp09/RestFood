package restaurante.api.orden;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import restaurante.api.mesa.Mesa;
import restaurante.api.ordenDetalle.OrdenDetalle;
import restaurante.api.usuario.Usuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id_ordenes")
@Table(name = "ordenes")
@Entity(name = "orden")
public class Orden {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_ordenes;
    private LocalDateTime fecha_apertura;
    @Enumerated(EnumType.STRING)
    private Estatus estatus;
    private BigDecimal total;
    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private @NotNull Usuario usuario;
    @ManyToOne
    @JoinColumn(name = "id_mesa")
    private Mesa mesa;
    @Enumerated(EnumType.STRING)
    private Tipo tipo;



    public Orden(Long aLong) {
        this.id_ordenes = aLong;
    }

    public Orden(Mesa mesa, Usuario usuario, Tipo tipo) {
        this.id_ordenes = null;
        this.fecha_apertura = LocalDateTime.now();
        this.estatus = Estatus.PREPARANCDO;
        this.total = BigDecimal.valueOf(0);
        this.usuario = usuario;
        this.mesa = mesa;
        this.tipo = tipo;
    }

    public void recalcularTotal(List<OrdenDetalle> detalles) {
        this.total = BigDecimal.ZERO;

        for (OrdenDetalle platillo : detalles) {
            this.total = this.total.add(platillo.getSubtotal());
        }
    }


}
