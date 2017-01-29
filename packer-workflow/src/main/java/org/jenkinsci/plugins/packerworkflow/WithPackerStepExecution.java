import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.Util;
import hudson.console.ConsoleLogFilter;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;

class WithPackerStepExecution extends StepExecution {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(WithPackerStepExecution.class.getName());

    private final transient WithPackerStep step;
    private final transient TaskListener listener;
    private final transient FilePath ws;
    private final transient Launcher launcher;
    private final transient EnvVars env;
    private transient EnvVars envOverride;
    private final transient Run<?, ?> build;

    private transient Computer computer;
    private transient FilePath tempBinDir;
    private transient BodyExecution body;

    private transient PrintStream console;

    WithPackerStepExecution(StepContext context, WithPackerStep step) throws Exception {
        super(context);
        this.step = step;
        // Or just delete these fields and inline:
        listener = context.get(TaskListener.class);
        ws = context.get(FilePath.class);
        launcher = context.get(Launcher.class);
        env = context.get(EnvVars.class);
        build = context.get(Run.class);
    }

    @Override
    public boolean start() throws Exception {
        envOverride = new EnvVars();
        console = listener.getLogger();

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "packer File: {0}", step.getPackerFile());
        }

        getComputer();

        setupPacker();

        ConsoleLogFilter consFilter = getContext().get(ConsoleLogFilter.class);
        EnvironmentExpander envEx = EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class),
        		new ExpanderImpl(envOverride));

        body = getContext().newBodyInvoker().withContexts(envEx, consFilter).withCallback(new Callback(tempBinDir)).start();

        return false;
    }

    private void setupPacker() throws AbortException, IOException, InterruptedException {
        String packerExecPath = obtainPackerExec();

        // Temp dir with the wrapper that will be prepended to the path
        tempBinDir = tempDir(ws).child("withPacker" + Util.getDigestOf(UUID.randomUUID().toString()).substring(0, 8));
        tempBinDir.mkdirs();
        // set the path to our script
        envOverride.put("PATH+Packer", tempBinDir.getRemote());

        LOGGER.log(Level.FINE, "Using temp dir: {0}", tempBinDir.getRemote());

        if (packerExecPath == null) {
            throw new AbortException("Couldn\u2019t find any packer executable");
        }

        FilePath packerExec = new FilePath(ws.getChannel(), packerExecPath);

        // TODO:  Check to see if file already exists in workspace since if it does, we probably
        // want to abort.
        settingsFromFile(step.getPackerFile(), ws.child(".json"));

        String content = packerWrapperContent(packerExec);

        createWrapperScript(tempBinDir, packerExec.getName(), content);

    }

    private String obtainPackerExec() throws AbortException, InterruptedException {
    	String packerExecPath = null;

    	LOGGER.fine("Setting up packer");

        if (Boolean.TRUE.equals(getComputer().isUnix())) {
        	packerExecPath = readFromProcess("/bin/sh", "-c", "which packer");
        } else {
        	packerExecPath = readFromProcess("where", "packer.cmd");
            if (packerExecPath == null) {
            	packerExecPath = readFromProcess("where", "packer.bat");
            }
        }

        if (packerExecPath == null) {
            throw new AbortException("Could not find packer executable, please set up a Packer installation");
        }

        LOGGER.log(Level.FINE, "Found exec for maven on: {0}", packerExecPath);
        console.printf("Using packer exec: %s%n", packerExecPath);
    	return packerExecPath;
    }
