# Revisión 2.5.3 — 3 de noviembre de 2026

## Comportamiento

- **Now Playing conserva el último vídeo.** El listener observa sesiones multimedia mientras otra app está
  delante. Guarda título, app, subtítulo, referencia de carátula, duración y posición en la TV. Cuando
  YouTube retira su sesión, la tarjeta continúa como **Resume**, con el progreso detenido y sin controles de
  salto que ya no funcionarían. Abrir o pulsar play en esa tarjeta abre la app de origen; la app decide cómo
  continuar su reproducción. Una sesión activa mantiene sus controles habituales.
- **La imagen ocupa toda la tarjeta.** Se elimina el desplazamiento del dibujo dentro de la cara ampliada.
  El banner se ajusta cubriendo la cara, sin dejar bandas por diferencias pequeñas de proporción.
- **El sintonizador se llama TV.** El nombre aparece tanto en el widget como en el selector de entradas.
- **Home, Apps y Settings cambian al recibir foco.** Las flechas recorren la barra y cambian el contenido.
  Abajo u OK entran en la página seleccionada. La reconstrucción de una página no convierte el foco
  automático de respaldo en otra orden de navegación.
- **Animaciones más ligeras.** El foco utiliza una ampliación de 240 ms, con el dibujo inmóvil dentro de su
  cara y sombras menores. Se recortan las filas que quedan fuera del área de apps. Los fondos fotográficos
  no desplazan una superficie de pantalla completa con cada flecha; el fondo animado espera 450 ms de
  inactividad antes de reanudarse.

No se añaden permisos. Se conservan los accesos a Input y Now Playing ya autorizados en 2.5.2.

## Reproducción y regresiones

El código anterior reprodujo los cuatro comportamientos: desaparición del vídeo al retirar la sesión,
borde inferior descubierto al enfocar la imagen, sintonizador llamado Sintonizador y pestaña Apps sin
cambio de contenido al recibir foco. El registro está en
`.local/diagnostics/feedback-2.5.3/regression-before.txt`.

La suite usa las vistas y servicios de entrada reales de la Xiaomi, junto con sesiones multimedia locales
de prueba que no se publican como activas. Comprueba la desaparición de una sesión, la reconstrucción de
Home, el cambio de metadatos mientras Home no observa la sesión, la conservación en preferencias y la
acción de reabrir la app. Comprueba también navegación con flechas y entrada con Abajo, cobertura de la
imagen y el nombre TV, además de las regresiones de 2.5.2.

Pasan las **21 comprobaciones**, incluida la recogida de fotogramas. La copia QA se mantiene despierta
durante la ejecución; la instrumentación por sí sola no cuenta como actividad física para el salvapantallas.
La suite lo descarta con una tecla de navegación y usa `FLAG_KEEP_SCREEN_ON` únicamente en QA. No cambia
el tiempo de reposo configurado en el sistema.

El caso multimedia valida sesiones y callbacks reales de Android con metadatos de prueba. No acredita por
sí solo que YouTube publique todos sus vídeos ni que permita restaurar una posición concreta desde fuera.
Las carátulas entregadas únicamente como bitmap se conservan en memoria; las referencias URI y el resto
de metadatos se conservan también después de reiniciar el proceso.

## Medición de animaciones

La suite ejecuta la misma secuencia de 48 cambios de foco en Apps y recoge `FrameMetrics.TOTAL_DURATION`.
La referencia inicial fue: 268 fotogramas, mediana **39,55 ms** y percentil 95 **63,88 ms**. La ejecución
final recogió 299 fotogramas, mediana **41,56 ms** y percentil 95 **59,25 ms**. La reducción del percentil 95
es modesta, alrededor del 7 %; la mediana no mejora y hubo variación entre ensayos. No se afirma una mejora
general de FPS ni una tasa estable de 60 FPS. Los tiempos son duración de trabajo por fotograma, no una
medición directa de cadencia de presentación. El registro final se guarda en
`.local/diagnostics/feedback-2.5.3/regression-after.txt`.

## Instalación

La compilación mantiene el paquete `dev.luma.tv`, la firma propia y la actualización con `install -r`.
El APK de 2.5.2 se ha guardado antes de compilar producción, dentro del diagnóstico local de esta revisión.

Se instaló 2.5.3 con éxito en la TV. En la interfaz de producción se comprobó la tarjeta **YouTube · Resume**
con un título real mientras Luma estaba delante, y la página Apps activada desde la barra con flechas, sin OK.
Las capturas privadas son `youtube-resume.png` y `tabs-apps.png`, dentro del diagnóstico de esta revisión.
La prueba de Apps no inicia ninguna reproducción. No se acredita restauración automática de una posición
mediante YouTube; la acción Resume abre su aplicación.

Referencias: [ciclo del listener](https://developer.android.com/reference/android/service/notification/NotificationListenerService#onListenerConnected()),
[destrucción de sesiones multimedia](https://developer.android.com/reference/android/media/session/MediaController.Callback#onSessionDestroyed()).
