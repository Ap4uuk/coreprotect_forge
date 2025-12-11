package ru.ap4uuk.coreprotect.command;

public class RollbackParams {

    public Integer radius;
    public Integer seconds;
    public String playerName;

    // НОВОЕ:
    public Integer sessionId;

    @Override
    public String toString() {
        return "RollbackParams{" +
                "radius=" + radius +
                ", seconds=" + seconds +
                ", playerName='" + playerName + '\'' +
                ", sessionId=" + sessionId +
                '}';
    }
}

