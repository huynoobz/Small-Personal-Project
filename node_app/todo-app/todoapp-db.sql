CREATE DATABASE todoapp;

USE todoapp;

CREATE TABLE tasks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status ENUM('pending', 'in_progress', 'completed') DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

SET @count = 0;
UPDATE tasks SET tasks.id = @count := @count + 1 where id>0;
ALTER TABLE tasks AUTO_INCREMENT =1;

INSERT INTO tasks (title, description, status) VALUES
('Tìm hiểu RESTful API', 'Nghiên cứu lý thuyết về API RESTful', 'pending'),
('Xây dựng API với Express', 'Thực hành tạo API CRUD', 'in_progress'),
('Triển khai ứng dụng lên server', 'Sử dụng PM2 và Nginx', 'completed');

SELECT * FROM tasks;
DELETE FROM tasks where id=4;
drop TABLE taskS;
SELECT * FROM tasks WHERE title LIKE '%API%';