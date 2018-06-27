package github.scarsz.wynter.wynter;

import github.scarsz.wynter.wynter.util.ValueUtil;
import net.dv8tion.jda.core.entities.Category;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.channel.text.TextChannelCreateEvent;
import net.dv8tion.jda.core.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.core.events.channel.text.update.TextChannelUpdateNameEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.script.ScriptException;
import java.util.*;

@SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
public class GuildConfig extends ListenerAdapter {

    public final Guild guild;

    public GuildConfig(String guildId) {
        this.guild = Wynter.instance.jda.getGuildById(guildId);
        if (Wynter.instance.jda.getGuildById(guildId) == null) throw new IllegalArgumentException("Attempted to set up GuildConfig for guild ID " + guildId + " but the bot is not in this guild");

        Log.info("Setting up GuildConfig for " + guild.toString());

        recompile();
        Wynter.instance.jda.addEventListener(this);
        Log.info("");
    }

    public final Map<String, Object> meta = new HashMap<>();
    public final List<CommandScriptProvider> commands = new ArrayList<>();
    public final List<EventScriptProvider> events = new ArrayList<>();

    /**
     * (re)Compile all of the scripts and meta information for this guild
     */
    public void recompile() {
        TextChannel metaTextChannel = Wynter.instance.mainGuild.getTextChannelsByName("meta", true).stream().filter(channel -> channel.getParent().getName().equals(guild.getId())).findFirst().orElse(null);
        if (metaTextChannel != null) {
            List<String> metaLines = new ArrayList<>();
            for (Message message : metaTextChannel.getHistory().retrievePast(100).complete()) {
                metaLines.addAll(Arrays.asList(message.getContentRaw().split("\n")));
            }
            for (String line : metaLines) {
                String[] splitLine = line.split(" ", 2);
                Object metaValue = ValueUtil.parse(splitLine[1]);

                if (splitLine[0].equalsIgnoreCase("trigger")) {
                    String[] triggers = new String[metaValue instanceof String ? 1 : ((Object[]) metaValue).length];
                    if (metaValue instanceof String) {
                        triggers[0] = (String) metaValue;
                    } else {
                        for (int i = 0; i < ((Object[]) metaValue).length; i++) {
                            triggers[i] = (String) ((Object[]) metaValue)[i];
                        }
                    }

                    meta.put("trigger", triggers);
                }
            }
        }

        destroy();

        for (TextChannel textChannel : Wynter.instance.mainGuild.getTextChannels()) {
            registerChannel(textChannel);
        }
    }
    public void destroy() {
        commands.forEach(provider -> Wynter.instance.jda.removeEventListener(provider));
        commands.clear();
        events.forEach(provider -> Wynter.instance.jda.removeEventListener(provider));
        events.clear();
    }

    private void registerChannel(TextChannel textChannel) {
        Category parentCategory = textChannel.getParent();
        if (parentCategory == null) return;
        if (!parentCategory.getName().equals(guild.getId())) return;

        if (!textChannel.getName().contains("_")) return;
        String name = textChannel.getName().split("_")[1];
        if (textChannel.getName().startsWith("cmd_") || textChannel.getName().startsWith("command_") || textChannel.getName().startsWith("admin_")) {
            if (commands.stream().anyMatch(provider -> provider.commandName.equalsIgnoreCase(name))) {
                textChannel.delete().reason("Duplicate channel for command " + name).queue(v -> Log.info("Deleted channel because a channel for command " + name + " already exists"));
                return;
            }

            commands.add(new CommandScriptProvider(textChannel.getId(), name));
            Log.info("Listening for command " + name + " @ " + textChannel.getParent().getName() + "/" + textChannel.getName());
        }
        if (textChannel.getName().startsWith("on_") || textChannel.getName().startsWith("event_")) {
            Class<? extends Event> eventClass = Wynter.reflections.getSubTypesOf(Event.class).stream().filter(c -> c.getSimpleName().equalsIgnoreCase(name)).findFirst().orElse(null);
            if (eventClass == null) {
                textChannel.sendMessage("Unknown event class: " + name).queue();
                return;
            }

            events.add(new EventScriptProvider(textChannel.getId(), eventClass));
            Log.info("Listening for event " + eventClass.getSimpleName() + " @ " + textChannel.getParent().getName() + "/" + textChannel.getName());
        }
    }
    private void unregisterChannel(TextChannel textChannel) {
        List<CommandScriptProvider> commandProvidersToRemove = new ArrayList<>();
        commands.stream().filter(provider -> provider.textChannel.getId().equals(textChannel.getId())).forEach(provider -> {
            Wynter.instance.jda.removeEventListener(provider);
            commandProvidersToRemove.add(provider);
        });
        commands.removeAll(commandProvidersToRemove);

        List<EventScriptProvider> eventProvidersToRemove = new ArrayList<>();
        events.stream().filter(provider -> provider.textChannel.getId().equals(textChannel.getId())).forEach(provider -> {
            Wynter.instance.jda.removeEventListener(provider);
            eventProvidersToRemove.add(provider);
        });
        events.removeAll(eventProvidersToRemove);

        Log.info("Unregistered channel " + textChannel.getParent().getName() + "/" + textChannel.getName());
    }

