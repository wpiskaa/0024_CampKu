/**
 * Inisialisasi database: membuat database + tabel dari sql/schema.sql,
 * lalu mengisi akun admin dan data contoh (hanya jika masih kosong).
 *   npm run db:init
 */
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const bcrypt = require('bcrypt');
const mysql = require('mysql2/promise');

async function main() {
  const conn = await mysql.createConnection({
    host: process.env.DB_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    multipleStatements: true,
  });

  const dbName = process.env.DB_NAME || 'campku';
  let schema = fs.readFileSync(path.join(__dirname, '..', 'sql', 'schema.sql'), 'utf8');
  schema = schema.replace(/campku/g, dbName);
  await conn.query(schema);
  // Pastikan kolom pickup_photo_path ada jika tabel lama sudah telanjur dibuat
  const [cols] = await conn.query("SHOW COLUMNS FROM rentals LIKE 'pickup_photo_path'");
  if (!cols.length) {
    await conn.query('ALTER TABLE rentals ADD COLUMN pickup_photo_path VARCHAR(255) NULL AFTER status');
  }
  console.log(`Skema database "${dbName}" siap.`);

  const adminEmail = (process.env.ADMIN_EMAIL || 'admin@campku.com').toLowerCase();
  const [admins] = await conn.query('SELECT id FROM users WHERE email = ?', [adminEmail]);
  if (!admins.length) {
    const hash = await bcrypt.hash(process.env.ADMIN_PASSWORD || 'admin123', 10);
    await conn.query("INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, 'admin')", [
      process.env.ADMIN_NAME || 'Administrator', adminEmail, hash,
    ]);
    console.log(`Admin dibuat: ${adminEmail}`);
  } else {
    console.log('Admin sudah ada, dilewati.');
  }

  const [[{ n }]] = await conn.query('SELECT COUNT(*) AS n FROM categories');
  const seedPhotos = {
    'Tenda Dome Kapasitas 4': 'uploads/equipments/seed-tenda-4.jpg',
    'Tenda Dome Kapasitas 2': 'uploads/equipments/seed-tenda-2.jpg',
    'Carrier 60 Liter': 'uploads/equipments/seed-carrier-60l.jpg',
    'Sleeping Bag Polar': 'uploads/equipments/seed-sleeping-bag.jpg',
    'Kompor Portable Gas': 'uploads/equipments/seed-kompor.jpg',
    'Headlamp LED': 'uploads/equipments/seed-headlamp.jpg',
  };

  if (n === 0) {
    const cats = ['Tenda', 'Carrier', 'Sleeping Bag', 'Kompor', 'Aksesoris'];
    for (const c of cats) await conn.query('INSERT INTO categories (name) VALUES (?)', [c]);

    const [rows] = await conn.query('SELECT id, name FROM categories');
    const id = Object.fromEntries(rows.map((r) => [r.name, r.id]));
    const items = [
      [id['Tenda'], 'Tenda Dome Kapasitas 4', 60000, 5, 'Tenda dome 4 orang, double layer, tahan hujan.', seedPhotos['Tenda Dome Kapasitas 4']],
      [id['Tenda'], 'Tenda Dome Kapasitas 2', 40000, 6, 'Tenda ringan untuk 2 orang, cocok untuk pendakian.', seedPhotos['Tenda Dome Kapasitas 2']],
      [id['Carrier'], 'Carrier 60 Liter', 35000, 8, 'Carrier 60L dengan rain cover dan back system nyaman.', seedPhotos['Carrier 60 Liter']],
      [id['Sleeping Bag'], 'Sleeping Bag Polar', 15000, 10, 'Sleeping bag hangat bahan polar.', seedPhotos['Sleeping Bag Polar']],
      [id['Kompor'], 'Kompor Portable Gas', 10000, 7, 'Kompor lipat portable, hemat gas.', seedPhotos['Kompor Portable Gas']],
      [id['Aksesoris'], 'Headlamp LED', 8000, 12, 'Headlamp LED terang dengan 3 mode cahaya.', seedPhotos['Headlamp LED']],
    ];
    for (const it of items) {
      await conn.query(
        'INSERT INTO equipments (category_id, name, daily_price, stock, description, photo_path) VALUES (?, ?, ?, ?, ?, ?)', it
      );
    }
    console.log('Data contoh kategori dan alat ditambahkan.');
  } else {
    for (const [name, photoPath] of Object.entries(seedPhotos)) {
      await conn.query('UPDATE equipments SET photo_path = ? WHERE name = ? AND photo_path IS NULL', [photoPath, name]);
    }
  }

  await conn.end();
  console.log('Selesai.');
}

main().catch((err) => {
  console.error('db:init gagal:', err.message);
  process.exit(1);
});

