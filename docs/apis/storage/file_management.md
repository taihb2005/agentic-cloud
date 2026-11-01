# Nhóm API Tải lên & Tải xuống Tệp (Storage Service)

Tài liệu này mô tả chi tiết các RESTful API phục vụ luồng upload và download file thông qua cơ chế Presigned URL của hệ thống lưu trữ MinIO/S3, giúp client truyền nhận dữ liệu trực tiếp với object storage mà không làm nghẽn băng thông của backend. Dữ liệu được đồng bộ trực tiếp từ source code các controller (`FileUploadController`, `FileDownloadController`) và các DTO liên quan.

---

## 1. Cơ chế Hoạt động (Presigned URL Pattern)

### 1.1. Luồng Tải lên (Upload Flow)
1. **Client** gửi danh sách thông tin tệp cần upload (tên, kích thước, hash SHA-256) tới `POST /storage-service/files/upload-urls`.
2. **Backend**:
   - Kiểm tra Quota của User. Nếu không đủ dung lượng, trả lỗi `400 Bad Request`.
   - Kiểm tra **Deduplication** dựa trên `hash`: nếu tệp đã tồn tại trên MinIO, hệ thống tạo metadata trỏ tới object sẵn có và trả về `instant_uploaded = true` (không cần đẩy file lên MinIO nữa).
   - Nếu là tệp mới: Tạo bản ghi file node trong DB ở trạng thái `PENDING_UPLOAD` và tạo Presigned URL (HTTP `PUT`).
3. **Client** thực hiện request `PUT` trực tiếp lên Presigned URL để đẩy file lên MinIO.
4. **MinIO** phát sinh sự kiện `s3:ObjectCreated:*` đẩy vào topic Kafka; service lưu trữ lắng nghe sự kiện để kích hoạt chuyển đổi trạng thái node sang `ACTIVE` và tính toán hạn mức quota chính xác.

### 1.2. Luồng Tải xuống (Download Flow)
1. **Client** gửi danh sách `node_id` cần tải về tới `POST /storage-service/files/download-urls`.
2. **Backend** xác thực quyền sở hữu của user đối với từng node, sinh Presigned URL (HTTP `GET`) có thời hạn xác định (ví dụ 15 phút).
3. **Client** dùng URL này để tải dữ liệu trực tiếp từ MinIO.

---

## 2. API Khởi tạo Phiên Tải lên (Get Upload URLs)

Yêu cầu hệ thống cấp danh sách Presigned PUT URL để client đẩy file trực tiếp lên MinIO.

- **Method & Endpoint:** `POST /storage-service/files/upload-urls` (hỗ trợ cả `/files/upload-urls`)
- **Controller:** `FileUploadController.getUploadUrls`
- **Headers bắt buộc:**
  - `X-User-Id`: UUID của người dùng (Gateway tự động gắn sau khi xác thực JWT)
- **Đầu vào (Request Body - `UploadUrlRequest`):**

```json
{
  "files": [
    {
      "parent_id": "1",
      "name": "bao-cao-q3.pdf",
      "size": 10485760,
      "mime_type": "application/pdf",
      "hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
  ]
}
```

### Chi tiết các trường trong `UploadUrlRequest`:

| Trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Mô tả |
|---|---|:---:|---|
| `files` | List<FileUploadItemRequest> | Có | Danh sách file cần tải lên (không được rỗng) |

### Chi tiết các trường trong từng phần tử `FileUploadItemRequest`:

| Trường | Kiểu dữ liệu | Bắt buộc | Ghi chú & Alias hỗ trợ |
|---|---|:---:|---|
| `parent_id` | String | Không | ID thư mục cha. Để `null` hoặc bỏ trống để lưu vào thư mục gốc. Alias: `parentId` |
| `name` | String | Có | Tên file hiển thị (không được để trống) |
| `size` | Long | Có | Kích thước file tính theo bytes (`>= 0`) |
| `mime_type` | String | Không | MIME type của file (ví dụ: `image/png`, `application/pdf`). Alias: `mimeType` |
| `hash` | String | Có | Mã băm SHA-256 của nội dung file (dùng cho deduplication). Alias: `file_hash`, `fileHash` |

