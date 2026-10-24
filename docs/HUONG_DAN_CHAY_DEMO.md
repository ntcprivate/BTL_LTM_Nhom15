# Hướng dẫn chạy demo Draw & Guess (không cần MySQL)

Server demo lưu tài khoản, chủ đề, từ khóa và lịch sử trận **trong bộ nhớ**, nên không cần cài MySQL hay tạo `config/db.properties`. Dùng để chơi thử, kiểm tra giao diện hoặc trình bày đồ án.

> **Lưu ý:** dữ liệu mất hết khi tắt server demo. Lần sau bật lại sẽ có đúng dữ liệu mẫu ban đầu.

---

## 1. Chuẩn bị (chỉ cần làm một lần)

- Cài **JDK 24 trở lên** và **Maven**. Kiểm tra bằng:
  ```powershell
  java -version
  mvn -version
  ```
- Có mã nguồn dự án, ví dụ ở `E:\BTL_LTM_Nhom15`.

---

## 2. Chạy demo

Mở **PowerShell** tại thư mục dự án (thư mục chứa `pom.xml`):
```powershell
cd E:\BTL_LTM_Nhom15
```

### Bước 1: Bật server demo
Ở terminal thứ nhất, chạy lệnh sau và **để nguyên cửa sổ này**:
```powershell
mvn -q compile exec:java "-Dexec.mainClass=com.nhom15.drawguess.server.demo.DemoServerMain"
```
Khi thấy dòng **`Đang chờ Client kết nối...`** là server đã sẵn sàng.

### Bước 2: Mở game
Mỗi người chơi mở **một terminal mới** tại thư mục dự án và chạy:
```powershell
mvn javafx:run
```
Muốn 2 người chơi trên cùng máy thì chạy lệnh này ở 2 terminal; tối đa 8 người.

### Bước 3: Đăng nhập và chơi
1. Đăng nhập bằng một tài khoản có sẵn (xem mục 3). Máy chủ để mặc định `localhost:5555`.
2. Chọn **Vào phòng chơi**, rồi **nhấp đúp vào cùng một phòng** (ví dụ Phòng 001).
3. Mọi người bấm **Sẵn sàng**. Trận bắt đầu khi tất cả đã sẵn sàng (ít nhất 2 người).
4. **Vẽ** từ khóa của mình trong 60 giây.
5. **Đoán** lần lượt tranh của từng người: mỗi vòng 30 giây, tối đa 3 lần đoán. Sau 15 giây, gợi ý sẽ mở thêm 1 chữ cái.
6. Hết trận, xem bục xếp hạng rồi bấm **Về phòng chờ** để chơi tiếp.

### Bước 4: Tắt demo
1. Đóng các cửa sổ game.
2. Ở terminal đang chạy server demo, bấm **Ctrl + C**.

---

## 3. Tài khoản có sẵn

| Tài khoản | Mật khẩu | Vai trò |
|---|---|---|
| `demo1` | `123` | Người chơi |
| `demo2` | `123` | Người chơi |
| `demo3` | `123` | Người chơi |
| `demo4` | `123` | Người chơi |
| `admin` | `admin` | Quản trị: thêm, sửa, xóa chủ đề và từ khóa |

Có thể bấm **"Chưa có tài khoản? Đăng ký"** để tạo tài khoản mới; tài khoản này cũng mất khi tắt server.

Dữ liệu mẫu gồm 3 chủ đề **Động vật**, **Đồ ăn** và **Đồ vật**, mỗi chủ đề 10 từ, đủ cho phòng 8 người.

---

## 4. Chơi với máy khác trong cùng mạng LAN

**Máy A** (chạy server):
1. Làm Bước 1 như trên.
2. Lấy địa chỉ IPv4 bằng lệnh `ipconfig`, ví dụ `192.168.1.10`.
3. Nếu Windows hỏi, cho phép Java đi qua Firewall ở mạng **Private**.

**Máy B** (chỉ chơi):
1. Chạy `mvn javafx:run`.
2. Ở màn hình đăng nhập, bấm **"Đổi máy chủ"** và nhập IPv4 của máy A, giữ cổng `5555`.
3. Kiểm tra kết nối nếu cần:
   ```powershell
   Test-NetConnection 192.168.1.10 -Port 5555
   ```
   Thấy `TcpTestSucceeded : True` là kết nối được.

---

## 5. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| Server báo **"Không mở được cổng 5555"** | Đang có một server khác chạy (server demo hoặc server thật). Tắt server đó rồi chạy lại. |
| Game báo **"Không nhận được phản hồi..."** | Server demo chưa bật, hoặc nhập sai địa chỉ máy chủ. Kiểm tra lại Bước 1 hoặc mục 4. |
| **"Tài khoản đang đăng nhập trên một kết nối khác"** | Tài khoản đó đang được dùng ở cửa sổ khác. Mỗi cửa sổ phải dùng một tài khoản riêng. |
| **"Chưa có chủ đề đủ từ khóa cho số người chơi"** | Admin đã xóa bớt từ khóa. Thêm từ hoặc tắt rồi bật lại server demo để về dữ liệu mẫu. |
| Terminal đứng im sau khi chạy `mvn javafx:run` | Bình thường: lệnh giữ terminal tới khi đóng cửa sổ game. Cửa sổ game có thể đang nằm sau các cửa sổ khác, xem trên thanh taskbar. |
| Các dòng `WARNING: ... sun.misc.Unsafe` | Chỉ là cảnh báo của Maven/JavaFX, bỏ qua được. |

---

## 6. Khác nhau giữa server demo và server thật

| | Server demo | Server thật (`ServerMain`) |
|---|---|---|
| Cần MySQL | Không | Có |
| Lưu dữ liệu sau khi tắt | Không | Có |
| Tài khoản mẫu | Có sẵn `demo1`–`demo4`, `admin` | Phải tự tạo, ADMIN tạo bằng `admin.sql` |
| Lệnh chạy | `mvn -q compile exec:java "-Dexec.mainClass=com.nhom15.drawguess.server.demo.DemoServerMain"` | Chạy `ServerMain` trong IDE (xem README) |

Hai server dùng chung cổng **5555** nên chỉ chạy được một cái mỗi lần. Phía client (`mvn javafx:run`) giống hệt nhau cho cả hai.
