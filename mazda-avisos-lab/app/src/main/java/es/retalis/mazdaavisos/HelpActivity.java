package es.retalis.mazdaavisos;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.Normalizer;
import java.util.Locale;

/** Searchable, offline instructions matching the controls implemented in this app. */
public final class HelpActivity extends SafeAreaActivity {
 private static final int INK=0xFF263548,MUTED=0xFF596677,BG=0xFFF4F5FB;
 private static final String[][] TOPICS={
   {"1. Primeros pasos", """
     1. Instala el APK de Mazda Avisos Lab. Las versiones con la misma firma se actualizan sobre la anterior y conservan los ajustes y el historial.
     2. Ten preparada una cuenta secundaria con acceso compartido a tu vehículo en MAZDA 6e & CX-6e, la aplicación oficial europea.
     3. En la portada, despliega «Conexión y herramientas» y pulsa «Conectar / autenticar Mazda».
     4. Introduce el correo, la contraseña y el PIN Mazda de seis cifras. Si Mazda envía un código por correo, introdúcelo en «Verificar dispositivo».
     5. Permite las notificaciones cuando Android lo solicite. Si la autorización MQTT está disponible, el monitor se inicia automáticamente. Espera a que la portada muestre «En línea».
     6. Abre el engranaje de ajustes para elegir qué avisos quieres recibir. En «Acceso a notificaciones» puedes revisar los permisos y enviar una prueba.
     """},
   {"2. Cuenta, registro, contraseña y PIN", """
     Utiliza una cuenta secundaria con acceso al coche para reducir los conflictos con la cuenta principal de la aplicación oficial.

     «Crear cuenta nueva» y «Recuperar contraseña», en la pantalla de conexión, abren el proceso de Mazda oficial. Si la app oficial no está instalada, se ofrece abrir su ficha en Google Play. Completa allí el registro o la recuperación y vuelve a Mazda Avisos para conectarte. Crear una cuenta no concede por sí solo acceso al vehículo: debes vincularlo o compartirlo desde Mazda.

     El PIN se verifica al autenticar la cuenta. Durante esa sesión de control se reutiliza sin pedirlo para cada función, aunque las órdenes siguen solicitando confirmación. La sesión de control dura como máximo 15 minutos. Si caduca, Android cierra el proceso o detienes el monitor, debes autenticarte de nuevo. Que el monitor siga conectado no prolonga la sesión de los controles.

     El PIN y los datos de acceso se mantienen temporalmente en memoria; no se guardan como contraseña o PIN en archivos, preferencias ni historial.
     """},
   {"3. Conexión y monitor en segundo plano", """
     El monitor MQTT recibe cambios que Mazda comunica del vehículo. Necesita internet en el teléfono, conexión del coche y una autorización válida de Mazda. Funciona sin Home Assistant.

     «En línea» confirma la conexión del monitor. «Reconectando» indica que está intentando recuperarla. «Monitor parado» o una petición de autenticación requieren revisar la conexión. El monitor y el control remoto son conexiones independientes: puede funcionar uno y necesitar reconexión el otro.

     Al conectar se mantiene una notificación persistente de monitorización. «Detener monitor», en las herramientas o en esa notificación, detiene la escucha y cierra la sesión de control. No desconecta ni cambia el estado físico del coche.

     Hay reintentos de conexión mientras Android los permita y avisos de pérdida o recuperación cuando se cumplen sus condiciones. Después de apagar o reiniciar el móvil, actualizar la app o perder la sesión, abre Mazda Avisos y vuelve a autenticarte si es necesario. No se guardan claves para reiniciar automáticamente una sesión caducada.
     """},
   {"4. Portada y estado del vehículo", """
     La portada muestra batería, autonomía, temperatura interior, carga, cable, cierre, ventanillas y maletero cuando Mazda proporciona esas lecturas. La antigüedad aparece junto al vehículo. «Sin datos» significa que no existe una lectura disponible; estar conectado no garantiza que el coche haya enviado todos los valores.

     «Actualizar vehículo» consulta el estado mediante la cuenta autenticada. «Actualizar pantalla», en las herramientas, solo vuelve a dibujar la información guardada y no fuerza una consulta al coche.

     Una lectura con más de 20 minutos se considera antigua. Los controles que necesitan un estado reciente pedirán actualizar antes de actuar.

     Los iconos reflejan la última lectura, no el resultado anticipado de una orden. Cierre relleno indica desbloqueado; Ventanas, alguna abierta; Maletero, abierto; Clima, encendido. El gris indica estado desconocido o antiguo. Pulsa «Temp» o «Batería» para abrir sus paneles.
     """},
   {"5. Acceso a notificaciones, permisos y prueba", """
     Ve a Ajustes > Acceso a notificaciones > Abrir notificaciones. Allí encontrarás el estado del permiso de Android, el sonido de los avisos, la prueba y el historial.

     «Configurar notificaciones de Android» abre los ajustes de esta app. Activa las notificaciones y revisa sonido, vibración, avisos emergentes y pantalla de bloqueo. En «Sonido y categoría de avisos del vehículo» puedes revisar el canal utilizado para los eventos del coche. Android puede silenciarlo aunque el permiso general esté activado.

     «Enviar notificación de prueba» envía un aviso local identificado como prueba. No envía órdenes ni consulta el coche y no se incorpora al historial. Si llega, confirma que Android permite ese aviso; no demuestra que MQTT esté conectado ni que Mazda esté enviando datos.

     Revisa el modo No molestar y, si los avisos se interrumpen, la batería y la actividad en segundo plano de la app. Volver de los ajustes de Android actualiza el estado mostrado. Ningún ajuste garantiza la recepción continua si falla la red, Mazda o el sistema del teléfono.
     """},
   {"6. Avisos de puertas, cierre, ventanillas y maletero", """
     En Ajustes puedes activar por separado:
     • Bloqueo y desbloqueo de las cerraduras.
     • Apertura y cierre físico de las puertas.
     • Apertura y cierre de las ventanillas.
     • Apertura y cierre del maletero.

     Desbloquear una cerradura y abrir físicamente una puerta son eventos distintos. Si varias ventanillas cambian en el mismo informe, la app agrupa el aviso.

     Los avisos se basan en cambios recibidos por MQTT. La primera lectura establece una referencia y no genera por sí sola un aviso de cambio. Activar una casilla no obliga al coche a enviar datos ni reproduce sucesos anteriores.
     """},
   {"7. Recordatorios cada minuto", """
     «Recordarme cada minuto si está aparcado y hay puertas abiertas o desbloqueado»: cuando la sesión MQTT confirma esas condiciones, espera un minuto y recuerda cada minuto que el coche sigue sin asegurar. El recordatorio termina cuando los estados recibidos dejan de cumplirlas, por ejemplo al cerrar las puertas y bloquear.

     «Avisar cada minuto si el coche está bloqueado y alguna ventanilla abierta»: es un ajuste independiente. Tras un minuto con esos estados confirmados, recuerda cerrar las ventanillas. Termina cuando Mazda informa de que se han cerrado o se ha desbloqueado el vehículo.

     Ambos requieren monitor conectado y estados recibidos en la conexión actual. Tras una reconexión esperan nuevas confirmaciones. Si el monitor pierde conexión no pueden continuar de forma fiable. No cierran ni bloquean nada automáticamente.
     """},
   {"8. Centinela básico", """
     El ajuste «Centinela básico» destaca cambios de apertura cuando el coche comunica aparcado y bloqueado. También puede destacar el paso de bloqueado a desbloqueado.

     Se basa en los estados del vehículo. No utiliza cámaras ni detecta personas, golpes o intrusiones. No graba vídeo y no sustituye una alarma. Un aviso de apertura no identifica quién ha abierto el coche ni demuestra un acceso no autorizado.
     """},
   {"9. Avisos de batería y carga", """
     Puedes activar avisos de carga iniciada, detenida o en espera, y de cable conectado o desconectado, siempre que Mazda envíe un cambio reconocible.

     «Avisos de porcentaje de batería» utiliza dos ajustes:
     • Umbral de batería baja: del 5 al 40 %, inicialmente 20 %. Avisa al descender y cruzar ese umbral.
     • Cambio de batería entre avisos: de 5 a 25 puntos, inicialmente 10. Avisa cuando la diferencia acumulada desde la referencia alcanza el cambio elegido, al subir o al bajar.

     «Descenso de batería estando aparcado» es experimental. Elige una caída de 2 a 10 puntos, inicialmente 3. Necesita que el coche comunique que está aparcado y no distingue por sí sola una avería de un consumo normal.

     La primera lectura fija referencias. Los códigos de carga no permiten distinguir con certeza todos los casos de carga completada o pausada: la app no confirma «carga completada» sin evidencia suficiente.
     """},
   {"10. Avisos de temperatura interior", """
     Activa «Alertas por temperaturas extremas» y elige la temperatura alta, entre 25 y 60 °C, y la baja, entre −10 y 15 °C. Los valores iniciales son 40 °C y 5 °C.

     Los avisos usan lecturas del sensor interior comunicadas por el coche. La primera lectura establece la referencia y se evita repetir continuamente un mismo episodio. El aviso se rearma cuando la temperatura se aleja suficientemente del umbral.

     Una temperatura extrema no confirma que haya alguien dentro. La app no detecta ocupantes y no debe utilizarse para vigilar a personas o animales en el vehículo.
     """},
   {"11. Controles rápidos y panel completo", """
     Desde la portada puedes tocar Cierre, Ventanas, Maletero o Clima para solicitar la acción correspondiente al último estado conocido. Si faltan datos o están antiguos, actualiza primero. Las órdenes requieren una sesión de control vigente y confirmación; para los movimientos que lo necesitan se reutiliza el PIN de la sesión.

     «Abrir panel completo de controles» permite seleccionar las acciones por separado: bloquear o desbloquear; subir o bajar las cuatro ventanillas; abrir o cerrar el maletero; climatización y desempañado; localizar el coche mediante luces y sonido; bocina; calefacción del volante; calefacción y ventilación de los asientos delanteros; y límite de carga.

     Para ventanillas y maletero se necesita una lectura reciente de coche aparcado. Comprueba que no hay personas, animales u obstáculos en su recorrido. Las órdenes pueden tardar varios segundos; espera al resultado antes de repetirlas.

     Una orden aceptada no siempre demuestra el cambio físico. Consulta el estado y verifica el resultado en el coche. La compatibilidad depende del equipamiento, los permisos y la respuesta de Mazda; los controles adicionales siguen pendientes de validación en este vehículo.
     """},
   {"12. Climatización, asientos y volante", """
     Pulsa «Temp» en la portada. La pestaña A/C muestra temperatura interior, humedad y calidad del aire si Mazda proporciona esos datos. Si faltan, aparece «sin datos».

     Ajusta la temperatura deseada entre 16 y 30 °C y pulsa activar climatización para solicitarla. Mover el selector por sí solo no envía la orden. También puedes apagar el clima y activar o desactivar el desempañado frontal.

     En «Asiento» se abren los controles de calefacción y ventilación de conductor y acompañante, con apagado o niveles 1–3, y los de calefacción del volante. Cada operación solicita confirmación. El color de un acceso no garantiza un estado físico si aún no se ha recibido una lectura.

     La climatización consume energía y Mazda puede limitar su duración o rechazar la orden según el estado del vehículo. Comprueba el resultado real y la información de la aplicación oficial.
     """},
   {"13. Panel de batería y límite de carga", """
     Pulsa «Batería» para ver el porcentaje, la autonomía, el estado de carga y el cable de la última lectura.

     «Objetivo de carga» permite solicitar un límite entre el 60 y el 100 %. Elige el porcentaje, confirma y espera la respuesta de Mazda. Modificar el límite no equivale a iniciar o detener inmediatamente la carga.

     La carga programada, los períodos de carga, la programación del precalentamiento de batería y la hora de salida remiten a la aplicación oficial. Esta versión explica esas opciones, pero no las modifica. Tampoco incorpora una orden remota verificada para iniciar o detener la carga.
     """},
   {"14. Historial y actividad reciente", """
     La portada muestra los tres últimos eventos guardados y un acceso al historial completo. También puedes consultarlo desde Ajustes > Acceso a notificaciones.

     El historial conserva los últimos 100 avisos y resultados de controles en este teléfono, con fecha, título y detalle, del más reciente al más antiguo. Tener un evento guardado no garantiza que Android haya mostrado su notificación.

     «Actualizar historial» vuelve a leer los eventos locales. «Borrar historial» pide confirmación y elimina solo esos registros; no borra los mensajes de Mazda oficial, no cambia los ajustes y no actúa sobre el coche. La prueba de notificación no se guarda como evento real.
     """},
   {"15. Centro de Mensajes y acceso rápido de Android", """
     Esta función es distinta del monitor MQTT. En «Conexión y herramientas», «Abrir Mazda oficial» abre la app europea. «Centro de Mensajes Mazda» solicita una comprobación manual de sus mensajes mediante accesibilidad.

     Para esa comprobación, instala MAZDA 6e & CX-6e y activa el servicio de Mazda Avisos Lab desde «Configurar accesibilidad». La accesibilidad es opcional para el monitor MQTT y los controles remotos; se utiliza para leer la pantalla de mensajes de la app oficial.

     Si no se abre el Centro de Mensajes automáticamente, ábrelo tú dentro de Mazda oficial. La primera lectura sirve como referencia; las siguientes pueden detectar mensajes nuevos. Es una función experimental y puede depender del diseño de la app oficial.

     Puedes añadir «Comprobar Mazda» al panel de ajustes rápidos de Android desde su opción de editar botones. Al pulsarlo se abre Mazda y se solicita la comprobación; requiere la app oficial y la accesibilidad activada. No inicia una vigilancia automática de ocupantes.
     """},
   {"16. Solución de problemas", """
     No llegan avisos: envía una prueba; revisa el permiso general, la categoría de avisos, el sonido y No molestar. Después comprueba que el monitor esté «En línea» y que el tipo de aviso esté activado. La primera lectura no genera por sí sola una alerta de cambio.

     Llegan con retraso o se detienen al apagar la pantalla: revisa internet, los ajustes de batería y la actividad en segundo plano. Si Android detuvo el servicio o la sesión caducó, vuelve a conectar la cuenta.

     Hay conexión pero faltan batería o temperatura: estar conectado no obliga al coche a publicar todos los sensores. Pulsa «Actualizar vehículo» con una sesión vigente y comprueba la antigüedad de las lecturas.

     Vuelve a pedir autenticación o PIN: la sesión de control dura hasta 15 minutos y puede perderse al cerrar el proceso o detener el monitor. Vuelve a autenticarte; no se guarda el PIN para recuperarlo tras un reinicio.

     Una orden se rechaza o queda sin confirmar: comprueba la sesión, el estado reciente del coche, que esté aparcado cuando corresponda y que admita la función. Verifica físicamente el resultado antes de repetir una orden.

     El Centro de Mensajes no funciona: comprueba la app europea y la accesibilidad, y abre el centro manualmente. «Copiar diagnóstico», en las herramientas, copia información técnica al portapapeles para revisarla; úsalo solo si necesitas analizar un problema.

     Para investigar funciones de la versión china, abre «Conexión y herramientas» > «Diagnóstico China / Europa». Autentica la cuenta y pulsa «Leer funciones declaradas de mi coche». El resultado busca menciones de Centinela, cámaras, DVR y aparcamiento en la configuración europea. Una mención no confirma que la función se pueda activar. La búsqueda de rastros revisa también nombres de campos y estados concretos de Centinela y aparcamiento recibidos por HTTPS y MQTT, sin guardar mensajes completos. Deja el monitor activo unos minutos y repite esa lectura. La tercera prueba comprueba China sin credenciales. La cuarta es opcional: antes de ejecutarla debes aceptar el envío del token temporal europeo y del identificador del coche a la pasarela china para consultar su configuración. No envía contraseña ni PIN. Ninguna prueba de esta pantalla cambia la región ni envía órdenes. «Copiar informe completo» reúne los resultados sin incluir cuentas, VIN ni datos de sesión.
     """},
   {"17. Privacidad y límites de la app", """
     La app conserva localmente ajustes, últimas lecturas, historial y algunos diagnósticos. La contraseña, el PIN y los datos de la sesión de control no se guardan como credenciales persistentes. La autenticación y los controles se comunican con los servicios de Mazda mediante conexiones cifradas.

     No hay DVR, vídeo, cámara Centinela, detección de golpes ni detección de ocupantes por MQTT verificada. Las funciones que aparecen en los menús pueden depender de la compatibilidad del vehículo y de cambios en los servicios de Mazda.

     Los avisos dependen del coche, Mazda, internet y Android. Mazda Avisos Lab no sustituye la app oficial ni una alarma y no garantiza recepción permanente. Las órdenes de control se envían cuando las solicitas y confirmas; los ajustes de notificaciones no envían órdenes al vehículo.
     """}
 };
 private LinearLayout topics;
 private TextView count;
 private EditText search;
 private final boolean[] expanded=new boolean[TOPICS.length];

