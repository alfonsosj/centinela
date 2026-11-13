# Mazda Avisos Lab v0.8 — recuperación de red y diagnóstico

Mantiene la escucha directa de eventos por MQTT y las notificaciones de bloqueo/desbloqueo ya comprobadas en la versión anterior.

## Mejoras
- Durante una sesión de primer plano de **máximo 90 minutos**, observa cambios de red mediante el callback de Android (Wi-Fi, datos móviles, pérdida y recuperación) y fuerza reconexión cuando corresponde.
- Usa latidos MQTT (PINGREQ/PINGRESP); reconecta si el broker deja de responder.
- Muestra el tipo de red en el estado del monitor y guarda **únicamente** un diagnóstico de actividad reciente, reconexiones y marca temporal. **No guarda tokens, contraseñas ni contenidos MQTT.**
- Desde la pantalla principal, «Actualizar estado del monitor» detecta cuándo la última señal tiene más de 90 segundos y advierte de que no se puede confirmar la escucha.
- Añade esperas de reconexión con retroceso corto y pausa cuando no hay red.

## Prueba
Instala v0.8 y autoriza la cuenta secundaria. Inicia «monitor en segundo plano (90 minutos)». Cambia de Wi-Fi a datos móviles apagando Wi-Fi desde los ajustes rápidos y espera el estado «Cambio de red» o «Conectando por datos móviles». Repite la prueba de bloqueo del vehículo; mantén siempre visible la notificación persistente. Después enciende Wi-Fi para verificar la nueva reconexión.

## Límites importantes
- Android restringe el tiempo acumulado de servicios de primer plano `dataSync`: **máximo 6 horas por 24 horas** al orientar la app a Android 15+; la sesión de 90 minutos puede terminar antes. No existe autoarranque tras reiniciar el móvil, y no se promete cobertura 24/7.
- Los mensajes de detección de ocupantes que muestra la app oficial **todavía no se han identificado** en `events/up`.
- No hay comandos de control sobre el vehículo. La notificación por cambio de cierre está clasificada como experimental.
- Si cambia la firma de desarrollo debug, desinstala solamente Mazda Avisos Lab antes de instalar la nueva APK; no desinstales Mazda oficial.
