const mysql = require('mysql2');
const dotenv = require('dotenv');
dotenv.config();

// Tạo kết nối tới MySQL
const db = mysql.createConnection({
    host: process.env.DB_HOST,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME
});

// Kết nối tới Database
db.connect((err) => {
    if (err) {
        console.error('Không thể kết nối tới MySQL:', err);
        return;
    }
    console.log('Đã kết nối tới MySQL Database.');
});

module.exports = db;
