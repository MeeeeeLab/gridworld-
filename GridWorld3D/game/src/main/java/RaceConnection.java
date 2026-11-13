import java.io.*;
import java.net.*;

public final class RaceConnection implements AutoCloseable {
    private final Socket socket;
    private final DataOutputStream out;
    public final int id;
    public volatile RaceSnapshot latest;
    public volatile String error;

    public RaceConnection(int port) throws IOException {
        socket=new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1",port),5000);
        socket.setTcpNoDelay(true); socket.setSoTimeout(10000);
        DataInputStream in=new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        if(in.readInt()!=1) throw new IOException("Protocol version mismatch");
        id=in.readInt();
        out=new DataOutputStream(socket.getOutputStream());
        Thread.ofPlatform().daemon().name("race-snapshots").start(()->{
            try { while(!socket.isClosed()) latest=RaceSnapshot.read(in); }
            catch(IOException e) { if(!socket.isClosed()) error="Connection lost. Restart the demo."; }
        });
    }
    public synchronized void send(int target, RaceWorld.Action action, boolean down) {
        if(error!=null || socket.isClosed()) return;
        try { out.writeByte(target); out.writeByte(action.ordinal()); out.writeBoolean(down); out.flush(); }
        catch(IOException e) { error="Connection lost. Restart the demo."; }
    }
    public void releaseAll() {
        for(int id=0;id<2;id++) for(RaceWorld.Action action:RaceWorld.Action.values()) send(id,action,false);
    }
    @Override public void close() {
        try { socket.close(); } catch(IOException ignored) { }
    }
}
