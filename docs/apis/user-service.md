# User Service API

Tài liệu này mô tả các API hiện có của `user-service`, dựa trên controller, DTO,
cấu hình bảo mật và exception handler trong source code.

## Thông tin chung

- Base URL local: `http://localhost:8081`
- Prefix của các API nghiệp vụ: `/user-service`
- Content type: `application/json`
- Các API controller và exception handler trả dữ liệu theo envelope
  `ApiResponse`:

```json
{
  "success": true,
  "message": "Login successful",
  "code": 200,
  "data": {},
  "timestamp": "2026-10-18T09:00:00Z"
}
```

Các thuộc tính không có giá trị có thể bị lược khỏi JSON. Response lỗi từ
exception handler dùng `success: false`, `code` và `error`; lỗi xác thực do
Spring Security chặn có thể dùng response mặc định của framework.

### Xác thực

Các route trong `/user-service/auth/**` không yêu cầu access token. Những route
còn lại yêu cầu JWT, gửi qua HTTP Bearer authentication trong header
`Authorization` hoặc cookie `access_token`.

Route quản lý người dùng còn áp dụng phân quyền theo mô tả của từng endpoint.
Rate limit mặc định là 20 request mỗi IP trong 60 giây; khi vượt giới hạn,
server trả `429 Too Many Requests`.

## Authentication

### Đăng ký

`POST /user-service/auth/register`

Tạo tài khoản với role mặc định `ROLE_USER` và trả về cặp token.

