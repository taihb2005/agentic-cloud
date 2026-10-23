package vn.edu.hust.kat.storageservice.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tiện ích xử lý chuỗi phân cấp tương thích với kiểu dữ liệu PostgreSQL ltree.
 * Quy tắc ltree:
 * - Các nhãn (labels) phân cách nhau bằng dấu chấm ('.').
 * - Mỗi nhãn chỉ gồm các ký tự: chữ cái, chữ số, gạch dưới [A-Za-z0-9_], tối đa 255 ký tự.
 * - Path trong db sẽ lưu dạng root.<node_id1>.<node_id2>....
 */
public final class NodePathUtilities {

    public static final String SEPARATOR = ".";
    public static final String SEPARATOR_REGEX = "\\.";

    // Regex kiểm tra 1 nhãn đơn lẻ
    public static final String LABEL_REGEX = "^[a-zA-Z0-9_]{1,255}$";
    private static final Pattern LABEL_PATTERN = Pattern.compile(LABEL_REGEX);

    // Regex kiểm tra toàn bộ đường dẫn ltree
    public static final String LTREE_PATH_REGEX = "^[a-zA-Z0-9_]{1,255}(\\.[a-zA-Z0-9_]{1,255})*$";
    private static final Pattern LTREE_PATH_PATTERN = Pattern.compile(LTREE_PATH_REGEX);

    private NodePathUtilities() {

    }

    /**
     * Nối nhãn con vào đường dẫn cha: parentPath + "." + childLabel.
     * Nếu parentPath rỗng/null, trả về chính childLabel đã chuẩn hóa.
     */
    public static String join(String parentPath, String childLabel) {
        String sanitizedChild = sanitizeLabel(childLabel);
        if (parentPath == null || parentPath.isBlank()) {
            return sanitizedChild;
        }
        return parentPath.trim() + SEPARATOR + sanitizedChild;
    }


    /**
     * Xâu chuỗi nhiều nhãn liên tiếp thành một đường dẫn hoàn chỉnh theo thứ tự từ trái sang phải.
     * Ví dụ: buildPath("root", "users", "user_123") -> "root.users.user_123"
     */
    public static String join(String... labels) {
        if (labels == null || labels.length == 0) {
            return "";
        }
        List<String> validLabels = new ArrayList<>();
        for (String label : labels) {
            if (label != null && !label.isBlank()) {
                validLabels.add(sanitizeLabel(label));
            }
        }
        return String.join(SEPARATOR, validLabels);
    }

    /**
     * Tách đường dẫn ltree thành danh sách các đường dẫn.
     * Ví dụ: "root.folder_a.file_b" -> ["root", "folder_a", "file_b"]
     */
    public static List<String> split(String path) {
        if (path == null || path.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(path.trim().split(SEPARATOR_REGEX));
    }

    /**
     * Lấy đường dẫn của nút cha trực tiếp.
     * Ví dụ: "root.user_1.docs" -> "root.user_1"
     * Trả về null nếu là nút gốc (chỉ có 1 nhãn duy nhất) hoặc đường dẫn rỗng.
     */
    public static String getParentPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        int lastIndex = path.lastIndexOf(SEPARATOR);
        if (lastIndex <= 0) {
            return null;
        }
        return path.substring(0, lastIndex);
    }

    /**
     * Lấy nhãn của nút lá cuối cùng (tên node hiện tại).
     * Ví dụ: "root.user_1.documents" -> "documents"
     */
    public static String getLeafLabel(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int lastIndex = path.lastIndexOf(SEPARATOR);
        if (lastIndex < 0) {
            return path.trim();
        }
        return path.substring(lastIndex + 1);
    }

    /**
     * Tính độ sâu (Depth / Level) của nút trong cây dữ liệu.
     * "root" -> 1, "root.users" -> 2, "root.users.avatar" -> 3
     */
    public static int getDepth(String path) {
        if (path == null || path.isBlank()) {
            return 0;
        }
        return split(path).size();
    }

