# Storage Service API Specification

Tài liệu này mô tả chi tiết toàn bộ các RESTful API của dịch vụ lưu trữ `storage-service`, được cập nhật trực tiếp dựa trên mã nguồn Java thực tế gồm các Controller, DTO, Entity, Security Filter và Exception Handler.

---

## 1. Tổng quan Kiến trúc & Thông tin Mạng

- **Cổng dịch vụ nội bộ (Internal Port):** `8082` (`http://localhost:8082`)
- **Public Base URL qua API Gateway:** `http://localhost:8089/api/v1/storage-service`
  - API Gateway cấu hình rewrite `.stripPrefix(2)` đối với `/api/v1/storage-service/**` => chuyển tiếp thành `/storage-service/**` tới `http://storage-service:8082`.
  - Các Controller trong `storage-service` đều hỗ trợ tiền tố chuẩn `/storage-service/...` (và một số route hỗ trợ thêm đường dẫn không có tiền tố như `/files`, `/nodes`, `/storage/users`).
- **Content-Type:** `application/json` (trừ khi Client gửi dữ liệu trực tiếp lên S3/MinIO qua Presigned URL).

### Cơ chế Xác thực & Định danh Người dùng

Hệ thống hoạt động theo mô hình Gateway Authentication:
1. Client gửi request kèm header `Authorization: Bearer <access-token>` tới **API Gateway**.
2. **API Gateway** xác thực JWT, trích xuất thông tin user và gắn các header nội bộ:
   - `X-User-Id`: UUID của người dùng hiện tại (bắt buộc).
   - `X-User-Roles`: Danh sách vai trò của người dùng (ví dụ: `ROLE_USER`, `ROLE_ADMIN`).
3. `GatewayAuthFilter` trong `storage-service` đọc `X-User-Id` để nạp thông tin người dùng (`FsUser`) vào Spring `SecurityContextHolder`. Các API lấy thông tin người dùng hiện tại qua annotation `@AuthenticationPrincipal FsUser user`.

---

## 2. Cấu trúc Response & Xử lý Batch chuẩn

### 2.1. Envelope chuẩn (`ApiResponse<T>`)

Mọi phản hồi từ `storage-service` đều được bọc trong envelope `ApiResponse`:

```json
{
  "success": true,
  "message": "Thành công",
  "code": 200,
  "data": {},
  "timestamp": "2026-10-30T10:00:00Z"
}
```

### 2.2. Phân trang chuẩn (`PagedResponse<T>`)

