/** Error dengan status HTTP, ditangkap oleh errorHandler. */
class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/** Membungkus handler async agar error diteruskan ke errorHandler. */
const asyncHandler = (fn) => (req, res, next) =>
  Promise.resolve(fn(req, res, next)).catch(next);

/** Mengubah nilai query/body menjadi integer positif, atau null jika tidak valid. */
function toInt(value) {
  if (value === undefined || value === null || value === '') return null;
  const n = Number(value);
  return Number.isInteger(n) ? n : null;
}

/** Memastikan string tidak kosong. */
function requireText(value, label, max = 255) {
  const text = typeof value === 'string' ? value.trim() : '';
  if (!text) throw new HttpError(400, `${label} wajib diisi`);
  if (text.length > max) throw new HttpError(400, `${label} maksimal ${max} karakter`);
  return text;
}

/** String opsional: kosong -> null. */
function optionalText(value, label, max = 255) {
  if (value === undefined || value === null) return null;
  const text = String(value).trim();
  if (!text) return null;
  if (text.length > max) throw new HttpError(400, `${label} maksimal ${max} karakter`);
  return text;
}

/** Escape karakter wildcard LIKE agar pencarian aman. */
function likePattern(search) {
  const s = String(search || '').trim().replace(/[\\%_]/g, (c) => '\\' + c);
  return `%${s}%`;
}

module.exports = { HttpError, asyncHandler, toInt, requireText, optionalText, likePattern };

