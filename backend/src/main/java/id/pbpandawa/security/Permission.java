package id.pbpandawa.security;

/**
 * Fine-grained permissions. ADMIN implicitly holds all of them; the USER role
 * holds the subset configured in table role_permissions (grantable only).
 */
public enum Permission {
    QUEUE_CREATE(true, "Menambah pertandingan ke antrean"),
    QUEUE_MANAGE(true, "Mengatur urutan & menghapus antrean milik orang lain"),
    MATCH_START(true, "Memulai pertandingan terdepan"),
    SHUTTLE_RECORD(true, "Mencatat +1 kok"),
    SHUTTLE_CORRECT(true, "Mengoreksi (mengurangi) jumlah kok saat game berlangsung"),
    MATCH_COMPLETE(true, "Menyelesaikan pertandingan"),
    MATCH_CANCEL(false, "Membatalkan pertandingan yang sedang berlangsung"),
    MATCH_CORRECT(false, "Mengoreksi data game yang sudah selesai"),
    PLAYER_MANAGE(false, "Mengelola data pemain"),
    REPORT_VIEW(false, "Melihat laporan klub"),
    SETTINGS_MANAGE(false, "Mengelola pengaturan & harga"),
    USER_MANAGE(false, "Mengelola akun & role"),
    AUDIT_VIEW(false, "Melihat audit log");

    private final boolean grantableToUser;
    private final String description;

    Permission(boolean grantableToUser, String description) {
        this.grantableToUser = grantableToUser;
        this.description = description;
    }

    public boolean isGrantableToUser() {
        return grantableToUser;
    }

    public String getDescription() {
        return description;
    }
}
