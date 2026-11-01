# User Service API

Tài liệu này mô tả chi tiết các RESTful API hiện có của `user-service`, được cập nhật trực tiếp dựa trên source code các Controller, DTO, cấu hình bảo mật và Exception Handler trong hệ thống.

---

## 1. Thông tin chung

- **Cổng dịch vụ nội bộ (Local/Internal Base URL):** `http://localhost:8081`
- **Public Base URL qua API Gateway:** `http://localhost:8089/api/v1/user-service`
  - API Gateway định tuyến `/api/v1/user-service/**` về `user-service:8081` và tự động lược bỏ tiền tố `/api/v1` (`stripPrefix(2)`), do đó request đến `user-service` giữ nguyên tiền tố `/user-service/...`.
- **Content-Type:** `application/json`
- **Quy ước đặt tên thuộc tính JSON (Naming Strategy):** `SNAKE_CASE` (theo cấu hình Spring Jackson: `spring.jackson.property-naming-strategy: SNAKE_CASE`, các trường `null` mặc định không xuất hiện trong JSON).

### Cấu trúc Response chuẩn (`ApiResponse`)

Tất cả các API Controller và Global Exception Handler đều đóng gói kết quả trả về trong đối tượng envelope `ApiResponse`:

```json
{
  "success": true,
  "message": "Login successful",
  "code": 200,
  "data": {},
  "timestamp": "2026-10-30T09:00:00Z"
}
```

- `success` (boolean): `true` nếu xử lý thành công, `false` nếu xảy ra lỗi.
- `message` (string, optional): Thông báo kết quả thao tác.
- `code` (integer): HTTP status code (200, 201, 400, 401, 403, 404, 409, 500,...).
- `data` (object, optional): Dữ liệu nghiệp vụ trả về.
- `error` (string, optional): Mô tả chi tiết khi xảy ra lỗi (`success: false`).
- `timestamp` (string ISO-8601): Thời điểm phản hồi.

---

## 2. Cơ chế Xác thực & Phân quyền

- **Public Endpoints (Không yêu cầu Token):**
  - `/user-service/auth/**` (Đăng ký, Đăng nhập, Refresh Token, Đăng xuất)
  - `/actuator/health`, `/actuator/info`
  - `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`
- **Authenticated Endpoints (Yêu cầu JWT Token):**
  - Tất cả các endpoint còn lại bắt buộc gửi JWT Token qua HTTP header:
    ```http
    Authorization: Bearer <access-token>
    ```
    hoặc qua Cookie: `access_token=<access-token>`.
  - Phân quyền theo vai trò (`ROLE_USER`, `ROLE_ADMIN`) dựa trên annotation `@PreAuthorize`.
- **Rate Limiting:**
  - Áp dụng mặc định 20 requests / 60 giây trên mỗi địa chỉ IP. Vượt giới hạn sẽ trả lỗi `429 Too Many Requests`.

---

## 3. Chi tiết API Authentication (`AuthController`)

Base route: `/user-service/auth`

### 3.1. Đăng ký tài khoản (`POST /user-service/auth/register`)

Tạo tài khoản người dùng mới với vai trò mặc định `ROLE_USER`, đồng thời tự động kích hoạt tạo không gian lưu trữ cá nhân (Storage) và trả về cặp Token.

