import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Two-player, loopback-only server. Shared keyboard commands may address either player. */
public final class RaceServer implements AutoCloseable {
    private record Input(int id, RaceWorld.Action action, boolean down) { }
    private final ServerSocket listener;
    private final CopyOnWriteArrayList<Socket> sockets = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DataOutputStream> outputs = new CopyOnWriteArrayList<>();
    private final ArrayBlockingQueue<Input> inputs = new ArrayBlockingQueue<>(512);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final RaceWorld world = new RaceWorld();
    private final Thread acceptThread, tickThread;

    public RaceServer(int port) throws IOException {
        listener = new ServerSocket(port, 2, InetAddress.getByName("127.0.0.1"));
        acceptThread = Thread.ofPlatform().daemon().name("race-accept").unstarted(this::accept);
        tickThread = Thread.ofPlatform().daemon().name("race-tick").unstarted(this::tick);
        acceptThread.start();tickThread.start();
    }
    public int port() { return listener.getLocalPort(); }

    private void accept() {
        try {
            for (int id=0; id<2 && running.get(); id++) {
                Socket socket=listener.accept();
                socket.setTcpNoDelay(true);
                sockets.add(socket);
                DataOutputStream out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                out.writeInt(2); out.writeInt(id); out.flush();
                outputs.add(out);
                Thread.ofPlatform().daemon().name("race-input-"+id).start(()->read(socket));
            }
        } catch(IOException e) { if(running.get()) fail(e); }
    }

    private void read(Socket socket) {
        try {
            DataInputStream in=new DataInputStream(socket.getInputStream());
            while(running.get()) {
                int id=in.readUnsignedByte(), action=in.readUnsignedByte();
                boolean down=in.readBoolean();
                if(id>1 || action>=RaceWorld.Action.values().length) throw new IOException("Invalid input packet");
                if(!inputs.offer(new Input(id,RaceWorld.Action.values()[action],down))) throw new IOException("Input queue full");
            }
        } catch(IOException e) { if(running.get()) fail(e); }
    }

    private void tick() {
        try {
            long next=System.nanoTime();
            while(running.get()) {
                boolean ready=outputs.size()==2;
                Input input;
                while((input=inputs.poll())!=null) if(ready) world.input(input.id,input.action,input.down);
                if(ready) world.step();
                // ponytail: synchronous writes suffice for two local clients; use NIO for WAN/backpressure.
                for(int id=0;id<outputs.size();id++) world.snapshot(id,ready).write(outputs.get(id));
                next+=1_000_000_000L/RaceWorld.HZ;
                long remaining=next-System.nanoTime();
                if(remaining>0) TimeUnit.NANOSECONDS.sleep(remaining);
                else next=System.nanoTime();
            }
        } catch(IOException e) { if(running.get()) fail(e); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        catch(RuntimeException e) { fail(e); }
    }

    private void fail(Exception e) {
        System.err.println("Match stopped: "+(e instanceof EOFException ? "a player disconnected" : e.getMessage()));
        close();
    }
    @Override public void close() {
        if(!running.getAndSet(false)) return;
        try { listener.close(); } catch(IOException ignored) { }
        for(Socket socket:sockets) try { socket.close(); } catch(IOException ignored) { }
        tickThread.interrupt(); acceptThread.interrupt();
    }
}
