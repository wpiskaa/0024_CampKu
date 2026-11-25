const { HttpError } = require('./http');

const MS_PER_DAY = 24 * 60 * 60 * 1000;
const LATE_FEE_RATE = 1.0; // Denda keterlambatan: dihitung 1 hari sewa penuh per hari terlambat

/** Tanggal hari ini (zona waktu server) dalam format YYYY-MM-DD. */
function todayStr() {
  const d = new Date();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

/** Memvalidasi string tanggal YYYY-MM-DD dan mengembalikan nilai UTC (ms). */
function parseDate(value, label) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new HttpError(400, `${label} harus berformat YYYY-MM-DD`);
  }
  const [y, m, d] = value.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== m - 1 || date.getUTCDate() !== d) {
    throw new HttpError(400, `${label} bukan tanggal yang valid`);
  }
  return date.getTime();
}

/** Selisih hari antara dua tanggal YYYY-MM-DD (b - a). */
function diffDays(a, b) {
  return Math.round((parseDate(b, 'tanggal') - parseDate(a, 'tanggal')) / MS_PER_DAY);
}

/**
 * FR-09: jumlah hari sewa. Sewa dan kembali di hari yang sama dihitung 1 hari.
 */
function rentalDays(rentDate, dueDate) {
  return Math.max(1, diffDays(rentDate, dueDate));
}

/** FR-09: total = jumlah hari x harga sewa per hari x jumlah alat. */
function calcTotal(rentDate, dueDate, dailyPrice, quantity) {
  return rentalDays(rentDate, dueDate) * dailyPrice * quantity;
}

/** Jumlah hari terlambat (0 bila belum lewat jatuh tempo). */
function lateDays(dueDate, actualDate) {
  return Math.max(0, diffDays(dueDate, actualDate));
}

/** FR-11: denda = hari terlambat x 10% x harga per hari x jumlah alat. */
function calcLateFee(dueDate, actualDate, dailyPrice, quantity) {
  return Math.round(lateDays(dueDate, actualDate) * LATE_FEE_RATE * dailyPrice * quantity);
}

/** Validasi tanggal sewa & kembali (FR-13). */
function validateDates(rentDate, dueDate) {
  const a = parseDate(rentDate, 'Tanggal sewa');
  const b = parseDate(dueDate, 'Tanggal kembali');
  if (b < a) throw new HttpError(400, 'Tanggal kembali tidak boleh sebelum tanggal sewa');
}

/** Menambah sejumlah hari pada tanggal YYYY-MM-DD. */
function addDaysStr(dateStr, daysToAdd) {
  const ms = parseDate(dateStr, 'Tanggal') + daysToAdd * MS_PER_DAY;
  const d = new Date(ms);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * Melengkapi baris sewa dengan info turunan:
 *  - is_late    : FR-12, status Terlambat otomatis (sedang disewa & lewat jatuh tempo)
 *  - late_days  : jumlah hari terlambat
 *  - late_fee   : untuk sewa aktif yang terlambat dihitung berjalan (estimasi sampai hari ini)
 *  - days       : durasi sewa
 *  - bonus_days : jumlah hari bonus gratis dari promo Admin (sewa >= 2 hari dapat +1 hari)
 */
function decorateRental(row) {
  const today = todayStr();
  const out = { ...row };
  out.days = rentalDays(row.rent_date, row.due_date);
  out.bonus_days = Number(row.bonus_days || 0);
  out.discount_percent = Number(row.discount_percent || 0);
  const payableDays = Math.max(1, out.days - out.bonus_days);
  const baseTotal = payableDays * (row.daily_price || 0) * (row.quantity || 1);
  out.discount_amount = Math.max(0, Math.round((baseTotal * out.discount_percent) / 100));
  out.is_late = false;
  out.late_days = 0;

  if (row.status === 'disewa' && row.due_date < today) {
    out.is_late = true;
    out.late_days = lateDays(row.due_date, today);
    out.late_fee = calcLateFee(row.due_date, today, row.daily_price, row.quantity);
  } else if (row.status === 'dikembalikan' && row.return_date) {
    out.late_days = lateDays(row.due_date, row.return_date);
  }
  return out;
}

module.exports = {
  todayStr, parseDate, addDaysStr, rentalDays, calcTotal, lateDays, calcLateFee, validateDates, decorateRental,
};

