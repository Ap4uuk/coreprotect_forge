package ru.ap4uuk.coreprotect.command;

public class LookupParams {

    public Integer radius;
    public Integer seconds;
    public String playerName;
    public Integer page;

    @Override
    public String toString() {
        return "LookupParams{" +
                "radius=" + radius +
                ", seconds=" + seconds +
                ", playerName='" + playerName + '\'' +
                ", page=" + page +
                '}';
    }
}

