# Kế hoạch cải thiện — Draw & Guess

Danh sách tổng hợp từ các lần rà soát: **code / bảo mật**, **luật chơi**, **UI/UX**, **hiệu năng**.
Đánh dấu `[x]` khi đã làm xong, ghi commit bên cạnh để dễ tra lại.

**Trạng thái nhánh:** `dev_vu` đã push lên GitHub tới commit `ae149e5`, chờ mở Pull Request vào `main`.

**Mốc quay lại:** tag `moc-truoc-cai-thien-ui` (commit `7d61be5`, trước phần cải thiện UI). Tag này mới chỉ có trên máy (`git push origin moc-truoc-cai-thien-ui` để đưa lên).
Vì các commit sau mốc đã được push, muốn bỏ chúng thì dùng `git revert <commit>` (tạo commit đảo ngược), **không** dùng `git reset --hard` rồi force push.

Mức độ: 🔴 nghiêm trọng · 🟠 nên sửa · 🟡 nhỏ / trau chuốt

---

## Tiến độ

| Nhóm | Đã xong | Tổng |
|---|---|---|
| A. Luật chơi | 5 | 14 |
| B. UI/UX | 20 | 22 |
| C. Code, mạng, bảo mật | 0 | 12 |
| D. Hiệu năng | 3 | 3 |
| E. Chạy thử, tài liệu | 2 | 2 |
| F. Rà soát UI/UX theo ui-ux-pro-max | 9 | 12 |
| **Tổng** | **39** | **65** |

### Lịch sử commit trên nhánh `dev_vu`

| Commit | Nội dung |
|---|---|
| `7d61be5` | A8 gợi ý từ khóa, A11 chủ phòng/kick, A12 hoàn tác khi vẽ |
| `f1897f9` | Tạo file kế hoạch này |
| `ae149e5` | B1–B19 (UI/UX), D1–D3 (hiệu năng), E1–E2 (server demo, hướng dẫn chạy) |

---

## A. Luật chơi và cách chơi

- [x] **A8 — Gợi ý từ khóa** `_ _ _   _ _ _`, mở 1 chữ cái sau 15 giây — `7d61be5`
- [x] **A11 — Chủ phòng và kick**: người vào đầu là chủ phòng, có nút "Mời ra" — `7d61be5`. *Đã bỏ chế độ tự đưa người chưa sẵn sàng ra sau 60 giây: chỉ chủ phòng mới mời ra được.*
- [x] **A12 — Hoàn tác khi vẽ**: nút ↶ Hoàn tác / Ctrl+Z, khôi phục cả tranh lỡ bấm "Xóa tranh" (tối đa 20 bước) — `7d61be5`
- [ ] 🔴 **A1 — Chọn chủ đề**: README ghi "chọn chủ đề" nhưng server chọn ngẫu nhiên (`WordDAO.selectWords`). Thêm chọn/bình chọn chủ đề hoặc sửa README.
- [ ] 🔴 **A2 — Chuẩn hóa đáp án**: so sánh sau NFC + gộp khoảng trắng (`GameManager.normalize`). Quyết định có chấp nhận gõ không dấu hay không.
- [x] 🔴 **A3 — Chống viết chữ lên tranh**: người vẽ được +1 cho mỗi người đoán đúng, nên viết chữ là có lợi. Ghi luật trên màn hình vẽ, giới hạn điểm người vẽ khi *tất cả* đều đoán đúng, hoặc thêm nút báo cáo. — chưa commit — đã làm: nút **Báo cáo tranh** trong phiên đoán (cả lúc công bố đáp án); quá nửa người đoán đang online báo cáo thì người vẽ mất điểm đã nhận ở vòng đó và không được cộng thêm, người đoán giữ điểm. Báo cáo chưa được lưu vào lịch sử trận (chỉ điểm cuối cùng được lưu).
- [ ] 🔴 **A4 — Bỏ qua vòng có tranh trắng** (không vẽ, mất kết nối, gửi lỗi) thay vì bắt cả phòng chờ 30 giây.
- [ ] 🔴 **A5 — Kết thúc trận khi còn dưới 2 người online** (`GameManager.disconnect`).
- [ ] 🟠 **A6 — Điểm theo tốc độ**: ví dụ đúng trong 10 giây đầu +3, 10 giây tiếp +2, sau đó +1.
- [ ] 🟠 **A7 — Thông báo "X đã đoán đúng!"** cho cả phòng (không lộ đáp án).
- [ ] 🟠 **A9 — Nút "Nộp sớm"**: server nhận tranh trong pha DRAWING; ai cũng nộp thì chuyển sang đoán ngay.
- [ ] 🟠 **A10 — Đếm ngược 3–5 giây trước khi vào trận**, cho phép hủy sẵn sàng trong lúc đếm.
- [x] 🟠 **A14 — Mời người đang online vào phòng** — chưa commit — đã làm: nút "Mời người chơi" ở phòng chờ mở danh sách người online kèm trạng thái; ai trong phòng cũng mời được người đang rảnh; người được mời có hộp thoại Vào phòng / Từ chối (hết hạn sau 30 giây) và người mời được báo khi bị từ chối. Server chặn mời lại cùng một người trong 10 giây, không mời khi phòng đang chơi / đủ người hoặc người đó đã bị chủ phòng mời ra.
- [ ] 🟡 **A13 — Dùng cột `difficulty`** (EASY/MEDIUM/HARD): từ khó nhiều điểm hơn hoặc chia đều độ khó.

