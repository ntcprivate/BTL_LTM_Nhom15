-- Run registration.sql first. In the SAME MySQL session, set:
-- SET @admin_username = 'admin';
-- SET @admin_password = 'YOUR_PRIVATE_PASSWORD';
-- Then execute this script. No default admin password is provided.
USE drawguess_db;

SET @admin_username = COALESCE(@admin_username, 'admin');

-- Existing accounts are never promoted or given a new password by this script.
INSERT INTO user_account (username, password, role)
SELECT TRIM(@admin_username), @admin_password, 'ADMIN'
WHERE CHAR_LENGTH(TRIM(@admin_username)) BETWEEN 1 AND 50
  AND @admin_password IS NOT NULL
  AND CHAR_LENGTH(TRIM(@admin_password)) > 0
  AND CHAR_LENGTH(@admin_password) <= 255
  AND @admin_password <> 'YOUR_PRIVATE_PASSWORD'
  AND NOT EXISTS (
      SELECT 1 FROM user_account WHERE username = TRIM(@admin_username)
  );

-- Check this result: SUCCESS requires role ADMIN.
SELECT CASE
    WHEN a.role = 'ADMIN' THEN 'ADMIN_READY'
    WHEN a.id IS NOT NULL THEN 'USERNAME_ALREADY_EXISTS_AS_USER: choose another username'
    ELSE 'ADMIN_NOT_CREATED: set a valid username and private password, then retry'
END AS setup_result, a.id, a.username, a.role
FROM (SELECT 1) AS setup
LEFT JOIN user_account AS a ON a.username = TRIM(@admin_username);

SET @admin_password = NULL;
