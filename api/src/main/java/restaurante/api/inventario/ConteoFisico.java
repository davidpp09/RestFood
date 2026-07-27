package restaurante.api.inventario;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import restaurante.api.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Un conteo físico: alguien abrió el refrigerador y contó. Es lo único que
 * convierte el kardex en información y no en un eco de lo que uno mismo tecleó.
 */
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id_conteo")
@Table(name = "conteos_fisicos")
@Entity(name = "conteoFisico")
public class ConteoFisico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_conteo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    private String notas;

    @OneToMany(mappedBy = "conteo", cascade = CascadeType.ALL)
    private List<ConteoDetalle> detalles = new ArrayList<>();

    public ConteoFisico(Usuario usuario, String notas, LocalDateTime fecha) {
        this.usuario = usuario;
        this.notas = notas;
        this.fecha = fecha;
    }

    public void agregar(ConteoDetalle detalle) {
        this.detalles.add(detalle);
    }
}
