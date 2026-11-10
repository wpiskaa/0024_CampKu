const express = require('express');
const { pool } = require('../db');
const { HttpError, asyncHandler, toInt, requireText, optionalText, likePattern } = require('../utils/http');
const { authenticate, adminOnly } = require('../middleware/auth');

const router = express.Router();
router.use(authenticate);

const SELECT = `SELECT c.id, c.name, c.description,
                       (SELECT COUNT(*) FROM equipments e WHERE e.category_id = c.id) AS equipment_count
                  FROM categories c`;

function parse(body) {
  return {
    name: requireText(body.name, 'Nama kategori', 100),
    description: optionalText(body.description, 'Deskripsi', 255),
  };
}

async function findOr404(id) {
  const [rows] = await pool.query(`${SELECT} WHERE c.id = ?`, [id]);
  if (!rows[0]) throw new HttpError(404, 'Kategori tidak ditemukan');
  return rows[0];
}

router.get('/', asyncHandler(async (req, res) => {
  const search = String(req.query.search || '').trim();
  const [rows] = search
    ? await pool.query(`${SELECT} WHERE c.name LIKE ? ORDER BY c.name`, [likePattern(search)])
    : await pool.query(`${SELECT} ORDER BY c.name`);
  res.json(rows);
}));

router.get('/:id', asyncHandler(async (req, res) => {
  res.json(await findOr404(toInt(req.params.id)));
}));

router.post('/', adminOnly, asyncHandler(async (req, res) => {
  const { name, description } = parse(req.body);
  const [r] = await pool.query('INSERT INTO categories (name, description) VALUES (?, ?)', [name, description]);
  res.status(201).json(await findOr404(r.insertId));
}));

router.put('/:id', adminOnly, asyncHandler(async (req, res) => {
  const id = toInt(req.params.id);
  await findOr404(id);
  const { name, description } = parse(req.body);
  await pool.query('UPDATE categories SET name = ?, description = ? WHERE id = ?', [name, description, id]);
  res.json(await findOr404(id));
}));

router.delete('/:id', adminOnly, asyncHandler(async (req, res) => {
  const id = toInt(req.params.id);
  await findOr404(id);
  await pool.query('DELETE FROM categories WHERE id = ?', [id]); // ditolak FK jika masih punya alat
  res.json({ message: 'Kategori dihapus' });
}));

module.exports = router;

