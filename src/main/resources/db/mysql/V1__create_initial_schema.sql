CREATE TABLE participants (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    deleted_at DATETIME(6),
    version BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE game_profiles (
    id BINARY(16) PRIMARY KEY,
    participant_id BINARY(16) NOT NULL,
    game VARCHAR(255) NOT NULL,
    account_id VARCHAR(255),
    base_score INTEGER NOT NULL,
    manual_score INTEGER,
    version BIGINT NOT NULL,
    CONSTRAINT uk_game_profiles_participant_game UNIQUE (participant_id, game),
    CONSTRAINT fk_game_profiles_participant FOREIGN KEY (participant_id)
        REFERENCES participants (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE game_profile_positions (
    profile_id BINARY(16) NOT NULL,
    position VARCHAR(255) NOT NULL,
    PRIMARY KEY (profile_id, position),
    CONSTRAINT fk_game_profile_positions_profile FOREIGN KEY (profile_id)
        REFERENCES game_profiles (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE matches (
    id BINARY(16) PRIMARY KEY,
    game VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    result_score_a INTEGER,
    result_score_b INTEGER,
    result_memo VARCHAR(2000),
    version BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE match_participants (
    id BINARY(16) PRIMARY KEY,
    match_id BINARY(16) NOT NULL,
    participant_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    score INTEGER NOT NULL,
    assigned_position VARCHAR(255) NOT NULL,
    team VARCHAR(255) NOT NULL,
    INDEX idx_match_participants_participant (participant_id),
    CONSTRAINT uk_match_participants_match_participant UNIQUE (match_id, participant_id),
    CONSTRAINT fk_match_participants_match FOREIGN KEY (match_id)
        REFERENCES matches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_match_participants_participant FOREIGN KEY (participant_id)
        REFERENCES participants (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
