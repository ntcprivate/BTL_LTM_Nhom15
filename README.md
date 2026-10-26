# Draw & Guess — Nhóm 15

Trò chơi vẽ và đoán từ bằng JavaFX, dùng TCP để kết nối client/server và MySQL để lưu tài khoản, chủ đề, từ khóa, kết quả trận. Mỗi phòng hỗ trợ **2–8 người**, với 100 phòng trong bộ nhớ server.

## Chức năng

- Đăng ký, đăng nhập; phân quyền USER và ADMIN.
- Trang chủ sau đăng nhập với lối tắt vào phòng, lịch sử và hồ sơ.
- Chọn avatar từ 9 tranh Van Gogh; avatar được lưu theo tài khoản và hiển thị trong phòng chờ.
- Vào phòng, chọn chủ đề, sẵn sàng, vẽ và đoán từ, tính điểm và xếp hạng.
- Người vào phòng đầu tiên là chủ phòng và có thể mời người khác ra; người bị mời ra vẫn tự vào lại hoặc được mời lại vào phòng. Người chưa sẵn sàng không bị tự động đưa ra; chỉ chủ phòng mới mời người khác ra được.
- Khi mọi người trong phòng đã sẵn sàng, phòng chờ **đếm ngược 5 giây** rồi mới vào trận. Trong lúc đếm, ai bấm **Tôi chưa sẵn sàng!** thì trận không bắt đầu và cả phòng thấy tên người đó; khi mọi người sẵn sàng lại thì đếm lại từ đầu. Có người rời phòng giữa lúc đếm thì những người còn lại được đếm lại.
- Trong phòng chờ, nút **Mời người chơi** hiện danh sách người đang online (đang rảnh / đang ở phòng nào / đang chơi). Ai trong phòng cũng mời được người đang rảnh; người được mời thấy hộp thoại **Vào phòng / Từ chối** (tự hết hạn sau 30 giây). Mỗi người chỉ mời lại cùng một người sau 10 giây.
- Ở màn hình chọn phòng, nút **Xem danh sách** trong thẻ "Người đang online" cho xem ai đang online và đang ở đâu (chỉ xem, muốn mời thì vào phòng trước).
- **Mất kết nối giữa trận** (rớt mạng, tắt game): client tự kết nối lại 5 lần, mỗi lần cách 2 giây, rồi đưa người chơi về đúng trận đang chơi với từ khóa, thời gian, gợi ý và điểm hiện tại. Nếu tắt hẳn game thì đăng nhập lại là tự vào lại trận, hoặc nhấp đúp vào phòng đang chơi của mình. Người chủ động bấm **Rời phòng** hoặc **Đăng xuất** khi đang chơi thì không vào lại được trận đó.
- **Vòng đấu**:
  - Ở phòng chờ, chủ phòng chọn 1–5 vòng. Mỗi vòng là một chủ đề, hoặc để "Ngẫu nhiên"; vòng ngẫu nhiên tránh chủ đề đã có ở vòng khác.
  - Người khác chỉ xem được. Không đổi được trong lúc đếm ngược hay đang chơi.
  - Mỗi vòng, cả phòng vẽ một từ của chủ đề đó, rồi đoán lần lượt tranh của nhau. Điểm cộng dồn qua các vòng.
  - Lịch sử trận lưu tên tất cả các chủ đề và tranh của từng vòng.
- **Chat ở phòng chờ**:
  - Khi thu gọn, khung chat chỉ hiện tin nhắn mới nhất và số tin chưa đọc.
  - Bấm biểu tượng chat để mở toàn bộ đoạn chat. Đoạn chat chiếm cả cột bên phải và có nút "Thu gọn".
  - Tin nhắn ở phòng chờ được chuyển sang khung chat của màn chơi.
- **Âm thanh**:
  - Có âm báo ngắn khi: đếm ngược vào trận, bắt đầu vẽ hoặc sang vòng mới, 5 giây cuối mỗi lượt, đoán đúng/sai, hết giờ, có kết quả trận, có tin nhắn của người khác và có lời mời.
  - Nút loa ở trang chủ, danh sách phòng, phòng chờ và màn chơi dùng để tắt/bật tiếng. Lựa chọn được nhớ cho lần mở game sau.
  - Âm báo do nhóm tự tạo bằng code (`src/main/resources/sounds`), không dùng nhạc có bản quyền.
