-- Chạy sau registration.sql và gameplay.sql. Có thể chạy lại.
USE drawguess_db;
CREATE TABLE IF NOT EXISTS game_match (
 id CHAR(36) PRIMARY KEY, room_id INT NOT NULL, category_name VARCHAR(100) NOT NULL,
 started_at BIGINT NOT NULL, ended_at BIGINT NULL, status VARCHAR(20) NOT NULL
);
CREATE TABLE IF NOT EXISTS match_player (
 match_id CHAR(36) NOT NULL, user_id INT NOT NULL, username VARCHAR(50) NOT NULL,
 score INT NOT NULL, player_rank INT NOT NULL, average_seconds DOUBLE NULL, online BOOLEAN NOT NULL,
 PRIMARY KEY(match_id,user_id), INDEX idx_player_history(user_id,match_id),
 FOREIGN KEY(match_id) REFERENCES game_match(id)
);
CREATE TABLE IF NOT EXISTS match_round (
 match_id CHAR(36) NOT NULL, round_index INT NOT NULL, drawer_id INT NOT NULL,
 drawer_name VARCHAR(50) NOT NULL, word_content VARCHAR(100) NOT NULL, image_path VARCHAR(1024) NULL,
 started_at BIGINT NULL, ended_at BIGINT NULL, PRIMARY KEY(match_id,round_index),
 FOREIGN KEY(match_id) REFERENCES game_match(id)
);
CREATE TABLE IF NOT EXISTS round_guess (
 match_id CHAR(36) NOT NULL, round_index INT NOT NULL, guess_index INT NOT NULL,
 user_id INT NOT NULL, username VARCHAR(50) NOT NULL, answer VARCHAR(100) NOT NULL,
 correct BOOLEAN NOT NULL, guessed_at BIGINT NOT NULL, elapsed_seconds DOUBLE NOT NULL,
 PRIMARY KEY(match_id,round_index,guess_index),
 FOREIGN KEY(match_id,round_index) REFERENCES match_round(match_id,round_index)
);