 @Override public void onCreate(Bundle state){
   super.onCreate(state);
   if(state!=null){
     boolean[] saved=state.getBooleanArray("expanded");
     if(saved!=null)System.arraycopy(saved,0,expanded,0,Math.min(saved.length,expanded.length));
   }
   ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
   LinearLayout root=new LinearLayout(this);root.setOrientation(1);
   root.setPadding(dp(18),dp(18),dp(18),dp(30));root.setFocusableInTouchMode(true);
   scroll.addView(root);setContentView(scroll);root.requestFocus();
   Button back=button("‹ Volver");root.addView(back);back.setOnClickListener(v->finish());
   root.addView(text("Ayuda",26,true));
   TextView intro=text("Instrucciones y funciones de Mazda Avisos. Guía disponible sin conexión. Toca un tema para desplegarlo o busca una palabra.",14,false);
   intro.setPadding(0,dp(8),0,dp(14));root.addView(intro);
   Button notifications=button("Abrir notificaciones e historial");root.addView(notifications);
   notifications.setOnClickListener(v->startActivity(new Intent(this,NotificationsActivity.class)));
   search=new EditText(this);search.setSingleLine(true);search.setTextSize(16);
   search.setHint("Buscar: PIN, batería, ventanillas…");
   search.setContentDescription("Buscar en la ayuda");search.setImeOptions(EditorInfo.IME_ACTION_DONE);
   root.addView(search,new LinearLayout.LayoutParams(-1,-2));
   count=text("",13,false);root.addView(count);
   topics=new LinearLayout(this);topics.setOrientation(1);root.addView(topics);
   search.addTextChangedListener(new TextWatcher(){
     public void beforeTextChanged(CharSequence s,int start,int length,int after){}
     public void onTextChanged(CharSequence s,int start,int before,int length){renderTopics(s.toString());}
     public void afterTextChanged(Editable value){}
   });
   if(state!=null)search.setText(state.getString("query",""));
   renderTopics(search.getText().toString());
 }
 @Override protected void onSaveInstanceState(Bundle state){
   state.putString("query",search.getText().toString());state.putBooleanArray("expanded",expanded);
   super.onSaveInstanceState(state);
 }
 private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
 private TextView text(String value,int size,boolean bold){
   TextView view=new TextView(this);view.setText(value);view.setTextSize(size);
   view.setTextColor(bold?INK:MUTED);view.setLineSpacing(dp(3),1);
   if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;
 }
 private Button button(String title){
   Button view=new Button(this);view.setText(title);view.setAllCaps(false);view.setMinHeight(dp(48));return view;
 }
 private static String normalize(String value){
   return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim();
 }
 private void renderTopics(String query){
   topics.removeAllViews();String key=normalize(query);int matches=0;
   for(int i=0;i<TOPICS.length;i++){
     if(!key.isEmpty()&&!normalize(TOPICS[i][0]+" "+TOPICS[i][1]).contains(key))continue;
     matches++;final int index=i;
     LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);
     panel.setPadding(dp(12),dp(8),dp(12),dp(12));
     GradientDrawable background=new GradientDrawable();background.setColor(Color.WHITE);
     background.setCornerRadius(dp(18));panel.setBackground(background);
     LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(12);
     topics.addView(panel,params);
     Button title=button(TOPICS[i][0]);title.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
     panel.addView(title,new LinearLayout.LayoutParams(-1,-2));
     TextView body=text(TOPICS[i][1].trim(),15,false);body.setTextIsSelectable(true);
     body.setPadding(dp(4),dp(10),dp(4),dp(4));panel.addView(body);
     boolean open=!key.isEmpty()||expanded[i];
     updateTopic(title,body,index,open);
     title.setOnClickListener(v->{
       boolean show=body.getVisibility()!=View.VISIBLE;expanded[index]=show;
       updateTopic(title,body,index,show);
     });
   }
   count.setText(matches==0?"No hay resultados. Prueba con otra palabra.":
     key.isEmpty()?TOPICS.length+" temas de ayuda":matches+" temas encontrados");
 }
 private void updateTopic(Button title,TextView body,int index,boolean open){
   title.setText(TOPICS[index][0]+(open?"  −":"  +"));
   title.setContentDescription(TOPICS[index][0]+(open?", desplegado":", plegado"));
   body.setVisibility(open?View.VISIBLE:View.GONE);
 }
}
