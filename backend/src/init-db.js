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
  if (n === 0) {
    const cats = ['Tenda', 'Carrier', 'Sleeping Bag', 'Kompor', 'Aksesoris'];
    for (const c of cats) await conn.query('INSERT INTO categories (name) VALUES (?)', [c]);

    const [rows] = await conn.query('SELECT id, name FROM categories');
    const id = Object.fromEntries(rows.map((r) => [r.name, r.id]));
    const items = [
      [id['Tenda'], 'Tenda Dome Kapasitas 4', 60000, 5, 'Tenda dome 4 orang, double layer, tahan hujan.'],
      [id['Tenda'], 'Tenda Dome Kapasitas 2', 40000, 6, 'Tenda ringan untuk 2 orang, cocok untuk pendakian.'],
      [id['Carrier'], 'Carrier 60 Liter', 35000, 8, 'Carrier 60L dengan rain cover dan back system nyaman.'],
      [id['Sleeping Bag'], 'Sleeping Bag Polar', 15000, 10, 'Sleeping bag hangat bahan polar.'],
      [id['Kompor'], 'Kompor Portable Gas', 10000, 7, 'Kompor lipat portable, hemat gas.'],
      [id['Aksesoris'], 'Headlamp LED', 8000, 12, 'Headlamp LED terang dengan 3 mode cahaya.'],
    ];
    for (const it of items) {
      await conn.query(
        'INSERT INTO equipments (category_id, name, daily_price, stock, description) VALUES (?, ?, ?, ?, ?)', it
      );
    }
    console.log('Data contoh kategori dan alat ditambahkan.');
  }

  await conn.end();
  console.log('Selesai.');
}

main().catch((err) => {
  console.error('db:init gagal:', err.message);
  process.exit(1);
});

