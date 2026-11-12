CREATE TABLE organization
(
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at DATETIME(6)  NOT NULL
);

CREATE TABLE enablement
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    product         VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    start_date      DATE        NOT NULL,
    end_date        DATE        NULL,
    updated_at      DATETIME(6) NOT NULL,
    -- una sola riga per coppia organizzazione/prodotto
    CONSTRAINT uk_enablement_org_product UNIQUE (organization_id, product),
    CONSTRAINT fk_enablement_org FOREIGN KEY (organization_id) REFERENCES organization (id)
);

-- outbox: gli eventi vengono scritti qui nella stessa transazione dei dati
-- e poi pubblicati su Kafka da OutboxPublisher
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
