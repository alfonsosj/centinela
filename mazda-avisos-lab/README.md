# Mazda Avisos Lab v0.10 — Avisos ampliados configurables

Android autónomo sin Home Assistant. Conexión MQTT directa al broker oficial de Mazda, usando cuenta secundaria compartida. No envía órdenes al vehículo.

## Avisos disponibles
- Bloqueo/desbloqueo de puertas; aperturas físicas individuales de puertas y maletero.
- Apertura/cierre de ventanas, por ventanilla.
- Cable de carga AC/DC conectado y desconectado.
- Inicio, parada y estado de espera de carga, según los códigos MQTT conocidos (se evita inferir «carga completada» de códigos sin confirmar).
- Nivel de batería inferior a umbral configurable (predeterminado 20 %), y cambios acumulados predeterminados de 10 puntos porcentuales.
- Descensos de carga del vehículo estando aparcado, opcional y experimental; requiere estado aparcado confiable, no indica por sí mismo robo o avería.
- Temperatura interior alta/baja con umbrales configurables (40 °C / 5 °C) e histéresis de 3 °C.
- **Centinela básico opcional:** notifica de forma destacada desbloqueos/aperturas de puertas, ventanas y maletero. NO detecta impactos, cámaras, personas, ocupantes ni intrusión. No controla sistemas del coche.

## Reglas
- La primera lectura establece referencia y no genera notificación retroactiva.
- Ausencia de un campo o códigos no documentados NO se tratan como cambio.
- Los estados se conservan en memoria durante reconexiones Wi-Fi/datos móviles en la misma sesión de la app.
- Se pueden activar/desactivar categorías y ajustar umbrales desde **Configurar tipos de avisos y Centinela**, incluso con el monitor funcionando.
- Las preferencias son locales. No se almacenan contraseñas, tokens ni contenido completo de MQTT.

## Límites
- No está verificada la presencia de avisos oficiales del Centro de Mensajes de Mazda en `events/up`; no sustituye avisos de ocupantes.
- Servicio Android specialUse experimental de duración no limitada por temporizador, pero Android/Samsung puede terminarlo por restricciones de batería o cambios de política.
- No se reinicia automáticamente tras apagar/encender el móvil y requiere nueva autenticación tras finalizar el proceso.
- Compatibilidad con otros vehículos/cuentas no verificada.
