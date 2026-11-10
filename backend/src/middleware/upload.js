const fs = require('fs');
const path = require('path');
const multer = require('multer');
const { HttpError } = require('../utils/http');

const UPLOAD_ROOT = path.join(__dirname, '..', '..', 'uploads');
const ALLOWED = { 'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp' };

/** Membuat middleware multer untuk satu file field "photo" ke subfolder tertentu. */
function photoUpload(subfolder) {
  const dir = path.join(UPLOAD_ROOT, subfolder);
  fs.mkdirSync(dir, { recursive: true });

  const upload = multer({
    storage: multer.diskStorage({
      destination: dir,
      filename: (req, file, cb) => {
        const ext = ALLOWED[file.mimetype] || '.jpg';
        cb(null, `${subfolder}-${req.params.id}-${Date.now()}${ext}`);
      },
    }),
    limits: { fileSize: 5 * 1024 * 1024 },
    fileFilter: (req, file, cb) => {
      if (!ALLOWED[file.mimetype]) return cb(new HttpError(400, 'Foto harus berformat JPG, PNG, atau WEBP'));
      cb(null, true);
    },
  }).single('photo');

  return (req, res, next) =>
    upload(req, res, (err) => {
      if (err) return next(err);
      if (!req.file) return next(new HttpError(400, 'File foto (field "photo") wajib dikirim'));
      next();
    });
}

/** Menghapus file lama (path relatif seperti "uploads/equipments/x.jpg") bila ada. */
function removeFile(relativePath) {
  if (!relativePath) return;
  const full = path.join(UPLOAD_ROOT, '..', relativePath);
  if (full.startsWith(UPLOAD_ROOT)) fs.promises.unlink(full).catch(() => {});
}

module.exports = { photoUpload, removeFile, UPLOAD_ROOT };

