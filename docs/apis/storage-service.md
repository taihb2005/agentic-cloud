# Storage Service API

Tài liệu này mô tả API cho `storage-service` để quản lý cây metadata file/folder,
upload/download object trên S3-compatible storage và chia sẻ node.

## Phạm vi và thông tin chung

- Target domain: `localhost:8082` (cổng expose trong mạng nội bộ của storage là 8082)

- Public base URL qua API Gateway: `http://localhost:8089/api/v1/storage-service`
- API Gateway hiện định tuyến `/api/v1/storage-service/**` tới `storage-service`
  tại `http://localhost:8082`.
- Content type: `application/json`, ngoại trừ request upload trực tiếp lên URL
  được ký bởi object storage.
- Các API dưới đây yêu cầu `Authorization: Bearer <access-token>`. Gateway xác
  thực JWT, bỏ header `Authorization` trước khi chuyển tiếp và thêm `X-User-Id`
  chứa UUID của user. Service phải lấy chủ sở hữu từ identity đã xác thực, không
  nhận `userId` làm căn cứ phân quyền từ body/query.
- Response JSON của API nghiệp vụ dùng envelope chung `ApiResponse`:

```json
{
  "success": true,
  "message": "Folder created",
  "code": 201,
  "data": {},
  "timestamp": "2026-10-18T10:00:00Z"
}
```

Các thông tin api trả ra đều được đặt ở trong cụm `data`. Nếu không mô tả gì thêm, mặc định data được trả ra trong cụm này.

## Quy ước metadata và quyền truy cập

- `nodeId`, `parentId`, `userId`, `shareId` là UUID; `userId` được lấy từ
  `X-User-Id` do gateway xác thực.
- Folder là node có `isFolder: true`; file là node có `isFolder: false`.
  Folder không có object key. File chỉ chuyển sang trạng thái sẵn sàng sau khi
  upload được xác nhận với object storage.
- `parentId` không truyền hoặc `null` có nghĩa là thư mục gốc của user hiện tại.
  Mọi truy vấn và thao tác phải kiểm tra quyền trên toàn bộ đường dẫn node, không
  chỉ dựa vào việc biết UUID.
- API chỉ trả node chưa bị xóa mặc định. `isDeleted`/`deletedAt` dùng cho thùng
  rác; xóa ở phiên bản này là xóa mềm metadata và không đồng nghĩa xóa object
  khỏi S3.
- `nodeStatus` là chuỗi trong entity, chưa có enum hay quy ước trạng thái trong
  source. Đề xuất chuẩn hóa các giá trị `PENDING_UPLOAD`, `ACTIVE`, `FAILED`;
  folder được tạo ở trạng thái `ACTIVE`.
- Permission code được lưu qua `FsNodePermission.code`, nhưng danh sách code
  chưa được khai báo trong source. Service cần seed và công bố các code hỗ trợ
  (ví dụ `READ`, `WRITE`) trước khi bật API phân quyền chia sẻ.

## API đề xuất

### Nhóm liên quan đến người dùng

### Duyệt và quản lý cây file/folder

| Method   | Path                      | Request                                           | Response                     | Mô tả                                                                                                    |
| -------- | ------------------------- | ------------------------------------------------- | ---------------------------- | -------------------------------------------------------------------------------------------------------- |
| `GET`    | `/nodes`                  | Query: `parentId`, `kind`, `page`, `size`, `sort` | `200` — trang `NodeResponse` | Liệt kê con trực tiếp trong một folder; bỏ `parentId` để liệt kê root. `kind` nhận `FILE` hoặc `FOLDER`. |
| `POST`   | `/nodes/folders`          | `CreateFolderRequest`                             | `201` — `NodeResponse`       | Tạo folder trong root hoặc folder cha.                                                                   |
| `GET`    | `/nodes/{nodeId}`         | Path: `nodeId`                                    | `200` — `NodeResponse`       | Lấy metadata của một node; không trả danh sách toàn bộ con.                                              |
| `PATCH`  | `/nodes/{nodeId}`         | `UpdateNodeRequest`                               | `200` — `NodeResponse`       | Đổi tên, di chuyển file/folder và/hoặc đổi trạng thái starred.                                           |
| `DELETE` | `/nodes/{nodeId}`         | Path: `nodeId`                                    | `200` — message              | Xóa mềm node. Khi xóa folder, áp dụng đệ quy cho cây con.                                                |
| `POST`   | `/nodes/{nodeId}/restore` | Path: `nodeId`                                    | `200` — `NodeResponse`       | Khôi phục node đã xóa; từ chối nếu folder cha đang bị xóa hoặc phát sinh trùng tên.                      |

Query phân trang mặc định `page=0`, `size=20`; giới hạn `size` tối đa cần được
cấu hình. Thứ tự mặc định nên là `name,asc`. Chỉ cho phép sort theo các trường
metadata được API công bố.

`CreateFolderRequest`:

```json
{
  "name": "reports",
  "parentId": "24f47bb8-b1b7-4e41-a704-dc67cd4e23e0"
}
```

