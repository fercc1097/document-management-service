# Colección de Postman

Pruebas del Document Management Service.

## Archivos

- `document-management-service.postman_collection.json` — la colección.
- `document-management-local.postman_environment.json` — environment `Local` (`baseUrl`, `user`, `docName`).

## Importar

En Postman: **Import** → arrastra ambos archivos. Selecciona el environment **Document Management - Local** en el selector superior derecho.

## Requisitos

El servicio, MinIO y Postgres deben estar levantados (ver [`../minio-local-setup.md`](../minio-local-setup.md)). Por defecto el servicio escucha en `http://localhost:8080` y MinIO en `http://localhost:9000`.

## Flujo end-to-end

El servicio no recibe los bytes de los archivos: usa **URLs presignadas de MinIO**. La carpeta *Flujo end-to-end* ejecuta el ciclo real en orden y encadena `id` / `uploadUrl` / `downloadUrl` entre pasos mediante scripts:

1. **Registrar documento** → devuelve `id` + `uploadUrl` (se guardan en variables de colección).
2. **Subir bytes a MinIO** (`PUT {{uploadUrl}}`) → abre **Body → binary** y selecciona un `.pdf` local. Este PUT va directo a MinIO, no al servicio.
3. **Confirmar subida** (`complete`) → el servicio valida el objeto y lo marca COMPLETED.
4. **Buscar documentos**.
5. **Obtener URL de descarga** → guarda `downloadUrl`.
6. **Descargar bytes desde MinIO**.

> El único paso manual es seleccionar el archivo en el paso 2. Puedes correr toda la carpeta con el **Collection Runner** (recuerda seleccionar el archivo primero, o el paso 3 devolverá 409).

La carpeta *Endpoints individuales* contiene los mismos endpoints aislados, incluyendo ejemplos de errores 400 (validación y path traversal) y el health check.

## Orden en `/search`

El parámetro `sort` sigue el formato Spring-Data `property[,dirección]`, por ejemplo `sort=createdAt,desc` o `sort=name,asc`. Repite el parámetro para ordenar por varias propiedades. Sin `sort`, el orden por defecto es `createdAt` descendente.