Request:

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "correct-horse-battery"
}
```

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `username` | string | Có | Không rỗng; độ dài 2–100 ký tự |
| `email` | string | Có | Không rỗng; phải đúng định dạng email |
| `password` | string | Có | Không rỗng; tối thiểu 8 ký tự |

Response `201 Created`: `data` có cấu trúc [Auth data](#auth-data).

Các lỗi có thể gặp: `400` dữ liệu không hợp lệ, `404` không tìm thấy role
`ROLE_USER`, `409` username đã được đăng ký.

### Đăng nhập

`POST /user-service/auth/login`

Request:

```json
{
  "username": "alice",
  "password": "correct-horse-battery"
}
```

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `username` | string | Có | Không rỗng |
| `password` | string | Có | Không rỗng |

Response `200 OK`: `data` có cấu trúc [Auth data](#auth-data). Khi đăng nhập
thành công, refresh token hiện có của user sẽ bị thu hồi trước khi tạo token mới.
Thông tin đăng nhập sai trả `401 Unauthorized`; tài khoản bị vô hiệu hóa trả
`403 Forbidden`.

### Làm mới token

`POST /user-service/auth/refresh`

Request:

```json
{
  "refresh_token": "<refresh-token>"
}
```

`refreshToken` là trường bắt buộc và không được để trống. Theo luồng dự kiến,
response `200 OK` trả cặp token mới và refresh token cũ được rotate. Token không
tìm thấy hoặc bị từ chối trả `400 Bad Request`.

> **Lưu ý triển khai hiện tại:** điều kiện kiểm tra trong `AuthService.refresh`
> từ chối request khi JWT refresh token hợp lệ (`isTokenValid` trả `true`), trong
> khi token hết hạn/không hợp lệ có thể đi tiếp nếu bản ghi chưa bị revoke. Vì
> vậy hành vi hiện tại không khớp với luồng refresh dự kiến ở trên; cần kiểm tra
> implementation trước khi dựa vào endpoint này.

### Đăng xuất

`POST /user-service/auth/logout`

Request:

```json
{
  "refresh_token": "<refresh-token>"
}
```

Response `200 OK` với message `Logged out successfully`. Nếu không tìm thấy token
thì request vẫn trả thành công; nếu tìm thấy, refresh token sẽ bị revoke. Trường
`refreshToken` bắt buộc và không được để trống.

### Auth data

Các API register, login và refresh trả `data` theo dạng:

```json
{
  "access_token": "<jwt>",
  "refresh_token": "<jwt>",
  "token_type": "Bearer",
  "user_id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
  "username": "alice",
  "email": "alice@example.com",
  "roles": ["ROLE_USER"]
}
```

## Users

Các endpoint trong phần này yêu cầu access token.

### Lấy hồ sơ người dùng hiện tại

`GET /user-service/users/me`

Trả `200 OK` với `data` là [User data](#user-data) của principal hiện tại.

### Lấy người dùng theo ID

`GET /user-service/users/{id}`

| Path parameter | Kiểu | Mô tả |
|---|---|---|
| `id` | UUID | ID người dùng |

Theo annotation phân quyền, endpoint dành cho ADMIN hoặc chính người dùng có ID
tương ứng. Trả `200 OK` với [User data](#user-data); trả `404 Not Found` nếu
không có user.

### Liệt kê người dùng

`GET /user-service/users`

Chỉ ADMIN. Hỗ trợ tham số phân trang của Spring Data:

| Query parameter | Mặc định | Mô tả |
|---|---:|---|
| `page` | `0` | Số trang, bắt đầu từ 0 |
| `size` | `20` | Số phần tử mỗi trang |
| `sort` | `createdAt` | Trường sắp xếp; có thể truyền hướng, ví dụ `createdAt,desc` |

Ví dụ: `GET /user-service/users?page=0&size=20&sort=createdAt,desc`

Trả `200 OK`; `data` là đối tượng trang Spring Data với danh sách `content` gồm
các [User data](#user-data) và metadata phân trang.

### Xóa người dùng

`DELETE /user-service/users/{id}`

Chỉ ADMIN. `id` là UUID. Trả `200 OK` với message `User deleted successfully`;
trả `404 Not Found` nếu không tìm thấy user.

### User data

```json
{
  "id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
  "username": "alice",
  "email": "alice@example.com",
  "roles": ["ROLE_USER"],
  "storage": {
      "storage_id": "uuid-của-storage-vừa-tạo",
      "storage_prefix": "bucket/taihb2005",
      "storage_bucket_name": "bucket-name",
      "storage_max_quota_bytes": "9999",
      "storage_current_quota_bytes": "43",
      "root_node_id": "uuid",
      "root_node_name": "root",
      "root_node_path": "root"
  },
  "enabled": true,
  "created_at": "2026-10-18T16:00:00"
}
```

## Health check

### Ping

`GET /user-service/ping`

Trả `200 OK` với `data` gồm `status` (`UP`), `timestamp` và `version` (`1.0.0`).
Mặc dù là ping endpoint, route này không nằm trong danh sách public của
`SecurityConfig`; gửi access token hợp lệ.

## Lưu ý cần xác minh trong implementation

- JWT đặt `sub` bằng user UUID, nhưng `GET /user-service/users/me` dùng tên
  principal đó để tra cứu theo username. Vì vậy route có thể trả `404` với user
  đăng nhập bình thường.
- Kiểm tra quyền tự xem tại `GET /user-service/users/{id}` truy cập
  `authentication.principal.id`, trong khi JWT filter tạo principal dạng chuỗi.
  Hành vi truy cập của user không phải ADMIN cần được xác minh; tài liệu không
  khẳng định self-access hoạt động.
- Thông báo validation của username trong source nói “6-20 characters”, nhưng
  constraint thực tế cho phép từ 2 đến 100 ký tự; bảng request ở trên ghi theo
  constraint thực tế.

## Spring Boot Actuator

Các endpoint được expose qua Actuator:

| Method | Path | Access |
|---|---|---|
| `GET` | `/actuator/health` | Public |
| `GET` | `/actuator/info` | Public |
| `GET` | `/actuator/metrics` | Yêu cầu JWT |

`/actuator/metrics` là endpoint gốc; các metric cụ thể có thể được truy vấn qua
`/actuator/metrics/{requiredMetricName}` theo Spring Boot Actuator.

## Lỗi và status code

| HTTP status | Ý nghĩa |
|---:|---|
| `400` | Request validation thất bại hoặc lỗi nghiệp vụ như refresh token bị từ chối |
| `401` | Thiếu/sai thông tin xác thực hoặc access token không được chấp nhận |
| `403` | Không đủ quyền hoặc tài khoản bị vô hiệu hóa |
| `404` | Không tìm thấy user/resource |
| `409` | Username đã được đăng ký |
| `429` | Vượt rate limit |
| `500` | Lỗi không xử lý được |

Lỗi validation trả envelope lỗi với nội dung mô tả các trường không hợp lệ.
