const fs = require('fs');
const path = require('path');
const express = require('express');
const cors = require('cors');

const { notFound, errorHandler } = require('./middleware/error');
const { UPLOAD_ROOT } = require('./middleware/upload');

const app = express();

app.use(cors());
app.use(express.json({ limit: '1mb' }));

// Foto dilayani statis agar dapat ditampilkan Coil: http://host:3000/uploads/...
fs.mkdirSync(UPLOAD_ROOT, { recursive: true });
app.use('/uploads', express.static(UPLOAD_ROOT));

app.get('/', (req, res) => res.json({ app: 'CampKu API', status: 'ok' }));

app.use('/api/auth', require('./routes/auth'));
app.use('/api/equipments', require('./routes/equipments'));
app.use('/api/categories', require('./routes/categories'));
app.use('/api/customers', require('./routes/customers'));
app.use('/api/rentals', require('./routes/rentals'));
app.use('/api/users', require('./routes/users'));
app.use('/api/dashboard', require('./routes/dashboard'));

app.use(notFound);
app.use(errorHandler);

module.exports = app;

