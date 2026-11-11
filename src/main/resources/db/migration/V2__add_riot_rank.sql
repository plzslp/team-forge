ALTER TABLE game_profiles ADD COLUMN riot_puuid VARCHAR(255);
ALTER TABLE game_profiles ADD COLUMN riot_tier VARCHAR(20);
ALTER TABLE game_profiles ADD COLUMN riot_division VARCHAR(5);
ALTER TABLE game_profiles ADD COLUMN riot_league_points INTEGER;
ALTER TABLE game_profiles ADD COLUMN riot_updated_at TIMESTAMP WITH TIME ZONE;