Các API trả về danh sách phân trang dùng DTO `PagedResponse<T>` trong `data`:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "total_elements": 100,
  "total_pages": 5,
  "last": false
}
```

### 2.3. Thao tác hàng loạt (`BatchOperationResponse<S, F>`)

Các API thực hiện thao tác trên nhiều node (di chuyển, xóa mềm, khôi phục, xóa vĩnh viễn) sử dụng `BatchOperationResponse` để cung cấp chi tiết kết quả từng phần:

```json
{
  "total": 3,
  "success_count": 2,
  "failed_count": 1,
  "succeeded": [
    { "node_id": "1", "parent_id": "10" }
  ],
  "failed": [
    {
      "code": "NODE_NOT_FOUND",
      "message": "Node không tồn tại",
      "data": { "node_id": "99" }
    }
  ]
}
```

Các mã lỗi nghiệp vụ chuẩn trong `OperationFailure` (`FailureCode`):
- `NOT_FOUND`, `FORBIDDEN`, `CONFLICT`, `INVALID_REQUEST`, `ALREADY_EXISTS`, `INVALID_STATE`, `INTERNAL_ERROR`
- `NODE_NOT_FOUND`, `BAD_REQUEST`, `MOVE_FAILED`, `NAME_ALREADY_EXISTS`, `INVALID_DESTINATION`, `DESTINATION_ERROR`, `DESTINATION_NOT_FOUND`

---

## 3. Danh mục API

| Nhóm chức năng | Method | Đường dẫn API | Mô tả |
|---|---|---|---|
| **Storage User & Quota** | `GET` | `/storage-service/storage/users/quota/{id}` | Lấy thông tin Quota và phần trăm dung lượng đã dùng |
| | `GET` | `/storage-service/storage/users/storage/{id}` | Lấy metadata không gian lưu trữ và node gốc của user |
| | `POST` | `/storage-service/storage/users/init` | Khởi tạo storage và thư mục root cho user |
| | `POST` | `/storage-service/storage/users/{userId}/init` | Khởi tạo nhanh storage bằng path variable |
| **Quản lý Thư mục** | `POST` | `/storage-service/nodes/folders` | Tạo mới một thư mục con |
| **Duyệt & Thao tác Node** | `GET` | `/storage-service/nodes` | Duyệt danh sách file/folder con (hỗ trợ lọc và phân trang) |
| | `GET` | `/storage-service/nodes/{id}` | Lấy thông tin metadata chi tiết của một node |
| | `PATCH` | `/storage-service/nodes/{id}` | Đổi tên hoặc cập nhật trạng thái quan trọng (starred) |
| | `DELETE` | `/storage-service/nodes` | Xóa mềm danh sách node (chuyển vào thùng rác) |
| | `POST` | `/storage-service/nodes/move` | Di chuyển danh sách node sang thư mục đích |
| **Quản lý Thùng rác** | `GET` | `/storage-service/nodes/trash` | Danh sách node đang nằm trong thùng rác |
| | `POST` | `/storage-service/nodes/trash/restore` | Khôi phục danh sách node từ thùng rác |
| | `DELETE` | `/storage-service/nodes/trash/permanent` | Xóa vĩnh viễn các node hoặc dọn sạch thùng rác |
| **Tải lên Tệp (Upload)** | `POST` | `/storage-service/files/upload-urls` | Xin cấp Presigned URL để upload file lên MinIO |
| **Tải xuống Tệp (Download)** | `POST` | `/storage-service/files/download-urls` | Xin cấp Presigned URL để tải file từ MinIO |
| **Chia sẻ File/Folder & Phân quyền** | `POST` | `/storage-service/nodes/{id}/share` | Thiết lập chia sẻ (`USER_GROUP` hoặc `PUBLIC_LINK`) với đúng 1 quyền (`VIEW`/`EDIT`) |
| | `GET` | `/storage-service/nodes/{id}/share` | Xem chi tiết cấu hình chia sẻ hiện tại (Chỉ Chủ sở hữu) |
| | `PUT` | `/storage-service/nodes/{id}/share/users` | Cập nhật danh sách user hoặc đổi quyền nhóm user |
| | `DELETE` | `/storage-service/nodes/{id}/share` | Hủy toàn bộ chia sẻ trên node về trạng thái riêng tư (`PRIVATE`) |
| | `PUT` | `/storage-service/nodes/{id}/password` | Đặt hoặc thay đổi mật khẩu bảo vệ node (Chủ sở hữu) |
| | `POST` | `/storage-service/nodes/{id}/verify-password` | Nhập mật khẩu để mở khóa node và nhận `share_access_token` |
| | `GET` | `/storage-service/nodes/shared-with-me` | Danh sách file/folder được chia sẻ với user hiện tại |
| | `GET` | `/storage-service/shares/public/{token}` | Xem thông tin và truy cập file/folder qua Public Link |
| *(Xem đặc tả chi tiết tại [node_sharing.md](storage/node_sharing.md))* | | | |

---

## 4. Chi tiết từng Nhóm API

### 4.1. Nhóm Storage User & Quota (`StorageUserController`)

Base route: `/storage-service/storage/users` (hoặc `/storage/users`)

#### 4.1.1. Lấy thông tin hạn mức lưu trữ (Quota)

- **Endpoint:** `GET /storage-service/storage/users/quota/{id}`
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")`
- **Path Parameter:** `id` (UUID, Bắt buộc) - ID của người dùng
- **Response `200 OK` (`ApiResponse<UserQuotaResponse>`):**