- **Endpoint:** `POST /user-service/auth/register`
- **Yêu cầu xác thực:** Không (Public)
- **Request Body (`RegisterRequest`):**

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "correct-horse-battery"
}
```

| Trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc validation |
|---|---|:---:|---|
| `username` | string | Có | Không được để trống; độ dài từ 2 đến 100 ký tự |
| `email` | string | Có | Không được để trống; đúng định dạng email RFC |
| `password` | string | Có | Không được để trống; độ dài tối thiểu 8 ký tự |

- **Response `201 Created` (`ApiResponse<AuthResponse>`):**

```json
{
  "success": true,
  "message": "User registered successfully",
  "code": 201,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "user_id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
    "username": "alice",
    "email": "alice@example.com",
    "roles": ["ROLE_USER"]
  },
  "timestamp": "2026-10-30T09:00:00Z"
}
```

- **Mã lỗi thường gặp:**
  - `400 Bad Request`: Dữ liệu đầu vào vi phạm validation.
  - `404 Not Found`: Không tìm thấy vai trò mặc định `ROLE_USER` trong hệ thống.
  - `409 Conflict`: `username` đã tồn tại trên hệ thống.

---

### 3.2. Đăng nhập (`POST /user-service/auth/login`)

Xác thực thông tin tài khoản bằng username và password; thu hồi (revoke) refresh token cũ và cấp phát cặp token mới.

- **Endpoint:** `POST /user-service/auth/login`
- **Yêu cầu xác thực:** Không (Public)
- **Request Body (`LoginRequest`):**

```json
{
  "username": "alice",
  "password": "correct-horse-battery"
}
```

| Trường | Kiểu dữ liệu | Bắt buộc | Ghi chú |
|---|---|:---:|---|
| `username` | string | Có | Tên đăng nhập |
| `password` | string | Có | Mật khẩu tài khoản |

- **Response `200 OK` (`ApiResponse<AuthResponse>`):**

```json
{
  "success": true,
  "message": "Login successful",
  "code": 200,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "user_id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
    "username": "alice",
    "email": "alice@example.com",
    "roles": ["ROLE_USER"]
  },
  "timestamp": "2026-10-30T09:00:00Z"
}
```

- **Mã lỗi thường gặp:**
  - `401 Unauthorized`: Sai username hoặc password.
  - `403 Forbidden`: Tài khoản bị vô hiệu hóa (`enabled = false`).

---

### 3.3. Làm mới Token (`POST /user-service/auth/refresh`)

Sử dụng Refresh Token còn hiệu lực để cấp phát cặp Token mới (Token Rotation).

- **Endpoint:** `POST /user-service/auth/refresh`
- **Yêu cầu xác thực:** Không (Public)
- **Request Body (`RefreshTokenRequest`):**

```json
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

| Trường | Kiểu dữ liệu | Bắt buộc | Ghi chú |
|---|---|:---:|---|
| `refresh_token` | string | Có | Chuỗi Refresh Token hợp lệ chưa bị revoke |

- **Response `200 OK` (`ApiResponse<AuthResponse>`):**

```json
{
  "success": true,
  "message": "Token refreshed",
  "code": 200,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "user_id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
    "username": "alice",
    "email": "alice@example.com",
    "roles": ["ROLE_USER"]
  },
  "timestamp": "2026-10-30T09:00:00Z"
}
```

- **Mã lỗi thường gặp:**
  - `400 Bad Request`: Token không hợp lệ, đã bị thu hồi hoặc hết hạn.

---

### 3.4. Đăng xuất (`POST /user-service/auth/logout`)

Thu hồi Refresh Token hiện tại để ngăn chặn việc tái sử dụng.

- **Endpoint:** `POST /user-service/auth/logout`
- **Yêu cầu xác thực:** Không (Public)
- **Request Body (`RefreshTokenRequest`):**

```json
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

- **Response `200 OK` (`ApiResponse<Void>`):**

```json
{
  "success": true,
  "message": "Logged out successfully",
  "code": 200,
  "timestamp": "2026-10-30T09:00:00Z"
}
```

---

## 4. Chi tiết API Quản lý Người dùng (`UserController`)

Base route: `/user-service/users`  
Header bắt buộc: `Authorization: Bearer <access-token>`

### 4.1. Lấy thông tin tài khoản hiện tại (`GET /user-service/users/me`)

Lấy thông tin cá nhân và thông tin không gian lưu trữ của người dùng đang đăng nhập (principal).

- **Endpoint:** `GET /user-service/users/me`
- **Phân quyền:** Bất kỳ người dùng nào đã xác thực
- **Response `200 OK` (`ApiResponse<UserResponse>`):**

```json
{
  "success": true,
  "code": 200,
  "data": {
    "id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
    "username": "alice",
    "email": "alice@example.com",
    "roles": ["ROLE_USER"],
    "storage": {
      "user_id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
      "storage_prefix": "users/alice",
      "storage_bucket_name": "agentic-cloud",
      "storage_max_quota_bytes": 10737418240,
      "storage_current_quota_bytes": 1048576,
      "root_node_id": 1,
      "root_node_name": "root",
      "root_node_path": "root",
      "created_at": "2026-10-30T08:00:00",
      "updated_at": "2026-10-30T08:05:00"
    },
    "enabled": true,
    "created_at": "2026-10-30T08:00:00"
  },
  "timestamp": "2026-10-30T09:00:00Z"
}
```

---

### 4.2. Lấy thông tin người dùng theo ID (`GET /user-service/users/{id}`)

- **Endpoint:** `GET /user-service/users/{id}`
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")` (Chỉ quản trị viên hoặc chính người dùng sở hữu ID đó).
- **Path Parameter:**
  - `id` (UUID, Bắt buộc): Định danh duy nhất của người dùng.
