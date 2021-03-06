package fi.helsinki.cs.tmc.cli.shared;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import fi.helsinki.cs.tmc.cli.analytics.AnalyticsFacade;
import fi.helsinki.cs.tmc.cli.backend.Settings;
import fi.helsinki.cs.tmc.cli.backend.TmcUtil;
import fi.helsinki.cs.tmc.cli.core.CliContext;
import fi.helsinki.cs.tmc.cli.io.TestIo;

import fi.helsinki.cs.tmc.cli.io.WorkDir;
import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.commands.GetUpdatableExercises.UpdateResult;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Exercise;

import fi.helsinki.cs.tmc.langs.util.TaskExecutorImpl;
import fi.helsinki.cs.tmc.spyware.EventSendBuffer;
import fi.helsinki.cs.tmc.spyware.EventStore;
import fi.helsinki.cs.tmc.spyware.SpywareSettings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PrepareForTest(TmcUtil.class)
public class ExerciseUpdaterTest {

    private CliContext ctx;
    private TmcCore mockCore;
    private ExerciseUpdater exerciseUpdater;

    @Before
    public void setUp() {
        SpywareSettings analyticsSettings = new Settings();
        Settings settings = new Settings();
        mockCore = new TmcCore(settings, new TaskExecutorImpl());
        EventSendBuffer eventSendBuffer = new EventSendBuffer(analyticsSettings, new EventStore());
        AnalyticsFacade analyticsFacade = new AnalyticsFacade(analyticsSettings, eventSendBuffer);
        ctx = new CliContext(new TestIo(), mockCore, new WorkDir(), settings, analyticsFacade);

        mockStatic(TmcUtil.class);
    }

    @Test
    public void noUpdatesAfterConstructor() {
        exerciseUpdater = new ExerciseUpdater(ctx, new Course());
        assertFalse(exerciseUpdater.newExercisesAvailable());
        assertFalse(exerciseUpdater.updatedExercisesAvailable());
    }

    @Test
    public void updateListsAreEmptyAfterConstructor() {
        exerciseUpdater = new ExerciseUpdater(ctx, new Course());
        assertTrue(exerciseUpdater.getNewExercises().isEmpty());
        assertTrue(exerciseUpdater.getUpdatedExercises().isEmpty());
    }

    @Test
    public void updatesAvailableAfterSetters() {
        List<Exercise> newExercises = Collections.singletonList(new Exercise("new"));
        List<Exercise> updatedExercises = Collections.singletonList(new Exercise("updated"));

        exerciseUpdater = new ExerciseUpdater(ctx, new Course());
        exerciseUpdater.setNewExercises(newExercises);
        exerciseUpdater.setUpdatedExercises(updatedExercises);

        assertTrue(exerciseUpdater.newExercisesAvailable());
        assertTrue(exerciseUpdater.updatedExercisesAvailable());

        assertTrue(exerciseUpdater.getNewAndUpdatedExercises().contains(newExercises.get(0)));
        assertTrue(exerciseUpdater.getNewAndUpdatedExercises().contains(updatedExercises.get(0)));
    }

    @Test
    public void worksWithActualResultObject() {
        UpdateResult result = mock(UpdateResult.class);
        List<Exercise> exercises = new ArrayList<>();
        exercises.add(new Exercise());

        when(result.getNewExercises()).thenReturn(exercises);
        when(result.getUpdatedExercises()).thenReturn(exercises);
        when(TmcUtil.getUpdatableExercises(eq(ctx), any(Course.class))).thenReturn(result);

        exerciseUpdater = new ExerciseUpdater(ctx, new Course());
        assertTrue(exerciseUpdater.updatesAvailable());
    }
}