> Ghi chú: mọi người vẽ từ khác nhau trong cùng chủ đề, nên ai cũng loại trừ được từ của chính mình. Mất kết nối giữa trận thì không vào lại được. Hai điểm này chấp nhận được, chỉ cần ghi vào luật.

---

## B. Giao diện và trải nghiệm (UI/UX)

### Màn hình chơi game
- [x] 🔴 **B1 — Thanh công cụ vẽ bị cắt chữ** ở cửa sổ khoảng 1000px ("…", "↶ Hoàn …", "Xóa tra…"). Chia 2 hàng hoặc dùng nút icon kèm tooltip. *Nút Hoàn tác (A12) làm lỗi này nặng thêm.* — `ae149e5` — đã làm: 2 hàng công cụ, nút không còn bị cắt chữ
- [x] 🔴 **B2 — Không biết nút Tẩy đang bật hay tắt**: thêm `.toggle-button:selected` trong `app.css`, đổi chữ thành "Đang tẩy". — `ae149e5` — đã làm: nền vàng + chữ "✓ Đang tẩy"
- [x] 🔴 **B3 — Hỏi xác nhận khi bấm "Rời phòng" / "Đăng xuất" trong trận**; đưa hai nút ra xa ô đoán. — `ae149e5` — đã làm: hỏi xác nhận; nút chuyển lên thanh tiêu đề
- [x] 🟠 **B4 — Bảng điểm**: thêm avatar, tô đậm dòng của mình, đánh dấu người đang vẽ, ✓ cho người đã đoán đúng. — `ae149e5` — đã làm: avatar, "• Bạn"; pha vẽ hiện "Đang vẽ", pha đoán hiện "Người vẽ" / "Đang đoán" / "✓ Đoán đúng"
- [x] 🟠 **B5 — Màn hình kết quả**: làm nổi bật người thắng (bục 1-2-3, vương miện). — `ae149e5` — đã làm: bục 1-2-3 + "★ Người thắng"
- [x] 🟠 **B6 — Đồng hồ đổi màu đỏ / nhấp nháy khi còn ≤ 10 giây.** — `ae149e5` — đã làm: chip đỏ + nhịp đập khi ≤ 10 giây
- [x] 🟠 **B7 — Dòng trạng thái và placeholder ô đoán bị cắt**: cho tự xuống dòng, rút gọn placeholder. — `ae149e5` — đã làm: dòng trạng thái tự xuống dòng, placeholder "Nhập đáp án..."
- [x] 🟡 **B8 — Phản hồi khi đoán**: ô nhập nháy xanh/đỏ, rung nhẹ khi sai, có thể thêm âm thanh. — `ae149e5` — đã làm: nháy xanh/đỏ + rung; **chưa có âm thanh** (cần thêm thư viện javafx-media)

