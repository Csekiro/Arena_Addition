package Csekiro.arena.entity;

public enum PortalColor {
    BLUE(0, "blue", 0x3F7BFF),
    ORANGE(1, "orange", 0xFF9A2E);

    private final int id;
    private final String name;
    private final int rgb;

    PortalColor(int id, String name, int rgb) {
        this.id = id;
        this.name = name;
        this.rgb = rgb;
    }

    public int id() {
        return this.id;
    }

    public String serializedName() {
        return this.name;
    }

    public int rgb() {
        return this.rgb;
    }

    public PortalColor opposite() {
        return this == BLUE ? ORANGE : BLUE;
    }

    public static PortalColor byId(int id) {
        return id == ORANGE.id ? ORANGE : BLUE;
    }
}
