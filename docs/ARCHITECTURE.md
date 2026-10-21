# Arsitektur & Aturan Bisnis – PB Pandawa

## 1. Gambaran umum

```
 HP / Laptop ──HTTPS──► Nginx (frontend SPA, /api proxy) ──► Spring Boot API ──► PostgreSQL
                         React + TanStack Query               JWT, JPA, Flyway
```

- **Frontend** memanggil API dengan origin yang sama (`/api`), sehingga di produksi tidak perlu CORS.
- Status antrean & game berlangsung diperbarui otomatis (polling 4 detik, juga saat tab kembali aktif) tanpa memuat ulang halaman.
- Timer game dihitung di HP dari `startedAt` dan dikoreksi dengan `serverTime` backend: berdetak tiap detik tanpa request tiap detik.
- **Backend** berlapis: Controller (validasi & otorisasi) → Service (transaksi & aturan bisnis) → Repository (JPA).
  Semua waktu memakai satu `Clock` bean (UTC) sehingga dapat diuji.

Paket backend (`id.pbpandawa`): `user` (akun, auth, role), `security` (JWT, permission), `player`, `match`
(antrean + siklus game), `court` (biaya lapangan harian), `settings`, `report` (rekap & CSV), `audit`, `seed`.

## 2. Role & permission

| Permission | Admin | User (default) | Bisa diberikan ke User |
|---|:-:|:-:|:-:|
| `QUEUE_CREATE` tambah antrean | ✔ | ✔ | ✔ |
| `QUEUE_MANAGE` atur urutan / hapus antrean orang lain | ✔ | – | ✔ |
| `MATCH_START` mulai game terdepan | ✔ | ✔ | ✔ |
| `SHUTTLE_RECORD` +1 kok | ✔ | ✔ | ✔ |
| `SHUTTLE_CORRECT` koreksi −1 kok saat main | ✔ | – | ✔ |
| `MATCH_COMPLETE` selesaikan game | ✔ | ✔ | ✔ |
| `MATCH_CANCEL`, `MATCH_CORRECT`, `PLAYER_MANAGE`, `REPORT_VIEW`, `SETTINGS_MANAGE`, `USER_MANAGE`, `AUDIT_VIEW` | ✔ | – | ✗ (admin saja) |

- Izin USER disimpan di tabel `role_permissions` dan diubah admin di **Pengaturan → Izin User**.
- Setiap request memuat ulang user & izin dari database: akun dinonaktifkan, logout, atau izin yang dicabut berlaku seketika.
  Logout menaikkan `users.token_version` sehingga semua token lama tidak berlaku.
- Otorisasi diterapkan di server (`@PreAuthorize` + pemeriksaan di service). Menyembunyikan tombol di UI hanya kenyamanan.
- User hanya melihat riwayat, statistik, dan estimasi biaya miliknya sendiri; nomor telepon pemain lain disembunyikan.
- Password di-hash BCrypt (cost 12); login dibatasi 5 kali gagal → kunci 5 menit per username.

## 3. Skema database (Flyway `V1__init_schema.sql`)

| Tabel | Isi & aturan penting |
|---|---|
| `players` | Profil pemain. `lower(nickname)` **unik** sebagai identitas manusia; ID numerik sebagai identitas sistem. Tidak pernah dihapus, hanya `status = INACTIVE`. |
| `users` | Akun login, terpisah dari profil pemain (`player_id` opsional, unik). `role`, `enabled`, `token_version`. |
| `role_permissions` | Mapping permission untuk role USER. |
| `club_settings` | Satu baris (`id = 1`): nama klub, harga kok, harga lapangan, zona waktu, metode pembagian lapangan, toggle fitur. Row lock-nya dipakai sebagai mutex antrean. |
| `matches` | Status, posisi antrean, `shuttlecock_count`, **snapshot** `shuttlecock_price`, `play_date` (tanggal lokal), waktu mulai/selesai/batal, pemenang/skor/catatan, user pelaku. CHECK constraint menjamin data game selesai lengkap. |
| `match_players` | Partisipasi: PK `(match_id, player_id)` → pemain tidak bisa dua kali dalam satu game; unik `(match_id, team, slot)`. |
| `match_events` | Log append-only: dibuat, dipindah, mulai, +kok, koreksi, selesai, batal. `request_id` unik → idempotensi. |
| `court_daily_charges` | Satu baris per `play_date` (**UNIQUE**), berisi snapshot harga & metode pembagian. |
| `audit_logs` | Perubahan penting: harga/pengaturan, pembatalan, koreksi kok, koreksi game selesai, akun & role, pemain. |

Index: status, `play_date` (partial untuk COMPLETED), `completed_at`, partisipasi per pemain, nickname/nama (pencarian),
audit per waktu & entitas.

Nominal uang: `NUMERIC(12,0)` (rupiah bulat) ↔ `BigDecimal` di Java. Jumlah kok & game: integer.
Waktu: `TIMESTAMPTZ` (UTC). Tanggal bermain: `DATE` hasil konversi ke zona klub saat game selesai.