### Điều hướng và thao tác
- [x] 🟠 **B9 — Nút "Về danh sách phòng" ở Lịch sử thực ra về trang chủ** (`HistoryView.java:35`). Đổi thành "Về trang chủ". — `ae149e5` — đã làm
- [x] 🟠 **B10 — Nhấp đúp để vào phòng** và bộ lọc "Chỉ hiện phòng vào được" (`RoomListView`). — `ae149e5` — đã làm: nhấp đúp / Enter; bộ lọc bật sẵn
- [x] 🟠 **B11 — Viền focus cho nút** khi dùng phím Tab: `.button:focused { -fx-border-color: #1d4ed8; -fx-border-width: 2; }`. — `ae149e5` — đã làm: dùng `:focus-visible`, chỉ hiện khi bấm Tab
- [x] 🟠 **B12 — Màn hình đăng ký không cho biết đang đăng ký vào server nào**: hiện địa chỉ server hoặc thêm ô nhập. — `ae149e5` — đã làm

### Chi tiết và trau chuốt
- [x] 🟡 **B13 — Chữ quá nhỏ** (9–11px ở `app.css` dòng 210, 470, 550…): nâng chữ thường lên tối thiểu khoảng 12px. — `ae149e5` — đã làm
- [x] 🟡 **B14 — Chữ tiếng Anh lẫn vào**: "Black" ở ô chọn màu; EASY/MEDIUM/HARD → Dễ / Trung bình / Khó. — `ae149e5` — đã làm
- [x] 🟡 **B15 — Admin**: thêm nhãn cho ô nhập, đổi "Sửa mục chọn" cho rõ nghĩa, tách "Đăng xuất" khỏi "Làm mới", hiện lỗi gần chỗ thao tác. — `ae149e5` — đã làm: nút "Thêm / Cập nhật / Xóa" kèm tooltip
- [x] 🟡 **B16 — Lịch sử**: danh sách trận quá thấp, ảnh tranh bị cắt và nhỏ, phải cuộn. — `ae149e5` — đã làm: danh sách và chi tiết chia theo chiều dọc, kéo được; ảnh to theo chiều rộng khung
- [x] 🟡 **B17 — Phòng chờ**: dòng "Sẵn sàng: 2/3" trùng với thẻ thống kê; thay bằng hướng dẫn việc cần làm tiếp. — `ae149e5` — đã làm
- [x] 🟡 **B18 — Đăng nhập**: thu gọn "Địa chỉ server / Cổng" vào mục "⚙ Cài đặt server". — `ae149e5` — đã làm: tự mở lại khi không kết nối được
- [x] 🟡 **B19 — Hình nền động chạy 60 khung hình/giây cả khi đang chơi** (`AnimatedStarryBackdrop`): tạm dừng hoặc giảm tốc trong trận. — `ae149e5` — đã làm
- [ ] 🟡 **B20 — Âm thanh khi đoán đúng / sai / hết giờ** (phần còn lại của B8): cần thêm thư viện `javafx-media` vào `pom.xml` và file âm thanh ngắn.
- [x] 🟠 **B22 — "Người đang online" ở màn hình Chọn phòng chơi đếm sai**: client chỉ cộng người đang ở trong phòng, nên không tính chính mình và những người đang ở trang chủ / danh sách phòng. Đã sửa: server gửi `ONLINE_COUNT` (số tài khoản đang đăng nhập, từ `SessionManager`) kèm danh sách phòng — chưa commit
- [ ] 🟡 **B21 — Nhãn "Địa chỉ server"** ở màn hình đăng nhập chưa thống nhất với chữ "máy chủ" dùng ở chỗ khác. Đổi thành "Địa chỉ máy chủ" (`LoginView`, 1 dòng).

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
- [ ] 🟠 **C7 — Cảnh báo cập nhật đồng bộ**: giao thức đã thêm `HINT`, `KICK_PLAYER`, `KICKED`, `ONLINE_COUNT`, trường chủ phòng trong `RoomInfo`, đếm ngược trong `PlayerInfo` và cờ "đã đoán đúng" trong `ScoreEntry`. Client cũ kết nối vào server mới sẽ bị ngắt hoặc hiển thị thiếu. Khi merge `dev_vu`, nhắc cả nhóm pull code mới cùng lúc.

