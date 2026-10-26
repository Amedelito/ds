package es.normsafio.muebles;

import java.util.List;

/** Definicion de un mueble. cmd = CustomModelData sobre BRICK (coincide con el texture pack). */
public record Furniture(String id, String name, int cmd, double width, double height, double seatHeight, double[] seats) {
    public boolean sittable() { return seats != null; }

    public static final List<Furniture> ALL = List.of(
        new Furniture("silla", "Silla", 7001, 0.7, 1.2, 0.62, new double[]{0.0}),
        new Furniture("taburete", "Taburete", 7002, 0.7, 0.75, 0.74, new double[]{0.0}),
        new Furniture("mesa", "Mesa", 7003, 1.2, 0.9, 0.0, null),
        new Furniture("mesita", "Mesita de noche", 7004, 0.9, 0.75, 0.0, null),
        new Furniture("sofa", "Sofa doble", 7005, 2.3, 1.2, 0.65, new double[]{-0.55, 0.55}),
        new Furniture("lampara", "Lámpara de pie", 7006, 0.6, 1.8, 0.0, null),
        new Furniture("estanteria", "Estantería", 7007, 0.95, 2.0, 0.0, null),
        new Furniture("tv", "Televisión", 7008, 1.5, 1.3, 0.0, null),
        new Furniture("planta", "Planta", 7009, 0.7, 1.5, 0.0, null),
        new Furniture("nevera", "Nevera", 7010, 0.95, 2.0, 0.0, null),
        new Furniture("armario", "Armario", 7011, 1.0, 2.0, 0.0, null),
        new Furniture("alfombra", "Alfombra", 7012, 2.0, 0.1, 0.0, null),
        new Furniture("reloj", "Reloj de pie", 7013, 0.8, 2.0, 0.0, null),
        new Furniture("cama", "Cama", 7014, 1.6, 0.9, 0.6, new double[]{0.0})
    );
}
