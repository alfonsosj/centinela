# Mazda Avisos Lab v0.7 — monitor MQTT temporal con pantalla apagada

Versión experimental. Mantiene la v0.6 (lectura del Centro de Mensajes, tile Android y escucha MQTT 2 min) y añade una sesión en primer plano de hasta 90 minutos.

## Prueba
1. Instala v0.7, asegurando los permisos Android de notificaciones y accesibilidad si usas Centro de Mensajes.
2. Abre «Configurar / iniciar MQTT con cuenta secundaria».
3. Autentica exclusivamente tu cuenta de pruebas y pulsa **Iniciar monitor en segundo plano (90 minutos)**.
4. Verifica que aparece la notificación persistente «Mazda Avisos Lab · MQTT», apaga la pantalla y cambia el cierre del CX-6e desde la app oficial.
5. Deberían recibirse alertas Android cuando cambie el estado del cierre, incluso con la pantalla apagada. En la notificación persistente se puede pulsar Detener.

## Privacidad y límites
- Una conexión MQTT TLS de solo lectura al broker entregado por Mazda Europa; suscribe reportes de estado y eventos.
- Los tokens y la configuración del broker se mantienen solo en memoria, no en preferencias ni en GitHub; deben reintroducirse tras detener/reiniciar la app.
- Android 15+ limita el uso diario de servicios de primer plano del tipo dataSync; no podemos prometer 24/7 ni funcionamiento indefinido. La sesión se detiene a los 90 minutos como máximo y puede ser interrumpida antes por el sistema.
- Reconexión automática limitada durante la sesión si se corta Internet, siempre que sigan siendo válidas las credenciales MQTT.
- No se han verificado eventos de ocupantes en events/up; **no sustituye notificaciones ni sistemas de seguridad del vehículo**.
- Por la firma debug diferente, es posible que sea necesario desinstalar solo Mazda Avisos Lab v0.6 (perdiendo su historial local) antes de instalar v0.7. La app Mazda oficial no se modifica.
