const express = require('express');
const { pool, withTransaction } = require('../db');
const { HttpError, asyncHandler, toInt, likePattern } = require('../utils/http');
const { authenticate, adminOnly } = require('../middleware/auth');
const { photoUpload, removeFile } = require('../middleware/upload');
const {
  todayStr, validateDates, calcTotal, calcLateFee, decorateRental,
} = require('../utils/rental');

const router = express.Router();
router.use(authenticate);

const SELECT = `SELECT r.id, r.customer_id, c.name AS customer_name, c.phone AS customer_phone,
                       r.equipment_id, e.name AS equipment_name, e.daily_price,
                       e.photo_path AS equipment_photo,
                       r.processed_by, u.name AS processed_by_name,
                       r.quantity, r.rent_date, r.due_date, r.return_date,
                       r.total_price, r.late_fee, r.status, r.return_photo_path
                  FROM rentals r
                  JOIN customers c ON c.id = r.customer_id
                  JOIN equipments e ON e.id = r.equipment_id
             LEFT JOIN users u ON u.id = r.processed_by`;

const STATUSES = ['dipesan', 'disewa', 'dikembalikan'];

/** Mengambil sewa; penyewa hanya boleh melihat miliknya sendiri. */
async function findOr404(id, user, conn = pool) {
  const [rows] = await conn.query(`${SELECT} WHERE r.id = ?`, [id]);
  const row = rows[0];
  if (!row) throw new HttpError(404, 'Penyewaan tidak ditemukan');
  if (user && user.role !== 'admin') {
    const [own] = await conn.query('SELECT 1 FROM customers WHERE id = ? AND user_id = ?', [row.customer_id, user.id]);
    if (!own.length) throw new HttpError(403, 'Anda hanya dapat melihat penyewaan milik Anda sendiri');
  }
  return decorateRental(row);
}

/** Validasi input form sewa (FR-13) dan hitung biaya (FR-09). */
async function parseRental(req) {
  const b = req.body;
  const equipmentId = toInt(b.equipment_id);
  const quantity = toInt(b.quantity);
  if (equipmentId === null || equipmentId <= 0) throw new HttpError(400, 'Alat wajib dipilih');
  if (quantity === null || quantity < 1) throw new HttpError(400, 'Jumlah minimal 1');
  validateDates(b.rent_date, b.due_date);

  // Admin memilih pelanggan; penyewa otomatis untuk dirinya sendiri
  let customerId;
  if (req.user.role === 'admin') {
    customerId = toInt(b.customer_id);
    if (customerId === null || customerId <= 0) throw new HttpError(400, 'Pelanggan wajib dipilih');
    const [c] = await pool.query('SELECT id FROM customers WHERE id = ?', [customerId]);
    if (!c.length) throw new HttpError(400, 'Pelanggan tidak ditemukan');
  } else {
    const [c] = await pool.query('SELECT id FROM customers WHERE user_id = ?', [req.user.id]);
    if (c.length) {
      customerId = c[0].id;
    } else {
      const [u] = await pool.query('SELECT name FROM users WHERE id = ?', [req.user.id]);
      const [ins] = await pool.query('INSERT INTO customers (user_id, name, phone) VALUES (?, ?, ?)', [
        req.user.id, u[0] ? u[0].name : 'Penyewa', '-',
      ]);
      customerId = ins.insertId;
    }
  }

  const [eq] = await pool.query('SELECT id, name, daily_price, stock FROM equipments WHERE id = ?', [equipmentId]);
  if (!eq.length) throw new HttpError(400, 'Alat tidak ditemukan');
  if (quantity > eq[0].stock) {
    throw new HttpError(400, `Stok ${eq[0].name} tidak cukup (tersedia ${eq[0].stock})`);
  }

  return {
    customerId, equipmentId, quantity,
    rentDate: b.rent_date, dueDate: b.due_date,
    total: calcTotal(b.rent_date, b.due_date, eq[0].daily_price, quantity),
  };
}

// Daftar + cari (nama pelanggan / nama alat) + filter status (termasuk "terlambat")
router.get('/', asyncHandler(async (req, res) => {
  const where = [];
  const params = [];

  if (req.user.role !== 'admin') {
    where.push('c.user_id = ?');
    params.push(req.user.id);
  }
  const search = String(req.query.search || '').trim();
  if (search) {
    where.push('(c.name LIKE ? OR e.name LIKE ?)');
    params.push(likePattern(search), likePattern(search));
  }
  const status = String(req.query.status || '').trim().toLowerCase();
  if (status === 'terlambat') {
    where.push("r.status = 'disewa' AND r.due_date < ?");
    params.push(todayStr());
  } else if (status) {
    if (!STATUSES.includes(status)) throw new HttpError(400, 'Status tidak valid');
    where.push('r.status = ?');
    params.push(status);
  }

  const sql = `${SELECT}${where.length ? ' WHERE ' + where.join(' AND ') : ''} ORDER BY r.id DESC`;
  const [rows] = await pool.query(sql, params);
  res.json(rows.map(decorateRental));
}));

router.get('/:id', asyncHandler(async (req, res) => {
  res.json(await findOr404(toInt(req.params.id), req.user));
}));

