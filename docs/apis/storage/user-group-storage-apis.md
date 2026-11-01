# Nhóm API Không gian Lưu trữ & Hạn mức Người dùng (Storage Service)

Tài liệu này mô tả chi tiết các RESTful API phục vụ việc tra cứu hạn mức sử dụng (Quota), kiểm tra không gian lưu trữ và khởi tạo không gian lưu trữ cho người dùng mới trong `storage-service`. Dữ liệu được đồng bộ trực tiếp từ Controller `StorageUserController` và các DTO tương ứng.

---

## 1. Thông tin Chung & Bảo mật

- **Base Route:** `/storage-service/storage/users` (hỗ trợ cả `/storage/users`)
- **Headers bắt buộc:**
  - `Authorization: Bearer <access-token>` (Client gửi tới Gateway)
  - `X-User-Id`: UUID của người dùng (Gateway inject vào request)
  - `X-User-Roles`: Danh sách vai trò người dùng (ví dụ: `ROLE_USER`, `ROLE_ADMIN`)
- **Phân quyền truy cập:**
  - Người dùng thông thường chỉ có thể xem Quota và Storage của chính mình (`#id == authentication.principal.id`).
  - Quản trị viên (`ROLE_ADMIN`) có quyền xem thông tin của bất kỳ người dùng nào trong hệ thống.
  - Các endpoint khởi tạo storage (`/init`) được thiết kế cho inter-service communication (gọi từ `user-service` khi user đăng ký hoặc từ các tác vụ quản trị).

---

## 2. Danh mục API

| Method | Endpoint | Quyền hạn | Mô tả |
|---|---|---|---|
| `GET` | `/storage-service/storage/users/quota/{id}` | Admin hoặc Chính chủ | Lấy thông tin dung lượng đã dùng, hạn mức và tỷ lệ % sử dụng |
| `GET` | `/storage-service/storage/users/storage/{id}` | Admin hoặc Chính chủ | Lấy thông tin chi tiết về không gian lưu trữ và node gốc |
| `POST` | `/storage-service/storage/users/init` | Hệ thống / Nội bộ | Khởi tạo không gian lưu trữ và cây dữ liệu gốc cho người dùng |
| `POST` | `/storage-service/storage/users/{userId}/init` | Hệ thống / Nội bộ | Khởi tạo nhanh không gian lưu trữ qua path variable |

---

## 3. Chi tiết từng API

### 3.1. Kiểm tra Hạn mức Dung lượng Lưu trữ (Quota)

Tra cứu dung lượng đã sử dụng (`current_quota`), hạn mức tối đa (`max_quota`) và trạng thái cảnh báo dung lượng của người dùng.

- **Endpoint:** `GET /storage-service/storage/users/quota/{id}`
- **Controller:** `StorageUserController.getUserQuota`
- **Path Parameter:**
  - `id` (UUID, Bắt buộc): ID của người dùng cần kiểm tra
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")`
- **Response `200 OK` (`ApiResponse<UserQuotaResponse>`):**

```json
{
  "success": true,
  "message": "Lấy thông tin thành công",
  "code": 200,
  "data": {
    "user_id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
    "storage_id": "9bc1e284-56a1-43ef-92e1-884021235678",
    "current_quota": 5368709120,
    "max_quota": 16106127360,
    "unit": "MB",
    "usage_percentage": 33.33,
    "status": "NORMAL"
  },
  "timestamp": "2026-10-30T10:00:00Z"
}
```

#### Chi tiết các trường trong `UserQuotaResponse`:

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `user_id` | UUID | Định danh người dùng |
| `storage_id` | UUID | Định danh bản ghi storage của người dùng (`FsUser`) |
| `current_quota` | Long | Dung lượng hiện đang sử dụng (tính theo byte) |
| `max_quota` | Long | Hạn mức dung lượng tối đa được cấp (tính theo byte) |
| `unit` | String | Đơn vị quy đổi để hiển thị (ví dụ: `MB`, `GB`) |
| `usage_percentage` | Double | Phần trăm dung lượng đã dùng (làm tròn 2 chữ số thập phân) |
| `status` | String | Trạng thái sử dụng (ví dụ: `NORMAL`, `WARNING`, `EXCEEDED`) |

