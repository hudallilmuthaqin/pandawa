# PB PANDAWA – Badminton Club Management System

Aplikasi full-stack untuk mengelola klub badminton dengan **1 lapangan**: pemain, antrean 2 vs 2, game berlangsung,
pencatatan kok, biaya kok & lapangan, riwayat, statistik, serta rekap harian/bulanan/range date.
Dirancang **mobile-first**: tombol MULAI, +1 KOK, dan SELESAI berukuran besar dan mudah dijangkau di HP.

| Bagian | Teknologi |
|---|---|
| Frontend | React 19 + TypeScript, Vite, Tailwind CSS 4, React Router, TanStack Query, Recharts, Zod + React Hook Form |
| Backend | Java 21, Spring Boot 3.5, Spring Web/Data JPA/Security, JWT, Bean Validation, Flyway, springdoc-openapi |
| Database | PostgreSQL 16 |
| Infra | Docker, Docker Compose, Nginx |

```
backend/    Spring Boot REST API (+ migration Flyway di src/main/resources/db/migration)
frontend/   React SPA
docs/       Arsitektur, aturan perhitungan, asumsi bisnis, dokumentasi API
```

---

## Menjalankan aplikasi

### Opsi A – Docker Compose (stack lengkap)

```bash
cp .env.example .env      # isi DB_PASSWORD dan JWT_SECRET (min. 32 karakter, mis. `openssl rand -base64 48`)
docker compose up -d --build
```

Buka **http://localhost:8081** dan login dengan `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD`.
Admin pertama hanya dibuat bila belum ada admin; setelah login, ganti password lalu hapus kedua variabel tersebut.
Profil `prod` tidak pernah membuat data demo.

Untuk mencoba stack Docker dengan data demo, set `SPRING_PROFILES_ACTIVE=dev` dan `SEED_DEMO_DATA=true` di `.env`
(**khusus pengujian**).

### Opsi B – Development lokal **tanpa Docker** (PostgreSQL embedded)

Butuh Java 21 dan Node 20+. Backend menjalankan PostgreSQL asli (biner embedded, data di `backend/.local-pg/`):

```bash
cd backend && ./mvnw spring-boot:test-run          # API di http://localhost:8080 (profil dev + data demo)
cd frontend && npm install && npm run dev           # UI di http://localhost:5173 (proxy /api → :8080)
```

### Opsi C – Development dengan PostgreSQL di Docker

```bash
docker compose -f docker-compose.dev.yml up -d      # PostgreSQL di localhost:5432 (pandawa/pandawa)
cd backend && ./mvnw spring-boot:run                # profil dev
cd frontend && npm run dev
```

### Opsi D – PostgreSQL yang terpasang di laptop / IntelliJ IDEA

Buat database kosong khusus aplikasi ini (jangan memakai database proyek lain):

```bash
psql -U postgres -c "CREATE DATABASE pandawa"
```

Lalu jalankan `spring-boot:run` (atau `PandawaApplication` di IntelliJ). Koneksi default di `application.yml`:
`jdbc:postgresql://localhost:5432/pandawa`, user `postgres`. Sebaiknya kredensial diisi lewat
*Run Configuration → Environment variables* (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), bukan di file.
Flyway membuat semua tabel otomatis saat pertama kali dijalankan.

Akses dari HP di jaringan yang sama: `http://<IP-laptop>:5173`.

### Akses publik sementara (Cloudflare Tunnel, tanpa server)

```bash
cp .env.example .env            # isi DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET (acak!)
./scripts/start-public.sh       # backend prod + build frontend + tunnel → URL https://….trycloudflare.com
./scripts/stop-public.sh
```

Aplikasi tetap berjalan di laptop ini: URL hanya hidup selama laptop menyala, dan berganti setiap kali dijalankan.
Jaringan dengan firewall SSL-inspection (mis. FortiGate kantor) memblokir tunnel; gunakan jaringan lain atau hotspot HP.
Sebelum dibuka ke publik, pastikan password admin bukan password demo.

### Akun demo (hanya bila `SEED_DEMO_DATA=true`)

Data demo **tidak lagi dibuat otomatis**. Aktifkan dengan environment variable `SEED_DEMO_DATA=true`
(hanya berjalan pada database kosong). `./mvnw spring-boot:test-run` tetap memakai data demo.

