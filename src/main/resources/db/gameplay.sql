-- Chạy sau registration.sql trong MySQL Workbench.
-- Khởi tạo 20 chủ đề, mỗi chủ đề 8 từ khóa; có thể chạy lại.
CREATE DATABASE IF NOT EXISTS drawguess_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE drawguess_db;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS category (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    UNIQUE KEY uq_category_name (name)
);

CREATE TABLE IF NOT EXISTS word (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_id INT NOT NULL,
    content VARCHAR(100) NOT NULL,
    difficulty VARCHAR(20) NOT NULL DEFAULT 'EASY',
    UNIQUE KEY uq_category_word (category_id, content),
    CONSTRAINT fk_word_category FOREIGN KEY (category_id) REFERENCES category(id)
);

INSERT IGNORE INTO category (name) VALUES
    ('Động vật'),
    ('Đồ ăn'),
    ('Phương tiện'),
    ('Thiên nhiên'),
    ('Nghề nghiệp'),
    ('Trái cây'),
    ('Rau củ'),
    ('Đồ gia dụng'),
    ('Trường học'),
    ('Nhạc cụ'),
    ('Thể thao'),
    ('Quần áo'),
    ('Cơ thể'),
    ('Công trình'),
    ('Thời tiết'),
    ('Biển'),
    ('Công nghệ'),
    ('Đồ chơi'),
    ('Hình học'),
    ('Lễ hội');

