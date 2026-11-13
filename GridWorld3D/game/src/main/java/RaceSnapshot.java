import java.io.*;

/** Only explored terrain and currently visible enemies cross the network. */
public record RaceSnapshot(int tick, int winner, boolean ready, int x, int y, int facing,
        boolean navUsed, boolean navigating, int stunned, int cooldown, int jammed, int flash,
        int enemyX, int enemyY, int enemyFacing, int enemyFlash, int shots, int hits,
        int steps, int score, int opponentSteps, int opponentScore, String known) {
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(tick); out.writeInt(winner); out.writeBoolean(ready);
        out.writeInt(x); out.writeInt(y); out.writeInt(facing);
        out.writeBoolean(navUsed); out.writeBoolean(navigating);
        out.writeInt(stunned); out.writeInt(cooldown); out.writeInt(jammed); out.writeInt(flash);
        out.writeInt(enemyX); out.writeInt(enemyY); out.writeInt(enemyFacing); out.writeInt(enemyFlash);
        out.writeInt(shots); out.writeInt(hits);
        out.writeInt(steps); out.writeInt(score); out.writeInt(opponentSteps); out.writeInt(opponentScore);
        out.writeUTF(known); out.flush();
    }
    public static RaceSnapshot read(DataInputStream in) throws IOException {
        return new RaceSnapshot(in.readInt(),in.readInt(),in.readBoolean(),in.readInt(),in.readInt(),in.readInt(),
            in.readBoolean(),in.readBoolean(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),
            in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),
            in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readUTF());
    }
}