Angka turunan (biaya per game, total harian, alokasi pemain) **tidak disimpan**; semuanya dihitung dari
`shuttlecock_count × shuttlecock_price` dan `court_daily_charges`. `shuttlecock_count` disimpan pada game
(diperbarui dalam transaksi yang sama dengan event-nya) agar pembacaan antrean cepat; `match_events` tetap menjadi jejak lengkapnya.

## 4. Siklus pertandingan

```
WAITING ──(naik ke depan)──► READY ──MULAI──► IN_PROGRESS ──SELESAI──► COMPLETED
   └───────────── hapus dari antrean ──────────────┐   └── batal (admin) ──► CANCELLED
                                                   ▼
                                              CANCELLED
```

- Antrean selalu dinomori ulang 1..n; posisi 1 berstatus **READY**, sisanya **WAITING**.
- **MULAI**: user hanya boleh memulai game READY; admin boleh memilih game antrean mana pun. Validasi: tidak ada game
  IN_PROGRESS, tepat 4 pemain, semua aktif. Harga kok saat itu disimpan sebagai snapshot.
- **+1 KOK**: hanya saat IN_PROGRESS; setiap ketukan membawa `requestId` (UUID) sehingga retry dari koneksi lambat tidak
  tercatat ganda. Pengurangan (−1) butuh `SHUTTLE_CORRECT` dan alasan (sesuai kebijakan), serta tercatat di audit.
- **SELESAI** (satu transaksi): kunci baris game → cek status (selesai dua kali → 409) → cek `expectedShuttlecockCount`
  (409 bila HP menampilkan angka lama) → simpan pemenang/skor/catatan → `completed_at`, `play_date`, status COMPLETED →
  buat biaya lapangan tanggal tersebut bila belum ada → event & audit. Gagal di langkah mana pun = rollback seluruhnya.
- Game selesai tidak dapat diubah/dihapus. Koreksi jumlah kok hanya oleh admin lewat
  `POST /api/matches/{id}/corrections` dengan alasan wajib; harga snapshot tidak berubah; tercatat di event & audit log.

## 5. Konsistensi & konkurensi

| Risiko | Penanganan |
|---|---|
| Dua game berjalan bersamaan | Pemeriksaan di service di bawah row lock `club_settings` **dan** partial unique index `ux_matches_single_in_progress` di database (pertahanan terakhir). Diuji dengan 4 request MULAI paralel → tepat 1 berhasil. |
| Perubahan antrean bersamaan | Semua mutasi antrean mengambil `SELECT … FOR UPDATE` pada baris `club_settings`, lalu menomori ulang. Urutan lock selalu settings → match (tanpa deadlock). |
| Double tap / retry +1 kok | `request_id` unik + pemeriksaan setelah mengunci baris game. Diuji: 5 request kembar paralel + 5 request berbeda → hasil 6. |
| Selesai dua kali | Row lock pada game + pemeriksaan status → 409 `ALREADY_COMPLETED`. Diuji paralel. |
| Biaya lapangan ganda | `UNIQUE(play_date)` + `INSERT … ON CONFLICT DO NOTHING`. |
| Edit pengaturan bersamaan | Optimistic `version` pada pengaturan & entitas (`@Version`). |
| Nominal dari frontend | Tidak pernah diterima; backend selalu menghitung dari data tersimpan. |

## 6. Aturan perhitungan biaya

Harga awal: kok **Rp4.000 per pemain per game**, lapangan **Rp7.000/hari**, 1 lapangan. Dapat diubah admin; hanya berlaku untuk game baru
(harga kok) dan tanggal bermain baru (harga lapangan).

1. **Biaya kok per game** = `jumlah pemain × harga kok snapshot game tersebut`. Setiap pemain membayar harga kok
   sekali per game, **berapa pun kok yang dipakai**; jumlah kok tetap dicatat untuk statistik pemakaian.
   Contoh: 4 pemain, 3 kok, harga Rp5.000 → 4 × Rp5.000 = Rp20.000 (Rp5.000 per pemain).
2. **Biaya kok klub (periode)** = jumlah biaya kok dari setiap game COMPLETED, **masing-masing game dihitung sekali**.
3. **Biaya lapangan** (metode `EQUAL_SPLIT`/`CLUB_ONLY`; untuk `PER_PLAYER` lihat poin 6) = jumlah `court_daily_charges` pada tanggal yang punya ≥ 1 game selesai
   = `jumlah hari bermain × harga lapangan per hari` (dengan snapshot harga per tanggal). Tidak bergantung jumlah game;
   tanggal tanpa game selesai tidak dikenakan biaya.
