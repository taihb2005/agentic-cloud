# Chi tiết Đầu vào / Đầu ra Nhóm API Duyệt thư mục (Định dạng JSON)

Tất cả các API trong nhóm này đều yêu cầu header xác thực:
`Authorization: Bearer <access-token>`

Dưới đây là mô tả chi tiết các trường đầu vào (Path/Query/Body) và Payload đầu ra (Response) ở định dạng JSON.

---

(Global prefix: /storage-service)

## 1. Lấy danh sách file/folder con (Duyệt thư mục)

**Method & Endpoint:** `GET /nodes`

**Required header**
X-User-Id: Id của người dùng đã được inject
X-User-Roles: Vai trò của người dùng được inject

**Đầu vào (Query Parameters):**

```json
{
  "parent_id": 4,
  "node_type": "FOLDER",
  "page": 0,
  "size": 20,
  "sort": "name" //Truyền dạng list
}
```

_Giải thích Query Params:_

- `parentId` (Long, null): ID của thư mục cha. Nếu truyền `null` hoặc "root" hoặc bỏ trống, hệ thống trả về thư mục gốc (Root).
- `nodeType` (String, null): Bộ lọc loại node. Chấp nhận `FILE` hoặc `FOLDER`. Bỏ trống để lấy cả hai.
- `name` (String, null): Bộ lọc tên thư mục hoặc file trong thư mục hiện tại
- `mimeType` (String, null): Bộ lọc loại file, có thể phát triển sau
- `page` (Integer): Trang hiện tại (mặc định: `0`).
- `size` (Integer): Số lượng bản ghi/trang (mặc định: `20`).
- `sort` (String): Tiêu chí sắp xếp (mặc định: `name,asc,desc`).

**Đầu ra (200 OK):**

```json
{
  "success": true,
  "message": "Lấy danh sách thành công",
  "code": 200,
  "data": {
    "content": [
      {
        "id": 5,
        "parent_id": 4,
        "path": "root.1.4.5",
        "name": "Tai-lieu-du-an-2026",
        "is_folder": true,
        "node_status": "ACTIVE",
        "mime_type": "null",
        "size_bytes": 0,
        "etag": "random-etag-shenanigan",
        "is_starred": false,
        "created_at": "2026-10-18T10:00:00Z",
        "updated_at": "2026-10-18T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "total_elements": 1,
    "total_pages": 1,
    "last": true
  },
  "timestamp": "2026-10-20T16:48:00Z"
}
```

---

## 2. Tạo thư mục mới

**Method & Endpoint:** `POST /nodes/folders`

**Required header**
X-User-Id: Id của người dùng đã được inject
X-User-Roles: Vai trò của người dùng được inject

**Đầu vào (Request Body):**

```json
{
  "name": "Tài liệu Cloud",
  "parent_id": "24f47bb8-b1b7-4e41-a704-dc67cd4e23e0"
}
```

_Giải thích Body:_

- `name` (String, Bắt buộc): Tên thư mục mới.
- `parent_id` (UUID, null): UUID thư mục cha. Truyền `null` để tạo ở Root.

**Đầu ra (201 Created):**

```json
{
  "success": true,
  "message": "Tạo thư mục thành công",
  "code": 201,
  "data": {
    "id": "e119bfe6-d566-41cb-992f-123456789abc",
    "parent_id": "24f47bb8-b1b7-4e41-a704-dc67cd4e23e0",
    "name": "Tài liệu Cloud",
    "is_folder": true,
    "node_status": "ACTIVE",
    "mime_type": null,
    "size_bytes": 0,
    "etag": null,
    "is_starred": false,
    "created_at": "2026-10-20T16:48:00Z",
    "updated_at": "2026-10-20T16:48:00Z"
  },
  "timestamp": "2026-10-20T16:48:00Z"
}
```

---

## 3. Lấy chi tiết metadata của một Node

**Method & Endpoint:** `GET /nodes/{node_id}`

