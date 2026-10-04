# Draw & Guess — Nhóm 15

Trò chơi vẽ và đoán từ bằng JavaFX, dùng TCP để kết nối client/server và MySQL để lưu tài khoản, chủ đề, từ khóa, kết quả trận. Mỗi phòng hỗ trợ **2–8 người**, với 100 phòng trong bộ nhớ server.

## Chức năng

- Đăng ký, đăng nhập; phân quyền USER và ADMIN.
- Vào phòng, chọn chủ đề, sẵn sàng, vẽ và đoán từ, tính điểm và xếp hạng.
- Lịch sử trận: thời gian, người tham gia, điểm, thứ hạng, từng vòng và các lượt đoán.
- Xem ảnh từng vòng trong lịch sử; client tải ảnh từ server qua TCP.
- ADMIN thêm, sửa, xóa chủ đề và từ khóa.

## Quy trình làm việc nhóm

`main` là nhánh chứa code chung đã được kiểm tra. Thành viên làm việc trên nhánh riêng và tạo Pull Request (PR) vào `main`; không push trực tiếp lên `main`.

Trước khi bắt đầu một công việc mới, hãy bảo đảm thay đổi đang làm đã được lưu trên nhánh của mình, rồi chạy:

```powershell
git switch main
git pull --ff-only origin main
git switch -c feature/ten-chuc-nang
```

Tên nhánh mô tả công việc, ví dụ `feature/room-chat`, `fix/history-image` hoặc `docs/setup-guide`. Sau khi sửa code và kiểm thử:

```powershell
mvn test
git add .
git status
git commit -m "Describe the change"
git push -u origin feature/ten-chuc-nang
```

Trước khi commit, kiểm tra không đưa mật khẩu, `config/db.properties`, `target/` hoặc `server-data/` vào Git. Trên GitHub, mở **Compare & pull request**, chọn nhánh đích `main`, ghi rõ phần đã sửa và kết quả kiểm thử, rồi yêu cầu leader `@ntcprivate` duyệt. Nếu cần sửa PR, tiếp tục commit và push lên chính nhánh đó; không cần tạo PR mới.

- **Approve** xác nhận code đã được duyệt; **Merge** mới đưa code vào `main`. Chờ leader kiểm tra và merge, không tự merge PR của mình theo quy trình của nhóm.
- Với quy tắc **Require approvals: 1**, PR cần ít nhất một approval hợp lệ. Quy tắc này tự nó không giới hạn người duyệt chỉ là leader; collaborator có quyền ghi cũng có thể duyệt PR của người khác. Người tạo PR không thể tự approve PR của mình.
- Khi bật **Dismiss stale pull request approvals when new commits are pushed**, nếu có commit mới làm thay đổi code trong PR đã được approve nhưng chưa merge, approval cũ bị hủy và cần duyệt lại. Tùy chọn này không tác động tới PR đã merge.
- Leader nên dùng cùng quy trình cho thay đổi lớn. Việc tài khoản quản trị được bỏ qua quy tắc hay không phụ thuộc cấu hình bảo vệ nhánh trên GitHub.

Sau khi PR được merge, cập nhật bản code chung bằng `git switch main` và `git pull --ff-only origin main` trước khi tạo nhánh cho công việc tiếp theo.

## Cài đặt môi trường

- JDK **24**, Maven và MySQL **8.x**. Kiểm tra `java -version` và `mvn -version` cùng sử dụng JDK 24.
- Clone repo và mở thư mục chứa `pom.xml`:

```powershell
git clone https://github.com/ntcprivate/BTL_LTM_Nhom15.git
cd BTL_LTM_Nhom15
```

## Cài cơ sở dữ liệu trên máy server

Trong MySQL Workbench, mở và chạy lần lượt:

1. `src/main/resources/db/registration.sql`: database và bảng tài khoản.
2. `src/main/resources/db/gameplay.sql`: chủ đề, từ khóa và dữ liệu mẫu.
3. `src/main/resources/db/history.sql`: các bảng lịch sử.
4. `src/main/resources/db/admin.sql`: tạo ADMIN theo hướng dẫn dưới đây.

Các script tạo bảng dùng `IF NOT EXISTS`; không xóa dữ liệu đang có. Chúng không tự nâng cấp cấu trúc bảng cũ khác với cấu trúc hiện tại.

### Tạo tài khoản ADMIN

Trong cùng một kết nối/tab SQL, chạy đoạn sau với mật khẩu riêng của bạn, rồi chạy toàn bộ `admin.sql`:

