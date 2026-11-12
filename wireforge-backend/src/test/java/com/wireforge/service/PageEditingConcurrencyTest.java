package com.wireforge.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class PageEditingConcurrencyTest {
    private final ProjectService service = new ProjectService(null, null, null, null, null, null, null, null, null, null, null, null, null);

    @Test void simultaneousEditorsHaveExactlyOneOwnerAndOtherReleasesCannotRemoveIt() throws Exception {
        long pageId = System.nanoTime();
        int count = 24;
        ExecutorService workers = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count), start = new CountDownLatch(1);
        var futures = new ArrayList<Future<String>>();
        String owner = null;
        try {
            for (int i = 0; i < count; i++) {
                String client = "concurrent-" + i;
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    Map<String, Object> result = service.lockPageEditing(pageId, client, client, true);
                    return Boolean.TRUE.equals(result.get("conflict")) ? null : client;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            for (var future : futures) {
                String candidate = future.get(5, TimeUnit.SECONDS);
                if (candidate != null) { assertNull(owner, "two clients acquired the exclusive lock"); owner = candidate; }
            }
            assertNotNull(owner);
            service.lockPageEditing(pageId, "non-owner", "Other", false);
            assertEquals(false, service.getPageEditingStatus(pageId, owner).get("conflict"));
            assertEquals(true, service.getPageEditingStatus(pageId, "non-owner").get("conflict"));
            assertEquals(false, service.lockPageEditing(pageId, owner, owner, true).get("conflict"));
            service.lockPageEditing(pageId, owner, owner, false);
            assertEquals(false, service.lockPageEditing(pageId, "next", "Next", true).get("conflict"));
            service.lockPageEditing(pageId, "next", "Next", false);
        } finally {
            start.countDown(); workers.shutdownNow();
            if (owner != null) service.lockPageEditing(pageId, owner, owner, false);
        }
    }

    @Test void expiredOwnerCanBeReplacedAndItsLateReleaseDoesNotDeleteTheNewOwner() {
        long pageId = System.nanoTime();
        @SuppressWarnings("unchecked")
        var sessions = (ConcurrentHashMap<Long, ProjectService.EditSession>) ReflectionTestUtils.getField(ProjectService.class, "PAGE_EDIT_SESSIONS");
        sessions.put(pageId, new ProjectService.EditSession("old", "Old", System.currentTimeMillis() - 15000));
        try {
            assertEquals(false, service.lockPageEditing(pageId, "new", "New", true).get("conflict"));
            service.lockPageEditing(pageId, "old", "Old", false);
            assertEquals(true, service.getPageEditingStatus(pageId, "old").get("conflict"));
        } finally { service.lockPageEditing(pageId, "new", "New", false); }
    }

    @Test void missingSessionIdentifiersCannotCreateLocks() {
        assertThrows(IllegalArgumentException.class, () -> service.lockPageEditing(1L, "", "User", true));
        assertThrows(IllegalArgumentException.class, () -> service.lockPageEditing(null, "client", "User", true));
    }
}
