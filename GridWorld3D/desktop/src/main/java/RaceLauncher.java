import com.jme3.system.AppSettings;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One server, two independent Java client processes and two first-person windows. */
public final class RaceLauncher {
    public static void main(String[] args) throws Exception {
        if(args.length>=2 && args[0].equals("--client")) {
            RaceConnection connection=new RaceConnection(Integer.parseInt(args[1]));
            RaceGame game=new RaceGame(connection);
            AppSettings settings=new AppSettings(true);
            settings.setTitle(connection.id==0?"GRID//RIFT  |  01 SCOUT":"GRID//RIFT  |  02 SIEGE");
            settings.setResolution(800,640);settings.setResizable(false);settings.setFrameRate(60);
            settings.setAudioRenderer(null);settings.setVSync(true);settings.setSamples(2);
            settings.setCenterWindow(false);settings.setWindowXPosition(20+connection.id*820);settings.setWindowYPosition(40);
            game.setSettings(settings);game.setShowSettings(false);game.start();
            if(args.length==3) visualCheck(game,connection,Path.of(args[2]));
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
        try(RaceServer server=new RaceServer(0)) {
            List<Process> clients=new ArrayList<>();
            Runtime.getRuntime().addShutdownHook(new Thread(()->{server.close();clients.forEach(Process::destroy);}));
            try {
                for(int id=0;id<2;id++) {
                    List<String> command=new ArrayList<>();
                    command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
                    if(System.getProperty("os.name").toLowerCase().contains("mac")) command.add("-XstartOnFirstThread");
                    command.addAll(List.of("-cp",System.getProperty("java.class.path"),"RaceLauncher","--client",Integer.toString(server.port())));
                    if(check)command.add(args[1]);
                    clients.add(new ProcessBuilder(command).inheritIO().start());
                }
                System.out.println("GRID//RIFT on 127.0.0.1:"+server.port()+" | close either window to end the match.");
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
                while(clients.stream().allMatch(Process::isAlive)) {
                    if(check && System.nanoTime()>deadline)throw new IOException("Visual check timed out");
                    Thread.sleep(100);
                }
                if(check) {
                    for(Process client:clients) {
                        if(!client.waitFor(15,TimeUnit.SECONDS) || client.exitValue()!=0) throw new IOException("Visual client failed");
                    }
                    for(int id=0;id<2;id++) if(!Files.exists(Path.of(args[1],"player-"+id+".pass"))) throw new IOException("Missing visual result for player "+id);
                    System.out.println("PASS: two TCP clients, first-person scenes, fog maps, navigation and rendered screenshots");
                }
            } finally {clients.forEach(Process::destroy);}
        }
    }

    private static void visualCheck(RaceGame game,RaceConnection connection,Path output) {
        Thread.ofPlatform().name("visual-check").start(()->{
            try {
                Files.createDirectories(output);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
                while((connection.latest==null || !connection.latest.ready()) && System.nanoTime()<deadline)Thread.sleep(50);
                RaceSnapshot start=connection.latest;
                if(start==null || !start.ready())throw new AssertionError("Server never became ready");
                Thread.sleep(1500);
                game.enqueue(()->{game.verifyView();return true;}).get(10,TimeUnit.SECONDS);
                connection.send(connection.id,RaceWorld.Action.NAV,true);
                connection.send(connection.id,RaceWorld.Action.NAV,false);
                Thread.sleep(2200);
                RaceSnapshot after=connection.latest;
                if(!after.navUsed() || (after.x()==start.x() && after.y()==start.y()))throw new AssertionError("Network navigation did not move player");
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
