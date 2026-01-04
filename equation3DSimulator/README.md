# Equation 3D Simulator

A powerful web-based 3D simulation tool for visualizing differential equations, dynamical systems, and mathematical models in real-time.

**Live Demo:** [Equation 3D Simulator](https://aquamarine-margot-94.tiiny.site/)

---

# Mô phỏng Phương trình 3D

Công cụ mô phỏng 3D mạnh mẽ trên web để trực quan hóa phương trình vi phân, hệ động lực và mô hình toán học theo thời gian thực.

**Bản demo trực tuyến:** [Equation 3D Simulator](https://aquamarine-margot-94.tiiny.site/)

---

## Features / Tính năng

### Supported Systems / Hệ thống được hỗ trợ

- **ODE (Ordinary Differential Equations)** - Linear & Nonlinear
- **PDE (Partial Differential Equations)** - Heat equation, Reaction-Diffusion
- **DAE (Differential Algebraic Equations)** - Index-1, Index-2, Index-3
- **Hybrid Systems** - Event-driven with reset rules
- **Stochastic Systems (SDE)** - Brownian motion, Ornstein-Uhlenbeck
- **Discrete Maps** - Iterative systems
- **Hamiltonian/Lagrangian Systems** - Conservative dynamics
- **Gradient Flow** - Optimization trajectories
- **Markov Processes** - Probabilistic systems
- **Algebraic Systems** - Parametric curves & implicit surfaces

### Key Features / Tính năng chính

- **Real-time 3D Visualization** - Interactive 3D plots with Three.js
- **Multiple Instances** - Run and compare multiple simulations simultaneously
- **Sync Mode** - Synchronize all instances with one click
- **First Person Mode** - Navigate the 3D space with WASD controls
- **Preset Library** - 100+ built-in presets (Lorenz, Rössler, Chua, etc.)
- **Custom Presets** - Save and load your own configurations
- **Adaptive Solvers** - RK4, Euler, Dormand-Prince, Fehlberg, and more
- **Expression Support** - Use math.js functions (sin, cos, exp, etc.)
- **Multi-language** - English and Vietnamese interface

---

## Quick Start / Bắt đầu nhanh

### English

1. **Select a System Type** - Choose from the dropdown (ODE, PDE, Hybrid, etc.)
2. **Choose a Preset** - Pick a built-in example or create your own
3. **Configure Parameters** - Set initial conditions, time step, and solver settings
4. **Click Start** - Watch your simulation come to life in 3D!

### Tiếng Việt

1. **Chọn loại hệ thống** - Chọn từ dropdown (ODE, PDE, Hybrid, v.v.)
2. **Chọn preset** - Chọn ví dụ có sẵn hoặc tạo của riêng bạn
3. **Cấu hình tham số** - Đặt điều kiện ban đầu, bước thời gian và cài đặt solver
4. **Nhấn Start** - Xem mô phỏng của bạn hiển thị trong không gian 3D!

---

## Usage Examples / Ví dụ sử dụng

### Lorenz Attractor / Hấp dẫn Lorenz

```
dx/dt = sigma*(y-x)
dy/dt = x*(rho-z)-y
dz/dt = x*y-beta*z

Parameters:
sigma = 10
rho = 28
beta = 8/3

Initial: 0.1, 0.1, 0.1
```

### Custom Equation / Phương trình tùy chỉnh

You can use variables `x`, `y`, `z`, `t` and arbitrary parameters. Supported functions include `sin`, `cos`, `exp`, `log`, `sqrt`, `pow`, etc.

Bạn có thể sử dụng biến `x`, `y`, `z`, `t` và các tham số tùy ý. Các hàm được hỗ trợ bao gồm `sin`, `cos`, `exp`, `log`, `sqrt`, `pow`, v.v.

---

## Controls / Điều khiển

### Camera Controls / Điều khiển camera

- **Orbit Mode (Default)**
  - Left mouse drag: Rotate camera
  - Mouse scroll: Zoom in/out
  - Right mouse drag: Pan
  - WASD: Move forward/backward/left/right
  - Shift/Ctrl: Move up/down

- **First Person Mode (Press Alt)**
  - WASD: Move forward/backward/left/right
  - Shift/Ctrl: Move up/down
  - Mouse: Rotate camera
  - Scroll: Adjust movement speed

### Simulation Controls / Điều khiển mô phỏng

- **Start** - Begin simulation
- **Pause/Resume** - Pause or resume simulation
- **Reset** - Reset to initial state
- **Validate** - Check if equations are valid
- **Add Instance** - Create multiple simulation instances
- **Sync Instances** - Run all instances simultaneously

---

## Advanced Features / Tính năng nâng cao

### Multiple Instances / Nhiều instance

Create multiple simulation instances to compare different parameter sets or initial conditions. Enable "Sync all instances" to run them simultaneously.

Tạo nhiều instance mô phỏng để so sánh các bộ tham số hoặc điều kiện ban đầu khác nhau. Bật "Sync all instances" để chạy đồng thời.

### Hybrid Systems / Hệ thống Hybrid

Define event conditions and reset rules for hybrid systems. Example: bouncing ball with elastic collision.

Định nghĩa điều kiện sự kiện và quy tắc reset cho hệ thống hybrid. Ví dụ: quả bóng nảy với va chạm đàn hồi.

### Custom Presets / Preset tùy chỉnh

Save your configurations as custom presets for quick access later.

Lưu cấu hình của bạn dưới dạng preset tùy chỉnh để truy cập nhanh sau này.

---

## Technical Details / Chi tiết kỹ thuật

- **Frontend:** Vanilla JavaScript, Three.js
- **Math Engine:** math.js
- **Solvers:** Euler, RK4, Adaptive RK methods (Dormand-Prince, Fehlberg, etc.)
- **Rendering:** WebGL via Three.js
- **Browser Support:** Modern browsers with WebGL support

---

## License / Giấy phép

This project is open source and available for educational and research purposes.

Dự án này là mã nguồn mở và có sẵn cho mục đích giáo dục và nghiên cứu.

---

## Author / Tác giả

Created by huynoobz

Version: 2.6.0

---

## Contributing / Đóng góp

Contributions are welcome! Please feel free to submit issues or pull requests.

Mọi đóng góp đều được chào đón! Vui lòng gửi issues hoặc pull requests.