```json
{
  "success": true,
  "message": "Lấy thông tin thành công",
  "code": 200,
  "data": {
    "user_id": "7ac9e9d3-79a2-44ec-806d-7029af302116",
    "storage_id": "9bc1e284-56a1-43ef-92e1-884021235678",
    "current_quota": 52428800,
    "max_quota": 10737418240,
    "unit": "MB",
    "usage_percentage": 0.49,
    "status": "NORMAL"
  },
  "timestamp": "2026-10-30T10:00:00Z"
}
```

#### 4.1.2. Lấy thông tin không gian lưu trữ (User Storage)

- **Endpoint:** `GET /storage-service/storage/users/storage/{id}`
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")`
- **Path Parameter:** `id` (UUID, Bắt buộc) - ID của người dùng
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

#### 4.1.3. Khởi tạo không gian lưu trữ cho người dùng mới

- **Endpoint:** `POST /storage-service/storage/users/init`
- **Request Body (`InitUserStorageRequest`):**

```json
{
  "userId": "7ac9e9d3-79a2-44ec-806d-7029af302116",
  "username": "alice",
  "email": "alice@example.com",
  "customQuotaBytes": 10737418240
}
```

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `userId` | UUID | Có | ID định danh của người dùng |
| `username` | string | Không | Tên tài khoản |
| `email` | string | Không | Email người dùng |
| `customQuotaBytes` | integer (Long) | Không | Hạn mức riêng (nếu không set sẽ lấy default hệ thống) |

- **Response `200 OK` hoặc `201 CREATED` (`ApiResponse<UserStorageResponse>`):** Trả về metadata của storage vừa tạo (hoặc storage đã tồn tại).

#### 4.1.4. Khởi tạo nhanh không gian lưu trữ qua ID

- **Endpoint:** `POST /storage-service/storage/users/{userId}/init`
- **Path Parameter:** `userId` (UUID, Bắt buộc)
- **Response `200 OK` (`ApiResponse<UserStorageResponse>`)**

---

### 4.2. Nhóm Quản lý Thư mục (`FsNodeFolderController`)

Base route: `/storage-service/nodes/folders`

#### 4.2.1. Tạo thư mục mới

- **Endpoint:** `POST /storage-service/nodes/folders`
- **Header:** `X-User-Id`
- **Request Body (`CreateFolderRequest`):**

```json
{
  "name": "Bao-cao-tai-chinh",
  "parent_id": "1"
}
```

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `name` | string | Có | Tên thư mục cần tạo |
| `parent_id` | string | Không | ID thư mục cha. Để `null` hoặc không truyền để tạo tại Root |

- **Response `200 OK` (`ApiResponse<NodeResponse>`):**

```json
{
  "success": true,
  "message": "Tạo thư mục thành công",
  "code": 200,
  "data": {
    "id": 12,
    "parent_id": 1,
    "path": "root.1.12",
    "name": "Bao-cao-tai-chinh",
    "is_folder": true,
    "node_status": "ACTIVE",
    "mime_type": null,
    "size_bytes": 0,
    "etag": null,
    "file_hash": null,
    "is_starred": false,
    "created_at": "2026-10-30T10:15:00",
    "updated_at": "2026-10-30T10:15:00",
    "deleted_at": null
  },
  "timestamp": "2026-10-30T10:15:00Z"
}
```

---

### 4.3. Nhóm Duyệt & Thao tác Node (`FsNodeController`)

Base route: `/storage-service/nodes`  
Header bắt buộc: `X-User-Id: <UUID>`

#### 4.3.1. Lấy danh sách file/folder con (Duyệt thư mục)

- **Endpoint:** `GET /storage-service/nodes`
- **Query Parameters:**

