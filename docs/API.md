# REST API – PB Pandawa

Base URL: `/api`. Semua endpoint (kecuali yang ditandai *publik*) memerlukan header `Authorization: Bearer <token>`.
Spesifikasi OpenAPI lengkap: `GET /v3/api-docs`, UI interaktif: `/swagger-ui.html`.

Tanggal: `YYYY-MM-DD` (tanggal lokal klub). Waktu: ISO-8601 UTC. Nominal: rupiah bulat (angka).
Pagination: `?page=0&size=20` → `{ content, page, size, totalElements, totalPages }`.
Periode laporan: `period=TODAY|WEEK|MONTH|CUSTOM` (+ `from`, `to` untuk CUSTOM, inklusif, maks. 366 hari).

Format error:
```json
{ "timestamp": "...", "status": 409, "code": "MATCH_ALREADY_IN_PROGRESS",
  "message": "Masih ada pertandingan yang sedang berlangsung...", "path": "/api/matches/5/start", "fieldErrors": [] }
```

## Authentication

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| POST | `/auth/login` | publik | `{username, password}` → `{token, expiresAt, user}`. 401 bila salah, 429 setelah 5 kali gagal (kunci 5 menit). Login sukses memasang cookie HttpOnly `pandawa_device`; percobaan dengan cookie itu dihitung per perangkat, sehingga orang lain yang salah password tidak bisa mengunci pemilik akun. |
| POST | `/auth/register` | publik | Pendaftaran mandiri pemain bila diizinkan: `{fullName, nickname, phone?, username, password}`. |
| GET | `/auth/me` | login | Profil akun, role, `playerId`, daftar permission. |
| POST | `/auth/logout` | login | Mencabut semua token user (204). |
| POST | `/auth/change-password` | login | `{currentPassword, newPassword}`. |

## Players

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| GET | `/players?q=&status=ACTIVE&page=&size=` | login | Cari pemain (telepon pemain lain disembunyikan untuk user). Termasuk `totalGames`. |
| POST | `/players` | `PLAYER_MANAGE` | `{fullName, nickname, phone?, registeredOn?, status?}`. 409 `DUPLICATE_PLAYER`. |
| GET | `/players/{id}` | login | Detail pemain. |
| PUT | `/players/{id}` | `PLAYER_MANAGE` | Ubah data. |
| PATCH | `/players/{id}/status` | `PLAYER_MANAGE` | `{status: ACTIVE|INACTIVE}` – riwayat tetap tersimpan. |
| GET | `/players/{id}/statistics?from=&to=` | admin / pemilik | Total sepanjang waktu & periode, alokasi kok, biaya pribadi, alokasi lapangan, menang/kalah, frekuensi per hari/minggu/bulan, 10 game terakhir. |

## Queue

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| GET | `/queue` | login | `{serverTime, current, next, queue[]}`. |
| POST | `/queue` | `QUEUE_CREATE` | `{team1PlayerIds:[a,b], team2PlayerIds:[c,d]}`. 400 `DUPLICATE_PLAYER_IN_MATCH`, `PLAYER_INACTIVE`. |
| PATCH | `/queue/{id}/position` | `QUEUE_MANAGE` | `{position}` (1 = terdepan). Mengembalikan antrean baru. |
| DELETE | `/queue/{id}` | `QUEUE_MANAGE` atau pembuat | Hapus game belum dimulai (disimpan CANCELLED). Body opsional `{reason}`. |

