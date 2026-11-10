const jwt = require('jsonwebtoken');
const { HttpError } = require('../utils/http');

const ACCESS_SECRET = () => process.env.JWT_ACCESS_SECRET || 'dev-access-secret';
const REFRESH_SECRET = () => process.env.JWT_REFRESH_SECRET || 'dev-refresh-secret';

const ACCESS_TTL = '15m'; // FR-02
const REFRESH_TTL = '7d'; // FR-02

function signAccessToken(user) {
  return jwt.sign({ sub: String(user.id), role: user.role }, ACCESS_SECRET(), { expiresIn: ACCESS_TTL });
}

function signRefreshToken(user) {
  // jti membuat setiap refresh token unik walau dibuat pada detik yang sama
  return jwt.sign({ sub: String(user.id), jti: require('crypto').randomUUID() }, REFRESH_SECRET(), {
    expiresIn: REFRESH_TTL,
  });
}

function verifyRefreshToken(token) {
  return jwt.verify(token, REFRESH_SECRET());
}

/** Middleware: wajib header Authorization: Bearer <access token>. */
function authenticate(req, res, next) {
  const header = req.headers.authorization || '';
  const [scheme, token] = header.split(' ');
  if (scheme !== 'Bearer' || !token) {
    return next(new HttpError(401, 'Token tidak ditemukan'));
  }
  try {
    const payload = jwt.verify(token, ACCESS_SECRET());
    req.user = { id: Number(payload.sub), role: payload.role };
    next();
  } catch (err) {
    // 401 -> klien (interceptor OkHttp) akan mencoba refresh token
    next(new HttpError(401, err.name === 'TokenExpiredError' ? 'Token kedaluwarsa' : 'Token tidak valid'));
  }
}

/** Middleware: membatasi akses berdasarkan peran. */
const requireRole = (...roles) => (req, res, next) => {
  if (!req.user || !roles.includes(req.user.role)) {
    return next(new HttpError(403, 'Anda tidak memiliki akses untuk aksi ini'));
  }
  next();
};

const adminOnly = requireRole('admin');

module.exports = {
  signAccessToken, signRefreshToken, verifyRefreshToken, authenticate, requireRole, adminOnly,
};
