Kafka Middleware DESA Backend
================================

Proyecto Spring Boot (Maven) con Java 17 — esqueleto inicial y middleware de eventos.

Requisitos
- Java 17 (JDK)
- Maven
- Docker y Docker Compose (opcional: para levantar Kafka/Zookeeper)

Resumen
- Middleware que valida tokens Keycloak, normaliza eventos y los reenruta al Core vía Kafka.
- Validación de esquema (JSON Schema) para eventos.
- Soporta modo local sin Kafka (`app.kafka.enabled=false`) para pruebas.

Cómo levantar Kafka (opcional)
- El `docker-compose.yml` incluido arranca Zookeeper + Kafka (topic `core-events`).

Desde cmd.exe:

```
cd /d C:\Users\Enzo\Documents\GitHub\DesarrolloAPSI\backend\Kafka_Middleware_DESA_2_BackEnd
docker-compose up -d
```

Seguridad: client secret por variable de entorno
- Para seguridad, el `keycloak.client-secret` se lee desde la variable de entorno `KEYCLOAK_CLIENT_SECRET`.
- Exporta la variable antes de ejecutar la app (ejemplos):
  - cmd.exe:
    ```cmd
    set KEYCLOAK_CLIENT_SECRET=8QHZseZkNWmhI7p2jFA5MbGF4ZdiS9xu
    ```
  - PowerShell:
    ```powershell
    $env:KEYCLOAK_CLIENT_SECRET = '8QHZseZkNWmhI7p2jFA5MbGF4ZdiS9xu'
    ```
- No dejes secretos en ficheros en repositorios públicos; usa vault/secret manager en producción.

Compilar y ejecutar la app
- Compilar:
```
cd /d C:\Users\Enzo\Documents\GitHub\DesarrolloAPSI\backend\Kafka_Middleware_DESA_2_BackEnd
mvn -DskipTests package
```
- Ejecutar (con Kafka habilitado):
```
java -jar target\kafka-middleware-desa-backend-0.0.1-SNAPSHOT.jar
```
- Ejecutar en modo prueba (sin Kafka, simula Core):
```
java -jar target\kafka-middleware-desa-backend-0.0.1-SNAPSHOT.jar --app.kafka.enabled=false
```

Obtener token desde Keycloak (client credentials)
- Si tienes un Keycloak corriendo externamente, obtén token con client credentials (ejemplo):

cmd.exe:
```
curl -X POST "http://localhost:8080/realms/ecommerce/protocol/openid-connect/token" -H "Content-Type: application/x-www-form-urlencoded" -d "grant_type=client_credentials&client_id=ecommerce-app&client_secret=%KEYCLOAK_CLIENT_SECRET%"
```
PowerShell:
```powershell
$tokenResp = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/realms/ecommerce/protocol/openid-connect/token' -ContentType 'application/x-www-form-urlencoded' -Body 'grant_type=client_credentials&client_id=ecommerce-app&client_secret=8QHZseZkNWmhI7p2jFA5MbGF4ZdiS9xu'
$token = $tokenResp.access_token
```

Probar flujo (POST /events y GET /events/poll)
- POST /events (ejemplo):
```
curl -X POST http://localhost:8090/events -H "Content-Type: application/json" -H "Authorization: Bearer <ACCESS_TOKEN>" -d "{\"type\":\"POST: Review creada\",\"payload\":{\"rateUpdated\":4.0,\"message\":\"Excelente producto\"},\"originModule\":\"ecommerce-app\",\"timestamp\":\"2025-09-28T02:30:04.967Z\"}"
```
- GET /events/poll:
```
curl -X GET http://localhost:8090/events/poll -H "Authorization: Bearer <ACCESS_TOKEN>"
```

Notas
- `security.jwks-uri` en `application.properties` se usa para verificar la firma del JWT si tu Keycloak está disponible.
- En modo sin Kafka (`app.kafka.enabled=false`) el middleware simula el Core almacenando el evento en memoria para que el módulo lo recupere por `/events/poll`.

Siguientes pasos (opcionales)
- Integrar secretos desde un vault.
- Añadir logging estructurado (SLF4J) y métricas.
