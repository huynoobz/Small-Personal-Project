const express = require('express');
const router = express.Router();
const db = require('../config/db'); 

// Lấy danh sách tất cả các task
router.get('/tasks', (req, res) => {
    const page = parseInt(req.query.page) || 1;
    const limit = parseInt(req.query.limit) || 10;
    const offset = (page - 1) * limit;
    const search = req.query.search || '';

    const sql = 'SELECT * FROM tasks WHERE title LIKE ? LIMIT ? OFFSET ?';
    db.query(sql, ['%'+search+'%', limit, offset], (err, results) => {
        if (err) throw err;

        const countSql = 'SELECT COUNT(*) AS total FROM tasks WHERE title LIKE ?';
        db.query(countSql, ['%'+search+'%'], (err, countResult) => {
            const total = countResult[0].total;
            const totalPages = Math.ceil(total / limit);
            res.json({
                page,
                limit,
                total,
                totalPages,
                search,
                results
            });
        });

    });
});

const Joi = require('joi');

const postSchema = Joi.object({
    title: Joi.string().min(3).required(),
    description: Joi.any().required()
});

// Thêm mới một task
router.post('/tasks', (req, res) => {
    const { error } = postSchema.validate(req.body);
    if (error) return res.status(400).json({ message: error.details[0].message });
    const { title, description } = req.body;

    const sql = 'INSERT INTO tasks (title, description) VALUES (?, ?)';
    db.query(sql, [title, description], (err, result) => {
        if (err) throw err;
        res.json({ message: 'Task created!', taskId: result.insertId });
    });
});

const putSchema = Joi.object({
    title: Joi.string().min(3).required(),
    description: Joi.any().required(),
    status: Joi.string().valid('pending', 'in_progress', 'completed').required(),
});

// Cập nhật task
router.put('/tasks/:id', (req, res) => {
    const { id } = req.params;
    const { error } = putSchema.validate(req.body);
    if (error) return res.status(400).json({ message: error.details[0].message });
    const { title, description, status } = req.body;

    const sql = 'UPDATE tasks SET title = ?, description = ?, status = ? WHERE id = ?';
    db.query(sql, [title, description, status, id], (err, result) => {
        if (err) throw err;
        res.json({ message: 'Task updated!' });
    });
});

// Xóa task
router.delete('/tasks/:id', (req, res) => {
    const { id } = req.params;
    const sql = 'DELETE FROM tasks WHERE id = ?';
    db.query(sql, [id], (err, result) => {
        if (err) throw err;
        if (result.affectedRows == 0) res.json({ message: 'Task NOT exist!' }); 
        else res.json({ message: 'Task deleted!' });
    });
    db.query('SET @count = 0');
    db.query('UPDATE tasks SET tasks.id = @count := @count + 1 where id>0;');
    db.query('ALTER TABLE tasks AUTO_INCREMENT =1;');
});

module.exports = router;
