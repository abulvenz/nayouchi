package io.vertx.starter;

import de.eismaenners.nayouchi.Consumes;
import de.eismaenners.nayouchi.EventBusConsumers;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.redis.RedisClient;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class MainVerticle extends AbstractVerticle {

    private final String WEB_ROOT = "src/main/webroot/";

    List<NameSearchGroup> currentGroups = new LinkedList<>();

    MailClient mailClient;
    MessageDigest encryptor;
    RedisClient redis;
    JedisPool jedisPool;

    JsonObject config;

    // The assumption with this method is that it's been called when the application
// is booting up so that a static pool has been created for all threads to use.
// e.g. pool = getPool()
//
    public static JedisPool getPool() throws URISyntaxException {
        URI redisURI = new URI(System.getenv("REDIS_URL"));
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        JedisPool pool = new JedisPool(poolConfig, redisURI);
        return pool;
    }

    @Override
    public void start() {
        readConfig();

        setupAndRestore();

        Router router = Router.router(vertx);

        // Allow outbound traffic to the vtoons addresses
        BridgeOptions options = new BridgeOptions()
                // this is the public store allowed inbound
                .addInboundPermitted(new PermittedOptions())
                // all outbound messages are permitted
                .addOutboundPermitted(new PermittedOptions());

        router.get("/usr/:userID").handler(ctx -> {
            ctx.reroute("/");
        });

        router.route("/eventbus/*").handler(SockJSHandler.create(vertx).bridge(options));

        EventBusConsumers.setupConsumers(this, vertx.eventBus());

        router.route().handler(StaticHandler
                .create(WEB_ROOT)
                .setCacheEntryTimeout(3600 * 12 * 1000)
                .setCachingEnabled(config.getJsonObject("general").getBoolean("caching", Boolean.TRUE))
                .setMaxAgeSeconds(3600 * 12));

        final int port = System.getenv("PORT") == null
                ? generalConfig().getInteger("port", 8888)
                : Integer.parseInt(System.getenv("PORT"));

        System.out.println("io.vertx.starter.MainVerticle.start() -> listening on port " + port);

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(port);
    }

    @Consumes(path = "proposeName")
    public void proposeName(Message<JsonObject> msg) {
        msgGroupAndUser(msg, (group, user) -> {
            msgParam(msg, "name", name -> {
                updateNames(group, user, (names) -> {
                    names.add(name.trim());
                    return names.stream().distinct().collect(Collectors.toList());
                });
            });
        });
    }

    @Consumes(path = "removeName")
    public void removeName(Message<JsonObject> msg) {
        msgGroupAndUser(msg, (group, user) -> {
            msgParam(msg, "name", name -> {
                updateNames(group, user, (names) -> {
                    names.remove(name);
                    return names.stream().distinct().collect(Collectors.toList());
                });
            });
        });
    }

    private void updateNames(NameSearchGroup group, NameSearchMember user, Function<List<String>, List<String>> todo) {
        user.nominations = todo.apply(user.nominations);
        backupOrRemove(group);
        sendUpdateSignalToUsers(group);
    }

    @Consumes(path = "resign")
    public void resign(Message<JsonObject> msg) {
        msgGroupAndUser(msg, (group, user) -> {
            updateGroup(group, (__)
                    -> group.members = group.members.stream().filter(u -> !u.id.equals(user.id)).collect(Collectors.toList()));
        });

        msgParam(msg, "grp", grpID
                -> msg.reply(findOptionalGroup(filterByGroupID(grpID))
                        .map(group -> new JsonObject().put("group", "updated"))
                        .orElse(new JsonObject().put("group", "removed")).encode()));
    }

    @Consumes(path = "upgrade")
    public void upgrade(Message<JsonObject> msg) {
        msgParam(msg, "grp", grpID -> msgParam(msg, "member", memberName -> {
            NameSearchGroup group = groupByID(grpID);
            updateUser(group, findUserInGroup(group, user -> user.name.equals(memberName)), user -> {
                user.role = Role.INITIATOR;
            });
        }));
    }

    @Consumes(path = "create")
    public void createGroup(Message<JsonObject> msg) {
        NameSearchGroup nsg = new NameSearchGroup();
        nsg.id = UUID.randomUUID().toString();
        nsg.name = msg.body().getString("name");
        NameSearchMember user = new NameSearchMember();
        user.id = msg.body().getString("usr");
        user.name = "";
        user.role = Role.INITIATOR;
        nsg.members.add(user);
        currentGroups.add(nsg);
        backupOrRemove(nsg);
        msg.reply(new JsonObject().put("update", "now").put("grp", nsg.id));
    }

    @Consumes(path = "list")
    public void listGroups(Message<JsonObject> msg) {
        msgParam(msg, "usr", userID -> {
            List<NameSearchGroup> theUsersGroups = currentGroups.stream()
                    .filter(group -> group.members.stream()
                    .filter(member -> member.id.equals(userID))
                    .findAny()
                    .isPresent())
                    .collect(Collectors.toList());
            msg.reply(theUsersGroups.stream()
                    .map(group -> group.toPublic(userID))
                    .collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
        });
    }

    @Consumes(path = "enter")
    public void enterGroup(Message<JsonObject> msg) {
        msgGroupAndUserID(msg, (grpID, userID) -> {
            msg.reply(groupByID(grpID).toCurrent(userID));
        });
    }

    @Consumes(path = "setUserName")
    public void setUserName(Message<JsonObject> msg) {
        msgGroupAndUser(msg, (group, user)
                -> msgParam(msg, "name", name
                        -> updateUser(group, user, user_ -> user_.name = name)));
    }

    @Consumes(path = "addMember")
    public void addMember(Message<JsonObject> msg) {
        msgGroupAndUser(msg, (group, user) -> {
            if (user.role == Role.PROPOSER) {
                return;
            }
            msgParam(msg, "email", email -> {
                String id = encryptWithSecret(email);

                String subject = "Signup";
                String text = "Du wurdest eingeladen, einen Namen zu suchen in der Gruppe " + group.name + ". Um teilzunehmen, folge dem Link: ";
                String confirmationlink = generalConfig().getString("appUrl") + "/usr/" + id;

                MailMessage mailMessage = new MailMessage(invitationConfig().getString("fromAdress"), email, subject, text + confirmationlink);

                mailClient.sendMail(mailMessage, (sendMail) -> {
                    if (sendMail.succeeded()) {
                        NameSearchMember newMember = new NameSearchMember();
                        newMember.id = id;
                        group.members.add(newMember);
                        backupOrRemove(group);
                        msg.reply(new JsonObject().put("result", "success").encode());
                    } else {
                        msg.reply(new JsonObject().put("result", "error").put("error", sendMail.cause().getMessage()).encode());
                    }
                });
            });
        });
    }

    @Consumes(path = "signup")
    public void signup(Message<JsonObject> msg) {
        msgParam(msg, "email", email -> {
            String id = encryptWithSecret(email);

            String subject = "Signup";
            String text = "no text ";
            String confirmationlink = generalConfig().getString("appUrl") + "/usr/" + id;

            MailMessage mailMessage = new MailMessage(invitationConfig().getString("fromAdress"), email, subject, text + confirmationlink);

            mailClient.sendMail(mailMessage, (sendMail) -> {
                if (sendMail.succeeded()) {
                    msg.reply(new JsonObject().put("result", "success").encode());
                } else {
                    msg.reply(new JsonObject().put("result", "error").put("error", sendMail.cause().getMessage()).encode());
                }
            });
        });
    }

    private NameSearchGroup groupByID(String grpID) {
        return findGroup(filterByGroupID(grpID));
    }

    public void msgGroupAndUser(Message<JsonObject> msg, BiConsumer<NameSearchGroup, NameSearchMember> todo) {
        msgGroupAndUserID(msg, (grpID, usrID) -> {
            final NameSearchGroup group = findGroup(filterByGroupID(grpID));
            todo.accept(group, findUserInGroup(group, filterByUserID(usrID)));
        });
    }

    private void msgParam(Message<JsonObject> msg, String paramName, Consumer<String> todo) {
        String name = msg.body().getString(paramName);
        todo.accept(name);
    }

    public void msgGroupAndUserID(Message<JsonObject> msg, BiConsumer<String, String> todo) {
        msgParam(msg, "usr", usrID -> {
            msgParam(msg, "grp", grpID -> {
                todo.accept(grpID, usrID);
            });
        });
    }

    private Predicate<NameSearchGroup> filterByGroupID(String grpID) {
        final Predicate<NameSearchGroup> filterByGroupID = grp -> grp.id.equals(grpID);
        return filterByGroupID;
    }

    private Predicate<NameSearchMember> filterByUserID(String usrID) {
        final Predicate<NameSearchMember> filterByUserID = member -> member.id.equals(usrID);
        return filterByUserID;
    }

    private void updateGroup(NameSearchGroup group, Consumer<NameSearchGroup> todo) {
        todo.accept(group);
        if (backupOrRemove(group)) {
            sendUpdateSignalToUsers(group);
        }
    }

    private void updateUser(NameSearchGroup group, NameSearchMember user, Consumer<NameSearchMember> todo) {
        todo.accept(user);
        backupOrRemove(group);

        sendUpdateSignalToUsers(group);
    }

    private void sendUpdateSignalToUsers(NameSearchGroup group) {
        vertx.eventBus().publish("grp-" + group.id, new JsonObject().put("update", "now"));
    }

    private NameSearchMember findUserInGroup(NameSearchGroup group, final Predicate<NameSearchMember> filterByUserID) {
        NameSearchMember user = group.members.stream()
                .filter(filterByUserID)
                .findAny().orElseThrow(() -> new RuntimeException("user not found"));
        return user;
    }

    private Optional<NameSearchGroup> findOptionalGroup(final Predicate<NameSearchGroup> filterByGroupID) {
        return currentGroups.stream()
                .filter(filterByGroupID)
                .findAny();
    }

    private NameSearchGroup findGroup(final Predicate<NameSearchGroup> filterByGroupID) {
        NameSearchGroup group = currentGroups.stream()
                .filter(filterByGroupID)
                .findAny().orElseThrow(() -> new RuntimeException("group not found"));
        return group;
    }

    private MailClient createMailClient() {
        MailConfig mailConfig = new MailConfig(config.getJsonObject("mail"));
        mailConfig.setStarttls(StartTLSOptions.REQUIRED);
        return MailClient.createShared(vertx, mailConfig);
    }

    private String appSecret() {
        return securityConfig().getString("secret");
    }

    private JsonObject securityConfig() {
        return config.getJsonObject("security");
    }

    private JsonObject invitationConfig() {
        return config.getJsonObject("invitation");
    }

    private JsonObject generalConfig() {
        return config.getJsonObject("general");
    }

    private String encryptWithSecret(String email) {
        String secret = appSecret();
        String id = encryption(secret + email);
        return id;
    }

    String encryption(String input) {
        if (input == null) {
            return null;
        }
        byte[] passBytes = input.getBytes();
        encryptor.reset();
        byte[] digested = encryptor.digest(passBytes);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digested.length; i++) {
            sb.append(Integer.toHexString(0xff & digested[i]));
        }
        return sb.toString();
    }

    public boolean backupOrRemove(NameSearchGroup group) {
        if (group.members.stream().anyMatch(m -> m.role == Role.INITIATOR)) {
            System.out.println("io.vertx.starter.MainVerticle.backup(BACKUP)");
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    String json = Json.encode(group);
                    jedis.hset("groups", group.id, json);
                }
            } else {
                backupToFile(group);
            }
            return true;
        } else {
            System.out.println("io.vertx.starter.MainVerticle.backup(REMOVE)");
            currentGroups.remove(group);
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.hdel("groups", group.id);
                }
            } else {

            }
        }
        return false;
    }

    public void restore() {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {

                Set<String> groups = jedis.hkeys("groups");

                currentGroups = groups.stream()
                        .map(e -> Json.decodeValue(jedis.hget("groups", e), NameSearchGroup.class))
                        .collect(Collectors.toList());
            }
        } else {
            restoreFromFile();
        }
    }

    public void backupToFile(NameSearchGroup group) {
        final String backupName = databaseConfigFolder() + "/" + group.id + ".json";
        final File groupFile = new File(backupName);
        String json = Json.encode(group);
        System.out.println("io.vertx.starter.MainVerticle.backup(" + backupName + ") " + json);
        try (final FileWriter fileWriter = new FileWriter(groupFile)) {
            fileWriter.write(json);
        } catch (IOException ex) {
            Logger.getLogger(MainVerticle.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void restoreFromFile() {
        final File databaseBaseFolder = new File(databaseConfigFolder());

        currentGroups = Arrays.stream(databaseBaseFolder.listFiles())
                .map(file -> createBufferedReader(file))
                .map(reader -> reader.lines().collect(Collectors.joining()))
                .map(lastState -> Json.decodeValue(lastState, NameSearchGroup.class))
                .collect(Collectors.toList());

    }

    private static BufferedReader createBufferedReader(File file) {
        try {
            return new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException ex) {
            Logger.getLogger(MainVerticle.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    public void log(String str) {
        System.out.println(">> " + str);
    }

    private void setupAndRestore() {
        if (System.getenv("REDIS_URL") != null) {
            try {
                jedisPool = getPool();
            } catch (Exception ex) {
                Logger.getLogger(MainVerticle.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        new File(databaseConfigFolder()).mkdirs();

        restore();

        try {
            this.encryptor = MessageDigest.getInstance(securityConfig().getString("encryption"));
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(MainVerticle.class.getName()).log(Level.SEVERE, null, ex);
        }

        System.out.println(System.getProperty("user.dir"));

        mailClient = createMailClient();
    }

    private String databaseConfigFolder() {
        return config.getJsonObject("database").getString("folder", "appState");
    }

    private void readConfig() {
        String configStr = System.getenv("APP_CONFIG_STR");

        if (configStr == null) {
            configStr = createBufferedReader(new File("config.json")).lines().collect(Collectors.joining());
            System.out.println("io.vertx.starter.MainVerticle.readConfig(FILE) \n " + configStr);
        } else {
            System.out.println("io.vertx.starter.MainVerticle.readConfig(ENV) \n " + configStr);
        }
        config = new JsonObject(Buffer.buffer(configStr));
    }
}
