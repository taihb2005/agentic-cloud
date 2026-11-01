# Nhóm API Quản lý Node & Thư mục (Storage Service)

Tài liệu này mô tả chi tiết các RESTful API dùng để duyệt cây thư mục, tạo thư mục, lấy metadata, cập nhật, di chuyển, xóa mềm và quản lý thùng rác trong `storage-service`. Dữ liệu được đồng bộ trực tiếp từ source code các controller (`FsNodeController`, `FsNodeFolderController`, `FsNodeTrashController`) và các DTO tương ứng.

---

## Thông tin Chung & Xác thực

- **Global Prefix:** `/storage-service` (hoặc gọi qua API Gateway `/api/v1/storage-service/**`)
- **Headers bắt buộc:**
  - `Authorization: Bearer <access-token>` (Client gửi tới Gateway)
  - `X-User-Id`: UUID của người dùng (Gateway inject vào request trước khi forward tới storage-service)
  - `X-User-Roles`: Danh sách quyền của người dùng
- **Định dạng dữ liệu:** `application/json`

---

## 1. Duyệt danh sách File & Folder con

Lấy danh sách các file/folder con trực tiếp của một thư mục (hoặc thư mục gốc), có hỗ trợ tìm kiếm theo tên, loại node, MIME type và phân trang.

- **Method & Endpoint:** `GET /storage-service/nodes`
- **Controller:** `FsNodeController.getChildNodes`
- **Đầu vào (Query Parameters):**

| Tham số | Kiểu dữ liệu | Mặc định | Mô tả |
|---|---|:---:|---|
| `parent_id` | String | `null` | ID của thư mục cha. Để `null` hoặc bỏ trống để lấy thư mục gốc (Root) |
| `node_type` | String | `null` | Bộ lọc loại node: `FILE` hoặc `FOLDER`. Bỏ trống để lấy cả hai |
| `name` | String | `null` | Lọc theo tên file/folder (tìm kiếm chứa từ khóa) |
| `mime_type` | String | `null` | Lọc theo kiểu MIME (ví dụ: `application/pdf`) |
| `page` | Integer | `0` | Chỉ số trang hiện tại (bắt đầu từ 0) |
| `size` | Integer | `20` | Số lượng bản ghi trên mỗi trang |
| `sort` | String / List | `name,asc` | Tiêu chí sắp xếp (ví dụ: `name,asc` hoặc `created_at,desc`) |

- **Đầu ra `200 OK` (`ApiResponse<PagedResponse<NodeResponse>>`):**

```json
{
  "success": true,
  "message": "Lấy danh sách thành công",
  "code": 200,
  "data": {
    "content": [
      {
        "id": 5,
        "parent_id": 1,
        "path": "root.1.5",
        "name": "Tai-lieu-du-an-2026",
        "is_folder": true,
        "node_status": "ACTIVE",
        "mime_type": null,
        "size_bytes": 0,
        "etag": null,
        "file_hash": null,
        "is_starred": false,
        "created_at": "2026-10-30T10:00:00",
        "updated_at": "2026-10-30T10:00:00",
        "deleted_at": null
      },
      {
        "id": 8,
        "parent_id": 1,
        "path": "root.1.8",
        "name": "hop-dong-lao-dong.pdf",
        "is_folder": false,
        "node_status": "ACTIVE",
        "mime_type": "application/pdf",
        "size_bytes": 1048576,
        "etag": "d41d8cd98f00b204e9800998ecf8427e",
        "file_hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "is_starred": true,
        "created_at": "2026-10-30T10:05:00",
        "updated_at": "2026-10-30T10:05:00",
        "deleted_at": null
      }
    ],
    "page": 0,
    "size": 20,
    "total_elements": 2,
    "total_pages": 1,
    "last": true
  },
  "timestamp": "2026-10-30T10:10:00Z"
}
```

---

## 2. Tạo Thư mục mới

Tạo một folder mới bên trong thư mục chỉ định hoặc tại thư mục gốc.

- **Method & Endpoint:** `POST /storage-service/nodes/folders`
- **Controller:** `FsNodeFolderController.createFolder`
- **Đầu vào (Request Body - `CreateFolderRequest`):**

```json
{
  "name": "Bao-cao-tai-chinh",
  "parent_id": "1"
}
```

| Thuộc tính | Kiểu dữ liệu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `name` | String | Có | Tên thư mục mới cần tạo |
| `parent_id` | String | Không | ID thư mục cha. Truyền `null` hoặc bỏ trống để tạo tại Root |