    /**
     * Execute the corresponding script in this guild for the given command
     * @param event Full command to be executed
     * @return Output of evaluating the channel's script, null if no result
     * @throws IllegalArgumentException Thrown if this guild does not have a command script corresponding to the requested command or if the command doesn't start with any relevant triggers
     */
    public Object executeCommand(GuildMessageReceivedEvent event) throws IllegalArgumentException {
        return executeCommand(event, Collections.emptyList());
    }

    public Object executeCommand(GuildMessageReceivedEvent event, List<String> extraTriggers) throws IllegalArgumentException {
        String[] command = event.getMessage().getContentRaw().split(" ", 2);
        String[] triggers = (String[]) meta.getOrDefault("trigger", Wynter.instance.getMainGuildConfig().meta.get("trigger"));
        boolean triggerFound = false;
        for (String trigger : triggers) {
            if (command[0].startsWith(trigger)) {
                command[0] = command[0].substring(trigger.length());
                triggerFound = true;
            }
        }
        for (String extraTrigger : extraTriggers) {
            if (command[0].startsWith(extraTrigger)) {
                command[0] = command[0].substring(extraTrigger.length());
                triggerFound = true;
            }
        }
        if (!triggerFound) {
            throw new IllegalArgumentException("No triggers matched for message \"" + event.getMessage().getContentRaw() + "\" " + Arrays.toString(triggers));
        }

        Log.info(event.getAuthor() + " @ " + event.getChannel() + ": " + event.getMessage().getContentRaw());

        CommandScriptProvider commandProvider = commands.stream().filter(provider -> provider.commandName.equalsIgnoreCase(command[0])).findFirst().orElse(null);
        if (commandProvider == null) {
            throw new IllegalArgumentException("This guild does not contain a command matching " + event.getMessage().getContentRaw());
        }

        if (commandProvider.isAdminOnly() && !Wynter.instance.admins.contains(event.getAuthor().getId())) {
            event.getMessage().addReaction(Emoji.NoEntrySign).queue();
            return null;
        }

        try {
            return commandProvider.eval(event);
        } catch (ScriptException e) {
            e.printStackTrace();
            event.getMessage().addReaction(Emoji.Wrench).queue();
            event.getMessage().addReaction("\uD83C\uDDE7").queue();
            event.getMessage().addReaction("\uD83C\uDDF7").queue();
            event.getMessage().addReaction("\uD83C\uDDF4").queue();
            event.getMessage().addReaction("\uD83C\uDDF0").queue();
            event.getMessage().addReaction("\uD83C\uDDEA").queue();
            event.getMessage().addReaction("\uD83C\uDDF3").queue();
            return null;
        }
    }

    @Override
    public void onTextChannelCreate(TextChannelCreateEvent event) {
        registerChannel(event.getChannel());
    }
    @Override
    public void onTextChannelDelete(TextChannelDeleteEvent event) {
        if (event.getChannel().getParent() == null || !event.getChannel().getParent().getName().equals(guild.getId())) return;
        unregisterChannel(event.getChannel());
    }
    @Override
    public void onTextChannelUpdateName(TextChannelUpdateNameEvent event) {
        unregisterChannel(event.getChannel());
        registerChannel(event.getChannel());
    }

}
