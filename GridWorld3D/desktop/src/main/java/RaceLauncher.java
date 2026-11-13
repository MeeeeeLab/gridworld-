import com.jme3.system.AppSettings;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One server, two independent Java client processes and two first-person windows. */
public final class RaceLauncher {
    public static void main(String[] args) throws Exception {
        if(args.length>=1 && args[0].equals("--garage")) {
            List<String> command=new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
            if(System.getProperty("os.name").toLowerCase().contains("mac"))command.add("-XstartOnFirstThread");
            command.addAll(List.of("-cp",System.getProperty("java.class.path"),"VehicleGarage"));
            if(args.length==2)command.add(args[1]);
            int exit=new ProcessBuilder(command).inheritIO().start().waitFor();
            if(exit!=0)throw new IOException("Vehicle workshop failed: "+exit);
            return;
        }
        if(args.length>=5 && args[0].equals("--client")) {
            System.out.println("Runtime: Java "+System.getProperty("java.version")+" / "+System.getProperty("os.name")+" / "+System.getProperty("os.arch"));
            RaceConnection connection=new RaceConnection(Integer.parseInt(args[1]));
            if(connection.id!=Integer.parseInt(args[2]))throw new IOException("Unexpected player assignment");
            Path ready=Path.of(args[3]);
            RaceGame game=new RaceGame(connection,()->{
                try {Files.writeString(ready,"First frame rendered for player "+(connection.id+1));}
                catch(IOException e) {throw new UncheckedIOException(e);}
            });
            AppSettings settings=new AppSettings(true);
            settings.setTitle(connection.id==0?"GRID//RIFT  |  01 SCOUT":"GRID//RIFT  |  02 SIEGE");
            settings.setResolution(800,640);settings.setResizable(false);settings.setFrameRate(60);
            settings.setAudioRenderer(null);settings.setVSync(true);settings.setSamples(2);
            boolean mac=System.getProperty("os.name").toLowerCase().contains("mac");
            // Cascade on MacBook displays: both title bars stay reachable in logical screen coordinates.
            settings.setCenterWindow(false);settings.setWindowXPosition(20+connection.id*(mac?60:820));
            settings.setWindowYPosition(40+(mac?connection.id*60:0));
            game.setSettings(settings);game.setShowSettings(false);game.start();
            if(!args[4].equals("-")) visualCheck(game,connection,Path.of(args[4]));
            return;
        }
        boolean check=args.length==2 && args[0].equals("--visual-check");
        if(args.length!=0 && !check) throw new IllegalArgumentException("Usage: RaceLauncher [--visual-check OUTPUT_DIR]");
        if(check) {
            Path output=Path.of(args[1]);Files.createDirectories(output);
            try(var files=Files.list(output)) {
                if(files.findAny().isPresent())throw new IOException("Visual check needs a new empty output directory");
            }
        }
        Path logs=Files.createDirectories(Path.of("logs"));
        Path runLogs=Files.createTempDirectory(logs,"run-").toAbsolutePath();
        Path session=Files.createTempDirectory("gridrift-session-");
        System.out.println("Window logs: "+runLogs);
        try(RaceServer server=new RaceServer(0)) {
            List<Process> clients=new ArrayList<>();
            Runtime.getRuntime().addShutdownHook(new Thread(()->{server.close();clients.forEach(Process::destroy);}));
            try {
                for(int id=0;id<2;id++) {
                    List<String> command=new ArrayList<>();
                    command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
                    command.add("--enable-native-access=ALL-UNNAMED");
                    if(System.getProperty("os.name").toLowerCase().contains("mac")) {
                        command.add("-XstartOnFirstThread");
                        command.add("-Xdock:name=GRID RIFT - Player "+(id+1));
                    }
                    Path nativeTemp=Files.createDirectory(session.resolve("player-"+(id+1)));
                    command.add("-Djava.io.tmpdir="+nativeTemp);
                    Path ready=session.resolve("player-"+(id+1)+".ready");
                    Path log=runLogs.resolve("player-"+(id+1)+".log");
                    command.add("-XX:ErrorFile="+runLogs.resolve("player-"+(id+1)+"-crash.log"));
                    command.addAll(List.of("-cp",System.getProperty("java.class.path"),"RaceLauncher","--client",
                        Integer.toString(server.port()),Integer.toString(id),ready.toString(),check?Path.of(args[1]).toAbsolutePath().toString():"-"));
                    Process client=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                    clients.add(client);
                    awaitWindow(client,ready,log,60);
                    System.out.println("Player "+(id+1)+" window ready.");
                }
                System.out.println("GRID//RIFT on 127.0.0.1:"+server.port()+" | close either window to end the match.");
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
                while(clients.stream().allMatch(Process::isAlive)) {
                    if(check && System.nanoTime()>deadline)throw new IOException("Visual check timed out");
                    Thread.sleep(100);
                }
                if(!check) {
                    for(int id=0;id<clients.size();id++) {
                        Process client=clients.get(id);
                        if(!client.isAlive() && client.exitValue()!=0)
                            throw windowFailure("Player "+(id+1)+" exited with code "+client.exitValue(),runLogs.resolve("player-"+(id+1)+".log"));
                    }
                }
                if(check) {
                    for(Process client:clients) {
                        if(!client.waitFor(15,TimeUnit.SECONDS) || client.exitValue()!=0) throw new IOException("Visual client failed");
                    }
                    for(int id=0;id<2;id++) if(!Files.exists(Path.of(args[1],"player-"+id+".pass"))) throw new IOException("Missing visual result for player "+id);
                    System.out.println("PASS: two TCP clients, first-person scenes, fog maps, navigation and rendered screenshots");
                }
            } finally {
                clients.forEach(Process::destroy);
                for(Process client:clients)if(!client.waitFor(5,TimeUnit.SECONDS))client.destroyForcibly().waitFor();
                try(var files=Files.walk(session)) {
                    for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);
                }
            }
        }
    }

    static void awaitWindow(Process client,Path ready,Path log,int timeoutSeconds) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while(!Files.isRegularFile(ready)) {
            if(!client.isAlive())throw windowFailure("Window exited before rendering (code "+client.exitValue()+")",log);
            if(System.nanoTime()>deadline)throw windowFailure("Window did not render within "+timeoutSeconds+" seconds",log);
            Thread.sleep(50);
        }
        if(!client.isAlive())throw windowFailure("Window exited during startup",log);
    }
    private static IOException windowFailure(String message,Path log) throws IOException {
        System.err.println(message+". Log: "+log);
        if(Files.exists(log)) {
            List<String> lines=Files.readAllLines(log);
            for(String line:lines.subList(Math.max(0,lines.size()-30),lines.size()))System.err.println(line);
        }
        return new IOException(message+". Please share "+log);
    }

    private static void visualCheck(RaceGame game,RaceConnection connection,Path output) {
        Thread.ofPlatform().name("visual-check").start(()->{
            try {
                Files.createDirectories(output);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
                while((connection.latest==null || !connection.latest.ready()) && System.nanoTime()<deadline)Thread.sleep(50);
                RaceSnapshot start=connection.latest;
                if(start==null || !start.ready())throw new AssertionError("Server never became ready");
                deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
                while(!game.enqueue(game::isViewReady).get(10,TimeUnit.SECONDS)) {
                    if(System.nanoTime()>deadline)throw new AssertionError("First rendered view never became ready");
                    Thread.sleep(50);
                }
                game.enqueue(()->{game.verifyView();return true;}).get(10,TimeUnit.SECONDS);
                connection.send(connection.id,RaceWorld.Action.NAV,true);
                connection.send(connection.id,RaceWorld.Action.NAV,false);
                Thread.sleep(2200);
                RaceSnapshot after=connection.latest;
                if(!after.navUsed() || after.navigating() || (after.x()==start.x() && after.y()==start.y()))throw new AssertionError("Network navigation did not move player");
                game.enqueue(()->{game.verifyView();return true;}).get(10,TimeUnit.SECONDS);
                var screenshot=new com.jme3.app.state.ScreenshotAppState(output.toAbsolutePath()+File.separator,"player-"+connection.id);
                screenshot.setIsNumbered(false);
                game.enqueue(()->{game.getStateManager().attach(screenshot);return true;}).get(5,TimeUnit.SECONDS);
                Thread.sleep(250);
                game.enqueue(()->{screenshot.takeScreenshot();return true;}).get(5,TimeUnit.SECONDS);
                Path picture=output.resolve("player-"+connection.id+".png");
                java.awt.image.BufferedImage image=null;
                while(image==null && System.nanoTime()<deadline) {
                    try { if(Files.exists(picture))image=javax.imageio.ImageIO.read(picture.toFile()); }
                    catch(IOException incomplete) { /* The render thread may still be writing the PNG. */ }
                    if(image==null)Thread.sleep(50);
                }
                if(image==null)throw new AssertionError("Screenshot was not written");
                java.util.Set<Integer> colors=new java.util.HashSet<>();
                for(int y=0;y<image.getHeight();y+=4)for(int x=0;x<image.getWidth();x+=4)colors.add(image.getRGB(x,y));
                if(image.getWidth()!=800 || colors.size()<50)throw new AssertionError("Blank or incomplete rendered frame");
                Files.writeString(output.resolve("player-"+connection.id+".pass"),"scene + fog + first-person camera + network navigation passed\n");
                // Both clients finish their evidence before either one closes the shared match.
                while((!Files.exists(output.resolve("player-0.pass")) || !Files.exists(output.resolve("player-1.pass"))) && System.nanoTime()<deadline)Thread.sleep(50);
                game.stop(true);
            } catch(Throwable e) {e.printStackTrace();game.stop(true);System.exit(1);}
        });
    }
}
