const express = require('express');
const { pool } = require('../db');
const { asyncHandler } = require('../utils/http');
const { authenticate } = require('../middleware/auth');
const { decorateRental } = require('../utils/rental');

const router = express.Router();
router.use(authenticate);

/**
 * Pusat Notifikasi dinamis (Admin & Penyewa):
 * - Admin   : pesanan baru menunggu validasi, sewa terlambat, stok menipis/habis, pengembalian terbaru.
 * - Penyewa : update status pesanan (dipesan, disewa, diskon/bonus, terlambat, dikembalikan).
 */
router.get('/', asyncHandler(async (req, res) => {
  const isAdmin = req.user.role === 'admin';
  const notifications = [];

  const rentalSql = `
    SELECT r.id, r.customer_id, c.name AS customer_name, c.phone AS customer_phone,
           r.equipment_id, e.name AS equipment_name, e.daily_price,
           r.quantity, r.rent_date, r.due_date, r.return_date,
           r.total_price, r.discount_percent, r.bonus_days, r.late_fee, r.status,
           r.pickup_photo_path, r.return_photo_path, r.created_at
      FROM rentals r
      JOIN customers c ON c.id = r.customer_id
      JOIN equipments e ON e.id = r.equipment_id
     ${isAdmin ? '' : 'WHERE c.user_id = ?'}
     ORDER BY r.id DESC
     LIMIT 30`;

  const [rentalRows] = isAdmin
    ? await pool.query(rentalSql)
    : await pool.query(rentalSql, [req.user.id]);

  const rentals = rentalRows.map(decorateRental);

  if (isAdmin) {
    // 1. Peringatan sewa terlambat (prioritas tertinggi)
    for (const r of rentals.filter((x) => x.is_late)) {
      notifications.push({
        id: `late_${r.id}`,
        type: 'late',
        title: `Terlambat ${r.late_days} Hari • ${r.customer_name}`,
        message: `${r.equipment_name} (${r.quantity} unit) melewati tanggal kembali ${r.due_date}.`,
        date: r.due_date,
        rental_id: r.id,
        equipment_id: null,
        urgent: true,
      });
    }

    // 2. Pesanan baru menunggu validasi & foto sebelum sewa
    for (const r of rentals.filter((x) => x.status === 'dipesan')) {
      notifications.push({
        id: `pending_${r.id}`,
        type: 'pending',
        title: `Menunggu Validasi • ${r.customer_name}`,
        message: `Pesanan ${r.equipment_name} (${r.quantity} unit, kode ${r.booking_code}) perlu verifikasi & foto sebelum disewa.`,
        date: r.rent_date,
        rental_id: r.id,
        equipment_id: null,
        urgent: true,
      });
    }

    // 3. Stok alat menipis atau habis
    const [lowStocks] = await pool.query(
      'SELECT id, name, stock FROM equipments WHERE stock <= 2 ORDER BY stock ASC, name ASC'
    );
    for (const eq of lowStocks) {
      notifications.push({
        id: `stock_${eq.id}`,
        type: 'stock',
        title: eq.stock === 0 ? `Stok Habis • ${eq.name}` : `Stok Menipis • ${eq.name}`,
        message: eq.stock === 0
          ? 'Stok saat ini 0 unit. Periksa pengembalian atau tambah stok.'
          : `Sisa stok hanya ${eq.stock} unit.`,
        date: '',
        rental_id: null,
        equipment_id: eq.id,
        urgent: eq.stock === 0,
      });
    }

    // 4. Pengembalian selesai terbaru (maks 5)
    for (const r of rentals.filter((x) => x.status === 'dikembalikan').slice(0, 5)) {
      notifications.push({
        id: `returned_${r.id}`,
        type: 'returned',
        title: `Selesai Dikembalikan • ${r.customer_name}`,
        message: `${r.equipment_name} telah dikembalikan pada ${r.return_date || r.due_date}.`,
        date: r.return_date || r.due_date,
        rental_id: r.id,
        equipment_id: null,
        urgent: false,
      });
    }
  } else {
    for (const r of rentals) {
      if (r.is_late) {
        notifications.push({
          id: `user_late_${r.id}`,
          type: 'late',
          title: `Lewat Jatuh Tempo ${r.late_days} Hari`,
          message: `${r.equipment_name} seharusnya kembali ${r.due_date}. Segera kembalikan untuk menghindari tambahan denda.`,
          date: r.due_date,
          rental_id: r.id,
          equipment_id: null,
          urgent: true,
        });
      } else if (r.status === 'dipesan') {
        notifications.push({
          id: `user_pending_${r.id}`,
          type: 'pending',
          title: `Pesanan Menunggu Validasi Admin`,
          message: `${r.equipment_name} (${r.booking_code}) tercatat. Tunjukkan QR Booking Pass saat pengambilan alat.`,
          date: r.rent_date,
          rental_id: r.id,
          equipment_id: null,
          urgent: false,
        });
      } else if (r.status === 'disewa') {
        const extras = [];
        if (r.discount_percent > 0) extras.push(`diskon ${r.discount_percent}%`);
        if (r.bonus_days > 0) extras.push(`bonus +${r.bonus_days} hari`);
        const extraNote = extras.length ? ` Termasuk ${extras.join(' & ')}.` : '';
        notifications.push({
          id: `user_rented_${r.id}`,
          type: 'rented',
          title: `Alat Sedang Disewa • ${r.equipment_name}`,
          message: `Jadwal pengembalian pada ${r.due_date}.${extraNote}`,
          date: r.rent_date,
          rental_id: r.id,
          equipment_id: null,
          urgent: false,
        });
      } else if (r.status === 'dikembalikan') {
        notifications.push({
          id: `user_returned_${r.id}`,
          type: 'returned',
          title: `Pengembalian Terverifikasi`,
          message: `${r.equipment_name} telah selesai dikembalikan. Terima kasih telah menyewa di CampKu!`,
          date: r.return_date || r.due_date,
          rental_id: r.id,
          equipment_id: null,
          urgent: false,
        });
      }
    }
  }

  res.json(notifications);
}));

module.exports = router;
