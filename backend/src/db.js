require('dotenv').config();
const mysql = require('mysql2/promise');

// dateStrings: kolom DATE dikembalikan sebagai 'YYYY-MM-DD' (bukan objek Date)
const pool = mysql.createPool({
  host: process.env.DB_HOST || '127.0.0.1',
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_NAME || 'campku',
  waitForConnections: true,
  connectionLimit: 10,
  dateStrings: true,
  charset: 'utf8mb4',
});

/** Menjalankan fungsi di dalam transaksi; otomatis commit / rollback. */
async function withTransaction(fn) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const result = await fn(conn);
    await conn.commit();
    return result;
  } catch (err) {
    await conn.rollback();
    throw err;
  } finally {
    conn.release();
  }
}

module.exports = { pool, withTransaction };

