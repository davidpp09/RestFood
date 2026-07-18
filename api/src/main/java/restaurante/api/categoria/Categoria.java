package restaurante.api.categoria;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id_categorias")
@Table(name = "categorias")
@Entity(name = "categoria")
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_categorias;
    @Column(unique = true, nullable = false)
    private String nombre;
    @Column(nullable = false)
    private String impresora;


    public Categoria(DatosRegistroCategoria datosRegistroCategoria) {
        this.id_categorias = null;
        this.nombre = datosRegistroCategoria.nombre();
        this.impresora = datosRegistroCategoria.impresora();
    }
}
