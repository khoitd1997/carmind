import java.util.*;

enum MoveType {
    UP, DOWN,

    LEFT, LEFT_DOWN, LEFT_UP,

    RIGHT, RIGHT_DOWN, RIGHT_UP,

    U_TURN_LEFT, U_TURN_LEFT_DOWN, U_TURN_LEFT_UP,

    U_TURN_RIGHT, U_TURN_RIGHT_DOWN, U_TURN_RIGHT_UP,

    // UP_SHIFT defined by left turn immediately followed by right turn
    UP_SHIFT_LEFT
}

enum HeadingType {
    TOP(0), BOTTOM(2), LEFT(3), RIGHT(1);

    private static Map<Integer, HeadingType> map = new HashMap<Integer, HeadingType>();
    private int numVal;

    HeadingType(int numVal) {
        this.numVal = numVal;
    }

    static {
        for (HeadingType heading : HeadingType.values()) {
            map.put(heading.numVal, heading);
        }
    }

    public static HeadingType valueOf(int val) {
        return (HeadingType) map.get(val);
    }

    public int getNumVal() {
        return numVal;
    }
}

class Point3Dim {
    public double x;
    public double y;
    public double z;

    public Point3Dim(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}

class UserMove {
    public MoveType moveType;
    public Point3Dim pt;

    public UserMove(MoveType mType) {
        this.moveType = mType;
        // this.pt = pt;
    }
}

class Location {
    public int floor;
    public int maxFloor;
    public int minFloor;

    public int sector;
    public int maxSector;
    public int minSector;

    public HeadingType currHeading;

    public Location(int initialSector, int maxSector, int minSector, int initialFloor, int maxFloor, int minFloor) {
        this.sector = initialSector;
        this.maxSector = maxSector;
        this.minSector = minSector;

        this.floor = initialFloor;
        this.maxFloor = maxFloor;
        this.minFloor = minFloor;

        currHeading = HeadingType.TOP;
    }

    private void goDownFloor() {
        floor -= 1;
        sector = 0;
    }

    private void goUpFloor() {
        floor += 1;
        sector = 0;
    }

    private Boolean isMoveToRight(MoveType move) {
        return move.toString().contains("RIGHT");
    }

    private Boolean isMoveToLeft(MoveType move) {
        return move.toString().contains("LEFT");
    }

    private void updateHeading(MoveType moveType) {
        if (moveType.toString().contains("U_TURN")) {
            if (currHeading == HeadingType.LEFT || currHeading == HeadingType.RIGHT) {
                throw new RuntimeException("u turn when heading left or right");
            }
            currHeading = HeadingType.valueOf((currHeading.getNumVal() + 2) % 4);
        } else if (moveType.toString().contains("SHIFT")) {
            if (currHeading != HeadingType.TOP) {
                throw new RuntimeException("can only shift when heading is top");
            }
            return;
        } else if (moveType.toString().contains("LEFT")) {
            int headingIntVal = currHeading.getNumVal() - 1;
            if (headingIntVal < 0) {
                headingIntVal += 4;
            }
            currHeading = HeadingType.valueOf(headingIntVal);
        } else if (moveType.toString().contains("RIGHT")) {
            int headingIntVal = currHeading.getNumVal() + 1;
            if (headingIntVal > 3) {
                headingIntVal -= 4;
            }
            currHeading = HeadingType.valueOf(headingIntVal);
        } else {
            throw new RuntimeException("fell through in heading " + currHeading + " " + moveType);
        }
    }

    public void update(UserMove move) {
        if (currHeading == HeadingType.TOP) {
            if (isMoveToRight(move.moveType)) {
                sector += 1;
            } else if (isMoveToLeft(move.moveType)) {
                sector -= 1;
            } else if (move.moveType == MoveType.UP_SHIFT_LEFT) {
                sector -= 1;
            } else {
                throw new RuntimeException("top fell through " + move.moveType + " " + currHeading);
            }
        } else if (currHeading == HeadingType.BOTTOM) {
            if (isMoveToLeft(move.moveType)) {
                sector += 1;
            } else if (isMoveToRight(move.moveType)) {
                sector -= 1;
            } else {
                throw new RuntimeException("bottom fell through " + move.moveType + " " + currHeading);
            }
        } else if (currHeading == HeadingType.LEFT) {
            if (move.moveType == MoveType.LEFT || move.moveType == MoveType.RIGHT) {
                sector = 0;
            } else {
                throw new RuntimeException("left fell through " + move.moveType + " " + currHeading);
            }
        } else if (currHeading == HeadingType.RIGHT) {
            if (move.moveType == MoveType.RIGHT) {
                sector += 1;
            } else if (move.moveType == MoveType.LEFT) {
                sector += 1;
            } else {
                throw new RuntimeException("right fell through " + move.moveType + " " + currHeading);
            }
        }
        if (sector == 0) {
            if (isMoveToLeft(move.moveType)) {
                goUpFloor();
            } else if (isMoveToRight(move.moveType)) {
                goDownFloor();
            } else {
                throw new RuntimeException("sector fell through " + move.moveType + " " + currHeading);
            }
        }
        updateHeading(move.moveType);
        if (floor > maxFloor || floor < minFloor) {
            throw new RuntimeException("floor invalid " + floor + " " + move.moveType + " " + currHeading);
        }
        if (sector > maxSector || sector < minSector) {
            throw new RuntimeException("sector invalid " + sector + " " + move.moveType + " " + currHeading);
        }
        System.out.println("floor " + floor + " sector " + sector + " heading " + currHeading);
    }
}

public class MoveSolver {
    public static void main(String[] args) {
        {
            Location location = new Location(2, 2, 0, 2, 3, 0);
            location.update(new UserMove(MoveType.UP_SHIFT_LEFT));
            location.update(new UserMove(MoveType.U_TURN_LEFT));
            location.update(new UserMove(MoveType.LEFT));
            location.update(new UserMove(MoveType.LEFT));
            location.update(new UserMove(MoveType.U_TURN_LEFT));
            location.update(new UserMove(MoveType.U_TURN_RIGHT));
            location.update(new UserMove(MoveType.U_TURN_RIGHT));
            location.update(new UserMove(MoveType.U_TURN_RIGHT));
        }
    }
}