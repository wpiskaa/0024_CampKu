const express = require('express');
const { pool } = require('../db');
const { HttpError, asyncHandler, toInt, requireText, optionalText, likePattern } = require('../utils/http');
const { authenticate, adminOnly } = require('../middleware/auth');

const router = express.Router();
router.use(authenticate, adminOnly); // seluruh endpoint pelanggan khusus Admin

const SELECT = 'SELECT id, user_id, name, phone, address, id_number FROM customers';

function parse(body) {
  const phone = requireText(body.phone, 'Nomor HP', 25);
  if (!/^[0-9+\-\s]{8,25}$/.test(phone)) throw new HttpError(400, 'Nomor HP tidak valid');
  return {
    name: requireText(body.name, 'Nama pelanggan', 100),
    phone,
    address: optionalText(body.address, 'Alamat', 255),
    id_number: optionalText(body.id_number, 'Nomor identitas', 50),
  };
}

async function findOr404(id) {
  const [rows] = await pool.query(`${SELECT} WHERE id = ?`, [id]);
  if (!rows[0]) throw new HttpError(404, 'Pelanggan tidak ditemukan');
  return rows[0];
}

// Cari berdasarkan nama atau HP
router.get('/', asyncHandler(async (req, res) => {
  const search = String(req.query.search || '').trim();
  const [rows] = search
    ? await pool.query(`${SELECT} WHERE name LIKE ? OR phone LIKE ? ORDER BY name`, [likePattern(search), likePattern(search)])
    : await pool.query(`${SELECT} ORDER BY name`);
  res.json(rows);
}));

router.get('/:id', asyncHandler(async (req, res) => {
  res.json(await findOr404(toInt(req.params.id)));
}));

router.post('/', asyncHandler(async (req, res) => {
  const d = parse(req.body);
  const [r] = await pool.query(
    'INSERT INTO customers (name, phone, address, id_number) VALUES (?, ?, ?, ?)',
    [d.name, d.phone, d.address, d.id_number]
  );
  res.status(201).json(await findOr404(r.insertId));
}));

router.put('/:id', asyncHandler(async (req, res) => {
  const id = toInt(req.params.id);
  await findOr404(id);
  const d = parse(req.body);
  await pool.query(
    'UPDATE customers SET name = ?, phone = ?, address = ?, id_number = ? WHERE id = ?',
    [d.name, d.phone, d.address, d.id_number, id]
  );
  res.json(await findOr404(id));
}));

router.delete('/:id', asyncHandler(async (req, res) => {
  const id = toInt(req.params.id);
  await findOr404(id);
  await pool.query('DELETE FROM customers WHERE id = ?', [id]); // ditolak FK jika punya riwayat sewa
  res.json({ message: 'Pelanggan dihapus' });
}));

module.exports = router;

