CREATE TABLE IF NOT EXISTS users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(100) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL DEFAULT 'ROLE_USER',
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Default admin user: admin / Admin@123
INSERT INTO users (username, password, role, enabled, created_at, updated_at)
VALUES ('admin', '$2a$12$x84lWYlrznNUTO1MoviRRenIrrkB0tToys.881fJhx3zNDObq7dkq', 'ROLE_ADMIN', TRUE, NOW(), NOW());