    /**
     * Lấy danh sách tất cả các đường dẫn tổ tiên (Ancestors) từ gốc đến trước nút hiện tại.
     * Ví dụ: "root.docs.reports.q1" -> ["root", "root.docs", "root.docs.reports"]
     */
    public static List<String> getAncestorPaths(String path) {
        List<String> labels = split(path);
        if (labels.size() <= 1) {
            return Collections.emptyList();
        }

        List<String> ancestors = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < labels.size() - 1; i++) {
            if (i > 0) {
                current.append(SEPARATOR);
            }
            current.append(labels.get(i));
            ancestors.add(current.toString());
        }
        return ancestors;
    }

    /**
     * Kiểm tra ancestorPath có phải là tiền tố (cha/ông) của descendantPath hay không.
     * Ví dụ: isAncestor("root.docs", "root.docs.reports.pdf") -> true
     */
    public static boolean isAncestor(String ancestorPath, String descendantPath) {
        if (ancestorPath == null || descendantPath == null) {
            return false;
        }
        String cleanAncestor = ancestorPath.trim();
        String cleanDescendant = descendantPath.trim();

        if (cleanDescendant.equals(cleanAncestor)) {
            return false;
        }
        return cleanDescendant.startsWith(cleanAncestor + SEPARATOR);
    }

    /**
     * Đổi gốc phân nhánh khi di chuyển cây thư mục (Move / Rebase Subtree).
     * Ví dụ:
     * - Node hiện tại: "root.old_folder.sub_a.file_b"
     * - oldBasePath: "root.old_folder"
     * - newBasePath: "root.target_folder"
     * => Kết quả: "root.target_folder.sub_a.file_b"
     */
    public static String rebase(String currentPath, String oldBasePath, String newBasePath) {
        Objects.requireNonNull(currentPath, "currentPath không được để trống");
        Objects.requireNonNull(oldBasePath, "oldBasePath không được để trống");
        Objects.requireNonNull(newBasePath, "newBasePath không được để trống");

        if (currentPath.equals(oldBasePath)) {
            return newBasePath;
        }

        if (!isAncestor(oldBasePath, currentPath)) {
            throw new IllegalArgumentException(String.format(
                    "Đường dẫn '%s' không nằm trong nhánh gốc cũ '%s'", currentPath, oldBasePath));
        }

        String subPath = currentPath.substring(oldBasePath.length() + SEPARATOR.length());
        return newBasePath + SEPARATOR + subPath;
    }

    /**
     * Chuẩn hóa UUID thành ltree label hợp lệ bằng cách thay thế dấu gạch ngang '-' bằng dấu '_'.
     * Ví dụ: 123e4567-e89b-12d3-a456-426614174000 -> u_123e4567_e89b_12d3_a456_426614174000
     */
    public static String formatUuidToLabel(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID không được null");
        }
        return "u_" + uuid.toString().replace("-", "_");
    }

    /**
     * Khôi phục UUID gốc từ nhãn ltree đã được định dạng bằng formatUuidToLabel.
     */
    public static UUID parseLabelToUuid(String label) {
        if (label == null || !label.startsWith("u_")) {
            throw new IllegalArgumentException("Nhãn không đúng định dạng UUID ltree (phải bắt đầu bằng u_)");
        }
        String uuidStr = label.substring(2).replace("_", "-");
        return UUID.fromString(uuidStr);
    }

    /**
     * Chuẩn hóa nhãn tự do: loại bỏ ký tự đặc biệt, thay thế khoảng trắng và dấu gạch nối bằng dấu gạch dưới.
     */
    public static String sanitizeLabel(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            throw new IllegalArgumentException("Nhãn ltree không được để trống");
        }
        // Thay thế khoảng trắng, dấu gạch ngang và dấu chấm bằng gạch dưới
        String sanitized = rawLabel.trim()
                .replaceAll("[\\s\\-\\.]+", "_")
                .replaceAll("[^a-zA-Z0-9_]", "");

        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Nhãn không chứa ký tự hợp lệ sau khi chuẩn hóa: " + rawLabel);
        }

        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }
        return sanitized;
    }

    /**
     * Kiểm tra tính hợp lệ của toàn bộ đường dẫn ltree.
     */
    public static boolean isValidPath(String path) {
        return path != null && LTREE_PATH_PATTERN.matcher(path.trim()).matches();
    }
}