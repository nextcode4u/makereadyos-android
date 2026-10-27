package com.makereadyos.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class WorkspaceThemeTest {
    @Test public void acceptsOnlyExistingWebThemes() {
        assertTrue(WorkspaceTheme.valid("light"));
        assertTrue(WorkspaceTheme.valid("dark"));
        assertTrue(WorkspaceTheme.valid("default"));
        assertFalse(WorkspaceTheme.valid(null));
        assertFalse(WorkspaceTheme.valid("system"));
        assertFalse(WorkspaceTheme.valid("#ffffff"));
        assertFalse(WorkspaceTheme.valid("dark; arbitrary input"));
    }
}
