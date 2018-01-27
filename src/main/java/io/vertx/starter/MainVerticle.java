package io.vertx.starter;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import redis.clients.jedis.Jedis;

public class MainVerticle extends AbstractVerticle {

    private final String WEB_ROOT = "src/main/webroot/";
    private final String NODE_MODULES = "node_modules/";

    List<NameSearchGroup> currentGroups = new LinkedList<>();

    MailClient mailClient;
    MessageDigest encryptor;
    RedisClient redis;
    Jedis jedis;

    JsonObject config;

    @Override
    public void start() {
        readConfig();

        if (System.getenv("REDIS_URL") != null) {
            jedis = new Jedis(System.getenv("REDIS_URL"));
        }

        new File(databaseConfigFolder()).mkdirs();

        restore();

        try {
            this.encryptor = MessageDigest.getInstance(securityConfig().getString("encryption"));
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(MainVerticle.class.getName()).log(Level.SEVERE, null, ex);
        }

        System.out.println(System.getProperty("user.dir"));

        Router router = Router.router(vertx);

        mailClient = createMailClient();

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

        vertx.eventBus().consumer("proposeName", this::distName);
        vertx.eventBus().consumer("removeName", this::removeName);
        vertx.eventBus().consumer("signup", this::signup);
        vertx.eventBus().consumer("list", this::listGroups);
        vertx.eventBus().consumer("create", this::createGroup);
        vertx.eventBus().consumer("enter", this::enterGroup);
        vertx.eventBus().consumer("addMember", this::addMember);
        vertx.eventBus().consumer("setUserName", this::setUserName);
        vertx.eventBus().consumer("upgrade", this::upgrade);
        vertx.eventBus().consumer("resign", this::resign);

        router.route().handler(StaticHandler
                .create(WEB_ROOT)
                .setCacheEntryTimeout(3600 * 12 * 1000)
                .setCachingEnabled(config.getJsonObject("general").getBoolean("caching", Boolean.TRUE))
                .setMaxAgeSeconds(3600 * 12));

//        router.route("/styles/*").handler(StaticHandler
//                .create(NODE_MODULES)
//                .setCacheEntryTimeout(3600 * 12 * 1000)
//                .setCachingEnabled(true)
//                .setMaxAgeSeconds(3600 * 12)
//        );
        final int port = System.getenv("PORT") == null
                ? generalConfig().getInteger("port", 8888)
                : Integer.parseInt(System.getenv("PORT"));

        System.out.println("io.vertx.starter.MainVerticle.start() -> listening on port " + port);

        System.out.println("io.vertx.starter.MainVerticle.start() PORT " + System.getenv("PORT"));

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(port);
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

    private void distName(Message<JsonObject> msg) {
        update(msg, (names, name) -> {
            names.add(name);
            return names.stream().distinct().collect(Collectors.toList());
        });
    }

    private void removeName(Message<JsonObject> msg) {
        update(msg, (names, name) -> {
            names.remove(name);
            return names.stream().distinct().collect(Collectors.toList());
        });
    }

    private void resign(Message<JsonObject> msg) {
        String usrID = msg.body().getString("usr");
        String grpID = msg.body().getString("grp");
        updateGroup(filterByGroupID(grpID), filterByUserID(usrID), (group, user) -> {
            group.members = group.members.stream().filter(u -> !u.id.equals(user.id)).collect(Collectors.toList());
        });
        msg.reply(
                findOptionalGroup(filterByGroupID(grpID))
                        .map(group -> new JsonObject().put("group", "updated"))
                        .orElse(new JsonObject().put("group", "removed")).encode());
    }

    private void update(Message<JsonObject> msg, BiFunction<List<String>, String, List<String>> todo) {
        String usrID = msg.body().getString("usr");
        String grpID = msg.body().getString("grp");
        String name = msg.body().getString("name");

        NameSearchGroup group = currentGroups.stream()
                .filter(grp -> grp.id.equals(grpID))
                .findAny().orElseThrow(() -> new RuntimeException("unknown groupID: " + grpID));

        NameSearchMember user = group.members.stream()
                .filter(member -> member.id.equals(usrID))
                .findAny().orElseThrow(() -> new RuntimeException("unknown userID: " + usrID));

        user.nominations = todo.apply(user.nominations, name);

        backup(group);

        vertx.eventBus().publish("grp-" + grpID, new JsonObject().put("update", "now"));
    }

    private void upgrade(Message<JsonObject> msg) {
        System.out.println("io.vertx.starter.MainVerticle.upgrade(" + msg.body().encode() + ")");
        updateUser(grp -> grp.id.equals(msg.body().getString("grp")), user -> user.name.equals(msg.body().getString("member")), user -> {
            user.role = Role.INITIATOR;
        });
    }

    public void msgGroupAndUser(Message<JsonObject> msg, BiConsumer<NameSearchGroup, NameSearchMember> todo) {
        msgGroupAndUserID(msg, (grpID, usrID) -> {
            final NameSearchGroup group = findGroup(filterByGroupID(grpID));
            todo.accept(group, findUserInGroup(group, filterByUserID(usrID)));
        });
    }

    public void msgGroupAndUserID(Message<JsonObject> msg, BiConsumer<String, String> todo) {
        String usrID = msg.body().getString("usr");
        String grpID = msg.body().getString("grp");
        todo.accept(grpID, usrID);
    }

    private void updateUser(Message<JsonObject> msg, Consumer<NameSearchMember> todo) {
        msgGroupAndUserID(msg, (grpID, usrID)
                -> updateUser(filterByGroupID(grpID), filterByUserID(usrID), todo));
    }

    private Predicate<NameSearchGroup> filterByGroupID(String grpID) {
        final Predicate<NameSearchGroup> filterByGroupID = grp -> grp.id.equals(grpID);
        return filterByGroupID;
    }

    private Predicate<NameSearchMember> filterByUserID(String usrID) {
        final Predicate<NameSearchMember> filterByUserID = member -> member.id.equals(usrID);
        return filterByUserID;
    }

    private void updateGroup(final Predicate<NameSearchGroup> filterByGroupID, final Predicate<NameSearchMember> filterByUserID, BiConsumer<NameSearchGroup, NameSearchMember> todo) {
        NameSearchGroup group = findGroup(filterByGroupID);
        NameSearchMember user = findUserInGroup(group, filterByUserID);

        todo.accept(group, user);
        if (backup(group)) {
            sendUpdateSignalToUsers(group);
        }
    }

    private void updateUser(final Predicate<NameSearchGroup> filterByGroupID, final Predicate<NameSearchMember> filterByUserID, Consumer<NameSearchMember> todo) {
        NameSearchGroup group = findGroup(filterByGroupID);
        NameSearchMember user = findUserInGroup(group, filterByUserID);

        todo.accept(user);
        backup(group);

        sendUpdateSignalToUsers(group);
    }

    private void sendUpdateSignalToUsers(NameSearchGroup group) {
        vertx.eventBus().publish("grp-" + group.id, new JsonObject().put("update", "now"));
    }

    private Optional< NameSearchMember> findOptionalUserInGroup(NameSearchGroup group, final Predicate<NameSearchMember> filterByUserID) {
        return group.members.stream()
                .filter(filterByUserID)
                .findAny();
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

    private void createGroup(Message<JsonObject> msg) {
        NameSearchGroup nsg = new NameSearchGroup();
        nsg.id = UUID.randomUUID().toString();
        nsg.name = msg.body().getString("name");
        NameSearchMember user = new NameSearchMember();
        user.id = msg.body().getString("usr");
        user.name = "";
        user.role = Role.INITIATOR;
        nsg.members.add(user);
        currentGroups.add(nsg);
        backup(nsg);
        msg.reply(new JsonObject().put("update", "now").put("grp", nsg.id));
    }

    private void listGroups(Message<JsonObject> msg) {
        String userID = msg.body().getString("usr");
        List<NameSearchGroup> theUsersGroups = currentGroups.stream()
                .filter(group -> group.members.stream()
                .filter(member -> member.id.equals(userID))
                .findAny()
                .isPresent())
                .collect(Collectors.toList());
        msg.reply(theUsersGroups.stream()
                .map(group -> group.toPublic(userID))
                .collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
    }

    private void enterGroup(Message<JsonObject> msg) {
        System.out.println("io.vertx.starter.MainVerticle.enterGroup()" + msg.body().encode());
        msgGroupAndUserID(msg, (grpID, userID) -> {
            msg.reply(findGroup(filterByGroupID(grpID)).toCurrent(userID));

        });
    }

    private void setUserName(Message<JsonObject> msg) {
        String name = msg.body().getString("name");
        updateUser(msg, user -> {
            user.name = name;
        });
    }

    private void addMember(Message<JsonObject> msg) {
        msgGroupAndUser(msg, (group, user) -> {
            if (user.role == Role.PROPOSER) {
                return;
            }

            String email = msg.body().getString("email");
            String secret = appSecret();
            String id = encryption(secret + email);

            String subject = "Signup";
            String text = "Du wurdest eingeladen, einen Namen zu suchen in der Gruppe " + group.name + ". Um teilzunehmen, folge dem Link: ";
            String confirmationlink = generalConfig().getString("appUrl") + "/usr/" + id;

            MailMessage mailMessage = new MailMessage(invitationConfig().getString("fromAdress"), email, subject, text + confirmationlink);

            mailClient.sendMail(mailMessage, (sendMail) -> {
                if (sendMail.succeeded()) {
                    NameSearchMember newMember = new NameSearchMember();
                    newMember.id = id;
                    group.members.add(newMember);
                    backup(group);
                    msg.reply(new JsonObject().put("result", "success").encode());
                } else {
                    msg.reply(new JsonObject().put("result", "error").put("error", sendMail.cause().getMessage()).encode());
                }
            });
        });
    }

    private void signup(Message<JsonObject> msg) {
        String email = msg.body().getString("email");
        String secret = appSecret();
        String id = encryption(secret + email);

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

    String encryption(String input) {
        if (input == null) {
            return null;
        }
        byte[] passBytes = input.getBytes();
        encryptor.reset();
        byte[] digested = encryptor.digest(passBytes);
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < digested.length; i++) {
            sb.append(Integer.toHexString(0xff & digested[i]));
        }
        return sb.toString();
    }

    public boolean backup(NameSearchGroup group) {
        if (group.members.stream().anyMatch(m -> m.role == Role.INITIATOR)) {
            System.out.println("io.vertx.starter.MainVerticle.backup(BACKUP)");
            if (jedis != null) {
                String json = Json.encode(group);
                jedis.hset("groups", group.id, json);
            }
            return true;
        } else {
            System.out.println("io.vertx.starter.MainVerticle.backup(REMOVE)");
            currentGroups.remove(group);
            if (jedis != null) {
                jedis.hdel("groups", group.id);
            }
        }
        return false;
    }

    public void restore() {
        if (jedis != null) {
            Set<String> groups = jedis.hkeys("groups");

            currentGroups = groups.stream()
                    .map(e -> Json.decodeValue(jedis.hget("groups", e), NameSearchGroup.class))
                    .collect(Collectors.toList());
        }
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
}
