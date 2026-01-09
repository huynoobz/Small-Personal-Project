const express = require("express");
const app = express();
app.use(express.json());

let users = [
  { id: 1, name: "Huy", email: "huy@example.com" },
  { id: 2, name: "Minh", email: "minh@example.com" }
];

// Lấy danh sách users
app.get("/users", (req, res) => {
  res.json(users);
});

// Lấy thông tin 1 user theo ID
app.get("/users/:id", (req, res) => {
  const user = users.find(u => u.id == req.params.id);
  user ? res.json(user) : res.status(404).json({ error: "User not found" });
});

// Thêm user mới
app.post("/users", (req, res) => {
  const newUser = { id: users.length + 1, ...req.body };
  users.push(newUser);
  res.status(201).json(newUser);
});

// Cập nhật user
app.put("/users/:id", (req, res) => {
  const index = users.findIndex(user => user.id == req.params.id);
  if (index !== -1) {
    users[index] = { ...users[index], ...req.body };
  }
  res.status(201).json(users[index]);
});

// Chạy server
app.listen(3000, () => console.log("REST API running on port 3000"));
