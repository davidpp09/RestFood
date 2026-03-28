package restaurante.api.usuario;

import restaurante.api.mesa.Estado;

public record DatosListaUsuario(
        Long id_usuarios,
        String nombre,
        Roles rol ,
        String constrasena,
        Boolean estatus,
        String email
) {
    public DatosListaUsuario(Usuario usuario) {
        this(usuario.getId_usuarios(), usuario.getNombre(),usuario.getRol(),usuario.getContrasena(),usuario.getEstatus(),usuario.getEmail());
    }
}
