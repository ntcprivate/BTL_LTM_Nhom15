# Kế hoạch cải thiện — Draw & Guess

Danh sách tổng hợp từ ba lần rà soát: **code / bảo mật**, **luật chơi**, **UI/UX**.
Đánh dấu `[x]` khi đã làm xong, ghi commit hoặc nhánh bên cạnh để dễ tra lại.

**Mốc quay lại:** tag `moc-truoc-cai-thien-ui` (commit `7d61be5`, nhánh `dev_vu`).
Muốn bỏ các thay đổi sau mốc này: `git reset --hard moc-truoc-cai-thien-ui` (chỉ làm khi chắc chắn, thay đổi chưa commit sẽ mất).

Mức độ: 🔴 nghiêm trọng · 🟠 nên sửa · 🟡 nhỏ / trau chuốt

---

## Tiến độ

| Nhóm | Đã xong | Tổng |
|---|---|---|
| A. Luật chơi | 3 | 13 |
| B. UI/UX | 0 | 19 |
| C. Code, mạng, bảo mật | 0 | 12 |

---

## A. Luật chơi và cách chơi

- [x] **A8 — Gợi ý từ khóa** `_ _ _   _ _ _`, mở 1 chữ cái sau 15 giây — `7d61be5`
- [x] **A11 — Chủ phòng và kick**: người vào đầu là chủ phòng, có nút "Mời ra"; khi đã có người sẵn sàng, ai chưa sẵn sàng sau 60 giây tự rời phòng — `7d61be5`
- [x] **A12 — Hoàn tác khi vẽ**: nút ↶ Hoàn tác / Ctrl+Z, khôi phục cả tranh lỡ bấm "Xóa tranh" (tối đa 20 bước) — `7d61be5`
- [ ] 🔴 **A1 — Chọn chủ đề**: README ghi "chọn chủ đề" nhưng server chọn ngẫu nhiên (`WordDAO.selectWords`). Thêm chọn/bình chọn chủ đề hoặc sửa README.
- [ ] 🔴 **A2 — Chuẩn hóa đáp án**: so sánh sau NFC + gộp khoảng trắng (`GameManager.normalize`). Quyết định có chấp nhận gõ không dấu hay không.
- [ ] 🔴 **A3 — Chống viết chữ lên tranh**: người vẽ được +1 cho mỗi người đoán đúng, nên viết chữ là có lợi. Ghi luật trên màn hình vẽ, giới hạn điểm người vẽ khi *tất cả* đều đoán đúng, hoặc thêm nút báo cáo.
- [ ] 🔴 **A4 — Bỏ qua vòng có tranh trắng** (không vẽ, mất kết nối, gửi lỗi) thay vì bắt cả phòng chờ 30 giây.
- [ ] 🔴 **A5 — Kết thúc trận khi còn dưới 2 người online** (`GameManager.disconnect`).
- [ ] 🟠 **A6 — Điểm theo tốc độ**: ví dụ đúng trong 10 giây đầu +3, 10 giây tiếp +2, sau đó +1.
- [ ] 🟠 **A7 — Thông báo "X đã đoán đúng!"** cho cả phòng (không lộ đáp án).
- [ ] 🟠 **A9 — Nút "Nộp sớm"**: server nhận tranh trong pha DRAWING; ai cũng nộp thì chuyển sang đoán ngay.
- [ ] 🟠 **A10 — Đếm ngược 3–5 giây trước khi vào trận**, cho phép hủy sẵn sàng trong lúc đếm.
- [ ] 🟡 **A13 — Dùng cột `difficulty`** (EASY/MEDIUM/HARD): từ khó nhiều điểm hơn hoặc chia đều độ khó.

> Ghi chú: mọi người vẽ từ khác nhau trong cùng chủ đề, nên ai cũng loại trừ được từ của chính mình. Mất kết nối giữa trận thì không vào lại được. Hai điểm này chấp nhận được, chỉ cần ghi vào luật.

---

## B. Giao diện và trải nghiệm (UI/UX)

### Màn hình chơi game
- [ ] 🔴 **B1 — Thanh công cụ vẽ bị cắt chữ** ở cửa sổ khoảng 1000px ("…", "↶ Hoàn …", "Xóa tra…"). Chia 2 hàng hoặc dùng nút icon kèm tooltip. *Nút Hoàn tác (A12) làm lỗi này nặng thêm.*
- [ ] 🔴 **B2 — Không biết nút Tẩy đang bật hay tắt**: thêm `.toggle-button:selected` trong `app.css`, đổi chữ thành "Đang tẩy".
- [ ] 🔴 **B3 — Hỏi xác nhận khi bấm "Rời phòng" / "Đăng xuất" trong trận**; đưa hai nút ra xa ô đoán.
- [ ] 🟠 **B4 — Bảng điểm**: thêm avatar, tô đậm dòng của mình, đánh dấu người đang vẽ, ✓ cho người đã đoán đúng.
- [ ] 🟠 **B5 — Màn hình kết quả**: làm nổi bật người thắng (bục 1-2-3, vương miện).
- [ ] 🟠 **B6 — Đồng hồ đổi màu đỏ / nhấp nháy khi còn ≤ 10 giây.**
- [ ] 🟠 **B7 — Dòng trạng thái và placeholder ô đoán bị cắt**: cho tự xuống dòng, rút gọn placeholder.
- [ ] 🟡 **B8 — Phản hồi khi đoán**: ô nhập nháy xanh/đỏ, rung nhẹ khi sai, có thể thêm âm thanh.

