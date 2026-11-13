# Mazda Avisos Lab v0.9 — app Android autónoma

No necesita Home Assistant, Plesk, NAS ni servidor propio. Cada propietario utiliza una cuenta Mazda autorizada (se recomienda una cuenta secundaria compartida para evitar interferir con la cuenta principal).

## Novedades
- Monitor en primer plano de tipo Android `specialUse` para una suscripción MQTT de eventos de vehículo con notificación continua.
- **Sin finalización automática a los 90 minutos**: reconecta al cambiar entre Wi-Fi y datos móviles y cuando el broker pierde comunicación; recrea la conexión técnica cada cuatro horas.
- Conserva en memoria el último estado conocido de bloqueo para evitar perder el primer cambio tras reconectar.
- El usuario puede detenerlo desde la notificación persistente o en la app.
- No se almacenan contraseñas, tokens ni canales completos MQTT; solo estados y contadores de diagnóstico. El login debe repetirse tras reinicio del dispositivo o terminación del proceso.
- Conserva el modo de prueba breve de 2 minutos y la lectura manual de Centro de Mensajes.

## Compatibilidad y limitaciones
- Un servicio `specialUse` **no está sujeto al límite de 6 h/24 h del tipo `dataSync`**, pero Android/One UI puede detenerlo, recortar batería o prohibir arranques en segundo plano. No hay garantía 24/7. La utilización `specialUse` deberá justificarse y someterse a revisión si se publica en Google Play.
- El primer aviso confirmado es el cambio de cierre. Los mensajes de ocupantes del Centro de Mensajes NO se han identificado en el canal events/up; no usar como sistema de seguridad.
- Tras reiniciar el móvil no hay inicio automático ni token persistente, por diseño. El servicio nunca se inicia sin intervención del usuario.

## Para probar
1. Instala el APK; si cambia la firma debug, desinstala solo Mazda Avisos Lab v0.8.
2. Autoriza la cuenta secundaria y pulsa «Iniciar monitor continuo experimental».
3. Comprueba la notificación persistente, bloquea pantalla y cambia la cerradura del coche.
4. Deja el monitor varias horas; verifica el estado y la notificación persistente. Prueba los cambios Wi-Fi/datos móviles.
5. Usa Detener cuando no quieras tener una conexión abierta.

El proyecto no se distribuye aún como producto estable para amigos; se valida el servicio de tipo especial primero.
