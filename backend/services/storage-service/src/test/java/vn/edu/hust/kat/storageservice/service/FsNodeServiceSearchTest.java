package vn.edu.hust.kat.storageservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.edu.hust.kat.storageservice.dto.response.NodeResponse;
import vn.edu.hust.kat.storageservice.dto.response.PagedResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FsNodeServiceSearchTest {

    @Mock
    private FsNodeRepository fsNodeRepository;

    @InjectMocks
    private FsNodeService fsNodeService;

    private UUID userId;
    private FsUser user;
    private FsNode rootNode;
    private FsNode subFolderNode;
    private FsNode nestedFileNode;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = FsUser.builder().id(userId).build();

        rootNode = FsNode.builder()
                .id(1L)
                .user(user)
                .parent(null)
                .name("root")
                .isFolder(true)
                .materializedPath("root")
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        subFolderNode = FsNode.builder()
                .id(10L)
                .user(user)
                .parent(rootNode)
                .name("Projects")
                .isFolder(true)
                .materializedPath("root.10")
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        nestedFileNode = FsNode.builder()
                .id(25L)
                .user(user)
                .parent(subFolderNode)
                .name("architecture-report.pdf")
                .isFolder(false)
                .mimeType("application/pdf")
                .sizeBytes(2048L)
                .materializedPath("root.10.25")
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();
    }

    @Test
    void getChildNodes_WhenParentUnspecifiedAndNoFilter_BrowsesDirectRootChildrenOnly() {
        when(fsNodeRepository.findByUserIdAndParentIsNull(userId)).thenReturn(Optional.of(rootNode));
        Page<FsNode> mockPage = new PageImpl<>(List.of(subFolderNode));
        when(fsNodeRepository.findChildNodesWithLtree(
                eq(userId),
                eq("root"),
                isNull(),
                isNull(),
                isNull(),
                eq(false),
                any(Pageable.class)
        )).thenReturn(mockPage);

        PagedResponse<NodeResponse> result = fsNodeService.getChildNodes(
                userId,
                null,
                null,
                null,
                null,
                0,
                20,
                List.of("name,asc")
        );

        assertEquals(1, result.getContent().size());
        assertEquals("Projects", result.getContent().get(0).getName());
        verify(fsNodeRepository).findChildNodesWithLtree(
                eq(userId),
                eq("root"),
                isNull(),
                isNull(),
                isNull(),
                eq(false),
                any(Pageable.class)
        );
    }

    @Test
    void getChildNodes_WhenParentUnspecifiedAndHasNameFilter_SearchesAcrossEntireWorkspaceRecursively() {
        when(fsNodeRepository.findByUserIdAndParentIsNull(userId)).thenReturn(Optional.of(rootNode));
        Page<FsNode> mockPage = new PageImpl<>(List.of(nestedFileNode));
        when(fsNodeRepository.findChildNodesWithLtree(
                eq(userId),
                eq("root"),
                isNull(),
                eq("report"),
                isNull(),
                eq(true),
                any(Pageable.class)
        )).thenReturn(mockPage);

        PagedResponse<NodeResponse> result = fsNodeService.getChildNodes(
                userId,
                "",
                null,
                "report",
                null,
                0,
                20,
                List.of("name,asc")
        );

        assertEquals(1, result.getContent().size());
        assertEquals(25L, result.getContent().get(0).getId());
        assertEquals(10L, result.getContent().get(0).getParentId());
        assertEquals("architecture-report.pdf", result.getContent().get(0).getName());
        verify(fsNodeRepository).findChildNodesWithLtree(
                eq(userId),
                eq("root"),
                isNull(),
                eq("report"),
                isNull(),
                eq(true),
                any(Pageable.class)
        );
    }

    @Test
    void getChildNodes_WhenExplicitParentIdProvidedWithFilter_SearchesOnlyDirectChildrenByDefault() {
        when(fsNodeRepository.findById(10L)).thenReturn(Optional.of(subFolderNode));
        Page<FsNode> mockPage = new PageImpl<>(List.of(nestedFileNode));
        when(fsNodeRepository.findChildNodesWithLtree(
                eq(userId),
                eq("root.10"),
                eq(false),
                eq("report"),
                isNull(),
                eq(false),
                any(Pageable.class)
        )).thenReturn(mockPage);

        PagedResponse<NodeResponse> result = fsNodeService.getChildNodes(
                userId,
                "10",
                "FILE",
                "report",
                null,
                0,
                20,
                List.of("name,asc")
        );

        assertEquals(1, result.getContent().size());
        verify(fsNodeRepository).findChildNodesWithLtree(
                eq(userId),
                eq("root.10"),
                eq(false),
                eq("report"),
                isNull(),
                eq(false),
                any(Pageable.class)
        );
    }

    @Test
    void getChildNodes_WhenExplicitRecursiveFlagTrue_SearchesSubtreeRecursively() {
        when(fsNodeRepository.findById(10L)).thenReturn(Optional.of(subFolderNode));
        Page<FsNode> mockPage = new PageImpl<>(List.of(nestedFileNode));
        when(fsNodeRepository.findChildNodesWithLtree(
                eq(userId),
                eq("root.10"),
                isNull(),
                isNull(),
                isNull(),
                eq(true),
                any(Pageable.class)
        )).thenReturn(mockPage);

        PagedResponse<NodeResponse> result = fsNodeService.getChildNodes(
                userId,
                "10",
                null,
                null,
                null,
                true,
                0,
                20,
                List.of("name,asc")
        );

        assertEquals(1, result.getContent().size());
        verify(fsNodeRepository).findChildNodesWithLtree(
                eq(userId),
                eq("root.10"),
                isNull(),
                isNull(),
                isNull(),
                eq(true),
                any(Pageable.class)
        );
    }
}