- **Đầu ra `200 OK` (`ApiResponse<NodeResponse>`):**

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

## 3. Lấy Chi tiết Metadata của một Node

Lấy thông tin metadata của một node (cả file hoặc folder) dựa trên ID.

- **Method & Endpoint:** `GET /storage-service/nodes/{id}`
- **Controller:** `FsNodeController.getFolder`
- **Đầu vào (Path Variable):**
  - `id` (String, Bắt buộc): ID của node cần tra cứu
- **Đầu ra `200 OK` (`ApiResponse<NodeResponse>`):**

```json
{
  "success": true,
  "message": "Lấy metadata thành công",
  "code": 200,
  "data": {
    "id": 8,
    "parent_id": 1,
    "path": "root.1.8",
    "name": "hop-dong-lao-dong.pdf",
    "is_folder": false,
    "node_status": "ACTIVE",
    "mime_type": "application/pdf",
    "size_bytes": 1048576,
    "etag": "d41d8cd98f00b204e9800998ecf8427e",
    "file_hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "is_starred": true,
    "created_at": "2026-10-30T10:05:00",
    "updated_at": "2026-10-30T10:05:00",
    "deleted_at": null
  },
  "timestamp": "2026-10-30T10:16:00Z"
}
```

---

## 4. Cập nhật Thông tin Node (Đổi tên, Đánh dấu Starred)

- **Method & Endpoint:** `PATCH /storage-service/nodes/{id}`
- **Controller:** `FsNodeController.updateNode`
- **Đầu vào (Path Variable):**
  - `id` (String, Bắt buộc): ID của node cần cập nhật
- **Đầu vào (Request Body - `UpdateNodeRequest`):**

```json
{
  "name": "hop-dong-lao-dong-2026.pdf",
  "starred": true
}
```

| Thuộc tính | Kiểu dữ liệu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `name` | String | Không | Tên mới của node (đổi tên) |
| `starred` | boolean | Không | Đánh dấu sao quan trọng (`true`) hoặc bỏ đánh dấu (`false`) |

- **Đầu ra `200 OK` (`ApiResponse<NodeResponse>`):** Trả về metadata node sau khi cập nhật.

---

## 5. Di chuyển Node sang Thư mục đích

Di chuyển một hoặc nhiều file/folder sang thư mục đích.

- **Method & Endpoint:** `POST /storage-service/nodes/move`
- **Controller:** `FsNodeController.moveNodes`
- **Đầu vào (Request Body - `MoveNodeRequest`):**

```json
{
  "src_node_ids": ["8", "12"],
  "dst_node_id": "5"
}
```

| Thuộc tính | Kiểu dữ liệu | Bắt buộc | Ghi chú & Alias hỗ trợ |
|---|---|:---:|---|
| `src_node_ids` | List<String> | Có | Danh sách ID các node cần di chuyển. Alias: `src_node_id`, `srcNodeIds`, `srcNodeId` |
| `dst_node_id` | String | Không | ID thư mục đích. Truyền `null` để di chuyển về thư mục gốc (Root). Alias: `dstNodeId` |

- **Đầu ra `200 OK` (`ApiResponse<BatchOperationResponse<MoveResult, NodeFailureResult>>`):**

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
        "node_id": "8",
        "parent_id": "5"
      },
      {
        "node_id": "12",
        "parent_id": "5"
      }
    ],
    "failed": []
  },
  "timestamp": "2026-10-30T10:20:00Z"
}
```

---

## 6. Xóa mềm Node (Đưa vào Thùng rác)

Chuyển một hoặc nhiều node vào thùng rác (`deleted_at != null`). Nếu node là thư mục, hệ thống tự động xử lý đệ quy các con cháu bên trong.

- **Method & Endpoint:** `DELETE /storage-service/nodes`
- **Controller:** `FsNodeController.softDeleteNodes`
- **Đầu vào (Request Body - `BatchNodesRequest`):**

```json
{
  "node_ids": ["8", "12"]
}
```

| Thuộc tính | Kiểu dữ liệu | Bắt buộc | Ghi chú & Alias hỗ trợ |
|---|---|:---:|---|
| `node_ids` | List<String> | Có | Danh sách ID các node cần xóa mềm. Alias: `nodes`, `node_id`, `nodeIds` |

- **Đầu ra `200 OK` (`ApiResponse<BatchOperationResponse<DeleteResult, NodeFailureResult>>`):**

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
        "node_id": "8",
        "operation_id": "op-del-8"
      },
      {
        "node_id": "12",
        "operation_id": "op-del-12"
      }
    ],
    "failed": []
  },
  "timestamp": "2026-10-30T10:25:00Z"
}
```