- **Hộp thông báo**:
  - Bấm ra ngoài hộp thông báo, hoặc nhấn Esc, thì đóng hộp. Cách này giống bấm nút hủy/đóng; với lời mời thì tính là từ chối.
  - Bấm ra ngoài danh sách người online thì đóng danh sách, và cú bấm vẫn có tác dụng ở màn hình phía sau.
- **Giữ tranh khi mất kết nối lúc đang vẽ**:
  - Trong lúc vẽ, client gửi bản nháp tranh lên server mỗi 2 giây, nhưng chỉ khi có nét mới.
  - Người vào lại kịp sẽ thấy tranh dở và vẽ tiếp được.
  - Người không quay lại thì đến lượt đoán, cả phòng xem tranh cuối cùng của họ thay vì tranh trắng.
- Khi vẽ có nút **Hoàn tác** (Ctrl+Z) để bỏ nét vẽ gần nhất hoặc khôi phục tranh vừa bị xóa (tối đa 20 bước).
- Khi đoán, người chơi thấy gợi ý số chữ cái dạng `_ _ _   _ _ _`; sau 15 giây server mở thêm một chữ cái ngẫu nhiên.
- Trong phiên đoán có nút **Báo cáo tranh** (khi tranh không đúng từ khóa, ví dụ viết chữ lên tranh). Khi **quá nửa** số người đoán đang online cùng báo cáo, người vẽ mất số điểm đã nhận ở vòng đó và không được cộng thêm; người đoán vẫn giữ điểm. Có thể báo cáo cả trong 3 giây công bố đáp án.
  - Người đã đoán đúng thì không báo cáo được nữa.
  - Người đã báo cáo thì không đoán tiếp tranh đó. Lượt kết thúc sớm khi mọi người đoán đều đã đoán đúng, hết 3 lần đoán, hoặc đã báo cáo.
- **Điểm theo tốc độ**: đoán đúng trong 10 giây đầu được 3 điểm, 10 giây tiếp theo 2 điểm, sau đó 1 điểm; người đoán đúng đầu tiên được thêm 1 điểm; người vẽ được 1 điểm cho mỗi người đoán đúng.
- **Trò chuyện** trong trận: khung chat bên trái ô vẽ (cửa sổ hẹp thì nằm trong thẻ "Trò chuyện" cạnh bảng điểm). Server chặn tin nhắn để lộ từ khóa chưa công bố (kể cả viết không dấu hoặc viết liền) và chặn gửi quá dồn dập.
- Mỗi tin nhắn (trong trận và ở phòng chờ) hiện **giờ gửi** theo giờ máy người xem: `14:05` nếu là hôm nay, kèm ngày nếu là hôm khác.
- **Màn kết quả có hai bảng xếp hạng** để chọn:
  - **Người đoán**: xếp theo điểm; bằng điểm thì ai có thời gian đoán đúng trung bình (TB) ngắn hơn xếp trên. Mỗi người có hiện thời gian đoán đúng trung bình của mình.
  - **Người vẽ**: xếp theo số lượt người khác đoán trúng tranh của mình, rồi ít tranh bị báo cáo hơn, rồi tranh được đoán ra nhanh hơn (TB). Lượt đoán trúng trên tranh bị báo cáo không được tính. Bảng này chỉ có ở màn kết quả, không lưu vào lịch sử.
- **Chơi lại**: màn kết quả có nút **Chơi lại** (về phòng chờ và tự báo sẵn sàng) và dòng "2/3 người muốn chơi lại • Đang chờ: …". Cả phòng cùng bấm thì đếm ngược 5 giây rồi vào ván mới.
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

Các script tạo bảng dùng `IF NOT EXISTS`; không xóa dữ liệu đang có. Riêng `registration.sql` tự thêm cột `avatar_key` nếu bảng tài khoản cũ chưa có cột này. Vì vậy, sau khi cập nhật phiên bản có avatar, hãy chạy lại `registration.sql` một lần trên database hiện có; tài khoản cũ được giữ nguyên và nhận avatar mặc định.

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

### Cấu hình địa chỉ server cho client

