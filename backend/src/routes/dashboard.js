const express = require('express');
const { pool } = require('../db');
const { asyncHandler } = require('../utils/http');
const { authenticate, adminOnly } = require('../middleware/auth');
const { todayStr, decorateRental } = require('../utils/rental');

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

// Laporan Pendapatan & Grafik Statistik Bisnis 6 Bulan Terakhir (Khusus Admin)
router.get('/reports', asyncHandler(async (req, res) => {
  const [rows] = await pool.query(
    `SELECT r.id, r.equipment_id, e.name AS equipment_name, c.name AS category_name,
            e.daily_price, r.quantity, r.rent_date, r.due_date, r.return_date,
            r.total_price, r.discount_percent, r.bonus_days, r.late_fee, r.status
       FROM rentals r
       JOIN equipments e ON e.id = r.equipment_id
       JOIN categories c ON c.id = e.category_id
      ORDER BY r.rent_date DESC, r.id DESC`
  );

  const rentals = rows.map(decorateRental);
  let totalRevenue = 0;
  let completedRevenue = 0;
  let activeRevenue = 0;
  let pendingRevenue = 0;
  let totalLateFees = 0;
  let totalDiscounts = 0;

  const eqMap = new Map();
  const monthTotals = new Map();

  for (const r of rentals) {
    const rev = Number(r.total_price || 0);
    const fee = Number(r.late_fee || 0);
    const disc = Number(r.discount_amount || 0);
    totalRevenue += rev;
    totalLateFees += fee;
    totalDiscounts += disc;
    if (r.status === 'dikembalikan') completedRevenue += rev + fee;
    else if (r.status === 'disewa') activeRevenue += rev + fee;
    else pendingRevenue += rev;

    const ym = String(r.rent_date || '').slice(0, 7);
    if (ym.length === 7) {
      const m = monthTotals.get(ym) || { revenue: 0, late_fee: 0, count: 0 };
      m.revenue += rev;
      m.late_fee += fee;
      m.count += 1;
      monthTotals.set(ym, m);
    }

    const eqItem = eqMap.get(r.equipment_id) || {
      id: r.equipment_id,
      name: r.equipment_name,
      category_name: r.category_name,
      rent_count: 0,
      total_units: 0,
      total_revenue: 0,
    };
    eqItem.rent_count += 1;
    eqItem.total_units += Number(r.quantity || 0);
    eqItem.total_revenue += rev + fee;
    eqMap.set(r.equipment_id, eqItem);
  }

  // Susun 6 bulan terakhir (termasuk bulan yang belum ada transaksi agar grafik selalu rapi)
  const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];
  const now = new Date();
  const monthlyRevenue = [];
  for (let i = 5; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const ym = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    const label = `${monthNames[d.getMonth()]} ${String(d.getFullYear()).slice(-2)}`;
    const agg = monthTotals.get(ym) || { revenue: 0, late_fee: 0, count: 0 };
    monthlyRevenue.push({
      month: ym,
      label,
      revenue: agg.revenue,
      late_fee: agg.late_fee,
      total: agg.revenue + agg.late_fee,
      count: agg.count,
    });
  }

  const topEquipments = Array.from(eqMap.values())
    .sort((a, b) => b.total_revenue - a.total_revenue || b.rent_count - a.rent_count)
    .slice(0, 5);

  res.json({
    total_revenue: totalRevenue,
    total_late_fees: totalLateFees,
    grand_total: totalRevenue + totalLateFees,
    completed_revenue: completedRevenue,
    active_revenue: activeRevenue,
    pending_revenue: pendingRevenue,
    total_discounts: totalDiscounts,
    total_transactions: rentals.length,
    monthly_revenue: monthlyRevenue,
    top_equipments: topEquipments,
  });
}));

module.exports = router;