**Đầu vào (Path Variable):**

- `node_id` (UUID): ID của file/folder.

**Đầu ra (200 OK):**

```json
{
  "success": true,
  "message": "Thành công",
  "code": 200,
  "data": {
    "id": "e119bfe6-d566-41cb-992f-123456789abc",
    "parent_id": "24f47bb8-b1b7-4e41-a704-dc67cd4e23e0",
    "name": "hop-dong.pdf",
    "is_folder": false,
    "node_status": "ACTIVE",
    "mime_type": "application/pdf",
    "size_bytes": 1048576,
    "etag": "d41d8cd98f00b204e9800998ecf8427e",
    "is_starred": true,
    "created_at": "2026-10-18T10:00:00Z",
    "updated_at": "2026-10-18T10:05:00Z"
  },
  "timestamp": "2026-10-20T16:48:00Z"
}
```

---

## 4. Cập nhật thông tin / Di chuyển Node

**Method & Endpoint:** `PATCH /nodes/{node_id}`

**Đầu vào (Path Variable):**

- `node_id` (UUID): ID của node cần sửa.

**Đầu vào (Request Body):**
_Chỉ gửi các trường cần cập nhật, các trường không gửi sẽ giữ nguyên giá trị cũ._

```json
{
  "name": "hop-dong-da-ky.pdf",
  "parent_id": null,
  "is_starred": true
}
```

_Giải thích Body:_

- `name`: Tên mới (đổi tên).
- `parent_id`: Chuyển node sang thư mục khác (gửi `null` để đưa ra Root).
- `is_starred`: Đánh dấu/Bỏ đánh dấu quan trọng.

**Đầu ra (200 OK):**

```json
{
  "success": true,
  "message": "Cập nhật thành công",
  "code": 200,
  "data": {
    "id": "e119bfe6-d566-41cb-992f-123456789abc",
    "parent_id": null,
    "name": "hop-dong-da-ky.pdf",
    "is_folder": false,
    "node_status": "ACTIVE",
    "mime_type": "application/pdf",
    "size_bytes": 1048576,
    "etag": "d41d8cd98f00b204e9800998ecf8427e",
    "is_starred": true,
    "created_at": "2026-10-18T10:00:00Z",
    "updated_at": "2026-10-20T16:48:00Z"
  },
  "timestamp": "2026-10-20T16:48:00Z"
}
```

---

## 5. Xóa mềm Node (Đưa vào thùng rác)

**Method & Endpoint:** `DELETE /nodes/{node_id}`

**Đầu vào (Path Variable):**

- `node_id` (UUID): ID của node cần xóa. (Nếu là thư mục, sẽ xóa đệ quy).

**Đầu ra (200 OK):**

```json
{
  "success": true,
  "message": "Đã chuyển vào thùng rác thành công",
  "code": 200,
  "data": null,
  "timestamp": "2026-10-20T16:48:00Z"
}
```

---

## 6. Khôi phục Node đã xóa

**Method & Endpoint:** `POST /nodes/{node_id}/restore`

**Đầu vào (Path Variable):**

- `node_id` (UUID): ID của node nằm trong thùng rác cần khôi phục.

**Đầu ra (200 OK):**

```json
{
  "success": true,
  "message": "Khôi phục thành công",
  "code": 200,
  "data": {
    "id": "e119bfe6-d566-41cb-992f-123456789abc",
    "parent_id": "24f47bb8-b1b7-4e41-a704-dc67cd4e23e0",
    "name": "hop-dong-da-ky.pdf",
    "is_folder": false,
    "node_status": "ACTIVE",
    "mime_type": "application/pdf",
    "size_bytes": 1048576,
    "etag": "d41d8cd98f00b204e9800998ecf8427e",
    "is_starred": true,
    "created_at": "2026-10-18T10:00:00Z",
    "updated_at": "2026-10-20T16:48:00Z"
  },
  "timestamp": "2026-10-20T16:48:00Z"
}
```
