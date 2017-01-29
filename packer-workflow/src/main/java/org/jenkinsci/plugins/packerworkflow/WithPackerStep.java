package org.jenkinsci.plugins.packerworkflow;

import java.util.Set;

import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.configfiles.custom.CustomConfig.CustomConfigProvider;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;

import biz.neustar.jenkins.plugins.packer;
import net.sf.json.JSONObject;

public class WithPackerStep extends Step {

    private String packerFile;

    @DataBoundConstructor
    public WithPackerStep() {
    }


    public String getPackerFile() {
        return packerFile;
    }

    @DataBoundSetter
    public void setPackerFile(String packerFile) {
        this.packerFile = packerFile;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new WithPackerStepExecution(context, this);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "withPacker";
        }

        @Override
        public String getDisplayName() {
            return "Provide Packer File";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, FilePath.class, Launcher.class, EnvVars.class, Run.class);
        }

    }

}