Màn hình đăng nhập có ô **Địa chỉ server** và **Cổng**. Khi server và client chạy trên cùng máy, dùng `localhost` và `5555`. Khi chơi qua LAN, nhập IPv4 của máy chạy server.

Để đặt giá trị mặc định riêng cho một máy, chạy:

```powershell
Copy-Item config/client.properties.example config/client.properties
```

Sau đó sửa `server.host` và `server.port` trong `config/client.properties`. File này được Git bỏ qua. Có thể dùng biến môi trường `DRAWGUESS_SERVER_HOST` và `DRAWGUESS_SERVER_PORT`; chúng được ưu tiên hơn file cấu hình. Người dùng vẫn có thể thay đổi giá trị trên màn hình đăng nhập trước khi kết nối.

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

ADMIN đăng nhập sẽ mở màn hình quản trị. USER đăng nhập sẽ vào trang chủ. Tại đây có thể vào danh sách phòng, mở lịch sử trận hoặc chọn “Hồ sơ & avatar”. Chọn một tranh, bấm **Lưu avatar**, rồi vào phòng để thấy avatar của mình và những người chơi khác.

## Chạy thử không cần MySQL

Server chạy thử lưu tài khoản, từ khóa và lịch sử trong bộ nhớ, không cần MySQL hay `config/db.properties`. Dữ liệu mất khi tắt server.

```powershell
mvn -q compile exec:java "-Dexec.mainClass=com.nhom15.drawguess.server.demo.DemoServerMain"
```

Mở thêm terminal và chạy `mvn javafx:run` cho mỗi người chơi. Tài khoản có sẵn: `demo1`–`demo4` (mật khẩu `123`) và `admin` (mật khẩu `admin`). Có 3 chủ đề mẫu, mỗi chủ đề 10 từ, đủ cho phòng 8 người. Chỉ chạy một server tại một thời điểm vì cả hai cùng dùng cổng 5555.

## Chơi trên hai máy trong LAN

Máy A có thể chạy **MySQL + server + một client**. Máy B chỉ cần JDK/Maven và client. Hai máy kết nối cùng mạng; chỉ máy A chạy server.

1. Trên máy A, dùng `ipconfig` để lấy IPv4 của card Wi-Fi/Ethernet đang dùng, ví dụ `192.168.1.10`.
2. Client trên máy A giữ `localhost`. Trên màn hình đăng nhập của máy B, nhập IPv4 máy A vào ô **Địa chỉ server** và giữ cổng `5555`.
3. Cho phép Java/server hoặc TCP cổng 5555 qua Windows Firewall trên máy A cho mạng Private. Chạy server và hai client.
4. Trên máy B, có thể kiểm tra kết nối bằng `Test-NetConnection 192.168.1.10 -Port 5555`.

Không cần mở cổng MySQL cho máy B. Nếu IP máy A thay đổi, nhập địa chỉ mới trên màn hình đăng nhập hoặc cập nhật `config/client.properties` của máy B.

## Lưu dữ liệu và ảnh

MySQL lưu trong `user_account`, `category`, `word`, `game_match`, `match_player`, `match_round`, `round_guess`. Cột `user_account.avatar_key` lưu khóa của tranh đại diện. Lịch sử lưu tên và từ khóa tại thời điểm chơi nên thao tác quản trị không đổi nội dung trận cũ.

Ảnh nằm trên máy server tại `server-data/drawings/<match-id>/<user-id>.png`. Giữ nguyên thư mục này để xem lại tranh; khi chuyển server, sao lưu cả database và thư mục ảnh. Repo không chứa tài khoản thử, dữ liệu trận hoặc ảnh chơi thử. Các phòng đang hoạt động không được khôi phục sau khi khởi động lại server.

## Kiểm thử

```powershell
mvn test
mvn '-Ddrawguess.integration=true' test
mvn '-Ddrawguess.ui=true' test
```

Lệnh đầu chạy unit test và TCP test, không cần MySQL. Kiểm thử tích hợp tạo và xóa database `drawguess_test_<UUID>` riêng, cần MySQL được cấu hình và quyền CREATE/DROP DATABASE. Kiểm thử giao diện cần môi trường desktop; ảnh kiểm tra nằm trong `target/qa`.

Xem thêm [HUONG_DAN.txt](HUONG_DAN.txt) về trạng thái trận và giới hạn lưu lịch sử.
