package fi.helsinki.cs.tmc.cli;

import fi.helsinki.cs.tmc.cli.analytics.AnalyticsFacade;
import fi.helsinki.cs.tmc.cli.analytics.TimeTracker;
import fi.helsinki.cs.tmc.cli.backend.CourseInfo;
import fi.helsinki.cs.tmc.cli.backend.Settings;
import fi.helsinki.cs.tmc.cli.command.SubmitCommand;
import fi.helsinki.cs.tmc.cli.core.AbstractCommand;
import fi.helsinki.cs.tmc.cli.core.CliContext;
import fi.helsinki.cs.tmc.cli.io.ShutdownHandler;
import fi.helsinki.cs.tmc.cli.io.Io;
import fi.helsinki.cs.tmc.cli.io.EnvironmentUtil;
import fi.helsinki.cs.tmc.cli.io.HelpGenerator;
import fi.helsinki.cs.tmc.cli.io.WorkDir;
import fi.helsinki.cs.tmc.cli.updater.AutoUpdater;
import fi.helsinki.cs.tmc.cli.core.CommandFactory;


import fi.helsinki.cs.tmc.cli.utils.OptionalToGoptional;
import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;
import fi.helsinki.cs.tmc.core.utilities.TmcRequestProcessor;
import fi.helsinki.cs.tmc.langs.util.TaskExecutor;
import fi.helsinki.cs.tmc.langs.util.TaskExecutorImpl;
import fi.helsinki.cs.tmc.spyware.EventSendBuffer;
import fi.helsinki.cs.tmc.spyware.EventStore;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The application class for the program.
 */
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final String previousUpdateDateKey = "update-date";
    private static final long defaultUpdateInterval = 60 * 60 * 1000;
    private static final String usage = "tmc [args] COMMAND [command-args]";

    private ShutdownHandler shutdownHandler;
    private final CliContext context;
    private final Io io;

    private final Options options;
    private final GnuParser parser;
    private String commandName;
    private boolean noAutoUpdate;

    private TimeTracker timeTracker;

    public Application(CliContext context) {
        this.timeTracker = new TimeTracker(context);
        this.parser = new GnuParser();
        this.options = new Options();

        this.context = context;
        this.io = context.getIo();

        options.addOption(
                OptionBuilder.withLongOpt("help-all")
                        .withDescription("Display all help information of tmc-cli")
                        .create());
        options.addOption("h", "help", false, "Display help information");
        options.addOption("v", "version", false, "Give the version of the tmc-cli");
        options.addOption("u", "force-update", false, "Force the auto-update");
        options.addOption("d", "no-update", false, "Disable temporarily the auto-update");

        Set<String> helpCategories = CommandFactory.getCommandCategories();
        for (String category : helpCategories) {
            if (category.equals("") || category.equals("hidden")) {
                continue;
            }
            options.addOption(
                    OptionBuilder.withLongOpt("help-" + category)
                            .withDescription("Display " + category + " help information")
                            .create());
        }

        //TODO implement the inTests as context.property
        if (!context.inTests()) {
            shutdownHandler = new ShutdownHandler(context.getIo());
            shutdownHandler.enable();
        }
    }

    private boolean runCommand(String name, String[] args) {
        String[] commandName = name.split(" ");
        AbstractCommand command = CommandFactory.createCommand(commandName[0].trim().toLowerCase());
        if (command == null) {
            io.errorln("Command " + name + " doesn't exist.");
            return false;
        }
        Optional<Thread> thread = sendAnalytics(command);

        command.execute(context, args);
        joinNewThread(thread);
        return true;
    }

    private Optional<Thread> sendAnalytics(AbstractCommand command) {
        Optional<Thread> thread = Optional.empty();
        if (command instanceof SubmitCommand || timeTracker.anHourHasPassedSinceLastSubmit()) {
            this.context.loadUserInformation(true);
            // get course info returns null
            CourseInfo courseInfo = this.context.getCourseInfo();
            if (courseInfo == null) {
                return Optional.empty();
            }
            TmcSettingsHolder.get().setCourse(OptionalToGoptional.convert(Optional.of(courseInfo.getCourse())));
            thread = this.context.getAnalyticsFacade().sendAnalytics();
            timeTracker.restart();
        }
        return thread;
    }

    private void joinNewThread(Optional<Thread> thread) {
        thread.ifPresent(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                logger.warn("Analytics thread interrupted");
            }
        });
    }

    private String[] parseArgs(String[] args) {
        CommandLine line;
        try {
            line = this.parser.parse(this.options, args, true);
        } catch (ParseException e) {
            io.println(e.getMessage());
            return null;
        }

        List<String> subArgs = new ArrayList<>(Arrays.asList(line.getArgs()));
        if (subArgs.size() > 0) {
            commandName = subArgs.remove(0);
        } else {
            commandName = "help";
        }

        if (commandName.startsWith("-")) {
            io.errorln("Unrecognized option: " + commandName);
            return null;
        }

        boolean showHelp = line.hasOption("h");
        boolean showVersion = line.hasOption("v");
        boolean forceUpdate = line.hasOption("u");
        this.noAutoUpdate = line.hasOption("d");

        if (forceUpdate && this.noAutoUpdate) {
            io.errorln("You can't use --force-update and --no-update at same time.");
            return null;
        }

        // handle help flags
        for (Option opt : line.getOptions()) {
            String helpPrefix = "help-";
            if (!opt.getLongOpt().startsWith(helpPrefix)) {
                continue;
            }

            String helpCategory = opt.getLongOpt().substring(helpPrefix.length());
            runCommand("help", new String[] {helpCategory});
            return null;
        }
        if (showHelp) {
            // don't run the help sub-command with -h switch
            if (commandName.equals("help")) {
                runCommand("help", new String[0]);
                return null;
            }
            runCommand(commandName, new String[] {"-h"});
            return null;
        }
        if (showVersion) {
            io.println("TMC-CLI version " + EnvironmentUtil.getVersion());
            return null;
        }
        if (forceUpdate) {
            runAutoUpdate();
            return null;
        }
        return subArgs.toArray(new String[subArgs.size()]);
    }

    public void printHelp(String description) {
        HelpGenerator.run(io, usage, description, this.options);
    }

    public void run(String[] args) {
        context.setApp(this);

        String[] commandArgs = parseArgs(args);
        if (commandArgs == null) {
            return;
        }

        if (!context.inTests() && !noAutoUpdate && versionCheck()) {
            return;
        }

        runCommand(commandName, commandArgs);

        if (!context.inTests()) {
            shutdownHandler.disable();
        }
    }

    public static void main(String[] args) {
        Settings settings = new Settings();
        TaskExecutor tmcLangs = new TaskExecutorImpl();
        TmcCore core = new TmcCore(settings, tmcLangs);
        EventSendBuffer eventSendBuffer = new EventSendBuffer(settings, new EventStore());
        AnalyticsFacade analyticsFacade = new AnalyticsFacade(settings, eventSendBuffer);
        Application app = new Application(new CliContext(null, core, new WorkDir(), settings, analyticsFacade));
        app.run(args);
        // Because of EventSendBuffer
        TmcRequestProcessor.instance.shutdown();
    }

    private boolean versionCheck() {
        Map<String, String> properties = context.getProperties();
        String previousTimestamp = properties.get(previousUpdateDateKey);
        Date previous = null;

        if (previousTimestamp != null) {
            long time;
            try {
                time = Long.parseLong(previousTimestamp);
            } catch (NumberFormatException ex) {
                io.errorln("The previous update date isn't a number.");
                logger.warn("The previous update date isn't a number.", ex);
                return false;
            }
            previous = new Date(time);
        }

        Date now = new Date();
        return !(previous != null && previous.getTime() + defaultUpdateInterval > now.getTime())
                && runAutoUpdate();
    }

    public boolean runAutoUpdate() {
        Map<String, String> properties = context.getProperties();
        Date now = new Date();
        AutoUpdater update =
                AutoUpdater.createUpdater(
                        io, EnvironmentUtil.getVersion(), EnvironmentUtil.isWindows());
        boolean updated = update.run();

        long timestamp = now.getTime();
        properties.put(previousUpdateDateKey, Long.toString(timestamp));
        context.saveProperties();
        return updated;
    }

}