---

### 3.2. Lấy Thông tin Không gian Lưu trữ (User Storage)

Tra cứu chi tiết cấu hình storage của người dùng: bucket S3, prefix phân vùng, thông tin thư mục gốc (`root_node_id`, `root_node_path`).

- **Endpoint:** `GET /storage-service/storage/users/storage/{id}`
- **Controller:** `StorageUserController.getUserStorage`
- **Path Parameter:**
  - `id` (UUID, Bắt buộc): ID của người dùng cần tra cứu
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")`
- **Response `200 OK` (`ApiResponse<UserStorageResponse>`):**

```json
{
  "success": true,
  "message": "Lấy thông tin thành công",
  "code": 200,
  "data": {
    "user_id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
    "storage_prefix": "users/alice",
    "storage_bucket_name": "agentic-cloud",
    "storage_max_quota_bytes": 10737418240,
    "storage_current_quota_bytes": 52428800,
    "root_node_id": 1,
    "root_node_name": "root",
    "root_node_path": "root",
    "created_at": "2026-10-30T08:00:00",
    "updated_at": "2026-10-30T08:05:00"
  },
  "timestamp": "2026-10-30T10:00:00Z"
}
```

#### Chi tiết các trường trong `UserStorageResponse`:

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `user_id` | UUID | ID của người dùng sở hữu |
| `storage_prefix` | String | Tiền tố thư mục phân vùng trên MinIO/S3 |
| `storage_bucket_name` | String | Tên bucket chứa dữ liệu trên object storage |
| `storage_max_quota_bytes` | Long | Hạn mức dung lượng tối đa (bytes) |
| `storage_current_quota_bytes` | Long | Dung lượng hiện tại đã sử dụng (bytes) |
| `root_node_id` | Long | ID bản ghi thư mục gốc (`FsNode`) của người dùng |
| `root_node_name` | String | Tên thư mục gốc (mặc định: `"root"`) |
| `root_node_path` | String | Materialized path của thư mục gốc (mặc định: `"root"`) |
| `created_at` | LocalDateTime | Thời điểm khởi tạo storage |
| `updated_at` | LocalDateTime | Thời điểm cập nhật storage |

---

### 3.3. Khởi tạo Không gian Lưu trữ cho Người dùng mới

API dùng để thiết lập không gian lưu trữ, tạo phân vùng thư mục trên MinIO và sinh thư mục gốc `root` cho người dùng mới đăng ký. Nếu storage của người dùng đã tồn tại từ trước, hệ thống trả về thông tin hiện tại với mã `200 OK`.

- **Endpoint:** `POST /storage-service/storage/users/init`
- **Controller:** `StorageUserController.initUserStorage`
- **Request Body (`InitUserStorageRequest`):**

```json
{
  "userId": "7ac9e9d3-79a2-44ec-806d-7029af302116",
  "username": "alice",
  "email": "alice@example.com",
  "customQuotaBytes": 10737418240
}
```

| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `userId` | UUID | Có | Định danh của người dùng (không được null) |
| `username` | String | Không | Tên tài khoản người dùng |
| `email` | String | Không | Địa chỉ email người dùng |
| `customQuotaBytes` | Long | Không | Hạn mức cấp riêng tính bằng bytes (nếu bỏ trống sẽ lấy mặc định hệ thống) |

- **Response:**
  - Nếu là người dùng mới: `201 CREATED` kèm message `"Khởi tạo không gian lưu trữ và cây dữ liệu thành công"`.
  - Nếu người dùng đã có storage: `200 OK` kèm message `"Không gian lưu trữ của người dùng đã tồn tại"`.
  - Dữ liệu `data` có cấu trúc là `UserStorageResponse`.

---

### 3.4. Khởi tạo Nhanh qua Path Variable

- **Endpoint:** `POST /storage-service/storage/users/{userId}/init`
- **Controller:** `StorageUserController.initUserStorageByUserId`
- **Path Parameter:**
  - `userId` (UUID, Bắt buộc): ID của người dùng cần khởi tạo storage
- **Response:** Cấu trúc tương tự mục 3.3.
