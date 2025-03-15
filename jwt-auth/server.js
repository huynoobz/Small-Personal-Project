const express = require("express");
const jwt = require("jsonwebtoken");
require("dotenv").config();

const app = express();
app.use(express.json());

const users = [
    { id: 1, username: "huy", password: "123456", role: "admin"},
    { id: 2, username: "huy2", password: "123456", role: "user"},
];
const logout_jwt_list = []; 

// Endpoint đăng nhập để tạo JWT
app.post("/login", (req, res) => {
    const { username, password } = req.body;
    const user = users.find((u) => u.username === username && u.password === password);

    if (!user) return res.status(401).json({ message: "Sai thông tin đăng nhập" });

    const token = jwt.sign({ userId: user.id, role: user.role }, process.env.JWT_SECRET, { expiresIn: "1h" });
    res.json({ token });
});

// Middleware xác thực JWT
const authenticateToken = (req, res, next) => {
    const token = req.header("Authorization")?.split(" ")[1];
    if (!token) return res.status(401).json({ message: "Không có token" });
    
    jwt.verify(token, process.env.JWT_SECRET, (err, decoded) => {
        if ((err) || (JSON.stringify(logout_jwt_list).includes(JSON.stringify(decoded)))) 
          return res.status(403).json({ message: "Token không hợp lệ" });
        req.user = decoded;
        next();
    });
};

const authenticateToken_admin = (req, res, next) => {
    const token = req.header("Authorization")?.split(" ")[1];
    if (!token) return res.status(401).json({ message: "Không có token" });
    
    jwt.verify(token, process.env.JWT_SECRET, (err, decoded) => {
        if ((err) || (JSON.stringify(logout_jwt_list).includes(JSON.stringify(decoded)))) 
          return res.status(403).json({ message: "Token không hợp lệ" });
        if (JSON.parse(JSON.stringify(decoded)).role != "admin")
          return res.status(403).json({ message: "Admin only" });
        req.user = decoded;
        next();
    });
};

// Endpoint cần xác thực mới truy cập được
app.get("/protected", authenticateToken, (req, res) => {
    res.json({ message: "Bạn đã truy cập thành công!", user: req.user });
});

// Endpoint logout
app.get("/logout", authenticateToken, (req, res) => {
    res.json({ message: "Bạn đã đăng xuất thành công!"});
    logout_jwt_list.push(req.user)
});

// Endpoint admin only
app.get("/secret", authenticateToken_admin, (req, res) => {
    res.json({ secret: process.env.JWT_SECRET});
});


app.listen(3000, () => console.log("Server chạy tại http://localhost:3000"));
