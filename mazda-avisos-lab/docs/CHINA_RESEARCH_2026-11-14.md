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

## Profundización y prueba 1.1.11

El propietario ha comunicado 92 códigos en su consulta europea, sin los marcadores buscados de Centinela, fotos, DVR o aparcamiento. Esto caracteriza esa respuesta concreta; no demuestra que el hardware o el firmware carezcan de esas funciones.

### Datos adicionales extraídos de los módulos oficiales

- El cliente compartido de la app china define para Mazda `vcsAppId=chang-ma` y `sdkAppType=maZDaApp`. El interceptor envía `vcs-app-id` y añade el token a los parámetros o al cuerpo de las peticiones. La consulta de configuración también envía `X-VCS-User-Token` en el módulo `oneButtonStart`.
- Para Mazda se mantiene la ruta `/cm-apigw`. Las rutas antiguas `/app2/` y `/appserver/` se transforman a `/app-third/`. Esto describe el cliente inspeccionado; el entorno nativo puede proporcionar la URL base y no se ha validado una sesión china.
- El módulo `carAPA` consulta `navigator.bluekeyH5.getBluetoothStateFromH5`, establece la conexión mediante `connectBleFromH5` y entrega el control a `parkingCommandFromH5`. Gestiona estados `APA_FunctionOnOffSts` y `APA_ParkNotice_5`, pausas y comunicación continua. Por tanto, ese módulo requiere la integración con la llave Bluetooth; una sustitución de host HTTP no implementa su canal de control.
- Centinela invoca `navigator.packaging.sendCmd` con el nombre de interfaz `setSentryMode`. El puente JavaScript solo entrega la petición al código nativo. No se ha obtenido una equivalencia demostrada con una ruta de mando europea. Los números 10006/10007 son identificadores de la interfaz, no códigos de mando telemático demostrados.
- `remotePhoto` verifica, en su rama MQTT, los códigos `#inCarPhoto` / `#outerCarPhoto` junto con un `commandCode`. Los álbumes usan rutas de lectura independientes de la captura. Tener acceso a un álbum no prueba que se pueda tomar una foto.
- El DEX principal del APK oficial está empaquetado con Jiagu; el análisis funcional aquí documentado procede de sus módulos JavaScript y recursos legibles. No se ha ejecutado el APK, extraído una sesión ajena ni eludido la vinculación de vehículos.

La página oficial de equipamiento de EZ-60 distingue DVR, Centinela / monitorización de vídeo, RPA y RMA: https://www.changan-mazda.com.cn/compare/?name=MAZDA+EZ-60 . Son funciones del mercado chino; no una lista de compatibilidad del vehículo europeo del propietario.

### Qué permite comprobar la nueva APK

1. **Configuración europea ampliada:** conserva los códigos funcionales e incorpora los metadatos presentes de mando y disponibilidad. No inventa los que falten ni trata `enable` o `status` como un contrato conocido.
2. **Rastro de telemetría:** registra únicamente nombres de campos y estados numéricos expresamente permitidos para buscar señales de Centinela, cámaras y APA en las respuestas HTTPS y los mensajes MQTT ya autorizados. Los valores ausentes permanecen ausentes; 0 no se convierte en «función activa». El inventario es acotado y solo reside en RAM.
3. **Consulta china anónima:** conserva la prueba sin credenciales. Una repetición desde el entorno de investigación con el canal `chang-ma` devolvió HTTP 503; no permitió extraer nuevas conclusiones sobre autenticación.
4. **Prueba regional con consentimiento separado:** consulta exclusivamente `/cm-apigw/vot-connect-conf-center/api/device/appGetCarConfFunc` con el identificador propio y, como máximo, los dos formatos ya disponibles de la sesión europea. Se comunica el traslado de esa credencial a China antes de poder realizarlo. Sin aceptación del diálogo no se envía. No se envían cabeceras europeas de autorización/dispositivo ni contraseña/PIN, no se siguen redirecciones, se conserva la verificación TLS y no se prueban rutas de control.

No se ha ejecutado la cuarta prueba en el entorno de desarrollo, que no dispone de una sesión del propietario. Solo el resultado obtenido en el móvil podrá indicar si esa solicitud concreta recibe configuración. Un rechazo tampoco identificará por sí solo si la barrera está en el formato de sesión, el mercado de la cuenta o la vinculación del coche. No se han activado Centinela, cámaras, DVR ni aparcamiento remoto.

### Validación

El arnés Java `tests/feature-discovery/AdvancedChecks.java` utiliza conexiones HTTPS simuladas, sin cuentas ni peticiones reales. Comprueba rechazo previo a HTTP de destinos, vehículos y credenciales no permitidos; cuerpo restringido a identificador propio y token; ausencia de cabeceras europeas de autorización/dispositivo en China; dos intentos como máximo; detención ante error TLS o sesión caducada; exclusión de datos privados en el informe; conservación de valores 0 sin declararlos activos; reinicio del inventario; y límites de tamaño/profundidad. Se ejecuta también en el workflow antes de compilar y firmar. Estas pruebas no validan el backend chino ni el comportamiento físico del vehículo.
