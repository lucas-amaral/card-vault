CREATE TABLE IF NOT EXISTS cards (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    external_id     VARCHAR(36)  NOT NULL UNIQUE,
    card_number_enc TEXT         NOT NULL COMMENT 'AES-256 encrypted card number',
    card_hash       VARCHAR(64)  NOT NULL UNIQUE COMMENT 'SHA-256 hash for fast lookup',
    batch_name      VARCHAR(100) NULL COMMENT 'Batch identifier from TXT file',
    created_by      BIGINT       NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_card_external_id (external_id),
    INDEX idx_card_hash (card_hash),
    INDEX idx_batch_name (batch_name),
    CONSTRAINT fk_cards_user FOREIGN KEY (created_by) REFERENCES users (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
