CREATE TABLE participants (
    id UUID PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL
);

CREATE TABLE game_profiles (
    id UUID PRIMARY KEY,
    participant_id UUID NOT NULL,
    game VARCHAR(255) NOT NULL,
    account_id VARCHAR(255),
    base_score INTEGER NOT NULL,
    manual_score INTEGER,
    version BIGINT NOT NULL,
    CONSTRAINT uk_game_profiles_participant_game UNIQUE (participant_id, game),
    CONSTRAINT fk_game_profiles_participant FOREIGN KEY (participant_id)
        REFERENCES participants (id) ON DELETE RESTRICT
);

CREATE TABLE game_profile_positions (
    profile_id UUID NOT NULL,
    position VARCHAR(255) NOT NULL,
    PRIMARY KEY (profile_id, position),
    CONSTRAINT fk_game_profile_positions_profile FOREIGN KEY (profile_id)
        REFERENCES game_profiles (id) ON DELETE RESTRICT
);

CREATE TABLE matches (
    id UUID PRIMARY KEY,
    game VARCHAR(255) NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result_score_a INTEGER,
    result_score_b INTEGER,
    result_memo VARCHAR(2000),
    version BIGINT NOT NULL
);

CREATE TABLE match_participants (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    score INTEGER NOT NULL,
    assigned_position VARCHAR(255) NOT NULL,
    team VARCHAR(255) NOT NULL,
    CONSTRAINT uk_match_participants_match_participant UNIQUE (match_id, participant_id),
    CONSTRAINT fk_match_participants_match FOREIGN KEY (match_id)
        REFERENCES matches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_match_participants_participant FOREIGN KEY (participant_id)
        REFERENCES participants (id) ON DELETE RESTRICT
);

CREATE INDEX idx_match_participants_participant ON match_participants (participant_id);
