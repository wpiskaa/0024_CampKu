const express = require('express');
const { pool } = require('../db');
const { asyncHandler } = require('../utils/http');
const { authenticate, adminOnly } = require('../middleware/auth');
const { todayStr } = require('../utils/rental');

const router = express.Router();
router.use(authenticate, adminOnly);

// Ringkasan: alat tersedia, sewa aktif, terlambat (+ data pendukung)
router.get('/', asyncHandler(async (req, res) => {
  const today = todayStr();
  const [[eq]] = await pool.query(
    `SELECT COUNT(*) AS total_equipment,
            COALESCE(SUM(stock > 0), 0) AS available_equipment,
            COALESCE(SUM(stock), 0) AS available_stock
       FROM equipments`
  );
  const [[rent]] = await pool.query(
    `SELECT COALESCE(SUM(status = 'dipesan'), 0) AS pending_rentals,
            COALESCE(SUM(status = 'disewa'), 0) AS active_rentals,
            COALESCE(SUM(status = 'disewa' AND due_date < ?), 0) AS late_rentals,
            COALESCE(SUM(status = 'dikembalikan'), 0) AS returned_rentals
       FROM rentals`,
    [today]
  );
  const [[cust]] = await pool.query('SELECT COUNT(*) AS total_customers FROM customers');

  res.json({
    total_equipment: Number(eq.total_equipment),
    available_equipment: Number(eq.available_equipment),
    available_stock: Number(eq.available_stock),
    pending_rentals: Number(rent.pending_rentals),
    active_rentals: Number(rent.active_rentals),
    late_rentals: Number(rent.late_rentals),
    returned_rentals: Number(rent.returned_rentals),
    total_customers: Number(cust.total_customers),
  });
}));

module.exports = router;

