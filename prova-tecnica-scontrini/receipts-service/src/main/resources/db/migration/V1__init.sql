CREATE TABLE receipt
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id     VARCHAR(36)   NOT NULL,
    cash_register_id    VARCHAR(50)   NOT NULL,
    receipt_number      INT           NOT NULL,
    type                VARCHAR(20)   NOT NULL,
    payment_type        VARCHAR(20)   NOT NULL,
    customer_id         VARCHAR(50)   NULL,
    total_amount        DECIMAL(12, 2) NOT NULL,
    vat_amount          DECIMAL(12, 2) NOT NULL,
    issued_at           DATETIME(6)   NOT NULL,
    business_date       DATE          NOT NULL,
    idempotency_key     VARCHAR(100)  NULL,
    original_receipt_id BIGINT        NULL,
    -- rete di sicurezza: anche in caso di bug non possono esistere due documenti con lo stesso numero
    CONSTRAINT uk_receipt_number UNIQUE (organization_id, cash_register_id, receipt_number),
    -- idempotenza: la stessa chiave non può emettere due documenti
    CONSTRAINT uk_receipt_idempotency UNIQUE (organization_id, idempotency_key),
    -- un documento può essere annullato una volta sola
    CONSTRAINT uk_receipt_original UNIQUE (original_receipt_id),
    CONSTRAINT fk_receipt_original FOREIGN KEY (original_receipt_id) REFERENCES receipt (id)
);

CREATE INDEX idx_receipt_day ON receipt (organization_id, cash_register_id, business_date);

CREATE TABLE receipt_line
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_id  BIGINT         NOT NULL,
    description VARCHAR(200)   NOT NULL,
    quantity    INT            NOT NULL,
    unit_price  DECIMAL(12, 2) NOT NULL,
    vat_rate    INT            NOT NULL,
    line_total  DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_line_receipt FOREIGN KEY (receipt_id) REFERENCES receipt (id)
);

-- ultimo numero usato per ogni punto cassa (vedi ReceiptNumberGenerator)
CREATE TABLE cash_register_counter
(
    organization_id  VARCHAR(36) NOT NULL,
    cash_register_id VARCHAR(50) NOT NULL,
    last_number      INT         NOT NULL,
    PRIMARY KEY (organization_id, cash_register_id)
);

CREATE TABLE daily_closure
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id  VARCHAR(36)    NOT NULL,
    cash_register_id VARCHAR(50)    NOT NULL,
    business_date    DATE           NOT NULL,
    receipt_count    INT            NOT NULL,
    total_amount     DECIMAL(12, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    attempts         INT            NOT NULL,
    next_attempt_at  DATETIME(6)    NOT NULL,
    last_error       VARCHAR(500)   NULL,
    protocol_number  VARCHAR(100)   NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    -- una sola chiusura per punto cassa e giorno
    CONSTRAINT uk_closure UNIQUE (organization_id, cash_register_id, business_date)
);

CREATE TABLE outbox_event
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic          VARCHAR(100) NOT NULL,
    message_key    VARCHAR(100) NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    payload        TEXT         NOT NULL,
    correlation_id VARCHAR(100) NULL,
    created_at     DATETIME(6)  NOT NULL,
    published_at   DATETIME(6)  NULL
);

CREATE INDEX idx_outbox_published ON outbox_event (published_at, id);