INSERT IGNORE INTO word (category_id, content, difficulty)
SELECT c.id, s.content, 'EASY'
FROM (
    SELECT 'Động vật' AS category_name, 'Con mèo' AS content
    UNION ALL
    SELECT 'Động vật' AS category_name, 'Con chó' AS content
    UNION ALL
    SELECT 'Động vật' AS category_name, 'Con voi' AS content
    UNION ALL
    SELECT 'Động vật' AS category_name, 'Con hổ' AS content
    UNION ALL
    SELECT 'Động vật' AS category_name, 'Con thỏ' AS content
    UNION ALL
    SELECT 'Động vật' AS category_name, 'Con cá' AS content
    UNION ALL
    SELECT 'Động vật' AS category_name, 'Con chim' AS content
    UNION ALL
    SELECT 'Động vật' AS category_name, 'Con rùa' AS content
    UNION ALL
    SELECT 'Đồ ăn' AS category_name, 'Bánh mì' AS content
    UNION ALL
    SELECT 'Đồ ăn' AS category_name, 'Phở' AS content
    UNION ALL
    SELECT 'Đồ ăn' AS category_name, 'Cơm' AS content
    UNION ALL
    SELECT 'Đồ ăn' AS category_name, 'Bánh pizza' AS content
    UNION ALL
    SELECT 'Đồ ăn' AS category_name, 'Bánh kem' AS content
    UNION ALL
    SELECT 'Đồ ăn' AS category_name, 'Kem' AS content
    UNION ALL
    SELECT 'Đồ ăn' AS category_name, 'Trứng rán' AS content
    UNION ALL
    SELECT 'Đồ ăn' AS category_name, 'Mì sợi' AS content
    UNION ALL
    SELECT 'Phương tiện' AS category_name, 'Xe đạp' AS content
    UNION ALL
    SELECT 'Phương tiện' AS category_name, 'Xe máy' AS content
    UNION ALL
    SELECT 'Phương tiện' AS category_name, 'Ô tô' AS content
    UNION ALL
    SELECT 'Phương tiện' AS category_name, 'Xe buýt' AS content
    UNION ALL
    SELECT 'Phương tiện' AS category_name, 'Tàu hỏa' AS content
    UNION ALL
    SELECT 'Phương tiện' AS category_name, 'Máy bay' AS content
    UNION ALL
    SELECT 'Phương tiện' AS category_name, 'Thuyền' AS content
    UNION ALL
    SELECT 'Phương tiện' AS category_name, 'Trực thăng' AS content
    UNION ALL
    SELECT 'Thiên nhiên' AS category_name, 'Cây' AS content
    UNION ALL
    SELECT 'Thiên nhiên' AS category_name, 'Hoa' AS content
    UNION ALL
    SELECT 'Thiên nhiên' AS category_name, 'Núi' AS content
    UNION ALL
    SELECT 'Thiên nhiên' AS category_name, 'Sông' AS content
    UNION ALL
    SELECT 'Thiên nhiên' AS category_name, 'Biển' AS content
    UNION ALL
    SELECT 'Thiên nhiên' AS category_name, 'Thác nước' AS content
    UNION ALL
    SELECT 'Thiên nhiên' AS category_name, 'Rừng' AS content
    UNION ALL
    SELECT 'Thiên nhiên' AS category_name, 'Đảo' AS content
    UNION ALL
    SELECT 'Nghề nghiệp' AS category_name, 'Bác sĩ' AS content
    UNION ALL
    SELECT 'Nghề nghiệp' AS category_name, 'Giáo viên' AS content
    UNION ALL
    SELECT 'Nghề nghiệp' AS category_name, 'Đầu bếp' AS content
    UNION ALL
    SELECT 'Nghề nghiệp' AS category_name, 'Công an' AS content
    UNION ALL
    SELECT 'Nghề nghiệp' AS category_name, 'Lính cứu hỏa' AS content
    UNION ALL
    SELECT 'Nghề nghiệp' AS category_name, 'Nông dân' AS content
    UNION ALL
    SELECT 'Nghề nghiệp' AS category_name, 'Ca sĩ' AS content
    UNION ALL
    SELECT 'Nghề nghiệp' AS category_name, 'Họa sĩ' AS content
    UNION ALL
    SELECT 'Trái cây' AS category_name, 'Quả táo' AS content
    UNION ALL
    SELECT 'Trái cây' AS category_name, 'Quả cam' AS content
    UNION ALL
    SELECT 'Trái cây' AS category_name, 'Quả chuối' AS content
    UNION ALL
    SELECT 'Trái cây' AS category_name, 'Quả xoài' AS content
    UNION ALL
    SELECT 'Trái cây' AS category_name, 'Quả dưa hấu' AS content
    UNION ALL
    SELECT 'Trái cây' AS category_name, 'Quả nho' AS content
    UNION ALL
    SELECT 'Trái cây' AS category_name, 'Quả dứa' AS content
    UNION ALL
    SELECT 'Trái cây' AS category_name, 'Quả dâu tây' AS content
    UNION ALL
    SELECT 'Rau củ' AS category_name, 'Cà rốt' AS content
    UNION ALL
    SELECT 'Rau củ' AS category_name, 'Cà chua' AS content
    UNION ALL
    SELECT 'Rau củ' AS category_name, 'Khoai tây' AS content
    UNION ALL
    SELECT 'Rau củ' AS category_name, 'Bắp cải' AS content
    UNION ALL
    SELECT 'Rau củ' AS category_name, 'Dưa chuột' AS content
    UNION ALL
    SELECT 'Rau củ' AS category_name, 'Bí đỏ' AS content
    UNION ALL
    SELECT 'Rau củ' AS category_name, 'Củ hành' AS content
    UNION ALL
    SELECT 'Rau củ' AS category_name, 'Củ cải' AS content
    UNION ALL
    SELECT 'Đồ gia dụng' AS category_name, 'Bàn' AS content
    UNION ALL
    SELECT 'Đồ gia dụng' AS category_name, 'Ghế' AS content
    UNION ALL
    SELECT 'Đồ gia dụng' AS category_name, 'Giường' AS content
    UNION ALL
    SELECT 'Đồ gia dụng' AS category_name, 'Tủ' AS content
    UNION ALL
    SELECT 'Đồ gia dụng' AS category_name, 'Đèn' AS content
    UNION ALL
    SELECT 'Đồ gia dụng' AS category_name, 'Quạt' AS content
    UNION ALL
    SELECT 'Đồ gia dụng' AS category_name, 'Tủ lạnh' AS content
    UNION ALL
    SELECT 'Đồ gia dụng' AS category_name, 'Máy giặt' AS content
    UNION ALL
    SELECT 'Trường học' AS category_name, 'Bút chì' AS content
    UNION ALL
    SELECT 'Trường học' AS category_name, 'Bút mực' AS content
    UNION ALL
    SELECT 'Trường học' AS category_name, 'Thước kẻ' AS content
    UNION ALL
    SELECT 'Trường học' AS category_name, 'Quyển sách' AS content
    UNION ALL
    SELECT 'Trường học' AS category_name, 'Quyển vở' AS content
    UNION ALL
    SELECT 'Trường học' AS category_name, 'Cặp sách' AS content
    UNION ALL
    SELECT 'Trường học' AS category_name, 'Bảng' AS content
    UNION ALL
    SELECT 'Trường học' AS category_name, 'Cục tẩy' AS content
    UNION ALL
    SELECT 'Nhạc cụ' AS category_name, 'Đàn piano' AS content
    UNION ALL
    SELECT 'Nhạc cụ' AS category_name, 'Đàn guitar' AS content
    UNION ALL
    SELECT 'Nhạc cụ' AS category_name, 'Trống' AS content
    UNION ALL
    SELECT 'Nhạc cụ' AS category_name, 'Sáo' AS content
    UNION ALL
    SELECT 'Nhạc cụ' AS category_name, 'Đàn violin' AS content
    UNION ALL
    SELECT 'Nhạc cụ' AS category_name, 'Kèn' AS content
    UNION ALL
    SELECT 'Nhạc cụ' AS category_name, 'Đàn organ' AS content
    UNION ALL
    SELECT 'Nhạc cụ' AS category_name, 'Đàn tranh' AS content
    UNION ALL
    SELECT 'Thể thao' AS category_name, 'Bóng đá' AS content
    UNION ALL
    SELECT 'Thể thao' AS category_name, 'Bóng rổ' AS content
    UNION ALL
    SELECT 'Thể thao' AS category_name, 'Bóng chuyền' AS content
    UNION ALL
    SELECT 'Thể thao' AS category_name, 'Cầu lông' AS content
    UNION ALL
    SELECT 'Thể thao' AS category_name, 'Bơi lội' AS content
    UNION ALL
    SELECT 'Thể thao' AS category_name, 'Chạy bộ' AS content
    UNION ALL
    SELECT 'Thể thao' AS category_name, 'Bóng bàn' AS content
    UNION ALL
    SELECT 'Thể thao' AS category_name, 'Cờ vua' AS content
    UNION ALL
    SELECT 'Quần áo' AS category_name, 'Áo sơ mi' AS content
    UNION ALL
    SELECT 'Quần áo' AS category_name, 'Áo phông' AS content
    UNION ALL
    SELECT 'Quần áo' AS category_name, 'Quần dài' AS content
    UNION ALL
    SELECT 'Quần áo' AS category_name, 'Váy' AS content
    UNION ALL
    SELECT 'Quần áo' AS category_name, 'Mũ' AS content
    UNION ALL
    SELECT 'Quần áo' AS category_name, 'Giày' AS content
    UNION ALL
    SELECT 'Quần áo' AS category_name, 'Tất' AS content
    UNION ALL
    SELECT 'Quần áo' AS category_name, 'Khăn quàng' AS content
    UNION ALL
    SELECT 'Cơ thể' AS category_name, 'Mắt' AS content
    UNION ALL
    SELECT 'Cơ thể' AS category_name, 'Tai' AS content
    UNION ALL
    SELECT 'Cơ thể' AS category_name, 'Mũi' AS content
    UNION ALL
    SELECT 'Cơ thể' AS category_name, 'Miệng' AS content
    UNION ALL
    SELECT 'Cơ thể' AS category_name, 'Tay' AS content
    UNION ALL
    SELECT 'Cơ thể' AS category_name, 'Chân' AS content
    UNION ALL
    SELECT 'Cơ thể' AS category_name, 'Tóc' AS content
    UNION ALL
    SELECT 'Cơ thể' AS category_name, 'Răng' AS content
    UNION ALL
    SELECT 'Công trình' AS category_name, 'Ngôi nhà' AS content
    UNION ALL
    SELECT 'Công trình' AS category_name, 'Cầu' AS content
    UNION ALL
    SELECT 'Công trình' AS category_name, 'Trường học' AS content
    UNION ALL
    SELECT 'Công trình' AS category_name, 'Bệnh viện' AS content
    UNION ALL
    SELECT 'Công trình' AS category_name, 'Lâu đài' AS content
    UNION ALL
    SELECT 'Công trình' AS category_name, 'Chùa' AS content
    UNION ALL
    SELECT 'Công trình' AS category_name, 'Tòa nhà' AS content
    UNION ALL
    SELECT 'Công trình' AS category_name, 'Sân vận động' AS content
    UNION ALL
    SELECT 'Thời tiết' AS category_name, 'Mặt trời' AS content
    UNION ALL
    SELECT 'Thời tiết' AS category_name, 'Mây' AS content
    UNION ALL
    SELECT 'Thời tiết' AS category_name, 'Mưa' AS content
    UNION ALL
    SELECT 'Thời tiết' AS category_name, 'Sấm sét' AS content
    UNION ALL
    SELECT 'Thời tiết' AS category_name, 'Cầu vồng' AS content
    UNION ALL
    SELECT 'Thời tiết' AS category_name, 'Tuyết' AS content
    UNION ALL
    SELECT 'Thời tiết' AS category_name, 'Gió' AS content
    UNION ALL
    SELECT 'Thời tiết' AS category_name, 'Bão' AS content
    UNION ALL
    SELECT 'Biển' AS category_name, 'Cá mập' AS content
    UNION ALL
    SELECT 'Biển' AS category_name, 'Cá heo' AS content
    UNION ALL
    SELECT 'Biển' AS category_name, 'Cá voi' AS content
    UNION ALL
    SELECT 'Biển' AS category_name, 'Bạch tuộc' AS content
    UNION ALL
    SELECT 'Biển' AS category_name, 'Sứa' AS content
    UNION ALL
    SELECT 'Biển' AS category_name, 'Sao biển' AS content
    UNION ALL
    SELECT 'Biển' AS category_name, 'Cua' AS content
    UNION ALL
    SELECT 'Biển' AS category_name, 'San hô' AS content
    UNION ALL
    SELECT 'Công nghệ' AS category_name, 'Máy tính' AS content
    UNION ALL
    SELECT 'Công nghệ' AS category_name, 'Điện thoại' AS content
    UNION ALL
    SELECT 'Công nghệ' AS category_name, 'Bàn phím' AS content
    UNION ALL
    SELECT 'Công nghệ' AS category_name, 'Chuột máy tính' AS content
    UNION ALL
    SELECT 'Công nghệ' AS category_name, 'Tai nghe' AS content
    UNION ALL
    SELECT 'Công nghệ' AS category_name, 'Máy ảnh' AS content
    UNION ALL
    SELECT 'Công nghệ' AS category_name, 'Tivi' AS content
    UNION ALL
    SELECT 'Công nghệ' AS category_name, 'Robot' AS content
    UNION ALL
    SELECT 'Đồ chơi' AS category_name, 'Búp bê' AS content
    UNION ALL
    SELECT 'Đồ chơi' AS category_name, 'Gấu bông' AS content
    UNION ALL
    SELECT 'Đồ chơi' AS category_name, 'Diều' AS content
    UNION ALL
    SELECT 'Đồ chơi' AS category_name, 'Bóng bay' AS content
    UNION ALL
    SELECT 'Đồ chơi' AS category_name, 'Con quay' AS content
    UNION ALL
    SELECT 'Đồ chơi' AS category_name, 'Ô tô đồ chơi' AS content
    UNION ALL
    SELECT 'Đồ chơi' AS category_name, 'Ngựa gỗ' AS content
    UNION ALL
    SELECT 'Đồ chơi' AS category_name, 'Xếp hình' AS content
    UNION ALL
    SELECT 'Hình học' AS category_name, 'Hình tròn' AS content
    UNION ALL
    SELECT 'Hình học' AS category_name, 'Hình vuông' AS content
    UNION ALL
    SELECT 'Hình học' AS category_name, 'Hình tam giác' AS content
    UNION ALL
    SELECT 'Hình học' AS category_name, 'Hình chữ nhật' AS content
    UNION ALL
    SELECT 'Hình học' AS category_name, 'Hình thoi' AS content
    UNION ALL
    SELECT 'Hình học' AS category_name, 'Hình cầu' AS content
    UNION ALL
    SELECT 'Hình học' AS category_name, 'Hình lập phương' AS content
    UNION ALL
    SELECT 'Hình học' AS category_name, 'Hình nón' AS content
    UNION ALL
    SELECT 'Lễ hội' AS category_name, 'Ông già Noel' AS content
    UNION ALL
    SELECT 'Lễ hội' AS category_name, 'Đèn lồng' AS content
    UNION ALL
    SELECT 'Lễ hội' AS category_name, 'Bánh chưng' AS content
    UNION ALL
    SELECT 'Lễ hội' AS category_name, 'Pháo hoa' AS content
    UNION ALL
    SELECT 'Lễ hội' AS category_name, 'Mặt nạ' AS content
    UNION ALL
    SELECT 'Lễ hội' AS category_name, 'Bí ngô Halloween' AS content
    UNION ALL
    SELECT 'Lễ hội' AS category_name, 'Hoa đào' AS content
    UNION ALL
    SELECT 'Lễ hội' AS category_name, 'Cây thông Noel' AS content
) AS s
JOIN category c ON c.name = s.category_name;