- **Response `200 OK` (`ApiResponse<UserResponse>`):** Cấu trúc tương tự mục 4.1.
- **Mã lỗi thường gặp:**
  - `403 Forbidden`: Người dùng thông thường cố truy vấn ID của tài khoản khác.
  - `404 Not Found`: Không tìm thấy người dùng với ID đã cung cấp.

---

### 4.3. Danh sách người dùng hệ thống (`GET /user-service/users`)

- **Endpoint:** `GET /user-service/users`
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN')")` (Chỉ dành cho ADMIN)
- **Query Parameters (Hỗ trợ phân trang Spring Data):**

| Query Parameter | Kiểu | Mặc định | Mô tả |
|---|---|:---:|---|
| `page` | integer | `0` | Chỉ số trang (bắt đầu từ 0) |
| `size` | integer | `20` | Số bản ghi trên mỗi trang |
| `sort` | string | `createdAt` | Trường sắp xếp, ví dụ `createdAt,desc` hoặc `username,asc` |

- **Response `200 OK` (`ApiResponse<Page<UserResponse>>`):**

```json
{
  "success": true,
  "code": 200,
  "data": {
    "content": [
      {
        "id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
        "username": "alice",
        "email": "alice@example.com",
        "roles": ["ROLE_USER"],
        "storage": {
          "user_id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
          "storage_prefix": "users/alice",
          "storage_bucket_name": "agentic-cloud",
          "storage_max_quota_bytes": 10737418240,
          "storage_current_quota_bytes": 0,
          "root_node_id": 1,
          "root_node_name": "root",
          "root_node_path": "root",
          "created_at": "2026-10-30T08:00:00",
          "updated_at": "2026-10-30T08:00:00"
        },
        "enabled": true,
        "created_at": "2026-10-30T08:00:00"
      }
    ],
    "pageable": {
      "page_number": 0,
      "page_size": 20,
      "sort": {
        "sorted": true,
        "unsorted": false,
        "empty": false
      }
    },
    "total_pages": 1,
    "total_elements": 1,
    "last": true,
    "first": true,
    "size": 20,
    "number": 0
  },
  "timestamp": "2026-10-30T09:00:00Z"
}
```

---

### 4.4. Xóa người dùng (`DELETE /user-service/users/{id}`)

- **Endpoint:** `DELETE /user-service/users/{id}`
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN')")` (Chỉ dành cho ADMIN)
- **Path Parameter:**
  - `id` (UUID, Bắt buộc): ID tài khoản cần xóa.
- **Response `200 OK` (`ApiResponse<Void>`):**

```json
{
  "success": true,
  "message": "User deleted successfully",
  "code": 200,
  "timestamp": "2026-10-30T09:00:00Z"
}
```

- **Mã lỗi thường gặp:**
  - `403 Forbidden`: Không có quyền ADMIN.
  - `404 Not Found`: Không tìm thấy user cần xóa.

---

### 4.5. Tìm kiếm người dùng (`GET /user-service/users/search`)

Tìm kiếm danh sách người dùng trong hệ thống theo username hoặc email. API này phục vụ trực tiếp cho tính năng chia sẻ tệp tin và thư mục (Node Sharing), cho phép người dùng đang đăng nhập tìm kiếm và lựa chọn các user khác để cấp quyền chia sẻ.

- **Endpoint:** `GET /user-service/users/search`
- **Controller:** `UserController.searchUsers`
- **Phân quyền:** Mọi người dùng đã xác thực (Authenticated: `@PreAuthorize("isAuthenticated()")`)
- **Query Parameters:**

| Query Parameter | Kiểu | Bắt buộc | Mặc định | Mô tả |
|---|---|:---:|:---:|---|
| `q` | string | Có | Không | Từ khóa tìm kiếm theo `username` hoặc `email` (tối thiểu 2 ký tự, không phân biệt hoa thường) |
| `limit` | integer | Không | `10` | Số lượng kết quả trả về tối đa (tối thiểu 1, tối đa 50) |

