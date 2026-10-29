# Protocolo motor ↔ agente

Ejemplos de los mensajes del protocolo (versión 1), uno por tipo, en `ejemplos/`. El nombre del archivo es el tipo de mensaje en minúsculas y con guiones; por ejemplo, `EJECUTAR_PASO` está en `ejecutar-paso.json`.

Los ejemplos tienen formato para que se lean bien. Por el socket, cada mensaje viaja en una sola línea JSON, en UTF-8 y terminado en `\n` (JSON Lines).

Las clases de cada mensaje y de su cuerpo están en el módulo `opsec-protocolo`: `Mensaje`, `TipoMensaje` y el paquete `dto`. Las pruebas de ese módulo leen todos estos ejemplos, así que un ejemplo que no coincida con el contrato hace fallar el build.

Este directorio es un contrato: los cambios se piden con una solicitud de cambio de contrato.
