const express = require('express');
const { pool } = require('../db');
const { HttpError, asyncHandler, toInt, requireText, optionalText, likePattern } = require('../utils/http');
const { authenticate, adminOnly } = require('../middleware/auth');
const { photoUpload, removeFile } = require('../middleware/upload');

const router = express.Router();
router.use(authenticate);

const SELECT = `SELECT e.id, e.category_id, c.name AS category_name, e.name, e.daily_price,
                       e.stock, e.description, e.photo_path
                  FROM equipments e JOIN categories c ON c.id = e.category_id`;

/** Validasi FR-13: harga & stok tidak negatif, form tidak kosong. */
function parse(body) {
  const categoryId = toInt(body.category_id);
  const dailyPrice = toInt(body.daily_price);
  const stock = toInt(body.stock);
  if (categoryId === null || categoryId <= 0) throw new HttpError(400, 'Kategori wajib dipilih');
  if (dailyPrice === null || dailyPrice < 0) throw new HttpError(400, 'Harga sewa harus angka bulat tidak negatif');
  if (stock === null || stock < 0) throw new HttpError(400, 'Stok harus angka bulat tidak negatif');
  return {
    category_id: categoryId,
    name: requireText(body.name, 'Nama alat', 150),
    daily_price: dailyPrice,
    stock,
    description: optionalText(body.description, 'Deskripsi', 5000),
  };
}

async function findOr404(id) {
  const [rows] = await pool.query(`${SELECT} WHERE e.id = ?`, [id]);
  if (!rows[0]) throw new HttpError(404, 'Alat tidak ditemukan');
  return rows[0];
}

// Daftar + cari nama + filter kategori
router.get('/', asyncHandler(async (req, res) => {
  const where = [];
  const params = [];
  const search = String(req.query.search || '').trim();
  if (search) { where.push('e.name LIKE ?'); params.push(likePattern(search)); }
  const categoryId = toInt(req.query.category_id);
  if (categoryId) { where.push('e.category_id = ?'); params.push(categoryId); }

  const sql = `${SELECT}${where.length ? ' WHERE ' + where.join(' AND ') : ''} ORDER BY e.name`;
  const [rows] = await pool.query(sql, params);
  res.json(rows);
}));

// Alat populer (paling sering disewa) - untuk Beranda semua peran.
// Harus didaftarkan sebelum '/:id'.
router.get('/popular', asyncHandler(async (req, res) => {
  const [rows] = await pool.query(
    `SELECT e.id, e.category_id, c.name AS category_name, e.name, e.daily_price, e.stock,
            e.description, e.photo_path,
            COALESCE(SUM(r.quantity), 0) AS rent_count
       FROM equipments e
       JOIN categories c ON c.id = e.category_id
       LEFT JOIN rentals r ON r.equipment_id = e.id
      GROUP BY e.id, c.name
      ORDER BY rent_count DESC, e.name
      LIMIT 5`
  );
  res.json(rows.map((r) => ({ ...r, rent_count: Number(r.rent_count) })));
}));

router.get('/:id', asyncHandler(async (req, res) => {
  res.json(await findOr404(toInt(req.params.id)));
}));

router.post('/', adminOnly, asyncHandler(async (req, res) => {
  const d = parse(req.body);
  const [r] = await pool.query(
    'INSERT INTO equipments (category_id, name, daily_price, stock, description) VALUES (?, ?, ?, ?, ?)',
    [d.category_id, d.name, d.daily_price, d.stock, d.description]
  );
  res.status(201).json(await findOr404(r.insertId));
}));

router.put('/:id', adminOnly, asyncHandler(async (req, res) => {
  const id = toInt(req.params.id);
  await findOr404(id);
  const d = parse(req.body);
  await pool.query(
    'UPDATE equipments SET category_id = ?, name = ?, daily_price = ?, stock = ?, description = ? WHERE id = ?',
    [d.category_id, d.name, d.daily_price, d.stock, d.description, id]
  );
  res.json(await findOr404(id));
}));

router.delete('/:id', adminOnly, asyncHandler(async (req, res) => {
  const id = toInt(req.params.id);
  const equipment = await findOr404(id);
  await pool.query('DELETE FROM equipments WHERE id = ?', [id]); // ditolak FK jika punya riwayat sewa
  removeFile(equipment.photo_path);
  res.json({ message: 'Alat dihapus' });
}));

// Unggah foto alat (multipart, field "photo"). Validasi id dulu sebelum menyimpan file.
router.post('/:id/photo', adminOnly, asyncHandler(async (req, res, next) => {
  req.equipment = await findOr404(toInt(req.params.id));
  next();
}), photoUpload('equipments'), asyncHandler(async (req, res) => {
  const photoPath = `uploads/equipments/${req.file.filename}`;
  await pool.query('UPDATE equipments SET photo_path = ? WHERE id = ?', [photoPath, req.equipment.id]);
  removeFile(req.equipment.photo_path);
  res.json(await findOr404(req.equipment.id));
}));

module.exports = router;

