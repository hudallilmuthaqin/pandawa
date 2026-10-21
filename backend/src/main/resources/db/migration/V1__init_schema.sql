-- =====================================================================
-- PB PANDAWA - initial schema
-- Money is stored as NUMERIC(12,0): whole rupiah, no fractional sen.
-- Instants are TIMESTAMPTZ (UTC); the local playing date (Asia/Jakarta by
-- default) is computed by the backend and stored in matches.play_date.
-- =====================================================================

CREATE TABLE players (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(100) NOT NULL,
    nickname        VARCHAR(40)  NOT NULL,
    phone           VARCHAR(30),
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    registered_on   DATE         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);
-- Nickname is the human-facing unique identity (case-insensitive) so two
-- "Didi" cannot be confused in statistics. Players are never hard-deleted.
CREATE UNIQUE INDEX ux_players_nickname ON players (lower(nickname));
CREATE INDEX ix_players_status ON players (status);
CREATE INDEX ix_players_full_name ON players (lower(full_name));

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(60)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(10)  NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    player_id       BIGINT REFERENCES players (id),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    token_version   INT          NOT NULL DEFAULT 0,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_users_username ON users (lower(username));
CREATE UNIQUE INDEX ux_users_player ON users (player_id) WHERE player_id IS NOT NULL;

-- Permission mapping for the USER role. ADMIN implicitly holds every permission.
CREATE TABLE role_permissions (
    role        VARCHAR(10) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    permission  VARCHAR(40) NOT NULL,
    PRIMARY KEY (role, permission)
);

CREATE TABLE club_settings (
    id                          SMALLINT PRIMARY KEY CHECK (id = 1),
    club_name                   VARCHAR(80)   NOT NULL,
    shuttlecock_price           NUMERIC(12,0) NOT NULL CHECK (shuttlecock_price >= 0),
    court_price_per_day         NUMERIC(12,0) NOT NULL CHECK (court_price_per_day >= 0),
    court_count                 SMALLINT      NOT NULL DEFAULT 1,
    timezone                    VARCHAR(40)   NOT NULL,
    court_split_method          VARCHAR(20)   NOT NULL CHECK (court_split_method IN ('EQUAL_SPLIT', 'CLUB_ONLY')),
    self_registration_enabled   BOOLEAN       NOT NULL DEFAULT FALSE,
    score_enabled               BOOLEAN       NOT NULL DEFAULT TRUE,
    correction_requires_reason  BOOLEAN       NOT NULL DEFAULT TRUE,
    user_export_enabled         BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                     BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE matches (
    id                  BIGSERIAL PRIMARY KEY,
    status              VARCHAR(12)   NOT NULL CHECK (status IN ('WAITING', 'READY', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    queue_position      INT,
    shuttlecock_count   INT           NOT NULL DEFAULT 0 CHECK (shuttlecock_count >= 0),
    shuttlecock_price   NUMERIC(12,0) CHECK (shuttlecock_price >= 0),  -- snapshot taken at start
    play_date           DATE,                                          -- local date at completion
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancel_reason       VARCHAR(200),
    winner_team         SMALLINT CHECK (winner_team IN (1, 2)),
    score               VARCHAR(60),
    notes               VARCHAR(500),
    created_by          BIGINT NOT NULL REFERENCES users (id),
    started_by          BIGINT REFERENCES users (id),
    completed_by        BIGINT REFERENCES users (id),
    cancelled_by        BIGINT REFERENCES users (id),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_matches_queue CHECK (
        (status IN ('WAITING', 'READY') AND queue_position IS NOT NULL)
        OR (status NOT IN ('WAITING', 'READY') AND queue_position IS NULL)),
    CONSTRAINT ck_matches_completed CHECK (
        status <> 'COMPLETED'
        OR (play_date IS NOT NULL AND completed_at IS NOT NULL AND started_at IS NOT NULL AND shuttlecock_price IS NOT NULL))
);
-- Only ONE court: at most one match may be IN_PROGRESS at any time.
CREATE UNIQUE INDEX ux_matches_single_in_progress ON matches ((1)) WHERE status = 'IN_PROGRESS';
-- Queue order: positions are renumbered 1..n under a pessimistic queue lock.
CREATE INDEX ix_matches_queue_position ON matches (queue_position) WHERE status IN ('WAITING', 'READY');
CREATE INDEX ix_matches_status ON matches (status);
CREATE INDEX ix_matches_play_date ON matches (play_date) WHERE status = 'COMPLETED';
CREATE INDEX ix_matches_completed_at ON matches (completed_at);

CREATE TABLE match_players (
    match_id    BIGINT   NOT NULL REFERENCES matches (id),
    player_id   BIGINT   NOT NULL REFERENCES players (id),
    team        SMALLINT NOT NULL CHECK (team IN (1, 2)),
    slot        SMALLINT NOT NULL CHECK (slot IN (1, 2)),
    PRIMARY KEY (match_id, player_id),        -- a player appears once per match
    CONSTRAINT ux_match_players_slot UNIQUE (match_id, team, slot)
);
CREATE INDEX ix_match_players_player ON match_players (player_id);

-- Event log for every state change and shuttlecock movement.
CREATE TABLE match_events (
    id              BIGSERIAL PRIMARY KEY,
    match_id        BIGINT      NOT NULL REFERENCES matches (id),
    type            VARCHAR(30) NOT NULL,
    delta           INT,
    count_after     INT,
    reason          VARCHAR(200),
    request_id      UUID,
    actor_id        BIGINT REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Idempotency: a retried "+1 KOK" request with the same id is applied once.
CREATE UNIQUE INDEX ux_match_events_request ON match_events (request_id) WHERE request_id IS NOT NULL;
CREATE INDEX ix_match_events_match ON match_events (match_id);

-- One court charge per local playing date, created when the first match of
-- that date is completed. The price/split method are snapshots.
CREATE TABLE court_daily_charges (
    id              BIGSERIAL PRIMARY KEY,
    play_date       DATE          NOT NULL UNIQUE,
    amount          NUMERIC(12,0) NOT NULL CHECK (amount >= 0),
    split_method    VARCHAR(20)   NOT NULL CHECK (split_method IN ('EQUAL_SPLIT', 'CLUB_ONLY')),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT REFERENCES users (id),
    actor_username  VARCHAR(60),
    action          VARCHAR(50)  NOT NULL,
    entity_type     VARCHAR(30)  NOT NULL,
    entity_id       BIGINT,
    summary         VARCHAR(300) NOT NULL,
    details         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_logs_created ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id);

-- Defaults
INSERT INTO club_settings (id, club_name, shuttlecock_price, court_price_per_day, court_count, timezone,
                           court_split_method, self_registration_enabled, score_enabled)
VALUES (1, 'PB PANDAWA', 4000, 7000, 1, 'Asia/Jakarta', 'EQUAL_SPLIT', FALSE, TRUE);

INSERT INTO role_permissions (role, permission) VALUES
    ('USER', 'QUEUE_CREATE'),
    ('USER', 'MATCH_START'),
    ('USER', 'SHUTTLE_RECORD'),
    ('USER', 'MATCH_COMPLETE');