| Tham số | Kiểu | Mặc định | Mô tả |
|---|---|:---:|---|
| `parent_id` | string | `null` | ID thư mục cha (bỏ trống để lấy thư mục gốc) |
| `node_type` | string | `null` | Lọc theo loại node: `FILE` hoặc `FOLDER` |
| `name` | string | `null` | Lọc theo tên file/thư mục |
| `mime_type` | string | `null` | Lọc theo MIME type (ví dụ `application/pdf`) |
| `page` | integer | `0` | Chỉ số trang |
| `size` | integer | `20` | Kích thước trang |
| `sort` | string/list | `name,asc` | Tiêu chí sắp xếp |

- **Response `200 OK` (`ApiResponse<PagedResponse<NodeResponse>>`):**

```json
{
  "success": true,
  "message": "Lấy danh sách thành công",
  "code": 200,
  "data": {
    "content": [
      {
        "id": 12,
        "parent_id": 1,
        "path": "root.1.12",
        "name": "Bao-cao-tai-chinh",
        "is_folder": true,
        "node_status": "ACTIVE",
        "mime_type": null,
        "size_bytes": 0,
        "etag": null,
        "file_hash": null,
        "is_starred": false,
        "created_at": "2026-10-30T10:15:00",
        "updated_at": "2026-10-30T10:15:00",
        "deleted_at": null
      }
    ],
    "page": 0,
    "size": 20,
    "total_elements": 1,
    "total_pages": 1,
    "last": true
  },
  "timestamp": "2026-10-30T10:15:00Z"
}
```

#### 4.3.2. Lấy metadata của một node

- **Endpoint:** `GET /storage-service/nodes/{id}`
- **Path Parameter:** `id` (string, Bắt buộc) - ID của node
- **Response `200 OK` (`ApiResponse<NodeResponse>`)**

#### 4.3.3. Cập nhật thông tin node

- **Endpoint:** `PATCH /storage-service/nodes/{id}`
- **Path Parameter:** `id` (string, Bắt buộc)
- **Request Body (`UpdateNodeRequest`):**

```json
{
  "name": "Bao-cao-tai-chinh-2026",
  "starred": true
}
```

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `name` | string | Không | Tên mới cần đổi |
| `starred` | boolean | Không | Đánh dấu hoặc bỏ đánh dấu quan trọng |

- **Response `200 OK` (`ApiResponse<NodeResponse>`)**

#### 4.3.4. Xóa mềm danh sách node (Đưa vào thùng rác)

- **Endpoint:** `DELETE /storage-service/nodes`
- **Request Body (`BatchNodesRequest`):**

```json
{
  "node_ids": ["12", "15"]
}
```

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|:---:|---|
| `node_ids` | array of string | Có | Hỗ trợ alias: `nodes`, `node_id`, `nodeIds` |

- **Response `200 OK` (`ApiResponse<BatchOperationResponse<DeleteResult, NodeFailureResult>>`):**

```json
{
  "success": true,
  "message": "Xóa node hoàn tất",
  "code": 200,
  "data": {
    "total": 2,
    "success_count": 2,
    "failed_count": 0,
    "succeeded": [
      {
        "node_id": "12",
        "operation_id": "op-del-12"
      },
      {
        "node_id": "15",
        "operation_id": "op-del-15"
      }
    ],
    "failed": []
  },
  "timestamp": "2026-10-30T10:20:00Z"
}
```

#### 4.3.5. Di chuyển danh sách node sang thư mục đích

- **Endpoint:** `POST /storage-service/nodes/move`
- **Request Body (`MoveNodeRequest`):**

```json
{
  "src_node_ids": ["12", "15"],
  "dst_node_id": "5"
}
```

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|:---:|---|
| `src_node_ids` | array of string | Có | Danh sách ID các node cần di chuyển. Alias: `src_node_id`, `srcNodeIds`, `srcNodeId` |
| `dst_node_id` | string | Không | ID thư mục đích (để `null` để chuyển về Root). Alias: `dstNodeId` |

- **Response `200 OK` (`ApiResponse<BatchOperationResponse<MoveResult, NodeFailureResult>>`):**

