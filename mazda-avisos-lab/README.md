# Mazda Avisos Lab v0.11 — interfaz renovada y Centinela contextual

- Pantalla principal rediseñada con fondo claro, tarjetas blancas redondeadas, tipografía jerárquica y botones discretos inspirados en la app Mazda oficial, sin copiar sus recursos gráficos ni su marca.
- Historial local de los últimos 100 avisos con fecha y hora, disponible desde la pantalla principal.
- Centinela básico: las aperturas de puertas, ventanas y maletero solo se destacan cuando el último estado recibido del coche es **aparcado y bloqueado**. El desbloqueo se avisa como cambio, aunque puede ser legítimo.
- Monitor MQTT autónomo, sin Home Assistant. Mantiene el servicio de primer plano experimental y las preferencias de v0.10.
- Primer estado MQTT es referencia; no es una alarma ni verifica intrusiones.
- Requiere validar en vehículo la correspondencia de todos los campos y los códigos de carga. No se ha identificado el origen de los avisos de ocupantes.