| Username | Password | Role |
|---|---|---|
| `admin` | `admin12345` | Admin |
| `rizal` | `pemain12345` | User (terhubung ke pemain Rizal) |
| `hudal` | `pemain12345` | User (terhubung ke pemain Hudal) |

Password demo dapat diganti lewat `SEED_ADMIN_PASSWORD` / `SEED_USER_PASSWORD`. Seeder hanya berjalan pada database
kosong dan selalu nonaktif di profil `prod`. Data demo berisi 10 pemain, riwayat 2 minggu terakhir, dan antrean
contoh dari brief (Rizal - Azis VS Didi - Arif, dst.).

---

## Testing

```bash
cd backend && ./mvnw test        # 44 unit + integration test terhadap PostgreSQL asli (embedded, tanpa Docker)
cd frontend && npm test          # unit test helper format/periode
cd frontend && npm run build     # type-check + build produksi
```

Cakupan integration test (lihat `backend/src/test/java/id/pbpandawa`):

| # | Skenario | Test |
|---|---|---|
| 1 | Menambahkan pemain (+ duplikasi nickname ditolak) | `PlayerApiTest` |
| 2–3 | Antrean 4 pemain; pemain sama dua kali ditolak | `MatchFlowTest` |
| 4–5 | Memulai game; dua game bersamaan dicegah (termasuk 4 request paralel) | `MatchFlowTest`, `ConcurrencyTest` |
| 6–7 | +1 kok, idempotensi retry, biaya game (pemain × harga kok) | `MatchFlowTest`, `ConcurrencyTest` |
| 8–9 | Selesai → keluar antrean, berikutnya READY; statistik bertambah | `MatchFlowTest` |
| 10 | Biaya kok per pemain = harga × jumlah game (contoh brief: Rizal 2 game = Rp8.000) | `ReportCalculationTest` |
| 11–13 | Lapangan sekali per tanggal; range 1–12 Okt 8 hari = Rp56.000; game belum selesai dikecualikan | `ReportCalculationTest` |
| 14 | Tanggal bermain zona Asia/Jakarta (lewat tengah malam WIB) | `ReportCalculationTest` |
| 15 | Selesai dua kali dicegah (juga paralel) | `MatchFlowTest`, `ConcurrencyTest` |
| 16 | Biaya kok klub = jumlah pemain × harga per game = total biaya kok semua pemain | `ReportCalculationTest` |
| 17 | Perubahan harga tidak mengubah riwayat | `ReportCalculationTest` |
| 18 | User tidak bisa mengakses fitur admin | `AuthorizationTest`, `PlayerApiTest` |

---

## Dokumentasi

- **Arsitektur, skema database, aturan perhitungan & asumsi bisnis:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- **CI/CD (Jenkins → Docker, auto deploy dari branch `main`):** [`infra/jenkins/README.md`](infra/jenkins/README.md)
- **Dokumentasi API:** [`docs/API.md`](docs/API.md); OpenAPI interaktif di `http://localhost:8080/swagger-ui.html`
  (aktif di dev; di prod set `SWAGGER_ENABLED=true` bila diperlukan).

## Konfigurasi

| Variabel | Keterangan |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Koneksi PostgreSQL |
| `JWT_SECRET` | Rahasia penandatangan token (wajib, ≥ 32 karakter) |
| `JWT_TTL` | Masa berlaku token (default `12h`) |
| `CORS_ALLOWED_ORIGINS` | Origin yang diizinkan bila frontend di domain lain (di Docker frontend & API satu origin lewat Nginx) |
| `SPRING_PROFILES_ACTIVE` | `dev` atau `prod` |
| `ADMIN_BOOTSTRAP_USERNAME`, `ADMIN_BOOTSTRAP_PASSWORD` | Admin pertama (dibuat sekali bila belum ada admin; password ≥ 12 karakter) |
| `SEED_DEMO_DATA` | Data demo (hanya dev) |
| `SWAGGER_ENABLED` | Swagger UI di prod |

Harga kok, harga lapangan, zona waktu, metode pembagian lapangan, izin user, pendaftaran mandiri, skor, kebijakan
koreksi, dan ekspor diatur Admin di menu **Pengaturan** (tersimpan di database, tercatat di audit log).