```json
{
  "success": true,
  "message": "Di chuyển node hoàn tất",
  "code": 200,
  "data": {
    "total": 2,
    "success_count": 2,
    "failed_count": 0,
    "succeeded": [
      {
        "node_id": "12",
        "parent_id": "5"
      },
      {
        "node_id": "15",
        "parent_id": "5"
      }
    ],
    "failed": []
  },
  "timestamp": "2026-10-30T10:25:00Z"
}
```

---

### 4.4. Nhóm Quản lý Thùng rác (`FsNodeTrashController`)

Base route: `/storage-service/nodes/trash`  
Header bắt buộc: `X-User-Id: <UUID>`

#### 4.4.1. Xem danh sách các node trong thùng rác

- **Endpoint:** `GET /storage-service/nodes/trash`
- **Query Parameters:** `parent_id`, `node_type`, `name`, `mime_type`, `page` (default 0), `size` (default 20), `sort` (default `deleted_at,desc`)
- **Response `200 OK` (`ApiResponse<PagedResponse<NodeResponse>>`):** Trả về danh sách các node đã bị xóa mềm (`deleted_at != null`).

#### 4.4.2. Khôi phục danh sách node từ thùng rác

- **Endpoint:** `POST /storage-service/nodes/trash/restore`
- **Request Body (`BatchNodesRequest`):**

```json
{
  "node_ids": ["12", "15"]
}
```

- **Response `200 OK` (`ApiResponse<BatchOperationResponse<RestoreResult, NodeFailureResult>>`):**

```json
{
  "success": true,
  "message": "Thao tác thành công",
  "code": 200,
  "data": {
    "total": 2,
    "success_count": 2,
    "failed_count": 0,
    "succeeded": [
      {
        "id": "12",
        "parent_id": "1",
        "name": "Bao-cao-tai-chinh",
        "is_folder": true,
        "node_status": "ACTIVE",
        "restored_count": 3
      }
    ],
    "failed": []
  },
  "timestamp": "2026-10-30T10:30:00Z"
}
```

#### 4.4.3. Xóa vĩnh viễn node trong thùng rác hoặc dọn sạch thùng rác

- **Endpoint:** `DELETE /storage-service/nodes/trash/permanent`
- **Request Body (`BatchNodesRequest`, tùy chọn):**
  - Gửi `node_ids`: Xóa vĩnh viễn các node cụ thể được chỉ định.
  - Không gửi body hoặc gửi danh sách rỗng: Kích hoạt xóa toàn bộ thùng rác của user bất đồng bộ qua Transactional Outbox + Kafka.
- **Response `200 OK` (`ApiResponse<BatchOperationResponse<Void, NodeFailureResult>>`)**

---

### 4.5. Nhóm Tải lên Tệp - File Upload (`FileUploadController`)

Base route: `/storage-service/files` (hoặc `/files`)  
Header bắt buộc: `X-User-Id: <UUID>`

#### 4.5.1. Yêu cầu cấp Presigned URL để Upload

Hệ thống kiểm tra Quota và kiểm tra deduplication thông qua file hash:
- Nếu file đã tồn tại trên MinIO (cùng hash): hệ thống trả về ngay `instant_uploaded = true`, không cần upload lại.
- Nếu là file mới: hệ thống tạo node ở trạng thái `PENDING_UPLOAD` và cấp Presigned PUT URL có thời hạn.

- **Endpoint:** `POST /storage-service/files/upload-urls`
- **Request Body (`UploadUrlRequest`):**

```json
{
  "files": [
    {
      "parent_id": "1",
      "name": "design-spec.pdf",
      "size": 1048576,
      "mime_type": "application/pdf",
      "hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
  ]
}
```

