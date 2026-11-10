const bcrypt = require('bcrypt');
const express = require('express');
const { pool, withTransaction } = require('../db');
const { HttpError, asyncHandler, toInt, requireText, optionalText, likePattern } = require('../utils/http');
const { authenticate, adminOnly } = require('../middleware/auth');

const router = express.Router();
router.use(authenticate, adminOnly); // FR-08: khusus Admin

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const SELECT = `SELECT u.id, u.name, u.email, u.role, u.created_at, c.phone
                  FROM users u LEFT JOIN customers c ON c.user_id = u.id`;

async function findOr404(id) {
  const [rows] = await pool.query(`${SELECT} WHERE u.id = ?`, [id]);
  if (!rows[0]) throw new HttpError(404, 'Pengguna tidak ditemukan');
  return rows[0];
}

function parse(body, { passwordRequired }) {
  const email = requireText(body.email, 'Email', 150).toLowerCase();
  if (!EMAIL_RE.test(email)) throw new HttpError(400, 'Format email tidak valid');
  if (!['admin', 'penyewa'].includes(body.role)) throw new HttpError(400, 'Peran harus admin atau penyewa');

  const password = typeof body.password === 'string' ? body.password : '';
  if ((passwordRequired || password) && password.length < 6) {
    throw new HttpError(400, 'Password minimal 6 karakter');
  }
  const phone = optionalText(body.phone, 'Nomor HP', 25);
  if (phone && !/^[0-9+\-\s]{8,25}$/.test(phone)) throw new HttpError(400, 'Nomor HP tidak valid');

  return { name: requireText(body.name, 'Nama', 100), email, role: body.role, password, phone };
}

// Cari berdasarkan nama atau email
router.get('/', asyncHandler(async (req, res) => {
  const search = String(req.query.search || '').trim();
  const [rows] = search
    ? await pool.query(`${SELECT} WHERE u.name LIKE ? OR u.email LIKE ? ORDER BY u.name`, [likePattern(search), likePattern(search)])
    : await pool.query(`${SELECT} ORDER BY u.name`);
  res.json(rows);
}));

router.get('/:id', asyncHandler(async (req, res) => {
  res.json(await findOr404(toInt(req.params.id)));
}));

router.post('/', asyncHandler(async (req, res) => {
  const d = parse(req.body, { passwordRequired: true });
  const hash = await bcrypt.hash(d.password, 10);
  const id = await withTransaction(async (conn) => {
    const [r] = await conn.query(
      'INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)',
      [d.name, d.email, hash, d.role]
    );
    if (d.role === 'penyewa') {
      await conn.query('INSERT INTO customers (user_id, name, phone) VALUES (?, ?, ?)', [r.insertId, d.name, d.phone || '-']);
    }
    return r.insertId;
  });
  res.status(201).json(await findOr404(id));
}));

router.put('/:id', asyncHandler(async (req, res) => {
  const id = toInt(req.params.id);
  await findOr404(id);
  const d = parse(req.body, { passwordRequired: false });

  if (id === req.user.id && d.role !== 'admin') {
    throw new HttpError(400, 'Anda tidak dapat mengubah peran akun Anda sendiri');
  }

  await withTransaction(async (conn) => {
    if (d.password) {
      const hash = await bcrypt.hash(d.password, 10);
      // refresh_token dikosongkan agar semua sesi lama harus login ulang
      await conn.query(
        'UPDATE users SET name = ?, email = ?, role = ?, password_hash = ?, refresh_token = NULL WHERE id = ?',
        [d.name, d.email, d.role, hash, id]
      );
    } else {
      await conn.query('UPDATE users SET name = ?, email = ?, role = ? WHERE id = ?', [d.name, d.email, d.role, id]);
    }
    // sinkronkan data pelanggan yang tertaut (nama & HP)
    if (d.phone) {
      await conn.query('UPDATE customers SET name = ?, phone = ? WHERE user_id = ?', [d.name, d.phone, id]);
    } else {
      await conn.query('UPDATE customers SET name = ? WHERE user_id = ?', [d.name, id]);
    }
  });
  res.json(await findOr404(id));
}));

router.delete('/:id', asyncHandler(async (req, res) => {
  const id = toInt(req.params.id);
  await findOr404(id);
  if (id === req.user.id) throw new HttpError(400, 'Anda tidak dapat menghapus akun Anda sendiri');
  await pool.query('DELETE FROM users WHERE id = ?', [id]); // customers.user_id & rentals.processed_by -> NULL
  res.json({ message: 'Pengguna dihapus' });
}));

module.exports = router;

