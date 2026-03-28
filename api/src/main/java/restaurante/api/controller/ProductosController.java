package restaurante.api.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import restaurante.api.mesa.DatosRegistroMesa;
import restaurante.api.producto.DatosRegistroProducto;
import restaurante.api.producto.Producto;
import restaurante.api.producto.ProductoRepository;

@RequestMapping("/productos")
@RestController
public class ProductosController {

    @Autowired
    ProductoRepository repository;

    @PostMapping
    public void registrar(@RequestBody @Valid DatosRegistroProducto datosRegistroProducto){
        repository.save(new Producto(datosRegistroProducto));
    }
}