---

## 7. Xem Danh sách Node trong Thùng rác

Liệt kê các node đang nằm trong thùng rác của người dùng.

- **Method & Endpoint:** `GET /storage-service/nodes/trash`
- **Controller:** `FsNodeTrashController.getTrash`
- **Đầu vào (Query Parameters):**

| Tham số | Kiểu dữ liệu | Mặc định | Mô tả |
|---|---|:---:|---|
| `parent_id` | String | `null` | ID thư mục cha |
| `node_type` | String | `null` | Bộ lọc loại node: `FILE` hoặc `FOLDER` |
| `name` | String | `null` | Lọc theo tên |
| `mime_type` | String | `null` | Lọc theo MIME type |
| `page` | Integer | `0` | Trang hiện tại |
| `size` | Integer | `20` | Số bản ghi trên mỗi trang |
| `sort` | String / List | `deleted_at,desc` | Tiêu chí sắp xếp |

- **Đầu ra `200 OK` (`ApiResponse<PagedResponse<NodeResponse>>`):**

```json
{
  "success": true,
  "message": "Lấy danh sách thành công",
  "code": 200,
  "data": {
    "content": [
      {
        "id": 8,
        "parent_id": 1,
        "path": "root.1.8",
        "name": "hop-dong-lao-dong.pdf",
        "is_folder": false,
        "node_status": "ACTIVE",
        "mime_type": "application/pdf",
        "size_bytes": 1048576,
        "etag": "d41d8cd98f00b204e9800998ecf8427e",
        "file_hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "is_starred": true,
        "created_at": "2026-10-30T10:05:00",
        "updated_at": "2026-10-30T10:05:00",
        "deleted_at": "2026-10-30T10:25:00"
      }
    ],
    "page": 0,
    "size": 20,
    "total_elements": 1,
    "total_pages": 1,
    "last": true
  },
  "timestamp": "2026-10-30T10:26:00Z"
}
```

---

## 8. Khôi phục Node từ Thùng rác

Khôi phục các node đã xóa mềm về vị trí ban đầu (hoặc về Root nếu thư mục cha cũ đã bị xóa).

- **Method & Endpoint:** `POST /storage-service/nodes/trash/restore`
- **Controller:** `FsNodeTrashController.restoreNodes`
- **Đầu vào (Request Body - `BatchNodesRequest`):**

```json
{
  "node_ids": ["8"]
}
```

- **Đầu ra `200 OK` (`ApiResponse<BatchOperationResponse<RestoreResult, NodeFailureResult>>`):**

```json
{
  "success": true,
  "message": "Thao tác thành công",
  "code": 200,
  "data": {
    "total": 1,
    "success_count": 1,
    "failed_count": 0,
    "succeeded": [
      {
        "id": "8",
        "parent_id": "1",
        "name": "hop-dong-lao-dong.pdf",
        "is_folder": false,
        "node_status": "ACTIVE",
        "restored_count": 1
      }
    ],
    "failed": []
  },
  "timestamp": "2026-10-30T10:30:00Z"
}
```

---

## 9. Xóa Vĩnh viễn Node khỏi Thùng rác

Hỗ trợ 2 chế độ:
1. **Xóa danh sách node cụ thể:** Truyền danh sách `node_ids`.
2. **Dọn sạch toàn bộ thùng rác (Empty Trash):** Không truyền body hoặc danh sách `node_ids` rỗng. Hành động này sẽ được ghi nhận vào Outbox và kích hoạt xử lý bất đồng bộ qua Kafka để xóa object trên MinIO và dọn DB.

- **Method & Endpoint:** `DELETE /storage-service/nodes/trash/permanent`
- **Controller:** `FsNodeTrashController.permanentDeleteTrash`
- **Đầu vào (Request Body - `BatchNodesRequest`, không bắt buộc):**

```json
{
  "node_ids": ["8"]
}
```

- **Đầu ra `200 OK` (`ApiResponse<BatchOperationResponse<Void, NodeFailureResult>>`):**

```json
{
  "success": true,
  "message": "Thao tác thành công",
  "code": 200,
  "data": {
    "total": 1,
    "success_count": 1,
    "failed_count": 0,
    "succeeded": [],
    "failed": []
  },
  "timestamp": "2026-10-30T10:35:00Z"
}
```
