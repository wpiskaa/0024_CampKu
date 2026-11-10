const crypto = require('crypto');
const bcrypt = require('bcrypt');
const express = require('express');
const { pool, withTransaction } = require('../db');
const { HttpError, asyncHandler, requireText, optionalText } = require('../utils/http');
const {
  signAccessToken, signRefreshToken, verifyRefreshToken, authenticate,
} = require('../middleware/auth');

const router = express.Router();
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const sha256 = (text) => crypto.createHash('sha256').update(text).digest('hex');

async function userWithPhone(id) {
  const [rows] = await pool.query(
    `SELECT u.id, u.name, u.email, u.role, u.created_at, c.phone
       FROM users u LEFT JOIN customers c ON c.user_id = u.id WHERE u.id = ?`,
    [id]
  );
  return rows[0] || null;
}

async function issueTokens(user) {
  const access_token = signAccessToken(user);
  const refresh_token = signRefreshToken(user);
  await pool.query('UPDATE users SET refresh_token = ? WHERE id = ?', [sha256(refresh_token), user.id]);
  return { access_token, refresh_token };
}

// FR-01 Register -------------------------------------------------
router.post('/register', asyncHandler(async (req, res) => {
  const name = requireText(req.body.name, 'Nama', 100);
  const email = requireText(req.body.email, 'Email', 150).toLowerCase();
  const phone = requireText(req.body.phone, 'Nomor HP', 25);
  const password = typeof req.body.password === 'string' ? req.body.password : '';

  if (!EMAIL_RE.test(email)) throw new HttpError(400, 'Format email tidak valid');
  if (!/^[0-9+\-\s]{8,25}$/.test(phone)) throw new HttpError(400, 'Nomor HP tidak valid');
  if (password.length < 6) throw new HttpError(400, 'Password minimal 6 karakter');

  const [dup] = await pool.query('SELECT id FROM users WHERE email = ?', [email]);
  if (dup.length) throw new HttpError(409, 'Email sudah terdaftar');

  const hash = await bcrypt.hash(password, 10);
  const userId = await withTransaction(async (conn) => {
    const [r] = await conn.query(
      "INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, 'penyewa')",
      [name, email, hash]
    );
    await conn.query('INSERT INTO customers (user_id, name, phone) VALUES (?, ?, ?)', [r.insertId, name, phone]);
    return r.insertId;
  });

  res.status(201).json({ message: 'Registrasi berhasil, silakan login', user: await userWithPhone(userId) });
}));

// FR-02 Login ----------------------------------------------------
router.post('/login', asyncHandler(async (req, res) => {
  const email = requireText(req.body.email, 'Email', 150).toLowerCase();
  const password = typeof req.body.password === 'string' ? req.body.password : '';
  if (!password) throw new HttpError(400, 'Password wajib diisi');

  const [rows] = await pool.query('SELECT * FROM users WHERE email = ?', [email]);
  const user = rows[0];
  // pesan sama untuk email/password salah agar tidak membocorkan keberadaan akun
  if (!user || !(await bcrypt.compare(password, user.password_hash))) {
    throw new HttpError(401, 'Email atau password salah');
  }

  const tokens = await issueTokens(user);
  res.json({ ...tokens, user: await userWithPhone(user.id) });
}));

// FR-03 Refresh token (rotasi) -----------------------------------
router.post('/refresh', asyncHandler(async (req, res) => {
  const token = optionalText(req.body.refresh_token, 'Refresh token', 1000);
  if (!token) throw new HttpError(400, 'Refresh token wajib dikirim');

  let payload;
  try {
    payload = verifyRefreshToken(token);
  } catch {
    throw new HttpError(401, 'Refresh token tidak valid atau kedaluwarsa');
  }

  const [rows] = await pool.query('SELECT * FROM users WHERE id = ?', [payload.sub]);
  const user = rows[0];
  if (!user || !user.refresh_token || user.refresh_token !== sha256(token)) {
    throw new HttpError(401, 'Refresh token sudah tidak berlaku, silakan login ulang');
  }

  res.json(await issueTokens(user));
}));

// Logout: hapus refresh token ------------------------------------
router.post('/logout', authenticate, asyncHandler(async (req, res) => {
  await pool.query('UPDATE users SET refresh_token = NULL WHERE id = ?', [req.user.id]);
  res.json({ message: 'Logout berhasil' });
}));

// Profil akun sendiri --------------------------------------------
router.get('/me', authenticate, asyncHandler(async (req, res) => {
  const user = await userWithPhone(req.user.id);
  if (!user) throw new HttpError(401, 'Akun tidak ditemukan');
  res.json(user);
}));

module.exports = router;

