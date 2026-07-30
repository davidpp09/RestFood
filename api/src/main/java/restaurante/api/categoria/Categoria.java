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
    // Tope de platillos activos a la vez. "Comida del día" va en 5 porque el
    // recuadro del menú en PDF solo tiene 5 renglones; el resto va en 7.
    @Column(name = "max_activos", nullable = false)
    private Integer maxActivos;


    public Categoria(DatosRegistroCategoria datosRegistroCategoria) {
        this.id_categorias = null;
        this.nombre = datosRegistroCategoria.nombre();
        this.impresora = datosRegistroCategoria.impresora();
        this.maxActivos = TOPE_POR_DEFECTO;
    }

    public static final int TOPE_POR_DEFECTO = 7;
}