### Nhỏ
- [ ] 🟡 **C8 — Cổng server cố định 5555** (`ServerMain.PORT`): cho cấu hình qua biến môi trường hoặc tham số.
- [ ] 🟡 **C9 — Không giới hạn số thread / kết nối** trên server.
- [ ] 🟡 **C10 — Không có pool kết nối DB**; `DBConnection` đọc lại file cấu hình mỗi lần mở kết nối.
- [ ] 🟡 **C11 — Định dạng code không đồng nhất**: `CatalogDAO` / `HistoryDAO` viết dồn nhiều lệnh trên một dòng.
- [ ] 🟡 **C12 — Tài liệu**: ghi "JDK 24 trở lên" (JDK 25 chạy được); ghi rõ ADMIN không vào phòng chơi.

---

## D. Hiệu năng (giật khi di chuột / khi vẽ)

Đo trên máy 1280×800, phóng 200% (JavaFX vẽ 2560×1600), GPU Direct3D, cửa sổ toàn màn hình.
Mức mượt là ≤ 16.7 ms/khung (60 FPS). Mỗi số là trung bình của 2 lần chạy, mỗi lần một JVM mới.
Công cụ đo: `mvn -q "-Ddrawguess.perf=true" "-Dtest=PerfProbeTest" "-Dperf.scenario=menu-move" test`
(tình huống: `menu-idle`, `menu-move`, `draw-long`, `draw-short`; kết quả nằm trong `target/perf.txt`).
Màn hình phải đang bật và cửa sổ đo không bị che, nếu không số đo sẽ sai.

- [x] **D1 — Cache khung nội dung sau nền động** (`AnimatedStarryBackdrop.followContent`) — `ae149e5`: menu 68 ms (15 FPS) → **12 ms (83 FPS)**. Đã kiểm tra chữ vẫn sắc nét ở 200%.
- [x] **D2 — Tách bóng đổ khỏi khung chứa canvas** (lớp `canvas-frame` riêng, được cache) — `ae149e5`: vẽ nét dài 22.5 → 17.6 ms; nét ngắn 30 → 18.5 ms.
- [x] **D3 — Hoàn tác lưu theo nét vẽ, không chụp ảnh canvas mỗi lần nhấn chuột** — `ae149e5`: khung > 25 ms khi vẽ nét ngắn 75 → 18 (trên 239 khung); trung bình ~17 ms (~59 FPS).

| Thử nghiệm | Trước → Sau | Kết luận |
|---|---|---|
| D1 cache khung nội dung | Menu 68 ms → 12 ms | Giữ |
| D2 tách và cache bóng đổ khung vẽ | Vẽ 22.5 / 30 ms → 17.6 / 18.5 ms | Giữ |
| D3 hoàn tác theo nét | Khung chậm khi vẽ nét ngắn 75 → 18 | Giữ |
| Pulse logger ngưỡng 0 (`-Djavafx.pulseLogger.threshold=0`) | JavaFX tự lỗi NullPointerException | Không dùng để đo |

---

## E. Chạy thử và tài liệu

- [x] **E1 — Server chạy thử không cần MySQL** (`server/demo/DemoServerMain`, `DemoStore`): tài khoản `demo1`–`demo4` / `123`, `admin` / `admin`, 3 chủ đề mẫu; lịch sử, xem tranh và Admin đều chạy được — `ae149e5`
- [x] **E2 — Hướng dẫn chạy demo** (`docs/HUONG_DAN_CHAY_DEMO.md` và mục "Chạy thử không cần MySQL" trong README) — `ae149e5`

---

## F. Rà soát lại UI/UX bằng skill `ui-ux-pro-max`