| Thuộc tính (mỗi phần tử) | Kiểu | Bắt buộc | Ràng buộc / Mô tả |
|---|---|:---:|---|
| `parent_id` | string | Không | ID thư mục cha chứa file. Alias: `parentId` |
| `name` | string | Có | Tên tệp tin (không được để trống) |
| `size` | integer (Long) | Có | Kích thước tệp (bytes, `>= 0`) |
| `mime_type` | string | Không | Định dạng MIME của file. Alias: `mimeType` |
| `hash` | string | Có | Mã băm SHA-256 của file. Alias: `file_hash`, `fileHash` |

- **Response `200 OK` (`ApiResponse<UploadUrlsResponse>`):**

```json
{
  "success": true,
  "message": "Thành công",
  "code": 200,
  "data": {
    "presigned_urls": [
      {
        "node_id": "18",
        "url": "http://localhost:9000/agentic-cloud/users/alice/uuid-object-key?X-Amz-Algorithm=...",
        "instant_uploaded": false,
        "expires_at": "2026-10-30T10:45:00Z"
      }
    ]
  },
  "timestamp": "2026-10-30T10:30:00Z"
}
```

---

### 4.6. Nhóm Tải xuống Tệp - File Download (`FileDownloadController`)

Base route: `/storage-service/files` (hoặc `/files`)  
Header bắt buộc: `X-User-Id: <UUID>`

#### 4.6.1. Yêu cầu cấp Presigned URL để Download

- **Endpoint:** `POST /storage-service/files/download-urls`
- **Request Body (`DownloadUrlRequest`):**

```json
{
  "files": [
    {
      "node_id": "18"
    }
  ]
}
```

| Thuộc tính | Kiểu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `files` | array of object | Có | Danh sách các file cần download |
| `node_id` | string | Có | ID của node file cần tải xuống |

- **Response `200 OK` (`ApiResponse<DownloadUrlsResponse>`):**

```json
{
  "success": true,
  "message": "Thành công",
  "code": 200,
  "data": {
    "download_urls": [
      {
        "node_id": "18",
        "file_name": "design-spec.pdf",
        "url": "http://localhost:9000/agentic-cloud/users/alice/uuid-object-key?X-Amz-Algorithm=...",
        "expires_at": "2026-10-30T10:45:00Z",
        "mime_type": "application/pdf",
        "size_bytes": 1048576
      }
    ]
  },
  "timestamp": "2026-10-30T10:30:00Z"
}
```

---

## 5. Bảng đặc tả chi tiết các Model DTO

### 5.1. `NodeResponse`
| Tên thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `id` | Long | ID duy nhất của node |
| `parent_id` | Long | ID của thư mục cha (`null` nếu ở Root) |
| `path` | String | Materialized path (ví dụ `root.1.18`) |
| `name` | String | Tên file hoặc folder |
| `is_folder` | Boolean | `true` nếu là thư mục, `false` nếu là file |
| `node_status` | String | Trạng thái (`ACTIVE`, `PENDING_UPLOAD`, `FAILED`,...) |
| `mime_type` | String | MIME type |
| `size_bytes` | Long | Kích thước file theo bytes |
| `etag` | String | ETag của object trên MinIO/S3 |
| `file_hash` | String | SHA-256 hash của nội dung file |
| `is_starred` | Boolean | Trạng thái đánh dấu quan trọng |
| `created_at` | LocalDateTime | Thời gian tạo |
| `updated_at` | LocalDateTime | Thời gian cập nhật gần nhất |
| `deleted_at` | LocalDateTime | Thời gian chuyển vào thùng rác (`null` nếu chưa xóa) |

### 5.2. `UserQuotaResponse`
| Tên thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `user_id` | UUID | ID của người dùng |
| `storage_id` | UUID | ID của không gian lưu trữ |
| `current_quota` | Long | Dung lượng hiện tại đã sử dụng (bytes) |
| `max_quota` | Long | Hạn mức dung lượng tối đa được cấp (bytes) |
| `unit` | String | Đơn vị tính (`MB`, `GB`,...) |
| `usage_percentage` | Double | Tỷ lệ % dung lượng đã dùng |
| `status` | String | Trạng thái quota (`NORMAL`, `WARNING`, `EXCEEDED`) |
