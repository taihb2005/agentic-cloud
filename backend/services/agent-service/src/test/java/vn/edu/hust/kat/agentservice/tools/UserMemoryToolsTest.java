package vn.edu.hust.kat.agentservice.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.memory.DualLayerMemoryManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserMemoryToolsTest {

    @Mock
    private UserContextManager userContextManager;

    @Mock
    private DualLayerMemoryManager memoryManager;

    @InjectMocks
    private UserMemoryTools userMemoryTools;

    @Test
    void updateUserPreference_UsesCurrentUserAndUpsertsPreference() {
        when(userContextManager.getCurrentUserIdAsString()).thenReturn("user-123");
        when(memoryManager.upsertUserPreference("user-123", "response_language", "English"))
                .thenReturn("mem-1");

        String result = userMemoryTools.updateUserPreference("response_language", "English");

        verify(memoryManager).upsertUserPreference("user-123", "response_language", "English");
        assertTrue(result.contains("response_language"));
        assertTrue(result.contains("English"));
    }

    @Test
    void saveUserContext_UsesCurrentUserAndSavesContext() {
        when(userContextManager.getCurrentUserIdAsString()).thenReturn("user-123");
        when(memoryManager.saveUserMemory("user-123", "NAMING_RULE", "YYYY-MM-DD_ten_tai_lieu"))
                .thenReturn("mem-2");

        String result = userMemoryTools.saveUserContext("NAMING_RULE", "YYYY-MM-DD_ten_tai_lieu");

        verify(memoryManager).saveUserMemory("user-123", "NAMING_RULE", "YYYY-MM-DD_ten_tai_lieu");
        assertTrue(result.contains("NAMING_RULE"));
    }

    @Test
    void recallUserContext_ReturnsMatchesWhenFound() {
        when(userContextManager.getCurrentUserIdAsString()).thenReturn("user-123");
        when(memoryManager.searchUserSemanticMemory("user-123", "ngôn ngữ", 5))
                .thenReturn(List.of("[USER_PREFERENCE:response_language] Preference [response_language]: Vietnamese"));

        String result = userMemoryTools.recallUserContext("ngôn ngữ");

        assertTrue(result.contains("Vietnamese"));
    }

    @Test
    void deleteUserPreference_RemovesPreferenceForCurrentUser() {
        when(userContextManager.getCurrentUserIdAsString()).thenReturn("user-123");

        String result = userMemoryTools.deleteUserPreference("response_language");

        verify(memoryManager).deleteUserPreference("user-123", "response_language");
        assertTrue(result.contains("response_language"));
    }
}
