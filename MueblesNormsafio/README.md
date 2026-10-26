# MueblesNormsafio

Plugin simple de muebles para Paper 1.21.1 (Java 21). Usa los modelos del texture pack
`Normsafio_TexturePack_v2.zip` (item `BRICK` con CustomModelData 7001-7014).

## Compilar
Necesitas JDK 21 y Maven:

    mvn clean package

El jar sale en `target/MueblesNormsafio.jar`. Copialo a `plugins/` y reinicia el servidor.

## Uso
- `/mueble` abre el menu (permiso `muebles.menu`, por defecto solo op). Shift+clic = 16 unidades.
- `/mueble dar <id> [jugador] [cantidad]` y `/mueble lista` (permiso `muebles.admin`).
- Clic derecho en un bloque con el mueble en la mano: lo coloca mirando hacia ti.
- Clic derecho en el mueble: sentarse (silla, taburete, sofa x2, cama).
- Clic derecho con un **palo**: gira el mueble 45 grados.
- Clic izquierdo: lo rompe y devuelve el item.
- Agachado + mano vacia + clic derecho: lo recoge (alternativa por si el clic izquierdo no se detecta).

Permiso `muebles.usar` (por defecto todos): colocar, sentarse, girar, recoger.
Al colocar se lanza un BlockPlaceEvent, asi que WorldGuard y similares lo respetan.

## Ajustes (config.yml)
- `yaw-offset`: si el frente del mueble queda al reves, pon 180 (o 90/270).
- `seat-offset`: altura al sentarse; sube/baja unas decimas si queda hundido o flotando.

## Muebles
silla, taburete, mesa, mesita, sofa, lampara, estanteria, tv, planta, nevera, armario, alfombra, reloj, cama

## Notas
- Los muebles son entidades (ItemDisplay + Interaction), no bloques: no hay colisiones solidas.
- Para anadir un mueble: modelo en el pack, entrada nueva en `Furniture.java` y su CMD en `brick.json`.
