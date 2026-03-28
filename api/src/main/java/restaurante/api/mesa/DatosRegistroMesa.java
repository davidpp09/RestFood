    package restaurante.api.mesa;

    import jakarta.validation.constraints.NotBlank;
    import jakarta.validation.constraints.NotNull;
    import jakarta.validation.constraints.Size;

    public record DatosRegistroMesa(

            @NotBlank @Size(min = 1, max = 3) String numero    ) {
    }
