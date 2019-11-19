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

    public UserMove(MoveType mType, Point3Dim pt) {
        this.moveType = mType;
        this.pt = pt;
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

    // convert raw moves into moves that make sense
    // for example, convert two close left turn to 1 u left turn
    public void preprocessMove(List<UserMove> userMoves) {
        List<UserMove> removeList = new ArrayList<>();
        for (int i = 0; i < userMoves.size() - 1; ++i) {
            UserMove currMove = userMoves.get(i);
            UserMove nextMove = userMoves.get(i + 1);
            if (Math.abs(currMove.pt.y - nextMove.pt.y) < 5) {
                boolean doRemove = false;
                if (currMove.moveType == nextMove.moveType) {
                    if (currMove.moveType == MoveType.LEFT) {
                        currMove.moveType = MoveType.U_TURN_LEFT;
                        doRemove = true;
                    } else if (currMove.moveType == MoveType.RIGHT) {
                        currMove.moveType = MoveType.U_TURN_RIGHT;
                        doRemove = true;
                    }
                } else if ((Math.abs(currMove.pt.y - nextMove.pt.y) < 4) && currMove.moveType == MoveType.LEFT
                        && nextMove.moveType == MoveType.RIGHT) {
                    currMove.moveType = MoveType.UP_SHIFT_LEFT;
                    doRemove = true;
                }
                if (doRemove) {
                    ++i;
                    removeList.add(nextMove);
                }
            }
        }
        userMoves.removeAll(removeList);
    }

    public void update(MoveType moveType) {
        if (currHeading == HeadingType.TOP) {
            if (isMoveToRight(moveType)) {
                sector += 1;
            } else if (isMoveToLeft(moveType)) {
                sector -= 1;
            } else if (moveType == MoveType.UP_SHIFT_LEFT) {
                sector -= 1;
            } else {
                throw new RuntimeException("top fell through " + moveType + " " + currHeading);
            }
        } else if (currHeading == HeadingType.BOTTOM) {
            if (isMoveToLeft(moveType)) {
                sector += 1;
            } else if (isMoveToRight(moveType)) {
                sector -= 1;
            } else {
                throw new RuntimeException("bottom fell through " + moveType + " " + currHeading);
            }
        } else if (currHeading == HeadingType.LEFT) {
            if (moveType == MoveType.LEFT || moveType == MoveType.RIGHT) {
                sector = 0;
            } else {
                throw new RuntimeException("left fell through " + moveType + " " + currHeading);
            }
        } else if (currHeading == HeadingType.RIGHT) {
            if (moveType == MoveType.RIGHT) {
                sector += 1;
            } else if (moveType == MoveType.LEFT) {
                sector += 1;
            } else {
                throw new RuntimeException("right fell through " + moveType + " " + currHeading);
            }
        }
        if (sector == 0) {
            if (isMoveToLeft(moveType)) {
                goUpFloor();
            } else if (isMoveToRight(moveType)) {
                goDownFloor();
            } else {
                throw new RuntimeException("sector fell through " + moveType + " " + currHeading);
            }
        }
        updateHeading(moveType);
        if (floor > maxFloor || floor < minFloor) {
            throw new RuntimeException("floor invalid " + floor + " " + moveType + " " + currHeading);
        }
        if (sector > maxSector || sector < minSector) {
            throw new RuntimeException("sector invalid " + sector + " " + moveType + " " + currHeading);
        }
        System.out.println("floor " + floor + " sector " + sector + " heading " + currHeading);
    }
}

public class MoveSolver {
    public static void main(String[] args) {
        // {
        // Location location = new Location(2, 2, 0, 2, 3, 0);
        // location.update(MoveType.UP_SHIFT_LEFT);
        // location.update(MoveType.U_TURN_LEFT);
        // location.update(MoveType.LEFT);
        // location.update(MoveType.LEFT);
        // location.update(MoveType.U_TURN_LEFT);
        // location.update(MoveType.U_TURN_RIGHT);
        // location.update(MoveType.U_TURN_RIGHT);
        // location.update(MoveType.U_TURN_RIGHT);
        // }

        {
            Location location = new Location(2, 2, 0, 2, 3, 0);
            List<UserMove> testMoves = new ArrayList<>();
            testMoves.add(new UserMove(MoveType.LEFT, new Point3Dim(0, 0, 0)));
            testMoves.add(new UserMove(MoveType.RIGHT, new Point3Dim(2, 2, 0)));
            testMoves.add(new UserMove(MoveType.LEFT, new Point3Dim(10, 5, 2)));
            testMoves.add(new UserMove(MoveType.LEFT, new Point3Dim(14, 9, 2)));
            testMoves.add(new UserMove(MoveType.LEFT, new Point3Dim(30, 40, 20)));

            location.preprocessMove(testMoves);
            for (UserMove testMove : testMoves) {
                System.out.println(testMove.moveType);
            }
        }
    }
}