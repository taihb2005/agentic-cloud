# Storage Service - User & Quota API

Tài liệu này mô tả chi tiết đầu vào và đầu ra (định dạng JSON) của nhóm API quản lý thông tin, thống kê và giới hạn lưu trữ (quota) của người dùng trong hệ thống `storage-service`.

## 1. Kiểm tra dung lượng lưu trữ (GET /storage/users/quota)

Lấy thông tin dung lượng đã sử dụng và tổng dung lượng được cấp phép của người dùng hiện tại (dựa vào `X-User-Id` từ API Gateway).

- **Request Params / Body:** Không có
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Lấy thông tin dung lượng thành công",
    "code": 200,
    "data": {
      "user_id": "uuid-cua-user",
      "storage_id": "uuid-của-storage-vừa-tạo",
      "current_quota": 5368709120,
      "max_quota": 16106127360, 
      "unit": "MB",
      "usage_percentage": 33.33,
      "status": "NORMAL" 
    },
    "timestamp": "2026-10-20T10:00:00Z"
  }
  ```

## 6. Đang ký người dùng mới (PUT /storage/users/me)

Lấy thông tin storage hiện tại của người dùng

- **Header**
X-User-Id: Id của người dùng
X-User-Roles: quyền của người dùng

- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Storage created successfully",
    "code": 200,
    "data": {
      "storage_id": "uuid-của-storage-vừa-tạo",
      "storage_prefix": "bucket/taihb2005",
      "storage_bucket_name": "bucket-name",
      "storage_max_quota_bytes": "9999",
      "storage_current_quota_bytes": "0",
      "root_node_id": "uuid",
      "root_node_name": "root",
      "root_node_path": "root"
    },
    "timestamp": "2026-10-20T10:00:00Z"
  }
  ```


## 2. Xem thống kê dữ liệu lưu trữ (GET /storage/users/me/stats)

Trả về các thống kê tổng quan về không gian làm việc của người dùng để hiển thị trên Dashboard.

- **Request Params / Body:** Không có
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Lấy thống kê lưu trữ thành công",
    "code": 200,
    "data": {
      "totalFiles": 1245,
      "totalFolders": 84,
      "starredCount": 15,
      "sharedWithMeCount": 32,
      "trashCount": 10,
      "storageByTypes": {
        "application/pdf": 1073741824,
        "image/jpeg": 2147483648,
        "video/mp4": 1048576000,
        "others": 1098907648
      }
    },
    "timestamp": "2026-10-20T10:00:00Z"
  }
  ```

## 5. (Admin/Internal) Cấp phát/Nâng cấp dung lượng (PUT /storage/users/{id})

API nội bộ dành cho hệ thống quản trị hoặc xử lý webhook sau khi thanh toán để nâng cấp dung lượng (Ví dụ: nâng cấp từ gói Free 15GB lên Pro 100GB).

- **Path Variable:** `userId` (UUID) - ID của người dùng cần cập nhật.
- **Request Body:**
  ```json
  {
    "totalBytes": 107374182400, // Cấp hạn mức mới (100GB)
    "reason": "UPGRADE_PLAN_PRO"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Cập nhật giới hạn dung lượng thành công",
    "code": 200,
    "data": {
      "userId": "uuid-cua-user",
      "usedBytes": 5368709120,
      "totalBytes": 107374182400,
      "usagePercentage": 5.0,
      "status": "NORMAL"
    },
    "timestamp": "2026-10-20T10:00:00Z"
  }
  ```

## 6. Đang ký người dùng mới (PUT /storage/users/init)

API nội bộ dành cho user-service gọi sang, dùng để cấp phát dung lượng cho user mới đăng ký

- **Kafka Event Trigger**

  ```json
  {
    "event_id": "user id cuar nguowif dùng",
    "event_type": "USER_CREATED",
    "payload": {
      "id": "uuid-cua-user",
      "username": "taihb2005",
      "email": "khuonganhtai05@gmail.com"
    }
    "timestamp": "2026-10-20T10:00:00Z"
  }

  ```

- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Storage created successfully",
    "code": 200,
    "data": {
      "storage_id": "uuid-của-storage-vừa-tạo",
      "storage_prefix": "bucket/taihb2005",
      "storage_bucket_name": "bucket-name",
      "storage_max_quota_bytes": "9999",
      "storage_current_quota_bytes": "0",
      "root_node_id": "uuid",
      "root_node_name": "root",
      "root_node_path": "root",
      "created_at": "timestamp"
    },
    "timestamp": "2026-10-20T10:00:00Z"
  }
  ```

## 6. Lấy thông tin người dùng (GET /storage/users/{id})

Dùng để lấy thông tin người dùng trên cloud

- **Path Variable:** `userId` (UUID) - ID của người dùng cần cập nhật.

- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Lấy thông tin thành công",
    "code": 200,
    "data": {
      "storage_id": "uuid-của-storage-vừa-tạo",
      "storage_prefix": "bucket/taihb2005",
      "storage_bucket_name": "bucket-name",
      "storage_max_quota_bytes": "9999",
      "storage_current_quota_bytes": "0",
      "root_node_id": "uuid",
      "root_node_name": "root",
      "root_node_path": "root",
      "created_at": "timestamp",
      "updated_at": "timestamp"
    },
    "timestamp": "2026-10-20T10:00:00Z"
  }
  ```