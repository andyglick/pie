package mb.pipe.run.cmd;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileObject;
import org.metaborg.core.MetaborgException;
import org.metaborg.core.build.CommonPaths;
import org.metaborg.core.resource.IResourceService;
import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.LoggerUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.inject.Inject;
import com.sun.nio.file.SensitivityWatchEventModifier;

import build.pluto.builder.BuildManager;
import build.pluto.builder.BuildRequest;
import build.pluto.dependency.database.XodusDatabase;
import build.pluto.util.LogReporting;
import mb.pipe.run.core.util.Path;
import mb.pipe.run.pluto.main.Pipeline;
import mb.pipe.run.pluto.main.Pipeline.Input;
import mb.pipe.run.pluto.main.Pipeline.Output;

@SuppressWarnings("restriction")
public class Runner {
    private static final ILogger logger = LoggerUtils.logger(Runner.class);

    private final IResourceService resourceService;


    @Inject public Runner(IResourceService resourceService) {
        this.resourceService = resourceService;
    }


    public int run(String[] args) throws Throwable {
        final Arguments arguments = new Arguments();
        final JCommander jc = new JCommander(arguments);

        try {
            jc.parse(args);
        } catch(ParameterException e) {
            System.err.println(e.getMessage());
            final StringBuilder sb = new StringBuilder();
            jc.usage(sb);
            System.err.println(sb.toString());
            return -1;
        }

        if(arguments.help) {
            final StringBuilder sb = new StringBuilder();
            jc.usage(sb);
            System.err.println(sb.toString());
            return 0;
        }

        if(arguments.exit) {
            System.err.println("Exitting immediately for testing purposes");
            return 0;
        }

        final FileObject langSpecLoc = resourceService.resolve(arguments.langSpec);
        final CommonPaths paths = new CommonPaths(langSpecLoc);
        final FileObject depLoc = paths.targetDir().resolveFile("dep");
        final File depDir = resourceService.localPath(depLoc);
        if(depDir == null) {
            throw new MetaborgException(
                "Language specification directory " + langSpecLoc + " is not on the local file system");
        }

        clean(depLoc, arguments);
        build(depDir, arguments);

        if(!arguments.continuous) {
            return 0;
        }

        final java.nio.file.Path path = FileSystems.getDefault().getPath(langSpecLoc.getName().getPath());
        try(final WatchService watchService = FileSystems.getDefault().newWatchService()) {
            registerWatcher(path, watchService);
            Files.walkFileTree(path, new SimpleFileVisitor<java.nio.file.Path>() {
                @Override public FileVisitResult preVisitDirectory(java.nio.file.Path dir, BasicFileAttributes attrs)
                    throws IOException {
                    registerWatcher(dir, watchService);
                    return FileVisitResult.CONTINUE;
                }
            });

            final AtomicBoolean stop = new AtomicBoolean(false);
            while(!stop.get()) {
                final WatchKey key = watchService.poll(25, TimeUnit.MILLISECONDS);
                if(key == null) {
                    continue;
                }
                boolean build = false;
                for(WatchEvent<?> event : key.pollEvents()) {
                    final java.nio.file.Path changedPath = (java.nio.file.Path) event.context();
                    final String pathStr = changedPath.toString();
                    final boolean relevantChange =
                        pathStr.endsWith(".sdf3") || pathStr.endsWith(".esv") || pathStr.endsWith(".min");
                    if(relevantChange) {
                        logger.info("Relevant change: {}", pathStr);
                    }
                    build |= relevantChange;
                }
                if(build) {
                    build(depDir, arguments);
                }
                key.reset();
            }
        }

        return 0;
    }


    private static void build(File depDir, Arguments arguments) throws Throwable {
        final Path langSpec = new Path(arguments.langSpec);
        final Path config = new Path(arguments.config);
        final Path esvLang = new Path(arguments.esv);
        final Path esvSpec = new Path(arguments.esvSpec);
        final Path sdfLang = new Path(arguments.sdf);
        final Path sdfSpec = new Path(arguments.sdfSpec);
        final Path file = new Path(arguments.file);

        final BuildRequest<?, Output, ?, ?> buildRequest =
            Pipeline.request(new Input(depDir, null, langSpec, config, esvLang, esvSpec, sdfLang, sdfSpec, file));

        try(final BuildManager buildManager =
            new BuildManager(new LogReporting(), XodusDatabase.createFileDatabase("pipeline-experiment"))) {
            if(arguments.clean) {
                buildManager.resetDynamicAnalysis();
            }

            final Output output = buildManager.requireInitially(buildRequest).getBuildResult();
            logger.info("Output: {}", output.ast);
        } catch(MetaborgException | IOException e) {
            logger.error("Build failed", e);
        }
    }

    private static void clean(FileObject depLoc, Arguments arguments) throws Throwable {
        if(!arguments.clean) {
            return;
        }

        depLoc.delete(new AllFileSelector());
        try(final BuildManager buildManager =
            new BuildManager(new LogReporting(), XodusDatabase.createFileDatabase("pipeline-experiment"))) {
            buildManager.resetDynamicAnalysis();
        }
    }

    private static void registerWatcher(java.nio.file.Path path, WatchService watchService) throws IOException {
        path.register(
            watchService, new WatchEvent.Kind[] { StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE },
            SensitivityWatchEventModifier.HIGH);
    }
}