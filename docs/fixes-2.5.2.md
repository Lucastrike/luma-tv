# Revisión 2.5.2 — 3 de noviembre de 2026

## Cambios

- **Imágenes al mover apps:** desmontar las tarjetas cancelaba sus animaciones en el fotograma actual.
  Una tarjeta sin foco podía conservar inclinación, escala o desplazamiento del dibujo. Al reconstruir las
  filas se cancelan las transiciones pendientes y se restablecen las propiedades visuales de cada tarjeta.
- **YouTube:** se elimina el logotipo recreado con Canvas. Se utiliza el banner de la actividad instalada,
  con el banner de la aplicación o su icono como alternativas de Android.
- **Now Playing:** en televisores sin pantalla de acceso a notificaciones, la acción se llama **Setup help**
  y explica la configuración desde un ordenador y la opción de ocultar el widget.
- **Input:** se añade el servicio opcional **Luma Input button**. Consume la pareja de eventos de la tecla
  Input, ignora sus repeticiones y deja pasar todas las demás teclas. Abre el mismo selector que la sección
  Inputs, con Luma ya abierto o desde un inicio en frío. No pide contenido de ventanas ni eventos de
  accesibilidad. **Settings → Remote Input button** permite abrir el selector y configurar el servicio.
  Si la acción de accesibilidad solo resuelve al stub de Google, se abre la configuración general de la TV.

Up Next mantiene el funcionamiento de 2.5.1: lista Watch Next de Android TV y recientes como alternativa.
**Settings → Up Next → Reset Up Next** oculta la actividad anterior en Luma sin borrar el historial de las
plataformas. No se reinicia la lista de producción durante las pruebas.

## Evidencia y pruebas

Antes del arreglo, la suite sobre la TV reprodujo dos fallos: una tarjeta YouTube sin foco conservaba su
posición o inclinación de foco, y el dibujo de YouTube difería del banner o icono de la aplicación instalada.
El registro local es `.local/diagnostics/remote-fixes/regression-before.txt`.

Después del arreglo pasan las **15 comprobaciones** sobre la Xiaomi conectada. El registro local es
`.local/diagnostics/remote-fixes/regression-after.txt`.

La suite QA comprueba movimiento entre filas, guardado y cancelación, alineación de todas las tarjetas sin
foco, igualdad del dibujo de YouTube con el recurso instalado, reinicio de Up Next y ayuda de Now Playing.
Además verifica el filtro de teclas, los metadatos del servicio, la apertura del selector desde la acción
del servicio con la actividad existente y en frío, y la selección de una pantalla de ajustes utilizable.

```sh
LUMA_DEVICE=IP_DE_LA_TV:5555 \
ANDROID_SDK_ROOT="$PWD/.local/toolchain/sdk" \
LUMA_JDK="$PWD/.local/toolchain/jdk/Contents/Home" \
bash tools/test-tv.sh
```

La copia QA se retira al terminar y se restaura el inicio previo. Estas pruebas no habilitan accesibilidad
ni acceso a notificaciones y no acreditan por sí solas el recorrido del botón físico o la reproducción musical.

## Activación e instalación

La versión 2.5.2 conserva el paquete `dev.luma.tv` y la firma propia. Una actualización con `install -r`
conserva los datos. El APK de producción no incluye la instrumentación de QA.

El usuario ha autorizado expresamente activar **Luma Input button** y **Luma Now Playing**. Se instaló
2.5.2 con `install -r`, manteniendo Luma como inicio predeterminado. Se retiró la copia QA. El APK anterior
está respaldado en `.local/diagnostics/remote-fixes/luma-2.5.1-before.apk`.

- `dumpsys accessibility` confirma el servicio **Luma Input button** conectado con capacidad 8 (filtrado de
  teclas) y sin tipos de eventos de accesibilidad solicitados.
- `dumpsys notification` confirma **Luma Now Playing** entre los listeners activos. Se conserva el listener
  de Google que ya estaba habilitado. Solo se guarda el fragmento de estado de listeners en el diagnóstico.
- En la interfaz real, Now Playing muestra **Nothing playing** y los accesos a YouTube Music y YouTube,
  sin solicitud de permisos. La captura es `.local/diagnostics/remote-fixes/now-playing.png`. No había una
  sesión reproduciendo; no se acredita aún reproducción musical ni controles sobre una app de música real.
- El envío de Input mediante `adb shell input keyevent 178` no abrió el selector. Las pruebas QA sí
  acreditan el filtro al recibir eventos y su acción de apertura, pero no sustituyen el recorrido físico
  del mando. Se ha solicitado al usuario comprobar ese botón; su resultado sigue pendiente.

La captura de Home conserva los mismos seis favoritos y el clima de Valencia. Up Next no se ha reiniciado.
Los estados de acceso anteriores y posteriores están en `access-before.json` y `access-after.json`, dentro
de `.local/diagnostics/remote-fixes/`. El selector de la sección Inputs funciona sin accesibilidad.

Android permite recibir las teclas antes de enviarlas al resto del sistema mediante
[`AccessibilityService.onKeyEvent`](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent)).
El acceso a las sesiones multimedia requiere un listener de notificaciones habilitado o el permiso de sistema
[`MEDIA_CONTENT_CONTROL`](https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName)).
Declarar estos servicios en el APK no concede ninguno de esos accesos.