Rà soát ngày 24/10/2026 theo `quick-reference.md` (98 quy tắc, 10 nhóm) và các hướng dẫn riêng cho JavaFX (`--stack javafx`).
Độ tương phản tính theo công thức WCAG từ màu trong `app.css`; bố cục đo ở kích thước cửa sổ laptop **1280×721** (1280×800 trừ taskbar và thanh tiêu đề).

**Đã đạt:** 32/35 cặp màu chữ–nền đạt 4.5:1; có viền focus khi dùng Tab; điều khiển bằng bàn phím (thẻ trang chủ và avatar đều là nút thật); hỏi xác nhận trước thao tác nguy hiểm; trạng thái đang tải / rỗng / lỗi đều có chữ; không dùng màu làm tín hiệu duy nhất; nút bị vô hiệu hóa có độ mờ rõ ràng; phong cách thống nhất.

- [x] 🟠 **F1 — Một số màn hình cao hơn cửa sổ ở 1280×721** nên mép trên/dưới bị cắt vài pixel: game cần ~804–824px, phòng chờ ~772px, đăng ký ~753px. Giảm khoảng đệm/ảnh trang trí, hoặc cho cuộn khi cửa sổ thấp. *(layout, `min/pref size`)* — chưa commit — đã làm: thanh tiêu đề game còn 2 dòng (chủ đề và từ khóa chung một dòng), thanh công cụ và phòng chờ gọn hơn, khoảng đệm quanh nội dung tự giảm từ 24 xuống 8px khi cửa sổ thấp hơn 800px. Đo ở 1280×721: trước đó game tràn 38–66px, phòng chờ tràn 15px; sau khi sửa cả 7 màn hình đều vừa
- [ ] 🟠 **F2 — Chưa có tùy chọn giảm chuyển động**: nền động (sao lấp lánh, parallax theo chuột) chạy liên tục chỉ để trang trí. Thêm công tắc "Giảm hiệu ứng" để tắt nền động và các hiệu ứng chuyển cảnh. *(reduced-motion, parallax-subtle)*
- [ ] 🟠 **F3 — Màu viết thẳng mã hex ở 148 chỗ trong `app.css`**: chưa dùng biến màu (looked-up colors) khai báo một lần trên `.root`. Đổi màu chủ đạo hay kiểm tra tương phản phải sửa rất nhiều chỗ. *(color-semantic, JavaFX "looked-up colors")*
- [x] 🟠 **F4 — Hộp thoại xác nhận xóa ở màn hình Quản trị chưa gắn cửa sổ cha** (`AdminView`, thiếu `initOwner`): có thể hiện ra ở giữa màn hình khác hoặc nằm sau cửa sổ game. *(modal ownership)* — chưa commit — đã làm: gắn `initOwner`, thêm tiêu đề hộp thoại
- [x] 🟡 **F5 — Ô số phòng gần như biến mất khi dòng phòng đang được chọn**: nền ô `#173f84` trên nền dòng `#1d4f91`, tương phản 1.24:1 (cần 3:1). *(non-text contrast)* — chưa commit — đã làm: ô số phòng chuyển nền vàng trên dòng đang chọn (6.31:1)
- [x] 🟡 **F6 — Chữ gợi ý mờ trong ô nhập (placeholder) chỉ đạt 3.37:1** (`#8b8b80` trên `#fffdf2`, cần 4.5:1). *(color-contrast)* — chưa commit — đã làm: đổi sang `#716e66` (4.99:1), vẫn nhạt hơn chữ người dùng gõ
- [ ] 🟡 **F7 — Tên tranh avatar ở trang chủ đạt 4.48:1**, sát dưới ngưỡng 4.5:1 (`.avatar-name #9a6a08`). *(color-contrast)*
- [x] 🟡 **F8 — Vùng bấm nhỏ**: ô chọn màu cao 20px và ô "Chỉ hiện phòng còn chỗ" 20px, dưới mức tối thiểu 24×24px của WCAG 2.5.8; thanh trượt nét vẽ 23px; các liên kết và nút "Mời ra" 24px (vừa chạm ngưỡng). *(touch-target-size)* — chưa commit — đã làm: ô chọn màu cao 34px, ô lọc phòng ~32px, thanh trượt có núm và vùng bấm lớn hơn, liên kết và nút "Mời ra" ~28px
- [x] 🟡 **F9 — Nhãn chưa liên kết với ô nhập** (không có `setLabelFor` nào): trình đọc màn hình không đọc được tên ô. *(form-labels, JavaFX "Connect labels to inputs")* — chưa commit — đã làm: mọi nhãn ở đăng nhập, đăng ký, quản trị và ô "Nét" đều `setLabelFor`; ô không có nhãn hiển thị (ô đoán, chọn màu, chọn vòng ở lịch sử, dấu đã sẵn sàng) có `accessibleText`
- [x] 🟡 **F10 — Không có nút hiện/ẩn mật khẩu** ở đăng nhập và đăng ký. *(password-toggle)* — chưa commit — đã làm: `PasswordInput` có nút con mắt 34×34px ở cuối ô (có tooltip và tên cho trình đọc màn hình), giữ nguyên chữ và vị trí con trỏ khi bật/tắt; xóa mật khẩu thì tự che lại
- [x] 🟡 **F11 — Biểu tượng là ký tự chữ** (★ ✓ ↶ ✦), kích thước và nét không đồng đều (↶ hiển thị nhỏ). Nên dùng một bộ icon vector thống nhất, ví dụ thư viện Ikonli. *(no-emoji-icons, icon-style-consistent)* — chưa commit — đã làm: bộ icon `Icons` vẽ bằng `SVGPath` (lưới 24×24, nét 2px, đầu tròn, kiểu Lucide/Feather), tự lấy màu theo màu chữ; không thêm thư viện. Thay ★ ✓ ↶ ✦ → ở hoàn tác, tẩy, bảng điểm, kết quả, phòng chờ, lịch sử, trang chủ và logo
- [x] 🟡 **F12 — Lỗi nhập liệu ở đăng nhập/đăng ký hiện ở ô thông báo cuối form**, không nằm ngay dưới ô bị sai và không tự đưa con trỏ về ô đó. *(error-placement, focus-management)* — chưa commit — đã làm: `FormField` hiện lỗi (icon + chữ đỏ) ngay dưới ô, viền ô chuyển đỏ, con trỏ về ô sai đầu tiên; kiểm tra mọi ô cùng lúc (cả địa chỉ và cổng máy chủ); lỗi tự ẩn khi sửa ô; lỗi "tên đã tồn tại" từ server cũng hiện dưới ô tên đăng nhập