## Matches

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| POST | `/matches/start-next` | `MATCH_START` | Mulai game terdepan. |
| POST | `/matches/{id}/start` | `MATCH_START` | User: hanya READY; admin: game antrean mana pun. 409 `MATCH_ALREADY_IN_PROGRESS`, `NOT_FRONT_OF_QUEUE`. |
| POST | `/matches/{id}/shuttlecocks` | `SHUTTLE_RECORD` (+1) / `SHUTTLE_CORRECT` (−) | `{delta: 1, requestId: "<uuid>", reason?}`. `requestId` yang sama tidak dihitung dua kali. |
| POST | `/matches/{id}/complete` | `MATCH_COMPLETE` | `{expectedShuttlecockCount?, winnerTeam?: 1|2, score?, notes?}`. 409 `ALREADY_COMPLETED`, `SHUTTLECOCK_COUNT_CHANGED`. |
| POST | `/matches/{id}/cancel` | antrean: pembuat/`QUEUE_MANAGE`; berlangsung: `MATCH_CANCEL` | `{reason}`. |
| POST | `/matches/{id}/corrections` | `MATCH_CORRECT` (admin) | Koreksi game selesai: `{shuttlecockCount, reason}`. Tercatat di audit log. |
| GET | `/matches/current` | login | Game berlangsung (204 bila tidak ada). |
| GET | `/matches/{id}` | login* | `{match, events[], serverTime}`. *User hanya untuk game selesai yang ia ikuti. |
| GET | `/matches/history?from=&to=&playerId=&q=&page=&size=` | login | Riwayat game selesai. User otomatis dibatasi ke game miliknya. |
| GET | `/matches?status=&page=` | `REPORT_VIEW` | Semua game (admin). |

Contoh objek game (diringkas):
```json
{ "id": 25, "status": "IN_PROGRESS", "queuePosition": null,
  "team1": [{"id":1,"nickname":"Rizal"},{"id":2,"nickname":"Azis"}],
  "team2": [{"id":3,"nickname":"Didi"},{"id":4,"nickname":"Arif"}],
  "shuttlecockCount": 4, "shuttlecockPrice": 4000, "priceSnapshot": true, "shuttlecockCost": 16000,
  "startedAt": "2026-10-12T12:00:00Z", "durationSeconds": 1500, "playDate": null, "version": 6 }
```

## Reports (admin: `REPORT_VIEW`)

| Method | Path | Keterangan |
|---|---|---|
| GET | `/reports/dashboard?period=` | Ringkasan periode, jumlah pemain, harga saat ini, data harian, rekap pemain, antrean, aktivitas terbaru. |
| GET | `/reports/daily?date=` | Ringkasan hari, daftar game, rekap pemain. |
| GET | `/reports/monthly?month=YYYY-MM` | Ringkasan bulan, rata-rata game per hari bermain, harian, pemain paling aktif. |
| GET | `/reports/range?period=CUSTOM&from=&to=` | Ringkasan, rekap per tanggal, rekap per pemain, catatan metode alokasi. |
| GET | `/reports/players?period=` | Rekap per pemain. |
| GET | `/reports/me?period=` | **login** – statistik & estimasi biaya pribadi (akun harus terhubung ke pemain). |
| GET | `/reports/export?type=players|matches|daily&period=` | CSV (UTF-8 BOM). User hanya `matches` miliknya bila diizinkan pengaturan. |

`summary`: `{ totalGames, uniquePlayers, totalShuttlecocks, shuttlecockCost, playDays, courtCost, totalExpense }`
dengan `totalExpense = shuttlecockCost + courtCost`.

## Settings, users, audit

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| GET | `/settings/public` | publik | Nama klub & status pendaftaran mandiri. |
| GET | `/settings` | login | Harga, zona waktu, metode pembagian, toggle, izin USER, `version`. |
| PUT | `/settings` | `SETTINGS_MANAGE` | Ubah semua pengaturan (kirim `version` untuk deteksi konflik). Harga baru hanya untuk game baru. |
| GET | `/settings/permissions` | login | Katalog permission. |
| GET/POST | `/users` | `USER_MANAGE` | Daftar / buat akun `{username, password, role, playerId?}`. |
| PUT | `/users/{id}` | `USER_MANAGE` | `{role, enabled, playerId?}` – tidak bisa menurunkan diri sendiri / admin terakhir. |
| POST | `/users/{id}/reset-password` | `USER_MANAGE` | `{newPassword}`. |
| GET | `/audit-logs?entityType=&action=&page=` | `AUDIT_VIEW` | Audit log terbaru. |
