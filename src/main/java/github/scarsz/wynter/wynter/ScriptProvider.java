package github.scarsz.wynter.wynter;

import github.scarsz.wynter.wynter.util.ReflectionUtils;
import github.scarsz.wynter.wynter.util.ValueUtil;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageHistory;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;

import javax.script.ScriptException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class ScriptProvider extends ListenerAdapter {

    public final Map<String, Object> flags = new HashMap<>();
    public String script;
    public final TextChannel textChannel;

    public ScriptProvider(String channelId) {
        this.textChannel = Wynter.instance.jda.getTextChannelById(channelId);
        compileScript(true);

        Wynter.instance.jda.addEventListener(this);
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (!event.getChannel().getId().equals(textChannel.getId())) return;
        if (event.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) return;
        compileScript(true);
    }
    @Override
    public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
        if (!event.getChannel().getId().equals(textChannel.getId())) return;
        if (event.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) return;
        compileScript(true);
    }
    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
        if (!event.getChannel().getId().equals(textChannel.getId())) return;
        compileScript();
    }

    public void compileScript() {
        compileScript(false);
    }
    public void compileScript(boolean clearBotMessages) {
        List<String> script = new LinkedList<>();

        MessageHistory history = textChannel.getHistory();
        while (history.retrievePast(100).complete().size() > 0);
        List<Message> messagesToDelete = new ArrayList<>();
        for (Message message : history.getRetrievedHistory()) {
            if (message.getAuthor().getId().equals(message.getJDA().getSelfUser().getId())) {
                if (clearBotMessages) {
                    // delete messages the bot sent (script execution messages)
                    messagesToDelete.add(message);
                }

                continue;
            }

            script.addAll(Arrays.asList(message.getContentRaw().split("\n")));
        }
        if (messagesToDelete.size() == 1) {
            textChannel.deleteMessageById(messagesToDelete.get(0).getId()).queue();
        } else if (messagesToDelete.size() > 1) {
            textChannel.deleteMessages(messagesToDelete).queue();
        }

        script = script.stream().filter(s -> !processLineForFlag(s)).collect(Collectors.toList());
        this.script = String.join("\n", script);
        Log.info("Compiled script for " + textChannel);
    }

    public boolean processLineForFlag(String line) {
        if (!line.startsWith("@")) return false;
        line = line.substring(1);

        String[] flag = line.split(" ", 2);
        flags.put(flag[0], flag.length == 2 ? ValueUtil.parse(flag[1]) : null);

        return true;
    }

    public boolean eval(Event event) throws ScriptException {
        Wynter.instance.scriptEngine.put("event", event);
        ReflectionUtils.collectVariables(event).forEach((s, o) -> Wynter.instance.scriptEngine.put(s, o));

        if (StringUtils.isBlank(script)) return true;

        Object result;
        long time = System.currentTimeMillis();
        try {
            result = Wynter.instance.scriptEngine.eval(script);
            time -= System.currentTimeMillis();
        } catch (Exception e) {
            this.textChannel.sendMessage(Emoji.X + " FAILED TO EVALUATE: " + e.getMessage()).queue();
            throw e;
        }

        String eventInformation = ReflectionUtils.collectVariables(event).entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
        String message = Emoji.WhiteCheckMark + " Evaluated successfully in " + Math.abs(time) + "ms";
        if (!StringUtils.isBlank(eventInformation)) message += " `(" + eventInformation + ")`";
        if (result != null) message += " with result: ```" + result + "```";
        this.textChannel.sendMessage(message).queue(m -> m.delete().queueAfter(5, TimeUnit.MINUTES, null, (Consumer<Throwable>) null));
        return true;
    }

}