```sql
SET @admin_username = 'admin';
SET @admin_password = 'YOUR_PRIVATE_PASSWORD'; -- thay bằng mật khẩu bạn chọn
```

Kết quả cần là `ADMIN_READY` và role `ADMIN`. Script không có mật khẩu mặc định; không thay mật khẩu hay nâng quyền tài khoản đã tồn tại. Nếu tên đã thuộc USER, chọn tên khác. Tài khoản đăng ký qua ứng dụng luôn là USER. Nếu bạn đã có ADMIN thì dùng tài khoản đó, không cần tạo lại.

### Cấu hình MySQL

Chạy tại thư mục gốc dự án:

```powershell
Copy-Item config/db.properties.example config/db.properties
```

Mở `config/db.properties`, điền `db.user` và `db.password` theo MySQL trên máy server; sửa `db.url` nếu cần. File này được Git bỏ qua. Khi chạy từ IDE, đặt working directory là thư mục chứa `pom.xml`.

Cũng có thể dùng biến môi trường `DRAWGUESS_DB_URL`, `DRAWGUESS_DB_USER`, `DRAWGUESS_DB_PASSWORD`; chúng được ưu tiên hơn file cấu hình. Ứng dụng không tự đọc `.env`. Nếu không cấu hình, mặc định là MySQL localhost:3306, database `drawguess_db`, user `root`, mật khẩu rỗng.

## Chạy thử

```powershell
mvn compile
```

1. Mở dự án Maven trong IDE và chạy `com.nhom15.drawguess.server.ServerMain` trước. Server lắng nghe TCP cổng **5555**.
2. Mở terminal tại thư mục gốc và chạy client:

```powershell
mvn javafx:run
```

3. Mở thêm terminal, chạy client thứ hai bằng cùng lệnh. Đăng nhập hai tài khoản USER khác nhau, vào cùng phòng, chọn chủ đề và cả hai bấm sẵn sàng. Chủ đề cần đủ từ khác nhau cho số người tham gia.

ADMIN đăng nhập sẽ mở màn hình quản trị. USER mở “Lịch sử trận đấu” từ danh sách phòng, chọn trận đã kết thúc và vòng để xem tranh, điểm và lượt đoán.

## Chơi trên hai máy trong LAN

Máy A có thể chạy **MySQL + server + một client**. Máy B chỉ cần JDK/Maven và client. Hai máy kết nối cùng mạng; chỉ máy A chạy server.

1. Trên máy A, dùng `ipconfig` để lấy IPv4 của card Wi-Fi/Ethernet đang dùng, ví dụ `192.168.1.10`.
2. Client trên máy A giữ `localhost`. Trong bản code trên máy B, mở `src/main/java/com/nhom15/drawguess/client/controller/AuthController.java`, tìm `tcpClient.connect("localhost", 5555)` và thay `localhost` bằng IPv4 máy A.
3. Cho phép Java/server hoặc TCP cổng 5555 qua Windows Firewall trên máy A cho mạng Private. Chạy server và hai client.
4. Trên máy B, có thể kiểm tra kết nối bằng `Test-NetConnection 192.168.1.10 -Port 5555`.

Client hiện dùng địa chỉ server trong code, chưa có ô nhập IP. Không cần mở cổng MySQL cho máy B. Nếu IP máy A thay đổi, cập nhật lại địa chỉ client máy B.

## Lưu dữ liệu và ảnh

MySQL lưu trong `user_account`, `category`, `word`, `game_match`, `match_player`, `match_round`, `round_guess`. Lịch sử lưu tên và từ khóa tại thời điểm chơi nên thao tác quản trị không đổi nội dung trận cũ.

Ảnh nằm trên máy server tại `server-data/drawings/<match-id>/<user-id>.png`. Giữ nguyên thư mục này để xem lại tranh; khi chuyển server, sao lưu cả database và thư mục ảnh. Repo không chứa tài khoản thử, dữ liệu trận hoặc ảnh chơi thử. Các phòng đang hoạt động không được khôi phục sau khi khởi động lại server.

## Kiểm thử

```powershell
mvn test
mvn '-Ddrawguess.integration=true' test
mvn '-Ddrawguess.ui=true' test
```

Lệnh đầu chạy unit test và TCP test, không cần MySQL. Kiểm thử tích hợp tạo và xóa database `drawguess_test_<UUID>` riêng, cần MySQL được cấu hình và quyền CREATE/DROP DATABASE. Kiểm thử giao diện cần môi trường desktop; ảnh kiểm tra nằm trong `target/qa`.

Xem thêm [HUONG_DAN.txt](HUONG_DAN.txt) về trạng thái trận và giới hạn lưu lịch sử.
