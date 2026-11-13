# Mazda Avisos Lab v0.12 — tablero visual

- Portada con ilustración vectorial original de un crossover y diseño de tarjetas inspirado en la organización de Mazda Europa (sin copiar sus imágenes o su identidad).
- Batería y temperatura interior como indicadores principales; además autonomía, carga, bloqueo y ventanas cuando estén disponibles.
- Los números solo aparecen cuando se han recibido realmente: conexión MQTT no implica telemetría disponible. Se muestra la antigüedad de la última lectura y una advertencia si supera 20 minutos.
- Datos mínimos guardados en el dispositivo únicamente para visualizar las últimas lecturas (SOC, temperatura, autonomía, estados básicos), sin guardar VIN, credenciales ni mensajes MQTT completos.
- Agrupa en una sola notificación las ventanas que cambien juntas dentro del mismo informe, evitando cuatro avisos simultáneos.
- Herramientas técnicas plegadas, botón de configuración visible, historial local de notificaciones.
- Monitoreo MQTT directo en Android sin Home Assistant y funciones previas intactas.
- No es alarma de seguridad; la recepción de mensajes de ocupantes del Centro de Mensajes sigue sin demostrar.
