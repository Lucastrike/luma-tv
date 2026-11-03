# Nuestra versión de Luma

Este checkout mantiene el código de Luma con las modificaciones locales. La instalación propia conserva el
identificador `dev.luma.tv` y se firma con nuestra clave. No requiere otra aplicación de Luma para abrirse.

## Firma y claves

La clave de actualización se carga desde `.signing/local.keystore` por defecto o desde `LUMA_KEYSTORE`.
Las claves y sus copias deben conservarse en almacenamiento privado, fuera de Git, con permisos `600`.
No se deben añadir a commits ni subir a un repositorio, issue o PR.

Las próximas actualizaciones deben usar esa misma clave. En otro ordenador o clon, hay que recuperar la clave
desde una copia privada antes de compilar una actualización. Generar otra identidad de firma impediría
actualizar la instalación existente.

La huella SHA-256 del certificado **público**, útil para identificar la firma, es:

```text
2a8d3fba5999ae4bd3e8e9cdcaa28ae24b107412b076d649349cd53d78d562de
```

## Compilar y actualizar esta TV

Desde la raíz del repositorio, con las herramientas locales de este ordenador:

```sh
ANDROID_SDK_ROOT="$PWD/.local/toolchain/sdk" \
LUMA_JDK="$PWD/.local/toolchain/jdk/Contents/Home" \
bash build.sh

export LUMA_DEVICE=IP_DE_LA_TV:5555
ANDROID_USER_HOME="$PWD/.local/adb-state" \
.local/android-tools/platform-tools/adb -P 5038 -s "$LUMA_DEVICE" \
install --no-incremental -r dist/luma-tv.apk
```

El paquete de producción predeterminado es `dev.luma.tv`; no establecer `LUMA_PACKAGE=dev.luma.tv.local`
para actualizar la instalación habitual. `-r` actualiza la aplicación y conserva sus datos cuando la firma
coincide. No hace falta desinstalar Luma en las siguientes actualizaciones.

## Sustitución inicial

El 3 de noviembre de 2026 se sustituyó la oficial 2.5 por nuestra 2.5.1, tras la autorización
expresa del usuario. Se guardaron los APK anteriores y se recogió la configuración visible para restaurarla:
orden de aplicaciones, favoritos, apps ocultas, apariencia, ubicación del tiempo, unidades y widgets.

La configuración visible se reconstruye a partir de la interfaz; no es una copia íntegra del almacenamiento
privado de la versión oficial. Los registros y resultados de la sustitución permanecen en almacenamiento
local excluido de Git. No se han reiniciado las listas de las plataformas de vídeo.

El auxiliar de migración es temporal, no tiene actividad de launcher y se retira al terminar. La copia
`dev.luma.tv.local` también se retira, dejando una sola instalación de Luma.

Se verificó el APK instalado contra la compilación propia, la resolución de Home, la retirada de las dos copias
auxiliares y la conservación de los demás paquetes. También se realizó una actualización real con `install -r`:
la instalación la aceptó y mantuvo los favoritos y la ubicación elegida en la pantalla de inicio. El acceso a notificaciones
quedó sin cambios. No se hizo ningún commit durante esta sustitución.

La activación autorizada de Input y Now Playing se describe en [el informe de 2.5.2](fixes-2.5.2.md).
El vídeo conservado, la navegación por foco y los cambios visuales posteriores se describen en
[el informe de 2.5.3](fixes-2.5.3.md). La firma y el procedimiento de actualización son los mismos.
