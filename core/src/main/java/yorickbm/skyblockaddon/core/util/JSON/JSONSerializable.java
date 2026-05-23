package yorickbm.skyblockaddon.core.util.JSON;

public interface JSONSerializable {

    public String toJSON();
    public void fromJSON(final String json);

}
