-- Chạy thủ công trên MySQL trước khi thử đăng ký.
-- CREATE IF NOT EXISTS không thay đổi cấu trúc bảng đã có.
CREATE DATABASE IF NOT EXISTS drawguess_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE drawguess_db;

CREATE TABLE IF NOT EXISTS user_account (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_account_username UNIQUE (username)
);

-- Với bảng có sẵn: kiểm tra SHOW INDEX FROM user_account;
-- Nếu username chưa có UNIQUE, xử lý các tên trùng rồi thêm ràng buộc:
-- ALTER TABLE user_account ADD CONSTRAINT uq_user_account_username UNIQUE (username);