// Buat penyewaan (Admin & Penyewa). Status awal selalu "dipesan".
router.post('/', asyncHandler(async (req, res) => {
  const d = await parseRental(req);
  const processedBy = req.user.role === 'admin' ? req.user.id : null;
  const [r] = await pool.query(
    `INSERT INTO rentals (customer_id, equipment_id, processed_by, quantity, rent_date, due_date, total_price, status)
     VALUES (?, ?, ?, ?, ?, ?, ?, 'dipesan')`,
    [d.customerId, d.equipmentId, processedBy, d.quantity, d.rentDate, d.dueDate, d.total]
  );
  res.status(201).json(await findOr404(r.insertId, req.user));
}));

// Ubah data penyewaan (Admin) - hanya selama masih "dipesan" agar stok tetap konsisten
router.put('/:id', adminOnly, asyncHandler(async (req, res) => {
  const id = toInt(req.params.id);
  const current = await findOr404(id, req.user);
  if (current.status !== 'dipesan') {
    throw new HttpError(400, 'Hanya penyewaan berstatus Dipesan yang dapat diubah');
  }
  const d = await parseRental(req);
  await pool.query(
    `UPDATE rentals SET customer_id = ?, equipment_id = ?, quantity = ?, rent_date = ?, due_date = ?,
            total_price = ? WHERE id = ?`,
    [d.customerId, d.equipmentId, d.quantity, d.rentDate, d.dueDate, d.total, id]
  );
  res.json(await findOr404(id, req.user));
}));

// FR-10, FR-11, FR-12: ubah status -> stok otomatis + hitung denda
router.patch('/:id/status', adminOnly, asyncHandler(async (req, res) => {
  const id = toInt(req.params.id);
  const next = String(req.body.status || '').toLowerCase();
  if (!['disewa', 'dikembalikan'].includes(next)) {
    throw new HttpError(400, 'Status harus "disewa" atau "dikembalikan"');
  }

  await withTransaction(async (conn) => {
    const [rows] = await conn.query('SELECT * FROM rentals WHERE id = ? FOR UPDATE', [id]);
    const rental = rows[0];
    if (!rental) throw new HttpError(404, 'Penyewaan tidak ditemukan');

    const [eqRows] = await conn.query('SELECT * FROM equipments WHERE id = ? FOR UPDATE', [rental.equipment_id]);
    const equipment = eqRows[0];

    if (next === 'disewa') {
      if (rental.status !== 'dipesan') throw new HttpError(400, 'Hanya penyewaan berstatus Dipesan yang dapat disewakan');
      if (rental.quantity > equipment.stock) {
        throw new HttpError(400, `Stok ${equipment.name} tidak cukup (tersedia ${equipment.stock})`);
      }
      await conn.query('UPDATE equipments SET stock = stock - ? WHERE id = ?', [rental.quantity, equipment.id]);
      await conn.query("UPDATE rentals SET status = 'disewa', processed_by = ? WHERE id = ?", [req.user.id, id]);
    } else {
      if (rental.status !== 'disewa') throw new HttpError(400, 'Hanya penyewaan berstatus Disewa yang dapat dikembalikan');
      const returnDate = todayStr();
      const lateFee = calcLateFee(rental.due_date, returnDate, equipment.daily_price, rental.quantity);
      await conn.query('UPDATE equipments SET stock = stock + ? WHERE id = ?', [rental.quantity, equipment.id]);
      await conn.query(
        "UPDATE rentals SET status = 'dikembalikan', return_date = ?, late_fee = ?, processed_by = ? WHERE id = ?",
        [returnDate, lateFee, req.user.id, id]
      );
    }
  });

  res.json(await findOr404(id, req.user));
}));

// Unggah foto kondisi saat dikembalikan (Admin)
router.post('/:id/return-photo', adminOnly, asyncHandler(async (req, res, next) => {
  req.rental = await findOr404(toInt(req.params.id), req.user);
  next();
}), photoUpload('returns'), asyncHandler(async (req, res) => {
  const photoPath = `uploads/returns/${req.file.filename}`;
  await pool.query('UPDATE rentals SET return_photo_path = ? WHERE id = ?', [photoPath, req.rental.id]);
  removeFile(req.rental.return_photo_path);
  res.json(await findOr404(req.rental.id, req.user));
}));

// Hapus penyewaan (Admin). Bila masih "disewa", stok dikembalikan lebih dulu.
router.delete('/:id', adminOnly, asyncHandler(async (req, res) => {
  const id = toInt(req.params.id);
  let photo = null;
  await withTransaction(async (conn) => {
    const [rows] = await conn.query('SELECT * FROM rentals WHERE id = ? FOR UPDATE', [id]);
    const rental = rows[0];
    if (!rental) throw new HttpError(404, 'Penyewaan tidak ditemukan');
    if (rental.status === 'disewa') {
      await conn.query('UPDATE equipments SET stock = stock + ? WHERE id = ?', [rental.quantity, rental.equipment_id]);
    }
    photo = rental.return_photo_path;
    await conn.query('DELETE FROM rentals WHERE id = ?', [id]);
  });
  removeFile(photo);
  res.json({ message: 'Penyewaan dihapus' });
}));

module.exports = router;

