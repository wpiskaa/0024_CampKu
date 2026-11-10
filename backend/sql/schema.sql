-- ============================================================
-- CampKu - Skema database (MySQL 5.7+ / MariaDB 10.4+ / XAMPP)
-- Dijalankan otomatis oleh:  npm run db:init
-- ============================================================

CREATE DATABASE IF NOT EXISTS campku
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campku;

-- 1. Pengguna (akun login) -------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
  name          VARCHAR(100) NOT NULL,
  email         VARCHAR(150) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          ENUM('admin','penyewa') NOT NULL DEFAULT 'penyewa',
  refresh_token VARCHAR(255) NULL,            -- disimpan dalam bentuk hash SHA-256
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB;

-- 2. Kategori alat ----------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
  id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  description VARCHAR(255) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_categories_name (name)
) ENGINE=InnoDB;

-- 3. Alat camping -----------------------------------------------
CREATE TABLE IF NOT EXISTS equipments (
  id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
  category_id INT UNSIGNED NOT NULL,
  name        VARCHAR(150) NOT NULL,
  daily_price INT UNSIGNED NOT NULL,          -- harga sewa per hari (Rupiah)
  stock       INT UNSIGNED NOT NULL DEFAULT 0, -- stok tersedia saat ini
  description TEXT NULL,
  photo_path  VARCHAR(255) NULL,
  PRIMARY KEY (id),
  KEY idx_equipments_category (category_id),
  KEY idx_equipments_name (name),
  CONSTRAINT fk_equipments_category FOREIGN KEY (category_id)
    REFERENCES categories (id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 4. Pelanggan (penyewa) -----------------------------------------
CREATE TABLE IF NOT EXISTS customers (
  id        INT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id   INT UNSIGNED NULL,                -- terisi jika pelanggan punya akun
  name      VARCHAR(100) NOT NULL,
  phone     VARCHAR(25)  NOT NULL,
  address   VARCHAR(255) NULL,
  id_number VARCHAR(50)  NULL,                -- nomor identitas (KTP/SIM)
  PRIMARY KEY (id),
  UNIQUE KEY uq_customers_user (user_id),     -- 1 user punya 0..1 customer
  KEY idx_customers_name (name),
  CONSTRAINT fk_customers_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

-- 5. Penyewaan ---------------------------------------------------
CREATE TABLE IF NOT EXISTS rentals (
  id                INT UNSIGNED NOT NULL AUTO_INCREMENT,
  customer_id       INT UNSIGNED NOT NULL,
  equipment_id      INT UNSIGNED NOT NULL,
  processed_by      INT UNSIGNED NULL,        -- admin yang memproses
  quantity          INT UNSIGNED NOT NULL,
  rent_date         DATE NOT NULL,
  due_date          DATE NOT NULL,
  return_date       DATE NULL,
  total_price       INT UNSIGNED NOT NULL DEFAULT 0,
  late_fee          INT UNSIGNED NOT NULL DEFAULT 0,
  status            ENUM('dipesan','disewa','dikembalikan') NOT NULL DEFAULT 'dipesan',
  return_photo_path VARCHAR(255) NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rentals_status (status),
  KEY idx_rentals_customer (customer_id),
  KEY idx_rentals_equipment (equipment_id),
  CONSTRAINT fk_rentals_customer FOREIGN KEY (customer_id)
    REFERENCES customers (id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_rentals_equipment FOREIGN KEY (equipment_id)
    REFERENCES equipments (id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_rentals_processed_by FOREIGN KEY (processed_by)
    REFERENCES users (id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