- **Đầu ra `200 OK` (`ApiResponse<UploadUrlsResponse>`):**

```json
{
  "success": true,
  "message": "Thành công",
  "code": 200,
  "data": {
    "presigned_urls": [
      {
        "node_id": "18",
        "url": "http://localhost:9000/agentic-cloud/users/alice/87ab2e10-18f2?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
        "instant_uploaded": false,
        "expires_at": "2026-10-30T10:45:00Z"
      }
    ]
  },
  "timestamp": "2026-10-30T10:30:00Z"
}
```

### Chi tiết trường trả về `PresignedUrlResponse`:

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `node_id` | String | ID của node file vừa được tạo trong cơ sở dữ liệu |
| `url` | String | Đường dẫn Presigned PUT URL có chữ ký bảo mật |
| `instant_uploaded` | Boolean | `true` nếu file đã trùng hash và được tạo lập tức; `false` nếu cần client upload file lên MinIO |
| `expires_at` | String (ISO UTC) | Thời hạn hiệu lực của URL (định dạng `yyyy-MM-dd'T'HH:mm:ss'Z'`) |

---

## 3. API Khởi tạo Phiên Tải xuống (Get Download URLs)

Yêu cầu hệ thống cấp danh sách Presigned GET URL để tải file từ MinIO.

- **Method & Endpoint:** `POST /storage-service/files/download-urls` (hỗ trợ cả `/files/download-urls`)
- **Controller:** `FileDownloadController.getDownloadUrls`
- **Headers bắt buộc:**
  - `X-User-Id`: UUID của người dùng
- **Đầu vào (Request Body - `DownloadUrlRequest`):**

```json
{
  "files": [
    {
      "node_id": "18"
    }
  ]
}
```

### Chi tiết các trường trong `DownloadUrlRequest`:

| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `files` | List<FileDownloadItemRequest> | Có | Danh sách file cần tải xuống (không được rỗng) |

### Chi tiết các trường trong `FileDownloadItemRequest`:

| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `node_id` | String | Có | ID của node file cần tải xuống |

- **Đầu ra `200 OK` (`ApiResponse<DownloadUrlsResponse>`):**

```json
{
  "success": true,
  "message": "Thành công",
  "code": 200,
  "data": {
    "download_urls": [
      {
        "node_id": "18",
        "file_name": "bao-cao-q3.pdf",
        "url": "http://localhost:9000/agentic-cloud/users/alice/87ab2e10-18f2?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
        "expires_at": "2026-10-30T10:45:00Z",
        "mime_type": "application/pdf",
        "size_bytes": 10485760
      }
    ]
  },
  "timestamp": "2026-10-30T10:30:00Z"
}
```

### Chi tiết trường trả về `DownloadUrlItemResponse`:

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `node_id` | String | ID của node file |
| `file_name` | String | Tên file hiển thị |
| `url` | String | Đường dẫn Presigned GET URL để tải file |
| `expires_at` | String (ISO UTC) | Thời hạn hết hạn của URL (`yyyy-MM-dd'T'HH:mm:ss'Z'`) |
| `mime_type` | String | Định dạng MIME của file |
| `size_bytes` | Long | Kích thước file (bytes) |

---

## 4. Các Mã Lỗi Thường Gặp

| HTTP Status | Trường hợp xảy ra |
|:---:|---|
| `400 Bad Request` | Thiếu thông tin bắt buộc trong request body, kích thước file âm, hoặc người dùng đã vượt hạn mức dung lượng (Quota exceeded) |
| `401 Unauthorized` | Thiếu hoặc sai header `X-User-Id` |
| `404 Not Found` | Không tìm thấy node file tương ứng hoặc node không thuộc quyền sở hữu của user |
| `500 Internal Server Error` | Lỗi kết nối tới MinIO SDK khi khởi tạo URL có chữ ký |
