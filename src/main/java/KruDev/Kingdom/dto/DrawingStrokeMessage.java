package KruDev.Kingdom.dto;

import lombok.Data;

/** Drawing stroke sent by the current drawer; relayed to all game subscribers. */
@Data
public class DrawingStrokeMessage {

    public enum StrokeType { START, MOVE, END, CLEAR }

    private StrokeType strokeType;
    private double x;
    private double y;
    private String color;
    private int brushSize;
    /** Session token of the sender — validated server-side before relay. */
    private String sessionToken;
}