> Ghi chú: bộ gợi ý thiết kế của skill (`--design-system`) đề xuất phong cách Claymorphism hồng – vàng cho game vẽ vui nhộn. Giao diện hiện tại theo chủ đề tranh Van Gogh đã có nét riêng, nhất quán và hợp với game (bo góc lớn, viền dày, màu ấm), nên **không cần đổi phong cách**; chỉ sửa các điểm F1–F12.

## Thứ tự đề xuất cho phần còn lại

Đã xong toàn bộ nhóm B (trừ hai mục nhỏ B20, B21), D và E. Việc còn lại nên làm theo thứ tự:

1. **A2, A5, A4**: sửa nhỏ ở `GameManager`, tránh chấm sai đáp án tiếng Việt, trận chỉ còn 1 người và phải chờ tranh trắng.
2. **C1, C3, C5**: ổn định khi chơi qua LAN (chặn gói tin quá lớn, giữ đúng thứ tự tin nhắn, phát hiện mất kết nối).
3. **A1**: chọn chủ đề, vì README đang mô tả chức năng này.
4. **A7, A9, A10**: làm trận đấu sôi động hơn (thông báo người đoán đúng, nộp tranh sớm, đếm ngược trước trận).
5. **C2**: băm mật khẩu (cần đặt lại mật khẩu hoặc chuyển đổi tài khoản cũ).
6. **C7**: nhắc cả nhóm cập nhật code cùng lúc khi merge.
7. Các mục 🟡 còn lại khi có thời gian.
