package github.scarsz.wynter.wynter;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.Event;

import javax.script.ScriptException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class EventScriptProvider extends ScriptProvider {

    public final Class<? extends Event> event;

    public EventScriptProvider(String channelId, Class<? extends Event> event) {
        super(channelId);
        this.event = event;
    }

    @Override
    public void onGenericEvent(Event event) {
        if (!this.event.isAssignableFrom(event.getClass())) return;

        Guild guild = null;
        Method guildMethod = Arrays.stream(event.getClass().getMethods()).filter(method -> method.getName().equals("getGuild")).findFirst().orElse(null);
        if (guildMethod == null) return;
        try { guild = (Guild) guildMethod.invoke(event); } catch (Exception e) { e.printStackTrace(); }
        assert guild != null;
        if (!guild.equals(this.textChannel.getGuild())) return;

        try { eval(event); } catch (Exception ignored) {}
    }

}