- **Quy tắc nghiệp vụ bảo mật & lọc dữ liệu:**
  1. Tự động loại trừ chính người dùng đang thực hiện request (`id != authentication.principal.id`).
  2. Chỉ tìm kiếm các tài khoản người dùng đang hoạt động (`enabled = true`).
  3. Chỉ trả về các thông tin công khai an toàn (`id`, `username`, `email`, `created_at`). Tuyệt đối không trả về thông tin nhạy cảm như vai trò nội bộ, quota, storage prefix hay bucket name.

- **Response `200 OK` (`ApiResponse<List<UserSearchResponse>>`):**

```json
{
  "success": true,
  "message": "Tìm kiếm người dùng thành công",
  "code": 200,
  "data": [
    {
      "id": "e4b2d184-2a67-4d92-8012-3f8d9c025112",
      "username": "bob_smith",
      "email": "bob@example.com",
      "created_at": "2026-10-15T08:30:00"
    },
    {
      "id": "f5c3e295-3b78-4ea3-9123-4a9e0d136223",
      "username": "bobby_dev",
      "email": "bobby.dev@company.com",
      "created_at": "2026-10-20T14:15:00"
    }
  ],
  "timestamp": "2026-10-31T10:00:00Z"
}
```

- **Mã lỗi thường gặp:**
  - `400 Bad Request`: Tham số `q` bị trống hoặc ít hơn 2 ký tự; tham số `limit` không hợp lệ.
  - `401 Unauthorized`: Chưa đăng nhập hoặc Access Token không hợp lệ.

---

## 5. Health Check API (`HealthController`)

### 5.1. Ping Service (`GET /user-service/ping`)

- **Endpoint:** `GET /user-service/ping`
- **Response `200 OK` (`ApiResponse<Map<String, Object>>`):**

```json
{
  "success": true,
  "code": 200,
  "data": {
    "status": "UP",
    "timestamp": "2026-10-30T09:00:00.123456Z",
    "version": "1.0.0"
  },
  "timestamp": "2026-10-30T09:00:00.123456Z"
}
```

---

## 6. Mô hình DTO (Data Transfer Objects)

### 6.1. Request DTOs

#### `RegisterRequest`
- `username`: String (not blank, 2-100 ký tự)
- `email`: String (not blank, email valid)
- `password`: String (not blank, tối thiểu 8 ký tự)

#### `LoginRequest`
- `username`: String (not blank)
- `password`: String (not blank)

#### `RefreshTokenRequest`
- `refresh_token`: String (not blank)

### 6.2. Response DTOs

#### `AuthResponse`
| Tên thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `access_token` | String | JWT access token |
| `refresh_token` | String | JWT refresh token |
| `token_type` | String | Luôn có giá trị `"Bearer"` |
| `user_id` | UUID | ID của người dùng |
| `username` | String | Tên đăng nhập |
| `email` | String | Email |
| `roles` | Set<String> | Danh sách các quyền, ví dụ `["ROLE_USER"]` |

#### `UserResponse`
| Tên thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `id` | UUID | ID duy nhất của người dùng |
| `username` | String | Tên đăng nhập |
| `email` | String | Email |
| `roles` | Set<String> | Danh sách vai trò |
| `storage` | UserStorageResponse | Thông tin chi tiết không gian lưu trữ gắn với user |
| `enabled` | boolean | Trạng thái tài khoản (`true`: hoạt động, `false`: bị khóa) |
| `created_at` | LocalDateTime | Thời điểm tạo tài khoản |

#### `UserStorageResponse`
| Tên thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `user_id` | UUID | ID của người dùng sở hữu storage |
| `storage_prefix` | String | Tiền tố thư mục trên Object Storage (MinIO) |
| `storage_bucket_name` | String | Tên bucket chứa dữ liệu |
| `storage_max_quota_bytes` | Long | Hạn mức lưu trữ tối đa (byte) |
| `storage_current_quota_bytes` | Long | Dung lượng hiện đang sử dụng (byte) |
| `root_node_id` | Long | ID bản ghi thư mục gốc (`FsNode`) |
| `root_node_name` | String | Tên thư mục gốc (mặc định `"root"`) |
| `root_node_path` | String | Đường dẫn materialized path của thư mục gốc |
| `created_at` | LocalDateTime | Thời gian tạo không gian lưu trữ |
| `updated_at` | LocalDateTime | Thời gian cập nhật gần nhất |

#### `UserSearchResponse`
| Tên thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `id` | UUID | ID duy nhất của người dùng |
| `username` | String | Tên đăng nhập |
| `email` | String | Địa chỉ email người dùng |
| `created_at` | LocalDateTime | Thời gian đăng ký tài khoản |
