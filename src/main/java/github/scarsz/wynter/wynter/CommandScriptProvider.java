package github.scarsz.wynter.wynter;

public class CommandScriptProvider extends ScriptProvider {

    public final String commandName;

    public CommandScriptProvider(String channelId, String commandName) {
        super(channelId);
        this.commandName = commandName;
    }

    public boolean isAdminOnly() {
        return textChannel.getName().split("_")[0].equalsIgnoreCase("admin");
    }

}
