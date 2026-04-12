package restaurante.api.mesa;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id_mesas")
@Table(name = "mesas")
@Entity(name = "mesa")
public class Mesa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_mesas;

    @Column(unique = true, nullable = false)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    public Mesa(Long id_mesas) {
        this.id_mesas = id_mesas;
    }

    public Mesa(DatosRegistroMesa datosRegistroMesa) {
        this.id_mesas = null;
        this.numero = datosRegistroMesa.numero();
        this.estado = Estado.LIBRE;
    }

    public void abrirMesa() {
        if (this.estado == Estado.OCUPADA) {
            throw new restaurante.api.infra.errores.ValidacionException("La mesa ya está en uso, no se puede abrir otra cuenta.");
        }
        this.estado = Estado.OCUPADA;
    }

    public void liberar() {
        this.estado = Estado.LIBRE;
    }
}
