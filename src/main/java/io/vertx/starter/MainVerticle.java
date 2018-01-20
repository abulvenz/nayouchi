package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

public class MainVerticle extends AbstractVerticle {

    private final String WEB_ROOT = "src/main/webroot/";
    private final String NODE_MODULES = "node_modules/";

    JsonArray names = new JsonArray();

    @Override
    public void start() {

        System.out.println(System.getProperty("user.dir"));

        Router router = Router.router(vertx);

        // Allow outbound traffic to the vtoons addresses
        BridgeOptions options = new BridgeOptions()
                // this is the public store allowed inbound
                .addInboundPermitted(new PermittedOptions())
                // all outbound messages are permitted
                .addOutboundPermitted(new PermittedOptions());

        router.get("/usr/:userID/grp/:grpID").handler(ctx -> {
            ctx.reroute("/");
        });

        router.route("/eventbus/*").handler(SockJSHandler.create(vertx).bridge(options));

        vertx.eventBus().consumer("sendName", this::distName);
        vertx.eventBus().consumer("fetch", this::fetchNames);
        vertx.eventBus().consumer("removeName", this::removeName);

        router.route().handler(StaticHandler.create(WEB_ROOT).setCachingEnabled(false));
        router.route("/styles/*").handler(StaticHandler.create(NODE_MODULES).setCachingEnabled(false));

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(8080);
    }

    private void distName(Message<JsonObject> msg) {
        names.add(msg.body());
        vertx.eventBus().publish("names", msg.body());
    }

    private void fetchNames(Message<String> msg) {
        System.out.println("fetch" + names.encode());
        msg.reply(names);
    }

    private void removeName(Message<JsonObject> msg) {

        if (names.remove(msg.body())) {
            System.out.println("remove" + msg.body().encode());
            vertx.eventBus().publish("removedName", msg.body());
        } else {
            System.out.println(msg.body() + " not found in " + names.encode());
        }
    }
}
