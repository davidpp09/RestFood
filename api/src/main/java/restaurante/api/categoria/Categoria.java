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
    private String nombre;
    private String impresora;


    public Categoria(Long id_categorias) {
        this.id_categorias = id_categorias;
    }

    public Categoria(DatosRegistroCategoria datosRegistroCategoria) {
        this.id_categorias = null;
        this.nombre = datosRegistroCategoria.nombre();
        this.impresora = datosRegistroCategoria.impresora();
    }
}