`UpdateNodeRequest` nhận một hoặc nhiều thuộc tính; thuộc tính không gửi giữ
nguyên giá trị hiện tại. `parentId: null` chuyển node về root.

```json
{
  "name": "annual-reports",
  "parentId": "24f47bb8-b1b7-4e41-a704-dc67cd4e23e0",
  "isStarred": true
}
```

Service phải từ chối di chuyển folder vào chính nó hoặc hậu duệ của nó, đồng
thời kiểm tra quyền ghi trên node nguồn và folder đích. Kiểm tra tên trùng phải
thực hiện trong cùng parent trước khi insert/update.

### Upload và download object

| Method | Path                           | Request                 | Response                        | Mô tả                                                                          |
| ------ | ------------------------------ | ----------------------- | ------------------------------- | ------------------------------------------------------------------------------ |
| `POST` | `/uploads`                     | `InitiateUploadRequest` | `201` — `UploadSessionResponse` | Tạo metadata file ở trạng thái `PENDING_UPLOAD` và cấp URL upload có thời hạn. |
| `POST` | `/uploads/{nodeId}/complete`   | `CompleteUploadRequest` | `200` — `NodeResponse`          | Xác minh object đã upload rồi chuyển metadata sang `ACTIVE`.                   |
| `GET`  | `/nodes/{nodeId}/download-url` | Path: `nodeId`          | `200` — `DownloadUrlResponse`   | Cấp URL download có thời hạn sau khi kiểm tra quyền đọc.                       |

`InitiateUploadRequest`:

```json
{
  "name": "report.pdf",
  "parentId": "24f47bb8-b1b7-4e41-a704-dc67cd4e23e0",
  "mimeType": "application/pdf",
  "sizeBytes": 1048576
}
```

`UploadSessionResponse`:

```json
{
  "node": {
    "id": "d098aed5-c455-4bfa-881e-33bc128282ec",
    "name": "report.pdf",
    "isFolder": false,
    "nodeStatus": "PENDING_UPLOAD"
  },
  "uploadUrl": "https://storage.example/...",
  "method": "PUT",
  "expiresAt": "2026-10-18T10:15:00Z",
  "requiredHeaders": {
    "Content-Type": "application/pdf"
  }
}
```

`CompleteUploadRequest` có thể truyền `etag` nhận được từ storage. Service phải
đọc metadata object từ S3/MinIO (ví dụ qua HEAD), kiểm tra object key thuộc upload
session, kích thước và content type theo policy rồi mới đánh dấu node `ACTIVE`;
không tin kích thước hay ETag do client tự khai báo. Thao tác complete nên idempotent
cho cùng `nodeId`.

`DownloadUrlResponse` chỉ chứa URL được ký và thời hạn:

```json
{
  "downloadUrl": "https://storage.example/...",
  "expiresAt": "2026-10-18T10:15:00Z"
}
```

URL ký phải có thời hạn ngắn, được giới hạn đúng object và method cần thiết. Không
trả `storageBucketName` hoặc `s3ObjectKey` ra client. Nếu upload không hoàn tất,
node `PENDING_UPLOAD` cần được dọn theo chính sách hết hạn; thời hạn và cơ chế dọn
cần được triển khai cùng service.

### Chia sẻ và phân quyền user

| Method   | Path                                   | Request                  | Response                           | Mô tả                                                                                          |
| -------- | -------------------------------------- | ------------------------ | ---------------------------------- | ---------------------------------------------------------------------------------------------- |
| `GET`    | `/nodes/{nodeId}/shares`               | Path: `nodeId`           | `200` — `ShareResponse`            | Liệt kê link share và user được cấp quyền trên node; chỉ owner/người có quyền quản lý share.   |
| `PUT`    | `/nodes/{nodeId}/share-link`           | `CreateShareLinkRequest` | `201` hoặc `200` — `ShareResponse` | Tạo hoặc cập nhật link công khai cho node.                                                     |
| `DELETE` | `/nodes/{nodeId}/share-link`           | Path: `nodeId`           | `200` — message                    | Thu hồi link công khai.                                                                        |
| `GET`    | `/shares/public/{token}`               | Path: token              | `200` — metadata và URL download   | Truy cập link công khai còn hiệu lực; kiểm tra hạn dùng và số lượt truy cập trước khi cấp URL. |
| `PUT`    | `/nodes/{nodeId}/permissions/{userId}` | `GrantPermissionRequest` | `200` — `NodePermissionResponse`   | Cấp hoặc cập nhật quyền cho một user.                                                          |
| `DELETE` | `/nodes/{nodeId}/permissions/{userId}` | Path: `nodeId`, `userId` | `200` — message                    | Thu hồi quyền user trên node.                                                                  |

`CreateShareLinkRequest`:

```json
{
  "expiresAt": "2026-10-25T10:00:00Z",
  "maxAccessCount": 20
}
```

