package core;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.Wait;
import org.jenkinsci.test.acceptance.po.Artifact;
import org.jenkinsci.test.acceptance.po.ArtifactArchiver;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.BuildWithParameters;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.ListView;
import org.jenkinsci.test.acceptance.po.ShellBuildStep;
import org.jenkinsci.test.acceptance.po.StringParameter;
import org.jenkinsci.test.acceptance.po.TimerTrigger;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;

import java.net.URL;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.jenkinsci.test.acceptance.Matchers.containsRegexp;
import static org.jenkinsci.test.acceptance.Matchers.hasContent;
import static org.jenkinsci.test.acceptance.Matchers.pageObjectDoesNotExist;
import static org.jenkinsci.test.acceptance.Matchers.pageObjectExists;
import static org.junit.Assert.*;

public class FreestyleJobTest extends AbstractJUnitTest {

    @Test
    @Issue("JENKINS-38928")
    public void apply_then_save() {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class, "simple-job");
        assertThat(j, pageObjectExists());

        j.configure();
        ShellBuildStep shell = j.addBuildStep(ShellBuildStep.class);
        shell.command("echo 1");

        j.apply();
        j.save();

        j.visit("config.xml");

        assertTrue("job config.xml should contain the step \"echo 1\"",driver.getPageSource().contains("echo 1"));
    }

    @Test
    public void runCurrentBuilds() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);
        j.configure();
        j.concurrentBuild.check();
        j.addShellStep("sleep 20");
        j.save();
        Build b1 = j.scheduleBuild().waitUntilStarted();
        Build b2 = j.scheduleBuild().waitUntilStarted();

        assertTrue(b1.isInProgress());
        assertTrue(b2.isInProgress());
    }

    @Test
    public void disableJob() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);
        assertThat(driver, not(Job.disabled()));

        j.configure();
        j.disable();
        j.save();

        assertThat(driver, Job.disabled());

        clickButton("Enable");

        assertThat(driver, not(Job.disabled()));
    }

    @Test
    public void buildParameterized() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);
        j.configure();
        j.addParameter(StringParameter.class).setName("text").setDefault("foo").setDescription("Bar");
        j.addShellStep("echo \">$text<\"");
        j.save();

        Build build = j.scheduleBuild(Collections.singletonMap("text", "asdf")).waitUntilFinished();
        assertThat(build.getConsole(), containsString(">asdf<"));
    }

    @Test
    public void discardBuilds() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);

        j.configure();
        try {
            check("Discard old builds");
        } catch (NoSuchElementException x) { // 1.636-
            check("Discard Old Builds");
        }
        j.control(by.name("_.numToKeepStr")).set(1);
        j.save();

        Build b1 = j.scheduleBuild().waitUntilFinished();
        Build b2 = j.scheduleBuild().waitUntilFinished();
        assertThat(b1, pageObjectDoesNotExist());
        assertThat(b2, pageObjectExists());

        b2.keepForever(true);

        Build b3 = j.scheduleBuild().waitUntilFinished();
        assertThat(b2, pageObjectExists());
        assertThat(b3, pageObjectExists());

        Build b4 = j.scheduleBuild().waitUntilFinished();
        assertThat(b2, pageObjectExists());
        assertThat(b3, pageObjectDoesNotExist());
        assertThat(b4, pageObjectExists());

        b2.keepForever(false);

        j.scheduleBuild().waitUntilFinished();
        assertThat(b2, pageObjectDoesNotExist());
    }

    @Test
    public void doNotDiscardSuccessfulBuilds() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);

        j.configure();
        try {
            check("Discard old builds");
        } catch (NoSuchElementException x) { // 1.636-
            check("Discard Old Builds");
        }
        j.control(by.name("_.numToKeepStr")).set(1);
        ShellBuildStep shellBuildStep = j.addShellStep("exit 0");
        j.save();

        Build b1 = j.scheduleBuild().waitUntilFinished();

        j.configure();
        shellBuildStep.command("exit 1");
        j.save();

        Build b2 = j.scheduleBuild().waitUntilFinished();
        Build b3 = j.scheduleBuild().waitUntilFinished();
        assertThat(b1, pageObjectExists());
        assertThat(b2, pageObjectDoesNotExist());
        assertThat(b3, pageObjectExists());
    }

    @Test
    public void archiveArtifacts() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);
        j.configure();
        j.addShellStep("echo 'yes' > include; echo 'no' > exclude;");
        ArtifactArchiver archiver = j.addPublisher(ArtifactArchiver.class);
        archiver.includes("**/*include*");
        archiver.excludes("exclude");
        j.save();
        Build build = j.scheduleBuild().waitUntilFinished();
        assertThat(build.getArtifact("exclude"), pageObjectDoesNotExist());
        Artifact include = build.getArtifact("include");
        assertThat(include, pageObjectExists());
        assertThat(include.getTextContent(), equalTo("yes"));
    }

    @Test
    public void jobDescripton() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);
        String description = "ein sinnloser Job...";
        j.description(description, false);

        assertNotNull(hasContent(description));
    }

    @Test
    public void buildPermlinks() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);
        j.configure();
        j.save();

        Build b1 = j.scheduleBuild().shouldSucceed();

        j.configure();
        j.save();

        assertNotNull(driver.findElement(By.className("permalinks-header")));

        assertEquals(j.getLastBuild(), b1);

        assertNotNull(hasContent("Build #1"));

        assertTrue(find(By.className("icon-blue")).getAttribute("tooltip").contains("Success"));
    }


    @Test
    public void buildPeriodically() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);
        j.configure();
        TimerTrigger trigger = j.addTrigger(TimerTrigger.class);
        trigger.spec.set("* * * * *");
        j.save();

        Build first = j.build(1);
        new Wait<>(first)
                .withTimeout(70, TimeUnit.SECONDS) // Wall-clock time
                .until(pageObjectExists())
        ;
        assertThat(first.getConsole(), containsString("Started by timer"));

        assertThat(j.build(3), pageObjectDoesNotExist());
    }

    @Test
    public void customWorkspace() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);
        j.configure();
        j.useCustomWorkspace("custom_workspace");
        j.save();
        Pattern expected = Pattern.compile("^Building in workspace (.*)custom_workspace$", Pattern.MULTILINE);
        assertThat(j.scheduleBuild().waitUntilFinished().getConsole(), containsRegexp(expected));
    }

    @Test
    public void scheduleFromView() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);
        ListView view = jenkins.views.create(ListView.class, "AView");
        view.configure();
        view.addJob(j);
        view.save();
        view.scheduleJob(j.name);
        j.build(1).waitUntilStarted().shouldSucceed();

        j.configure();
        StringParameter p = j.addParameter(StringParameter.class);
        p.setName("foo");
        j.save();

        view.scheduleJob(j.name);
        BuildWithParameters paramPage = new BuildWithParameters(j, new URL(driver.getCurrentUrl()));
        paramPage.enter(Collections.singletonList(p), Collections.singletonMap("foo", "bar"));
        paramPage.start();

        j.build(2).waitUntilStarted().shouldSucceed();
    }

    @Test @Issue({"JENKINS-21457", "JENKINS-20772", "JENKINS-21478"})
    public void showErrorSavingConfig() throws Exception {
        FreeStyleJob j = jenkins.jobs.create(FreeStyleJob.class);
        j.configure();
        TimerTrigger trigger = j.addTrigger(TimerTrigger.class);
        trigger.spec.set("not_a_time");
        clickButton("Apply");

        By error = by.css("#error-description pre");

        assertThat(waitFor(error).getText(), containsString("Invalid input: \"not_a_time\""));
        clickLink("Close");

        j.configure();
        j.addTrigger(TimerTrigger.class);
        trigger.spec.set("not_a_time_either");
        clickButton("Apply");

        assertThat(waitFor(error).getText(), containsString("Invalid input: \"not_a_time_either\""));
        clickLink("Close");
    }
}
