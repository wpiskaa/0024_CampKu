require('dotenv').config();
const app = require('./app');
const { pool } = require('./db');

const PORT = Number(process.env.PORT || 3000);

app.listen(PORT, '0.0.0.0', async () => {
  console.log(`CampKu API berjalan di http://localhost:${PORT}`);
  try {
    await pool.query('SELECT 1');
    console.log('Koneksi MySQL berhasil');
  } catch (err) {
    console.error(`Koneksi MySQL GAGAL: ${err.message}`);
    console.error('Pastikan MySQL (XAMPP) menyala dan jalankan "npm run db:init" sekali.');
  }
});