### Điều hướng và thao tác
- [ ] 🟠 **B9 — Nút "Về danh sách phòng" ở Lịch sử thực ra về trang chủ** (`HistoryView.java:35`). Đổi thành "Về trang chủ".
- [ ] 🟠 **B10 — Nhấp đúp để vào phòng** và bộ lọc "Chỉ hiện phòng vào được" (`RoomListView`).
- [ ] 🟠 **B11 — Viền focus cho nút** khi dùng phím Tab: `.button:focused { -fx-border-color: #1d4ed8; -fx-border-width: 2; }`.
- [ ] 🟠 **B12 — Màn hình đăng ký không cho biết đang đăng ký vào server nào**: hiện địa chỉ server hoặc thêm ô nhập.

### Chi tiết và trau chuốt
- [ ] 🟡 **B13 — Chữ quá nhỏ** (9–11px ở `app.css` dòng 210, 470, 550…): nâng chữ thường lên tối thiểu khoảng 12px.
- [ ] 🟡 **B14 — Chữ tiếng Anh lẫn vào**: "Black" ở ô chọn màu; EASY/MEDIUM/HARD → Dễ / Trung bình / Khó.
- [ ] 🟡 **B15 — Admin**: thêm nhãn cho ô nhập, đổi "Sửa mục chọn" cho rõ nghĩa, tách "Đăng xuất" khỏi "Làm mới", hiện lỗi gần chỗ thao tác.
- [ ] 🟡 **B16 — Lịch sử**: danh sách trận quá thấp, ảnh tranh bị cắt và nhỏ, phải cuộn.
- [ ] 🟡 **B17 — Phòng chờ**: dòng "Sẵn sàng: 2/3" trùng với thẻ thống kê; thay bằng hướng dẫn việc cần làm tiếp.
- [ ] 🟡 **B18 — Đăng nhập**: thu gọn "Địa chỉ server / Cổng" vào mục "⚙ Cài đặt server".
- [ ] 🟡 **B19 — Hình nền động chạy 60 khung hình/giây cả khi đang chơi** (`AnimatedStarryBackdrop`): tạm dừng hoặc giảm tốc trong trận.

---

## C. Code, mạng và bảo mật

### Nghiêm trọng
- [ ] 🔴 **C1 — Server nhận object Java không giới hạn**: một client có thể gửi mảng byte cỡ GB làm sập server. Thêm `ObjectInputFilter` (chỉ nhận class trong `common.protocol`, giới hạn kích thước) ở `ClientHandler`.
- [ ] 🔴 **C2 — Mật khẩu lưu dạng chữ thường** trong `user_account`: băm bằng PBKDF2 có salt (`UserDAO`).
- [ ] 🔴 **C3 — Client gửi tin có thể sai thứ tự**: `AuthController.sendAuthenticated` tạo một thread cho mỗi tin. Dùng một `SingleThreadExecutor` chung.

### Nên sửa
- [ ] 🟠 **C4 — Một client treo làm đứng cả phòng**: server ghi socket trong lúc giữ khóa phòng. Mỗi client nên có hàng đợi gửi và thread ghi riêng.
- [ ] 🟠 **C5 — Heartbeat / timeout**: mất mạng là bị khóa đăng nhập (`USER_ALREADY_ONLINE`) cho tới khi TCP tự phát hiện. Client gửi PING định kỳ, server đặt `setSoTimeout` khoảng 30 giây.
- [ ] 🟠 **C6 — Ghi MySQL trong lúc giữ khóa phòng** (`GameManager.persist`): MySQL chậm thì game giật.
- [ ] 🟠 **C7 — Cảnh báo cập nhật đồng bộ**: giao thức đã thêm `HINT`, `KICK_PLAYER`, `KICKED`. Client cũ kết nối vào server mới sẽ bị ngắt. Nhắc cả nhóm pull code mới.

### Nhỏ
- [ ] 🟡 **C8 — Cổng server cố định 5555** (`ServerMain.PORT`): cho cấu hình qua biến môi trường hoặc tham số.
- [ ] 🟡 **C9 — Không giới hạn số thread / kết nối** trên server.
- [ ] 🟡 **C10 — Không có pool kết nối DB**; `DBConnection` đọc lại file cấu hình mỗi lần mở kết nối.
- [ ] 🟡 **C11 — Định dạng code không đồng nhất**: `CatalogDAO` / `HistoryDAO` viết dồn nhiều lệnh trên một dòng.
- [ ] 🟡 **C12 — Tài liệu**: ghi "JDK 24 trở lên" (JDK 25 chạy được); ghi rõ ADMIN không vào phòng chơi.

---

## Thứ tự đề xuất

1. B1, B2: lỗi dễ thấy nhất khi demo.
2. B9, B11, A2: mỗi mục chỉ sửa vài dòng.
3. B3, A5, A4: tránh mất trận hoặc phải chờ vô ích.
4. C1, C3, C5: ổn định khi chơi qua LAN.
5. B4, B5, A7, A9: làm game sôi động và hoàn thiện hơn.
6. Các mục 🟡 còn lại khi có thời gian.
