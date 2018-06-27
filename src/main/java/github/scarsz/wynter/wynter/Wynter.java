package github.scarsz.wynter.wynter;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Category;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.channel.category.CategoryCreateEvent;
import net.dv8tion.jda.core.events.channel.category.CategoryDeleteEvent;
import net.dv8tion.jda.core.events.channel.category.update.CategoryUpdateNameEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.security.auth.login.LoginException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Wynter extends ListenerAdapter {

    public static Wynter instance;
    public static Log log;

    public List<String> admins = new ArrayList<String>() {{
        add("95088531931672576"); // Scarsz
        add("142968127829835777"); // Androkai
        add("126506484883259392"); // Buildblox
    }};
    public List<GuildConfig> guildConfigs = new ArrayList<>();
    public Guild mainGuild = null;
    public JDA jda = null;
    public ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("js");

    public static final Reflections reflections;
    static {
        ConfigurationBuilder builder = new ConfigurationBuilder()
                .setScanners(new SubTypesScanner(false), new ResourcesScanner())
                .setUrls(ClasspathHelper.forClassLoader(Arrays.asList(ClasspathHelper.contextClassLoader(), ClasspathHelper.staticClassLoader()).toArray(new ClassLoader[0])))
                .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix("net.dv8tion.jda.core.events")));
        builder = builder.setUrls(builder.getUrls().stream().filter(url -> !Pattern.compile(".*[.](so|dll)", Pattern.CASE_INSENSITIVE).matcher(url.getFile()).matches()).collect(Collectors.toList()));
        reflections = new Reflections(builder);
    }

    public Wynter(String botToken) throws LoginException, InterruptedException {
        Wynter.instance = this;
        Wynter.log = new Log("360238302164680706");

        jda = new JDABuilder(AccountType.BOT)
                .setToken(botToken)
                .setAutoReconnect(true)
                .setBulkDeleteSplittingEnabled(false)
                .setGame(Game.playing("in the snow"))
                .buildBlocking();
        Log.info("JDA connected, initializing\n");
        mainGuild = jda.getGuildById("360223454768660492");

        jda.addEventListener(this);
        scriptEngine.put("jda", jda);

        for (Category category : mainGuild.getCategories()) {
            String guildId = category.getName().replaceAll("\\D", "");
            if (StringUtils.isBlank(guildId)) continue;
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                Log.warn("Category for guild ID " + category.getName() + " exists but the bot is not a part of this guild");
                continue;
            }
            guildConfigs.add(new GuildConfig(category.getName()));
        }
        Log.info("Initialized, ready for action\n");
    }

    public void executeGlobalCommand(GuildMessageReceivedEvent event, List<String> extraTriggers) {
        getMainGuildConfig().executeCommand(event, extraTriggers);
    }
    public GuildConfig getMainGuildConfig() {
        GuildConfig guild = guildConfigs.stream().filter(info -> info.guild.getId().equals(mainGuild.getId())).findFirst().orElse(null);
        assert guild != null;
        return guild;
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getGuild().equals(mainGuild) && event.getChannel().getParent() != null && StringUtils.isNumeric(event.getChannel().getParent().getName()) && event.getJDA().getGuildById(event.getChannel().getParent().getName()) != null) return; // don't process messages from source code channels
        if (event.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) return; // don't process messages from self

        for (GuildConfig config : guildConfigs) {
            if (!event.getGuild().equals(config.guild)) continue; // only match this config if the guilds match
            if (config == getMainGuildConfig()) continue; // handled below

            try {
                config.executeCommand(event);
                return;
            } catch (IllegalArgumentException ignored) {}
        }

        // if this is still being executed, no GuildConfigs had a command matching the requested one with the same guild
        // fall back to Wynter's GuildConfig

        try {
            Wynter.instance.executeGlobalCommand(event, guildConfigs.stream().filter(gc -> gc.guild.equals(event.getGuild())).map(gc -> Arrays.asList((String[]) gc.meta.get("trigger"))).findFirst().orElse(Collections.emptyList()));
        } catch (IllegalArgumentException e) {
            // no matching command found
            if (event.getMessage().getContentRaw().split("\\*").length % 2 == 0) event.getMessage().addReaction(Emoji.Question).queue();
        } catch (Exception ignored) {}
    }

    @Override
    public void onCategoryCreate(CategoryCreateEvent event) {
        guildConfigs.add(new GuildConfig(event.getCategory().getName()));
    }
    @Override
    public void onCategoryUpdateName(CategoryUpdateNameEvent event) {
        GuildConfig config = guildConfigs.stream().filter(gc -> gc.guild.getId().equals(event.getGuild().getId())).findFirst().orElse(null);
        if (config == null) return;
        config.destroy();

        guildConfigs.add(new GuildConfig(event.getCategory().getName()));
    }
    @Override
    public void onCategoryDelete(CategoryDeleteEvent event) {
        GuildConfig config = guildConfigs.stream().filter(gc -> gc.guild.getId().equals(event.getGuild().getId())).findFirst().orElse(null);
        if (config == null) return;
        config.destroy();
    }

}
