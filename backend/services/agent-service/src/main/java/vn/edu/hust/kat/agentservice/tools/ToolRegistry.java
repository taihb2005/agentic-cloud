package vn.edu.hust.kat.agentservice.tools;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Getter
@RequiredArgsConstructor
public class ToolRegistry {

    private final StorageReadTools readTools;
    private final StorageSafeTools safeTools;
    private final StorageSensitiveTools sensitiveTools;

    public Object[] getAllTools() {
        return new Object[]{readTools, safeTools, sensitiveTools};
    }
}
