// Uji unit logika biaya sewa & denda (tanpa database): npm test
const assert = require('assert');
const { rentalDays, calcTotal, calcLateFee, lateDays, validateDates, decorateRental, todayStr } = require('../src/utils/rental');

let passed = 0;
const test = (name, fn) => {
  try { fn(); passed++; console.log(`PASS  ${name}`); }
  catch (e) { console.error(`FAIL  ${name}\n      ${e.message}`); process.exitCode = 1; }
};

test('FR-09: hari sewa = selisih hari, minimal 1', () => {
  assert.strictEqual(rentalDays('2026-01-01', '2026-01-04'), 3);
  assert.strictEqual(rentalDays('2026-01-01', '2026-01-01'), 1);
  assert.strictEqual(rentalDays('2026-02-27', '2026-03-02'), 3); // lintas bulan (2026 bukan kabisat)
});

test('FR-09: total = hari x harga x jumlah', () => {
  assert.strictEqual(calcTotal('2026-01-01', '2026-01-04', 50000, 2), 300000);
});

test('FR-11: denda per hari = 10% harga sewa per hari', () => {
  assert.strictEqual(calcLateFee('2026-01-04', '2026-01-04', 50000, 1), 0); // tepat waktu
  assert.strictEqual(calcLateFee('2026-01-04', '2026-01-03', 50000, 1), 0); // lebih awal
  assert.strictEqual(calcLateFee('2026-01-04', '2026-01-07', 50000, 1), 15000); // 3 hari x 5.000
  assert.strictEqual(calcLateFee('2026-01-04', '2026-01-06', 50000, 2), 10000 * 2); // x jumlah alat
});

test('lateDays tidak negatif', () => {
  assert.strictEqual(lateDays('2026-01-04', '2026-01-01'), 0);
  assert.strictEqual(lateDays('2026-01-04', '2026-01-10'), 6);
});

test('FR-13: validasi tanggal', () => {
  assert.throws(() => validateDates('2026-01-05', '2026-01-04'), /tidak boleh sebelum/);
  assert.throws(() => validateDates('2026-13-01', '2026-01-04'), /bukan tanggal yang valid/);
  assert.throws(() => validateDates('01-01-2026', '2026-01-04'), /YYYY-MM-DD/);
  assert.doesNotThrow(() => validateDates('2026-01-04', '2026-01-04'));
});

test('FR-12: penanda Terlambat otomatis hanya untuk status disewa lewat jatuh tempo', () => {
  const base = { rent_date: '2020-01-01', due_date: '2020-01-03', daily_price: 10000, quantity: 1, late_fee: 0, return_date: null };
  assert.strictEqual(decorateRental({ ...base, status: 'disewa' }).is_late, true);
  assert.strictEqual(decorateRental({ ...base, status: 'dipesan' }).is_late, false);
  assert.strictEqual(decorateRental({ ...base, status: 'dikembalikan', return_date: '2020-01-05' }).is_late, false);
  const future = { ...base, due_date: '2999-01-01', status: 'disewa' };
  assert.strictEqual(decorateRental(future).is_late, false);
  assert.match(todayStr(), /^\d{4}-\d{2}-\d{2}$/);
});

console.log(`\n${passed} tes lulus`);
