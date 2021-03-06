package fi.helsinki.cs.tmc.cli.command.hidden;

import fi.helsinki.cs.tmc.cli.Application;
import fi.helsinki.cs.tmc.cli.backend.Settings;
import fi.helsinki.cs.tmc.cli.core.CliContext;
import fi.helsinki.cs.tmc.cli.io.TestIo;

import fi.helsinki.cs.tmc.cli.io.WorkDir;
import org.junit.Before;
import org.junit.Test;

public class ShellHelperCommandTest {

    private Application app;
    private TestIo io;

    @Before
    public void setUp() {
        io = new TestIo();
        CliContext ctx = new CliContext(io, null, new WorkDir(), new Settings(), null);
        app = new Application(ctx);
    }

    @Test
    public void failWhenNoOptionsGiven() {
        String[] args = {"shell-helper"};
        app.run(args);
        io.assertContains("internal usage");
    }

    @Test
    public void getCommandList() {
        String[] args = {"shell-helper", "-c"};
        app.run(args);
        io.assertContains("help\n");
    }
}
