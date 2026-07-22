# Postman Collection

Tests for the Document Management Service.

## Files

- `document-management-service.postman_collection.json` — the collection.
- `document-management-local.postman_environment.json` — the `Local` environment (`baseUrl`, `user`, `docName`).

## Import

In Postman: **Import** → drag the two files. Then select the **Document Management - Local** environment in the selector at the top right.

## Requirements

The service, MinIO, and Postgres must run (refer to [`../minio-local-setup.md`](../minio-local-setup.md)). By default, the service listens on `http://localhost:8080` and MinIO listens on `http://localhost:9000`.

## End-to-end flow

The service does not get the file bytes. It uses **presigned MinIO URLs**. The *End-to-end flow* folder runs the real cycle in order. The scripts chain the `id`, the `uploadUrl`, and the `downloadUrl` between the steps:

1. **Register the document** → returns the `id` and the `uploadUrl` (the scripts save them as collection variables).
2. **Send the bytes to MinIO** (`PUT {{uploadUrl}}`) → open **Body → binary** and select a local `.pdf`. This PUT goes directly to MinIO, not to the service.
3. **Confirm the upload** (`complete`) → the service validates the object and sets it to COMPLETED.
4. **Search the documents**.
5. **Get the download URL** → the script saves the `downloadUrl`.
6. **Download the bytes from MinIO**.

> The only manual step is the file selection in step 2. You can run the full folder with the **Collection Runner**. First select the file, or step 3 returns 409.

The *Individual endpoints* folder has the same endpoints alone. It includes examples of 400 errors (validation and path traversal) and the health check.

## Order in `/search`

The `sort` parameter uses the Spring-Data format `property[,direction]`, for example `sort=createdAt,desc` or `sort=name,asc`. Repeat the parameter to sort by more than one property. Without `sort`, the default order is `createdAt` in the decreasing sequence.
