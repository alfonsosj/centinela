# Comprobación China / Europa — 14 de noviembre de 2026

## Fuente y método

Se descargó la aplicación ofrecida por la página oficial de Changan Mazda:

- Página: https://appmanage.changan-mazda.com.cn/appmanage/download/
- APK enlazado en la página: https://chana-mazda-bucket.changan-mazda.com.cn/2026-10-10/2108836929658163202.apk
- Paquete: `com.cma.yuemaxk`.
- Versión leída del manifiesto: `v1.0.72`, código `103`.
- Tamaño: 440409618 bytes.
- SHA-256: `6925c1f304fae3fdfdcd781d4db193e0698609676ebd311c799b47789c3f7777`.

Se inspeccionaron estáticamente sus recursos y módulos JavaScript empaquetados. No se ejecutó la aplicación oficial, no se inició sesión en una cuenta china y no se enviaron órdenes a vehículos. El APK oficial y su código no se incorporan a este repositorio.

## Hallazgos comprobados en los recursos

| Función | Indicios concretos | Alcance de la evidencia |
| --- | --- | --- |
| Centinela | Módulo `micro.cn.com.mazda.sentinelModeV2`; comando de interfaz `setSentryMode`; campos `sentryModeSwitchSts`, `SentryModeSensitivity` y `sentryModeOnLength` | Existe la implementación de interfaz china; no demuestra disponibilidad en Europa. |
| Fotos interiores | Módulo `remotePhoto`; código de función `#inCarPhoto`; llamada de interfaz `takePhoto` | La interfaz comprueba la configuración por vehículo y la presencia de un código de mando. |
| Fotos exteriores | Código `#outerCarPhoto`; llamada de interfaz `photograph` | Misma limitación: no constituye prueba física ni autorización para un coche europeo. |
| Aparcamiento | Módulo `carAPA` y opciones `castingItem` / `remoteVideoControl` | Se refiere a aparcamiento; el nombre `remoteVideoControl` no demuestra una función DVR. |
| Automatizaciones | Módulos `oneButtonStart`, `oneButtonStartCreate` y `cubeScenePro` | Presencia de módulos; no se implementan acciones en Mazda Avisos. |

El cliente chino distingue protocolos MQTT y TLV. La consulta MQTT de configuración permite reconocer códigos como `#inCarPhoto` y `#outerCarPhoto`; la existencia de cámaras no debe inferirse de un botón genérico ni de un valor ausente interpretado como cero.

## Consultas públicas realizadas

Solo se envió un cuerpo JSON vacío, sin credenciales ni identificador de vehículo, al endpoint de lectura de configuración de cada plataforma.

| Plataforma | Endpoint | Respuesta observada |
| --- | --- | --- |
| China | `https://m.iov.changan.com.cn/cm-apigw/vot-connect-conf-center/api/device/appGetCarConfFunc` | HTTP 200, `success=false`, código `40005`: requiere parámetro token. |
| Europa | `https://ca-m.iov.changanauto.com.de/user-apigw/vot-connect-conf-center/api/device/appGetCarConfFunc` | HTTP 200, `success=false`, código `APIGW_1_7_02_001`: requiere X-Tsp-User-Token. |

Estas respuestas confirman que las pasarelas responden y muestran diferentes requisitos de autenticación. No prueban que un token europeo sea aceptado en China ni que un VIN europeo esté vinculado allí.

## Prueba incorporada a Mazda Avisos 1.1.10

El diagnóstico europeo reutiliza la consulta de configuración que ya realiza el inicio de sesión y examina códigos funcionales. La prueba china sigue siendo anónima. No existe un selector que redirija los controles o los tokens europeos a China.

Para completar la comprobación específica del vehículo, el propietario debe autenticar su cuenta en el móvil y ejecutar la lectura desde Conexión y herramientas > Diagnóstico China / Europa. Los resultados indican menciones en la configuración, no disponibilidad confirmada ni éxito de activación.