4. **Total pengeluaran klub** = biaya kok klub + biaya lapangan.
5. **Biaya kok per pemain** (laporan pribadi) = Σ harga snapshot dari setiap game yang diikuti (= jumlah game × harga
   bila harga tidak berubah). Kok terpakai pemain = total kok dari setiap game yang diikuti (statistik saja).
   Jumlah biaya kok semua pemain = biaya kok klub (diuji: contoh brief 2 game → Rp32.000 untuk keduanya).
6. **Alokasi biaya lapangan pribadi** (pengaturan):
   - `EQUAL_SPLIT` – biaya lapangan tanggal itu dibagi rata ke **pemain unik** tanggal itu (bukan per game).
     Pembulatan: tiap pemain mendapat `floor(biaya / n)`; sisa rupiah (≤ n−1) diberikan Rp1 ke pemain dengan ID terkecil
     terlebih dahulu, sehingga total pembagian **selalu sama persis** dengan biaya lapangan. Contoh Rp7.000 / 6 pemain →
     4 × Rp1.167 + 2 × Rp1.166.
   - `PER_PLAYER` – setiap pemain unik pada tanggal itu membayar harga lapangan per hari **penuh** (sekali per tanggal,
     berapa pun jumlah game-nya). Biaya lapangan klub tanggal itu = `harga × jumlah pemain unik`
     (contoh: 4 pemain × Rp7.000 = Rp28.000). `court_daily_charges.amount` tetap menyimpan snapshot harga per hari.
   - `CLUB_ONLY` – biaya lapangan hanya pengeluaran klub, tidak masuk iuran pribadi.
7. **Estimasi biaya pribadi** = biaya kok pribadi + alokasi lapangan (ditampilkan terpisah).
8. **Total bermain** pemain bertambah hanya saat game yang diikutinya menjadi COMPLETED.

### Tanggal & zona waktu

- `play_date` = tanggal **lokal Asia/Jakarta** (dapat diatur) saat game diselesaikan, dihitung backend dari `completed_at`.
  Contoh: selesai 00:20 WIB 13 Okt (17:20 UTC 12 Okt) → masuk tanggal 13 Okt.
- Semua rekap memakai rentang tanggal lokal **inklusif** `from ≤ play_date ≤ to`, sehingga tidak ada pergeseran zona waktu.
- Preset: Hari Ini, Minggu Ini (Senin–Minggu), Bulan Ini, Custom (maks. 366 hari, `to ≥ from`).
- Dashboard, rekap harian, bulanan, range, statistik pemain, dan CSV memakai satu fungsi perhitungan yang sama
  (`ReportService.compute`) langsung dari database → angkanya selalu konsisten.

## 7. Asumsi bisnis yang diambil

1. **Snapshot harga kok diambil saat game dimulai** (bukan saat selesai) agar biaya yang tampil selama permainan tidak
   berubah; perubahan harga di tengah game tidak memengaruhi game tersebut.
2. **Tanggal bermain = tanggal selesai** (zona klub). Game yang melewati tengah malam masuk ke tanggal selesainya.
3. **Biaya lapangan dibuat saat game pertama hari itu selesai**, beserta snapshot harga & metode pembagian. Mengubah metode
   pembagian berlaku untuk tanggal bermain berikutnya; laporan lama tetap konsisten.
4. **Biaya kok = harga kok per pemain per game**, tidak bergantung jumlah kok (keputusan pemilik klub, menggantikan
   rumus lama `jumlah kok × harga`). Contoh brief: Rizal 2 game × Rp4.000 = Rp8.000.
5. **Game dibatalkan** (dari antrean maupun saat berlangsung) disimpan sebagai CANCELLED untuk audit, tidak memengaruhi
   statistik, biaya kok, maupun biaya lapangan.
6. **Nickname unik (case-insensitive)** menjadi identitas yang terlihat; nama lengkap boleh sama.
7. Game boleh diselesaikan dengan 0 kok (UI memberi peringatan).
8. Seorang pemain boleh berada di beberapa pertandingan antrean sekaligus (contoh brief: Rizal di game 1 dan 2), tetapi
   tidak dua kali dalam satu pertandingan.
9. Hanya 1 lapangan (`court_count = 1`); struktur siap dikembangkan, tetapi aturan "maks. 1 IN_PROGRESS" dikunci di database.
10. Pemenang & skor opsional dan dapat dimatikan di pengaturan.
11. Ekspor CSV untuk user (bila diaktifkan admin) hanya berisi riwayat game miliknya.

## 8. Logging & error

- Format error konsisten: `{ timestamp, status, code, message, path, fieldErrors[] }`, pesan dalam Bahasa Indonesia,
  `code` stabil untuk ditangani frontend (mis. `MATCH_ALREADY_IN_PROGRESS`, `ALREADY_COMPLETED`, `DUPLICATE_PLAYER`).
- Setiap entri audit juga ditulis ke log aplikasi (`AUDIT …`); error tak terduga dicatat lengkap di server tetapi tidak
  pernah mengirim stack trace ke klien.
- Health check: `GET /actuator/health`.
