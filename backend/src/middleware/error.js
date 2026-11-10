const { HttpError } = require('../utils/http');

function notFound(req, res, next) {
  next(new HttpError(404, `Endpoint ${req.method} ${req.originalUrl} tidak ditemukan`));
}

// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, next) {
  let status = err.status || 500;
  let message = err.message || 'Terjadi kesalahan pada server';

  if (err.code === 'ER_DUP_ENTRY') {
    status = 409;
    message = 'Data sudah ada (duplikat)';
    if (/email/i.test(err.sqlMessage || '')) message = 'Email sudah terdaftar';
    else if (/categories/i.test(err.sqlMessage || '')) message = 'Nama kategori sudah ada';
  } else if (err.code === 'ER_ROW_IS_REFERENCED_2') {
    status = 409;
    message = 'Data tidak dapat dihapus karena masih dipakai oleh data lain';
  } else if (err.code === 'ER_NO_REFERENCED_ROW_2') {
    status = 400;
    message = 'Data relasi (kategori/pelanggan/alat) tidak ditemukan';
  } else if (err.name === 'MulterError') {
    status = 400;
    message = err.code === 'LIMIT_FILE_SIZE' ? 'Ukuran foto maksimal 5 MB' : `Unggah gagal: ${err.message}`;
  } else if (err.type === 'entity.parse.failed') {
    status = 400;
    message = 'Format JSON tidak valid';
  }

  if (status >= 500) console.error(err);
  res.status(status).json({ message });
}

module.exports = { notFound, errorHandler };

