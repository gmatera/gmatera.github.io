-- Un'istanza MySQL con due database separati, ognuno con il suo utente:
-- account non può leggere i dati di receipts e viceversa.
CREATE DATABASE IF NOT EXISTS account_db;
CREATE DATABASE IF NOT EXISTS receipts_db;

CREATE USER IF NOT EXISTS 'account'@'%' IDENTIFIED BY 'account';
GRANT ALL PRIVILEGES ON account_db.* TO 'account'@'%';

CREATE USER IF NOT EXISTS 'receipts'@'%' IDENTIFIED BY 'receipts';
GRANT ALL PRIVILEGES ON receipts_db.* TO 'receipts'@'%';

FLUSH PRIVILEGES;
