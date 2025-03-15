const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const taskRoutes = require('./routes/taskRoutes');

const app = express();
const PORT = process.env.PORT || 5000;

// Middleware kiểm tra Token (JWT)
const jwt = require('jsonwebtoken');

const authMiddleware = (req, res, next) => {
    const token = req.header('Authorization')?.replace('Bearer ', '');
    if (!token) return res.status(401).json({ message: 'Không có token!' });

    try {
        const decoded = jwt.verify(token, process.env.S_KEY);
        req.user = decoded;
        next();
    } catch (err) {
        res.status(403).json({ message: 'Token không hợp lệ!' });
    }
};

app.use(bodyParser.json());
app.use(cors());

app.post('/login', (req, res) => {
    const { username, password } = req.body;
    // Giả định kiểm tra tài khoản đúng
    if (username === process.env.admin && password === process.env.admin_pass) {
        const token = jwt.sign({ username, role: 'admin' }, process.env.S_KEY, { expiresIn: '1h' });
        return res.json({ token });
    }
    res.status(401).json({ message: 'Sai tài khoản hoặc mật khẩu!' });
});

const roleMiddleware = (role) => (req, res, next) => {
    if (req.user.role !== role) return res.status(403).json({ message: 'Không đủ quyền truy cập!' });
    next();
};

// Chỉ admin mới có thể truy cập
app.get('/admin', authMiddleware, roleMiddleware('admin'), (req, res) => {
    res.json({ message: 'Chào mừng admin!' });
});

// Sử dụng các routes cho API
app.use('/api', taskRoutes);

// Trang chính
app.get('/', (req, res) => {
    res.send('Welcome to the To-Do API with MySQL!');
});

app.use((err, req, res, next) => {
    console.error(err.stack);
    res.status(500).json({ message: 'Đã xảy ra lỗi!' });
});

const fs = require('fs');
const path = require('path');
const morgan = require('morgan');
const accessLogStream = fs.createWriteStream(path.join(__dirname, 'access.log'), { flags: 'a' });

app.use(morgan('combined',{ stream: accessLogStream }));

// Khởi động server
app.listen(PORT, () => {
    console.log(`Server is running on http://localhost:${PORT}`);
});
