CREATE TABLE IF NOT EXISTS audit_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id   VARCHAR(36)   NOT NULL,
    username     VARCHAR(100)  NULL,
    http_method  VARCHAR(10)   NOT NULL,
    endpoint     VARCHAR(255)  NOT NULL,
    status_code  INT           NOT NULL,
    request_body TEXT          NULL,
    response_body TEXT         NULL,
    ip_address   VARCHAR(45)   NULL,
    duration_ms  BIGINT        NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_username (username),
    INDEX idx_audit_created_at (created_at),
    INDEX idx_audit_request_id (request_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;