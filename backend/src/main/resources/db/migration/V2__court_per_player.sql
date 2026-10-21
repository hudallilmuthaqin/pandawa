-- PER_PLAYER: every unique player of a date pays the full daily court price (club court cost = price x players).
ALTER TABLE club_settings DROP CONSTRAINT club_settings_court_split_method_check;
ALTER TABLE club_settings ADD CONSTRAINT club_settings_court_split_method_check
    CHECK (court_split_method IN ('EQUAL_SPLIT', 'CLUB_ONLY', 'PER_PLAYER'));
ALTER TABLE court_daily_charges DROP CONSTRAINT court_daily_charges_split_method_check;
ALTER TABLE court_daily_charges ADD CONSTRAINT court_daily_charges_split_method_check
    CHECK (split_method IN ('EQUAL_SPLIT', 'CLUB_ONLY', 'PER_PLAYER'));