Server sinh token ngẫu nhiên khó đoán; URL chia sẻ không được dùng trực tiếp UUID
của node. Khi `expiresAt` hoặc `maxAccessCount` không được gửi, policy mặc định
cần được xác định và công bố. Link thu hồi/hết hạn hoặc đã đạt giới hạn lượt truy
cập trả `404 Not Found` hoặc `410 Gone` theo policy được thống nhất; đề xuất dùng
`410 Gone` cho link đã tồn tại nhưng không còn hiệu lực.

`GrantPermissionRequest`:

```json
{
  "permissionCode": "READ"
}
```

Chỉ nhận `permissionCode` đã được seed trong `FsNodePermission`. Quyền user và
link công khai không làm mất quyền sở hữu của owner. Việc kế thừa quyền từ folder
cha cần được xác định rõ khi triển khai; đề xuất kiểm tra quyền hiệu lực trên
đường dẫn ancestor và cho phép quyền cụ thể tại node ghi đè theo policy.

## DTO đề xuất

DTO không expose entity JPA hoặc quan hệ lazy:

- `NodeResponse`: `id`, `parentId`, `name`, `isFolder`, `nodeStatus`, `mimeType`,
  `sizeBytes`, `etag`, `isStarred`, `createdAt`, `updatedAt`; chỉ thêm `children`
  khi endpoint/option yêu cầu.
- `CreateFolderRequest`: `name`, `parentId`.
- `UpdateNodeRequest`: `name`, `parentId`, `isStarred` (các trường tùy chọn).
- `InitiateUploadRequest`: `name`, `parentId`, `mimeType`, `sizeBytes`.
- `UploadSessionResponse`: `node`, `uploadUrl`, `method`, `expiresAt`,
  `requiredHeaders`.
- `CompleteUploadRequest`: `etag` tùy chọn; thông tin thực tế phải được xác minh
  từ storage.
- `DownloadUrlResponse`: `downloadUrl`, `expiresAt`.
- `ShareResponse`: thông tin share cần thiết như id, URL/token, trạng thái và hạn
  dùng; không trả `createdBy` nếu không cần cho client.
- `GrantPermissionRequest` và `NodePermissionResponse`: permission code đã được
  server công bố.

Response dùng tên thuộc tính camelCase theo Jackson. `ApiResponse` được dùng cho
response JSON của service; request/response truyền trực tiếp giữa client và S3
không bọc trong envelope này.

## Quy tắc lỗi

|                 HTTP status | Trường hợp                                                                                           |
| --------------------------: | ---------------------------------------------------------------------------------------------------- |
|           `400 Bad Request` | Body/query không hợp lệ, trạng thái node không cho phép thao tác, di chuyển tạo vòng lặp.            |
|          `401 Unauthorized` | Thiếu hoặc JWT không hợp lệ; thường được API Gateway trả trước khi vào service.                      |
|             `403 Forbidden` | User đã xác thực nhưng không sở hữu node hoặc không có permission cần thiết.                         |
|             `404 Not Found` | Node/parent không tồn tại hoặc không thuộc phạm vi user; có thể dùng cùng response để tránh dò UUID. |
|              `409 Conflict` | Tên đã tồn tại trong parent, upload đã ở trạng thái không tương thích, hoặc thao tác cạnh tranh.     |
|                  `410 Gone` | Link share đã hết hạn, bị thu hồi hoặc hết lượt truy cập.                                            |
|     `413 Payload Too Large` | File vượt hạn mức dung lượng được cấu hình.                                                          |
|           `502 Bad Gateway` | S3/MinIO không khả dụng hoặc trả lỗi khi xác minh/cấp URL.                                           |
| `500 Internal Server Error` | Lỗi ngoài các trường hợp đã xác định; không trả thông tin nội bộ hay credential.                     |

## Ghi chú triển khai và các điểm cần chốt

- Database hiện có unique constraint `(parent_id, name)` cho `FsNode`; constraint
  không bao gồm `user_id`. Cần kiểm tra lại scope uniqueness và hành vi `NULL`
  của database cho root trước khi hỗ trợ nhiều user có cùng tên ở cùng parent.
- Metadata database và object storage không cùng một transaction. Upload theo
  hai bước initiate/complete giúp tránh đánh dấu file sẵn sàng trước khi object
  tồn tại; cần có cleanup cho upload dở dang.
- Gateway hiện chỉ xác thực JWT và chuyển `X-User-Id`; phân quyền owner/share và
  kiểm soát object key phải được thực hiện tại storage-service. Hạn chế truy cập
  trực tiếp tới port service từ mạng không tin cậy để tránh giả mạo header.
- Gateway đang route tới port `8082`; cấu hình service cần đồng bộ với port này.
  MinIO được khai báo ở `backend/services/storage-service/docker-compose.yml`
  với API port `9000` và console port `9001`. Chỉ API port dùng cho thao tác
  object; console không phải endpoint của storage-service.
- Build hiện khai báo Spring Web, JPA, Security, Validation, Actuator,
  Springdoc, MySQL/H2 và Flyway; chưa thấy dependency S3/MinIO SDK, controller,
  DTO, migration hay test API trong `storage-service`. Cần bổ sung những phần đó
  khi triển khai thiết kế này.
