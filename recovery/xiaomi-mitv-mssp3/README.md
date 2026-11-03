# Restaurar la Xiaomi MiTV-MSSP3 al estado original

Este directorio conserva la recuperación de la Xiaomi P1E con Android 9 configurada el **3 de noviembre de 2026**. Su único destino es la configuración anterior a la limpieza y a la instalación de Luma. FLauncher no es una alternativa de recuperación.

El script y los registros proceden de la intervención realizada en esa tele. Las copias publicadas conservan los estados necesarios para restaurar la configuración, con la dirección de red y las rutas de instalación particulares retiradas. Los registros íntegros se conservan únicamente en almacenamiento local privado.

## Qué restaura

1. Comprueba la conexión autorizada y el modelo `MiTV-MSSP3`.
2. Habilita primero el inicio original de Google y PatchWall.
3. Recupera el inicio `com.google.android.tvlauncher/.MainActivity` y los estados originales de `com.miui.tv.analytics`, `com.xiaomi.statistic`, `com.google.android.tvlauncher` y `com.mitv.tvhome.atv`.
4. Restablece y verifica las tres escalas de animación: `1.0`, `1.0` y ausencia de valor explícito para `animator_duration_scale`.
5. Comprueba que Home resuelve al inicio original y lo abre.
6. Retira Luma y cualquier resto de FLauncher que se hubiera añadido durante esta intervención. Las aplicaciones y datos que existían antes se conservan.

Si no puede verificar el inicio original, los estados o las animaciones, se detiene antes de desinstalar los launchers añadidos.

## Archivos que deben conservarse

| Archivo | Contenido |
| --- | --- |
| [restaurar-xiaomi.py](restaurar-xiaomi.py) | Script de restauración. |
| [xiaomi-estado-antes-optimizacion.json](xiaomi-estado-antes-optimizacion.json) | Inicio, estados de paquetes y animaciones originales. Es la referencia de restauración. |
| [xiaomi-estado-antes-luma.json](xiaomi-estado-antes-luma.json) | Registro que confirma que Luma no estaba instalado antes. Su configuración de FLauncher es histórica y no se usa como destino de restauración. |
| [xiaomi-prueba-restauracion-original.json](xiaomi-prueba-restauracion-original.json) | Resultado de la restauración completa verificada en la TV el 3 de noviembre de 2026. |

Los dos archivos de estado deben permanecer junto al script. No se deben sustituir por una captura de la TV ya modificada: se perdería la referencia original. El campo `serial` publicado es `null`; la dirección o el identificador ADB se proporciona en cada ejecución con `--address`.

## Requisitos

- Python 3 y [ADB de Android Platform Tools](https://developer.android.com/tools/releases/platform-tools).
- El ordenador y la tele en la misma red.
- Depuración habilitada y conexión del ordenador autorizada en la pantalla de la TV.

El script busca ADB, en este orden: `--adb`, `.local/android-tools/platform-tools/adb` y el `PATH`. Usa el puerto **5038** del servidor ADB que se empleó durante la intervención. Las claves locales se guardan en `.local/adb-state/`.

`.local/` está excluido de Git: ni las herramientas descargadas, ni las claves ADB, ni los registros de ejecución se publican con el código. Un clon nuevo necesita ADB y su propia autorización en la TV.

## Ver el plan sin modificar la TV

Desde la raíz del repositorio:

```sh
python3 recovery/xiaomi-mitv-mssp3/restaurar-xiaomi.py --dry-run
```

El modo de simulación funciona sin ADB instalado: muestra los comandos y no contacta con la tele ni crea claves o registros.

## Ejecutar la restauración

Desde la raíz del repositorio:

```sh
python3 recovery/xiaomi-mitv-mssp3/restaurar-xiaomi.py --address IP_DE_LA_TV:5555
```

La dirección de la TV no se guarda en el repositorio. Sustituir `IP_DE_LA_TV:5555` por su dirección actual o identificador ADB. Sin `--address`, una ejecución real se detiene antes de contactar con ningún dispositivo. El modo `--dry-run` utiliza un marcador y sigue funcionando sin dirección.

Para indicar una copia concreta de ADB:

```sh
python3 recovery/xiaomi-mitv-mssp3/restaurar-xiaomi.py --address IP_DE_LA_TV:5555 --adb /ruta/a/platform-tools/adb
```

Si la TV solicita autorización de depuración, aceptarla y repetir el comando. El resultado se guarda en `.local/recovery/xiaomi-restauracion-resultado.json`; si se detiene, ese registro muestra el último paso ejecutado.

El comando original `./luma.sh restore` sigue siendo la recuperación genérica de Luma. Para esta tele, el script de este directorio también restaura PatchWall, estadísticas y animaciones, y retira Luma.

## Recuperar el inicio desde la TV

Si no hay acceso por ADB:

1. Abrir Ajustes de Android desde el menú de ajustes de la tele.
2. Entrar en Aplicaciones y mostrar las aplicaciones del sistema.
3. Habilitar **Android TV Home** (`com.google.android.tvlauncher`) y **PatchWall** (`com.mitv.tvhome.atv`).
4. Una vez habilitados, desinstalar **Luma TV** y pulsar Home. Si Android pregunta qué inicio usar, elegir **Android TV Home**.

Esto recupera el inicio. Para restaurar también los estados exactos de los servicios y las animaciones, ejecutar el script cuando vuelva a haber acceso ADB.

## Alcance y comprobaciones

La restauración completa se ejecutó y verificó en la TV: volvieron el inicio original, los cuatro estados de paquetes y los tres valores de animación, y se comprobó la ausencia de Luma y FLauncher. Después se reinstaló Luma oficial y se reaplicó la limpieza. La adaptación de rutas a este repositorio se comprueba mediante simulación y pruebas con ADB simulado; no se vuelve a restaurar la TV por copiar estos archivos.

El respaldo cubre los cambios de configuración descritos. No es una imagen completa del firmware ni una copia de las cuentas, y no recupera archivos o datos que un limpiador haya borrado. La depuración queda como estaba en la referencia inicial, ya habilitada por el usuario.

Para verificar los controles del script sin una TV:

```sh
python3 -m unittest discover -s recovery/xiaomi-mitv-mssp3 -p 'test_*.py'
```
