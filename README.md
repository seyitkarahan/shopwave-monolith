# ShopWave — Monolith + Inventory Service (LAB-6)

Dağıtık Sistemler lab dersleri için e-ticaret uygulaması.  
**LAB-6** ile stok yönetimi ayrı bir Spring Boot servisine ve ayrı PostgreSQL veritabanına taşındı.

## Stack

| Katman            | Teknoloji |
|-------------------|-----------|
| Monolith backend  | Spring Boot 3.2, Java 21, JPA, Flyway, RestClient |
| Inventory service | Spring Boot 3.2, Java 21, JPA, Flyway |
| Veritabanı        | PostgreSQL 15 (2 instance) |
| Frontend          | React 18, TypeScript, Vite, Tailwind CSS |
| Infra             | Docker Compose |

## Başlatmak

```bash
docker compose up --build
```

| Servis              | URL / Bağlantı |
|---------------------|----------------|
| Frontend            | http://localhost:3000 |
| Monolith API        | http://localhost:8080/api/v1/products |
| Monolith health     | http://localhost:8080/health |
| Inventory service   | http://localhost:8081/health |
| Monolith DB         | localhost:**5433** / `shopwave` |
| Inventory DB        | localhost:**5434** / `shopwave_inventory` |

Frontend yalnızca monolith’e (`:8080`) istek atar; stok çağrıları monolith üzerinden inventory-service’e proxy edilir.

## Mimari (LAB-6 sonrası)

```
Frontend → Monolith (:8080) ──HTTP──► Inventory Service (:8081)
                │                              │
                ▼                              ▼
           shopwave DB                  shopwave_inventory DB
        (catalog, order, audit)            (inventory)
```

- `placeOrder`: önce **HTTP reserve** (inventory-service), sonra **order save** (monolith) — tek transaction yok
- `cancel` / `deliver`: `release` / `deduct` HTTP ile
- Yeni ürün: monolith product kaydı + HTTP `init` stok

## API Özeti

```bash
# Ürünler (monolith)
GET  /api/v1/products
POST /api/v1/orders

# Stok (monolith proxy → inventory-service)
GET  /api/v1/inventory/products/{id}
GET  /api/v1/inventory/low-stock
POST /api/v1/inventory/products/{id}/add

# Stok (doğrudan inventory-service — debug)
POST http://localhost:8081/api/v1/inventory/reserve
POST http://localhost:8081/api/v1/inventory/release
POST http://localhost:8081/api/v1/inventory/deduct
```

---

## LAB-6 — Test Senaryoları

### 1. Normal sipariş

```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "shippingAddress": "Istanbul",
    "items": [{"productId": 1, "quantity": 2}]
  }' | jq .
```

Stok inventory DB’de rezerve edilir; sipariş monolith DB’de oluşur.

### 2. Partial failure demo (stok rezerve, sipariş yok)

Monolith’i demo modunda yeniden başlat:

```bash
LAB6_PARTIAL_FAILURE_DEMO=true docker compose up -d backend
```

Aynı `POST /api/v1/orders` isteği **409** döner; log’da `LAB-6 PARTIAL FAILURE DEMO` görünür.  
Inventory DB’de `reserved` artmış, monolith’te yeni order **yok** — LAB-8 Saga ile telafi edilecek.

Kapatmak için:

```bash
LAB6_PARTIAL_FAILURE_DEMO=false docker compose up -d backend
```

### 3. Chaos — timeout / 503 (inventory-service)

```bash
CHAOS_ENABLED=true CHAOS_DELAY_MS=5000 CHAOS_FAIL_RATE=0 \
  docker compose up -d inventory-service
```

`read-timeout-ms` (varsayılan 3000) aşılınca monolith **503** benzeri hata döner; mesajda “state is ambiguous” uyarısı vardır (LAB-8 TODO).

Rastgele 503:

```bash
CHAOS_ENABLED=true CHAOS_FAIL_RATE=0.5 docker compose up -d inventory-service
```

### 4. Inventory servisi kapalı

```bash
docker stop shopwave-inventory-service
curl -s -X POST http://localhost:8080/api/v1/orders -H "Content-Type: application/json" \
  -d '{"customerId":1,"shippingAddress":"x","items":[{"productId":1,"quantity":1}]}'
# → 503 Service Unavailable
docker start shopwave-inventory-service
```

### 5. Stok kontrolü (inventory DB)

```bash
curl -s http://localhost:8081/api/v1/inventory/products/1 | jq .
```

---

## Lab Yol Haritası

### LAB-6 — Servis Ayrımı ✅
- [x] `inventory-service` ayrı Spring Boot uygulaması
- [x] Ayrı PostgreSQL (`inventory-db`, host port 5434)
- [x] Monolith → `RestClient` HTTP entegrasyonu
- [x] Timeout yapılandırması (`shopwave.inventory.*-timeout-ms`)
- [x] Chaos flag (delay / fail-rate) inventory-service’te
- [x] Partial failure demo: reserve OK → order NOT saved

### LAB-7 — Resilience (sonraki)
- [ ] Retry, Circuit Breaker, Bulkhead

### LAB-8 — Saga Pattern (sonraki)
- [ ] Compensation: partial failure sonrası `release`
- [ ] Outbox

---

## Geliştirme (lokal, Docker olmadan)

```bash
# Terminal 1 — inventory DB (Docker)
docker run -d --name inv-db -p 5434:5432 \
  -e POSTGRES_DB=shopwave_inventory -e POSTGRES_USER=shopwave -e POSTGRES_PASSWORD=shopwave \
  postgres:15

# Terminal 2
cd inventory-service && mvn spring-boot:run

# Terminal 3 — monolith DB + backend
cd backend && INVENTORY_SERVICE_URL=http://localhost:8081 mvn spring-boot:run
```
